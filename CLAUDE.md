# Sift

Android call-screening app. Blocks unknown callers on the first attempt; allows them through on the second — because real people call back. 100% on-device, no external data sent.

## Architecture

Clean Architecture + MVVM. Three layers: `domain` (business logic, no Android imports), `data` (Room, ContactsProvider, DataStore), `presentation` (Compose + ViewModels). Dependency rule: `presentation → domain ← data`.

Key components: `IncomingCallScreeningService` → `EvaluateIncomingCallUseCase` → `ContactsRepository` + `CallAttemptRepository` + `SettingsRepository`.

Decision flow: in contacts? → ALLOW. In manual blacklist? → DISALLOW. In manual whitelist? → ALLOW. First attempt? → DISALLOW silently + log. Second attempt (configurable)? → ALLOW.

Stack: Kotlin, Jetpack Compose, Hilt, Room, DataStore, libphonenumber, JUnit5, MockK, Turbine, JaCoCo, Gherkin + Cucumber-JVM + Appium. Min SDK 29.

## Non-negotiable rules

1. **Never block a number that is in the device contacts.**
2. **Language:** all development in English — identifiers, KDoc, comments, commits, PR titles/descriptions/reviews. Governance docs (`.md` files, `.claude/`) stay in Spanish.
3. **Commits:** Conventional Commits v1.0.0, atomic (one logical change each), imperative English description. No umbrella commits.
4. **Merge:** rebase + fast-forward only. No squash, no merge commits.
5. **Coverage:** ≥80% on `domain`/`data` via JaCoCo — CI gate, not a goal.
6. **KDoc:** every public function (and non-trivial private one) needs full KDoc (`@param`, `@return`, `@throws`). Outdated KDoc = bug.
7. **No unnecessary comments:** if the KDoc or the name already explains it, no inline comment. Comments explain *why*, never *what*.
8. **Complexity limits:** max 40 lines/function, cyclomatic complexity ≤10, max 5 params, nesting ≤3.
9. **No anti-patterns:** no god classes, no empty catches, no business logic in UI layer, no dead code.
10. **Privacy:** 100% local logic. Never commit secrets, keystores, real phone numbers, or real contacts.

## Agents (`.claude/agents/`)

8 specialized subagents, each with scoped tools and responsibilities. Read `AGENTS_WORKFLOW.md` for the full roster. Key rule: **do not do another agent's job** — if you spot something outside your scope, report it.

## Phases (F0 → F6)

F0 Setup → F1 Call Screening Core → F2 Attempt History → F3 Manual Lists → F4 UI → F5 Onboarding → F6 Release.

Each phase requires: code + unit tests (≥80% coverage) + E2E scenarios + QA certification + `code-review-agent` gate + Orchestrator merge + docs updated.

## Reference docs

| Doc | What it covers |
|---|---|
| `PROJECT_CONTEXT.md` | Goals, scope, product rules, risks |
| `ARCHITECTURE.md` | Layers, stack, decision flow, roadmap |
| `DEVELOPMENT_STANDARDS.md` | Git, commits, PRs, CI/CD, coverage |
| `CODE_QUALITY_STANDARDS.md` | SOLID, complexity, KDoc, anti-patterns |
| `AGENTS_WORKFLOW.md` | Agent roster, branches, pipeline, branch protection |
| `LOCAL_AUTOMATION_SETUP.md` | Auth, per-agent commit identity, worktrees, orchestration |
