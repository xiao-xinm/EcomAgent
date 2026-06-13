# CLAUDE.md

This file defines the working rules for Claude Code in the SmartCS-Agent project.

## Project Context

SmartCS-Agent is an AI customer service system for e-commerce. It uses a hybrid service model:

- Agent handles common requests automatically.
- Sensitive operations such as refund and exchange go through human review.
- Human agents provide fallback for high-risk, low-confidence, or escalated cases.

The repository is split into frontend and backend:

- `backend/`: Java 17 / Spring Boot 3 multi-module backend.
- `frontend/`: frontend workspace.
- `frontend/workstation/`: agent workstation frontend.
- `infra/`, `docs/`, `requirements/`, `scripts/`: infrastructure and documentation.

Claude Code must read the root `README.md` before making project changes.

## Current Assignment Scope

Claude Code is responsible for the frontend workspace, including:

- `frontend/client-h5/`: customer-facing H5 chat experience.
- `frontend/app-h5/`: app-embedded H5 chat experience.
- `frontend/workstation/`: human agent workstation.

When a task is about only one frontend app, keep edits scoped to that app. Shared frontend workspace changes are allowed only when they are required by the requested task.

Allowed write scope:

- `frontend/**`
- root `package.json` only if a future frontend workspace setup explicitly requires it.
- frontend config files that are strictly required for the requested frontend work.

Do not modify these areas unless explicitly asked:

- `backend/**`
- `infra/sql/**`
- `docs/**`
- `requirements/**`
- root Maven files
- database schema or seed SQL

## Frontend Tech Stack

Use the stack defined by `README.md`:

For customer-facing H5 apps:

- Vue 3 or React, following the existing app choice.
- TypeScript.
- TailwindCSS when the app needs custom lightweight UI styling.
- WebSocket or SockJS capability for chat streaming and message events.

For the human agent workstation:

- React 18
- TypeScript
- Ant Design 5
- Ant Design Pro / `@ant-design/pro-components`
- WebSocket capability reserved for real-time work order events

For all frontend apps:

- ESLint + Prettier
- Node.js 18+
- npm workspaces

Do not switch the project to pnpm or yarn unless explicitly instructed.

Use Vite if a build tool is needed and no existing build tool has been chosen for the target frontend app.

## Required Structure

Keep frontend apps under:

```text
frontend/client-h5/
frontend/app-h5/
frontend/workstation/
```

Recommended workstation structure:

```text
frontend/workstation/
  src/
    app/
    pages/
    layouts/
    components/
    services/
    websocket/
    types/
    routes/
    styles/
    config/
```

The frontend workspace root must keep:

```text
npm run dev:client-h5
npm run dev:app-h5
npm run dev:workstation
```

Default local dev ports:

- Customer chat page: `http://localhost:3000`
- Agent workstation: `http://localhost:3001`

## What To Build Now

Only build the frontend scope explicitly requested by the user. If the user asks for a scaffold, build a minimal technical scaffold:

- Application bootstrap.
- Basic layout placeholder.
- Routing placeholder.
- Theme/config placeholder.
- API client placeholder without real endpoints.
- WebSocket client placeholder without real event contracts.
- Type directories and empty extension points for future API documents.

Do not implement business pages yet.

Do not implement:

- Work order list behavior.
- Work order detail behavior.
- Approve/reject behavior.
- Human takeover behavior.
- Operation log behavior.
- Fake API fields.
- Mock business workflows.

Those will be implemented only after the backend API document is finalized.

## API And Configuration Rules

Do not hardcode backend URLs.

Use environment/config placeholders such as:

- `VITE_CLIENT_API_BASE_URL`
- `VITE_CLIENT_WS_URL`
- `VITE_WORKSTATION_API_BASE_URL`
- `VITE_WORKSTATION_WS_URL`

Do not invent request/response models for business APIs. Leave type placeholders and TODO comments that reference future API documentation.

## UI Direction

For customer-facing H5 apps:

- Build an actual chat experience, not a landing page.
- Keep the interface lightweight, mobile-friendly, and focused on conversation.
- Prioritize readable message bubbles, clear input controls, loading states, and connection states.
- Avoid marketing sections, decorative hero areas, and fake product copy.

For the human agent workstation:

The workstation is an internal customer service operation tool.

Design direction:

- Calm and utilitarian.
- Clear information hierarchy.
- High information density.
- Suitable for repeated agent workflows.
- Use Ant Design components and ProComponents where appropriate.

Avoid:

- Marketing pages.
- Landing pages.
- Large hero sections.
- Decorative dashboards.
- Fake product copy.
- Overly colorful visual design.

## Quality Rules

Before finishing frontend changes, run the available checks for the app or workspace you changed when dependencies are installed.

Examples:

```bash
cd frontend
npm run lint --workspace @smartcs/client-h5
npm run typecheck --workspace @smartcs/client-h5
npm run build --workspace @smartcs/client-h5

npm run lint --workspace @smartcs/app-h5
npm run typecheck --workspace @smartcs/app-h5
npm run build --workspace @smartcs/app-h5

npm run lint --workspace @smartcs/workstation
npm run typecheck --workspace @smartcs/workstation
npm run build --workspace @smartcs/workstation
```

For shared frontend workspace changes, also run the root workspace scripts when practical:

```bash
cd frontend
npm run lint
npm run typecheck
npm run build
```

If dependencies are not installed or checks cannot run, report that clearly and explain the exact command the user should run.

Keep changes small and aligned with the existing repository structure.

Do not delete existing files unless they are replaced by the new scaffold and the reason is clear.

Do not rename project directories without explicit approval.

## Task Granularity And PR Workflow

Work in small, reviewable tasks. When the user gives a broad goal, split it into the smallest useful implementation units before coding. Each unit should have a clear purpose, a limited file scope, and its own verification result.

Do not bundle unrelated changes into one task. In particular:

- Do not modify multiple frontend apps in one task unless the change is truly shared.
- Do not mix feature work, refactoring, formatting, dependency changes, and documentation cleanup unless they are required for the same task.
- Do not add speculative abstractions for future work.
- Prefer the existing project patterns over introducing new libraries or architecture.

For each task, make the best implementation choice available in the current codebase:

- Read the relevant files first.
- Compare simple alternatives when there is a meaningful tradeoff.
- Choose the option that is easiest to maintain, easiest to test, and most consistent with the repository.
- Keep the code complete for the requested task, but avoid premature business complexity.
- Leave the code in a state that another engineer can review without needing hidden context.

After completing each task, prepare a pull request for that task:

1. Run the relevant lint, typecheck, build, or test commands.
2. Review the diff and remove accidental or unrelated changes.
3. Commit only the files needed for the task.
4. Open a PR if the repository remote and authentication are available.
5. If a PR cannot be opened from the current environment, provide a PR-ready summary that includes changed files, behavior, verification commands, and any known limitations.

Each PR should stay focused and small. The PR description must include:

- What changed.
- Why this implementation was chosen.
- What was intentionally not changed.
- How it was verified.
- Any follow-up work that should be handled in a separate PR.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **EcomAgent** (369 symbols, 606 relationships, 15 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/EcomAgent/context` | Codebase overview, check index freshness |
| `gitnexus://repo/EcomAgent/clusters` | All functional areas |
| `gitnexus://repo/EcomAgent/processes` | All execution flows |
| `gitnexus://repo/EcomAgent/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
