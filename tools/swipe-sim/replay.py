# Replays swipes logged by SwipeLogger (debug builds) through the current and elastic decoders.
#
#   adb shell run-as com.akssri.krkey cat files/swipes.jsonl > swipes.jsonl
#   python3 replay.py swipes.jsonl [path/to/en_dict.txt]
# Set LEARNED=learned.json (word -> count, e.g. from shared_prefs/krkey_user_dict.xml) to
# include learned words, and LEARNED_MODE=bonus to reproduce the earlier on-device rule.
#
# Labels come from what the user did: "accepted" (kept the top word; biased towards the current
# decoder, which produced it), "picked" (chose another candidate: the current decoder was wrong).
# "rejected" swipes (backspaced) have no label and are only counted.
import json, os, sys
here = os.path.dirname(os.path.abspath(__file__))
log_path = sys.argv[1]
sys.argv = [sys.argv[0]] + sys.argv[2:]
exec(open(os.path.join(here, "elastic.py")).read().split('if __name__ == "__main__":')[0])
import numpy as np

LAMBDA = 0.12
learned_now = {}
if os.environ.get("LEARNED"):
    LEARNED_MODE = os.environ.get("LEARNED_MODE", "rank")
    learned_now = {w.lower(): n for w, n in json.load(open(os.environ["LEARNED"])).items()}
records = [json.loads(l) for l in open(log_path) if l.strip()]
stats = {}
for rec in records:
    out = rec["outcome"]
    if out == "picked" and rec["candidates"] and rec["label"] == rec["candidates"][0][0]:
        out = "accepted"  # re-tapped the word already committed
    s = stats.setdefault(out, {"n": 0, "current": 0, "elastic": 0, "device": 0})
    s["n"] += 1
    if rec["label"] is None:
        continue
    # Rebuild the layout this swipe was made on (globals used by sim.py / elastic.py)
    rects = {k[0]: k[1:] for k in rec["keys"] if len(k[0]) == 1}
    C = {c: ((r[0] + r[2]) / 2, (r[1] + r[3]) / 2) for c, r in rects.items()}
    KU = (np.mean([r[2] - r[0] for r in rects.values()]) + np.mean([r[3] - r[1] for r in rects.values()])) / 2
    KC = {c: np.array(xy) / KU for c, xy in C.items()}
    path = [(p[0], p[1]) for p in rec["path"]]
    label = rec["label"].lower()
    if learned_now:
        # The learned list is from after logging: undo the learning this swipe's own outcome caused
        learned = dict(learned_now)
        if learned.get(label, 0) > 0:
            learned[label] -= 1
        set_learned({w: n for w, n in learned.items() if n > 0})
    s["device"] += bool(rec["candidates"]) and rec["candidates"][0][0].lower() == label
    s["current"] += [w.lower() for w in predict(path, "logfreq")[:1]] == [label]
    s["elastic"] += [w.lower() for w in decode(path)[:1]] == [label]

for out, s in stats.items():
    if out == "rejected":
        print(f"{out:9s} n={s['n']} (no label)")
    else:
        print(f"{out:9s} n={s['n']}  top1 device(logged)={s['device']/max(1,s['n']):.0%} current={s['current']/max(1,s['n']):.0%} elastic={s['elastic']/max(1,s['n']):.0%}")
