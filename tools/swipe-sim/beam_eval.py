# Beam width / ranking trade-off for decode_beam() vs exact decode(), on held-out words.
# Usage: python3 beam_eval.py
import os, multiprocessing as mp
here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "elastic.py")).read().split('if __name__ == "__main__":')[0])
random.seed(99)
test = random.choices([w for w, _ in pool], weights=wts, k=300)
LONG = [w for w in random.choices([w for w, _ in pool], weights=wts, k=4000) if len(geo(w)) >= 8][:120]

def run(cfg):
    beam, alpha = cfg
    random.seed(8)
    per, nodes = [], []
    for kw in cfgs:
        ok = 0
        for w in test:
            path = swipe2(w, **kw)
            r = decode(path) if beam is None else decode_beam(path, P, 5, beam, alpha, nodes)
            ok += r[:1] == [w]
        per.append(ok / len(test))
    ok_long, long_nodes = 0, []
    for w in LONG:
        path = swipe2(w, sig=0.2)
        r = decode(path) if beam is None else decode_beam(path, P, 5, beam, alpha, long_nodes)
        ok_long += r[:1] == [w]
    return cfg, sum(per) / len(per), per, ok_long / len(LONG), (np.mean(nodes) if nodes else 0), (np.max(long_nodes) if long_nodes else 0)

if __name__ == "__main__":
    grid = [(None, None)] + [(b, a) for b in (50, 100, 200, 400) for a in (0.0, 0.5, 1.0)]
    with mp.get_context("fork").Pool(13) as pl:
        for cfg, m, per, lng, nm, nx in sorted(pl.map(run, grid), key=lambda r: (r[0][0] or 1e9, r[0][1] or 0)):
            label = "exact" if cfg[0] is None else f"beam={cfg[0]:3d} alpha={cfg[1]}"
            print(f"{label:22s} top1={m:.1%} long(>=8 letters)={lng:.0%} nodes avg={nm:.0f} max(long)={nx:.0f}  {['%.0f%%' % (x*100) for x in per]}", flush=True)
