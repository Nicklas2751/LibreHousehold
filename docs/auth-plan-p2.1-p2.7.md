# Implementierungsplan P2.1–P2.7: Lifecycle-Härtung (E-Mail-Verifikation, Passwort-Reset, Rate-Limiting)

Referenz: [`docs/auth-meta-plan.md`](auth-meta-plan.md), Abschnitt „Phase 2 — Lokale Accounts:
Lifecycle-Härtung (OWASP ASVS)" (Zeilen 136–148), Punkte **P2.1–P2.7**. Dieses Dokument ist ein
Arbeitsdokument (kein Arc42-Kapitel, kein ADR) und wird nach Umsetzung nicht dauerhaft gepflegt.

**Scope:** E-Mail-Verifikation (P2.1–P2.3), Passwort-Reset (P2.4–P2.6) und Rate-Limiting/Lockout
(P2.7) für **lokale** Accounts. Explizit **nicht** Teil dieses Plans:

- Social Login (Phase 3) — Verifikationsstatus für federated Accounts ist ein eigenes Thema
  (externe Provider verifizieren i. d. R. selbst), hier nicht mitgeplant.
- Änderungen an Access Control/Rollen (P1.6, bereits umgesetzt).
- Neue Named Interfaces zwischen `household` und `notifications` über den hier beschriebenen
  Domain-Event-Mechanismus hinaus.

**Hinweis zur Struktur:** Die als Vorlage referenzierten Detailpläne `docs/auth-plan-p1.1-p1.2.md`
und `docs/auth-plan-p1.3-p1.6.md` existieren nicht mehr im Repository (Pläne sind temporäre
Arbeitsdokumente). `docs/auth-plan-p1.7-p1.10.md` ist zum Zeitpunkt der Erstellung dieses
Dokuments als Staged-Deletion im Arbeitsverzeichnis markiert (nicht Teil dieser Aufgabe, nicht
angefasst) und wurde stattdessen per `git show HEAD:docs/auth-plan-p1.7-p1.10.md` als
Stilvorlage gelesen. Dieses Dokument übernimmt dessen Gliederung.

