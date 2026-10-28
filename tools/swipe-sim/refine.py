# Local refinement around the best tune.py result, scored on a held-out word sample
# (different seed from tune.py), alongside the current decoder. Usage: python3 refine.py
import os, sys, multiprocessing as mp
here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "elastic.py")).read().split('if __name__ == "__main__":')[0])
random.seed(99)
test = random.choices([w for w, _ in pool], weights=wts, k=300)  # held-out sample

def eval_config(p):
    random.seed(8)
    per = []
    for kw in cfgs:
        if p == "current":
            per.append(sum(predict(swipe2(w, **kw), "logfreq")[:1] == [w] for w in test) / len(test))
        else:
            per.append(sum(decode(swipe2(w, **kw), p)[:1] == [w] for w in test) / len(test))
    return sum(per) / len(per), per, p

if __name__ == "__main__":
    LAMBDA = 0.12
    base = dict(W_PT=1.0, W_DEV=1.0, W_BACK=2.0, W_LEAD=2.0, W_TAIL=1.0, LAMBDA=0.2)
    grid = ["current", base]
    for k, vals in [("W_BACK", [3.0, 4.0]), ("W_LEAD", [3.0, 4.0]), ("W_TAIL", [1.5, 2.0]),
                    ("LAMBDA", [0.15, 0.25]), ("W_DEV", [0.75, 1.5])]:
        for v in vals:
            grid.append({**base, k: v})
    grid.append({**base, "W_BACK": 3.0, "W_LEAD": 3.0, "W_TAIL": 1.5})
    with mp.get_context("fork").Pool(14) as pool_:
        res = sorted(pool_.map(eval_config, grid), key=lambda r: -r[0])
    for m, per, p in res:
        print(f"{m:.1%} {['%.0f%%' % (x*100) for x in per]} {p}", flush=True)
