# LibreHousehold Context for AI Assistants

This document is the canonical AI-assistant reference for LibreHousehold and should remain the single maintained source for project-specific assistant guidance.

## Project Overview

LibreHousehold is a free and open-source software project for managing household expenses and tasks. The project is still in active development.

### Main Goals

- Completely free
- No ads
- Modern user interface
- Easy to use
- Easy to self-host
- Easy to contribute

### Main Features

- Create households and invite other house-/flatmates
- Track and share expenses with your mates
- Create and manage tasks
- Work offline and sync later
- Responsive design for mobile and desktop

## Architecture Big Picture

LibreHousehold is a **Modular Monolith** designed for household management. It enforces strict separation between Frontend and Backend via an API-first approach.

- **Frontend:** SvelteKit 5 application (SPA/PWA)
- **API:** Defined in `api/openapi.yml`
- **Backend:** Kotlin-based modular monolith (currently still WIP / mostly empty)
- **Documentation:** Arc42/AsciiDoc architecture documentation handled by `docToolchain`
- **External System:** E-Mail server for notifications

### Architecture Decisions (ADRs)

#### Frontend & Backend Separation (ADR-001)

- **Decision:** Split Frontend/Backend with API communication
- **Reason:** Simplicity, understandability, maintainability (QG4)
- **Technologies:** Separate applications with best-fitting technologies each

#### Backend Technology (ADR-002)

- **Language:** Kotlin
- **Reason:** Modern, Java-interoperable, good coroutine support, better performance than Python/Node.js
- **Container:** Docker support for easy hosting (QG3)

#### Architecture Style (ADR-004)

- **Style:** Modular Monolith (Modulith)
- **Reason:** Combination of monolith simplicity and microservice modularity
- **Advantage:** Modules can be extracted to microservices later

#### Frontend Technology (ADR-006)

- **Technology:** Progressive Web App (PWA)
- **Reason:** Cross-platform, no app store costs, easy to host
- **Language:** TypeScript (ADR-008) for type safety and maintainability

### Quality Goals

| ID | Quality Goal | Motivation |
| --- | --- | --- |
| QG1 | Easy to learn (Learnability) | Users should be able to use the app without reading a manual or getting training |
| QG2 | Secure (Security) | Expenses are sensitive data - integrity and confidentiality are important |
| QG3 | Easy to host (Flexibility) | Technical users should be able to host the app themselves without much effort |
| QG4 | Maintainable (Maintainability) | Code should be understandable and modifiable for open-source contributions |

### Data & State Flow

1. **API Schema:** `api/openapi.yml` is the single source of truth for the data model.
2. **Code Generation:** Frontend client types and fetch logic are generated from the OpenAPI spec via `npm run openapi`.
3. **State Management:**
   - Uses **Svelte 5 Runes** (`.svelte.ts` files like `householdState.svelte.ts`) for reactive global state.
   - Traditional stores are also present; verify whether they should be migrated to Runes.
4. **Mocking:** `api/mokapi.js` provides a fake backend for frontend development.

### System Architecture

#### High-Level Structure

- **Frontend:** Progressive Web App (PWA) with TypeScript
- **Backend:** Kotlin-based Modular Monolith
- **External Systems:** E-Mail server for notifications

#### Modules (Frontend & Backend)

| Module | Responsibility |
| --- | --- |
| Expenses | Managing expenses (CRUD operations) |
| Tasks | Managing tasks (CRUD operations) |
| User Settings | User-specific settings, changing login data |
| App Settings | Frontend-specific settings (frontend only) |
| Household | Creating/managing households, inviting/removing users |
| Setup | Initial application setup for administrators |
| Administration | Application management, user management, system settings |
| Notifications | Sending/displaying notifications, configuration |

#### Actors

- **User:** Normal users of the app for household/expense management
- **Administrator:** Hosts and manages an app instance
- **E-Mail Server:** External system for email delivery

## Critical Workflows

All frontend commands must be run from the `frontend/` directory.

### Development

```bash
cd frontend
npm install
npm run dev
```

### API Updates

When `api/openapi.yml` changes, regenerate the client code:

```bash
cd frontend
npm run openapi
```

This cleans `frontend/src/generated-sources/openapi` and regenerates it.

### Testing & Quality

- **Unit Tests:** `npm run test:unit` (Vitest)
- **Linting:** `npm run lint` (ESLint + Prettier)
- **Formatting:** `npm run format`
- **Type Check:** `npm run check`

## Development Context

### Project Structure

```text
LibreHousehold/
├── backend/           # Kotlin backend (modular monolith)
├── frontend/          # TypeScript PWA
├── docs/
│   └── architecture/  # Arc42 architecture documentation
│       ├── adrs/      # Architecture Decision Records
│       └── chapters/  # Arc42 chapters
├── build/             # Build artifacts
└── README.adoc        # Project README
```

### Important Design Principles

