# Prototype of an elastic-alignment swipe decoder, evaluated against the current krkey decoder.
#
# Each letter of a word is aligned to a point of the (resampled) swipe, monotonically. Cost:
#   W_PT   * distance of each letter's key from its aligned point
#   W_DEV  * deviation of the swipe from the straight line between consecutive letters
#            (integrated over arc length), plus W_BACK for moving against that line
#   W_LEAD / W_TAIL * arc length before the first / after the last aligned letter
#   LAMBDA * ln(rank + 2) frequency prior
# The DP runs over the dictionary trie: a node's cost row extends its parent's in O(M), and a
# row's minimum never decreases down the trie, so subtrees are pruned against the current top-k.
#
# Usage: python3 elastic.py [path/to/en_dict.txt]
import os
import numpy as np

here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "sim2.py")).read().split("cfgs=")[0])

M = 48
P = dict(W_PT=1.0, W_DEV=1.0, W_BACK=3.0, W_LEAD=3.0, W_TAIL=1.5, LAMBDA=0.2)  # tuned (refine.py)
KC = {c: np.array(xy) / KU for c, xy in C.items()}  # key centres in key units


class Node:
    __slots__ = ("children", "words")

    def __init__(self):
        self.children = {}
        self.words = []  # (word, rank) ending here


def insert(root_, w, rank):
    g = geo(w)
    if len(g) < 2 or any(c not in C for c in g):
        return
    n = root_
    for c in g:
        n = n.children.setdefault(c, Node())
    n.words.append((w, rank))


root = Node()
for rank, w in enumerate(dic):
    insert(root, w, rank)

# Learned words (lowercase word -> times learned), as on the device.
#   "rank":  learning lifts a word's effective rank to at most LEARNED_ONLY_RANK / (1 + n)^1.5
#   "bonus": the earlier on-device rule, a bonus of min(0.6, 0.25 ln(1 + n)) off the score
LEARNED = {}
LEARNED_MODE = "rank"
LEARNED_ONLY_RANK = 1000
learned_root = Node()
_dict_lower = {w.lower() for w in dic}


def set_learned(words):
    global LEARNED, learned_root
    LEARNED = {w.lower(): n for w, n in words.items()}
    learned_root = Node()
    for w in LEARNED:
        if w not in _dict_lower:
            insert(learned_root, w, -1)


def prior(word, rank, p):
    n = LEARNED.get(word.lower(), 0)
    r = LEARNED_ONLY_RANK if rank < 0 else rank
    if LEARNED_MODE == "bonus":
        return p["LAMBDA"] * np.log(r + 2) - (min(0.6, 0.25 * np.log(1 + n)) if n else 0.0)
    if n:
        r = min(r, LEARNED_ONLY_RANK / (1 + n) ** 1.5)
    return p["LAMBDA"] * np.log(r + 2)


def seg_dev(pts, a, b):
    ab = b - a
    L2 = float(ab @ ab)
    if L2 == 0:
        return np.linalg.norm(pts - a, axis=1), None
    t = np.clip(((pts - a) @ ab) / L2, 0, 1)
    return np.linalg.norm(pts - (a + t[:, None] * ab), axis=1), ab / np.sqrt(L2)


def decode(path, p=P, k=5):
    pts = np.array(resample(path, M)) / KU
    steps = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    A = np.concatenate([[0.0], np.cumsum(steps)])
    h = np.concatenate([[0.0], steps])  # arc length each point accounts for
    move = np.vstack([[0, 0], np.diff(pts, axis=0)])
    total = A[-1]
    tail = p["W_TAIL"] * (total - A)
    best = []  # sorted (score, word)
    prior_min = p["LAMBDA"] * np.log(2) - (0.6 if LEARNED_MODE == "bonus" and LEARNED else 0.0)

    def cap():
        return best[-1][0] if len(best) >= k else 1e9

    def visit(node, row, prev_key):
        for c, child in node.children.items():
            K = KC[c]
            d = np.linalg.norm(pts - K, axis=1) * p["W_PT"]
            if row is None:
                new = p["W_LEAD"] * A + d
            else:
                dev, u = seg_dev(pts, prev_key, K)
                cost = dev
                if u is not None:
                    cost = cost + p["W_BACK"] * np.maximum(0, -(move @ u)) / np.maximum(h, 1e-9) * (h > 0)
                S = np.cumsum(p["W_DEV"] * cost * h)
                new = d + np.minimum.accumulate(row - S) + S
            if new.min() + prior_min >= cap():
                continue
            if child.words:
                base = float((new + tail).min())
                for w, r in child.words:
                    s = base + prior(w, r, p)
                    if s < cap():
                        best.append((s, w))
                        best.sort()
                        del best[k:]
            visit(child, new, K)

    visit(root, None, None)
    visit(learned_root, None, None)
    return [w for _, w in best]


