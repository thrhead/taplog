# Android foundation final-review fix report

Date: 2026-09-16. Starting HEAD: `836beb8`.

## Scope and sources

Read the entire 1,286-line review package
`.superpowers/sdd/plan/review-c7b80fa..836beb8.diff`, together with canonical
`specs/002-android-foundation/spec.md`, `plan.md`, and `tasks.md`, the current
workflow, Dockerfile, README, root build, verification contract, and original
T025 report/brief. No subagents were dispatched. No merge or push was performed.

This change addresses the three requested foundation review findings only.
PRD, spec, plan, module/build behavior, dependency versions, and device-lane
requirements are unchanged. Review-reception and systematic-debugging guidance
informed the root-cause checks; verification guidance required fresh evidence
before committing.

## Files and fixes

1. `.github/workflows/android-foundation.yml`: replaced the invalid
   setup-android and wrapper-validation pins with the immutable commits for
   the exact requested releases. Nearby `# v3.2.1` and `# v4.0.1` comments are
   preserved. `contents: read`, trigger filters, concurrency, timeout, JDK 17,
   SDK packages, verification command, and failure-only artifacts are unchanged.
2. `.devcontainer/Dockerfile`: removed the chain-wide `|| true`. SDK ownership
   is required for the intended `vscode` contributor environment, so it fails
   normally too; there is no explicitly optional operation to tolerate. Added
   curl's HTTP failure flag and exposed license/ownership stderr. The JDK 17
   paths/package, command-line tools revision `11076708`, platform 36, Build
   Tools 36.0.0, and platform-tools provisioning remain unchanged. Docker uses
   POSIX shell pipeline status from `sdkmanager` for license acceptance; adding
   pipefail would unnecessarily treat an expected early exit of `yes` as fatal.
3. `README.md`: identified version and dependency reports as Codespaces/local
   contributor checks, and accurately stated that CI validates the wrapper and
   runs `./gradlew --no-daemon clean foundationCheck`. Added a final newline.
4. `specs/002-android-foundation/contracts/verification-contract.md`: replaced
   `:data:test` with the actual `:data:testDebugUnitTest` dependency and named
   the two debug lint tasks and Detekt/formatting lane explicitly. No new task
   or behavior was introduced.
5. `specs/002-android-foundation/tasks.md`: preserved T025's approved completion
   bookkeeping while adding its JDK 17 prerequisite exception and original
   command results directly beside T025 in tracked documentation. The original
   non-zero suite result is explicit; the checkbox does not imply passing
   foundation or device acceptance. This records execution history, not a
   product, scope, toolchain, or acceptance exception.
6. `.superpowers/sdd/plan/final-fix-report.md`: this report, intentionally
   force-staged despite the repository's `.superpowers/` scratch ignore rule.

## Action verification

The supplied package and local task reports contain the old pins and release
names, but no explicit reviewer replacement hashes. The exact named release
tags were resolved upstream and their commit/action definitions verified;
no floating tag is used in the workflow.

Commands (network checks required execution outside the network sandbox):

```sh
git ls-remote https://github.com/android-actions/setup-android.git refs/tags/v3.2.1
git ls-remote https://github.com/gradle/actions.git refs/tags/v4.0.1
curl -sSL -o /dev/null -w 'setup-android old pin: HTTP %{http_code}\n' https://api.github.com/repos/android-actions/setup-android/commits/b8db5e95450f3b063ee387ef93836d55734c5685
curl -sSL -o /dev/null -w 'gradle/actions old pin: HTTP %{http_code}\n' https://api.github.com/repos/gradle/actions/commits/d156388eb19633375bd4d97184ea5f03828904d1
curl -fsSL https://api.github.com/repos/android-actions/setup-android/commits/00854ea68c109d98c75d956347303bf7c45b0277 | jq -r '[.sha, .commit.message] | @tsv'
curl -fsSL https://api.github.com/repos/gradle/actions/commits/16bf8bc8fe830fa669c3c9f914d3eb147c629707 | jq -r '[.sha, .commit.message] | @tsv'
curl -fsSL https://raw.githubusercontent.com/android-actions/setup-android/00854ea68c109d98c75d956347303bf7c45b0277/action.yml
curl -fsSL https://raw.githubusercontent.com/gradle/actions/16bf8bc8fe830fa669c3c9f914d3eb147c629707/wrapper-validation/action.yml
```

Key output:

```text
00854ea68c109d98c75d956347303bf7c45b0277 refs/tags/v3.2.1
16bf8bc8fe830fa669c3c9f914d3eb147c629707 refs/tags/v4.0.1
setup-android old pin: HTTP 422
gradle/actions old pin: HTTP 422
00854ea68c109d98c75d956347303bf7c45b0277 Update dependencies and rebuild
16bf8bc8fe830fa669c3c9f914d3eb147c629707 Rework docs for Develocity support\n\nFixes #339
```

