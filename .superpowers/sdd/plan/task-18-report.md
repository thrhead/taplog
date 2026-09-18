# T018 implementation report

Added four pure JVM tests to `PersistenceMapperTest`:

- all four event behaviors round-trip, including a unitless positive Counter;
- completed and incomplete Duration payloads remain terminal, while malformed terminal rows fail with `MappingFailure`;
- State payload scope/generation round-trips and target-scope disagreement fails with `MappingFailure`;
- zero/nonnegative revisions and allocated sequences are accepted, while an out-of-bound sequence and negative revision fail with `MappingFailure`.

TDD evidence: the all-behaviors test was run once with an intentionally incorrect expected sequence and failed at the assertion, then passed after restoring the contract expectation. No production code or CAS behavior was changed.

Verification:

- `:data:testDebugUnitTest --tests io.github.thrhead.taplog.data.persistence.PersistenceMapperTest` — BUILD SUCCESSFUL
- `:data:testDebugUnitTest` — BUILD SUCCESSFUL

## Round 1 review fixes

Added duplicate/reused sequence rejection and explicit preservation assertions for valid sequence/revision values, plus State payload group/generation mismatch rejection and zero/negative persisted Counter quantity rejection. Cross-commit CAS remains out of scope.

The monotonic assertion was intentionally made incorrect once and failed at the expected assertion before being restored. Focused mapper tests and the complete data JVM suite both pass after the fixes.

## Round 2 review fix

Added a valid second State Group to the State mapper fixture. The mismatch mutation now points the persisted State payload at that existing group while the owning Record remains attached to the original group, proving payload/Record group disagreement rather than only missing-group rejection.
