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

<!-- graft:start -->
## Graft — repo context graph

This repo is indexed in `graft/`: small linked markdown nodes that explain each
system and carry exact file:line spans, kept in sync with the code through git.

For ANY task here — understanding how something works, finding where code lives,
or scoping a change — get context from the graph before grepping or opening
source files. Re-ask freely (it's cheap) and reuse literal identifiers you
already have (symbol, error string, file name) as the query. New to this repo?
Run `graft map` first — a token-budgeted orientation (dir clusters, hubs,
hotspots), no LLM, no key.

- Run `graft ask "<your question>" --source` → ranked nodes with the relevant
  code spans inlined (each hit's ≤8-line crux by default; `--full` for whole
  definitions when the crux isn't enough). Match the tool to the task shape:
  for understanding or editing, the top node IS the answer — cite its
  `covers:` file:line spans and edit straight from `--source`. For
  exhaustive tasks ("every occurrence / every caller of this pattern"), ranked
  results are top-N, not complete — run `graft grep "<literal>"` instead
  (exhaustive over indexed files, grouped by enclosing symbol), falling back
  to raw `grep -rn` only for unindexed files.
- `graft skeleton <file>` → every definition's signature + span, ~10× cheaper
  than reading the file; use it to skim an API surface.
- `graft callers <symbol>` gives precomputed, exact edges — who calls this.
  Add `--direction out` for what it calls, or `--depth N` to walk
  transitively for the full blast radius. For structural questions, skip
  ranking and use this directly.
- Or browse: `graft/INDEX.md` lists every node; follow the links.
- Monorepos and folders of multiple repos rank fairly across sub-projects —
  hits carry `[scope/]` labels naming which one they're from. Narrow with
  `graft ask "<task>" --in <scope>/` once you know where you're working.

If a returned span is truncated ("+N more lines"), open the file at that exact
range before finalizing. Only open source files when a node genuinely lacks a
needed detail, and then at the exact file:line the node points to — never
re-read whole files.

After big code changes, refresh the graph with `graft build` (deterministic,
no API key, $0).
<!-- graft:end -->
