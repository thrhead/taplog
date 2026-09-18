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
