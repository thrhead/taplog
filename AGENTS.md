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

## Model Routing Policy

Select the cheapest model that is appropriate for the task, but prioritize
correctness over token cost.

### GPT-6 Astra

Use Astra for tasks with high architectural or systemic impact, including:

- greenfield system architecture
- major architecture changes
- cross-module design decisions
- difficult domain modeling
- specification conflicts with system-wide consequences
- concurrency architecture
- security/cryptography architecture
- persistence architecture with migration implications
- complex NFC/Android lifecycle architecture
- difficult root-cause analysis where multiple subsystems interact
- final architectural review before major implementation phases

Do not use Astra for routine implementation, boilerplate, formatting,
straightforward tests, or documentation cleanup.

### GPT-5.6 Sol

Use Sol for difficult engineering work that needs strong reasoning but does
not require project-wide architectural re-evaluation, including:

- complex implementation
- difficult debugging
- complex refactoring
- code review of high-risk changes
- database implementation
- NFC implementation
- parser/fuzzy-matching implementation
- lifecycle/background execution issues
- security-sensitive implementation
- difficult test failures
- verification of high-risk features

### GPT-5.6 Terra

Use Terra as the default model for normal engineering work, including:

- project constitution
- feature specifications
- implementation roadmaps
- normal feature implementation
- straightforward refactors
- Spec Kit task decomposition
- documentation
- normal tests
- ordinary reviews
- routine planning

### GPT-5.6 Luna

Use Luna only for low-risk, mechanical, or high-volume work, including:

- formatting
- boilerplate
- repetitive resource changes
- simple mappings
- repetitive test-case expansion
- fixtures/sample data
- documentation cleanup
- mechanical renames
- simple file transformations

Do not use Luna to make architecture, domain, persistence, API, security,
or product-behavior decisions.

### Antigravity

Prefer Antigravity instead of Codex/Luna for bounded, mechanical tasks when
the task is already precisely defined by an approved Spec Kit task.

Antigravity must follow the restrictions defined in the Antigravity section
of this file.

## Model Escalation Rules

If the currently active model is weaker than the recommended model for the
task:

1. Do not silently proceed with a high-impact decision.
2. State the recommended model before substantive work begins.
3. Explain in one short sentence why escalation is warranted.
4. Wait for the user to switch models if the task requires Astra or Sol.

If the currently active model is stronger than necessary, continuing is
allowed, but mention that a cheaper model would normally be sufficient when
starting a new independent task.

Never downgrade models in the middle of an unfinished reasoning chain solely
to save tokens.

When uncertain between two model tiers, use the stronger tier if an incorrect
decision would have high blast radius or be expensive to reverse.