#!/usr/bin/env python3
"""Generate small random decision tables for the golden corpus.

Usage:
    python3 -I golden/scripts/generate_random_tables.py --seed 20261027 --count 100 --out golden/inputs/random

Table i (1-based) is drawn from random.Random(seed + i): 3-40 rows; 1-5 condition columns c1..cK, each with
2-4 values from "0 1 2 3"; decision column d with 2-3 values from "a b c"; with probability 0.1 one cell
(possibly in d) is left empty. Small alphabets make duplicates and inconsistent rows common, on purpose.
The generated files are committed; regenerating with another Python version may draw differently.
"""
import argparse
import csv
import pathlib
import random


def table(rng):
    rows = rng.randint(3, 40)
    columns = rng.randint(1, 5)
    alphabets = [list("0123")[:rng.randint(2, 4)] for _ in range(columns)]
    decisions = list("abc")[:rng.randint(2, 3)]
    data = [[rng.choice(alphabet) for alphabet in alphabets] + [rng.choice(decisions)] for _ in range(rows)]
    if rng.random() < 0.1:
        data[rng.randrange(rows)][rng.randrange(columns + 1)] = ""
    header = [f"c{c}" for c in range(1, columns + 1)] + ["d"]
    return header, data


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--seed", required=True, type=int)
    parser.add_argument("--count", required=True, type=int)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    for i in range(1, args.count + 1):
        header, data = table(random.Random(args.seed + i))
        path = args.out / f"random-{i:03d}.csv"
        with path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f, lineterminator="\n")
            writer.writerow(header)
            writer.writerows(data)
    print(f"wrote {args.count} tables to {args.out}")


if __name__ == "__main__":
    main()
