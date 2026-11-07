#!/usr/bin/env python3
"""List the golden inputs whose legacy output depends on a most-common-decision (MCD) tie.

Usage:
    python3 -I golden/scripts/find_mcd_ties.py --inputs golden/inputs --out golden/outputs/mcd-ties.json

The legacy MCD step (DecisionTableFactory.removeInconsistenciesMCD) replaces each group of rows with equal
conditions and different decisions by one row with the group's most common decision, counted over the whole
table. When two of the group's decisions share the highest count, Spark's sort order picks the winner, so the
golden output for that input and mode may differ from run to run (registry #2). The double golden run only
catches the ties that happened to resolve differently; this script finds all of them from the inputs alone.

It models the legacy semantics on Spark 2.1:
- the CSV is read with a header, every value a string, an empty cell is null (here: None);
- duplicate column names become name + position, empty names _c + position (Spark 2.1 CSV reader);
- "dt" mode is one table with the last column as decision; "is" mode is one table per column, moved last;
- rows are de-duplicated first (dropDuplicates), and decisions are counted on the de-duplicated rows;
- a group is inconsistent when more than one de-duplicated row shares its conditions, but rows with a null
  condition never take part (the equi-join on conditions does not match null);
- null decisions are dropped from a group's decision set (collect_set);
- the winner is the first decision, in count-descending order, that is in the group's set: a tie matters
  only when two or more of the set's decisions share the set's highest count.
Values are compared as strings; quoting differences between Python's and Spark's CSV readers do not change
which values are equal.
"""
import argparse
import collections
import csv
import json
import pathlib


def read_table(path):
    text = path.read_text(encoding="utf-8")
    if not text.strip():
        return None, []
    rows = list(csv.reader(text.splitlines()))
    header, data = rows[0], [row for row in rows[1:] if row]
    names = []
    for position, name in enumerate(header):
        if name == "":
            names.append(f"_c{position}")
        elif header.count(name) > 1:
            names.append(f"{name}{position}")
        else:
            names.append(name)
    return names, [[value if value != "" else None for value in row] for row in data]


def decision_tables(names, rows, mode):
    if mode == "dt":
        return [(names, rows)]
    tables = []
    for decision in range(len(names)):
        order = [c for c in range(len(names)) if c != decision] + [decision]
        tables.append(([names[c] for c in order], [[row[c] for c in order] for row in rows]))
    return tables


def ties_in(names, rows):
    unique = list(dict.fromkeys(tuple(row) for row in rows))
    counts = collections.Counter(row[-1] for row in unique)
    groups = collections.defaultdict(list)
    for row in unique:
        groups[row[:-1]].append(row[-1])
    ties = []
    for conditions, decisions in groups.items():
        if len(decisions) < 2 or any(value is None for value in conditions):
            continue
        candidates = sorted({d for d in decisions if d is not None})
        if len(candidates) < 2:
            continue
        top = max(counts[d] for d in candidates)
        tied = [d for d in candidates if counts[d] == top]
        if len(tied) > 1:
            ties.append({
                "decisionAttribute": names[-1],
                "conditions": dict(zip(names[:-1], conditions)),
                "tiedDecisions": tied,
                "count": top,
            })
    return ties


def render(input_modes):
    """JSON with one tie per line: public/car alone has thousands of tied groups."""
    def text(value):
        return json.dumps(value, ensure_ascii=False)

    lines = [
        "{",
        '  "generatedBy": "golden/scripts/find_mcd_ties.py",',
        '  "description": ' + text("Input-modes whose legacy output depends on a most-common-decision tie "
                                   "(registry #2). A superset of the input-modes in nondeterministic.json.") + ",",
        '  "inputModes": [',
    ]
    for i, input_mode in enumerate(input_modes):
        lines.append(f'    {{"input": {text(input_mode["input"])}, "mode": {text(input_mode["mode"])}, "ties": [')
        ties = input_mode["ties"]
        lines += ["      " + text(tie) + ("," if j < len(ties) - 1 else "") for j, tie in enumerate(ties)]
        lines.append("    ]}" + ("," if i < len(input_modes) - 1 else ""))
    lines += ["  ]", "}"]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--inputs", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path)
    args = parser.parse_args()

    input_modes = []
    for path in sorted(args.inputs.rglob("*.csv"), key=lambda p: p.relative_to(args.inputs).as_posix()):
        input_id = path.relative_to(args.inputs).as_posix()[: -len(".csv")]
        names, rows = read_table(path)
        if names is None:
            continue
        for mode in ("dt", "is"):
            ties = [tie for table in decision_tables(names, rows, mode) for tie in ties_in(*table)]
            if ties:
                input_modes.append({"input": input_id, "mode": mode, "ties": ties})

    args.out.write_text(render(input_modes), encoding="utf-8")
    print(f"{len(input_modes)} input-modes with MCD ties written to {args.out}")


if __name__ == "__main__":
    main()