Both replacement commit API requests returned HTTP 200. The pinned definitions
exist and declare Node 20: setup-android runs `dist/index.js` and supports the
existing `packages` input; wrapper-validation runs
`../dist/wrapper-validation/main/index.js`. All five workflow action references
were inspected and remain full 40-character SHAs with release comments.
Initial sandbox `git ls-remote` attempts exited 128 (DNS blocked); the network
retry exited 0. A browser API attempt could not open the endpoints, so the
terminal verification above supplied the evidence.

## Dockerfile and shell verification

Commands:

```sh
docker version
docker build --check -f .devcontainer/Dockerfile .devcontainer
awk '/^RUN / {line=$0; while (sub(/\\$/, "", line)) {getline nextline; line=line nextline} sub(/^RUN /, "", line); print line}' .devcontainer/Dockerfile | sh -n
node /tmp/taplog-final-docker-check.cjs --baseline
node /tmp/taplog-final-docker-check.cjs
```

Docker access initially failed on the sandbox socket permission; the permitted
retry confirmed Docker client/server 29.7.2-2. Docker `--check` exited 0:

```text
#1 [internal] load build definition from Dockerfile
#1 transferring dockerfile: 1.43kB 0.0s done
#2 [internal] load metadata for mcr.microsoft.com/devcontainers/base:ubuntu-24.04
#2 DONE 0.4s
#3 [internal] load .dockerignore
Check complete, no warnings found.
```

The corrected continuation-aware awk extraction passed `sh -n` (exit 0, no
output). An earlier exploratory extraction that removed continuations without
joining lines produced `sh: 2: Syntax error: "&&" unexpected`; that was a
checker error, not a Dockerfile error, and is superseded by the checks above.

The Node harness extracts the actual two RUN commands, checks each with `sh -n`,
and executes the real SDK shell operator chain with external operations replaced
by harmless shell functions. It independently injects exit 42 at mkdir,
download, unzip, move, temporary-archive cleanup, license acceptance, SDK package
installation, and ownership. It performs no real filesystem mutation or network
operation. The baseline is read with `git show 836beb8:.devcontainer/Dockerfile`;
the corrected file is read directly. No baseline checkout was performed.

Baseline output (harness exit 1):

```text
PASS: RUN 1 POSIX shell syntax
PASS: RUN 2 POSIX shell syntax
PASS: success: exit 0, expected 0
FAIL: mkdir: exit 0, expected 42
FAIL: curl: exit 0, expected 42
FAIL: unzip: exit 0, expected 42
FAIL: mv: exit 0, expected 42
FAIL: rm: exit 0, expected 42
FAIL: licenses: exit 0, expected 42
FAIL: packages: exit 0, expected 42
FAIL: chown: exit 0, expected 42
```

Corrected output (harness exit 0):

```text
PASS: RUN 1 POSIX shell syntax
PASS: RUN 2 POSIX shell syntax
PASS: success: exit 0, expected 0
PASS: mkdir: exit 42, expected 42
PASS: curl: exit 42, expected 42
PASS: unzip: exit 42, expected 42
PASS: mv: exit 42, expected 42
PASS: rm: exit 42, expected 42
PASS: licenses: exit 42, expected 42
PASS: packages: exit 42, expected 42
PASS: chown: exit 42, expected 42
```

Sandbox Node attempts stalled without output; the permitted host retry completed
and produced the evidence above. Shellcheck and actionlint were unavailable.
Docker `--check` parses/checks the Dockerfile but does not execute provisioning
or establish a successful clean Codespaces image build.

## Gradle verification

The default `java -version` still reports JDK 25, but the approved JDK is now
available at `/usr/lib/jvm/java-17-openjdk-amd64` (OpenJDK 17.0.20). Platform 36
and executable Build Tools 36.0.0 `aapt2` were also confirmed. This new environment
observation does not rewrite the original T025 exception.

All new wrapper checks explicitly use these environment values:

```sh
GRADLE_USER_HOME=/tmp/taplog-task-25-gradle
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ANDROID_HOME=/opt/android-sdk
```

`./gradlew --version` exited 0:

```text
Gradle 9.6.0
Kotlin:        2.3.21
Launcher JVM:  17.0.20 (Ubuntu 17.0.20+8-1-24.04-Ubuntu)
Daemon JVM:    /usr/lib/jvm/java-17-openjdk-amd64
```

`./gradlew --offline --no-daemon foundationCheck` exited 1 in the sandbox:

```text
Gradle could not start your build.
Could not determine a usable wildcard IP for this machine.
```

The permitted host retry, `./gradlew --offline --no-daemon clean foundationCheck`,
also exited 1:

