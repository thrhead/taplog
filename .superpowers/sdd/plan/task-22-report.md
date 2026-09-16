# Task 22 Audit Report

## Scope

Audited `gradle/libs.versions.toml` for fixed, centrally discoverable version declarations. No dependency or toolchain changes were made.

## Files changed

- `specs/002-android-foundation/tasks.md` — marked T022 complete.
- `.superpowers/sdd/plan/task-22-report.md` — added this report.

## Verification

Exact command run:

```bash
set -eu
printf '%s\n' 'COMMAND: awk -F... catalog version declarations'
awk '
  /^\[versions\]$/ { in_versions=1; next }
  /^\[/ { in_versions=0 }
  in_versions && /^[[:space:]]*[A-Za-z0-9._-]+[[:space:]]*=[[:space:]]*"[^"]+"[[:space:]]*$/ { print }
' gradle/libs.versions.toml
printf '%s\n' 'COMMAND: reject dynamic/range version declarations'
if rg -n --pcre2 '^[[:space:]]*[A-Za-z0-9._-]+[[:space:]]*=[[:space:]]*"[^"\r\n]*(\+|latest\.|\[[^\]]*\]|\([^)]*\)|\*|[<>]=?)[^"\r\n]*"[[:space:]]*$' gradle/libs.versions.toml; then
  echo 'FAIL: dynamic or range declaration found'
  exit 1
else
  echo 'PASS: no dynamic or range declarations found'
fi
printf '%s\n' 'COMMAND: reject inline dependency/plugin versions outside [versions]'
if rg -n --pcre2 'version[[:space:]]*=[[:space:]]*"' gradle/libs.versions.toml; then
  echo 'FAIL: inline dependency/plugin version found'
  exit 1
else
  echo 'PASS: dependency/plugin versions use version.ref or omit version for BOM-managed entries'
fi
```

Complete output:

```text
COMMAND: awk -F... catalog version declarations
agp = "9.4.0"
kotlin = "2.3.21"
ksp = "2.3.6"
gradle = "9.6.0"
composeBom = "2026.03.00"
activityCompose = "1.13.0"
junit = "4.13.2"
androidxTestRunner = "1.7.0"
androidxTestExtJunit = "1.3.0"
detekt = "1.23.8"
COMMAND: reject dynamic/range version declarations
PASS: no dynamic or range declarations found
COMMAND: reject inline dependency/plugin versions outside [versions]
PASS: dependency/plugin versions use version.ref or omit version for BOM-managed entries
```

Additional repository check: `git diff --check` passed after the changes.

## Status

DONE

## Commit

`f3f86d9` — `chore: audit version declarations`

## Concerns

None.
