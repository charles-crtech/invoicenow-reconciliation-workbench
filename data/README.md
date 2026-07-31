# Synthetic datasets

Only small, reviewable evidence fixtures belong in source control.

- `smoke/v1/` is the committed, deterministic source-contract v1 bundle used by
  tests and early import development.
- `generated/`, `performance/`, `raw/`, and `uploads/` are ignored because they
  may be large, local, transient, or user supplied.

All repository-authored datasets are synthetic. Do not place personal,
customer, credential, production, or otherwise sensitive data in this tree.
Regenerate the smoke files from the versioned profile by following the
[generator guide](../generator/README.md).
