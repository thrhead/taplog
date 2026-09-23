# Management Application Contract

This is an internal :app contract between management use cases/state holders and the existing :core/:data seams. It is not a new persistence or domain contract.

## Dependencies

- Read current DomainState through a narrow :data-owned application port backed by the approved local aggregate read.
- Invoke existing/compatibly extended core management operations through the existing EventEngine boundary.
- Use DeletionScope.preview/confirm and deleteScope(recordId, targetId?, confirmed) for permanent deletion.

## Mutation outcome

| Core result | Application state | UI behavior |
| --- | --- | --- |
| Applied | Success plus revisions/effects | Refresh from persisted state; show localized success. |
| NeedsConfirmation | ConfirmationRequired plus reason/impact context | Show irreversible confirmation; do not commit. |
| Conflict | Conflict plus stable reason | Refresh current state; explain stale context; never overwrite. |
| Invalid | Invalid plus stable reason | Keep form/selection; show localized field or action error. |
| StorageFailure | StorageError | Treat state as unchanged; show retry-safe error; no false success. |

Raw exceptions are not user-facing result text.

## Intent rules

- Archive, unarchive, link, unlink, and relink execute directly after intent validation.
- Permanent deletion has two calls: read-only preview, then explicit confirmed mutation. Cancel, dismissal, or absent confirmation performs no commit.
- The app supplies ExpectedContext from displayed entity/relationship and dataset state where supported.
- The app never performs a direct DAO write or derives lifecycle effects, deletion impact, snapshots, or revision values.

## Read states

Reads expose Loading, Empty, Content, and typed StorageError. Active and archived filters are explicit query parameters; no hidden deletion or auto-rebind occurs during refresh.
