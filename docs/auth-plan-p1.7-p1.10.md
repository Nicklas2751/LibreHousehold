# Implementierungsplan P1.7–P1.10: Frontend-Auth (Setup, Login, Invite-Join, API-Client)

Referenz: [`docs/auth-meta-plan.md`](auth-meta-plan.md), Abschnitt „Phase 1 — Lokale Accounts:
Kernflow", Punkte **P1.7–P1.10**. Dieses Dokument ist ein Arbeitsdokument (kein Arc42-Kapitel,
kein ADR) und wird nach Umsetzung nicht dauerhaft gepflegt.

Hinweis zur Struktur: Der als Vorlage genannte Detailplan `docs/auth-plan-p1.1-p1.2.md` existiert
zum Zeitpunkt der Erstellung dieses Dokuments nicht mehr im Repository (Pläne sind temporäre
Arbeitsdokumente, keine dauerhaften Repo-Inhalte). Dieses Dokument folgt daher der im Auftrag
beschriebenen Struktur direkt.

**Scope:** Ausschließlich Frontend (SvelteKit-SPA). P1.3–P1.6 (Backend: `AccountRegistration`,
Spring Authorization Server, Resource-Server-Absicherung, Access Control) werden parallel von
einem anderen Detailplan bearbeitet und hier nur an den Schnittstellen referenziert (OpenAPI-
Verträge, Cookie-/CSRF-Namen, Redirect-URLs). Es wird keine Backend-Mechanik geplant.

Status: Entwurf, noch nicht umgesetzt.

---

## 1. Bindende Vorgaben

Übernommen aus dem Meta-Plan (siehe dort für Details, hier nur die für das Frontend relevanten
Konsequenzen):

