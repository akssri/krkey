# Random search over the elastic decoder's weights (parallel). Usage: python3 tune.py [n_configs]
import os, sys, multiprocessing as mp
here = os.path.dirname(os.path.abspath(__file__))
sys.argv = [sys.argv[0]] + sys.argv[2:]
n_cfg = int(os.environ.get("N_CFG", "48"))
exec(open(os.path.join(here, "elastic.py")).read().split('if __name__ == "__main__":')[0])

def eval_config(p):
    random.seed(7)
    per = []
    for kw in cfgs:
        per.append(sum(decode(swipe2(w, **kw), p)[:1] == [w] for w in test) / len(test))
    return sum(per) / len(per), per, p

if __name__ == "__main__":
    rng = random.Random(3)
    grid = [dict(P)]
    for _ in range(n_cfg - 1):
        grid.append(dict(W_PT=1.0,
                         W_DEV=rng.choice([0.25, 0.5, 1.0, 2.0]),
                         W_BACK=rng.choice([0.0, 0.5, 1.0, 2.0]),
                         W_LEAD=rng.choice([0.5, 1.0, 2.0]),
                         W_TAIL=rng.choice([0.1, 0.25, 0.5, 1.0]),
                         LAMBDA=rng.choice([0.06, 0.12, 0.2, 0.3])))
    with mp.get_context("fork").Pool(14) as pool:
        res = sorted(pool.map(eval_config, grid), key=lambda r: -r[0])
    for m, per, p in res[:8]:
        print(f"{m:.1%} {['%.0f%%' % (x*100) for x in per]} {p}")
