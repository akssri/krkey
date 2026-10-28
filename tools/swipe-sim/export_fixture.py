# Exports synthetic swipes and the elastic prototype's top-5 (decode_beam, as the app runs) for the Kotlin
# parity test (app/src/test/.../WordPredictorParityTest.kt).
# Usage: python3 export_fixture.py > ../../app/src/test/resources/swipe_parity.json
import json, os, sys
here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "elastic.py")).read().split('if __name__ == "__main__":')[0])

random.seed(11)
cases = []
for kw in cfgs:
    for w in test[:20]:
        path = swipe2(w, **kw)
        cases.append({"word": w, "path": [[float(x), float(y)] for x, y in path], "top": decode_beam(path, P, 5)})
print(json.dumps({
    "params": P,
    "keys": [[k, *map(float, r)] for k, r in rects.items()],
    "cases": cases,
}))
