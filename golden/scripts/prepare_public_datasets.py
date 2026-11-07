#!/usr/bin/env python3
"""Convert raw UCI dataset files into golden-corpus CSVs: a header row, values unchanged, decision attribute last.

Usage:
    python3 -I golden/scripts/prepare_public_datasets.py --source-dir DIR --out golden/inputs/public

DIR holds the unpacked UCI archives (any subdirectories): lenses.data, zoo.data, car.data, monks-1.train,
monks-2.train, monks-3.train. Sources and checksums are listed in golden/README.md.
"""
import argparse
import csv
import pathlib
import sys


def find(source_dir, name):
    matches = sorted(source_dir.rglob(name))
    if not matches:
        sys.exit(f"missing {name} under {source_dir}")
    return matches[0]


def data_lines(path):
    return [line for line in path.read_text(encoding="ascii").splitlines() if line.strip()]


def write(out_dir, name, header, rows):
    path = out_dir / f"{name}.csv"
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(rows)
    print(f"{path}: {len(rows)} rows")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source-dir", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    # lenses.data: whitespace-separated "id age prescription astigmatic tear-rate class"; drop id
    write(args.out, "lenses",
          ["age", "spectacle-prescription", "astigmatic", "tear-production-rate", "contact-lenses"],
          [line.split()[1:] for line in data_lines(find(args.source_dir, "lenses.data"))])

    # zoo.data: "animal_name,<16 attributes>,type"; drop animal_name
    write(args.out, "zoo",
          ["hair", "feathers", "eggs", "milk", "airborne", "aquatic", "predator", "toothed", "backbone",
           "breathes", "venomous", "fins", "legs", "tail", "domestic", "catsize", "type"],
          [line.split(",")[1:] for line in data_lines(find(args.source_dir, "zoo.data"))])

    # car.data: already "<6 attributes>,class"
    write(args.out, "car",
          ["buying", "maint", "doors", "persons", "lug_boot", "safety", "class"],
          [line.split(",") for line in data_lines(find(args.source_dir, "car.data"))])

    # monks-N.train: space-separated "class a1 a2 a3 a4 a5 a6 id"; drop id, move class last
    for n in (1, 2, 3):
        rows = []
        for line in data_lines(find(args.source_dir, f"monks-{n}.train")):
            parts = line.split()
            rows.append(parts[1:7] + [parts[0]])
        write(args.out, f"monks-{n}", ["a1", "a2", "a3", "a4", "a5", "a6", "class"], rows)


if __name__ == "__main__":
    main()
