# Offline simulator for krkey's swipe decoder (port of WordPredictor.predict).
# Runs synthetic swipes on the portrait Latin layout and reports top-1/top-5 accuracy.
# Usage: python3 sim.py [path/to/en_dict.txt]
# Faithful-ish port of krkey WordPredictor.predict on the krkey Latin portrait layout
import math, random, sys
random.seed(1)
D=2.75; W=1080; M=3*D; KH=50*D; PITCH=KH+2*M
def row(keys, weights, y):
    tot=sum(weights); x=0; out={}
    for k,w in zip(keys,weights):
        kw=W*w/tot; 
        if k: out[k]=((x+M, y+M, x+kw-M, y+M+KH))
        x+=kw
    return out
rects={}
rects.update(row("qwertyuiop",[1]*10,0))
rects.update(row("asdfghjkl",[1]*9,PITCH))
rects.update(row([None]+list("zxcvbnm")+[None],[1.2]+[1]*7+[1.2],2*PITCH))
C={k:((r[0]+r[2])/2,(r[1]+r[3])/2) for k,r in rects.items()}
ws=[r[2]-r[0] for r in rects.values()]; hs=[r[3]-r[1] for r in rects.values()]
KU=(sum(ws)/len(ws)+sum(hs)/len(hs))/2
import os
DICT=sys.argv[1] if len(sys.argv)>1 else os.path.join(os.path.dirname(os.path.abspath(__file__)),"../../app/src/main/assets/en_dict.txt")
dic=[l.strip() for l in open(DICT)][:20000]
N=40
def dist(a,b): return math.hypot(a[0]-b[0],a[1]-b[1])
def plen(p): return sum(dist(p[i-1],p[i]) for i in range(1,len(p)))
def resample(p,n):
    L=plen(p)
    if L==0: return [p[0]]*n
    iv=L/(n-1); res=[p[0]]; idx=1; acc=0.0
    for i in range(1,n-1):
        t=i*iv
        while idx<len(p):
            s=dist(p[idx-1],p[idx])
            if acc+s>=t:
                r=(t-acc)/s; a,b=p[idx-1],p[idx]
                res.append((a[0]+r*(b[0]-a[0]),a[1]+r*(b[1]-a[1]))); break
            acc+=s; idx+=1
    while len(res)<n: res.append(p[-1])
    return res[:n]
LAMBDA=0.0  # weight of the additive log-rank prior (lm="logfreq")
def geo(w):  # letters used for geometry: lowercase, apostrophes/hyphens skipped
    return "".join(c for c in w.lower() if c not in "'-")
big={}
for i,w in enumerate(dic):
    g=geo(w)
    if len(g)<2 or any(c not in C for c in g): continue
    big.setdefault((g[0],g[-1]),[]).append((w,i))
def near(p,r): 
    return [k for k,c in C.items() if dist(p,c)<r*KU]
def score(w,fi,rp,ps,pe,L,lm):
    ideal=[C[c] for c in geo(w)]
    sd=dist(ps,ideal[0])/KU; ed=dist(pe,ideal[-1])/KU
    if sd>2 or ed>2: return 100
    ri=resample(ideal,N); shape=sum(dist(rp[i],ri[i]) for i in range(N))/KU/N
    order=0; pi=0
    for c in ideal:
        found=False; mn=1e9
        for i in range(pi,len(rp)):
            d=dist(rp[i],c)/KU; mn=min(mn,d)
            if d<1.2: found=True; pi=i; break
        order+= 0.2*mn if found else 1
    lp=abs(math.log((L+KU)/(plen(ideal)+KU)))*2.5
    base=sd+ed+shape*0.5+order*0.3+lp*0.4
    if lm=="krkey": return base*(1+fi/len(dic)*0.25)
    if lm=="logfreq": return base+LAMBDA*math.log(fi+2) if base<8 else 100  # additive log-rank prior
    return base
def predict(path,lm="krkey"):
    rp=resample(path,N); ps,pe=path[0],path[-1]; L=plen(path); cands=[]
    for a in near(ps,1.5):
        for b in near(pe,1.5):
            for w,i in big.get((a,b),[]):
                s=score(w,i,rp,ps,pe,L,lm)
                if s<8 or (lm=="logfreq" and s<100): cands.append((s,w))
    cands.sort(); return [w for s,w in cands[:5]]
def swipe(w,sig):
    # human-ish: noisy vertex positions, corner cutting via smoothing, dense sampling
    v=[(C[c][0]+random.gauss(0,sig*KU),C[c][1]+random.gauss(0,sig*KU)) for c in w]
    pts=[]
    for i in range(1,len(v)):
        a,b=v[i-1],v[i]; n=max(2,int(dist(a,b)/8))
        for j in range(n): t=j/n; pts.append((a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1])))
    pts.append(v[-1])
    k=6; sm=[]
    for i in range(len(pts)):
        s=pts[max(0,i-k):i+k+1]; sm.append((sum(p[0] for p in s)/len(s),sum(p[1] for p in s)/len(s)))
    sm[0]=pts[0]; sm[-1]=pts[-1]
    return sm
test=[w for w in dic[:3000] if len(geo(w))>=2 and all(c in C for c in geo(w))]
random.shuffle(test); test=test[:400]
for sig in (0.0,0.2,0.35):
    for lm in ("krkey","logfreq"):
        t1=t5=0
        for w in test:
            r=predict(swipe(w,sig),lm); t1+= r[:1]==[w]; t5+= w in r
        print(f"noise={sig:.2f}key lm={lm:8s} top1={t1/len(test):.0%} top5={t5/len(test):.0%}")
