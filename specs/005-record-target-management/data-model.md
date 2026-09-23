# Data Model: Record + Target Management

005 does not introduce a new database schema. It uses the domain and persistence entities already approved by 003/004 and adds application-state models that are not persisted.

## Persisted domain entities

| Entity | Management role | Rules carried into 005 |
| --- | --- | --- |
| Record | Reusable behavior definition | Immutable RecordId; name/icon; four behaviors; lifecycle; optional Counter unit/default quantity; optional State Group; monotonic revision; hasEvents locks behavior and Counter unit after first Event. |
| Target | Independent reusable context | Immutable TargetId; name/icon; lifecycle; monotonic revision. It can relate to many Records and cannot be standalone-permanently-deleted in 005. |
| RecordTarget | Record–Target eligibility | Pair identity; linked; independent monotonic revision. Unlink blocks future Events without deleting definitions/history. Relink sets true and increments revision only. |
| Event | Historical occurrence | Never rewritten by ordinary definition edit, archive, unarchive, unlink, or relink. Creation-time snapshots remain authoritative. |
| BindingLifecycle | Historical channel binding state | Archive/unlink may orphan and snapshot it through core lifecycle effects. Relink must not reactivate existing orphaned bindings. |
| StateScope / generations | State lifecycle context | Core updates/reset behavior; app only displays effects and does not compute generations. |
| UndoReceipt / invalidations | Lifecycle metadata | Core may invalidate on archive/unlink; app does not reconstruct or delete it independently. |
| DeleteImpact / DeleteConfirmation | Irreversible scope | Preview is read-only; confirmation is explicit; commit removes only core-provided scope. |

## Non-persisted application models

- ManagementSection: Records or Targets, with active/archived filter.
- RecordEditorState: name, icon, behavior, Counter unit, default quantity, State Group selection, field visibility/validation, and existing entity revision/context.
- TargetEditorState: name, icon, existing entity revision/context.
- AssignmentState: selected Record, active valid Targets, linked/unlinked relationship revisions, and distinct no-Target option.
- ManagementScreenState: Loading, Empty, Content, Confirmation, Success, Conflict, Invalid, StorageError; mutation states include stable core category/reason.
- DeleteConfirmationState: exact DeleteImpact counts/identifiers supplied by core, scope label, irreversible warning, and confirmation token/context. It is not itself authority to delete.

## Validation and transitions

1. Create Record: name and behavior are required; only behavior-valid fields are submitted; Counter default is positive and defaults to quantity 1 when omitted; State requires the existing State Group contract.
2. Edit Record: name/icon/default quantity may change per core; behavior and Counter unit changes after hasEvents=true are rejected; every accepted definition change increments only Record revision.
3. Create/edit Target: name is required; accepted changes increment only Target revision.
4. Archive/unarchive: lifecycle changes preserve identity/history and increment the affected definition revision. Core effects are atomically persisted; unarchive does not reverse terminal effects or orphaned bindings.
5. Link: active Record + active Target and a valid pair are required; link makes the pair eligible for future Events.
6. Unlink: core marks pair unavailable, increments relationship revision, and applies lifecycle/orphan/Undo effects. No confirmation is required.
7. Relink: only explicit user action changes an existing unlinked pair back to linked=true; historical Events, snapshots, orphaned bindings, state generations, and terminal statuses remain unchanged.
8. Delete: preview exact scope; no mutation until confirmation; null target is Record-wide and non-null target is pair-scoped; standalone Target deletion is absent.

## Invariants

- No-Target is targetId=null / TargetScope.NoTarget, never a synthetic Target.
- Revisions are monotonic per entity/relationship; no global mutationRevision is introduced.
- Active Event creation choices exclude archived Records, archived Targets, and unlinked pairs.
- Ordinary management never changes an Event identity or snapshot.
- A stale revision, dataset generation, lifecycle context, or relationship
  context cannot overwrite newer state. Command-level stale detection returns
  Conflict before commit; a commit-time Boolean rejection is surfaced as
  StorageFailure according to the approved 004 boundary.
- A committed operation survives restart; an interrupted operation cannot produce a partial aggregate.