BEAM = 200
BEAM_ALPHA = 0.5


def decode_beam(path, p=P, k=5, beam=BEAM, alpha=BEAM_ALPHA, stats=None):
    """Beam-search variant of decode() (what the app runs): per trie depth, keeps the `beam` best
    prefixes ranked by min_j(row[j] + alpha * tail[j]), i.e. charging part of the swipe not yet
    explained. Subtrees that cannot reach the top k are pruned as in decode()."""
    pts = np.array(resample(path, M)) / KU
    steps = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    A = np.concatenate([[0.0], np.cumsum(steps)])
    h = np.concatenate([[0.0], steps])
    move = np.vstack([[0, 0], np.diff(pts, axis=0)])
    tail = p["W_TAIL"] * (A[-1] - A)
    best = {}
    frontier = [(root, None, None), (learned_root, None, None)]
    nodes = 0
    prior_min = p["LAMBDA"] * np.log(2) - (0.6 if LEARNED_MODE == "bonus" and LEARNED else 0.0)

    def cap():
        return sorted(best.values())[k - 1] if len(best) >= k else 1e9

    while frontier:
        nxt = []
        for node, row, prev_key in frontier:
            for c, child in node.children.items():
                K = KC[c]
                d = np.linalg.norm(pts - K, axis=1) * p["W_PT"]
                if row is None:
                    new = p["W_LEAD"] * A + d
                else:
                    dev, u = seg_dev(pts, prev_key, K)
                    cost = dev
                    if u is not None:
                        cost = cost + p["W_BACK"] * np.maximum(0, -(move @ u)) / np.maximum(h, 1e-9) * (h > 0)
                    S = np.cumsum(p["W_DEV"] * cost * h)
                    new = d + np.minimum.accumulate(row - S) + S
                nodes += 1
                if new.min() + prior_min >= cap():
                    continue
                if child.words:
                    base = float((new + tail).min())
                    for w, r in child.words:
                        sc = base + prior(w, r, p)
                        if sc < best.get(w, 1e9):
                            best[w] = sc
                if child.children:
                    nxt.append((float((new + alpha * tail).min()), child, new, K))
        nxt.sort(key=lambda t: t[0])
        frontier = [(n, r, kk) for _, n, r, kk in nxt[:beam]]
    if stats is not None:
        stats.append(nodes)
    return [w for w, _ in sorted(best.items(), key=lambda kv: kv[1])[:k]]


cfgs = [dict(sig=0.2), dict(sig=0.2, step=60), dict(sig=0.2, k=18), dict(sig=0.2, overshoot=0.6), dict(sig=0.35, k=12)]


def evaluate(dec, label):
    random.seed(7)
    per = []
    t5s = []
    for kw in cfgs:
        t1 = t5 = 0
        for w in test:
            r = dec(swipe2(w, **kw))
            t1 += r[:1] == [w]
            t5 += w in r
        per.append(t1 / len(test))
        t5s.append(t5 / len(test))
    print(f"{label:34s} mean top1={sum(per)/len(per):.1%} top5={sum(t5s)/len(t5s):.1%}  per-distortion={['%.0f%%' % (x*100) for x in per]}", flush=True)


if __name__ == "__main__":
    import time
    LAMBDA = 0.12
    evaluate(lambda pth: predict(pth, "logfreq"), "current (log-rank 0.12)")
    t = time.time()
    evaluate(decode, "elastic " + str(P))
    print(f"elastic: {(time.time()-t)/(len(test)*len(cfgs))*1000:.0f} ms/swipe (python)")