**Stand der Design-Entscheidungen:** Alle sechs Punkte in Abschnitt 3 wurden ursprünglich als
Empfehlung mit Alternative(n) entworfen und anschließend mit dem Nutzer im Detail durchgesprochen
(siehe je Punkt „✅ entschieden"/„Entscheidung (vom Nutzer bestätigt)"). Bei 3.4 (Rate-Limiting-
Bibliothek) wurde dabei zusätzlich die laut AGENTS.md „Dependency Management" nötige Rückfrage vor
dem Hinzufügen von `bucket4j-core` gestellt und beantwortet. Bei 3.6 (Ort des
Verifikationshinweises) wich die tatsächliche Entscheidung von der Empfehlung ab (Ergänzung um
lokal per `localStorage` gemerkten Banner-„geschlossen"-Zustand) — das führte zu einer separaten
Klärung, ob damit die bestehende Session-State-Regel (kein localStorage für Session-/Auth-Zustand,
Abschnitt 1) generell gelockert werden soll; Ergebnis: nein, nur für reine UI-Präferenzen wie
dieses Banner, der eigentliche Verifikationsstatus bleibt allein aus `GET /me` abgeleitet (siehe
Abschnitt 1 und 3.6).

Status: Entwurf, noch nicht umgesetzt.

---

## 1. Bindende Vorgaben

Übernommen aus dem Meta-Plan und den referenzierten ADRs, hier nur die für Phase 2 relevanten
Konsequenzen:

- **ADR-009 (Argon2id):** `PasswordEncoderConfig` (`backend/src/main/java/eu/wiegandt/librehousehold/config/PasswordEncoderConfig.java`)
  stellt bereits den `PasswordEncoder`-Bean; ein per Reset gesetztes Passwort läuft durch denselben
  Encoder wie Setup/Join/Change-Password (`AccountService`, siehe Abschnitt 2.2) — keine neue
  Hashing-Logik nötig.
- **ADR-011 (Named Interfaces vs. Domain Events):** Der E-Mail-Versand bei Registrierung
  (Verifikations-Mail) und bei Reset-Anforderung **muss** als Domain Event vom `household`-Modul
  publiziert und vom `notifications`-Modul per `@ApplicationModuleListener` konsumiert werden
  (Muster: „Reaktion auf Lifecycle-Event", siehe ADR-011 Entscheidungsregel-Tabelle, Zeile
  „Notification: X was deleted" → Domain Event; analog hier „Account wurde registriert" /
  „Passwort-Reset wurde angefordert"). Kein synchroner Named-Interface-Call vom `household`- ins
  `notifications`-Modul, weil E-Mail-Versand die produzierende Transaktion (Setup/Join/
  Reset-Request) nicht blockieren soll und `household` nicht wissen muss, dass `notifications`
  existiert.
- **ADR-012 (1 Account : 1 Household):** Verifikations- und Reset-Token hängen am `member`
  (per `member_id`/E-Mail), nicht an einer separaten Account-Cross-Household-Identität — konsistent
  mit dem bestehenden Modell, keine neue Fragestellung hier.
- **ADR-013/ADR-014 (Spring Authorization Server, BFF-Cookie-Session):** Ein Passwort-Reset darf
  keine Tokens ans Frontend geben; die einzige Schnittstelle bleibt die bestehende
  Cookie-Session. Die „Invalidierung bestehender Sessions nach Reset" (P2.5) muss serverseitig
  über den bestehenden Authorization-Server-/Servlet-Session-Mechanismus erfolgen, nicht über
  einen neuen Frontend-Mechanismus.
- **ADR-015 (dedizierte `account`-Tabelle):** Neue Spalten/Tabellen für Verifikationsstatus und
  Token-Ablage gehören — konsistent mit der dort begründeten Trennung „`member` = Haushalts-
  Mitgliedschaft, `account` = Login-Identität" — an/neben `account`, nicht an `member`.
- **OWASP ASVS 5.0 L2** (`docs/architecture/chapters/08_concepts.adoc#section-security-controls-authentication`,
  Zeilen 31–119): Diese Phase adressiert konkret **RATE1** (Zeile 100–103, „deliberately deferred to
  a dedicated future hardening effort" — das ist exakt P2.7) und **ENUM1** (Zeile 90–93, bereits
  „Decided" für Login/Registrierung/Passwort-Reset — muss für die neuen P2.1/P2.4-Endpoints
  **fortgeführt**, nicht neu erfunden werden: keine unterscheidbaren Antworten je nachdem, ob eine
  E-Mail existiert). Für Verifikation/Reset selbst existiert **noch kein** Eintrag in dieser Tabelle
  — siehe Abschnitt 2.7 zur Doku-Konsequenz.
- **TDD-Pflicht** (AGENTS.md): Unit-Tests mocken nur direkte Dependencies, Mapper werden nie
  gemockt, jede DB-lesende/schreibende Service-Methode braucht mindestens einen Happy-Path-`*IT`-Test
  (`@DataJdbcTest`/`@SpringBootTest` + Testcontainers, siehe `HouseholdSetupServiceIT`,
  `AccountServiceIT` als Referenzmuster, Abschnitt 2.5). Kein `@BeforeEach` für Testdaten — die
  einzige akzeptierte Ausnahme im bestehenden Code ist rein technisches Request-Context-Binding
  (`HouseholdSetupServiceIT.bindRequestContext()`, Zeile 71–75), keine Testdaten.
- **Keine neue Dependency ohne Rückfrage** (AGENTS.md „Dependency Management"): betrifft konkret
  P2.7 (Rate-Limiting-Bibliothek) — siehe Abschnitt 3.4, dort als offene Frage markiert, **nicht**
  selbst entschieden.
- **DaisyUI-Modals statt `confirm()`/`alert()`/`prompt()`** und **Toast-Store für Fehler**
  (AGENTS.md „UI Conventions"/„Error Handling") — relevant für P2.3 (Resend-Bestätigung) und P2.6
  (Erfolgsmeldung nach Reset-Request).
- **localStorage nur für reine UI-Präferenzen, nicht für Session-/Auth-Zustand** (vom Nutzer in
  dieser Runde geklärt, siehe Abschnitt 3.6): Der Verifikationsstatus selbst kommt weiterhin
  ausschließlich aus `CurrentUser` (`GET /me`, `api/openapi.yml:1499-1526`) und wird bei jedem
  Reload neu aus der Session abgeleitet — er wird **nicht** in localStorage gecacht. Erlaubt ist
  localStorage nur für eine nicht-sicherheitsrelevante UI-Präferenz wie „Banner geschlossen"
  (P2.3, siehe 3.6). Die weitergehende Frage, ob künftig auch Session-/Auth-Zustand selbst
  (`currentUser`, Login-Status) in localStorage gecacht werden darf — was `sessionState.svelte.ts`
  und die ADR-014-Begründung (BFF/XSS-Blast-Radius) beträfe —, ist explizit **nicht** Teil dieses
  Plans und wird bei Bedarf als eigenes Vorhaben (ADR-014-Revision) behandelt.

---

## 2. Ist-Zustand (mit Datei-/Zeilenreferenzen)

### 2.1 `notifications`-Modul: Verzeichnisse existieren, aber komplett leer

`find backend/src/main/java/eu/wiegandt/librehousehold/notifications` liefert genau zwei leere
Verzeichnisse: `notifications/internal/` und `notifications/service/` — **keine einzige Java-Datei**.
Ebenso `backend/src/test/java/eu/wiegandt/librehousehold/notifications/internal/` (leer). Kein
`package-info.java` existiert für irgendein Modul im gesamten Backend (verifiziert:
`find backend/src/main/java -iname package-info.java` liefert nichts) — konsistent mit
`docs/architecture/chapters/11_technical_risks.adoc` TD1, das genau das als offene Tech-Debt
benennt („Module boundaries are not yet technically enforced: no `package-info.java` with
`@ApplicationModule(allowedDependencies = ...)` exists"). **P2.2 legt hier den ersten echten Code
im `notifications`-Modul an** — siehe Abschnitt 3.1 zur offenen Frage, wie die vorbereitete
`internal`/`service`-Aufteilung genutzt wird.

`spring-boot-starter-mail` ist bereits als Produktions-Dependency vorhanden
(`backend/pom.xml:71-73`), `spring-boot-starter-mail-test` als Test-Dependency
(`backend/pom.xml:192-195`) — **keine neue Dependency für den E-Mail-Versand selbst nötig**.
`grep -rn "JavaMailSender" backend/src` liefert keinen Treffer — der Starter ist deklariert, aber
bisher nirgends injiziert/verwendet. Keine SMTP-Konfiguration in
`backend/src/main/resources/application.yaml` (einzige Konfigurationsdatei, siehe Abschnitt 2.6).

### 2.2 `account`-Schema: kein Verifikations-/Reset-Feld, `isNew()` immer `true`

`backend/src/main/resources/db/migration/household/V1__create_household_and_member.sql:26-29`:

```sql
CREATE TABLE account
(
    member_id     UUID PRIMARY KEY REFERENCES member (id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL
);
```

Kein `email_verified`, kein Token-bezogenes Feld. `AccountEntity`
(`backend/src/main/java/eu/wiegandt/librehousehold/household/model/AccountEntity.java:12-31`) ist
ein Record, das `Persistable<UUID>` implementiert und `isNew()` **immer** `true` zurückgibt (Zeile
23–25) — Updates laufen daher zwingend über `@Modifying @Query`, nicht über `save()`
(siehe `AccountRepository.updatePasswordHash`,
`backend/src/main/java/eu/wiegandt/librehousehold/household/repository/AccountRepository.java:13-15`).
Jede neue Spalte auf `account` (z. B. `email_verified`) braucht also ebenfalls eine
`@Modifying @Query`-Methode, kein `save()`.

`AccountService`
(`backend/src/main/java/eu/wiegandt/librehousehold/household/service/AccountService.java`) hat
aktuell genau zwei Methoden: `createAccount(memberId, rawPassword)` (Zeile 23–25) und
`changePassword(memberId, oldPassword, newPassword)` (Zeile 27–33, wirft
`InvalidPasswordException`, wenn `oldPassword` nicht passt). **Kein** `resetPassword`, **kein**
Verifikations-Bezug.

Es existiert **keine** ADR zu Token-Ablage für Verifikation/Reset — `docs/architecture/adrs/`
enthält 15 ADRs (adr-001 bis adr-015), keine davon behandelt Einmal-Token. Diese Design-Frage ist
für diesen Plan komplett offen (siehe Abschnitt 3.2).

### 2.3 Session-/Authorization-Server-Infrastruktur: kein Mechanismus zur gezielten Session-Invalidierung

`SecurityConfig`
(`backend/src/main/java/eu/wiegandt/librehousehold/config/SecurityConfig.java`) konfiguriert zwei
Filter-Chains (Authorization-Server-Chain, Zeile 156–174; Default-Chain, Zeile 176–235), aber
**keinen** `SessionRegistry`/`ConcurrentSessionControl`-Bean — `grep -rn "SessionRegistry"
backend/src` liefert keinen Treffer. D. h. es gibt aktuell **keine** Möglichkeit, "alle aktiven
`HttpSession`s eines Principals" serverseitig aufzuzählen und zu invalidieren.

Zwei bestehende, thematisch angrenzende Bausteine:

- `RevokeAuthorizedClientLogoutHandler`
  (`backend/src/main/java/eu/wiegandt/librehousehold/config/RevokeAuthorizedClientLogoutHandler.java`)
  entfernt bei explizitem Logout den `OAuth2AuthorizedClient` des **aktuellen** Principals
  (`authorizedClientService.removeAuthorizedClient(CLIENT_ID, authentication.getName())`, Zeile
  21) — das betrifft nur die eine gerade abgemeldete Session, kein Cross-Session-Widerruf.
- `AccountSessionAuthenticator`
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/service/AccountSessionAuthenticator.java`)
  **etabliert** programmatisch eine neue authentifizierte Session (nach Setup/Join, siehe
  Klassenkommentar Zeile 13–19) über `HttpSessionSecurityContextRepository` — das ist die
  Gegenrichtung dessen, was P2.5 braucht, aber zeigt das Muster, wie außerhalb eines echten
  `formLogin()`-Requests mit der `HttpSession`/`SecurityContext` programmatisch interagiert wird
  (`RequestContextHolder`, Zeile 61 in dieser Datei).

Für "alle Sessions eines Accounts nach Reset invalidieren" existiert also **kein** bestehender
Mechanismus — das ist eine echte Neuentwicklung, nicht nur eine Erweiterung (siehe Abschnitt 3.3).

`oauth2_authorization`-Tabelle
(`backend/src/main/resources/db/migration/household/V1__create_household_and_member.sql:41-73`,
Spring Authorization Servers eigenes JDBC-Schema) hält `principal_name` pro ausgestelltem Access-/
Refresh-/ID-Token — grundsätzlich abfragbar/löschbar per `principal_name = email`, falls der Reset
zusätzlich alle ausgestellten OAuth2-Tokens widerrufen soll (nicht nur die `HttpSession`).

### 2.4 OpenAPI-Konventionen (für P2.1/P2.4 fortzuführen)

Bestehende Muster in `api/openapi.yml`, die für die neuen Endpoints übernommen werden sollten:

- **`Password`-Schema** (`api/openapi.yml:2410-2416`): `minLength: 8`, `maxLength: 128`,
  `format: password` — für das neue Passwort im Reset-Confirm wiederverwenden
  (`$ref: "#/components/schemas/Password"`), nicht neu definieren.
- **Nicht-enumerierendes 200/204 statt 404 bei „E-Mail könnte existieren"-Endpoints:** Bereits
  gelebtes Muster ist zwar `checkEmailAvailability` (`api/openapi.yml:1460-1497`), das **bewusst**
  `EmailAvailability{available:boolean}` zurückgibt (dieser Endpoint ist explizit zum Prüfen von
  Verfügbarkeit gedacht, kein ENUM1-Verstoß). Für den **Reset-Request-Endpoint** (P2.4) gilt
  jedoch das Gegenteil: ENUM1 (`08_concepts.adoc:90-93`) verlangt hier, dass die Antwort **nicht**
  verrät, ob die E-Mail existiert — anders als `checkEmailAvailability`, das genau dafür da ist.
  Der Reset-Request-Endpoint muss daher unabhängig vom Ergebnis identisch antworten (siehe
  Aufgaben P2.4).
- **409 mit `Problem.type`-Unterscheidung** (`api/openapi.yml:74-97`, Muster für
  `setupHousehold`): Für Token-bezogene Fehler (abgelaufen vs. bereits eingelöst vs. nicht
  gefunden) sollte dasselbe Muster verwendet werden — eigene `/problems/...`-Type-Werte statt
  undifferenziertem Statuscode, konsistent mit `classifyConflictProblem` im Frontend (siehe
  Abschnitt 2.5).
- **`security: []` für unauthentifizierte Endpoints** (z. B. `api/openapi.yml:55` bei
  `setupHousehold`, `api/openapi.yml:1411` bei `joinHousehold`, `api/openapi.yml:1470` bei
  `checkEmailAvailability`): Reset-Request, Reset-Confirm, Verifikations-Confirm und
  Verifikations-Resend sind alle unauthentifiziert (der Nutzer hat per Definition keine gültige
  Session, wenn er sein Passwort vergessen hat) bzw. teilweise authentifiziert (Resend kann sowohl
  vor als auch nach Login sinnvoll sein, siehe Abschnitt 3.5).
- **Tags:** `members` (`api/openapi.yml:34-35`) passt für Verifikations-Resend/-Confirm (analog
  `checkEmailAvailability`, ebenfalls auf `Account`/`Member` bezogen); für Reset-Request/-Confirm
  ist `session` (`api/openapi.yml:36-37`) naheliegend, da inhaltlich näher an Login/Session-Handling
  als an Member-Verwaltung.
- **`CurrentUser`** (`api/openapi.yml:2594-2607`) hat noch **kein** `emailVerified`-Feld — P2.1
  muss es dort ergänzen, damit das Frontend (P2.3) den Hinweis überhaupt anzeigen kann, ohne einen
  zusätzlichen Request zu brauchen.

### 2.5 Backend-Service-/Controller-Struktur (Ansatzpunkte für neue Endpoints)

- `MembersApiDelegateImpl`
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/controller/MembersApiDelegateImpl.java`)
  ist der bestehende Ort für account-bezogene, aber member-zentrierte Endpoints
  (`checkEmailAvailability` Zeile 32–34, `changePassword` Zeile 82–87) — konsistenter Ort für
  Verifikations-Resend/-Confirm.
- `SessionApiDelegateImpl`
  (`backend/src/main/java/eu/wiegandt/librehousehold/session/controller/SessionApiDelegateImpl.java`)
  ist der bestehende Ort für Session-nahe, aber unauthentifiziert erreichbare Endpoints — passt
  für Reset-Request/-Confirm (`session`-Modul, eigenes `session.exception`-Package für
  `NoAuthenticatedSessionException`, analog nutzbar für Token-Fehler).
- `HouseholdSetupService.setupHousehold`
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/service/HouseholdSetupService.java:49-78`)
  und `MemberManagementService.joinHousehold`
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/service/MemberManagementService.java:88-102`)
  sind die beiden Stellen, an denen ein Account entsteht — beide rufen bereits
  `accountService.createAccount(...)` auf (Zeile 64 bzw. 101) und sind daher die beiden Stellen, an
  denen P2.2 das `AccountRegistered`-Domain-Event publizieren muss (`ApplicationEventPublisher`,
  bereits als Feld in `MemberManagementService` vorhanden, Zeile 40; in `HouseholdSetupService`
  noch **nicht** injiziert — muss ergänzt werden).
- Bestehendes Event-Muster: `MemberRemoved`
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/MemberRemoved.java`, Record im
  **Root-Package** `household`, javadoc erklärt „Defined in the public package so consuming
  modules can reference the type"), publiziert in `MemberManagementService.deleteMemberAndPublishEvent`
  (Zeile 145–148: `eventPublisher.publishEvent(new MemberRemoved(memberId))`), konsumiert per
  `@ApplicationModuleListener` in `TaskService.onHouseholdDeleted`
  (`backend/src/main/java/eu/wiegandt/librehousehold/tasks/service/TaskService.java:117-120`,
  analoges Muster für `HouseholdDeleted`). **Exakt dieses Muster** wird für
  `AccountRegistered`/`PasswordResetRequested` übernommen.
- **IT-Test-Referenzmuster:** `HouseholdSetupServiceIT`
  (`backend/src/test/java/eu/wiegandt/librehousehold/household/service/HouseholdSetupServiceIT.java`)
  zeigt `@SpringBootTest(webEnvironment = MOCK)` + `RequestContextHolder`-Bindung (Zeile 71–75, die
  einzige akzeptierte `@BeforeEach`-Ausnahme, siehe Abschnitt 1) für Tests, die
  `AccountSessionAuthenticator` durchlaufen. `AccountServiceIT`
  (`backend/src/test/java/eu/wiegandt/librehousehold/household/service/AccountServiceIT.java:54-93`)
  zeigt das Muster für reine `AccountService`-Tests ohne Session-Bezug (`webEnvironment = NONE`,
  Argon2id-Hash-Assertion `startsWith("$argon2id$")`, Zeile 70) — direktes Vorbild für einen neuen
  `resetPassword`-Test.

### 2.6 Konfiguration (`application.yaml`)

`backend/src/main/resources/application.yaml` ist die einzige Konfigurationsdatei (kein
`application.properties`, kein Profil-Split). Bestehende Konvention für neue, projekteigene
Settings: Namespace `librehousehold.security.*`
(`librehousehold.security.oauth2-authorization-server.issuer` Zeile 46,
`librehousehold.security.oauth2-client.redirect-uri` Zeile 48,
`librehousehold.security.cors.allowed-origins` Zeile 58, jeweils mit erklärendem Kommentarblock
direkt darüber). Neue Settings für Token-Gültigkeit (P2.1/P2.4) und Rate-Limits (P2.7) sollten
diesem Muster folgen, z. B. `librehousehold.security.email-verification.token-validity` /
`librehousehold.security.password-reset.token-validity` (Kommentar-Konvention: Grund für den
gewählten Default direkt im Kommentar, wie bei den bestehenden Einträgen).

### 2.7 Sicherheits-Checkliste (Chapter 8) hat noch keine Einträge für P2.1–P2.6

`docs/architecture/chapters/08_concepts.adoc:36-119` (Security-Controls-Tabelle) enthält aktuell
**RATE1** (Zeile 100–103, „deliberately deferred", = P2.7) und **ENUM1** (Zeile 90–93, bereits
„Decided", muss für die neuen Endpoints fortgeführt werden), aber **keinen** Eintrag für
E-Mail-Verifikations-Integrität oder Reset-Token-Sicherheit selbst (z. B. „Reset-Token ist
Single-Use", „Reset-Token hat eine kurze Gültigkeit", „Verifikations-Link invalidiert sich nach
Nutzung"). Anders als generische Doku-Pflege (Meta-Plan X.1, querschnittlich für z. B. `AGENTS.md`)
ist das hier **Teil dieses Plans**: Diese Tabelle ist laut P0.5 (Meta-Plan) die Akzeptanzkriterien-
Quelle für P1–P3, RATE1/ENUM1 sind die konkreten, diesem Plan zugeordneten Einträge, und neue
Einträge für Verifikations-/Reset-Token-Sicherheit entstehen unmittelbar aus den in Abschnitt 3.2
getroffenen Entscheidungen dieses Plans. Die konkreten Aufgaben dazu stehen bei den jeweiligen
P-Punkten in Abschnitt 5 (P2.2 Aufgabe 7, P2.5 Aufgabe 6, P2.7 Aufgabe 4).

### 2.8 Frontend-Bausteine (bereits vorhanden, für P2.3/P2.6 wiederverwendbar)

Alle in `auth-plan-p1.7-p1.10.md` geplanten Bausteine sind mittlerweile umgesetzt und liegen im
Arbeitsverzeichnis:

- `frontend/src/lib/PasswordField.svelte` (Props `label`, `hint`, `placeholder`, `value`
  (`$bindable`), `autocomplete`, `name`; Reveal-Toggle über `EyeIcon`/`EyeClosedIcon`,
  `minlength="8"`/`maxlength="128"` bereits gesetzt, Zeile 1–36) — direkt wiederverwendbar für das
  neue Passwort im Reset-Confirm-Formular (P2.6).
- `frontend/src/lib/stores/sessionState.svelte.ts` (Runes-State `session: {status, currentUser}`,
  `setAuthenticated`/`setGuest`) — `currentUser` liefert nach P2.1 auch `emailVerified`
  (Abschnitt 2.4), P2.3 liest das direkt von dort, kein neuer State nötig.
- `frontend/src/lib/api/httpClient.ts` (zentrale `apiConfiguration` mit `csrfMiddleware` +
  `sessionExpiredMiddleware`, Zeile 38–42) — alle neuen API-Client-Aufrufe (Resend, Reset-Request,
  Reset-Confirm) laufen automatisch durch CSRF-Header-Injektion und 401-Handling, ohne eigene
  Konfiguration.
- `frontend/src/lib/toast.ts` / `frontend/src/lib/stores/toastStore.ts` /
  `frontend/src/lib/Toasts.svelte` — bestehendes Toast-System für Resend-Bestätigung (P2.3) und
  Reset-Request-Bestätigung (P2.6).
- `frontend/src/routes/login/+page.svelte` (Zeile 1–41: natives `<form>`-POST gegen
  `formLogin()`, kein `fetch`-JSON) — P2.6 ergänzt hier einen „Passwort vergessen?"-Link; es gibt
  noch **keine** `/forgot-password`- oder `/reset-password/[token]`-Route
  (`find frontend/src/routes -iname "*forgot*" -o -iname "*reset*"` liefert nichts).
- `frontend/src/routes/app/settings/user/+page.svelte` (`settings.user.password`-Sektion, siehe
  `frontend/messages/de.json:83-92`) ist der bestehende „Passwort ändern"-Ort — der P2.3-
  Verifikationshinweis gehört eher auf ein global sichtbares Element (z. B. ein Banner in
  `frontend/src/routes/app/+layout.svelte`, das bei `!currentUser.emailVerified` erscheint) als in
  die Settings-Unterseite, da er nicht nur beim gezielten Aufsuchen der Settings sichtbar sein
  soll (siehe Abschnitt 3.6).
- i18n-Konvention (`frontend/messages/de.json`/`en.json`): verschachtelte Keys pro Screen/Flow
  (`setup.create_account_step.*`, `invite.*`, `login.*`, `settings.user.password.*`) — neue Keys
  folgen `verification.*` bzw. `forgot_password.*`/`reset_password.*`.

### 2.9 Konsequenzen bei dauerhaft unverifizierter E-Mail-Adresse — Recherche für Entscheidungen 3.7–3.9

Ursprünglich sah dieser Plan **keine** Konsequenz bei Nichtverifikation vor (nur der Hinweis-Banner
aus 3.6). Auf Nachfrage wurde das um echte Durchsetzung erweitert. Recherchierte Anknüpfungspunkte:

- **Login-Blocking-Mechanismus bereits vorhanden, nur ungenutzt:** `AccountPrincipal`
  (`.../household/AccountPrincipal.java:47-50`) implementiert `UserDetails.isEnabled()` bereits,
  gibt aktuell hart `true` zurück. Spring Securitys `AbstractUserDetailsAuthenticationProvider`
  prüft `isEnabled()` in den `DefaultPreAuthenticationChecks` **vor** dem Passwortabgleich und wirft
  bei `false` eine `DisabledException` — ein Login mit `emailVerified == false` kann also ohne neue
  Infrastruktur blockiert werden, indem `isEnabled()` den `emailVerified`-Wert zurückgibt.
  `AccountUserDetailsService.loadUserByUsername` (Zeile 22-29) baut `AccountPrincipal` aktuell nur
  aus `email`+`passwordHash` — muss um `account.emailVerified()` erweitert werden.
- **Kein Sonderfall für die erste Session nach Setup/Join nötig:** `AccountSessionAuthenticator`
  (siehe P1.8-Nachtrag, Abschnitt 2.3) authentifiziert die erste Session direkt, **ohne** über
  `AccountUserDetailsService`/den regulären `formLogin()`-Pfad zu laufen — ein frisch registrierter,
  noch unverifizierter Nutzer bleibt also nach Setup/Join eingeloggt, wird aber beim nächsten
  regulären Login (nach Logout/Session-Ablauf) durch die `isEnabled()`-Prüfung abgewiesen. Das
  entspricht exakt der Anforderung „sobald man sich ausloggt oder der Login abläuft, soll ein
  erneuter Login nicht möglich sein".
- **`member.email` ist zugleich die Login-Kennung — Ändern ist über einen bereits bestehenden,
  generischen Endpoint möglich:** `MemberManagementService.updateMember` (Zeile 108-119) erlaubt
  optional `update.getEmail()` zu setzen (`memberRepository.updateEmail`, Zeile 115) — das ist
  bereits heute der (einzige) Weg, die eigene Login-E-Mail zu ändern (ADR-012: `member.email` **ist**
  der Account-Identifier). Der Endpoint (`MembersApiDelegateImpl.updateMember`, Zeile 61-66) ist
  sowohl für den Nutzer selbst als auch für den Household-Admin freigegeben
  (`isSelf(...) or isAdminOfHousehold(...)`) und bündelt `name`/`email`/`avatar` in einem PATCH —
  eine Sperre für „E-Mail ändern" muss also **feldspezifisch** greifen (nur beim `email`-Teilupdate),
  nicht den ganzen Endpoint blockieren, da `name`/`avatar` laut Auftrag weiterhin änderbar bleiben
  sollen (nur Passwort/E-Mail explizit ausgenommen).
- **Passwort-Reset-Anfrage (P2.4/P2.5) bleibt bewusst unberührt:** Der Reset-Flow läuft
  unauthentifiziert über den Token-Link in der E-Mail — das Erhalten und Anklicken dieses Links ist
  selbst bereits ein Nachweis über den Mailbox-Zugriff. Ihn zusätzlich hinter einer
  Verifikationsprüfung zu sperren, würde genau den Fall verschlechtern, den ein Reset eigentlich
  auffangen soll (Nutzer kommt nicht mehr an sein Konto). Nur das **authentifizierte** „Passwort
  ändern" (`AccountService.changePassword`) ist von der Anforderung gemeint.
- **Einladungslink unabhängig vom Verifikationsstatus des Admins:** `resolveInvite`/`joinHousehold`
  (`MemberManagementService`) prüfen aktuell nirgends einen `emailVerified`-Status des einladenden
  Mitglieds — der Invite-Mechanismus ist unabhängig von der Account-Tabelle. Keine Code-Änderung
  nötig, nur eine Klarstellung, dass hier bewusst **nichts** ergänzt wird.
- **Kaskadierende Löschung ist bereits als Baustein vorhanden:** `MemberManagementService` hat
  bereits `removeMember(householdId, memberId)` (Zeile 130-137, verweigert das Entfernen eines
  Admins über `assertMemberIsNotHouseholdAdmin`, Zeile 139-143) und
  `HouseholdManagementService.deleteHousehold(householdId)` (Zeile 48-57). Die `account`-Tabelle
  referenziert `member` mit `ON DELETE CASCADE`
  (`V1__create_household_and_member.sql:28`, `member_id UUID PRIMARY KEY REFERENCES member (id) ON
  DELETE CASCADE`) — das Löschen eines `member` (bzw. aller Members eines Haushalts) löscht die
  zugehörige(n) `account`-Zeile(n) automatisch mit. Für die neue Grace-Period-Löschung genügt es
  also, je nach `member.isAdmin()` einen der beiden bestehenden Wege aufzurufen — **kein** neuer
  Lösch-Mechanismus nötig.
- **`HouseholdDeleted`-Event trägt keine Mitgliederdaten — Mitglieder sind zum Zeitpunkt der
  Listener-Ausführung bereits gelöscht:** `HouseholdManagementService.deleteHousehold` (Zeile 48-57)
  löscht `memberRepository.deleteByHouseholdId(householdId)` (Zeile 50) **vor** dem
  `eventPublisher.publishEvent(new HouseholdDeleted(householdId))` (Zeile 56). Das Event
  (`.../household/HouseholdDeleted.java`) trägt nur `UUID householdId` — kein Mitgliedsname, keine
  E-Mail. Spring Moduliths `@ApplicationModuleListener` verarbeitet Events über die
  Event-Publication-Registry **nach** dem Commit der auslösenden Transaktion (asynchron/in neuer
  Transaktion) — zu diesem Zeitpunkt sind die `member`-Zeilen bereits weg, ein Listener könnte also
  **nicht** nachträglich per `memberRepository.findByHouseholdId` die zu benachrichtigenden
  E-Mail-Adressen ermitteln. Eine „Mitglieder bei Haushalts-Löschung benachrichtigen"-Funktion
  bräuchte daher zwingend entweder ein erweitertes `HouseholdDeleted`-Event (Mitgliederdaten bereits
  vor dem Löschen eingesammelt) oder einen Datenerfassungsschritt vor `deleteByHouseholdId`. Das
  betrifft nicht nur den hier neu geplanten Auto-Lösch-Trigger, sondern **jeden** Aufruf von
  `deleteHousehold` — also auch die bereits in P1.6 ausgelieferte manuelle Admin-Löschung. Diese
  generelle Erweiterung ist deshalb **nicht** Teil dieses Plans (siehe Abgrenzung in 3.10) und wird
  als neuer Punkt im Meta-Plan nachgetragen.
- **Kein bestehender Scheduled-Job im Projekt:** `grep -rn "@Scheduled\|@EnableScheduling"
  backend/src/main/java` liefert nichts. Die Grace-Period-Löschung (3.9) ist der **erste** Einsatz
  von Spring-eigenem Scheduling in diesem Projekt — `@EnableScheduling` muss neu ergänzt werden
  (keine neue Dependency, Teil von `spring-boot-starter`).

---

## 3. Offene Design-Entscheidungen

Alle sechs Punkte wurden im Zuge der Plan-Erstellung mit dem Nutzer durchgesprochen und
entschieden (siehe unten je Punkt).

### 3.1 Nutzung der bereits vorbereiteten `notifications`-Paketstruktur — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.**

**Empfehlung:** Die vorhandene Struktur (`notifications` Root-Package leer,
`notifications.internal` leer, `notifications.service` leer) wird wie folgt gefüllt, konsistent
mit ADR-011s Konvention „Root-Package = öffentlich, Subpackages = intern":

- `notifications.internal`: `AccountRegistrationListener` (`@ApplicationModuleListener` für
  `AccountRegistered`), `PasswordResetRequestedListener` (analog für `PasswordResetRequested`),
  `EmailSenderService` (kapselt `JavaMailSender` + Templating), zugehörige Mail-Templates.
- `notifications.service`: **wird nicht befüllt** — es gibt aktuell keinen Bedarf für eine
  synchrone Named-Interface-Abfrage *in* das `notifications`-Modul hinein (kein anderes Modul
  fragt „wurde diese Mail schon verschickt?" synchron ab); der leere `service`-Ordner ist ein
  Überbleibsel einer früheren, nicht weiterverfolgten Planung und wird gelöscht statt künstlich
  befüllt zu werden.
- `notifications` (Root): bleibt vorerst leer, es sei denn P2.2 stellt fest, dass tatsächlich ein
  synchroner Query-Bedarf besteht (aktuell nicht ersichtlich).

**Alternative:** `service`-Package für eine öffentliche `EmailNotificationService`-Named-Interface
nutzen, falls zukünftige Module (z. B. `tasks` für Fälligkeits-Erinnerungen) synchron E-Mails
anstoßen sollen wollen. Zurückgestellt: aktuell hat kein Modul diesen Bedarf, und eine
spekulative Named-Interface-Abstraktion ohne echten zweiten Konsumenten wäre eine verfrühte
Abstraktion (AGENTS.md „Avoid speculative abstractions").

### 3.2 Token-Format & Ablage für Verifikations-/Reset-Token — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.** Neue Tabelle `account_token` (Flyway-Migration im `household`-Schema, analog
`invite`, siehe `V1__create_household_and_member.sql:16-22`):

```sql
CREATE TABLE account_token
(
    id          BIGSERIAL PRIMARY KEY,
    member_id   UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    token       UUID NOT NULL UNIQUE,
    purpose     TEXT NOT NULL, -- 'EMAIL_VERIFICATION' | 'PASSWORD_RESET'
    valid_until TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Begründung: Das Projekt hat mit `invite` (`InviteEntity`/`InviteRepository`) bereits exakt dieses
Muster für Einmal-Token (zufällige `UUID`, DB-Zeile mit Ablaufdatum, `findByToken` +
Ablauf-Filter-Prädikat, siehe `MemberManagementService.resolveInvite`, Zeile 79–83). Ein
signiertes JWT wäre hier ein inkonsistenter Fremdkörper (bräuchte eigene Signaturschlüssel-
Verwaltung, ist aber nicht widerrufbar, ohne eine Blocklist zu führen — was wieder eine DB-Zeile
wäre, also keinen Vorteil brächte). Eine gemeinsame Tabelle mit `purpose`-Diskriminator statt zwei
getrennter Tabellen (`email_verification_token`, `password_reset_token`) vermeidet Code-
Duplikation bei Erstellung/Ablauf-Prüfung/Aufräumen, bei geringem Nachteil (ein zusätzliches
`purpose`-Filterprädikat pro Query).

**Alternative:** Signiertes, zeitlich begrenztes JWT ohne DB-Ablage (zustandslos). Verworfen: kein
Widerruf möglich (ein einmal ausgestelltes Reset-Token bliebe bis zum Ablauf gültig, selbst wenn
der Nutzer es nie nutzt und stattdessen ein neues anfordert — Verstoß gegen „Single-Use"), und
bräuchte einen neuen Signaturschlüssel-Verwaltungsmechanismus, den es für dieses Format sonst
nirgends im Projekt gibt (Spring Authorization Server verwaltet seine eigenen JWK-Schlüssel intern,
das wäre kein wiederverwendbarer Baustein für projekteigene Tokens).

**Ablauf-/Resend-Regeln (konkретer Vorschlag, Teil derselben Entscheidung):**
Verifikations-Token: 24h Gültigkeit, Resend-Cooldown 60s (verhindert Mail-Flooding durch
wiederholtes Klicken); Reset-Token: 1h Gültigkeit (kürzer, da sicherheitskritischer), kein
Resend-Cooldown nötig, da jede neue Anforderung das alte Token implizit ungültig machen sollte
(siehe unten). Beide Werte als `librehousehold.security.*.token-validity`-Property konfigurierbar
(Abschnitt 2.6).

**Single-Use-Konsequenz:** Beim Einlösen (Confirm) wird die Token-Zeile gelöscht (nicht nur
`valid_until` in die Vergangenheit gesetzt) — konsistent mit dem Prinzip „ein Token, eine
Verwendung", und vermeidet eine unbegrenzt wachsende Tabelle (kein Bedarf, eingelöste Tokens
aufzuheben). Bei erneuter Anforderung (Resend/erneuter Reset-Request) wird ein zuvor für
denselben `member_id`+`purpose` ausgestelltes, noch gültiges Token vorher gelöscht (ein aktives
Token pro Zweck und Mitglied), damit nicht mehrere parallel gültige Links im Umlauf sind.

### 3.3 Mechanismus zur Session-Invalidierung nach Passwort-Reset — ✅ entschieden

Das ist laut Ist-Zustand-Recherche (Abschnitt 2.3) die architektonisch anspruchsvollste
Einzelentscheidung dieses Plans, weil dafür **kein** bestehender Baustein wiederverwendet werden
kann.

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.** Spring Securitys eingebautes `SessionRegistry` (`SessionRegistryImpl`) als neuer
Bean registrieren, zusammen mit `HttpSessionEventPublisher` (nötig, damit `SessionRegistry`
overhaupt über Session-Erzeugung/-Ende informiert wird — Standard-Spring-Security-Baustein, keine
neue Dependency, da Teil von `spring-boot-starter-security`, bereits vorhanden,
`backend/pom.xml:79-82`). `AccountService.resetPassword(memberId, newPassword)` (neue Methode)
ruft nach dem Passwort-Update `sessionRegistry.getAllSessions(principal, false)` auf und
invalidiert jede zurückgegebene `SessionInformation` per `expireNow()` — das erzwingt bei der
nächsten Anfrage dieser Sessions eine Re-Authentifizierung, ohne dass das Frontend etwas davon
wissen muss (der übliche `sessionExpiredMiddleware`-401-Pfad, siehe
`frontend/src/lib/api/httpClient.ts:23-34`, greift automatisch). Zusätzlich: bestehende
`OAuth2AuthorizedClient`-Einträge für den Principal per `OAuth2AuthorizedClientService`
entfernen (dasselbe Muster wie `RevokeAuthorizedClientLogoutHandler`, Zeile 21, aber für **alle**
Sessions statt nur die aktuelle) — sonst könnte ein bereits ausgestellter Access-Token trotz
Passwort-Reset weiterverwendet werden, bis er regulär abläuft.

**Alternative 1:** Nur die `oauth2_authorization`-Tabelle bereinigen (`DELETE ... WHERE
principal_name = :email`), `HttpSession`s selbst unangetastet lassen. Verworfen: Eine bereits
authentifizierte `HttpSession` (Cookie `SESSION`) bliebe gültig und könnte über
`AccountOidcPrincipal` weiterhin `GET /me` etc. aufrufen, ohne dass je ein neuer OAuth2-Flow
nötig wäre — das widerspricht dem Ziel „nach Reset ist jede andere Session tot".

**Alternative 2:** Gar keine aktive Invalidierung, nur Passwort ändern (der Angreifer, der die
alte Session hatte, verliert sein Zugriffsrecht ohnehin beim nächsten Login-Versuch mit dem alten
Passwort). Verworfen: widerspricht der expliziten Meta-Plan-Vorgabe „Invalidierung bestehender
Sessions nach Reset" (Zeile 145) — genau das Szenario, das ein Reset eigentlich adressieren soll
(kompromittiertes Passwort, Angreifer hat aber schon eine aktive Session), wäre sonst nicht
abgedeckt.

### 3.4 Rate-Limiting-Ansatz: Bibliothek vs. Eigenbau — ✅ entschieden (Rückfrage laut AGENTS.md gestellt und beantwortet)

Keine Rate-Limiting-Bibliothek ist aktuell in `backend/pom.xml` deklariert (`grep -n
"bucket4j\|resilience4j\|Bucket4j" backend/pom.xml` liefert nichts). Laut AGENTS.md „Dependency
Management" braucht eine neue Dependency eine explizite Begründung und **Rückfrage vor dem
Hinzufügen** — diese Rückfrage wurde bei der Erstellung dieses Plans gestellt.

**Entscheidung (vom Nutzer bestätigt): Option A, `bucket4j-core`.**

**Option A: `bucket4j-core` (+ ggf. `bucket4j-jcache` für einen verteilten Store).**
Vorteil: etablierte, gut getestete Token-Bucket-Implementierung, deklarativ konfigurierbar,
unterstützt spätere Mehrinstanzigkeit über einen JCache-/Redis-Backend, falls das Deployment das
je braucht. Nachteil: neue Dependency, zusätzliche Lernkurve für Konfiguration.

**Option B: Eigenbau mit einer neuen `login_attempt`-Tabelle** (Zeitstempel + Identifier + Zähler,
per Flyway-Migration, Cleanup per scheduled Job oder TTL-artigem Ablauf-Prädikat in der Query
selbst). Vorteil: keine neue Dependency, volle Kontrolle, passt zum bestehenden Muster
(`invite`/`account_token`, siehe 3.2) einfacher Zeilen mit Ablaufzeit. Nachteil: Rate-Limiting ist
ein gut erforschtes Problem („Sliding Window", „Token Bucket" etc.) — ein Eigenbau riskiert
subtile Bugs (Race Conditions bei parallelen Anfragen), die eine etablierte Bibliothek bereits
gelöst hat.

**Verdeployment-Kontext für die Entscheidung:** Laut `server.forward-headers-strategy: framework`
und dem Kommentar in `application.yaml:24-29` („Nginx terminates TLS and forwards internally...
the backend is only reachable from Nginx on the internal Docker network") ist das Deployment-Modell
aktuell **Single-Instance** (ein Backend-Container hinter einem Nginx-Reverse-Proxy, kein
Hinweis auf Load-Balancing/mehrere Instanzen in `docs/architecture/chapters/07_deployment_view.adoc`
— nicht in dieser Recherche gegengelesen, aber aus der Kommentar-Formulierung „the backend" im
Singular klar impliziert). Das vereinfacht **beide** Optionen: eine simple In-Memory-Zählung
(Bucket4j lokal ohne JCache-Backend, oder eine Eigenbau-`ConcurrentHashMap`) wäre für den aktuellen
Deployment-Stand ausreichend korrekt, ohne dass verteilte Konsistenz gelöst werden müsste.

Gewählt wurde Option A (Bucket4j, lokaler In-Memory-Bucket, kein JCache-Backend nötig beim
aktuellen Single-Instance-Deployment) — geringeres Bug-Risiko bei überschaubarer neuer
Dependency, klar auf ein späteres verteiltes Backend erweiterbar, falls das Deployment-Modell
sich ändert.

### 3.5 Verifikations-Resend: authentifiziert, unauthentifiziert oder beides? — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.** Resend ist **ausschließlich authentifiziert** erreichbar
(`POST /members/{memberId}/verification/resend`, `@PreAuthorize
"@householdAccessGuard.isSelf(#memberId, authentication)"`, analog `changePassword`,
`MembersApiDelegateImpl.java:82-87`). Begründung: Ein unverifizierter Nutzer hat direkt nach
Setup/Join bereits eine authentifizierte Session (`AccountSessionAuthenticator`, siehe Abschnitt
2.3) — der Verifikationshinweis (P2.3) erscheint ohnehin erst nach dem Login/Bootstrap, ein
unauthentifizierter Resend-Endpoint hätte also keinen echten Anwendungsfall, würde aber ein
zusätzliches ENUM1-Risiko schaffen (ein unauthentifizierter Endpoint, der per E-Mail-Adresse
Resend auslöst, müsste wie der Reset-Request nicht-enumerierend antworten — unnötige Komplexität
für einen Fall, der praktisch nicht vorkommt: ein Nutzer ohne Session, der weiß, dass seine
E-Mail unverifiziert ist, kann sich einfach einloggen und den Button dort klicken).

**Alternative:** Zusätzlich ein unauthentifizierter Resend per E-Mail-Adresse (analog
Reset-Request). Verworfen als unnötige Oberfläche ohne echten Use-Case, siehe Begründung oben.

### 3.6 Ort des Verifikationshinweises im Frontend — ✅ entschieden (mit Anpassung ggü. der Empfehlung)

**Entscheidung (vom Nutzer bestätigt, mit Ergänzung):** Ein schließbares DaisyUI-`alert`-Banner in
`frontend/src/routes/app/+layout.svelte` (Route-Guard-Layout, siehe `auth-plan-p1.7-p1.10.md`
Abschnitt 3.7 — dieselbe Datei, die `session.status` bereits beobachtet), sichtbar für jede
`/app/*`-Seite, solange `session.currentUser?.emailVerified === false`. Begründung: Der Hinweis
soll nicht nur beim gezielten Besuch der Settings auffallen (siehe Ist-Zustand 2.8), sondern
überall im eingeloggten Bereich, bis der Nutzer verifiziert.

**Ergänzung ggü. der ursprünglichen Empfehlung — Banner-„geschlossen"-Zustand wird per
localStorage gemerkt** (Klarstellung, siehe Abschnitt 1): Anders als ursprünglich empfohlen
(„kein localStorage-Merken erlaubt") bleibt ein per Klick geschlossenes Banner nach einem Reload
geschlossen, gespeichert unter einem neuen `localStorage`-Key (Vorschlag:
`email-verification-banner-dismissed`, Wert der zuletzt gesehenen Member-ID, damit ein
Accountwechsel im selben Browser das Banner für den neuen Account wieder zeigt). Wichtig:
Ausschließlich dieser reine UI-Zustand wandert nach localStorage — `session.currentUser?.emailVerified`
selbst bleibt weiterhin allein aus `GET /me` abgeleitet und wird **nicht** gecacht; sobald die
E-Mail tatsächlich verifiziert wird, verschwindet das Banner unabhängig vom localStorage-Wert
(Bedingung bleibt `!emailVerified`, das Dismissed-Flag greift nur zusätzlich, solange
`!emailVerified` noch `true` ist).

**Alternative (verworfen):** Hinweis nur in den User-Settings
(`frontend/src/routes/app/settings/user/+page.svelte`, neben dem bestehenden
`settings.user.password`-Block). Verworfen: würde von Nutzern, die die Settings nie gezielt
aufsuchen, nie gesehen — schwächt den Zweck der Verifikation (E-Mail-Adresse tatsächlich
bestätigen) ab.

### 3.7 Umfang der Durchsetzung: was wird bei unverifizierter E-Mail eingeschränkt? — ✅ entschieden (Nutzervorgabe)

**Entscheidung (vom Nutzer vorgegeben):**

| Aktion | Bei unverifiziertem Account |
|---|---|
| Ausgaben (Expenses) erstellen/bearbeiten | **Uneingeschränkt** |
| Aufgaben (Tasks) erstellen/bearbeiten | **Uneingeschränkt** |
| Einladungslink erzeugen/nutzen (als Admin bzw. als Beitretender) | **Uneingeschränkt** — der Einladungsmechanismus prüft den Verifikationsstatus des Admins nicht (siehe 2.9) |
| Passwort ändern (`PATCH .../change-password`, authentifiziert, `AccountService.changePassword`) | **Gesperrt**, bis `emailVerified` |
| E-Mail ändern (`email`-Feld in `PATCH .../members/{memberId}`) | **Gesperrt**, bis `emailVerified` — nur das `email`-Teilfeld, `name`/`avatar` bleiben änderbar |
| Passwort-Reset anfordern/einlösen (P2.4/P2.5, unauthentifiziert per Token-Link) | **Uneingeschränkt** — bewusst ausgenommen, siehe Begründung in 2.9 |
| Erneuter Login nach Logout/Session-Ablauf | **Gesperrt**, bis `emailVerified` (siehe 3.8) — die unmittelbar nach Setup/Join bestehende erste Session bleibt unberührt |

Begründung: Kernfunktionen des Haushaltsmanagements (Ausgaben/Aufgaben/Einladung) sollen für einen
frisch registrierten Nutzer sofort nutzbar sein — die Verifikationspflicht soll gezielt
sicherheitsrelevante Aktionen (Zugangsdaten ändern, erneute Authentifizierung) betreffen, nicht die
gesamte App sperren. Das ist eine bewusst schwächere Durchsetzung als „Account komplett sperren bis
verifiziert", aber genau das vom Nutzer vorgegebene Verhalten.

### 3.8 Login-Blocking-Mechanismus für unverifizierte Accounts — ✅ entschieden

**Entscheidung:** `AccountPrincipal.isEnabled()` (`.../household/AccountPrincipal.java:47-50`) gibt
künftig `emailVerified` zurück statt hart `true`; `AccountUserDetailsService.loadUserByUsername`
(Zeile 22-29) übergibt beim Bau von `AccountPrincipal` zusätzlich `account.emailVerified()`. Ein
Login-Versuch eines unverifizierten Accounts scheitert dadurch bereits in Spring Securitys
`DefaultPreAuthenticationChecks` mit `DisabledException`, bevor überhaupt das Passwort geprüft
wird — kein neuer Mechanismus, reine Nutzung eines bereits vom Framework bereitgestellten Hooks
(siehe 2.9).

**Sicherheits-Nebenwirkung, bewusst in Kauf genommen (Ergänzung zu ENUM1):** Eine `DisabledException`
ist von einer `BadCredentialsException` unterscheidbar, sofern die Login-Fehlerbehandlung das nicht
aktiv vereinheitlicht — ein Angreifer könnte daraus lernen, dass eine E-Mail-Adresse zwar
registriert, aber noch unverifiziert ist (ein geringfügiges Informationsleck ggü. dem in Chapter 8
als „Decided" geführten ENUM1). Der Login-Fehlerpfad (`formLogin()`-Failure-Handling) sollte daher
geprüft werden, ob er `DisabledException` bereits generisch behandelt oder eine eigene Meldung
zeigt — falls Letzteres, ist das eine bewusste, dem Nutzerwunsch geschuldete Abweichung von der
strikten ENUM1-Auslegung, kein Bug. Dokumentation dieses Kompromisses gehört in den neuen `VERIFY1`-
Eintrag der Sicherheits-Checkliste (siehe P2.2 Aufgabe 7, unten ergänzt).

### 3.9 Grace-Period-Löschung unverifizierter Accounts — ✅ entschieden

**Entscheidung:**

- Neue Spalten auf `account` (zusätzlich zu `email_verified`, siehe Entscheidung 3.2/Aufgabe 1):
  `registered_at TIMESTAMPTZ NOT NULL` (beim Anlegen explizit per `Instant.now()` in
  `AccountService.createAccount` gesetzt, analog zum bestehenden Muster in
  `HouseholdManagementService.regenerateInvite`, das `validUntil` ebenfalls in Java statt per
  DB-Default berechnet) und `verification_deletion_warning_sent_at TIMESTAMPTZ NULL` (verhindert
  mehrfachen Warn-Mail-Versand bei wiederholten Job-Läufen).
- Neuer, per `@Scheduled` periodisch laufender Job (Vorschlag: stündlich,
  `@Scheduled(fixedRate = 3_600_000)`, Intervall selbst nicht konfigurierbar — kein Bedarf für
  Laufzeit-Anpassung ersichtlich) in einer neuen Klasse
  `.../household/service/UnverifiedAccountExpiryJob.java`, der:
  1. alle `account`-Zeilen mit `email_verified = false` und `registered_at` älter als
     `grace-period-days` minus `deletion-warning-hours-before` sucht, bei denen
     `verification_deletion_warning_sent_at IS NULL` ist, und für diese eine Warn-E-Mail versendet
     (`EmailSenderService`, neue Methode `sendVerificationDeletionWarningEmail`, siehe P2.2
     Aufgabe 4) sowie `verification_deletion_warning_sent_at` setzt;
  2. alle `account`-Zeilen mit `email_verified = false` und `registered_at` älter als
     `grace-period-days` sucht und je nach `member.isAdmin()`:
     - **Admin:** `householdManagementService.deleteHousehold(member.householdId())` aufruft — das
       löscht den gesamten Haushalt (kaskadiert bereits auf alle Mitglieder inkl. deren
       `account`-Zeilen, siehe 2.9). Die Benachrichtigung der übrigen Mitglieder ist **nicht** Teil
       dieses Plans (siehe 3.10/Meta-Plan-Nachtrag).
     - **Kein Admin:** `memberManagementService.removeMember(member.householdId(), member.id())`
       aufruft (bestehende Methode, siehe 2.9) — entfernt nur diesen einen Nutzer.
- Neue konfigurierbare Properties, im bestehenden `librehousehold.security.*`-Namespace
  (Abschnitt 2.6): `librehousehold.security.email-verification.grace-period-days` (Standard `7`),
  `librehousehold.security.email-verification.deletion-warning-hours-before` (Standard `24`).
- `@EnableScheduling` neu ergänzen (erster Scheduled-Job im Projekt, siehe 2.9) — auf der
  Hauptanwendungsklasse oder einer neuen `@Configuration`-Klasse, konsistent mit bestehenden
  Config-Klassen unter `.../config/`.
- Bewusste Vereinfachung: Für jede fällige `account`-Zeile wird der zugehörige `member` per
  `memberRepository.findById` einzeln nachgeladen (kein Join/Batch-Query) — bei der zu erwartenden
  Datenmenge (Haushalts-App, keine Massen-Registrierung) unproblematisch, konsistent mit dem
  Projekt-Prinzip „keine spekulative Optimierung ohne echten Bedarf".

**Alternative (verworfen):** Löschung/Warnung inline bei jedem Login-Versuch prüfen statt eines
Scheduled Jobs. Verworfen: ein Nutzer, der sich nie wieder einloggt, würde nie gelöscht und nie
gewarnt — das widerspricht dem Ziel einer verlässlichen Frist, die unabhängig vom Nutzerverhalten
greift.

### 3.10 Abgrenzung: was NICHT mehr Teil dieses Plans ist

Die folgenden, im Zuge dieser Erweiterung erkannten Punkte gehören inhaltlich nicht mehr zu
„P2.1–P2.7: E-Mail-Verifikation/Passwort-Reset/Rate-Limiting" und wurden stattdessen als neue,
noch nicht detailgeplante Punkte in `docs/auth-meta-plan.md` ergänzt (siehe dort):

- **Mitglieder-Benachrichtigung bei Haushalts-Löschung (jeder Auslöser).** Der Wunsch „gibt es im
  Haushalt noch mehr Mitglieder, sollen diese informiert werden, dass der Haushalt gelöscht wurde"
  gilt laut Nutzervorgabe **immer**, wenn ein ganzer Haushalt gelöscht wird — also auch bei der
  bereits in P1.6 ausgelieferten manuellen Admin-Löschung, nicht nur beim hier neu geplanten
  Auto-Lösch-Trigger. Das ist ein allgemeiner, nachträglicher Ausbau des bestehenden
  `deleteHousehold`-Flows (inkl. einer nötigen Erweiterung des `HouseholdDeleted`-Events um
  Mitgliederdaten, siehe 2.9) und kein Bestandteil der ursprünglichen Phase-2-Definition. Dieser
  Plan (P2.2, Aufgabe „Grace-Period-Löschung") **ruft** beim Admin-Fall lediglich das bestehende
  `deleteHousehold` auf — sobald die Benachrichtigungsfunktion im Rahmen des neuen Meta-Plan-Punkts
  existiert, greift sie automatisch auch hier, ohne dass dieser Plan dafür angepasst werden müsste.

---

## 4. Empfohlene Umsetzungsreihenfolge

Die Meta-Plan-Reihenfolge P2.1 → P2.7 wird **nicht** unverändert übernommen, weil P2.2 zwingend
Vorarbeit braucht, die in keinem der 7 Punkte explizit benannt ist (das leere `notifications`-Modul,
Abschnitt 2.1/3.1), und weil P2.7 vollständig orthogonal zu P2.1–P2.6 ist (andere Endpoints,
andere Tabelle, keine inhaltliche Abhängigkeit). Empfohlene Reihenfolge:

1. **P2.1 (OpenAPI: E-Mail-Verifikation)** zuerst, weil P2.2 den generierten Vertrag
   (`LocalRegistration`/`CurrentUser`-Erweiterung, neue Endpoints) braucht, um überhaupt gegen
   etwas zu implementieren (gleiches Verhältnis wie P1.1 → P1.3 im vorherigen Detailplan).
2. **P2.2 (Backend: Verifikations-Versand & -Prüfung)**, inklusive der in Abschnitt 3.1/3.2
   beschriebenen Vorarbeit (Flyway-Migration `account_token`, `notifications`-Modul-Code,
   `AccountRegistered`-Event in `HouseholdSetupService`/`MemberManagementService`) **sowie** der
   nachträglich ergänzten Durchsetzungs-Aufgaben aus 3.7–3.9 (Login-Blocking, Passwort-/E-Mail-
   Änderungssperre, Grace-Period-Löschung) — letztere sind unabhängig von P2.3–P2.7 und könnten
   auch als letzter Teilschritt von P2.2 erfolgen, ohne andere Punkte zu blockieren.
3. **P2.3 (Frontend: Verifikationshinweis & Resend-UI)** direkt danach, da P2.3 vollständig von
   P2.1/P2.2 abhängt (neuer `emailVerified`-Feld, neuer Resend-Endpoint) und inhaltlich klein ist.
4. **P2.4 (OpenAPI: Passwort-Reset)**, analog zu Schritt 1 — Vertrag vor Implementierung.
5. **P2.5 (Backend: Passwort-Reset)**, nutzt dieselbe `account_token`-Tabelle aus Schritt 2 (nur
   anderer `purpose`-Wert) — daher **nach** P2.2, nicht parallel, um die Tabelle nicht doppelt
   anzulegen oder zu migrieren.
6. **P2.6 (Frontend: „Passwort vergessen"-Flow)**, analog zu Schritt 3.
7. **P2.7 (Backend: Rate-Limiting & Lockout)** kann **parallel** zu 1–6 begonnen werden, sobald
   die in Abschnitt 3.4 beschriebene Rückfrage geklärt ist — es hat keine Datenabhängigkeit zu den
   anderen Punkten, sollte aber inhaltlich zuletzt *abgeschlossen* werden, weil es die Endpoints
   aus P2.2/P2.5 mit absichert (Rate-Limiting für Login **und** die neuen Reset-/
   Verifikations-Endpoints, siehe Meta-Plan-Beschreibung „Login-, Reset- und
   Verifikations-Endpunkte").

---

## 5. Aufgaben je P-Punkt

Für jeden Schritt gilt strikt Rot-Grün-Refactor. Dateipfade sind neu anzulegende Dateien, sofern
nicht anders vermerkt.

### P2.1 — OpenAPI: E-Mail-Verifikation

**Aufgabe 1: Schema-Erweiterungen in `api/openapi.yml`**

- `CurrentUser` (Zeile 2594–2607) um `emailVerified: boolean` (required) ergänzen.
- Neues Schema `EmailVerificationConfirm { token: string (format: uuid) }`.
- Kein neues Schema für Resend nötig (kein Body, nur Pfadparameter `memberId`).
- Neue Pfade:
  - `POST /members/{memberId}/verification/resend` — `security: [{sessionCookie: []}]`
    (Default, kein `security: []`), 204 bei Erfolg, 404 falls `memberId` nicht dem
    authentifizierten Principal entspricht (via `@PreAuthorize`, kein Body-Leak nötig, da
    ohnehin nur der eigene Member adressierbar ist — kein ENUM1-Bezug, da authentifiziert, siehe
    Entscheidung 3.5).
  - `POST /members/verification/confirm` — `security: []`, Body `EmailVerificationConfirm`, 204
    bei Erfolg, 409 mit `Problem.type` `/problems/verification-token-invalid` (Token nicht
    gefunden oder abgelaufen — beide Fälle identisch beantwortet, kein Unterschied nötig, da hier
    kein Enumerationsrisiko besteht, nur ein Gültigkeitsstatus).
- `npm run openapi` (Frontend) und `./mvnw clean compile` (Backend) nach der Änderung, wie in
  AGENTS.md „API Updates" vorgeschrieben.
- Kein eigener Test für die OpenAPI-Datei selbst (reine Vertragsänderung, wie bei P1.1/P1.2 im
  Vorgänger-Plan) — Validierung erfolgt implizit über die generierten Typen, die P2.2 kompilieren
  muss.

### P2.2 — Backend: Verifikations-Versand & -Prüfung

**Aufgabe 1: Flyway-Migration `account_token`**

- Neue Datei `backend/src/main/resources/db/migration/household/V2__create_account_token.sql`
  (nächste Versionsnummer nach `V1__create_household_and_member.sql`), Inhalt wie in
  Entscheidung 3.2 skizziert, plus drei neue Spalten auf `account` (per
  `ALTER TABLE account ADD COLUMN`): `email_verified BOOLEAN NOT NULL DEFAULT FALSE`,
  `registered_at TIMESTAMPTZ NOT NULL DEFAULT now()` (DB-Default nur als Absicherung für
  Bestandsdaten der Migration selbst — künftige Inserts setzen den Wert explizit in Java, siehe
  Aufgabe 8) und `verification_deletion_warning_sent_at TIMESTAMPTZ NULL` (siehe Entscheidung 3.9).
- Kein TDD-Rot/Grün für eine reine Migration — Verifikation erfolgt implizit durch die
  IT-Tests der folgenden Aufgaben (Flyway muss beim Testcontainer-Start fehlerfrei durchlaufen).

**Aufgabe 2: `AccountEntity` um `emailVerified`, `registeredAt`, `verificationDeletionWarningSentAt` erweitern, neue `AccountTokenEntity`**

- `AccountEntity` (`.../household/model/AccountEntity.java`) um drei neue Record-Felder erweitern:
  `@Column("email_verified") boolean emailVerified`,
  `@Column("registered_at") Instant registeredAt`,
  `@Column("verification_deletion_warning_sent_at") Instant verificationDeletionWarningSentAt`
  (nullable). Konstruktor-Reihenfolge/alle Aufrufstellen (`AccountService.createAccount`, siehe
  Aufgabe 8) entsprechend anpassen.
- Neue Datei `.../household/model/AccountTokenEntity.java`, Record analog `InviteEntity`
  (`@Id Long id`, `memberId`, `token: UUID`, `purpose: String`, `validUntil: Instant`) — **kein**
  `Persistable`-Interface nötig, da `id` `null` bleibt bis zum ersten `save()` (Standard-Verhalten
  für auto-generierte `BIGSERIAL`-IDs, siehe `InviteEntity`, das ebenfalls kein `Persistable`
  implementiert).
- `AccountRepository` um `@Modifying @Query("UPDATE account SET email_verified = true WHERE
  member_id = :memberId")` erweitern.
- Neue Datei `.../household/repository/AccountTokenRepository.java`:
  `findByTokenAndPurpose(UUID token, String purpose)`,
  `deleteByMemberIdAndPurpose(UUID memberId, String purpose)`.
- Kein eigener Unit-Test für reine Repository-Interfaces (Spring-Data-JDBC-Derived-Queries,
  konsistent mit AGENTS.md „Prefer Derived Queries" — Verifikation über die IT-Tests von
  Aufgabe 3/4).

**Aufgabe 3: `AccountRegistered`-Domain-Event + Publizierung**

- Neue Datei `.../household/AccountRegistered.java` (Root-Package, Record `(UUID memberId,
  String email)`, Javadoc analog `MemberRemoved.java`).
- `HouseholdSetupService`: `ApplicationEventPublisher` als neue Konstruktor-Dependency ergänzen,
  nach `accountService.createAccount(...)` (Zeile 64) `eventPublisher.publishEvent(new
  AccountRegistered(savedMember.getId(), setup.getMember().getEmail()))` aufrufen.
- `MemberManagementService`: analog nach `accountService.createAccount(...)` (Zeile 101, bestehender
  `eventPublisher` bereits vorhanden, Zeile 40).
- Test zuerst (Unit, da nur ein zusätzlicher Publish-Call — `ApplicationEventPublisher` ist eine
  direkte Dependency und wird gemockt): Erweiterung bestehender
  `HouseholdSetupServiceTest`/`MemberManagementServiceTest`, falls vorhanden (sonst neue reine
  Unit-Tests analog dem Muster in `AccountServiceIT`, aber mit Mockito statt Testcontainers, da
  hier nur der Publish-Aufruf verifiziert wird, keine DB-Interaktion):
  - `it('setupHousehold_validSetup_publishesAccountRegisteredEvent')` —
    `verify(eventPublisher).publishEvent(new AccountRegistered(member.getId(), member.getEmail()))`.
  - Rot: Event wird noch nicht publiziert.
  - Grün: Publish-Aufruf ergänzen.
  - Refactor: keiner nötig.

**Aufgabe 4: `notifications`-Modul — `EmailSenderService` + Listener**

- Neue Datei `.../notifications/internal/EmailSenderService.java`, kapselt `JavaMailSender`
  (Spring-Boot-Autokonfiguration, sobald `spring.mail.*` in `application.yaml` gesetzt ist —
  neue Properties `spring.mail.host`/`port`/`username`/`password` ergänzen, kein Default
  committen, analog dem Kommentar zu `oauth2-client.client-secret`, Zeile 49–51 in
  `application.yaml`, „fail-fast … must set this explicitly").
  Methode `sendVerificationEmail(String toEmail, UUID token)`,
  `sendPasswordResetEmail(String toEmail, UUID token)`.
- Test zuerst, `EmailSenderServiceTest` (Unit, `JavaMailSender` gemockt — direkte Dependency):
  - `it('sendVerificationEmail_buildsMessageWithTokenLink')` — `verify(mailSender).send(any(
    SimpleMailMessage.class))` mit Argument-Captor, Assertion auf Betreff/Body enthält Token.
  - Rot: Klasse fehlt.
  - Grün: minimale `MimeMessage`/`SimpleMailMessage`-Konstruktion.
  - Refactor: Templating (Betreff/Body-Text) in Konstanten oder ein einfaches Template-Resource
    auslagern, falls die Nachricht mehrzeilig wird — keine neue Templating-Bibliothek (kein
    Thymeleaf o. ä.), da die Mail-Inhalte simpel genug für String-Konkatenation/`String.format`
    sind (AGENTS.md „kein Trivial-Single-Use-Dependency").
- Neue Datei `.../notifications/internal/AccountRegistrationListener.java`:
  ```java
  @ApplicationModuleListener
  void on(AccountRegistered event) {
      var token = accountTokenService.issueToken(event.memberId(), "EMAIL_VERIFICATION", VERIFICATION_TOKEN_VALIDITY);
      emailSenderService.sendVerificationEmail(event.email(), token);
  }
  ```
- Test zuerst (Unit, `AccountTokenService`+`EmailSenderService` gemockt — direkte Dependencies):
  - `it('on_accountRegistered_issuesTokenAndSendsVerificationEmail')`.
  - Rot: Klasse fehlt.
  - Grün: wie oben skizziert.
  - Refactor: keiner nötig bei dieser Größe.
- **IT-Test** (Happy-Path-Pflicht laut AGENTS.md für DB-lesende/schreibende Methoden):
  `AccountRegistrationListenerIT` — `@SpringBootTest` mit `spring-boot-starter-mail-test`
  (liefert typischerweise eine Test-`JavaMailSender`-Ersatzkonfiguration/GreenMail-Integration,
  siehe `backend/pom.xml:192-195` — exakte API vor Umsetzung per Context7/aktueller
  Spring-Boot-4-Doku verifizieren, da hier nicht weiter untersucht) + Testcontainers,
  publiziert `AccountRegistered` direkt über den echten `ApplicationEventPublisher`, prüft, dass
  in `account_token` eine Zeile mit `purpose = 'EMAIL_VERIFICATION'` existiert **und** eine Mail
  im Test-Mail-Server ankam.

**Aufgabe 5: `AccountTokenService` (Ausstellung/Einlösung, wiederverwendet in P2.5)**

- Neue Datei `.../household/service/AccountTokenService.java`:
  `issueToken(UUID memberId, String purpose, Duration validity): UUID` (löscht zuerst ein
  eventuell bestehendes Token für `memberId`+`purpose`, siehe Entscheidung 3.2, dann neues Token
  erzeugen und speichern), `consumeToken(UUID token, String purpose): UUID` (memberId) — wirft
  neue Exception `AccountTokenInvalidException`, falls nicht gefunden/abgelaufen; löscht die Zeile
  bei erfolgreicher Einlösung (Single-Use, siehe 3.2).
- Test zuerst, `AccountTokenServiceIT` (Testcontainers, DB-Interaktion, kein Mocking des
  Repositories laut AGENTS.md-Pflicht für DB-Zugriffe):
  - `it('issueToken_existingTokenForSamePurpose_deletesOldTokenFirst')`
  - `it('issueToken_validCall_persistsTokenWithCorrectExpiry')`
  - `it('consumeToken_validToken_returnsMemberIdAndDeletesToken')`
  - `it('consumeToken_expiredToken_throwsAccountTokenInvalidException')`
  - `it('consumeToken_unknownToken_throwsAccountTokenInvalidException')`
  - Rot: Klasse fehlt.
  - Grün: minimale Implementierung wie oben.
  - Refactor: Konstanten für `purpose`-Strings (`EMAIL_VERIFICATION`, `PASSWORD_RESET`) statt
    Magic Strings (AGENTS.md).

**Aufgabe 6: Controller-Endpoints**

- `MembersApiDelegateImpl`: `resendVerificationEmail(UUID memberId)` — lädt Member-E-Mail,
  publiziert erneut `AccountRegistered` (Wiederverwendung desselben Event/Listener-Pfads statt
  eigener Resend-Logik — konsistent mit ADR-011, kein neuer Event-Typ nötig) oder ruft
  `accountTokenService.issueToken`+`emailSenderService.sendVerificationEmail` direkt auf (letzteres
  vorzuziehen, da ein erneuter `AccountRegistered`-Event semantisch falsch wäre — der Account
  wird ja nicht erneut registriert). `@PreAuthorize
  "@householdAccessGuard.isSelf(#memberId, authentication)"` (siehe Entscheidung 3.5).
  `confirmEmailVerification(EmailVerificationConfirm body)` — ruft
  `accountTokenService.consumeToken(body.getToken(), "EMAIL_VERIFICATION")`, dann
  `accountRepository`-Update `email_verified = true`.
- Test zuerst (Unit, `AccountTokenService`/`AccountService`/`MemberManagementService` gemockt —
  direkte Dependencies der Controller-Klasse):
  - `it('resendVerificationEmail_ownMember_issuesNewTokenAndSendsEmail')`
  - `it('confirmEmailVerification_validToken_marksAccountVerified')`
  - `it('confirmEmailVerification_invalidToken_returns409WithVerificationTokenInvalidProblem')` —
    hier auf Exception-Handler-Ebene testen (`ValidationExceptionHandler`/analoges Mapping neu
    ergänzen für `AccountTokenInvalidException` → `/problems/verification-token-invalid`, 409).
  - Rot: Methoden fehlen im generierten `MembersApiDelegate`-Interface, bis P2.1 umgesetzt ist
    (Compile-Fehler wie im Vorgänger-Plan Abschnitt 2.2 dokumentiert — erwartetes Rot durch die
    Vertragsänderung selbst).
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig bei dieser Größe.
- `CurrentUser`-Aufbau in `SessionApiDelegateImpl.getCurrentUser()` (Zeile 33–43) um
  `emailVerified` aus `AccountRepository` ergänzen — dafür muss `MemberQuery`
  (Named Interface, `.../household/MemberQuery.java`) oder ein neues, schlankes
  `AccountQuery`-Named-Interface `isEmailVerified(UUID memberId): boolean` ergänzt werden, falls
  `session`-Modul nicht direkt auf `household.service.AccountRepository` zugreifen darf (Modul-
  Grenzen, ADR-011) — zu klären, ob `session` bereits als Teil von `household` zählt oder ein
  eigenes Modul ist (Verzeichnis `backend/src/main/java/eu/wiegandt/librehousehold/session/`
  liegt **außerhalb** von `household/`, ist also ein eigenständiges Package/Modul — Named
  Interface nötig, kein direkter Repository-Zugriff).

**Aufgabe 7: Sicherheits-Checkliste aktualisieren (neuer Eintrag VERIFY1)**

- `docs/architecture/chapters/08_concepts.adoc` (Security-Controls-Tabelle, Zeile 36–119, siehe
  Abschnitt 2.7): neue Zeile `VERIFY1` einfügen (Empfehlung: direkt nach `ENUM1`, vor `ERR1`) mit
  Control „E-Mail-Verifikationstoken sind Single-Use, zeitlich begrenzt gültig und werden nur
  gehasht/als zufälliger Wert abgelegt, nie im Klartext geloggt", ASVS-Referenz V6.2.3/V16.5.1 (je
  nach finaler ASVS-5.0-Nummerierung prüfen), Status „Decided, siehe Token-Format-Entscheidung in
  diesem Plan (Abschnitt 3.2)" nach Umsetzung von Aufgabe 5 (`AccountTokenService`).
- Zusätzlich einen Hinweis in der `ENUM1`-Zeile selbst ergänzen (Status bleibt „Decided", aber
  Beschreibung um eine Fußnote/Klammerzusatz erweitern): Login-Blocking unverifizierter Accounts
  (Aufgabe 8, Entscheidung 3.8) macht den Account-Status über eine unterscheidbare
  `DisabledException` erkennbar — bewusst akzeptierte, dokumentierte Abweichung, kein Bug.
- Kein Code, keine Tests — reine Doku-Änderung, direkt im Zuge dieser Aufgabe erledigt, nicht als
  separate Nacharbeit vermerkt.

**Aufgabe 8: Login-Blocking für unverifizierte Accounts**

- `AccountPrincipal` (`.../household/AccountPrincipal.java`): neues Record-Feld
  `boolean emailVerified` ergänzen, `isEnabled()` (Zeile 47-50) gibt `emailVerified` zurück statt
  hart `true` (siehe Entscheidung 3.8).
- `AccountUserDetailsService.loadUserByUsername` (Zeile 22-29): `AccountPrincipal`-Konstruktion um
  `account.emailVerified()` erweitern.
- `AccountService.createAccount` (Aufgabe 9 unten): `registeredAt` beim Anlegen setzen (siehe dort),
  `emailVerified` startet mit `false` (Default aus Aufgabe 1).
- Test zuerst (Unit, `AccountRepository`/`MemberManagementService` gemockt — direkte Dependencies
  von `AccountUserDetailsService`):
  - `it('loadUserByUsername_unverifiedAccount_returnsPrincipalWithEnabledFalse')`
  - `it('loadUserByUsername_verifiedAccount_returnsPrincipalWithEnabledTrue')`
  - Rot: `AccountPrincipal`/`AccountUserDetailsService` ignorieren `emailVerified` noch.
  - Grün: wie oben beschrieben.
  - Refactor: keiner nötig.
- **IT-Test** (Filter-Chain-Verhalten, analog `AuthorizationServerConfigurationIT`):
  `it('login_unverifiedAccount_rejectedWithDisabledAccount')` — Login-Versuch mit korrektem
  Passwort, aber `emailVerified = false`, erwartet Ablehnung (kein 200/Redirect auf `/app`).
  `it('login_verifiedAccount_succeeds')` als Gegenprobe.
- **Kein** Sondertest für die erste Session nach Setup/Join nötig — `AccountSessionAuthenticator`
  läuft nicht über `AccountUserDetailsService` (siehe 2.9), bestehende P1.8-Tests dafür bleiben
  unverändert grün.

**Aufgabe 9: Passwort-/E-Mail-Änderung sperren, solange unverifiziert**

- Neue Exception `.../household/exception/EmailNotVerifiedException.java`, analog
  `InvalidPasswordException` (`extends ErrorResponseException`, `super(HttpStatus.FORBIDDEN)`).
- `AccountService.changePassword` (Zeile 27-33): vor der Passwortprüfung
  `if (!account.emailVerified()) throw new EmailNotVerifiedException();` ergänzen.
- `AccountService.createAccount` (Zeile 23-25): `AccountEntity`-Konstruktion um `registeredAt =
  Instant.now()` (App-Server-Uhrzeit statt DB-`now()`, siehe Entscheidung 3.9) und
  `verificationDeletionWarningSentAt = null` erweitern.
- `MemberManagementService.updateMember` (Zeile 108-119): vor
  `update.getEmail().ifPresent(...)` (Zeile 115) prüfen, ob `update.getEmail()` gesetzt **und**
  das Zielmitglied unverifiziert ist (`accountRepository.findById(memberId)`, neue direkte
  Dependency `AccountRepository` in `MemberManagementService` — bereits vorhanden über
  `accountService`? Falls `AccountService` keine reine `isEmailVerified`-Abfragemethode anbietet,
  eine ergänzen, `AccountService` bleibt direkte Dependency, kein neues Named Interface nötig, da
  beide Klassen im selben Modul liegen) — bei unverifiziert `throw new
  EmailNotVerifiedException()`, **bevor** `name`/`avatar` verarbeitet werden würde diese sonst
  fälschlich mitblockieren; da `updateMember` aber ohnehin für alle drei Felder eine Methode ist,
  die Prüfung so plazieren, dass ein reines Name-/Avatar-Update ohne `email`-Feld **nicht**
  betroffen ist (`if (update.getEmail().isPresent() && !isVerified) throw ...` **vor** den
  drei `ifPresent`-Aufrufen, nicht danach).
- Test zuerst (Unit, `AccountRepository`/`MemberRepository` gemockt):
  - `it('changePassword_unverifiedAccount_throwsEmailNotVerifiedException')`
  - `it('changePassword_verifiedAccountWrongOldPassword_throwsInvalidPasswordException')` (Regression,
    stellt sicher, dass die neue Prüfung die bestehende nicht verdeckt)
  - `it('updateMember_unverifiedAccountWithEmailChange_throwsEmailNotVerifiedException')`
  - `it('updateMember_unverifiedAccountWithOnlyNameChange_succeeds')` — Abgrenzung zur
    Feld-Spezifität (siehe Entscheidung 3.7).
  - Rot: Prüfungen fehlen.
  - Grün: wie oben beschrieben.
  - Refactor: Named Constant/Hilfsmethode `requireEmailVerified(memberId)` falls die Prüfung an
    mehr als diesen zwei Stellen dupliziert würde (aktuell zwei Stellen — laut AGENTS.md „Kein
    @BeforeEach"/DRY-Grundsatz vertretbar, aber bei einer dritten Stelle extrahieren).
- Neuer Problem-Type `/problems/email-not-verified` (403) in `api/openapi.yml` ergänzen (analog
  bestehenden `Problem`-Type-Konventionen, Abschnitt 2.4), Frontend-Fehlerbehandlung für
  `changePassword`/`updateMember` in P2.3/Settings-Seite entsprechend erweitern (Toast mit Hinweis
  „Bitte zuerst E-Mail-Adresse bestätigen").

**Aufgabe 10: Grace-Period-Löschung unverifizierter Accounts (Scheduled Job)**

- Neue Konfigurationsklasse oder Ergänzung einer bestehenden `@Configuration`-Klasse unter
  `.../config/` um `@EnableScheduling` (siehe 2.9 — erster Scheduled-Job im Projekt).
- Neue Datei `.../household/service/UnverifiedAccountExpiryJob.java`
  (`@Scheduled(fixedRate = 3_600_000)`, siehe Entscheidung 3.9), Konstruktor-Dependencies:
  `AccountRepository`, `MemberRepository`, `HouseholdManagementService`, `MemberManagementService`,
  `EmailSenderService` (neue Methode `sendVerificationDeletionWarningEmail`, siehe Aufgabe 4).
- `AccountRepository` um zwei neue Derived Queries erweitern:
  `findByEmailVerifiedFalseAndVerificationDeletionWarningSentAtIsNullAndRegisteredAtBefore(Instant threshold)`,
  `findByEmailVerifiedFalseAndRegisteredAtBefore(Instant threshold)`.
- Neue Properties in `application.yaml` unter `librehousehold.security.email-verification.*`
  (Abschnitt 2.6): `grace-period-days` (Standard `7`), `deletion-warning-hours-before`
  (Standard `24`) — per `@ConfigurationProperties` oder `@Value`, konsistent mit bestehendem
  Muster für `librehousehold.security.*`.
- Test zuerst (Unit, alle Dependencies gemockt, `Clock`/`Instant.now()` über eine injizierbare
  `Clock`-Bean falls im Projekt bereits üblich, sonst direkter `Instant`-Parametertest der
  Schwellenwert-Berechnung als reine Hilfsmethode):
  - `it('run_accountPastWarningThresholdNotYetWarned_sendsWarningEmailAndSetsTimestamp')`
  - `it('run_accountAlreadyWarned_doesNotSendSecondWarningEmail')`
  - `it('run_adminAccountPastGracePeriod_deletesEntireHousehold')` —
    `verify(householdManagementService).deleteHousehold(householdId)`.
  - `it('run_nonAdminAccountPastGracePeriod_removesOnlyThatMember')` —
    `verify(memberManagementService).removeMember(householdId, memberId)`.
  - `it('run_verifiedAccount_isIgnored')`
  - Rot: Klasse fehlt.
  - Grün: minimale Implementierung wie oben.
  - Refactor: Schwellenwert-Berechnung (`registeredAt` + Konfigurationswerte → Vergleichszeitpunkt)
    als reine, eigenständig getestete Funktion auslagern, falls die Job-Klasse dadurch unübersichtlich
    würde.
- **IT-Test** `UnverifiedAccountExpiryJobIT` (Testcontainers, echte DB): mindestens ein Happy-Path
  je Verzweigung (Warnung, Admin-Löschung, Mitglieds-Entfernung), konsistent mit AGENTS.md-Pflicht
  „jede DB-lesende/schreibende Service-Methode braucht mindestens einen Happy-Path-IT-Test".
- **Abgrenzung (siehe 3.10):** Dieser Job ruft beim Admin-Fall lediglich das bestehende
  `deleteHousehold` auf. Eine Benachrichtigung der übrigen Haushaltsmitglieder ist **nicht** Teil
  dieser Aufgabe — das ist der in `docs/auth-meta-plan.md` neu ergänzte, eigenständige Punkt zur
  allgemeinen Mitglieder-Benachrichtigung bei Haushalts-Löschung.

### P2.3 — Frontend: Verifikationshinweis & Resend-UI

**Aufgabe 1: Banner in `app/+layout.svelte`**

- `frontend/src/routes/app/+layout.svelte` um einen DaisyUI-`alert`-Block ergänzen, sichtbar bei
  `session.currentUser?.emailVerified === false` (siehe Entscheidung 3.6), mit einem
  „Erneut senden"-Button, der `MembersApi.resendVerificationEmail({memberId})` aufruft
  (`apiConfiguration` aus `httpClient.ts`, Abschnitt 2.8) und bei Erfolg einen Toast zeigt
  (`Toast`-Klasse, `frontend/src/lib/toast.ts`), sowie einem Schließen-Button.
- Reine Funktion `isVerificationBannerDismissed(memberId: string): boolean` und
  `dismissVerificationBanner(memberId: string): void` in neuer Datei
  `frontend/src/lib/verificationBannerDismissal.ts` — kapselt den `localStorage`-Zugriff
  (Key `email-verification-banner-dismissed`, Wert = zuletzt geschlossene Member-ID, siehe
  Entscheidung 3.6) hinter einer testbaren Funktion statt direkter `localStorage`-Aufrufe in der
  Komponente.
- Test zuerst, `frontend/src/lib/verificationBannerDismissal.spec.ts`:
  - `it('isVerificationBannerDismissed_notDismissed_returnsFalse')`
  - `it('isVerificationBannerDismissed_dismissedForSameMemberId_returnsTrue')`
  - `it('isVerificationBannerDismissed_dismissedForDifferentMemberId_returnsFalse')` — deckt den
    Accountwechsel-Fall ab (siehe Entscheidung 3.6).
  - Rot: Modul fehlt.
  - Grün: minimale `localStorage.getItem`/`setItem`-Implementierung.
  - Refactor: Named Constant für den Storage-Key statt Magic String.
- Test zuerst, `frontend/src/routes/app/+layout.svelte.spec.ts` (Component-Test, folgt dem
  `vitest-browser-svelte`-Muster aus `JoinWizard.svelte.spec.ts`, siehe Vorgänger-Plan
  Abschnitt 2.7):
  - `it('zeigt das Verifikationsbanner, wenn emailVerified false ist')`
  - `it('zeigt kein Banner, wenn emailVerified true ist')`
  - `it('ruft resendVerificationEmail auf und zeigt einen Erfolgs-Toast bei Klick auf Erneut senden')`
  - `it('zeigt das Banner nach Reload nicht erneut, wenn es zuvor für denselben Member geschlossen wurde')`
  - `it('zeigt das Banner weiterhin, wenn emailVerified weiterhin false ist, obwohl es zuvor geschlossen wurde und der Reload simuliert wird')`
    — stellt sicher, dass „geschlossen" nur das Banner ausblendet, nicht den Verifikationsstatus
    selbst überschreibt (siehe Klarstellung in Entscheidung 3.6).
  - Rot: Banner-Logik fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig bei dieser Größe.
- Neue i18n-Keys `frontend/messages/de.json`/`en.json`: `verification.banner_text`,
  `verification.resend_button`, `verification.resend_success_toast`,
  `verification.resend_error_toast`. `npm run paraglide` danach ausführen.

**Aufgabe 2: `/members/verification/confirm`-Landing-Page**

- Neue Route `frontend/src/routes/verify-email/[token]/+page.svelte` (analog
  `frontend/src/routes/invite/[token]/+page.svelte`), ruft beim Mount
  `MembersApi.confirmEmailVerification({token})` auf, zeigt Erfolg/Fehler-Zustand, Link zum
  Dashboard bei Erfolg.
- Test zuerst, `verify-email-page.svelte.spec.ts` (analog `login-page.svelte.spec.ts`, siehe
  Ist-Zustand 2.8):
  - `it('ruft confirmEmailVerification mit dem Token aus der URL auf')`
  - `it('zeigt einen Fehlerzustand bei ungültigem/abgelaufenem Token (409)')`
  - Rot: Route fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: `extractErrorStatus`/Problem-Type-Klassifizierung wiederverwenden
    (`frontend/src/lib/api/errorStatus.ts`, `problemMapping.ts` — bereits vorhanden laut
    Vorgänger-Plan Aufgaben P1.10/P1.7, hier nur um den neuen Problem-Type
    `/problems/verification-token-invalid` erweitern).

### P2.4 — OpenAPI: Passwort-Reset-Flow

**Aufgabe 1: Schema-Erweiterungen in `api/openapi.yml`**

- Neue Schemas: `PasswordResetRequest { email: string (format: email) }`,
  `PasswordResetConfirm { token: string (format: uuid), newPassword:
  $ref: "#/components/schemas/Password" }`.
- Neue Pfade:
  - `POST /password-reset/request` — `security: []`, Body `PasswordResetRequest`, **immer** 202
    (nicht 200/204 — „Accepted", signalisiert bewusst "wird bearbeitet", ohne festzulegen ob eine
    Mail tatsächlich verschickt wurde), unabhängig davon, ob die E-Mail existiert (ENUM1, siehe
    Ist-Zustand 2.4). Kein 404/409 für „E-Mail nicht gefunden" — das wäre der ENUM1-Verstoß, den
    dieser Endpoint gerade vermeiden muss.
  - `POST /password-reset/confirm` — `security: []`, Body `PasswordResetConfirm`, 204 bei Erfolg,
    409 mit `Problem.type` `/problems/password-reset-token-invalid` (Token ungültig/abgelaufen,
    analog P2.1).
- `npm run openapi` / `./mvnw clean compile` danach.

### P2.5 — Backend: Passwort-Reset

**Aufgabe 1: `AccountService.resetPassword`**

- `AccountService` um `resetPassword(UUID memberId, String newPassword)` erweitern — **kein**
  `oldPassword`-Parameter (Unterschied zu `changePassword`, da der Nutzer sein altes Passwort per
  Definition nicht mehr weiß/nutzen will).
- Test zuerst, Erweiterung von `AccountServiceIT` (siehe Referenzmuster Abschnitt 2.5, Zeile 75–93):
  - `it('resetPassword_validCall_persistsNewHashedPassword')` — analog
    `changePassword_correctOldPassword_persistsNewHashedPassword` (Zeile 76–93), aber ohne
    Alt-Passwort-Prüfung.
  - Rot: Methode fehlt.
  - Grün: `accountRepository.updatePasswordHash(memberId, passwordEncoder.encode(newPassword))`.
  - Refactor: keiner nötig.

**Aufgabe 2: `PasswordResetRequested`-Event + Publizierung**

- Neue Datei `.../household/PasswordResetRequested.java` (Root-Package, Record `(UUID memberId,
  String email)`, analog `AccountRegistered`).
- Neue/erweiterte Service-Methode in `MemberManagementService` (oder neuer
  `PasswordResetService`, siehe unten): `requestPasswordReset(String email)` — sucht
  `findMemberIdByEmail(email)` (bereits vorhanden, Zeile 186–188 in `MemberManagementService`),
  publiziert bei Treffer `PasswordResetRequested`; **bei keinem Treffer wird still nichts getan**
  (kein Fehler, kein Event) — der Controller antwortet in beiden Fällen identisch mit 202 (siehe
  P2.4, ENUM1).
- Test zuerst (Unit, `ApplicationEventPublisher`+`MemberRepository` gemockt):
  - `it('requestPasswordReset_existingEmail_publishesPasswordResetRequestedEvent')`
  - `it('requestPasswordReset_unknownEmail_publishesNothing')`
  - Rot: Methode fehlt.
  - Grün: wie oben.
  - Refactor: keiner nötig.

**Aufgabe 3: `notifications`-Listener für `PasswordResetRequested`**

- Neue Datei `.../notifications/internal/PasswordResetRequestedListener.java`, analog
  `AccountRegistrationListener` (Aufgabe 4 in P2.2), nutzt `accountTokenService.issueToken(...,
  "PASSWORD_RESET", PASSWORD_RESET_TOKEN_VALIDITY)` + `emailSenderService.sendPasswordResetEmail`.
- Test zuerst (Unit, Dependencies gemockt), IT-Test analog `AccountRegistrationListenerIT`.

**Aufgabe 4: Session-Invalidierung nach Reset (Entscheidung 3.3)**

- `SessionRegistry`-Bean + `HttpSessionEventPublisher`-Bean in `SecurityConfig` ergänzen.
- `AccountService.resetPassword` (oder ein neuer, dedizierter `PasswordResetService`, der
  `AccountService`, `SessionRegistry` und `OAuth2AuthorizedClientService` orchestriert, um
  `AccountService` selbst frei von Security-Infrastruktur-Belangen zu halten — Empfehlung: **neuer
  Service**, da `AccountService` sonst zwei fachlich unterschiedliche Verantwortlichkeiten
  bekäme, SRP) ruft nach dem Passwort-Update `sessionRegistry.getAllSessions(...)` +
  `expireNow()` je Session sowie `authorizedClientService.removeAuthorizedClient(CLIENT_ID,
  email)` auf.
- Test zuerst, neue IT-Testklasse `PasswordResetServiceIT` (`@SpringBootTest(webEnvironment =
  MOCK)`, da echte `HttpSession`-Interaktion nötig ist, analog `HouseholdSetupServiceIT`, Zeile
  36–44 zur Begründung von `MOCK` statt `NONE`):
  - `it('confirmPasswordReset_existingSession_expiresAllSessionsForThatAccount')` — Setup: über
    `AccountSessionAuthenticator`-Analogon (oder direktes `SessionRegistry.registerNewSession`)
    eine Session simulieren, dann Reset auslösen, prüfen `sessionRegistry.getSessionInformation(
    sessionId).isExpired()` ist `true`.
  - Rot: Mechanismus fehlt.
  - Grün: wie oben beschrieben.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 5: Controller-Endpoints**

- Neuer Controller-Delegate oder Erweiterung von `SessionApiDelegateImpl`
  (`requestPasswordReset(PasswordResetRequest)`, `confirmPasswordReset(PasswordResetConfirm)`) —
  Empfehlung: **neue Klasse** `PasswordResetApiDelegateImpl` im `household.controller`-Package
  (nicht `session`, da inhaltlich näher an `AccountService`/`MemberManagementService` als an
  `GET /me`/`POST /logout`, die beide reine Session-Introspektion sind, kein Account-Mutations-
  Bezug) — abweichend von der in Abschnitt 2.4 zunächst vorgeschlagenen `session`-Tag-Zuordnung;
  der OpenAPI-**Tag** (`session`) und das Backend-**Package** (`household.controller`) müssen
  nicht identisch sein, siehe bereits bestehende Asymmetrie (`checkEmailAvailability` liegt unter
  Tag `members`, aber im selben `MembersApiDelegateImpl` wie `changePassword`).
- Test zuerst (Unit, Services gemockt):
  - `it('requestPasswordReset_anyEmail_alwaysReturns202')`
  - `it('confirmPasswordReset_validToken_resetsPasswordAndReturns204')`
  - `it('confirmPasswordReset_invalidToken_returns409WithPasswordResetTokenInvalidProblem')`
  - Rot: Endpoints fehlen (Compile-Fehler durch P2.4-Vertragsänderung).
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig.

**Aufgabe 6: Sicherheits-Checkliste aktualisieren (neuer Eintrag RESET1)**

- `docs/architecture/chapters/08_concepts.adoc` (Security-Controls-Tabelle, siehe Abschnitt 2.7):
  neue Zeile `RESET1` einfügen (Empfehlung: direkt nach `VERIFY1`, vor `ERR1`) mit Control
  „Passwort-Reset-Token sind Single-Use, kurzlebig, und ein erfolgreicher Reset invalidiert alle
  bestehenden Sessions des Accounts", ASVS-Referenz V6.2.3/V6.3.2 (je nach finaler
  ASVS-5.0-Nummerierung prüfen), Status „Decided, siehe Session-Invalidierungs-Entscheidung in
  diesem Plan (Abschnitt 3.3)" nach Umsetzung von Aufgabe 4 (Session-Invalidierung).
- Kein Code, keine Tests — reine Doku-Änderung, direkt im Zuge dieser Aufgabe erledigt.

### P2.6 — Frontend: „Passwort vergessen"-Flow

**Aufgabe 1: Link auf `/login`**

- `frontend/src/routes/login/+page.svelte` um einen `<a href="/forgot-password">`-Link ergänzen
  (UI-only, kein Test nötig laut AGENTS.md).
- Neuer i18n-Key `login.forgot_password_link`.

**Aufgabe 2: `/forgot-password`-Route**

- Neue Route `frontend/src/routes/forgot-password/+page.svelte`: E-Mail-Eingabefeld, Submit ruft
  `SessionApi.requestPasswordReset({email})` (oder generierte Client-Klasse je nach P2.5-
  Controller-Zuordnung), zeigt **immer** dieselbe Erfolgsmeldung („Falls ein Konto mit dieser
  E-Mail existiert, wurde eine E-Mail verschickt.") unabhängig vom Ergebnis — konsistent mit
  ENUM1/P2.4.
- Test zuerst, `forgot-password-page.svelte.spec.ts`:
  - `it('ruft requestPasswordReset mit der eingegebenen E-Mail auf')`
  - `it('zeigt die generische Erfolgsmeldung auch bei einem Fehler-Response an')` — testet
    explizit das ENUM1-Verhalten, nicht nur den Happy Path.
  - Rot: Route fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig.
- Neue i18n-Keys `forgot_password.title`, `..._email_label`, `..._submit_button`,
  `..._success_message`.

**Aufgabe 3: `/reset-password/[token]`-Route**

- Neue Route `frontend/src/routes/reset-password/[token]/+page.svelte`: nutzt
  `PasswordField.svelte` (Abschnitt 2.8) für das neue Passwort, ruft bei Submit
  `confirmPasswordReset({token, newPassword})` auf, bei Erfolg Redirect zu `/login` mit
  Erfolgshinweis, bei 409 (Problem-Type `password-reset-token-invalid`) Fehleranzeige mit Link
  zurück zu `/forgot-password`.
- Test zuerst, `reset-password-page.svelte.spec.ts`:
  - `it('ruft confirmPasswordReset mit Token aus der URL und eingegebenem Passwort auf')`
  - `it('zeigt einen Fehlerzustand und einen Link zurück zu forgot-password bei ungültigem Token')`
  - Rot: Route fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: `extractErrorStatus`/`classifyConflictProblem`-Äquivalent für den neuen
    Problem-Type wiederverwenden (siehe P2.3 Aufgabe 2, dieselbe Erweiterung).
- Neue i18n-Keys `reset_password.title`, `..._password_label`, `..._submit_button`,
  `..._invalid_token_error`, `..._back_to_forgot_password_link`.

### P2.7 — Backend: Rate-Limiting & Lockout

**Voraussetzung:** Rückfrage aus Entscheidung 3.4 muss geklärt sein (Bibliothek vs. Eigenbau),
bevor Aufgabe 1 beginnt.

**Aufgabe 1: Rate-Limiting-Filter/-Aspekt für Login**

- Abhängig vom Ergebnis der Rückfrage: entweder ein neuer `OncePerRequestFilter`
  (`RateLimitingFilter`, in `config`-Package, analog `CsrfCookieFilter`), der vor dem
  `formLogin()`-Endpoint (`/login`, `POST`) greift und pro `username`+IP-Kombination einen
  Bucket4j-`Bucket` prüft, oder ein äquivalenter Eigenbau-Zähler gegen eine neue
  `login_attempt`-Tabelle.
- Test zuerst (Unit, `Bucket`/Zähler-Logik als reine Funktion extrahiert, damit sie ohne
  Servlet-Mocking testbar ist — analog dem Muster `isStateChangingMethod`/
  `getCsrfTokenFromCookieHeader` im Frontend, hier als reine Java-Methode
  `boolean isRateLimited(String key)` in einer eigenen, gemockten `RateLimiter`-Abstraktion):
  - `it('isRateLimited_underThreshold_returnsFalse')`
  - `it('isRateLimited_atThreshold_returnsTrue')`
  - `it('isRateLimited_afterWindowExpiry_returnsFalseAgain')`
  - Rot: Klasse fehlt.
  - Grün: minimale Implementierung je nach gewählter Option.
  - Refactor: Schwellenwerte als konfigurierbare Properties
    (`librehousehold.security.rate-limit.login.max-attempts`,
    `...window-duration`), analog dem `librehousehold.security.*`-Namespace-Muster
    (Abschnitt 2.6).
- **IT-Test** (da der Filter/Aspekt Teil der echten Filter-Chain ist, analog
  `AuthorizationServerConfigurationIT`/`ClientRegistrationAuthorizationUriOverrideIT` als
  Referenzmuster für Filter-Chain-Tests): `it('login_tooManyFailedAttempts_returns429')`.

**Aufgabe 2: Rate-Limiting für Reset-/Verifikations-Endpunkte**

- Denselben Filter/Aspekt aus Aufgabe 1 auf `POST /password-reset/request`,
  `POST /password-reset/confirm`, `POST /members/verification/confirm`,
  `POST /members/{memberId}/verification/resend` anwenden (Meta-Plan-Wortlaut „Login-, Reset-
  und Verifikations-Endpunkte", Zeile 147–148) — Schlüssel hier eher IP-basiert als
  username-basiert, da diese Endpoints teils unauthentifiziert sind und keinen sinnvollen
  „username" haben (bei `password-reset/request` wäre die E-Mail selbst der Schlüssel, aber ein
  Rate-Limit **nur** nach E-Mail würde einen Angreifer nicht bremsen, der viele verschiedene
  E-Mails durchprobiert — zusätzlich IP-basiertes Limit nötig).
- Test zuerst, analog Aufgabe 1, für jeden der vier Endpoints mindestens ein
  `it('...tooManyRequests_returns429')`-IT-Test.

**Aufgabe 3: Lockout nach wiederholten Fehlversuchen (über reines Rate-Limiting hinaus)**

- Abgrenzung zu Aufgabe 1: Rate-Limiting drosselt die *Rate* (z. B. „max. 5 Versuche pro Minute"),
  Lockout sperrt den *Account* nach einer höheren Schwelle unabhängig vom Zeitfenster (z. B. „nach
  10 Fehlversuchen für 15 Minuten gesperrt, unabhängig davon, wie langsam sie erfolgten") — beide
  Mechanismen ergänzen sich (RATE1 nennt explizit „Rate limiting **/** anti-automation").
- Empfehlung: `account.locked_until TIMESTAMP WITH TIME ZONE NULL`-Spalte (weitere
  Migration, `V3__add_account_lockout.sql`), `AccountUserDetailsService`
  (`.../household/service/AccountUserDetailsService.java`, bestehende Klasse) prüft beim Laden
  zusätzlich `locked_until`, `AuthenticationFailureBadCredentialsEvent`-Listener (Spring-Security-
  eigenes Event, kein neuer Mechanismus) zählt Fehlversuche hoch und setzt `locked_until` bei
  Erreichen der Schwelle.
- Test zuerst (Unit für den Event-Listener, Dependencies gemockt; IT für
  `AccountUserDetailsService` mit tatsächlich gesperrtem Account):
  - `it('onAuthenticationFailure_thresholdReached_locksAccountForConfiguredDuration')`
  - `it('loadUserByUsername_lockedAccount_throwsLockedException')`
  - Rot: Mechanismus fehlt.
  - Grün: minimale Implementierung.
  - Refactor: Schwellenwert/Sperrdauer als Property
    (`librehousehold.security.lockout.max-attempts`, `...lockout-duration`).

**Aufgabe 4: Sicherheits-Checkliste aktualisieren (RATE1 von „deferred" auf „Decided")**

- `docs/architecture/chapters/08_concepts.adoc:100-103` (`RATE1`, siehe Abschnitt 2.7): Status von
  „Deliberately deferred to a dedicated future hardening effort" auf „Decided" ändern, mit Verweis
  auf den in Aufgabe 1–3 umgesetzten Mechanismus (Rate-Limiting-Ansatz aus Entscheidung 3.4,
  Lockout aus Aufgabe 3).
- Zugehöriger Risk-Eintrag in `docs/architecture/chapters/11_technical_risks.adoc` (falls RATE1
  dort referenziert ist — prüfen und ggf. als erledigt markieren statt als offenes Risiko
  stehenzulassen) ebenfalls aktualisieren.
- Kein Code, keine Tests — reine Doku-Änderung, direkt im Zuge dieser Aufgabe erledigt, nicht als
  separate Nacharbeit vermerkt.

---

## Offene Nacharbeit außerhalb dieses Plans

- Der leere `notifications.service`-Ordner sollte beim Anlegen der ersten echten Dateien in
  `notifications.internal` (P2.2) entfernt werden, falls Entscheidung 3.1 wie empfohlen
  ausfällt — als Aufräumschritt innerhalb von P2.2 Aufgabe 4, nicht als separater Punkt.