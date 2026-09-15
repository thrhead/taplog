# TapLog Agent Instructions

## Source of Truth

Product requirements:
`docs/product/TapLog_V1_PRD.md`

The PRD defines product behavior. Do not silently introduce product behavior that contradicts or extends it.

Spec Kit artifacts are the canonical feature specifications, implementation plans, and task lists.

## Tool Responsibilities

### Spec Kit

Use Spec Kit for:
- project constitution
- feature specifications
- requirement clarification
- technical plans
- task decomposition
- spec/plan/task consistency analysis
- implementation convergence

Do not create competing spec or implementation-plan artifacts when a corresponding Spec Kit artifact already exists.

### Superpowers

Use Superpowers for:
- architectural brainstorming and design reasoning
- test-driven development
- systematic debugging
- isolated worktrees when appropriate
- code review
- verification before completion

When a Spec Kit spec, plan, or task artifact exists, treat it as canonical instead of creating a competing implementation specification.

### Codex

Codex is the lead engineering agent.

Use Codex for:
- architecture
- domain modeling
- module boundaries
- persistence/database architecture
- Event Engine
- NFC
- Android lifecycle/background execution
- concurrency
- encryption/security
- parser architecture
- cross-cutting refactors
- difficult debugging
- code review
- final verification

### Antigravity

Antigravity is a bounded implementation worker.

Antigravity does not manage the Spec Kit workflow.

It may read Spec Kit artifacts and implement clearly scoped tasks.

Use Antigravity for:
- repetitive tests
- fixtures/sample data
- mechanical mappings
- resources/string work
- straightforward UI components
- previews
- formatting/lint fixes
- documentation synchronization
- bounded low-risk implementation
- repetitive boilerplate

Antigravity must not independently change:
- product behavior
- architecture
- public contracts
- domain semantics
- database schema
- persistence strategy
- security behavior
- module boundaries
- cross-module APIs

If a task requires one of these changes, return it to Codex.

## Development Rules

- Do not implement the entire PRD as one task.
- Work feature-by-feature.
- Work on one independently verifiable implementation task at a time.
- Domain and application behavior should be test-driven.
- Do not declare completion without executing verification commands.
- Do not silently modify the PRD to match implementation.
- Prefer clarification over guessing product behavior.
- Future Scope items are not part of V1 unless explicitly promoted.
- Implementation decisions must remain traceable to the PRD and approved architecture.
