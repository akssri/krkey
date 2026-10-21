#!/usr/bin/env python3
# Generates app/src/main/assets/en_dict.txt (one word per line, most frequent first)
# from the AOSP LatinIME English wordlist (Apache-2.0), e.g.
#   android-keyboard/dictionaries/en_US_wordlist.combined.gz
#
# Usage: python3 gen_en_dict.py <en_US_wordlist.combined.gz> [count] > en_dict.txt
#
# Keeps unflagged words (drops offensive, abbreviation, medical, babytalk, ...)
# made only of ASCII letters and apostrophes. Possessives ("band's") are dropped;
# contractions ("don't", "I'm", "it's") are kept.
import gzip
import re
import sys

S_CONTRACTIONS = {"it's", "he's", "she's", "that's", "what's", "let's", "there's", "here's",
                  "who's", "how's", "where's", "when's", "why's"}
APOSTROPHE_SUFFIXES = ("n't", "'ll", "'ve", "'re", "'d", "'m")


def keep(word, flags):
    if flags or not re.fullmatch(r"[A-Za-z']+", word):
        return False
    if "'" not in word:
        return len(word) >= 2 or word in ("I", "a")
    lower = word.lower()
    return lower in S_CONTRACTIONS or lower.endswith(APOSTROPHE_SUFFIXES)


def main():
    src = sys.argv[1]
    count = int(sys.argv[2]) if len(sys.argv) > 2 else 20000
    rows = []
    with gzip.open(src, "rt", encoding="utf-8") as f:
        for line in f:
            m = re.match(r" word=([^,]*),f=(\d+)(?:,flags=([^,]*))?", line)
            if m and int(m.group(2)) > 0 and keep(m.group(1), m.group(3) or ""):
                rows.append((m.group(1), int(m.group(2))))
    rows.sort(key=lambda r: -r[1])  # stable: keeps source order within equal frequency
    seen = set()
    out = []
    for word, _ in rows:
        if word.lower() in seen:
            continue
        seen.add(word.lower())
        out.append(word)
        if len(out) == count:
            break
    sys.stdout.write("\n".join(out) + "\n")


if __name__ == "__main__":
    main()
