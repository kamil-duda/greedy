#!/usr/bin/env python3
"""Checks the spike's generated inputs (Task 6). Run: python3 -I spike/data/check_inputs.py spike/results/data"""
import csv
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
awkward = root / "awkward"
edge = ["bom-crlf", "dotted-names", "empty-cells", "identical-rows", "mcd-tie", "numeric-like", "quoted-values",
        "single-condition", "single-decision", "single-row", "unicode", "whitespace"]
expected = [root / "small.csv", root / "large.csv"] + [awkward / f"{name}.csv" for name in
            ["wide-500", "wide-2000", "names", "quoted-newline", "bom-crlf", "empty-cells"]] + \
           [awkward / f"edge-{name}.csv" for name in edge]
problems = [f"missing {path}" for path in expected if not path.is_file()]


def header(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return next(csv.reader(f))


def records(path):
    with open(path, newline="", encoding="utf-8-sig") as f:
        return list(csv.reader(f))


if not problems:
    size = (root / "large.csv").stat().st_size
    if not 0.95 * 2**30 <= size <= 1.1 * 2**30:
        problems.append(f"large.csv is {size} bytes, not 0.95-1.1 GiB")
    if len(header(root / "large.csv")) != 20:
        problems.append("large.csv does not have 20 columns")
    if len(header(awkward / "wide-500.csv")) != 500:
        problems.append("wide-500.csv does not have 500 columns")
    if len(header(awkward / "wide-2000.csv")) != 2000:
        problems.append("wide-2000.csv does not have 2,000 columns")
    rows = records(awkward / "quoted-newline.csv")
    if len(rows) != 4 or not any("\n" in value for row in rows for value in row):
        problems.append("quoted-newline.csv should parse into a header and 3 records, one value with a line break")

print("\n".join(problems) if problems else f"inputs ok: {len(expected)} files")
sys.exit(1 if problems else 0)
