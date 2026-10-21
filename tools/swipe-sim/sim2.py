# Usage-weighted (Zipf) evaluation with realistic distortions: sparse sampling,
# corner cutting, end overshoot. Compares krkey's frequency weighting with a log-rank prior.
# Usage: python3 sim2.py [path/to/en_dict.txt]
import os
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),'sim.py')).read().split("test=[w")[0])
import collections
def swipe2(w,sig,step=8,k=6,overshoot=0.0):
    v=[(C[c][0]+random.gauss(0,sig*KU),C[c][1]+random.gauss(0,sig*KU)) for c in geo(w)]
    if overshoot and len(v)>1:
        a,b=v[-2],v[-1]; d=dist(a,b) or 1
        v[-1]=(b[0]+(b[0]-a[0])/d*overshoot*KU, b[1]+(b[1]-a[1])/d*overshoot*KU)
    pts=[]
    for i in range(1,len(v)):
        a,b=v[i-1],v[i]; n=max(2,int(dist(a,b)/8))
        for j in range(n): t=j/n; pts.append((a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])))
    pts.append(v[-1])
    sm=[]
    for i in range(len(pts)):
        s=pts[max(0,i-k):i+k+1]; sm.append((sum(p[0] for p in s)/len(s),sum(p[1] for p in s)/len(s)))
    sm[0]=pts[0]; sm[-1]=pts[-1]
    # decimate to emulate MOVE-event sampling without historical points
    keep=[sm[0]]; acc=0
    for i in range(1,len(sm)):
        acc+=dist(sm[i-1],sm[i])
        if acc>=step: keep.append(sm[i]); acc=0
    keep.append(sm[-1]); return keep
pool=[(w,i) for i,w in enumerate(dic[:5000]) if len(geo(w))>=2 and all(c in C for c in geo(w))]
wts=[1/(i+1) for w,i in pool]   # Zipf usage weighting
test=random.choices([w for w,_ in pool],weights=wts,k=300)
cfgs=[("clean, dense",dict(sig=0.2)),
      ("sparse samples (60px)",dict(sig=0.2,step=60)),
      ("corner cutting",dict(sig=0.2,k=18)),
      ("end overshoot 0.6 key",dict(sig=0.2,overshoot=0.6))]
for name,kw in cfgs:
    for lm in ("krkey","logfreq"):
        random.seed(7); t1=0; conf=collections.Counter()
        for w in test:
            r=predict(swipe2(w,**kw),lm); t1+= r[:1]==[w]
            if r[:1]!=[w]: conf[(w,r[0] if r else "-")]+=1
        print(f"{name:24s} lm={lm:8s} top1={t1/len(test):.0%}  e.g. {[f'{a}->{b}' for (a,b),_ in conf.most_common(5)]}")
