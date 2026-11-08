#!/usr/bin/env python3
"""Generates the Phase 1 spike's inputs (Task 6).

Usage:
    python3 -I spike/data/generate.py --repo . --out spike/results/data [--seed 20261108] [--large-bytes 1073741824]

small.csv is golden/inputs/public/car.csv. large.csv has 20 columns c1..c19,d; column i draws from its own
alphabet of 2-6 random words (3-6 letters), and d depends on c1 and c2 with 2 % noise, so rules exist.
awkward/ holds the inputs that stress storage and parsing: 500 and 2,000 columns, odd column names, a quoted
line break, BOM with CRLF, empty cells, and copies of the golden edge inputs that pass upload validation.
"""
import argparse
import csv
import pathlib
import random
import shutil
import string

EDGE = ["bom-crlf", "dotted-names", "empty-cells", "identical-rows", "mcd-tie", "numeric-like", "quoted-values",
        "single-condition", "single-decision", "single-row", "unicode", "whitespace"]


def words(rng, count):
    return ["".join(rng.choice(string.ascii_lowercase) for _ in range(rng.randint(3, 6))) for _ in range(count)]


def large(rng, path, target_bytes):
    alphabets = [words(rng, rng.randint(2, 6)) for _ in range(19)]
    written, rows, batch = 0, 0, 100_000
    with open(path, "w", newline="", encoding="utf-8") as f:
        header = ",".join([f"c{i}" for i in range(1, 20)] + ["d"]) + "\n"
        f.write(header)
        written += len(header)
        while written < target_bytes:
            columns = [rng.choices(alphabet, k=batch) for alphabet in alphabets]
            noise = rng.choices([True, False], weights=[2, 98], k=batch)
            lines = []
            for r in range(batch):
                values = [column[r] for column in columns]
                decision = values[0] == alphabets[0][0] and values[1] != alphabets[1][0]
                if noise[r]:
                    decision = not decision
                lines.append(",".join(values) + ("," + ("yes" if decision else "no")) + "\n")
            chunk = "".join(lines)
            f.write(chunk)
            written += len(chunk.encode("utf-8"))
            rows += batch
    return rows


def write_csv(path, header, rows, line_end="\n", bom=False):
    with open(path, "w", newline="", encoding="utf-8-sig" if bom else "utf-8") as f:
        csv.writer(f, lineterminator=line_end).writerows([header] + rows)


def wide(rng, path, columns, rows):
    header = [f"c{i}" for i in range(1, columns)] + ["d"]
    write_csv(path, header, [[rng.choice("0123") for _ in range(columns - 1)] + [rng.choice("ab")] for _ in range(rows)])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    parser.add_argument("--seed", type=int, default=20261108)
    parser.add_argument("--large-bytes", type=int, default=2**30)
    args = parser.parse_args()
    rng = random.Random(args.seed)
    awkward = args.out / "awkward"
    awkward.mkdir(parents=True, exist_ok=True)

    shutil.copyfile(args.repo / "golden/inputs/public/car.csv", args.out / "small.csv")
    wide(rng, awkward / "wide-500.csv", 500, 1000)
    wide(rng, awkward / "wide-2000.csv", 2000, 100)
    write_csv(awkward / "names.csv", ["first name", "a.b", 'say "hi"', "łzawienie", "x" * 70, "d"],
              [[rng.choice("01"), rng.choice("xy"), rng.choice("pq"), rng.choice(["tak", "nie"]), rng.choice("mn"),
                rng.choice(["ą", "ę"])] for _ in range(50)])
    write_csv(awkward / "quoted-newline.csv", ["a", "b", "d"],
              [["x", "line1\nline2", "y"], ["z", "plain", "n"], ["w,1", "p", "y"]])
    write_csv(awkward / "bom-crlf.csv", ["a", "b", "d"],
              [[rng.choice("12"), rng.choice("34"), rng.choice("yn")] for _ in range(100)], line_end="\r\n", bom=True)
    write_csv(awkward / "empty-cells.csv", ["a", "b", "c", "d"],
              [[rng.choice(["", "0", "1"]) for _ in range(3)] + [rng.choice(["y", "n"])] for _ in range(200)])
    for name in EDGE:
        shutil.copyfile(args.repo / f"golden/inputs/edge/{name}.csv", awkward / f"edge-{name}.csv")

    rows = large(rng, args.out / "large.csv", args.large_bytes)
    print(f"generated: large.csv with {rows} rows, awkward set in {awkward}")


if __name__ == "__main__":
    main()
