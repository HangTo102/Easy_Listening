#!/usr/bin/env python3
"""把 ECDICT 的 ecdict.csv 转成 Easy_Listening 词典源 CSV（word,phonetic,pos,translation）。

用法示例：
    # 只保留四六级/考研/高中等标签（体积小，推荐）
    python tools/scripts/ecdict_to_csv.py ecdict.csv assets/dict/source/ecdict.csv --tags cet4,cet6,ky,gk,zk

    # 全量
    python tools/scripts/ecdict_to_csv.py ecdict.csv assets/dict/source/ecdict.csv --all

    # 过滤：柯林斯星级 >= 2
    python tools/scripts/ecdict_to_csv.py ecdict.csv out.csv --min-collins 2

ECDICT 列：word, phonetic, definition, translation, pos, collins, oxford,
          tag, bnc, frq, exchange, detail, audio
"""
import argparse
import csv
import re
import sys

WORD_RE = re.compile(r"^[A-Za-z][A-Za-z'\- ]*$")


def to_int(value):
    value = (value or "").strip()
    return int(value) if value.isdigit() else 0


def clean(text):
    # ECDICT 用字面 "\n" 表示换行，统一替换为空格，保证单行
    return (text or "").replace("\\n", " ").replace("\n", " ").replace("\r", " ").strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", help="ECDICT 的 ecdict.csv")
    ap.add_argument("dst", help="输出 CSV")
    ap.add_argument("--tags", default="",
                    help="只保留 tag 含这些词的条目（逗号分隔，如 cet4,cet6,ky）")
    ap.add_argument("--all", action="store_true", help="不做标签过滤，保留全部")
    ap.add_argument("--min-collins", type=int, default=0, help="柯林斯星级下限")
    ap.add_argument("--min-bnc", type=int, default=0, help="BNC 词频下限")
    ap.add_argument("--min-frq", type=int, default=0, help="COCA 词频下限")
    ap.add_argument("--no-phonetic-req", action="store_true",
                    help="允许没有音标的词条（默认只要有释义即可）")
    args = ap.parse_args()

    tags = [t.strip().lower() for t in args.tags.split(",") if t.strip()]

    kept = 0
    skipped = 0
    with open(args.src, "r", encoding="utf-8", errors="replace", newline="") as fin, \
         open(args.dst, "w", encoding="utf-8", newline="") as fout:
        reader = csv.reader(fin)
        writer = csv.writer(fout)
        writer.writerow(["word", "phonetic", "pos", "translation"])

        next(reader, None)  # 跳过表头

        for row in reader:
            if len(row) < 10:
                skipped += 1
                continue
            word = clean(row[0])
            phonetic = clean(row[1])
            translation = clean(row[3])
            tag = (row[7] or "").lower()
            collins = to_int(row[5])
            bnc = to_int(row[8])
            frq = to_int(row[9])

            if not word or not WORD_RE.match(word):
                skipped += 1
                continue
            if not translation:
                skipped += 1
                continue
            if tags and not any(t in tag.split() for t in tags):
                skipped += 1
                continue
            if collins < args.min_collins or bnc < args.min_bnc or frq < args.min_frq:
                skipped += 1
                continue

            writer.writerow([word, phonetic, "", translation])
            kept += 1

    print("kept %d, skipped %d -> %s" % (kept, skipped, args.dst))


if __name__ == "__main__":
    sys.exit(main())