1. **Modularity:** Clear module boundaries with defined interfaces
2. **Offline-First:** App should work without internet connection
3. **Responsive Design:** Mobile-first approach
4. **Self-hostable:** Docker containers for easy deployment
5. **Open Source:** Community-friendly and contribution-encouraging

## Technology Stack

### Backend

- **Language:** Kotlin
- **Architecture:** Modular Monolith (Modulith)
- **Deployment:** Docker container
- **Security:** Argon2id for password hashing (ADR-009)

### Frontend

- **Type:** Progressive Web App (PWA)
- **Language:** TypeScript
- **Framework:** SvelteKit 5
- **State Management:** Svelte stores plus Svelte 5 Runes
- **i18n:** Paraglide (`$lib/paraglide/messages.js`)
- **Styling:** DaisyUI + Tailwind CSS
- **OpenAPI Client:** Generated TypeScript client under `src/generated-sources/openapi`

### External Integrations

- E-Mail server for notifications and invitations

## Conventions & Patterns

### Frontend (SvelteKit)

- **Styling:** Tailwind CSS with DaisyUI components
- **Internationalization:** Uses Paraglide JS. Text should be keyed in `messages/` and imported.
- **Project Structure:**
  - `src/routes`: SvelteKit pages and layouts
  - `src/lib`: Shared components, utilities, and stores
  - `src/lib/stores`: State management
  - `src/generated-sources`: **Do not edit manually**; generated API clients

### Documentation

- Architecture documentation lives in `docs/architecture/`
- Use AsciiDoc (`.adoc`) for architecture/project documentation

### Store Pattern

Stores use the pattern with `Configuration` and an `Api` class:

```ts
const apiConfig = new Configuration({ basePath: '/api' });
const api = new TasksApi(apiConfig);

export const functionName = async (
  householdId: string,
  ...
): Promise<ReturnType> => {
  // API call
  // Store update
};
```

## Integration Points

- **API Client:** Imported from `$lib/api` or generated sources
- **Mock Server:** Configured in `api/mokapi.js`. If adding new API endpoints, update the mock to return realistic fake data.

## Important Frontend Files

| File | Description |
| --- | --- |
| `frontend/src/lib/stores/taskStore.ts` | Store for tasks with API functions |
| `frontend/src/lib/stores/memberStore.ts` | Store for household members |
| `frontend/src/lib/stores/householdState.svelte.ts` | Svelte 5 Rune-based state for the household |
| `frontend/src/lib/taskDueCalculator.ts` | Logic for due dates and recurrence |
| `frontend/src/routes/app/tasks/[[new]]/+page.svelte` | Task overview and creation page |

## Key Directories

- `frontend/`: Main web application
- `api/`: OpenAPI specifications and mock server data
- `backend/`: Kotlin backend
- `docs/`: Architecture documentation (AsciiDoc)
- `build/`: Build artifacts (for example the microsite)

## Important Notes for AI Assistants

1. **Maintainability is priority:** Code should be understandable for open-source contributions
2. **Respect module boundaries:** Modules communicate only through defined interfaces
3. **Mobile-first:** UI/UX decisions should consider mobile devices
4. **Security:** Handle sensitive data (expenses) securely
5. **Deployment:** Changes should consider Docker containerization

## Guardrails for AI Agents

### Workflow Rules

1. Create a plan before implementing larger changes.
2. Use this file (`AGENTS.md`) as the central reference for project-specific context.
3. Keep implementation aligned with architecture, module boundaries, and maintainability goals.

### Svelte Tooling

- Call `svelte-list-sections` first to discover available documentation sections.
- Use `svelte-get-documentation` to fetch all relevant sections at once.
- `svelte-svelte-autofixer` should be used before sending Svelte code back to the user.
- Only use `svelte-playground-link` after user confirmation.

### Context7

- Use Context7 for code generation, setup questions, and API/library documentation.

### OpenAPI Development

- Validate OpenAPI changes before committing.
- Ensure all new schemas and properties have `example` values.
- Use `PATCH` for partial resource updates, with the request body containing the fields to update.
- Follow REST best practices: the path identifies the resource, the body contains the changes.
- After OpenAPI changes, regenerate the TypeScript client with `npm run openapi`.

### Test-Driven Development (TDD)

- Follow TDD for business logic: write tests first, then implement, then refactor.
- Unit tests are required for pure functions and business logic.
- UI-only changes (visual/styling only) do not require tests.
- Test files should be named `*.spec.ts` next to the source file where practical.
- Run relevant tests as part of validation.

### Error Handling

- Show toast notifications on errors using the toast store.
- Use optimistic updates for UI responsiveness where appropriate.
- Background saves should not block user interaction.

## Findings & Notes

### Task Schema

The `Task` schema has a `done` field (date format) that either contains a date or is `null`.

### Recurring Tasks

- `recurring: boolean`
- `recurrenceUnit`: days, weeks, months, years
- `recurrenceInterval`: count (minimum `1`)
- For recurring tasks, the task is considered done only if the done date is after the last due date.