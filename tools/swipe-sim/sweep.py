# Sweeps the log-rank prior weight (LAMBDA) over a mix of distortions on usage-weighted swipes.
# Usage: python3 sweep.py [path/to/en_dict.txt]
import os, sys
here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "sim2.py")).read().split("cfgs=")[0])
cfgs = [dict(sig=0.2), dict(sig=0.2, step=60), dict(sig=0.2, k=18), dict(sig=0.2, overshoot=0.6), dict(sig=0.35, k=12)]
runs = [("krkey", None)] + [("logfreq", l) for l in (0.05, 0.1, 0.15, 0.2, 0.25, 0.35)]
for lm, lam in runs:
    if lam is not None:
        LAMBDA = lam
    random.seed(7)
    per = []
    for kw in cfgs:
        t1 = sum(predict(swipe2(w, **kw), lm)[:1] == [w] for w in test)
        per.append(t1 / len(test))
    print(f"{lm:8s} lambda={lam}  mean top1={sum(per)/len(per):.1%}  per-distortion={['%.0f%%' % (p*100) for p in per]}")