```text
Remove shutdown hook failed
java.lang.IllegalStateException: Shutdown in progress
Invalid Java installation found at '/usr/local/sdkman/candidates/java/25.0.4+1-ms' (SDKMAN!) auto-detected.
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
```

Its daemon log explicitly reports Java 17 and the intended JAVA_HOME. The
output does not establish why the daemon terminated or that JDK probing was
the cause.

A safe host task-graph check disabled automatic JDK probing only for the command:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk-amd64 foundationCheck --dry-run
```

Exit 0, `BUILD SUCCESSFUL in 22s`. Relevant output:

```text
:detekt SKIPPED
:app:assembleDebug SKIPPED
:app:lintDebug SKIPPED
:app:testDebugUnitTest SKIPPED
:core:test SKIPPED
:data:lintDebug SKIPPED
:data:testDebugUnitTest SKIPPED
:foundationCheck SKIPPED
```

All tasks are SKIPPED because this is a dry run, not passing execution of tests,
lint, compilation, or Detekt. Gradle also reported deprecated features that
will be incompatible with Gradle 10; no toolchain upgrade is authorized here.

## Repository consistency verification

Commands:

```sh
git diff --check
git diff --stat
git diff -- .devcontainer/Dockerfile .github/workflows/android-foundation.yml README.md specs/002-android-foundation/contracts/verification-contract.md specs/002-android-foundation/tasks.md
rg -n 'uses:|contents: read|run:|java-version:|packages:|if: failure' .github/workflows/android-foundation.yml
rg -n 'Codespaces / Local|GitHub Actions validates|:data:testDebugUnitTest|:data:lintDebug|T025 verification exception|JDK 17 was unavailable' README.md specs/002-android-foundation/contracts/verification-contract.md specs/002-android-foundation/tasks.md
git diff --quiet -- docs/product/TapLog_V1_PRD.md specs/002-android-foundation/spec.md specs/002-android-foundation/plan.md
```

Whitespace and unchanged-PRD/spec/plan checks exited 0 with no output. The scans
show `contents: read`, unchanged failure-only uploads, the two corrected release
pins, the single CI Gradle suite command, contributor-only reports, the exact
debug test/lint task names, and the tracked T025 exception. The reviewed diff
contains five implementation/documentation files plus this report; it changes
no app/core/data source or build definition.

## Concerns and handoff

- The explicit reviewer replacement hashes were absent from the supplied
  package; the implemented hashes are the independently verified commits of
  the exact requested reviewed releases.
- A Dockerfile check and controlled failure injection do not replace a full
  clean devcontainer provisioning run or a hosted GitHub Actions run.
- No device tests or five-attempt launch acceptance were performed; those
  remain the canonical external device lane.
- The original T025 failure remains recorded, and the new plain JDK 17 suite
  retry also failed by daemon termination. No blanket foundation-pass claim
  follows from the successful version/task-graph checks.
- The changes are to be committed together after final verification. This
  report is part of that commit; obtain its SHA with
  `git log -1 --format='%h %s' -- .superpowers/sdd/plan/final-fix-report.md`.

## Final controlled suite result

Command, with the same explicit Gradle cache/JDK/SDK environment above:

```sh
./gradlew --offline --no-daemon --max-workers=1 -Porg.gradle.java.installations.auto-detect=false -Porg.gradle.java.installations.paths=/usr/lib/jvm/java-17-openjdk-amd64 clean foundationCheck --console=plain
```

Exit 1. It reached compilation and dexing, then the JDK 17 daemon disappeared:

```text
> Task :app:clean
> Task :core:clean
> Task :data:clean
> Task :detekt UP-TO-DATE
> Task :app:compileDebugKotlin
> Task :app:desugarDebugFileDependencies
> Task :app:mergeExtDexDebug
The message received from the daemon indicates that the daemon has disappeared.
Daemon pid: 132384
javaHome=/usr/lib/jvm/java-17-openjdk-amd64,javaVersion=17
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
```

The controlled retry also failed, so automatic JDK probing is not an established
root cause. No further suite/network retries are planned in this bounded fix
wave. The full suite remains unverified; the fixes themselves have the focused
action, Docker, shell-behavior, documentation, and Gradle task-graph evidence
above. No product/build changes were made to conceal the failure.

## Commit preparation

Only the six enumerated files are staged, including the requested ignored report.
Final gates are `git diff --cached --check`, review of
`git diff --cached --stat`, and unchanged PRD/spec/plan checks. The intended
commit message is `fix: address Android foundation final review findings`.

The staging commands completed with exit 0; `git diff --cached --check` produced
no output. `git diff --cached --name-only` returned exactly:

```text
.devcontainer/Dockerfile
.github/workflows/android-foundation.yml
.superpowers/sdd/plan/final-fix-report.md
README.md
specs/002-android-foundation/contracts/verification-contract.md
specs/002-android-foundation/tasks.md
```
