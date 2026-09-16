# Foundation daemon investigation — 2026-09-16

Scope: `002-android-foundation`, starting at commit `fa43250`. This is a
verification record, not a replacement specification or implementation plan.

## Confirmed failure mechanism

The Gradle daemon receives an externally delivered `SIGTERM` while the build is
still running. The traced original configuration produced:

```text
149531 --- SIGTERM {si_signo=SIGTERM, si_code=SI_USER, si_pid=0, si_uid=61876} ---
147149 +++ exited with 143 +++
```

Thread 149531 belongs to daemon 147149. The build user is UID 1000. The signal
sender is not identifiable from the container's PID namespace. The daemon's
shutdown hook runs; the client then reports:

```text
FAILURE: Build failed with an exception.

* What went wrong:
Gradle build daemon disappeared unexpectedly (it may have been killed or may have crashed)
```

This is not evidence of a D8 exception or an intrinsic JVM crash. The original
2 GiB control also completed dexing and `:app:assembleDebug`, then terminated
during lint. No `hs_err_pid`, heap-dump, or replay crash artifact was found.
Visible cgroup `oom` and `oom_kill` counters remained zero. Kernel logs and
ancestor-namespace process inspection are unavailable, so the external sender's
identity and termination policy remain unknown.

## Resource hypothesis and minimal experiment

The hypothesis was that host memory pressure leads an external actor to terminate
the build daemon. The host has approximately 7.8 GiB RAM and no swap. The original
control's last sample had 829,088 KiB `MemAvailable`, daemon RSS 1,772,804 KiB, and
a Kotlin process RSS 497,768 KiB. Existing unrelated Java processes also consume
memory; they were not stopped. Visible cgroup `memory.max` is unlimited, which
does not establish that the enclosing host has no resource policy.

Only `org.gradle.jvmargs`'s heap ceiling was changed for the experiment, from
`-Xmx2048m` to `-Xmx1024m`. JDK 17, SDK, Gradle, dependencies, workers, task set,
and architecture were unchanged. Both 1 GiB experiments completed the full lane.
The original 2 GiB crossover failed again. These results support a smaller build
resource budget as a mitigation, not a claim that Java threw `OutOfMemoryError`
or that the external policy has been identified. Heap ceiling is not an RSS
ceiling: the successful repeat still reached roughly 1.6 GiB daemon RSS.

| Run | Heap | Outcome | Complete output and resource samples |
| --- | --- | --- | --- |
| Baseline | 2048 MiB | Exit 1, daemon disappears near dexing | `/tmp/taplog-debug-baseline/` |
| Signal-traced reproduction | 2048 MiB | Exit 1; external SIGTERM, daemon exit 143 | `/tmp/taplog-debug-traced/`; `/tmp/taplog-debug-signal-trace.log` |
| Minimal experiment | 1024 MiB, CLI override | Exit 0; 1m 51s; 102 actionable tasks | `/tmp/taplog-debug-heap1024/` |
| Original-configuration crossover | 2048 MiB | Exit 1 after assembly, during lint | `/tmp/taplog-debug-control2048/` |
| Experiment repeat | 1024 MiB, CLI override | Exit 0; 1m 24s; 102 actionable tasks | `/tmp/taplog-debug-heap1024-repeat/` |
| First persisted-setting verification | 1024 MiB | Exit 1 during lint; confounded by concurrent dependency-report daemon | `/tmp/taplog-debug-final/` |
| Isolated persisted-setting verification | 1024 MiB, no CLI override | Exit 0; 1m 26s; 102 actionable tasks | `/tmp/taplog-debug-final-sequential/` |

The concurrent dependency-report run was an investigation mistake, not a valid
isolated confirmation. Only its newly created idle daemon (PID 164319) was
terminated after the dependency report completed. No unrelated daemon was killed.
The exact foundation command was rerun alone and passed with the persisted setting.
The earlier failures were not erased or treated as passing verification.

Every run directory contains the unabridged `gradle.log`, `environment.log`,
`resources.log`, and `exit-status`. Daemon logs remain under
`/tmp/taplog-task-25-gradle/daemon/9.6.0/` (baseline 143151, traced 147149,
experiment 152260, crossover 155207, repeat 159674, first persisted run 162649).
The isolated persisted-setting run used daemon 165773; its log confirms JDK 17
and `-Xmx1024m` without a command-line JVM override.
These raw logs are temporary local artifacts, not committed repository files.

## Reproduction environment and commands

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export GRADLE_USER_HOME=/tmp/taplog-task-25-gradle
unset GRADLE_OPTS
./gradlew --version
./gradlew --no-daemon clean foundationCheck
./gradlew :core:dependencies :data:dependencies :app:dependencies
```

The writable cache and host execution are needed for this agent sandbox, not a
change to the contributor command contract. Java 17.0.20 is installed here; the
earlier T025 claim that JDK 17 was unavailable was incorrect. Wrapper verification
now exits 0 and reports Gradle 9.6.0 with launcher and daemon JDK 17. The dependency
command exits 0 (`BUILD SUCCESSFUL in 29s`, three tasks executed).

The signal capture launched the diagnostic build as a child of `strace`:

```sh
strace -f -e trace=none -e signal=SIGTERM,SIGINT,SIGHUP,SIGQUIT,SIGKILL \
  -o /tmp/taplog-debug-signal-trace.log \
  bash /tmp/taplog-debug-run.sh /tmp/taplog-debug-traced
```

The single-variable test appended
`'-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8'` to the exact foundation
task command. Persisted-setting validation must not use that override.

## Boundaries

No architecture, product behavior, module dependency, task wiring, dependency
version, SDK version, worker count, or test assertion was changed. The proposed
build change is only the daemon heap ceiling in `gradle.properties`.
Device/emulator acceptance and the five build-install-launch attempts have not
been executed. A non-device build success does not establish full feature
acceptance. Spec Kit converge has not been run during this investigation.

## Final verification and disposition

- `./gradlew --version`: exit 0; Gradle 9.6.0, JDK 17.0.20.
- `./gradlew --no-daemon clean foundationCheck`: exit 0, `BUILD SUCCESSFUL in
  1m 26s`; 102 actionable tasks, 96 executed and six up-to-date. Assembly, all
  three JVM test tasks, both lint tasks, and Detekt/formatting are included.
- Test XML contains one test per module, zero failures/errors/skips.
- `./gradlew :core:dependencies :data:dependencies :app:dependencies`: exit 0;
  dependency reports retain the approved graph; the `:core` report has no Android
  artifact. Android test configurations' references to their own module are not
  additional production dependency edges.
- `git diff --check`: passes.
- Independent read-only re-review found no remaining Critical/Important issue.
  Detekt was `UP-TO-DATE` in the final run; it was included, not freshly executed.

The device-independent verification prerequisite for Spec Kit converge is now
met. Converge itself and device acceptance remain unexecuted. The confirmed
external signal is the failure mechanism; memory pressure is the tested trigger
hypothesis, not a proven host policy. The smaller heap is a verified mitigation
in an isolated run, not a guarantee against future external termination under
other host workloads. No dependency/toolchain incompatibility or dexing defect
was established, so no such code or version was changed.

## Interpretation references

- [Gradle build environment](https://docs.gradle.org/current/userguide/config_gradle.html):
  `org.gradle.jvmargs` configures the build VM, not merely the wrapper client.
- [Linux PID namespaces](https://man7.org/linux/man-pages/man7/pid_namespaces.7.html):
  ancestor processes are not visible from a child namespace; signal sender PID
  information has namespace limitations. The trace does not identify a specific
  Codespaces supervisor.
