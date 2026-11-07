# RSES 2.2 file formats: overview

Source: *RSES 2.2 User's Guide*, Warsaw University, 19 January 2005, Appendix A "Selected RSES 2.2 file formats" (pp. 81–86) and section 3.3 "Rule sets" (pp. 34–36). A copy is in the repo root: [`RSES_2.2_User’s_Guide.pdf`](RSES_2.2_User’s_Guide.pdf). Original: <https://www.mimuw.edu.pl/~szczuka/rses/RSES_doc_eng.pdf> (older address: <http://logic.mimuw.edu.pl/~rses/RSES_doc_eng.pdf>).

RSES has not been developed since 2005; its successor is [Rseslib 3](https://github.com/awojna/Rseslib).

## General rules for all RSES files

- Extra spaces have no meaning; the guide adds them only for readability.
- Table names, attribute names and values may contain spaces only inside double or single quotes: `"serum cholestoral"` or `'serum cholestoral'`.
- Accepted character sets: ISO-8859-1 (Western) and ISO-8859-2 (Central European, Latin-2).

## Data set: `file_name.tab` (A.1)

```
TABLE therapy
ATTRIBUTES 5
 temperature numeric 1
 headache numeric 0
 cough symbolic
 catarrh symbolic
 disease symbolic
OBJECTS 4
 38.7    7       no  no  angina
 38.3    MISSING yes yes influenza
 MISSING 3       no  no  cold
 36.7    1       no  no  healthy
```

- `TABLE <name>`: unique name of the table within the RSES project.
- `ATTRIBUTES <n>`: number of columns, decision included. One line per attribute follows: `<name> symbolic`, or `<name> numeric <d>`, where `<d>` is the number of digits after the decimal point (`numeric 0` = integers).
- The decision attribute is the last attribute.
- `OBJECTS <m>`: number of rows. The rows follow, values in attribute order, separated by whitespace.
- Missing value: `MISSING`; `NULL` and `?` mean the same.

## Rule set: `file_name.rul` (A.3)

```
RULE_SET Demo
ATTRIBUTES 5
 temperature numeric 1
 headache numeric 0
 cough symbolic
 catarrh symbolic
 disease symbolic
DECISION_VALUES 4
angina
influenza
cold
healthy
RULES 16
(temperature=38.7)&(headache=7)=>(disease=angina[1]) 1
(temperature=38.3)&(headache=MISSING)=>(disease=influenza[1]) 1
(headache=7)&(cough=no)=>(disease=angina[1]) 1
(cough=yes)=>(disease=influenza[1]) 1
(catarrh=no)=>(disease={angina[1],cold[1],healthy[1]}) 3
...
```

- `RULE_SET <name>`: unique name of the rule set within the project.
- `ATTRIBUTES`: same format as in the data file. RSES checks that a data set has identical attributes before applying the rules to it.
- `DECISION_VALUES <k>`: the admissible values of the decision attribute (the last attribute), one per line.
- `RULES <r>`: number of rules; one rule per line follows (a line wrapped in the guide's listing is a single line in the file).

Each rule line:

```
(attribute_1=value_1)&(attribute_2=value_2)=>(decision_attribute=decision_value[support]) support
```

A generalized rule has several decisions, with the support of each decision value in brackets and the total support at the end:

```
(catarrh=no)=>(disease={angina[1],cold[2],healthy[1]}) 4
```

**Support** (A.3): "By supporting we understand the objects in training data that match at the conditional part of rule and have the decision identical with that suggested by rule. The number of supporting objects is written at the end of each rule."

The rule-set view in the RSES GUI (3.3) has a **Match** column, "number of objects from training table matching the conditional part of the rule (support of the rule)", which counts the conditional part only. For a rule with a single decision on consistent data, the two numbers are equal.

Default extensions: `.tab` data, `.red` reducts, `.rul` rules. (A.3 says "file containing reducts is `.rul`", a typo in the guide.)

## Reduct set: `file_name.red` (A.2), for reference

```
REDUCTS (5)
{ temperature, headache } 1.0
{ cough } 0.25
```

One reduct per line: its attributes between `{` and `}`, then the positive region of the table reduced to those attributes plus the decision (`1.0` = consistent).

## What this project's legacy `rses` output writes

`ToStringParser.buildStringRSES`, e.g. `src/test/resources/files/paper-sample-rules.rul`:

```
(f2=1)=>(f1=0[1]) 1
(astigmatic=1)&(tear-production-rate=2)&(age=1)=>(contact-lenses=2[2]) 2
```

- Rule lines only, in the RSES line format. There is no `RULE_SET`, `ATTRIBUTES`, `DECISION_VALUES` or `RULES` header, so RSES cannot load the file as is.
- Support follows the RSES definition above (`GreedyUtils.calculateSupport`: rows matching the conditions and the decision). After the most-common-decision step every rule has exactly one decision, so the generalized form never occurs and support equals Match.
- `is` mode (information system) writes the rules of every decision attribute into one file; an RSES rule set has a single decision attribute, the last one.
- Values with spaces are not quoted, empty cells are written as `null` rather than `MISSING`, the file is UTF-8 rather than ISO-8859-1/2, and rule order is arbitrary (hash-based).
- The csvm output (`-wmetadata.csv`) calls the same number "coverage".

Planned changes (`PLAN.md`, behavior-change registry):

- **#8:** full RSES header, quoted names and values with spaces, one rule set per decision attribute (zip in `is` mode), RSES-compatible encoding; the legacy format stays available as an option.
- **#9:** `MISSING`, `NULL` and `?` handled as missing values.