- **BFF-Pattern mit httpOnly/SameSite-Cookies** (ADR-014). Tokens verlassen das Backend nie. Die
  SPA sieht nur eine Session-Cookie (`SESSION`, httpOnly — für JS nicht lesbar) und ein lesbares
  CSRF-Cookie (`XSRF-TOKEN`, siehe `sessionCookie`-Security-Scheme-Beschreibung in
  `api/openapi.yml:2645-2653`: „State-changing requests additionally require a matching
  `X-XSRF-TOKEN` header.").
- **Kein localStorage/sessionStorage** (AGENTS.md „State Management Rules"). Der gesamte
  Session-/Auth-Zustand lebt ausschließlich in-memory (Svelte-Zustand). Nach einem Hard-Reload
  muss der Zustand über `GET /me` neu aufgebaut werden — das ist explizit gewollt, kein Bug.
- **1 Account : 1 Household** (ADR-012). Setup und Invite-Join müssen „Account existiert
  bereits" (`/problems/account-already-exists`) klar von „Haushalt existiert bereits"
  (`/problems/household-already-exists`) unterscheiden (nur bei Setup relevant, Invite-Join kennt
  nur die Account-Kollision).
- **Zentrale Architekturentscheidung P1.4/P1.8 (mit dem Backend-Plan identisch zu übernehmen):**
  Die SPA führt selbst **keine** PKCE-/Authorization-Code-/Token-Exchange-Logik aus. Das Backend
  betreibt die komplette OAuth2-Authorization-Code+PKCE-Journey serverseitig (Spring Security
  `oauth2Login()` als Client gegen die eingebettete Authorization-Server-Instanz). Für die SPA
  bedeutet das: `/login` ist kein `fetch`-basierter JSON-Formular-Submit, sondern eine echte
  Browser-Navigation gegen eine vom Backend bereitgestellte Form-Login- bzw. Login-Start-URL.
  Nach Erfolg navigiert das Backend den Browser zurück in die SPA (z. B. `/app`); die SPA
  hydratisiert ihren Zustand danach über `GET /me`.
- **TDD-Pflicht** (AGENTS.md „Test-Driven Development"): Business-Logik (Validierung,
  Fehler-Mapping nach `Problem.type`, Debounce-/Verfügbarkeits-Check, Guard-Entscheidungen,
  CSRF-Header-Ableitung, 401-Interceptor-Logik) wird als reine, aus der Komponente extrahierte
  Funktion getestet (Vitest, `*.spec.ts`). UI-only-Änderungen (Styling) benötigen keine Tests.
- **Keine neue Dependency ohne Rückfrage** (globale Instruktion + AGENTS.md „Dependency
  Management"). Siehe Abschnitt 3.5 zur Component-Testing-Frage — hier ist **keine** Rückfrage
  nötig, da `vitest-browser-svelte` bereits vorhanden ist (siehe Ist-Zustand).
- **DaisyUI-Modals statt `confirm()`/`alert()`/`prompt()`** (AGENTS.md „UI Conventions") — relevant
  für eventuelle Bestätigungsdialoge (z. B. Logout-Bestätigung, falls gewünscht; siehe P1.8).
- **Toast-Store für Fehleranzeigen** (AGENTS.md „Error Handling"), Muster bereits etabliert in
  `frontend/src/lib/SetupWizard.svelte:138` und `frontend/src/lib/JoinWizard.svelte:70`.

---

## 2. Ist-Zustand (mit Datei-/Zeilenreferenzen)

### 2.1 Bereits generierter Vertrag (P1.1/P1.2, nicht ändern)

Geprüft in `api/openapi.yml` und `frontend/src/generated-sources/openapi` (gitignored, aber
bereits aus der aktuellen Spec generiert vorhanden):

- `LocalRegistration { password: string }` (`api/openapi.yml:2451-2458`) — Pflichtfeld in
  `HouseholdSetup` (`api/openapi.yml:2472-2484`) **und** `MemberRegistration`
  (`api/openapi.yml:2564-2587`).
- `EmailAvailability { available: boolean }`, Endpoint `GET /accounts/availability?email=...`
  → `AccountsApi.checkEmailAvailability({ email })` (`.../apis/AccountsApi.ts:41-77`).
- `CurrentUser { member, householdId, preferences }`, Endpoint `GET /me`
  → `SessionApi.getCurrentUser()` (`.../apis/SessionApi.ts:37-62`), wirft bei 401 einen
  `ResponseError` (401 ist im OpenAPI-Vertrag explizit als „No authenticated session" spezifiziert,
  `api/openapi.yml:1552-1557`).
- `POST /logout` → `SessionApi.logout()` (`.../apis/SessionApi.ts:68-92`), idempotent, keine
  Security-relevanten Rückgabewerte.
- 409-Antworten unterscheiden `type: "/problems/household-already-exists"` vs.
  `"/problems/account-already-exists"` bei `setupHousehold` (`api/openapi.yml:72-95`); bei
  `joinHousehold` gibt es nur `"/problems/account-already-exists"` (`api/openapi.yml:1475-1488`).
- Generierte Modelle setzen `password`/`localRegistration` als **required** TypeScript-Property
  (`.../models/LocalRegistration.ts`, `.../models/MemberRegistration.ts`,
  `.../models/HouseholdSetup.ts`).

### 2.2 Bereits kaputter Build auf diesem Stand — Beleg, warum P1.7/P1.9 nötig sind

`npx svelte-check` (ausgeführt gegen den aktuellen Branch-Stand) liefert bereits **zwei
Compile-Fehler**, weil die Komponenten das neue `localRegistration`-Pflichtfeld noch nicht setzen:

```
ERROR "src/lib/JoinWizard.svelte" 45:5
  "Property 'localRegistration' is missing in type '{ id: ...; name: string; email: string;
   avatar: string | undefined; }' but required in type 'MemberRegistration'."
ERROR "src/lib/SetupWizard.svelte" 118:9
  "Property 'localRegistration' is missing in type '{ household: Household; member: Member; }'
   but required in type 'HouseholdSetup'."
```

P1.7 und P1.9 beheben diese beiden Fehler als Nebeneffekt der eigentlichen Aufgabe (Passwortfeld
ergänzen); das ist kein zusätzlicher Scope, sondern der Kern der Aufgabe.

### 2.3 SetupWizard (P1.7-relevant)

- Komponente: `frontend/src/lib/SetupWizard.svelte`. Verwendet von
  `frontend/src/routes/setup/+page.svelte`.
- Schritt 1 (Admin-Account) rendert `frontend/src/lib/MemberProfileForm.svelte` (Zeilen 198–214),
  eine **gemeinsam mit `JoinWizard.svelte` genutzte** Komponente für Name/E-Mail/Avatar
  (`MemberProfileForm.svelte:1-123`). Sie hat noch **kein** Passwortfeld.
- `finish()` (`SetupWizard.svelte:102-141`) baut aktuell
  `const householdSetup: HouseholdSetup = { household, member };` — ohne `localRegistration`
  (Compile-Fehler, siehe 2.2).
- 409-Fehlerbehandlung existiert bereits, aber **undifferenziert**: `SetupWizard.svelte:128-140`
  prüft nur `status === 409` und zeigt immer `m['invite.email_taken']()` — unabhängig davon, ob
  `household-already-exists` oder `account-already-exists` zurückkam. Das
  `Problem.type`-Feld wird nirgends ausgewertet.
- Der Status-Extraktions-Codeblock (`err instanceof ResponseError ? err.response.status : ...`,
  `SetupWizard.svelte:129-134`) ist **identisch dupliziert** in `JoinWizard.svelte:61-66`.
- Kein E-Mail-Verfügbarkeits-Check vorhanden (`AccountsApi` wird nirgends importiert).
- Eigene `Configuration`-Instanz: `SetupWizard.svelte:103`.

### 2.4 JoinWizard / Invite-Join (P1.9-relevant)

- Komponente: `frontend/src/lib/JoinWizard.svelte`. Verwendet von
  `frontend/src/routes/invite/[token]/+page.svelte` (Prop `token`).
- Nutzt ebenfalls `MemberProfileForm.svelte` (Zeilen 100–119).
- `join()` (`JoinWizard.svelte:39-75`) baut `memberRegistration` ohne `localRegistration`
  (Compile-Fehler, siehe 2.2).
- 409-Handling identisch zum SetupWizard: undifferenziert, `m['invite.email_taken']()`
  (`JoinWizard.svelte:67-69`) — bei Invite-Join ist das inhaltlich zufällig richtig, weil dort nur
  `account-already-exists` auftreten kann, aber die Logik ist nicht explizit darauf geprüft.
- Bereits vorhandener Component-Test `frontend/src/lib/JoinWizard.svelte.spec.ts` mockt
  `MembersApi` aus `../generated-sources/openapi` via `vi.mock` (Zeilen 12-21) und `$app/navigation`
  (Zeile 23) — **exaktes Referenzmuster** für die P1.9-Tests.
- Eigene `Configuration`-Instanz: `JoinWizard.svelte:23`.

### 2.5 Login / Session-Bootstrap / Guards (P1.8-relevant)

- **Es gibt aktuell keine `/login`-Route.** `find frontend/src/routes` zeigt keinen
  `login`-Ordner. Der Link in `frontend/src/lib/WelcomeScreen.svelte:39-41`
  (`<a ... href="/login">{m['welcome.login_button']()}</a>`) ist ein **toter Link** (404).
- **Es gibt keine Session-Bootstrap-Logik.** `frontend/src/routes/+layout.svelte` initialisiert nur
  `settingsStore` (`if (browser) initSettings();`, Zeile 6) — keinerlei Aufruf von
  `SessionApi.getCurrentUser()`.
- **Es gibt keine Route-Guards.** `frontend/src/routes/app/+layout.svelte` (9 Zeilen) rendert
  direkt `AppMenu` + `children()` ohne jede Auth-Prüfung. Kein `+layout.ts`/`+layout.server.ts`
  existiert unter `frontend/src/routes/app/`.
- **Es gibt keine Logout-UI.** Keine Fundstelle für „logout"/„Abmelden" in `.svelte`-Dateien
  (durchsucht). `frontend/src/routes/app/settings/+page.svelte` (die Settings-Übersicht) wäre der
  naheliegende Ort für einen Logout-Eintrag — hat aktuell nur Appearance/Language/Nav-Cards zu
  User/Household/Categories (Zeilen 27–130).
- Zustand wird aktuell in zwei getrennten, klassischen `Writable`-Stores gehalten (kein Runes-Bezug
  trotz `.svelte.ts`-Endung bei einem der beiden):
  - `frontend/src/lib/stores/householdState.svelte.ts` (5 Zeilen): `writable<Household|undefined>`.
  - `frontend/src/lib/stores/userState.ts` (9 Zeilen): `writable<Member|undefined>`.
  Beide werden nur beim erfolgreichen Setup/Join **lokal aus der Response befüllt**
  (`updateHouseholdState`, `updateUserState`), nie aus `GET /me`. Nach Reload sind beide `undefined`
  — das ist exakt der im Meta-Plan beschriebene Bug „State geht beim Reload verloren".
- SvelteKit-Grundgerüst: `frontend/src/hooks.server.ts` macht nur Paraglide-Middleware,
  `frontend/src/hooks.ts` nur `reroute` für i18n-URLs. Kein `handle`-Hook mit Auth-Bezug. Kein
  `hooks.client.ts`.
- `frontend/vite.config.ts:9-11,23-28`: Dev-Proxy leitet `/api/*` an `VITE_API_URL` weiter
  (`changeOrigin: true`), d. h. im Dev-Betrieb ist der Aufruf same-origin aus Sicht des Browsers,
  Cookies würden dort auch ohne `credentials: 'include'` mitgeschickt. Für produktive
  Deployments (separate Domains/Subdomains, WebView-Kontexte, zukünftige native Shells) ist
  `credentials: 'include'` trotzdem notwendig und vom Meta-Plan explizit gefordert.
- SvelteKit-Dokuprüfung (Sections „Loading data" / „Hooks", via Svelte-MCP): Layout-`load`-
  Funktionen laufen **nicht** bei jeder Client-Side-Navigation zwischen Kind-Routen erneut (siehe
  Abschnitt „Implications for authentication" der SvelteKit-Doku) — ein serverseitiger
  `+layout.server.ts`-Load-Guard wäre hier ohnehin nicht die richtige Wahl, weil die App laut
  AGENTS.md offline-first/SPA-artig ist und alle bisherigen Datenzugriffe client-seitig in
  `onMount`/Aktionen erfolgen (kein bestehendes `+page.server.ts`/`+layout.server.ts` im gesamten
  `routes`-Baum). Das bestätigt die im Auftrag vorgegebene Leitplanke „aus dem Session-State
  ableiten, nicht bei jedem Route-Wechsel neu `/me` aufrufen".

### 2.6 API-Client (P1.10-relevant)

`grep -rn "new Configuration(" frontend/src` findet **12 unabhängige Instanziierungen** in 10
Dateien (keine davon setzt `credentials` oder `middleware`):

| Datei | Zeile(n) |
|---|---|
| `frontend/src/lib/JoinWizard.svelte` | 23 |
| `frontend/src/lib/SetupWizard.svelte` | 103 |
| `frontend/src/lib/stores/expenseStore.ts` | 13 |
| `frontend/src/lib/stores/settingsStore.ts` | 7 |
| `frontend/src/lib/stores/categoryStore.ts` | 12 |
| `frontend/src/lib/stores/financialStore.ts` | 12 |
| `frontend/src/lib/stores/memberStore.ts` | 6 |
| `frontend/src/lib/stores/statisticsStore.ts` | 17 |
| `frontend/src/lib/stores/taskStore.ts` | 14 |
| `frontend/src/lib/stores/reimbursementStore.ts` | 14 |
| `frontend/src/routes/app/settings/household/+page.svelte` | 25, 26 |
| `frontend/src/routes/app/settings/user/+page.svelte` | 18, 19, 20 |

Alle folgen dem in AGENTS.md dokumentierten „Store Pattern"
(`const apiConfig = new Configuration({ basePath: '/api' }); const api = new XyzApi(apiConfig);`).

Generierter `Configuration`/`BaseAPI`-Code (`frontend/src/generated-sources/openapi/runtime.ts`)
unterstützt bereits alles, was für die Zentralisierung nötig ist, ohne den Generator-Output
anzufassen:

- `ConfigurationParameters.credentials?: RequestCredentials` (Zeile 28).
- `ConfigurationParameters.middleware?: Middleware[]` (Zeile 21) mit
  `interface Middleware { pre?(context): Promise<FetchParams|void>; post?(context): Promise<Response|void>; onError?(context): Promise<Response|void>; }`
  (Zeilen 387-391).
- `ResponseError extends Error { response: Response }` (Zeilen 260-265) — Grundlage für die
  bereits zweifach duplizierte Status-Extraktion (siehe 2.3).
- `export const DefaultConfig = new Configuration();` (Zeile 87) — wird aktuell **nicht** genutzt,
  jede Store-Datei baut ihre eigene Instanz statt diese zu parametrisieren oder zu ersetzen.

### 2.7 Vorhandene Test-Infrastruktur

`frontend/package.json` (Stand geprüft): **kein** `@testing-library/svelte`. Stattdessen bereits
vorhanden: `vitest` `^4.1.9`, `@vitest/browser` `^4.1.10`, `@vitest/browser-playwright` `^4.1.8`,
`vitest-browser-svelte` `^3.0.0`, `playwright` `^1.61.1`. Das ist eine **echte
Component-Interaktions-Test-Infrastruktur**, die bereits produktiv genutzt wird:
`frontend/src/lib/JoinWizard.svelte.spec.ts` rendert die echte Komponente
(`render(JoinWizard, {...})` aus `vitest-browser-svelte`) und interagiert über
`page.getByRole(...)`/`page.getByText(...)` aus `vitest/browser`.

→ **Keine Rückfrage zu einer neuen Dependency nötig.** Component-Interaktionstests für
SetupWizard/JoinWizard/Login-Formular/App-Layout-Guard können und sollen mit dem bereits
etablierten `vitest-browser-svelte`-Muster geschrieben werden, exakt wie in
`JoinWizard.svelte.spec.ts` vorgemacht.

`frontend/vite.config.ts:31-49` trennt zwei Test-Projekte:

- `client`: `include: ['src/**/*.svelte.{test,spec}.{js,ts}']`, läuft im echten Browser
  (Playwright/Chromium) — **hier laufen Component-Tests und Tests von `.svelte.ts`-Runen-Modulen**.
- `server`: alle übrigen `*.spec.ts`, Node-Umgebung — hier laufen reine Unit-Tests von
  `.ts`-Modulen ohne Runes.

Wichtige Konsequenz für P1.8: Ein neues Session-State-Modul, das `$state`-Runes verwendet, **muss**
`sessionState.svelte.ts` heißen (nicht `sessionState.ts`), damit es im `client`-Testprojekt läuft
und Runes außerhalb einer Komponente überhaupt funktionieren.

---

## 3. Offene Design-Entscheidungen

### 3.1 `/login`-Seite: eigenes gestyltes Formular vs. reine Weiterleitung — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): Variante (a) — eigene `/login`-Seite mit nativem `<form method="post">`.**

`frontend/src/routes/login/+page.svelte` rendert ein eigenes, DaisyUI-gestyltes Formular
(E-Mail + Passwort, gleiches Look&Feel wie `WelcomeScreen`/`SetupWizard`) mit
`<form method="post" action="{loginActionUrl}">` — **kein** `fetch`, volle Seitennavigation. Die
`action`-URL zeigt auf die vom Backend bereitgestellte Form-Login-Processing-URL (exakter Pfad ist
Ergebnis des P1.4-Feinplans; hier nur als Konfigurationskonstante behandelt, s. Abschnitt 4.2).
Vorteil: eigenes Branding/UX bleibt erhalten, Nutzer verlässt die SPA optisch nie. Nachteil: setzt
voraus, dass Spring Security einen sauberen Ersatz der Default-Login-Page durch eine
extern gehostete Form erlaubt (Standard-Fähigkeit von `formLogin().loginPage(...)` — muss im
Backend-Feinplan (P1.4) bestätigt werden; hier nur als Annahme dokumentiert).

**Alternative (b): Redirect per `window.location.href` auf eine Backend-Login-Start-URL.**
Einfacher (kein eigenes Formular, kein CSRF-Handling im Frontend-Login-Formular nötig), aber
schlechteres eigenes Branding (Nutzer sieht kurzzeitig eine Backend-gerenderte Seite). Falls sich
im P1.4-Feinplan herausstellt, dass ein sauberer Custom-Login-Page-Ersatz nicht/nur mit
unverhältnismäßigem Aufwand möglich ist, ist (b) der Fallback.

**Konsequenz für diesen Plan:** P1.8 wird für Variante (a) geplant (siehe Aufgaben unten). Der
`<form>`-Submit selbst ist ein reiner Browser-Mechanismus (keine JS-Business-Logik, kein
`fetch`) und braucht daher **keinen** Unit-Test nach der TDD-Pflicht — die einzige testbare
Logik auf dieser Seite ist ggf. eine clientseitige Vorab-Validierung (z. B. „E-Mail-Format",
analog `isValidEmail` in `setupWizardLogic.ts`) sowie die Berechnung der `action`-URL selbst,
falls sie dynamisch aus `window.location`/Konfiguration zusammengesetzt wird.

### 3.2 Wohin mit dem Passwortfeld: `MemberProfileForm.svelte` erweitern vs. neue Komponente — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): neue, eigenständige Komponente `frontend/src/lib/PasswordField.svelte`**, die von
`MemberProfileForm.svelte` per Composition eingebunden wird (Props: `label`, `hint`,
`bind:value`, optionales `autocomplete`-Attribut für `new-password` vs. `current-password`).

Begründung: `MemberProfileForm` wird von SetupWizard **und** JoinWizard geteilt — beide brauchen
jetzt zusätzlich ein Passwortfeld mit Maskierung + Reveal-Toggle. Das Feld direkt in
`MemberProfileForm` einzubetten ist am DRYsten und entspricht dem SRP nicht schlechter als die
aktuelle Struktur, **aber** das Reveal-Toggle (Augen-Icon, `type="password"`⇄`type="text"`-
Umschaltung) ist eigenständige, wiederverwendbare UI-Logik ohne Bezug zu Name/E-Mail/Avatar.
Eine eigene `PasswordField.svelte`-Komponente hält `MemberProfileForm` fokussiert (SRP) und macht
das Reveal-Toggle unabhängig testbar/wiederverwendbar (z. B. später für „Passwort ändern" in den
Usersettings, `PasswordChangeRequest`, außerhalb dieses Scopes, aber die Komponente wird dort
später ohne Änderung wiederverwendbar sein).

**Alternative:** Passwortfeld + Toggle-State direkt inline in `MemberProfileForm.svelte`. Weniger
Dateien, aber `MemberProfileForm` wüchse um eine weitere, thematisch fremde Verantwortlichkeit
(Passwort-Sichtbarkeit ist kein Profil-Datum). Nur sinnvoll, falls das Reveal-Toggle nirgends
sonst gebraucht wird — das ist aktuell nicht bekannt, daher die Empfehlung für die eigene
Komponente.

Die Reveal-Toggle-Logik selbst (welcher `type` aktuell aktiv ist) ist trivialer UI-State
(`$state<boolean>`) ohne Verzweigungslogik — laut AGENTS.md „UI-only changes... do not require
tests" fällt das Umschalten selbst unter UI-only. Was **doch** getestet werden muss, ist die
Passwort-**Validierung** (Mindestlänge etc., siehe 3.4).

### 3.3 Zentrale Session-State-Verwaltung: neues Runes-Modul vs. Erweiterung von `userState`/`householdState` — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): neues Modul `frontend/src/lib/stores/sessionState.svelte.ts` mit echten Svelte-5-
Runes (`$state`), als alleinige Quelle für „bin ich eingeloggt?".** Bestehende
`userState`/`householdState` (klassische `writable`-Stores) bleiben unverändert bestehen und
werden weiterhin von SetupWizard/JoinWizard beim erfolgreichen Abschluss lokal befüllt (kein
Grund, funktionierenden Code für diesen Plan umzubauen — das wäre eine spekulative Erweiterung des
Scopes). Der neue Session-State bündelt zusätzlich den Bootstrapping-Status:

```ts
export type SessionStatus = 'bootstrapping' | 'authenticated' | 'guest';
export const session: { status: SessionStatus; currentUser: CurrentUser | null } = $state({
	status: 'bootstrapping',
	currentUser: null
});
```

Nach erfolgreichem Bootstrap (`GET /me` → 200) wird zusätzlich `updateHouseholdState(...)` und
`updateUserState(...)` aus dem `CurrentUser`-Payload befüllt, damit bestehende Komponenten
(Settings, AppMenu etc.), die weiterhin `householdState`/`userState` lesen, nach einem Reload
korrekt befüllt sind — das behebt den im Meta-Plan genannten Bug „State geht beim Reload
verloren" ohne alle Konsumenten dieser Stores anzufassen.

**Alternative:** `userState`/`householdState` selbst auf Runes migrieren und um einen
Session-Status erweitern. Technisch sauberer (eine Quelle der Wahrheit statt zwei parallele
Strukturen), aber deutlich größerer Blast-Radius (alle Konsumenten dieser beiden Stores
müssten geprüft/ggf. angepasst werden) und außerhalb des hier beauftragten Scopes (P1.7–P1.10
betrifft Auth, nicht eine generelle Store→Runes-Migration). AGENTS.md nennt diese Migration
explizit als offene, aber nicht terminierte Aufgabe („verify whether they should be migrated to
Runes") — das ist bewusst kein Bestandteil dieses Plans.

### 3.4 Zentraler API-Client: Ort und Bauform der Factory — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** neues Modul `frontend/src/lib/api/httpClient.ts`, das eine einzige, vorkonfigurierte
`Configuration`-Instanz exportiert:

```ts
export const apiConfiguration = new Configuration({
	basePath: '/api',
	credentials: 'include',
	middleware: [csrfMiddleware, sessionExpiredMiddleware]
});
```

Alle 12 Fundstellen aus 2.6 werden auf `import { apiConfiguration } from '$lib/api/httpClient';`
umgestellt (`new XyzApi(apiConfiguration)` statt `new XyzApi(new Configuration({ basePath: '/api' }))`).
Das ist eine reine Wire-up-Änderung ohne Verhaltensänderung an den einzelnen Stores — daher pro
Store **kein** neuer Test nötig (die Stores testen weiterhin ihre eigene Logik, nicht die
Configuration-Verdrahtung); die neue Middleware-Logik selbst wird isoliert getestet (s. Aufgaben
P1.10).

**Alternative:** `DefaultConfig` in `runtime.ts` global mutieren (`DefaultConfig.config = ...`).
Abgelehnt, weil `runtime.ts` generierter Code ist („Do not edit the class manually") und
`Configuration` keinen Setter für einzelne Parameter anbietet, der das ohne Re-Instantiierung
erlauben würde — der einzige Weg wäre, `BaseAPI`-Subklassen ohne Argument zu instanzieren, was
bei den 12 Fundstellen ohnehin explizit `new Configuration({...})` übergibt und daher nicht
greifen würde, ohne ebenfalls alle 12 Stellen anzufassen. Kein Vorteil gegenüber der Empfehlung,
zusätzliches implizites globales Mutieren eines Singletons — schlechter nachvollziehbar.

### 3.5 CSRF-Header-Ableitung: Middleware `pre`-Hook vs. Wrapper um jede Store-Methode — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** ein einziger `pre`-Middleware-Hook (`csrfMiddleware`) in `httpClient.ts`, der bei
jedem Request aus `document.cookie` das `XSRF-TOKEN`-Cookie liest und bei state-changing Methoden
(`POST`/`PUT`/`PATCH`/`DELETE`) den Header `X-XSRF-TOKEN` setzt. Das ist die einzige Stelle, die
für **alle** generierten API-Klassen automatisch greift, weil sie über die gemeinsame
`apiConfiguration` (3.4) injiziert wird. Die eigentliche Cookie-Parsing- und
Methodenklassifizierungs-Logik wird als zwei pure Funktionen ausgelagert und isoliert getestet
(siehe Aufgaben P1.10) — der Middleware-Hook selbst ist nur dünner Adapter-Code (Cookie-String
lesen, Funktionen aufrufen, `context.init.headers` mutieren).

**Alternative:** Wrapper-Funktion, die jede Store-Methode einzeln umschließt. Abgelehnt: würde die
12 Store-Dateien viel invasiver anfassen (jeder API-Call bräuchte einen Wrapper-Aufruf) und wäre
nicht automatisch für neue, zukünftige Store-Dateien wirksam.

### 3.6 401-Interceptor: wann ist ein 401 „Session abgelaufen" vs. „normaler Gast-Zustand"? — ✅ entschieden (zustandsbasiert per `session.status`, vom Nutzer bestätigt)

Das ist die kniffligste Design-Entscheidung, weil `GET /me` selbst laut OpenAPI-Vertrag mit 401
antwortet, wenn **keine** Session existiert (`api/openapi.yml:1552-1557`) — das ist der normale,
erwartete Weg, den Gast-Zustand beim Bootstrap zu erkennen, **kein** Fehlerfall. Ein globaler
401-Interceptor, der bei jedem 401 hart auf `/login` umleitet, würde beim allerersten
Bootstrap-Aufruf eines nicht eingeloggten Nutzers sofort auf `/login` umleiten, obwohl der Nutzer
z. B. gerade nur die öffentliche `WelcomeScreen`- oder `/invite/{token}`-Seite besuchen wollte.

**Empfehlung:** Der 401-Interceptor (`sessionExpiredMiddleware`, `post`-Hook) prüft den
**aktuellen** `session.status` (3.3), bevor er reagiert:

- War `session.status === 'authenticated'` (d. h. der Nutzer war schon eingeloggt) und kommt jetzt
  irgendwo ein 401 zurück → echte Session-Ablauf-/Revocation-Situation: `setGuest()` aufrufen und
  per `goto('/login')` umleiten (nur falls nicht bereits auf `/login`, sonst kein Redirect
  nötig — sonst Endlosschleife/unnötige Navigation).
- In allen anderen Fällen (`bootstrapping`/`guest`) → **kein** Redirect, kein State-Reset; der 401
  wird an den Aufrufer (i. d. R. den Bootstrap-Code selbst) durchgereicht, der ihn als „ist eben
  ein Gast" interpretiert.

Die Entscheidungslogik wird als pure, getestete Funktion ausgelagert:
`shouldTreatAsSessionExpiry(currentStatus: SessionStatus): boolean` (nur `true` für
`'authenticated'`).

**Alternative:** URL-Pattern-Ausschlussliste (`/me`, `/logout` vom Interceptor ausnehmen).
Abgelehnt: brüchiger (bricht bei Pfadänderungen im Backend-Plan), und deckt den Fall nicht ab,
dass **irgendein anderer** Endpoint während des Bootstraps zufällig vor `/me` aufgerufen wird
und ebenfalls 401 liefert (aktuell nicht der Fall, aber die zustandsbasierte Prüfung ist robuster
gegen zukünftige Reihenfolgeänderungen).

### 3.7 Route-Guard-Mechanismus für `/app/*` — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt, konsistent mit der Entscheidung gegen einen SvelteKit-Node-Server, siehe DD-7 im Backend-Plan):** Kein `+layout.server.ts`/`+layout.ts` mit Redirect (das würde serverseitiges
Rendering und eine Kopplung von SvelteKit-eigenem Server an das Backend voraussetzen, die es in
diesem rein clientseitig arbeitenden SPA/PWA-Projekt bisher nirgends gibt — kein einziges
`+page.server.ts`/`+layout.server.ts` existiert aktuell im `routes`-Baum, siehe 2.5). Stattdessen:
Guard-Logik direkt in `frontend/src/routes/app/+layout.svelte` per `$effect`, das den reaktiven
`session.status` (3.3) beobachtet und bei `'guest'` per `goto('/login')` umleitet; bei
`'bootstrapping'` wird ein Ladezustand gerendert (UI-only, kein Test nötig), bei `'authenticated'`
werden `children()` gerendert. Die Entscheidung „umleiten oder nicht" wird als pure Funktion
`shouldRedirectToLogin(status: SessionStatus): boolean` (nur `true` für `'guest'`) ausgelagert und
getestet — das ist exakt die im Auftrag geforderte „Guard-Entscheidungslogik".

Der Bootstrap-Aufruf selbst (`GET /me`) passiert **einmalig** beim App-Start im Root-Layout
(`frontend/src/routes/+layout.svelte`, analog zu `initSettings()` in Zeile 6), nicht im
`/app`-Layout und nicht bei jedem Route-Wechsel — damit ist die Vorgabe „nicht bei jedem
Route-Wechsel neu `/me` aufrufen" erfüllt, weil der Guard in `/app/+layout.svelte` nur den bereits
im Speicher gehaltenen `session.status` liest.

---

## 4. Empfohlene Umsetzungsreihenfolge

Die Meta-Plan-Reihenfolge ist P1.7 → P1.8 → P1.9 → P1.10. Diese Reihenfolge nach ID beizubehalten
würde jedoch bedeuten, dass P1.7 und P1.9 zunächst eine der 12 verstreuten
`new Configuration({...})`-Instanzen kopieren (SetupWizard/JoinWizard sind genau zwei dieser
Fundstellen) und P1.8 seine 401-Redirect-Logik ohne die in P1.10 gebaute Middleware nicht sinnvoll
verdrahten kann. Empfohlen wird daher folgende **Umsetzungsreihenfolge**, die IDs bleiben wie im
Meta-Plan:

1. **P1.10 (Teil A — Client-Wrapper):** `httpClient.ts` mit `apiConfiguration`, CSRF-Middleware,
   Umstellung aller 12 Fundstellen. Dies ist reine Infrastruktur ohne fachliche
   Verhaltensänderung und blockiert nichts fachlich Sichtbares.
2. **P1.7:** SetupWizard-Passwortfeld + E-Mail-Verfügbarkeitscheck + 409-Differenzierung, nutzt ab
   sofort `apiConfiguration` aus Schritt 1 statt einer neuen eigenen `Configuration`.
3. **P1.9:** Invite-Join-Passwortfeld, analog zu P1.7, nutzt `PasswordField.svelte` aus Schritt 2
   direkt mit.
4. **P1.8:** Session-State-Modul, Bootstrap, `/login`-Route, Guards, Logout — nutzt für den
   401-Interceptor bereits die Middleware-Infrastruktur aus Schritt 1 (**P1.10 Teil B —
   `sessionExpiredMiddleware`** wird hier ergänzt, weil sie zwingend den in P1.8 gebauten
   `session`-State kennen muss; dieser Teil von P1.10 kann architektonisch nicht vor P1.8 fertig
   werden, siehe Abschnitt 3.6).

Die Aufgabenlisten unten sind nach P-ID gegliedert (wie vom Meta-Plan verlangt), referenzieren sich
aber gegenseitig entsprechend dieser Reihenfolge.

---

## 5. Aufgaben je P-Punkt

Für jeden Schritt gilt strikt Rot-Grün-Refactor. Dateipfade sind neu anzulegende Dateien, sofern
nicht anders vermerkt.

### P1.10 — API-Client & Guards (Teil A: Client-Wrapper, zuerst umzusetzen)

**Aufgabe 1: Reine Funktion `getCsrfTokenFromCookieHeader`**

- Neue Datei `frontend/src/lib/api/csrf.ts`.
- Test zuerst, `frontend/src/lib/api/csrf.spec.ts`:
  - `describe('getCsrfTokenFromCookieHeader')`
    - `it('liest den XSRF-TOKEN-Wert aus einem Cookie-String mit mehreren Cookies')`
    - `it('gibt null zurück, wenn kein XSRF-TOKEN-Cookie vorhanden ist')`
    - `it('dekodiert einen URL-kodierten Cookie-Wert')`
  - Rot: Modul `csrf.ts` existiert nicht → Importfehler.
  - Grün (minimal): Funktion parst `cookieHeader.split('; ')`, sucht Präfix `XSRF-TOKEN=`, gibt
    `decodeURIComponent(...)` des Werts oder `null` zurück.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 2: Reine Funktion `isStateChangingMethod`**

- Gleiche Datei `csrf.ts`.
- Test in `csrf.spec.ts`, parametrisiert (`it.each`):
  - `describe('isStateChangingMethod')`
    - `it.each(['POST','PUT','PATCH','DELETE'])('gibt true für %s zurück', ...)`
    - `it.each(['GET','HEAD','OPTIONS'])('gibt false für %s zurück', ...)`
  - Rot: Funktion fehlt.
  - Grün: `['POST','PUT','PATCH','DELETE'].includes(method.toUpperCase())`.

**Aufgabe 3: Fehler-Status-Extraktion zentralisieren (DRY-Fix für bestehende Duplikation)**

- Neue Datei `frontend/src/lib/api/errorStatus.ts`, Funktion `extractErrorStatus(err: unknown): number | undefined`
  — konsolidiert die aktuell zweifach duplizierte Logik aus `SetupWizard.svelte:129-134` und
  `JoinWizard.svelte:61-66`.
- Test zuerst, `frontend/src/lib/api/errorStatus.spec.ts`:
  - `describe('extractErrorStatus')`
    - `it('liest den Status aus einem ResponseError')`
    - `it('liest den Status aus einem objektartigen Fehler mit status-Property')`
    - `it('gibt undefined zurück für einen Fehler ohne erkennbaren Status')`
  - Rot: Modul fehlt.
  - Grün: Portierung der bestehenden Fallunterscheidung (`instanceof ResponseError` →
    `err.response.status`; sonst `typeof err === 'object' && err !== null && 'status' in err` →
    `(err as { status: unknown }).status`; sonst `undefined`).
  - Refactor: SetupWizard.svelte und JoinWizard.svelte werden in den jeweiligen P1.7/P1.9-Schritten
    auf diese Funktion umgestellt (siehe dort) statt die Logik weiter zu duplizieren.

**Aufgabe 4: `httpClient.ts` mit zentraler `apiConfiguration` und CSRF-Middleware**

- Neue Datei `frontend/src/lib/api/httpClient.ts`.
- Test zuerst, `frontend/src/lib/api/httpClient.spec.ts`:
  - `describe('csrfMiddleware')`
    - `it('setzt den X-XSRF-TOKEN-Header bei POST, wenn ein XSRF-TOKEN-Cookie gesetzt ist')` —
      `document.cookie` im Test setzen (läuft im `client`-Vitest-Projekt/echter Browser, `document`
      ist verfügbar), `pre({ url, init: { method: 'POST', headers: {} } })` aufrufen, Header prüfen.
    - `it('setzt keinen Header bei GET, selbst wenn ein XSRF-TOKEN-Cookie gesetzt ist')`
    - `it('setzt keinen Header, wenn kein XSRF-TOKEN-Cookie vorhanden ist')`
  - Wichtig: Diese Testdatei muss `httpClient.svelte.spec.ts` **oder** eine reine `.spec.ts` im
    `server`-Projekt sein — da `document.cookie` nur im Browser-Testprojekt existiert, muss die
    Datei `httpClient.svelte.spec.ts` heißen, damit sie im `client`-Projekt (echter Browser)
    läuft (siehe Ist-Zustand 2.7). Alternativ: `document` in dieser Testdatei mocken, falls sie im
    `server`-Projekt bleiben soll — Empfehlung ist der Browser-Testlauf, da realistischer.
  - Rot: Modul/Middleware fehlt.
  - Grün (minimal): `csrfMiddleware: Middleware = { pre: async ({ init }) => { if (!isStateChangingMethod(init.method ?? 'GET')) return; const token = getCsrfTokenFromCookieHeader(document.cookie); if (!token) return; return { url, init: { ...init, headers: { ...init.headers, 'X-XSRF-TOKEN': token } } }; } }`.
  - `apiConfiguration = new Configuration({ basePath: '/api', credentials: 'include', middleware: [csrfMiddleware] })` exportieren (die `sessionExpiredMiddleware` wird erst in P1.8 ergänzt, siehe dort — an dieser Stelle nur als leeres `middleware`-Array-Slot vorbereitet oder schlicht in P1.8 per `withPostMiddleware`/erneuter Konstruktion ergänzt; genaue Verdrahtung siehe P1.8-Aufgabe „401-Interceptor").
  - Refactor: keiner erforderlich.

**Aufgabe 5: 12 Fundstellen umstellen**

- Kein neuer Test nötig (reine Wire-up-Änderung, bestehende Store-/Component-Tests bleiben grüner
  Nachweis, dass sich das Verhalten nicht ändert — bestehende Tests wie `taskStore.spec.ts`,
  `categoryStore.spec.ts` laufen weiter, weil sie die jeweilige `*Api`-Klasse ohnehin mocken, nicht
  die `Configuration`).
- Alle 12 Stellen aus Abschnitt 2.6 ersetzen `const apiConfig = new Configuration({ basePath: '/api' });`
  durch `import { apiConfiguration } from '$lib/api/httpClient';` und `new XyzApi(apiConfiguration)`.
- Danach `npm run test` und `npx svelte-check` laufen lassen, um sicherzustellen, dass keine
  bestehenden Tests durch die Umstellung brechen.

### P1.7 — SetupWizard um Passwort erweitern

**Aufgabe 1: `PasswordField.svelte` mit Maskierung + Reveal-Toggle**

- Neue Datei `frontend/src/lib/PasswordField.svelte`.
- UI-only-Anteil (Maskierung/Toggle-Rendering) benötigt laut AGENTS.md keinen Test. Die
  Passwort-**Validierung** (Mindestlänge 8, Maximallänge 128 gemäß `Password`-Schema,
  `api/openapi.yml:2443-2449`) wird als pure Funktion ausgelagert:
- Neue Datei `frontend/src/lib/passwordValidation.ts`, Funktion `isValidPassword(password: string): boolean`.
- Test zuerst, `frontend/src/lib/passwordValidation.spec.ts`:
  - `describe('isValidPassword')`
    - `it('lehnt ein Passwort mit weniger als 8 Zeichen ab')`
    - `it('akzeptiert ein Passwort mit genau 8 Zeichen')`
    - `it('lehnt ein Passwort mit mehr als 128 Zeichen ab')`
    - `it('akzeptiert ein Passwort mit genau 128 Zeichen')`
  - Rot: Modul fehlt.
  - Grün: `password.length >= 8 && password.length <= 128`.
  - Refactor: keiner nötig.
- Component-Test für das Reveal-Toggle ist optional (UI-only); falls gewünscht als
  Interaktionstest analog `JoinWizard.svelte.spec.ts`-Muster:
  `it('zeigt das Passwort im Klartext nach Klick auf das Reveal-Icon')` — kein Pflichtbestandteil
  laut AGENTS.md, wird hier nur als mögliche Ergänzung erwähnt, nicht als Aufgabe eingeplant.

**Aufgabe 2: E-Mail-Verfügbarkeits-Check, debounced**

- Neue Datei `frontend/src/lib/emailAvailability.ts`, Funktion
  `createDebouncedAvailabilityChecker(checkFn: (email: string) => Promise<EmailAvailability>, delayMs: number)`,
  die eine debounced Prüf-Funktion zurückgibt (Pattern: letzter Aufruf gewinnt, vorherige
  Timer werden gecancelt).
- Test zuerst, `frontend/src/lib/emailAvailability.spec.ts` (Vitest Fake Timers):
  - `describe('createDebouncedAvailabilityChecker')`
    - `it('ruft checkFn erst nach Ablauf der Debounce-Zeit auf')`
    - `it('bricht einen ausstehenden Aufruf ab, wenn vorher erneut aufgerufen wird')`
    - `it('gibt das Ergebnis von checkFn zurück, wenn die Debounce-Zeit abgelaufen ist')`
  - Rot: Modul fehlt.
  - Grün: minimale Implementierung mit `setTimeout`/`clearTimeout` und Promise-Wrapping.
  - Refactor: prüfen, ob eine bereits vorhandene Bibliotheksfunktion existiert — laut
    `package.json` ist keine Debounce-Utility-Library vorhanden; eine neue Dependency nur für
    Debounce wäre laut AGENTS.md „Dependency Management" nicht gerechtfertigt („kein
    Trivial-Single-Use" — hier aber tatsächlich trivial genug für eine Eigenimplementierung, siehe
    „Do not add a dependency for a trivial single-use implementation").

**Aufgabe 3: 409-Differenzierung nach `Problem.type`**

- Neue Datei `frontend/src/lib/api/problemMapping.ts`, Funktion
  `classifyConflictProblem(problemType: string | undefined): 'household-exists' | 'account-exists' | 'unknown'`.
- Test zuerst, `frontend/src/lib/api/problemMapping.spec.ts`:
  - `describe('classifyConflictProblem')`
    - `it('erkennt /problems/household-already-exists')`
    - `it('erkennt /problems/account-already-exists')`
    - `it('gibt unknown für einen unbekannten oder fehlenden Problem-Type zurück')`
  - Rot: Modul fehlt.
  - Grün: einfaches String-Mapping der beiden bekannten Konstanten.
  - Refactor: Konstanten `HOUSEHOLD_ALREADY_EXISTS = '/problems/household-already-exists'` und
    `ACCOUNT_ALREADY_EXISTS = '/problems/account-already-exists'` statt Magic Strings (AGENTS.md
    „Replace magic numbers and magic strings with named constants").

**Aufgabe 4: SetupWizard verdrahten (Component-Test)**

- `SetupWizard.svelte` anpassen: `MemberProfileForm`-Aufruf um `PasswordField` ergänzen (entweder
  direkt in `MemberProfileForm.svelte` eingebettet oder als zusätzlicher Slot/Prop — Umsetzung
  gemäß Entscheidung 3.2), `localRegistration: { password }` in `HouseholdSetup` ergänzen,
  `AccountsApi.checkEmailAvailability` beim E-Mail-`oninput` (debounced) aufrufen, 409-Handling auf
  `classifyConflictProblem` + `extractErrorStatus` umstellen (dabei muss der rohe `Problem`-Body
  aus der `ResponseError.response` gelesen werden — `await err.response.json()` — um an
  `problem.type` zu kommen).
- Test zuerst (erweitert/neu), `frontend/src/lib/SetupWizard.svelte.spec.ts` (neu, folgt dem Muster
  aus `JoinWizard.svelte.spec.ts`):
  - `describe('SetupWizard')`
    - `it('Formular abschicken — ruft setupHousehold mit localRegistration.password auf')`
    - `it('E-Mail bereits vergeben (account-already-exists) — zeigt spezifische Fehlermeldung am E-Mail-Feld')`
    - `it('Haushalt existiert bereits (household-already-exists) — zeigt allgemeine Fehlermeldung per Toast')`
    - `it('E-Mail-Verfügbarkeits-Check — ruft checkEmailAvailability nach Debounce-Zeit auf')`
  - Rot: Komponente hat noch kein Passwortfeld/keine `AccountsApi`-Anbindung → Selektoren/Assertions
    schlagen fehl.
  - Grün: minimale Verdrahtung wie oben beschrieben.
  - Refactor: Duplizierte Statusauswertung durch `extractErrorStatus`/`classifyConflictProblem`
    ersetzen, `Configuration`-Instanz durch `apiConfiguration` aus P1.10 ersetzen.
- Neue i18n-Keys in `messages/de.json`/`messages/en.json` (Struktur analog `setup.create_account_step.*`):
  `setup.create_account_step.password_label`, `..._placeholder`, `..._hint`,
  `..._account_exists_error` (spezifisch für `account-already-exists`, unterscheidet sich vom
  bisherigen generischen `invite.email_taken`, das nur noch für Invite-Join genutzt wird), sowie
  ein Toast-Text für `household-already-exists`
  (`setup.create_account_step.household_exists_error`). `npm run paraglide` danach ausführen.

### P1.9 — Invite-Join mit Passwortvergabe

**Aufgabe 1: JoinWizard verdrahten (Component-Test)**

Analog P1.7/Aufgabe 4, aber für `JoinWizard.svelte`: `PasswordField` ergänzen,
`memberRegistration.localRegistration = { password }` setzen, 409-Handling auf
`extractErrorStatus`/`classifyConflictProblem` umstellen (bei Invite-Join ist laut
`api/openapi.yml:1475-1488` nur `account-already-exists` möglich — die Klassifizierung wird
trotzdem verwendet, um konsistent mit P1.7 zu bleiben und robust zu sein, falls das Backend später
weitere 409-Typen ergänzt).

- Test zuerst, Erweiterung von `frontend/src/lib/JoinWizard.svelte.spec.ts` (bestehende Datei, alle
  vorhandenen Tests bleiben unverändert grün):
  - `describe('JoinWizard')` (bestehende Gruppe)
    - `it('Formular abschicken — ruft joinHousehold mit localRegistration.password auf')` (Erweiterung
      des bestehenden Tests „ruft joinHousehold mit korrekten Daten auf", Zeile 57-81)
    - `it('409 account-already-exists — zeigt spezifische Fehlermeldung am E-Mail-Feld')` (Ersatz/
      Präzisierung des bestehenden Tests Zeile 104-115, der aktuell nur generisch auf Status 409
      reagiert)
  - Rot: `PasswordField` fehlt in der Komponente, `mockJoinHousehold` wird ohne `localRegistration`
    aufgerufen → `toHaveBeenCalledWith(expect.objectContaining({ localRegistration: { password: ... } }))`
    schlägt fehl.
  - Grün: minimale Verdrahtung analog SetupWizard.
  - Refactor: `Configuration`-Instanz durch `apiConfiguration` ersetzen, Statuslogik zentralisieren.
- Neue i18n-Keys analog `invite.password_label`, `..._placeholder`, `..._hint` in
  `messages/de.json`/`messages/en.json`, `npm run paraglide` danach ausführen.

### P1.8 — Login, Session-Bootstrap, Route-Guards, Logout

**Aufgabe 1: Session-State-Modul**

- Neue Datei `frontend/src/lib/stores/sessionState.svelte.ts` (Runes, siehe Entscheidung 3.3):
  `session`-Objekt, `setAuthenticated(user: CurrentUser)`, `setGuest()`.
- Test zuerst, `frontend/src/lib/stores/sessionState.svelte.spec.ts` (läuft im `client`-Projekt,
  da Runes verwendet werden):
  - `describe('sessionState')`
    - `it('setAuthenticated setzt status auf authenticated und currentUser auf den übergebenen User')`
    - `it('setGuest setzt status auf guest und currentUser auf null')`
    - `it('der initiale Status ist bootstrapping')`
  - Rot: Modul fehlt.
  - Grün: minimale Implementierung wie in 3.3 skizziert.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 2: Pure Guard-/Interceptor-Entscheidungsfunktionen**

- Neue Datei `frontend/src/lib/stores/sessionGuard.ts` (bewusst **keine** `.svelte.ts`-Endung —
  reine Funktionen ohne Runes-Bezug, laufen im `server`-Testprojekt):
  `shouldRedirectToLogin(status: SessionStatus): boolean`,
  `shouldTreatAsSessionExpiry(status: SessionStatus): boolean`.
- Test zuerst, `frontend/src/lib/stores/sessionGuard.spec.ts`:
  - `describe('shouldRedirectToLogin')`
    - `it.each(['guest'])('gibt true zurück für Status %s', ...)`
    - `it.each(['authenticated', 'bootstrapping'])('gibt false zurück für Status %s', ...)`
  - `describe('shouldTreatAsSessionExpiry')`
    - `it.each(['authenticated'])('gibt true zurück für Status %s', ...)`
    - `it.each(['guest', 'bootstrapping'])('gibt false zurück für Status %s', ...)`
  - Rot: Modul fehlt.
  - Grün: triviale Vergleiche wie in 3.6/3.7 beschrieben.
  - Refactor: keiner nötig.

**Aufgabe 3: Bootstrap-Funktion**

- Neue Datei `frontend/src/lib/stores/sessionBootstrap.ts`, Funktion `bootstrapSession(): Promise<void>`,
  die `SessionApi.getCurrentUser()` aufruft, bei Erfolg `setAuthenticated(user)` sowie
  `updateHouseholdState(...)`/`updateUserState(...)` aus dem `CurrentUser`-Payload befüllt, bei 401
  (erkannt über `extractErrorStatus(err) === 401`) `setGuest()` aufruft, bei jedem anderen Fehler
  ebenfalls `setGuest()` aufruft (fail-safe: im Zweifel Gast-Zustand, nicht fälschlich
  „authenticated" anzeigen) und einen Toast zeigt.
- Test zuerst, `frontend/src/lib/stores/sessionBootstrap.spec.ts` (Muster: `vi.mock` auf
  `../../generated-sources/openapi`, analog `JoinWizard.svelte.spec.ts:12-21`):
  - `describe('bootstrapSession')`
    - `it('bei 200 — setzt session auf authenticated mit dem zurückgegebenen CurrentUser')`
    - `it('bei 401 — setzt session auf guest, ohne einen Toast zu zeigen')`
    - `it('bei einem unerwarteten Fehler — setzt session auf guest und zeigt einen Toast')`
  - Rot: Modul fehlt.
  - Grün: minimale Implementierung wie beschrieben.
  - Refactor: keiner nötig.

**Aufgabe 4: Root-Layout ruft Bootstrap einmalig auf**

- `frontend/src/routes/+layout.svelte` erweitern: `if (browser) bootstrapSession();` neben dem
  bestehenden `if (browser) initSettings();` (Zeile 6).
- Kein zusätzlicher Test nötig — die aufgerufene Funktion ist bereits vollständig getestet
  (Aufgabe 3); der Aufruf selbst ist eine einzeilige Verdrahtung ohne Verzweigungslogik.

**Aufgabe 5: 401-Interceptor in `httpClient.ts` ergänzen (schließt P1.10 Teil B ab)**

- `frontend/src/lib/api/httpClient.ts` erweitern: `sessionExpiredMiddleware: Middleware['post']`
  hinzufügen, das bei `response.status === 401` und `shouldTreatAsSessionExpiry(session.status)`
  `setGuest()` aufruft und — falls `window.location.pathname` nicht bereits mit `/login` beginnt —
  per `goto('/login')` umleitet.
- Test zuerst, Erweiterung von `frontend/src/lib/api/httpClient.svelte.spec.ts`:
  - `describe('sessionExpiredMiddleware')`
    - `it('bei 401 und Status authenticated — setzt session auf guest und navigiert zu /login')`
    - `it('bei 401 und Status guest — ändert session nicht und navigiert nicht')`
    - `it('bei 401 und Status authenticated, aber bereits auf /login — navigiert nicht erneut')`
  - Rot: Middleware fehlt.
  - Grün: minimale Implementierung wie beschrieben, `goto` via `$app/navigation` gemockt
    (`vi.mock('$app/navigation', ...)`, Muster aus `JoinWizard.svelte.spec.ts:23`).
  - Refactor: `apiConfiguration`-Export in `httpClient.ts` um `sessionExpiredMiddleware` im
    `middleware`-Array ergänzen.

**Aufgabe 6: `/login`-Route (Variante a, siehe Entscheidung 3.1)**

- Neue Datei `frontend/src/routes/login/+page.svelte`: DaisyUI-gestyltes Formular
  (E-Mail + Passwort, gleiches Grundlayout wie `WelcomeScreen`), `<form method="post" action={loginActionUrl}>`.
  `loginActionUrl` ist eine Konstante/Konfigurationswert, dessen genauer Pfad aus dem
  P1.4-Backend-Feinplan übernommen wird (Platzhalter, z. B. `/api/login`, bis dahin abzustimmen).
- Kein `fetch`, daher kein API-Client-Test nötig für den Submit selbst. Falls eine
  Client-seitige Vorab-Validierung (Pflichtfelder, E-Mail-Format) ergänzt wird, gilt dafür
  regulär TDD wie in P1.7 Aufgabe 1 (Wiederverwendung von `isValidEmail` aus
  `setupWizardLogic.ts:118-121` möglich).
- Optionaler Component-Test (kein Pflichtbestandteil, da reine Formular-Markup-Struktur ohne
  JS-Verzweigungslogik): `it('rendert ein Formular mit method=post und der erwarteten action-URL')`.
- `frontend/src/lib/WelcomeScreen.svelte:39-41` bleibt unverändert (Link zeigt bereits auf
  `/login`, war zuvor nur tot, weil die Route fehlte).

**Aufgabe 7: Route-Guard für `/app/*`**

- `frontend/src/routes/app/+layout.svelte` erweitern: `$effect`, das `session.status` beobachtet
  und bei `shouldRedirectToLogin(session.status) === true` per `goto('/login')` umleitet; bei
  `session.status === 'bootstrapping'` einen Ladezustand (`<span class="loading loading-spinner">`)
  statt `children()` rendern.
- Test zuerst, neue Datei `frontend/src/routes/app/layout-guard.svelte.spec.ts` (Component-Test,
  Muster wie `JoinWizard.svelte.spec.ts`, `sessionState` wird vor jedem Test auf einen definierten
  Ausgangswert gesetzt statt gemockt, da es sich um echten Runes-State handelt):
  - `describe('App-Layout-Guard')`
    - `it('Status guest — navigiert zu /login und rendert children nicht')`
    - `it('Status bootstrapping — zeigt einen Ladeindikator und rendert children nicht')`
    - `it('Status authenticated — rendert children')`
  - Rot: Guard-Logik fehlt, `children()` wird immer gerendert.
  - Grün: minimale `$effect`+Conditional-Rendering-Implementierung wie beschrieben.
  - Refactor: Verzweigungsentscheidung nutzt ausschließlich `shouldRedirectToLogin` aus P1.8
    Aufgabe 2 (kein Duplikat der Bedingung im Template).

**Aufgabe 8: Logout**

- Neue Funktion `logout()` in `frontend/src/lib/stores/sessionBootstrap.ts` (oder eigene
  `sessionLogout.ts` — Empfehlung: eigene Datei `frontend/src/lib/stores/sessionLogout.ts`, um
  Bootstrap- und Logout-Verantwortlichkeit sauber zu trennen), die `SessionApi.logout()` aufruft,
  anschließend `setGuest()` sowie Reset von `householdState`/`userState` auf `undefined`, und dann
  per `goto('/')` navigiert.
- Test zuerst, `frontend/src/lib/stores/sessionLogout.spec.ts`:
  - `describe('logout')`
    - `it('ruft SessionApi.logout auf, setzt session auf guest und navigiert zu /')`
    - `it('setzt session auch dann auf guest, wenn der Logout-Request fehlschlägt')` (laut
      OpenAPI-Doku ist `/logout` idempotent und soll laut Beschreibung „always succeeds", ein
      Netzwerkfehler auf Client-Seite ist trotzdem denkbar — lokaler State wird im Zweifel
      trotzdem zurückgesetzt, damit der Nutzer nicht in einem inkonsistenten UI-Zustand hängen
      bleibt)
  - Rot: Funktion fehlt.
  - Grün: minimale Implementierung wie beschrieben, `goto` gemockt.
  - Refactor: keiner nötig.
- UI-Integration: Logout-Eintrag in `frontend/src/routes/app/settings/+page.svelte` ergänzen
  (analog zu den bestehenden Nav-Cards, Zeilen 77-129), ruft `logout()` auf Klick auf. Kein
  natives `confirm()` — falls eine Bestätigung gewünscht ist, DaisyUI-`<dialog class="modal">`
  gemäß AGENTS.md „UI Conventions" (optional, nicht zwingend Teil dieses Plans, da der Meta-Plan
  keine Bestätigung fordert; Empfehlung: ohne Bestätigungsdialog, da Logout keine destruktive,
  irreversible Aktion ist — jederzeit erneut einloggbar).
- Neue i18n-Keys: `settings.logout.title`/`settings.logout.description` (Struktur analog den
  bestehenden Einträgen `settings.user.title`/`...description`, Zeilen 87-88 in
  `frontend/src/routes/app/settings/+page.svelte`). `npm run paraglide` danach ausführen.

---

## 6. Out of Scope

- P0.x, P1.1–P1.2 (bereits umgesetzt/generiert).
- P1.3–P1.6 (Backend: `AccountRegistration`, Spring Authorization Server, Resource-Server-/BFF-
  Absicherung, Access Control je Haushalt/Rolle) — wird parallel geplant, hier nur konsumiert.
- P2.x (E-Mail-Verifikation, Passwort-Reset, Rate-Limiting) und P3.x (Social Login) — eigene,
  spätere Phasen laut Meta-Plan.
- Migration von `userState`/`householdState`/`taskStore` etc. auf Svelte-5-Runes — bewusst
  abgelehnte Erweiterung des Scopes, siehe Entscheidung 3.3.
- „Passwort ändern" (`PUT /household/{householdId}/members/{memberId}/password`,
  `PasswordChangeRequest`) in den Usersettings — nicht Teil von P1.7–P1.10, obwohl `PasswordField.svelte`
  (Entscheidung 3.2) dafür bewusst wiederverwendbar gebaut wird.
- Exakte Pfad-/Mechanik-Details des Backend-seitigen Login-Formular-Ersatzes (Variante a aus 3.1)
  — muss im P1.4-Feinplan bestätigt werden; hier nur als Annahme dokumentiert.
- Automatisiertes Silent-Refresh o. Ä. — das BFF-Cookie-Modell braucht laut Meta-Plan keine
  clientseitige Token-Refresh-Logik.
- Rate-Limiting/Lockout-UI (P2.7, Backend-seitig, kein Frontend-Bestandteil in P1).

---

## 7. Akzeptanzkriterien

- [ ] `npx svelte-check` liefert keine Fehler mehr für `SetupWizard.svelte`/`JoinWizard.svelte`
      (behebt die in Abschnitt 2.2 dokumentierten Compile-Fehler).
- [ ] Alle 12 in Abschnitt 2.6 gelisteten Fundstellen nutzen die zentrale `apiConfiguration` aus
      `frontend/src/lib/api/httpClient.ts`; keine verstreute `new Configuration({...})`-Instanz
      mehr außerhalb dieser einen Datei.
- [ ] Jeder state-changing Request (`POST`/`PUT`/`PATCH`/`DELETE`) über die generierten API-Klassen
      trägt automatisch den `X-XSRF-TOKEN`-Header, sofern ein `XSRF-TOKEN`-Cookie gesetzt ist; `GET`-
      Requests tragen ihn nicht.
- [ ] Alle Requests über die generierten API-Klassen werden mit `credentials: 'include'` gesendet.
- [ ] SetupWizard: Passwortfeld (maskiert, mit Reveal-Toggle) vorhanden; `localRegistration.password`
      wird an `setupHousehold` mitgeschickt; E-Mail-Verfügbarkeit wird debounced geprüft;
      `account-already-exists` und `household-already-exists` erzeugen unterscheidbare
      Fehlermeldungen (spezifisch am E-Mail-Feld bzw. per Toast).
- [ ] JoinWizard: Passwortfeld vorhanden; `localRegistration.password` wird an `joinHousehold`
      mitgeschickt; `account-already-exists` erzeugt eine spezifische Fehlermeldung am E-Mail-Feld.
- [ ] `/login` ist eine erreichbare Route mit echtem `<form method="post">` gegen die
      Backend-Login-URL (kein `fetch`); der Link in `WelcomeScreen.svelte` funktioniert.
- [ ] Nach einem Hard-Reload mit gültiger Backend-Session wird der Nutzer über `GET /me` erkannt
      und landet nicht mehr im Gast-Zustand (behebt den in Abschnitt 2.5 dokumentierten Bug).
- [ ] `GET /me` wird beim App-Start genau einmal aufgerufen, nicht bei jedem Wechsel zwischen
      Routen unterhalb von `/app`.
- [ ] Unauthentifizierter Zugriff auf eine `/app/*`-Route leitet zu `/login` um; während des
      Bootstrap-Ladevorgangs wird kein vorzeitiger Redirect ausgelöst.
- [ ] Ein 401 auf einen beliebigen authentifizierten Request während einer laufenden Session setzt
      den Zustand zurück und leitet zu `/login` um; ein 401 auf den initialen `/me`-Bootstrap-Call
      löst **keinen** Redirect aus (reiner Gast-Zustand).
- [ ] Logout ruft `SessionApi.logout()` auf, setzt den lokalen Zustand zurück und navigiert weg von
      `/app`.
- [ ] Für jede in Abschnitt 5 aufgeführte Business-Logik-Funktion existiert mindestens ein
      Vitest-Testfall pro Verzweigung (positiv + je Fehlerpfad negativ), Namensmuster
      `methodName_input_expectedOutput`-Äquivalent in den `describe`/`it`-Titeln (deutschsprachig,
      wie im bestehenden Code üblich, siehe `JoinWizard.svelte.spec.ts`).
- [ ] `npm run test`, `npm run lint`, `npm run check` sind grün.
- [ ] Neue i18n-Keys sind in `messages/de.json` **und** `messages/en.json` gepflegt,
      `npm run paraglide` wurde ausgeführt.