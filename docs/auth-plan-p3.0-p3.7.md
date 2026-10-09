# Implementierungsplan P3.0–P3.7: Social Login (föderiert, konfigurierbar)

Referenz: [`docs/auth-meta-plan.md`](auth-meta-plan.md), Abschnitt „Phase 3 — Social Login
(föderiert, konfigurierbar)" (Zeilen 175–209), Punkte **P3.0–P3.7**. Dieses Dokument ist ein
Arbeitsdokument (kein Arc42-Kapitel, kein ADR) und wird nach Umsetzung nicht dauerhaft gepflegt.

**Scope:** Föderiertes Login gegen externe OIDC-/OAuth2-Provider (Google, GitHub, Microsoft,
Keycloak, Authentik, generisches OIDC) für **lokale** Accounts im 1:1-Account-Household-
Modell (ADR-012), inklusive einer lokalen Dex-basierten Dev-/Test-Umgebung (P3.0) und
selfhoster-gerichteter README-Dokumentation (P3.7). Explizit **nicht** Teil dieses Plans:

- **Apple („Sign in with Apple").** Vom Nutzer nach Vorlage der Entscheidung explizit aus dieser
  Phase herausgenommen („Lass das erstmal weg. Apple muss nicht unbedingt sein.") — der
  JWT-Client-Secret-Mechanismus (Rotation, Team-/Key-ID) ist eigenständig genug, um bei Bedarf
  als eigener, späterer Punkt nachgezogen zu werden, siehe „Offene Nacharbeit außerhalb dieses
  Plans" am Ende dieses Dokuments. Das Konfigurationsschema (Abschnitt 3.2) bleibt aber so
  gestaltet, dass ein weiterer Provider-Typ sich später ohne Bruch ergänzen lässt.
- Der eigentliche lokale Accounts-Flow (Setup/Login/Passwort-Reset/Verifikation/Rate-Limiting) —
  bereits umgesetzt (P1/P2), wird hier nur insofern berührt, wie Federation ihn erweitert
  (z. B. `password_hash` nullable, Login-Blocking-Gate für föderierte Principals).
- Account-Unlinking/-Deaktivieren einzelner Provider nach Ersteinrichtung (kein Bestandteil des
  Meta-Plan-Wortlauts P3.1–P3.6; wird — falls gewünscht — ein eigener, späterer Punkt).
- Multi-Faktor-Authentifizierung (TOTP/WebAuthn) — separates Thema, nicht Teil von „Social Login".
- Mobile-native Deep-Link-Rückkehr aus Provider-Apps (die Anwendung ist eine PWA/SPA, kein
  natives App-Callback-Schema geplant).

**Stand der Design-Entscheidungen (nach Rückfrage-Runde):** Anders als beim Vorgänger-Detailplan
(`auth-plan-p2.1-p2.7.md`, git-historisch, siehe dort Zeile 23–33) wurden die Punkte in Abschnitt 3
nicht in einem durchgehenden Gespräch entschieden, sondern in zwei Schritten: Recherche +
Empfehlung/Alternative(n) hier im Dokument, danach eine gezielte Rückfrage zu den vier
architektonisch wichtigsten bzw. laut AGENTS.md „Dependency Management" rückfragepflichtigen
Punkten. Ergebnis:

- **Entschieden (vom Nutzer bestätigt):** 3.2 (eigene `social-login.*`-Konfigurationsschicht),
  3.3 (Kontexttransport über einen Session-Endpoint), 3.12 (Dex-Wiring über
  `wf.garnier:testcontainers-dex`).
  3.7 wurde durch die Rückfrage **aufgelöst**, aber anders als erwartet: Apple entfällt komplett
  aus dem Scope dieser Phase (siehe oben), die ursprüngliche Frage „wie generieren wir das
  JWT-Client-Secret" stellt sich damit hier nicht mehr.
- **Als vom übrigen Kontext vorgezeichnet übernommen, ohne separate Rückfrage** (Begründung jeweils
  in Abschnitt 3): 3.1 (Federation-Architektur — von ADR-014 praktisch erzwungen), 3.4
  (E-Mail-Linking — im Meta-Plan-Wortlaut selbst bereits so benannt), 3.5 (`password_hash`
  nullable — wörtlich in ADR-015 antizipiert), 3.6 (GitHub-Unterstützung trotz Nicht-OIDC — im
  Meta-Plan-Wortlaut und im P3.7-Auftrag explizit gefordert), 3.10 (Setup/Invite rein über Social
  ohne Passwort-Workaround — im Meta-Plan-Wortlaut wörtlich „rein über Social" gefordert).
- **Empfehlung übernommen, geringe Tragweite/reversibel, keine gesonderte Rückfrage gestellt:**
  3.8 (Discover-Endpoint-Gestaltung), 3.9 (Frontend-UI-Ansatz), 3.11 (reine
  Dokumentationskonsequenz), 3.13 (README-Struktur). Widerspruch jederzeit möglich, siehe je
  Abschnitt.

Status: Entwurf, noch nicht umgesetzt.

---

## 1. Bindende Vorgaben

Übernommen aus dem Meta-Plan und den referenzierten ADRs, hier nur die für Phase 3 relevanten
Konsequenzen:

- **ADR-009 (Argon2id):** Bleibt unverändert für lokale Passwörter. Für rein föderierte Accounts
  (kein lokales Passwort, siehe Abschnitt 3.5) entfällt Argon2id schlicht — es gibt nichts zu
  hashen. Keine neue Hashing-Logik nötig.
- **ADR-011 (Named Interfaces vs. Domain Events):** Jede neue Cross-Modul-Interaktion folgt der
  bestehenden Entscheidungstabelle. Konkret: Der Discover-Endpoint (P3.4) braucht **keine** neue
  Named Interface, da er reine, prozessweit gültige Konfiguration liefert (keine Haushalts-/
  Mitgliedsdaten) und daher direkt aus einer `@ConfigurationProperties`-Bean gelesen werden kann,
  unabhängig vom Modul, in dem der Controller liegt. Account-Linking (P3.3) bleibt vollständig
  innerhalb von `household` (kein neues Modul, siehe Abschnitt 3.4).
- **ADR-012 (1 Account : 1 Household):** Zentrale Randbedingung für die gesamte Phase 3. Eine
  extern beglaubigte Identität (Google-`sub`, GitHub-`id`, Apple-`sub` etc.) muss sich **immer**
  auf genau einen `member`/`account` in genau einem Haushalt auflösen — es gibt kein
  „Wähle einen Haushalt nach dem Google-Login"-Konzept, wie es Multi-Tenant-SaaS-Anwendungen
  oft haben. Das vereinfacht das Linking-Modell (keine Mehrdeutigkeit, welcher Haushalt gemeint
  ist), verschärft aber die Frage, **in welchem Kontext** (Setup/Invite/Login) ein Federation-
  Redirect gestartet wurde — siehe Abschnitt 3.3, die zentrale neue Design-Frage dieser Phase.
- **ADR-013 (Spring Authorization Server als AS *und* Resource Server):** Antizipiert Phase 3
  bereits explizit in der Begründung (siehe Abschnitt 2.4) — SAS wurde u. a. genau deshalb
  gewählt, weil es „both cases — local password accounts today and federated social login in the
  future — through the same standardized Authorization Code + PKCE flow" unterstützt, ohne einen
  weiteren Dienst zu betreiben. Diese Phase löst diese Antizipation ein, verändert aber nicht die
  Grundarchitektur (kein Wechsel zu einem externen IdP, kein zusätzlicher Dienst).
- **ADR-014 (BFF-Pattern, httpOnly-Cookie-Session):** Bleibt vollständig unverändert. Tokens —
  weder die eigenen SAS-Tokens noch die der externen Provider (Google/GitHub-Access-Tokens,
  Apple-`id_token`) — dürfen jemals den Browser erreichen. Das ist der entscheidende Grund,
  warum die „Spring-Authorization-Server-Federation"-Architektur (Abschnitt 3.1) und nicht die
  Alternative gewählt werden sollte: Bei letzterer würde das SPA direkt Tokens externer Provider
  empfangen.
- **ADR-015 (dedizierte `account`-Tabelle):** Antizipiert Phase 3 ebenfalls bereits explizit
  (siehe Abschnitt 2.4 unten, wörtliches Zitat) — „a future account may be entirely federated (no
  password hash at all)". Das ist ein **starker, im ADR bereits vorweggenommener Hinweis**, dass
  `account.password_hash` in dieser Phase nullable werden sollte, statt eine komplett neue
  Account-Tabellenstruktur einzuführen (siehe Abschnitt 3.5).
- **OWASP ASVS 5.0 L2** (`docs/architecture/chapters/08_concepts.adoc:36-135`, vollständige
  Tabelle in Abschnitt 2.5 wiedergegeben): Phase 3 berührt konkret **RED1** (bereits „Decided" für
  die *eigenen* Registered Clients — deckt aber **nicht** automatisch die neuen *externen*
  Provider-Registrierungen ab, siehe Abschnitt 3.11), **RED2** (noch „Open" — invite-link-artige
  Redirects, jetzt zusätzlich relevant für den Rücksprung aus einem externen Provider),
  **ENUM1** (bereits „Decided", muss um das Linking-Verhalten erweitert werden, siehe 3.4) und
  **PKCE1** (bereits „Decided" für den SPA↔eigener-AS-Flow — für den neuen AS↔externer-Provider-
  Flow bietet Spring Security PKCE für Confidential Clients nicht standardmäßig an, siehe 3.1).
  Phase 3 braucht mindestens einen neuen Tabelleneintrag (**FED1**, siehe Abschnitt 3.11).
- **TDD-Pflicht** (AGENTS.md): Unit-Tests mocken nur direkte Dependencies, Mapper werden nie
  gemockt, jede DB-lesende/schreibende Service-Methode braucht mindestens einen Happy-Path-`*IT`-
  Test. Für Filter-Chain-/OAuth2-Client-Verhalten (z. B. „ein `OidcUser` von Provider X wird zu
  Member Y gemappt") gilt dasselbe Muster wie in `AuthorizationServerConfigurationIT` (siehe
  Vorgänger-Plan) — echte HTTP-Interaktion über `@SpringBootTest(webEnvironment = RANDOM_PORT` oder
  `MOCK)`, kein Mocken von Spring-Security-internen Klassen (`OAuth2AuthorizationRequest` etc.).
  Für die externen Provider selbst braucht es einen Test-Double — genau dafür ist P3.0 (Dex) da:
  IT-Tests laufen gegen einen echten, aber lokalen OIDC-Server, nicht gegen echte Google-/GitHub-
  Endpunkte (die wären weder in CI erreichbar noch deterministisch, siehe AGENTS.md „System/E2E:
  mock only external third-party APIs that are impractical to run in CI" — ein echter Google-
  Login ist genau so ein Fall).
- **Keine neue Dependency ohne Rückfrage** (AGENTS.md „Dependency Management"): betrifft in dieser
  Phase `wf.garnier:testcontainers-dex`/`spring-boot-testcontainers-dex` (P3.0, Abschnitt 3.12,
  **Rückfrage gestellt und bestätigt**) und Microsofts „Spring Boot Starter for Microsoft Entra"
  (P3.2, Abschnitt 3.2, **Empfehlung: nicht hinzufügen**, keine gesonderte Rückfrage nötig, da
  geringe Tragweite). Eine Apple-spezifische Dependency-Frage (`patrickbussmann/oauth2-apple`)
  stellt sich nicht mehr — Apple ist nach Nutzerentscheidung aus dieser Phase herausgenommen
  (siehe Scope-Absatz am Dokumentanfang). `spring-security-oauth2-client` selbst ist bereits
  vorhanden (`backend/pom.xml:84-86`, siehe Abschnitt 2.8) — **keine** Rückfrage nötig, um
  zusätzliche `ClientRegistration`s zu konfigurieren.
- **DaisyUI-Modals statt `confirm()`/`alert()`/`prompt()`**, **Toast-Store für Fehler**
  (AGENTS.md „UI Conventions"/„Error Handling") — relevant für P3.5 (Provider-Buttons, Fehler beim
  Rücksprung von einem externen Provider, z. B. „Zugriff verweigert" oder „E-Mail nicht
  verifiziert").
- **Modul-Grenzen/ADR-011 „Root-Package = öffentlich, Subpackages = intern"**: Neue
  Account-Linking-Logik gehört, wie die bestehende `AccountRegistration`, in
  `household.service`; ein neues `AccountIdentityEntity`/`AccountIdentityRepository` folgt exakt
  dem bestehenden `AccountTokenEntity`/`AccountTokenRepository`-Muster (siehe Abschnitt 2.11 und
  Abschnitt 3.4).
- **Spring Data JDBC: Prefer Derived Queries** (AGENTS.md) — für die neue `account_identity`-
  Tabelle (Abschnitt 3.4) gilt dasselbe Muster wie für `account_token`: Derived Queries wo möglich,
  `@Modifying @Query` nur für Updates auf dem `Persistable`-immer-`isNew()`-Muster.
- **Test Data/Instancio, Mapper-Test-Konventionen, `given`/`when`/`then`,
  `methodName_input_expectedOutput`**: gelten unverändert für alle neuen Tests in Abschnitt 5.

---

## 2. Ist-Zustand (mit Datei-/Zeilenreferenzen)

### 2.1 `SecurityConfig`: Drei-Chain-Struktur bereits vorhanden, aber nur ein interner OIDC-Loop

`backend/src/main/java/eu/wiegandt/librehousehold/config/SecurityConfig.java` (294 Zeilen)
enthält bereits genau die zwei Filter-Chains, die die Referenz-Architektur aus Spring Authorization
Servers offiziellem „How-to: Authenticate using Social Login"-Guide (siehe Abschnitt 3.1) braucht
— aber mit einer entscheidenden Einschränkung:

- **Chain 1 — `authorizationServerSecurityFilterChain`** (Zeile 184–202, `@Order(1)`): der
  eigentliche Authorization Server, `securityMatcher` auf die AS-eigenen Endpunkte,
  `anyRequest().authenticated()`, `.oidc(Customizer.withDefaults())`. Leitet unauthentifizierte
  HTML-Requests per `LoginUrlAuthenticationEntryPoint` auf `/login` um (Zeile ~200).
- **Chain 2 — `defaultSecurityFilterChain`** (Zeile 204–292, implizit nach Chain 1 einsortiert):
  enthält bereits `.oauth2Login((login) -> login.userInfoEndpoint((userInfo) ->
  userInfo.oidcUserService(oidcUserService)))` (Zeile 272–273) — **aber** das ist der
  bestehende, rein interne „Loop-Back"-Mechanismus: Das Backend ist hier OAuth2-Client **seines
  eigenen** embedded Authorization Servers (Chain 1), nicht eines externen Providers. Der Zweck
  (siehe `AccountOidcUserService`, Abschnitt 2.12) ist, aus dem intern ausgestellten `OidcUser`
  einen `AccountOidcPrincipal` mit `memberId`/`householdId`/`isAdmin` zu bauen — die eigentliche
  Autorisierungsgrundlage für alle Business-Endpoints.
- Es gibt **aktuell genau eine** `ClientRegistration`, manuell gebaut (nicht per
  `ClientRegistrations.fromIssuerLocation`, Zeile 140–161, mit Begründung im Kommentar: Issuer-
  Discovery würde eager beim Context-Refresh laufen, bevor der Servlet-Container Requests
  annimmt) — `clientId = RegisteredClientSeeder.CLIENT_ID` (Konstante `"spa-backend-client"`,
  siehe Abschnitt 2.15). `InMemoryClientRegistrationRepository` enthält also nur diesen einen
  Eintrag.
- **Für P3.1 fehlt exakt eine dritte Ebene**, die der offizielle Guide vorsieht: `oauth2Login()`
  **auf Chain 1 selbst** (dort, wo heute nur `formLogin()` via `LoginUrlAuthenticationEntryPoint`
  auf `/login` verweist) gegen **externe** Provider — siehe Abschnitt 3.1 für die exakte
  Ziel-Architektur und den Vergleich mit dem offiziellen Referenz-Code.
- CORS-Bean (Zeile 171–182): expliziter Allow-List-Ansatz, `allowCredentials(true)`. Unverändert
  relevant, keine Anpassung durch Phase 3 nötig (externe Provider-Redirects laufen über den
  Browser direkt zum Provider und zurück zum Backend, nicht über CORS-Requests des SPA).
- Rate-Limiting-Filter (Zeile 261) und `ConcurrentSessionFilter` (Zeile 268) sind bereits vor der
  bestehenden `UsernamePasswordAuthenticationFilter` eingehängt — für P3.2/P3.3 zu klären, ob ein
  fehlgeschlagener externer Login (z. B. Provider lehnt ab) ebenfalls rate-limitiert werden soll
  (siehe Abschnitt 3.11, `FED1`).

### 2.2 `application.yaml`: `librehousehold.security.*`-Namespace, kein Provider-Schema

`backend/src/main/resources/application.yaml` (129 Zeilen) hat bereits die Properties aus P1/P2
(`oauth2-authorization-server.issuer` Zeile 54, `oauth2-client.redirect-uri` Zeile 56,
`cors.allowed-origins` Zeile 60–66, `email-verification.*` Zeile 67–80, `password-reset.*` Zeile
81–86, `rate-limit.*` Zeile 87–102, `lockout.*` Zeile 103–110, `librehousehold.frontend.base-url`
Zeile 112–118, `librehousehold.notifications.templates.override-dir` Zeile 120–128). **Keine**
`spring.datasource.*`-Properties in dieser Datei — DB-Verbindung kommt vollständig von außen
(Env-Variablen/Testcontainers-`@ServiceConnection`, siehe Abschnitt 2.7). **Kein**
`spring.security.oauth2.client.registration.*`/`.provider.*`-Block existiert bisher — P3.2 legt
diesen komplett neu an (siehe Abschnitt 3.2 für das Schema).

`spring.mail.host: ""` (Zeile 7–14, leer per Default mit Fail-Fast-Kommentar) ist ein direktes
Vorbild für das Muster „Property ohne Default, Selfhoster **muss** explizit setzen" — dasselbe
Muster gilt für jede Provider-`client-secret` in P3.2.

### 2.3 Frontend: `oauth2Login.ts` — ein einziger hartkodierter Pfad, keine Provider-Auswahl

`frontend/src/lib/oauth2Login.ts` (21 Zeilen, vollständig):

```ts
export const OAUTH2_LOGIN_PATH = '/oauth2/authorization/spa-backend-client';

export function redirectToOAuth2Login(): void {
	window.location.href = OAUTH2_LOGIN_PATH;
}

export async function completeSilentOAuth2Login(): Promise<void> {
	await fetch(OAUTH2_LOGIN_PATH, { credentials: 'include' });
}
```

Kommentare erklären: niemals direkt zu `/login` navigieren; nur
`/oauth2/authorization/spa-backend-client` lässt den Server den Request sichern und den vollen
Authorization-Code-+-PKCE-Roundtrip fortsetzen. `completeSilentOAuth2Login` wird direkt nach
Setup/Invite-Join genutzt (`AccountSessionAuthenticator` hat die AS-Session bereits
authentifiziert, siehe Abschnitt 2.13), um den OAuth2-Hand-off ohne sichtbares Login-Formular
abzuschließen. **Kein PKCE-Code im Frontend sichtbar** — PKCE wird vollständig serverseitig von
Spring Authorization Server generiert/verwaltet. **Für P3.5 heißt das:** Es gibt aktuell **keine**
Mehr-Provider-Dispatch-Logik im Frontend — ein einziger konstanter Pfad. P3.5 muss diesen Pfad
parametrisieren (`/oauth2/authorization/{registrationId}`, wobei `registrationId` z. B. `google`,
`github`, `dev-provider` sein kann) und dynamisch aus dem Discover-Ergebnis (P3.4) einen Button
pro konfiguriertem Provider rendern.

Bestehende, für P3.5/P3.6 wiederverwendbare Frontend-Bausteine: `frontend/src/routes/setup/+page.svelte`,
`frontend/src/routes/invite/[token]/+page.svelte`, `frontend/src/routes/login/+page.svelte`
(natives `<form>`-POST gegen `formLogin()`), `frontend/src/lib/stores/sessionState.svelte.ts`,
`sessionBootstrap.ts`, `sessionGuard.ts`, `sessionLogout.ts` (Runes-State-Module, siehe
`frontend/src/lib/stores/`).

### 2.4 ADR-013/014/015: Phase 3 wurde architektonisch bereits vorweggenommen

- **ADR-013** (Spring Authorization Server als AS *und* Resource Server im Monolithen,
  Authorization Code + PKCE fürs SPA): Verwirft Keycloak als externen Dienst (Betriebsaufwand,
  QG3/QG4), verwirft reinen Session-Only-Ansatz („kein Pfad zu Social Login später") und einen
  reinen Externer-IdP-Ansatz („zwingt jeden Nutzer zu einem Drittanbieter-Konto, widerspricht dem
  Self-Hosting-/FOSS-Charakter"). Zitat aus der Begründung: SAS „supports both cases — local
  password accounts today and federated social login in the future — through the same
  standardized Authorization Code + PKCE flow, without operating an additional service".
- **ADR-014** (BFF-Pattern, httpOnly/SameSite-Cookie, aktive Token-Revocation beim Logout, Cookie-
  basiertes CSRF): Gilt unverändert für Phase 3 — föderierte Logins erzeugen dieselbe Art
  Backend-gehaltener Session, kein neuer Frontend-Token-Umgang nötig. Zitiert explizit die IETF-BCP
  „OAuth 2.0 for Browser-Based Apps".
- **ADR-015** (dedizierte `account`-Tabelle, 1:1 zu `member`): Kontext-Abschnitt nimmt Phase 3
  **wörtlich** vorweg — *„once social login is added, some accounts may authenticate purely via a
  federated provider without ever setting a local password"* und *„a future account may be
  entirely federated (no password hash at all)"*. Das ist die stärkste vorhandene Design-Vorgabe
  für Abschnitt 3.5 (nullable `password_hash`, keine neue Account-Tabellenstruktur).

### 2.5 Sicherheits-Checkliste (Kapitel 8): aktueller Stand, 14 Einträge

`docs/architecture/chapters/08_concepts.adoc:36-135`, vollständige Tabelle:

| ID | Control | Status |
|---|---|---|
| PW1 | Passwort-Mindestlänge, keine Komplexitätsregeln | Decided |
| PW2 | Keine erzwungene Passwort-Rotation | Decided |
| PW3 | Breached-Password-Check | Deliberately not implemented (Risk R5) |
| PW4 | Maskiertes Passwortfeld mit Reveal-Toggle | Open |
| SES1 | Sichere Cookie-Attribute | Decided (ADR-014) |
| SES2 | CSRF-Schutz für Cookie-Requests | Decided (ADR-014) |
| SES3 | Kein Session-/Token-Zustand in localStorage | Decided (ADR-014) |
| CORS1 | Fixe/validierte CORS-Origin | Largely moot (Nginx same-origin) |
| **RED1** | OAuth `redirect_uri`-Exact-Allow-List | **Decided** — „Enforced natively by Spring Authorization Server's registered-client configuration" — deckt nur die **eigenen** Registered Clients (das SPA) ab, nicht die neuen externen Provider-Registrierungen aus P3.2 (siehe Abschnitt 3.11) |
| **RED2** | Kein automatischer Redirect zu nicht vertrauenswürdigen Hosts (Invite-Links) | **Open** — jetzt zusätzlich relevant für den Rücksprung von einem externen Provider |
| ENUM1 | Keine Konto-Enumeration über Login/Registrierung/Reset | Decided (mit dokumentierten Ausnahmen VERIFY1/RATE1) |
| VERIFY1 | E-Mail-Verifikationstoken Single-Use/zeitlich begrenzt | Decided |
| RESET1 | Reset-Token Single-Use/kurzlebig, invalidiert alle Sessions | Decided |
| ERR1 | Generische Fehlermeldung, keine Stacktraces | Open |
| RATE1 | Rate-Limiting/Anti-Automation | Decided (Bucket4j + DB-Lockout) |
| HASH1 | Argon2id | Decided (ADR-009) |
| **PKCE1** | Authorization Code Flow mit PKCE | **Decided** (ADR-013) — gilt für SPA↔eigener-AS; für AS↔externer-Provider bietet Spring Security bei Confidential Clients standardmäßig **kein** PKCE an (siehe Abschnitt 3.1) |
| AC1 | Objekt-/Feld-Zugriffskontrolle je Haushalt | Open |

**Kein** bestehender Eintrag deckt Vertrauen in externe Identitätsprovider, Account-Linking-
Integrität oder Token-Validierung föderierter Logins ab — Phase 3 braucht mindestens **FED1**
(siehe Abschnitt 3.11).

### 2.6 Technical Risks (Kapitel 11): keine Phase-3-Erwähnung, aber TD1 wird relevant

`docs/architecture/chapters/11_technical_risks.adoc` (88 Zeilen): **TD1** (High) — kein
`package-info.java`/`@ApplicationModule(allowedDependencies=...)` irgendwo im Backend, nur
`ApplicationModules.verify()`-Zyklenerkennung läuft. **TD2** (High) — unbegrenzte Upload-Größe.
**TD3** (Medium) — kein Bildtyp-/Magic-Byte-Check. **TD4** (Low, akzeptiert) — kompilierzeit-
Named-Interface-Abhängigkeiten von `tasks`/`expenses` auf `household` (gewollt). Risiken: R1
(Named-Interface-Bloat), R2 (Eventual-Consistency-Missbrauch), R3 (1:1 Account:Household als
bewusste Scope-Grenze), R4 (Java-Verbosity), R5 (kein Breached-Password-Check, akzeptiert).
**Keiner der bestehenden Einträge erwähnt Social Login** — Phase 3 sollte auf **TD1** verweisen,
da eine neue, größere Federation-Logik ohne Modul-Grenzen-Erzwingung fortsetzt, was TD1 bereits
als Risiko benennt.

### 2.7 `TestcontainersConfiguration`: `@ServiceConnection`-Muster, kein manuelles `@DynamicPropertySource`

`backend/src/test/java/eu/wiegandt/librehousehold/TestcontainersConfiguration.java` (32 Zeilen,
vollständig):

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    LgtmStackContainer grafanaLgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:latest"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

    @Bean
    @ServiceConnection
    MailpitContainer mailpitContainer() {
        return new MailpitContainer();
    }
}
```

Alle drei Container nutzen Spring Boots `@ServiceConnection`-Mechanismus (Spring Boot 3.1+): Für
Postgres/Grafana-LGTM/Mailpit existieren bekannte `ConnectionDetails`-Typen (JDBC, OTLP, Mail), die
Spring Boot automatisch aus dem laufenden Container ableitet und die zugehörigen
`spring.datasource.*`/`spring.mail.*`-Properties **implizit** überschreibt — **kein** manuelles
`@DynamicPropertySource` nötig. Wichtig: `MailpitContainer` (`ch.martinelli.oss:testcontainers-
mailpit:1.3.1`, siehe Abschnitt 2.8) ist bereits ein **Drittanbieter-Community-Modul** (nicht
`org.testcontainers:*`), das trotzdem via `@ServiceConnection` funktioniert, weil es selbst eine
passende `ConnectionDetails`-Factory mitbringt. Das ist das **exakte Präzedenzmuster** für
`wf.garnier:testcontainers-dex`/`spring-boot-testcontainers-dex` (siehe Abschnitt 3.12) — anders
als bei Postgres/Mail gibt es aber **keinen** eingebauten Spring-Boot-„OAuth2-Client"-
`ConnectionDetails`-Typ; ob `spring-boot-testcontainers-dex` (das dedizierte Autokonfigurations-
Begleitmodul, siehe Recherche Abschnitt 3.12) diese Lücke selbst schließt (eigene
`ConnectionDetails`+Factory, die direkt `spring.security.oauth2.client.provider.<id>.issuer-uri`
setzt) oder ob eine einfachere manuelle Lösung (`@DynamicPropertySource`) nötig ist, ist eine der
in Abschnitt 3.12 offenen technischen Fragen.

`TestLibrehouseholdApplication.java` (41 Zeilen) selbst enthält **keine** Container-Definitionen,
sondern nur lokale Property-Defaults als Kommandozeilen-Argumente (`oauth2-client.client-secret`,
CORS-Origin, `authorization-uri`/`redirect-uri` auf den Vite-Dev-Server `http://localhost:5173`,
`frontend.base-url`) und startet `SpringApplication.from(LibrehouseholdApplication::main)
.with(TestcontainersConfiguration.class).run(...)`. Ein dokumentierter Dev-Tools-Bootstrap-Kniff
(Zeile 29–33): Der Default-Argument-Katalog wird gegen bereits übergebene Keys gefiltert, damit ein
DevTools-Neustart (der `main()` mit bereits gesetzten Args erneut aufruft) keine doppelten
`--key=value`-Argumente erzeugt. **Für P3.0 wichtig:** Ein Dex-Issuer, der als weiteres
Kommandozeilen-Default-Argument eingespeist würde, bräuchte denselben Filter — spricht zusätzlich
für die `@ServiceConnection`/Autokonfigurations-Variante, falls verfügbar, statt eines weiteren
Command-Line-Arguments.

### 2.8 `backend/pom.xml`: Kern-Dependency bereits vorhanden, kein neuer OAuth2-Client nötig

`backend/pom.xml` (375 Zeilen): Java **25** (Zeile 38), `spring-boot-starter-parent` **4.1.0**
(Zeile 7), `spring-modulith.version` **2.1.0** (Zeile 39).

- **`spring-boot-starter-oauth2-client` bereits vorhanden** (Zeile 84–86) **und**
  `spring-boot-starter-oauth2-authorization-server` bereits vorhanden (Zeile 88–90), beide ohne
  explizite Version (Boot-managed) — **keine neue Kern-Dependency nötig**, um zusätzliche
  `ClientRegistration`s für Google/GitHub/generisches OIDC zu konfigurieren.
- `bucket4j-core` **8.10.1** (Zeile 45, 92–95) — Präzedenzfall für „Rückfrage gestellt und
  beantwortet" (P2.7), kein Bezug zu Phase 3 außer als Muster.
- `spring-boot-starter-thymeleaf` (Zeile 101–103) bereits vorhanden (P2.9, E-Mail-Templating) —
  falls Phase 3 eine neue Mail braucht (z. B. „Ihr Account wurde mit Google verknüpft"), **keine**
  neue Dependency nötig.
- Testcontainers-Module (Zeile 232–251): `testcontainers-grafana`, `testcontainers-junit-jupiter`,
  `testcontainers-postgresql`, **`ch.martinelli.oss:testcontainers-mailpit:1.3.1`** (Drittanbieter-
  Modul, Präzedenzfall für `wf.garnier:testcontainers-dex`, siehe Abschnitt 2.7/3.12). **Kein**
  Dex-/OIDC-bezogenes Testcontainers-Modul bisher vorhanden.
- `bcprov-jdk18on` **1.85** (Zeile 104–109, runtime scope, BouncyCastle) bereits vorhanden —
  ursprünglich für Apples JWT-Client-Secret-Signierung relevant recherchiert; da Apple aus dieser
  Phase herausgenommen wurde (siehe Scope-Absatz), ohne weitere Konsequenz für P3, aber nützlicher
  Hinweis, falls Apple später als eigener Punkt nachgezogen wird (transitiv vorhandenes
  Nimbus-JOSE-JWT über `spring-security-oauth2-jose` würde dafür wahrscheinlich ausreichen).
- `openapi-generator-maven-plugin` **7.24.0** (Zeile 341), `useSpringBoot4`, `useJackson3`,
  `delegatePattern=true`, `useTags=true` (Zeile 360–365) — Baseline für die P3.4-Schemagenerierung.

### 2.9 README-Struktur: exaktes Stilvorbild für P3.7 bereits vorhanden

`README.adoc` (95 Zeilen, vollständig gelesen). Gliederung: `Features` (10), `Main Goals` (18),
`Development` (27) → `Lint OpenAPI Specification` (29), **`How to Customize Email Templates`**
(38–91, aus dem allerletzten Commit `9f98915`), `Software Architecture Overview` (93, nur ein
externer Link).

Der Abschnitt „How to Customize Email Templates" ist bereits **exakt** im Diátaxis-How-to-Stil
geschrieben, den P3.7 fordert: zielgerichtet, schrittweise, mit einer Unterscheidung
„During Development" (Zeile 63–73) vs. „In Production (Docker Compose)" (Zeile 75–91), konkreten
Env-Var-Namen (`LIBREHOUSEHOLD_NOTIFICATIONS_TEMPLATES_OVERRIDE_DIR`) und einem Verweis auf die
konkreten Default-Dateien im Repo. **Das ist die direkte, unmittelbar wiederverwendbare
Stilvorlage für P3.7** — ein neuer, gleichrangiger `== How to Configure Social Login`-Abschnitt
(mit `===`-Unterabschnitten je Provider oder einer gemeinsamen Tabelle, siehe Abschnitt 3.13)
direkt nach diesem Abschnitt und vor `Software Architecture Overview` platziert, mit derselben
Dev/Docker-Compose-Aufteilung. Es gibt aktuell **keinen** separaten „Getting Started"/
„Configuration"-Abschnitt, an den stattdessen verwiesen werden könnte.

### 2.10 `account`-Tabelle: aktueller vollständiger Stand (nach P2.7), `password_hash` noch `NOT NULL`

`backend/src/main/resources/db/migration/household/V1__create_household_and_member.sql` (einzige
Migrationsdatei — frühere `V2`/`V3`-Aufteilungen wurden zwischenzeitlich wieder in `V1`
zusammengeführt; das im Git-Status sichtbare `AD V3__add_account_lockout.sql` ist ein
vorbestehender, nicht von dieser Recherche verursachter Arbeitszustand und wird hier nicht
angefasst). Aktuelle `account`-Tabelle (Zeile 26–35):

```sql
CREATE TABLE account
(
    member_id                             UUID PRIMARY KEY REFERENCES member (id) ON DELETE CASCADE,
    password_hash                         TEXT NOT NULL,
    email_verified                        BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at                         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    verification_deletion_warning_sent_at TIMESTAMP WITH TIME ZONE NULL,
    locked_until                          TIMESTAMP WITH TIME ZONE NULL,
    failed_login_attempts                 INT NOT NULL DEFAULT 0
);
```

**`password_hash TEXT NOT NULL`** — aktuell zwingend gesetzt. Für einen rein föderierten Account
(nie ein lokales Passwort vergeben) gibt es keinen sinnvollen Wert dafür — siehe Abschnitt 3.5 für
die Migrationsentscheidung. Ebenfalls in derselben Datei: `household`/`member`/`invite` (Zeile
1–24), `account_token` (Zeile 37–44, `member_id`/`token UUID UNIQUE`/`purpose TEXT`/`valid_until`
— direktes Strukturvorbild für die neue `account_identity`-Tabelle, siehe Abschnitt 3.4), sowie das
vollständige Spring-Authorization-Server-JDBC-Schema (`oauth2_registered_client`,
`oauth2_authorization`, `oauth2_authorization_consent`, Zeile 46–113).

### 2.11 `AccountEntity`/`AccountRepository`/`AccountService`: aktueller Stand, keine Linking-Fähigkeit

- `AccountEntity` (`.../household/model/AccountEntity.java`, Record, `Persistable<UUID>`,
  `isNew()` immer `true`): Felder `memberId`, `passwordHash`, `emailVerified`, `registeredAt`,
  `verificationDeletionWarningSentAt`, `failedLoginAttempts`, `lockedUntil` — **sieben**
  Positionsparameter im Konstruktor. Jede Strukturänderung (z. B. `passwordHash` nullable machen,
  siehe 3.5) betrifft **jeden** Aufrufort, der diesen Konstruktor direkt oder indirekt befüllt.
- `AccountRepository`: `updatePasswordHash`, `markEmailVerified`, zwei Derived-Finder für den
  Grace-Period-Job, `updateVerificationDeletionWarningSentAt`,
  `incrementFailedLoginAttempts`/`lockUntil`/`resetFailedLoginAttempts` — durchweg
  `@Modifying @Query`, konsistent mit dem `Persistable`-immer-`isNew()`-Muster.
- `AccountService` (`.../household/service/AccountService.java`, 52 Zeilen, vollständig):
  `createAccount(memberId, rawPassword)` (Zeile 25–28, befüllt alle sieben Felder positional:
  `emailVerified=false, registeredAt=Instant.now(), warningSentAt=null,
  failedLoginAttempts=0, lockedUntil=null`), `resetPassword` (Zeile 30–32), `changePassword`
  (Zeile 34–43, wirft `EmailNotVerifiedException`/`InvalidPasswordException`),
  `isEmailVerified`/`markEmailVerified` (Zeile 45–51). **Es existiert keine Methode**, um einen
  Account ohne Passwort anzulegen oder eine externe Identität mit einem bestehenden Account zu
  verknüpfen — das ist vollständig neue Oberfläche für P3.3.

### 2.12 Principal-Typen: `AccountPrincipal` (intern), `AccountOidcPrincipal` (Business-API), kein Nicht-OIDC-Pfad

- `AccountPrincipal` (`.../household/AccountPrincipal.java`, 62 Zeilen): reiner `UserDetails`-
  Record `(email, passwordHash, emailVerified)`, **nur** für den internen `formLogin()` gegen den
  embedded AS (Chain 2) — Javadoc (Zeile 11–13): „Not used for authorizing business API calls —
  that happens via `AccountOidcPrincipal` once the OIDC login has completed". `isEnabled()` gibt
  `emailVerified` zurück (VERIFY1, Zeile 47–56).
- `AccountOidcPrincipal` (`.../household/AccountOidcPrincipal.java`): Record `(OidcUser oidcUser,
  UUID memberId, UUID householdId, boolean isAdmin)` — der tatsächliche Principal-Typ für **jede**
  Business-API-Autorisierung (`HouseholdAccessGuard`, `SessionApiDelegateImpl`,
  `@PreAuthorize`-Guards durchgehend).
- `AccountOidcUserService` (`.../household/service/AccountOidcUserService.java`) hat eine
  `enrich(OidcUser oidcUser): AccountOidcPrincipal`-Methode (Zeile 35–39), die das Member über den
  **internen** OIDC-`sub`/E-Mail-Claim nachlädt und `AccountOidcPrincipal` baut — das ist der
  Anreicherungspunkt für den **internen** Loop-Back-Roundtrip. Für echte externe Provider
  (Google/GitHub/etc.) braucht P3.3 entweder eine Erweiterung dieses Enrichment-Schritts um einen
  **zweiten**, unterscheidbaren Principal-Typ (externer `sub`+Issuer statt lokalem `sub`) oder
  einen parallelen Pfad — aktuell ist **genau eine** `ClientRegistration` und **ein**
  `OidcUserService`-Bean in `SecurityConfig` verdrahtet, Mehr-Provider-Dispatch ist komplett neue
  Design-Fläche.
- **Kein** `AccountOAuth2Principal`/Nicht-OIDC-Principal-Typ existiert. Relevant für GitHub (reines
  OAuth2, kein `id_token`): `SecurityConfig`s `defaultSecurityFilterChain` verdrahtet aktuell nur
  `.userInfo.oidcUserService(oidcUserService)` (Zeile 272–273), **kein** `.userService(...)` (das
  Nicht-OIDC-Gegenstück) — GitHub-artige Provider brauchen zusätzlich einen
  `OAuth2UserService<OAuth2UserRequest, OAuth2User>` (siehe Abschnitt 3.6).

### 2.13 `HouseholdSetupService`/`MemberManagementService`: Account-Erstellung ist untrennbar an ein Passwort gekoppelt — die zentrale Lücke für P3.6

Beide Stellen, an denen heute ein Account entsteht, sind eng an einen rohen Passwort-String
gekoppelt:

```java
// HouseholdSetupService.setupHousehold (Zeile 69-72)
accountService.createAccount(savedMember.getId(), setup.getLocalRegistration().getPassword());
eventPublisher.publishEvent(new AccountRegistered(savedMember.getId(), setup.getMember().getEmail()));
accountSessionAuthenticator.authenticateAndPersistSession(
        setup.getMember().getEmail(), setup.getLocalRegistration().getPassword());
```

```java
// MemberManagementService.joinHousehold (Zeile ~96-100)
accountService.createAccount(saved.getId(), registration.getLocalRegistration().getPassword());
eventPublisher.publishEvent(new AccountRegistered(saved.getId(), registration.getEmail()));
accountSessionAuthenticator.authenticateAndPersistSession(
        registration.getEmail(), registration.getLocalRegistration().getPassword());
```

`AccountSessionAuthenticator.authenticateAndPersistSession(email, rawPassword)`
(`.../household/service/AccountSessionAuthenticator.java`, 95 Zeilen, vollständig gelesen) lädt
`AccountUserDetailsService.loadUserByUsername(email)`, prüft **das rohe Passwort** gegen den
gespeicherten Hash (`passwordEncoder.matches(...)`, Zeile 78) und baut daraus manuell ein
authentifiziertes `UsernamePasswordAuthenticationToken` samt `FactorGrantedAuthority` (nötig, damit
`JwtGenerator.getAuthenticationTime` beim anschließenden Code-Exchange nicht mit
`IllegalArgumentException` scheitert, siehe Klassen-Javadoc Zeile 24–29). Die Klasse dokumentiert
selbst explizit, **warum** sie nicht über den regulären `AuthenticationManager` läuft (Zeile
31–39): dessen `DefaultPreAuthenticationChecks` würde `isEnabled()`/VERIFY1 bereits für die allererste
Session greifen lassen.

**Konsequenz, zentral für P3.6:** Sowohl `HouseholdSetup`/`MemberRegistration` (OpenAPI-Schemas,
siehe Abschnitt 2.14) als auch dieser komplette Code-Pfad setzen zwingend ein rohes Klartext-
Passwort voraus — sowohl um den Account anzulegen als auch um die erste Session zu etablieren. Ein
Setup/Join **ausschließlich** über Social Login (P3.6) hat **kein** Passwort zur Verfügung. Das ist
keine kleine Erweiterung, sondern verlangt entweder (a) einen neuen Session-Etablierungspfad in
`AccountSessionAuthenticator`, der ein bereits vorhandenes, extern authentifiziertes
`OAuth2AuthenticationToken` akzeptiert statt ein Passwort zu verifizieren, oder (b) einen
grundsätzlich anderen Ablauf, bei dem das SPA **zuerst** den externen Federation-Login durchläuft
(gegen Chain 1, siehe Abschnitt 3.1) und der Setup/Join-Request danach **innerhalb** dieser bereits
(extern) authentifizierten, aber noch nicht mit einem Haushalt verknüpften Session erfolgt. Siehe
Abschnitt 3.3 und 3.10 für die ausführliche Design-Diskussion — das ist die architektonisch
anspruchsvollste Einzelfrage dieses gesamten Plans.

### 2.14 OpenAPI: `localRegistration` ist heute *required*, kein Alternativpfad

`api/openapi.yml`:

- `LocalRegistration` (Zeile 2585–2592): `{ password: Password }`, `required: [password]`.
- `HouseholdSetup` (Zeile 2606–2618): `required: [household, member, localRegistration]` —
  **`localRegistration` ist Pflicht**, kein `oneOf`/optionales Feld für einen Social-Pfad.
- `MemberRegistration` (Zeile 2698–2721): ebenfalls `required: [..., localRegistration]`.
- `CurrentUser` (Zeile 2761–2779): `{ member, household, preferences, emailVerified }` — **kein**
  Feld, das anzeigt, über welche Methode(n) (lokal/Provider X/Provider Y) der Account
  authentifiziert werden kann. Für P3.3 (Anzeige „Verknüpfte Provider" o. Ä., falls gewünscht —
  nicht explizit im Meta-Plan-Wortlaut gefordert, siehe Abschnitt 3.4) wäre das ein weiteres,
  optionales Erweiterungsfeld.
- Bestehende Pfad-/Tag-Konventionen: `/household/setup`, `/invite/{token}/join` sind
  `security: []` (unauthentifiziert). `/members/availability` liefert
  `EmailAvailability{available}`. `Problem`-Schema (Zeile 1895ff.) mit `type`-Diskriminator für
  409-Fehler — Muster für neue Federation-Fehler (siehe P3.3/P3.6, Abschnitt 5).
- **`session`-Modul** (`backend/src/main/java/eu/wiegandt/librehousehold/session/`, eigenständiges
  Package **außerhalb** von `household/`): `SessionApiDelegateImpl` implementiert `GET /me`
  (`getCurrentUser`) ausschließlich über drei Named Interfaces aus `household`
  (`MemberQuery`, `HouseholdQuery`, `PreferencesQuery` aus `usersettings`, `PasswordReset`) —
  **kein** direkter Zugriff auf `household.service`-interne Klassen. Für den Discover-Endpoint
  (P3.4) ist die naheliegende Frage, ob er ebenfalls in `session` liegt oder ein neues,
  eigenständiges Delegate wird (siehe Abschnitt 3.8) — er braucht aber, anders als `GET /me`,
  **keine** Named Interface auf `household`, da er rein aus `@ConfigurationProperties` liest
  (siehe Abschnitt 1).

### 2.15 `RegisteredClientSeeder`: Muster für den eigenen Registered Client, kein Provider-Bezug

`backend/src/main/java/eu/wiegandt/librehousehold/config/RegisteredClientSeeder.java` (79 Zeilen,
vollständig): idempotenter `ApplicationRunner`, seedet **den einen** `RegisteredClient`
(`CLIENT_ID = "spa-backend-client"`) für das SPA — Confidential Client
(`CLIENT_SECRET_BASIC`, da Spring Authorization Server öffentlichen Clients keine Refresh-Tokens
ausstellt, siehe Klassenkommentar), `requireProofKey(true)` (PKCE), 10-Minuten-Access-Token,
30-Tage-Refresh-Token, `reuseRefreshTokens(false)`. Betrifft nur den *eigenen* Client (das SPA
gegenüber dem eigenen AS) — hat keinen direkten Bezug zu den *externen* Provider-Registrierungen,
die P3.2 hinzufügt (die leben in einer separaten `ClientRegistrationRepository`, nicht in der
`RegisteredClientRepository`-JDBC-Tabelle).

---

## 3. Offene Design-Entscheidungen

**Status nach der Rückfrage-Runde:** siehe Zusammenfassung im Kopf dieses Dokuments. Jeder
Unterabschnitt trägt seinen finalen Status in der Überschrift.

### 3.1 Federation-Architektur: Spring-AS-Federation vs. Multi-Client-Registrierung — ✅ übernommen (durch ADR-014 praktisch vorgegeben, keine gesonderte Rückfrage nötig)

**Empfehlung: Spring-Authorization-Server-Federation** (der eigene AS wird selbst OAuth2-Client
externer Provider, auf seiner eigenen Login-Seite/Chain 1), **wie im offiziellen Spring-
Authorization-Server-Guide** [„How-to: Authenticate using Social Login"](https://docs.spring.io/spring-authorization-server/reference/guides/how-to-social-login.html)
beschrieben. Konkrete Ziel-Architektur (Ergänzung zu Abschnitt 2.1):

```java
@Bean
@Order(1)
public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) {
    // unverändert wie heute, ABER:
    http.exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
        new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/google"), // oder dynamisch, siehe P3.5
        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    // ...
}

@Bean // Chain 2, unverändert an Position/Order
public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) {
    http.oauth2Login(Customizer.withDefaults()); // NEU: zusätzlich zu formLogin(), nicht statt
    // ...
}
```

Ablauf: Ein nicht authentifizierter `/oauth2/authorize`-Request (Chain 1) leitet auf
`/oauth2/authorization/{registrationId}` um. Chain 2 übernimmt den kompletten externen
Redirect-Flow (Google/GitHub/OIDC), fängt den Callback unter
`/login/oauth2/code/{registrationId}` ab, legt ein `OAuth2AuthenticationToken` (Principal =
`OidcUser`/`OAuth2User`) in die Session. Der Browser wird zurück auf `/oauth2/authorize`
geschickt — Chain 1 sieht jetzt eine authentifizierte Session und fährt mit dem normalen
Authorization-Code-Flow für das SPA fort. Das bestehende PKCE-für-SPA-Setup
(`RegisteredClientSeeder`, Abschnitt 2.15) bleibt **komplett unverändert** — es kommt nur eine
zusätzliche Authentisierungsquelle für den Resource-Owner-Login-Schritt hinzu, analog zum
bestehenden `formLogin()`.

Für die Zuordnung des externen `OidcUser`/`OAuth2User` zu einem lokalen Account zeigt der Guide das
`AuthenticationSuccessHandler`-Pattern (`FederatedIdentityAuthenticationSuccessHandler`, delegiert
an `SavedRequestAwareAuthenticationSuccessHandler`, ruft dabei einen
`Consumer<OAuth2User>`/`Consumer<OidcUser>`-Hook auf) — dieser Hook ist der Ort für
Account-Lookup/-Linking/JIT-Provisioning (siehe Abschnitt 3.3/3.4). Ergänzend zeigt der Guide einen
`OAuth2TokenCustomizer<JwtEncodingContext>` (`FederatedIdentityIdTokenCustomizer`), um Claims aus
dem externen Provider (z. B. `given_name`, `picture`) in das vom eigenen AS ausgestellte
`id_token` zu übernehmen, falls das SPA sie sehen soll.

**Begründung gegenüber der Alternative:** Bei einem BFF-Cookie-Pattern (ADR-014, Tokens verlassen
das Backend nie) ist Federation die einzig konsistente Wahl:

| | Federation (empfohlen) | SPA direkt gegen externen IdP (Alternative) |
|---|---|---|
| Tokens im Browser | Nie — nur das eigene Session-Cookie erreicht den Browser | Google-/GitHub-Tokens erreichen den Browser → verletzt ADR-014 |
| Resource-Server-Vertrauensraum | Ein Issuer (der eigene AS) für alle Resource Server | Jeder Resource Server müsste mehrere Issuer/JWKS validieren; GitHub hat gar kein JWKS |
| Account-Status (Lockout/VERIFY1) | Zentral im eigenen AS erzwingbar, für **jeden** Login-Weg | Umgeht die zentrale Lockout-/Verifizierungslogik teilweise |
| Logout/Session-Modell | Ein Session-Cookie, ein Refresh-Token, ein Logout | Mehrere parallele Provider-Sessions, kein einheitliches Logout |

**Alternative (verworfen): Multi-Client-Registrierung / SPA authentifiziert sich direkt bei
externen Providern**, das Backend würde nur noch als reiner Resource Server externe Access-Tokens
validieren. Verworfen aus genau den in der Tabelle genannten Gründen — bricht ADR-014 fundamental
und würde eine komplette Session-Architektur-Revision erzwingen, nicht nur eine Erweiterung.

**Wichtige Einschränkung, die sich aus der Recherche ergibt (PKCE1):** Spring Security bietet PKCE
für den `oauth2Login()`-Flow standardmäßig nur für **Public Clients** an (kein Client-Secret). Der
eigene AS tritt gegenüber externen Providern typischerweise als **Confidential Client** auf (er hat
ein Client-Secret vom Provider) — PKCE ist dort nicht zwingend nötig (das Client-Secret selbst
erfüllt bereits einen ähnlichen Zweck), sollte aber, wo vom Provider unterstützt (z. B. Google),
trotzdem aktiviert werden, sofern Spring Security das für Confidential Clients unterstützt — zu
verifizieren bei der Umsetzung von P3.1/P3.2 anhand der dann aktuellen Spring-Security-Doku.

### 3.2 Provider-Konfigurationsschema — ✅ entschieden (vom Nutzer bestätigt: eigene Schicht)

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.** Ein diskriminiertes Schema unter einem neuen `librehousehold.security.social-
login.*`-Namespace (konsistent mit dem bestehenden `librehousehold.security.*`-Muster, Abschnitt
2.2), das zwischen drei Provider-*Arten* unterscheidet, weil sie strukturell unterschiedliche
Properties brauchen (siehe Recherche-Ergebnis Frage 2):

```yaml
librehousehold:
  security:
    social-login:
      enabled: false # globaler Schalter, siehe P3.4/lokal-vs-social-vs-both aus dem Meta-Plan-Wortlaut
      providers:
        google: # OIDC-Provider, spring.security.oauth2.client.provider.google existiert bereits
                # als Spring-Security-CommonOAuth2Provider-Default — hier wird nur registration befüllt
          type: oidc
          client-id: ${GOOGLE_CLIENT_ID:}
          client-secret: ${GOOGLE_CLIENT_SECRET:} # kein Default -> fail-fast, Muster wie spring.mail.host
          display-name: "Google"
        github: # Plain-OAuth2-Provider, kein Discovery, siehe CommonOAuth2Provider.GITHUB
          type: oauth2
          client-id: ${GITHUB_CLIENT_ID:}
          client-secret: ${GITHUB_CLIENT_SECRET:}
          display-name: "GitHub"
        keycloak-self-hosted: # generisches OIDC, Selfhoster-konfiguriert
          type: oidc
          issuer-uri: ${KEYCLOAK_ISSUER_URI:}
          client-id: ${KEYCLOAK_CLIENT_ID:}
          client-secret: ${KEYCLOAK_CLIENT_SECRET:}
          display-name: "Keycloak"
```

Eine `@ConfigurationProperties`-Klasse (`SocialLoginProperties`, `config`-Package, analog
bestehenden `@Value`-Injektionen) liest diese Struktur ein und baut daraus zur Laufzeit die
`ClientRegistration`s (statt eines rein statischen `InMemoryClientRegistrationRepository`, wie es
heute für den einen SPA-Client existiert, siehe Abschnitt 2.1) — für `type: oidc` per
`ClientRegistrations.fromIssuerLocation(issuerUri)` (funktioniert für Google, da Google selbst
Discovery unterstützt, ODER per `CommonOAuth2Provider.GOOGLE.getBuilder(...)`, falls kein
`issuer-uri`, sondern nur `client-id`/`-secret` gesetzt ist — **Empfehlung: `CommonOAuth2Provider`
für Google/GitHub direkt nutzen, wo vorhanden**, da es Boilerplate spart, siehe Recherche Frage 2),
für `type: oauth2` per manuellem `ClientRegistration.withRegistrationId(...)` mit
`CommonOAuth2Provider`-Defaults, falls der Provider bekannt ist (GitHub), sonst komplett explizit
(`authorization-uri`/`token-uri`/`user-info-uri`/`user-name-attribute`).

**Startvalidierung** (explizit im Meta-Plan-Wortlaut gefordert, P3.2): Eine `@PostConstruct`- oder
`ApplicationRunner`-Prüfung (analog `RegisteredClientSeeder`, Abschnitt 2.15), die beim Start
fehlschlägt (Fail-Fast, klare Fehlermeldung), falls `social-login.enabled: true`, aber ein
konfigurierter Provider unvollständige Properties hat (z. B. `type: oidc` ohne `issuer-uri` **und**
ohne einen bekannten `CommonOAuth2Provider`-Namen) — konsistent mit dem bestehenden Fail-Fast-
Muster für `oauth2-client.client-secret` (Abschnitt 2.2).

**Alternative 1 (verworfen): Ausschließlich Spring Boots eigenes natives
`spring.security.oauth2.client.*`-Property-Schema direkt nutzen, ohne eigene
`librehousehold.security.social-login.*`-Zwischenschicht.** Vorteil: kein eigener
Parsing-/Validierungscode, exakt das Boot-Standard-Verhalten (`OAuth2ClientPropertiesRegistrationAdapter`
übernimmt automatisch die `ClientRegistrationRepository`-Bean-Erzeugung, sobald
`spring.security.oauth2.client.registration.*` gesetzt ist — **kein eigener Code nötig**). Nachteil:
Es gäbe dann **keine** zentrale, für P3.4 (Discover-Endpoint) leicht abfragbare Liste „welche
Provider sind aktiv, mit welchem Anzeigenamen" — das müsste dann aus der von Spring bereits
gebauten `ClientRegistrationRepository` zur Laufzeit zurückgelesen werden (funktioniert, ist aber
unüblich, da dieses Repository nicht primär zum Iterieren gedacht ist, sondern zum Nachschlagen per
`registrationId`) und `enabled: false`/„local only" ließe sich nicht so einfach als ein einzelner
Schalter modellieren. **Empfehlung bleibt die eigene, dünne Schicht** — sie kostet wenig Code
(reine Mapping-Klasse) und macht P3.4 trivial.

**Alternative 2 (verworfen): Microsofts „Spring Boot Starter for Microsoft Entra" für den Microsoft-
Fall.** Kapselt das im Recherche-Ergebnis (Frage 4) genannte Tenant-`issuer`-Platzhalter-Problem
bequem, ist aber eine zusätzliche, Microsoft-spezifische Dependency, die laut AGENTS.md
„Dependency Management" eine Rückfrage bräuchte — **Empfehlung: nicht hinzufügen**, stattdessen
Single-Tenant-Konfiguration dokumentieren (siehe P3.7/Abschnitt 3.13) und, falls Multi-Tenant
(`common`) tatsächlich gebraucht wird, einen eigenen, kleinen toleranten Issuer-Validator schreiben
(wenige Zeilen, kein Bibliotheks-Bedarf).

### 3.3 Kontextvermittlung durch den Federation-Redirect (Setup vs. Invite vs. Login) — ✅ entschieden (vom Nutzer bestätigt: Session-Endpoint)

Das ist die aus der Ist-Zustand-Recherche (Abschnitt 2.13) hervorgegangene, im Meta-Plan-Wortlaut
selbst noch nicht aufgelöste Kernfrage: Ein Federation-Login kann in **drei fachlich
unterschiedlichen Kontexten** gestartet werden, die alle denselben technischen Redirect-Flow
(Abschnitt 3.1) durchlaufen, aber am Ende fachlich völlig unterschiedliche Aktionen brauchen:

1. **Login** (ein bereits existierendes Mitglied meldet sich erneut an): externe E-Mail muss auf
   ein bestehendes `member`/`account` gemappt werden (siehe 3.4).
2. **Setup** (ein neuer Haushalt wird gerade angelegt, P3.6): es gibt noch **kein** Mitglied — der
   externe Login **ist** die Account-Erstellung für den ersten Admin.
3. **Invite-Join** (ein Beitretender löst ein Invite-Token ein, P3.6): es gibt noch kein Mitglied
   in diesem Haushalt, aber der Kontext (`householdId` aus dem Invite-Token) ist bereits bekannt,
   bevor der externe Login überhaupt beginnt.

**Empfehlung: Kontext über einen zusätzlichen Session-Zustand vor dem Redirect transportieren,
nicht über die `registrationId` selbst.** Konkret: Bevor das SPA zu
`/oauth2/authorization/{registrationId}` navigiert, ruft es einen neuen, unauthentifizierten
Endpoint auf (z. B. `POST /auth/social-context`, Body `{ context: "SETUP" | "INVITE", inviteToken?
}`), der diesen Kontext serverseitig in der (bereits vom Servlet-Container verwalteten,
noch-nicht-authentifizierten) `HttpSession` ablegt — dieselbe Session, die der anschließende
`oauth2Login()`-Redirect ohnehin nutzt (Spring Security speichert den `OAuth2AuthorizationRequest`
selbst bereits sessionbasiert zwischen Redirect und Callback). Der
`FederatedIdentityAuthenticationSuccessHandler`-Hook (Abschnitt 3.1) liest diesen Kontext beim
Callback aus derselben Session und entscheidet: Login (E-Mail-Match erforderlich, 3.4), Setup
(neuer Haushalt+Mitglied+Account werden aus dem externen `OidcUser` gebaut, ohne dass das SPA
vorher `POST /household/setup` mit Body aufrufen musste) oder Invite-Join (Mitglied wird im
Ziel-Haushalt aus dem Invite-Token angelegt). Fehlt der Kontext (z. B. direkter Login-Button ohne
vorherigen Setup/Invite-Schritt), ist „Login" der Default.

**Alternative 1 (verworfen): Pro Kontext eine eigene `registrationId`** (z. B. `google-login`,
`google-setup`, `google-invite` als drei separate `ClientRegistration`s desselben Providers).
Verworfen: verdreifacht die Anzahl der Registrierungen pro Provider (und pro zusätzlichem Provider
erneut), macht P3.2/P3.4 unnötig komplex (der Discover-Endpoint müsste pro Provider drei Varianten
zurückgeben), und der externe Provider selbst kennt ohnehin nur eine Redirect-URI pro
Registrierung — bei Google/GitHub müssten für jede der drei Registrierungen separate Redirect-URIs
in der externen Provider-Konsole hinterlegt werden, was die Selfhoster-Einrichtung (P3.7)
unnötig verkompliziert.

**Alternative 2 (verworfen): Kontext als Query-Parameter in der `/oauth2/authorization/{id}`-URL
übergeben** (z. B. `?context=setup&inviteToken=...`). Verworfen: Spring Securitys
`OAuth2AuthorizationRequestRedirectFilter` unterstützt zwar `authorizationRequestUri`-
Anpassungen über einen `OAuth2AuthorizationRequestResolver`, aber der externe Provider selbst
reflektiert Query-Parameter nicht zuverlässig zurück (nur der `state`-Parameter kommt garantiert
zurück, und der wird von Spring Security bereits intern für CSRF-Schutz verwendet — ihn zusätzlich
für fachlichen Kontext zu missbrauchen wäre fragil und potenziell ein Sicherheitsrisiko, falls der
State dadurch vorhersagbar/manipulierbar würde). Die eigene, separate Session-Ablage (Empfehlung)
ist robuster und nutzt ausschließlich bereits vom Server kontrolliertes serverseitiges Session-
Storage, kein Client-kontrolliertes Feld.

**Auswirkung auf RED2** (siehe Abschnitt 2.5): Der Invite-Token-Kontext muss beim Auflösen genauso
validiert werden wie der bestehende `resolveInvite`-Pfad (Ablaufdatum-Prüfung,
`InvalidInviteException`) — keine neue Redirect-Gefahr, aber eine zusätzliche Stelle, an der die
bestehende Invite-Validierung dupliziert oder wiederverwendet werden muss (Empfehlung:
wiederverwenden, `MemberManagementService.resolveInvite` bleibt die einzige Quelle der Wahrheit für
Invite-Gültigkeit).

### 3.4 Account-Linking-Datenmodell und Linking-Regel — ✅ übernommen (im Meta-Plan-Wortlaut bereits so vorgegeben, keine gesonderte Rückfrage nötig)

**Empfehlung:** Neue Tabelle `account_identity` (Flyway-Migration, analog `account_token`, siehe
Abschnitt 2.10):

```sql
CREATE TABLE account_identity
(
    id                BIGSERIAL PRIMARY KEY,
    member_id         UUID NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    provider_id       TEXT NOT NULL, -- entspricht der ClientRegistration.registrationId, z.B. 'google', 'github'
    external_subject  TEXT NOT NULL, -- OIDC 'sub' bzw. GitHub 'id' (userNameAttributeName)
    linked_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (provider_id, external_subject)
);
```

Ein `member` kann **mehrere** verknüpfte Provider haben (z. B. sowohl Google als auch GitHub auf
denselben Account) — das UNIQUE-Constraint verhindert nur, dass **dieselbe** externe Identität
(Provider+Subject-Kombination) an zwei verschiedene lokale Accounts gebunden wird. **Linking-
Regel:** Beim ersten erfolgreichen externen Login mit einer bestimmten Provider+Subject-Kombination
wird geprüft, ob bereits eine `member_id` verknüpft ist (dann: direkt einloggen). Ist das nicht der
Fall, wird die vom Provider gelieferte **verifizierte** E-Mail-Adresse (bei OIDC-Providern über den
Claim `email_verified: true`, siehe unten) gegen ein bestehendes `member.email` gematcht — bei
Treffer wird die neue `account_identity`-Zeile **automatisch** mit diesem bestehenden Account
verknüpft (kein expliziter „Verknüpfen"-Klick nötig, da die verifizierte E-Mail bereits ein
ausreichender Vertrauensbeweis ist, analog zum bestehenden ENUM1-Ansatz „E-Mail ist der
Account-Identifier", ADR-012). Gibt es **keinen** Treffer, hängt das weitere Vorgehen vom Kontext
ab (Login: Fehler „kein Account gefunden"; Setup/Invite: neuer Account wird angelegt, siehe 3.3).

**Wichtiger Sicherheitsvorbehalt:** Das automatische Linking per E-Mail darf **nur** greifen, wenn
der Provider die E-Mail als verifiziert kennzeichnet (`email_verified` OIDC-Standardclaim; bei
GitHub gibt es kein äquivalentes Claim im Basis-`/user`-Response — dort müsste zusätzlich
`/user/emails` abgefragt werden, das pro Adresse ein `verified: boolean`-Feld liefert, siehe
Recherche Frage 2). Ein Provider, der eine **unverifizierte** E-Mail liefert, darf **kein**
automatisches Linking auslösen (sonst könnte ein Angreifer mit einer beliebigen, nicht bestätigten
E-Mail bei einem laxen Drittanbieter einen fremden Account übernehmen) — stattdessen müsste dieser
Fall wie „kein Treffer" behandelt werden.

**Alternative 1 (verworfen): Provider-Subject direkt als zusätzliche Spalte auf `account` statt
einer eigenen Tabelle** (z. B. `account.google_sub`, `account.github_id` als je eigene, nullable
Spalten). Verworfen: skaliert nicht mit der Anzahl konfigurierbarer Provider (jeder neue Provider
bräuchte eine neue Migration/Spalte), erlaubt außerdem strukturell nur **einen** Provider pro Typ
verknüpft — falls später ein Nutzer sowohl privates als auch geschäftliches Google-Konto verknüpfen
wollte (unwahrscheinlich, aber die generische `account_identity`-Tabelle schließt es nicht künstlich
aus), wäre das mit Spalten unmöglich.

**Alternative 2 (verworfen): Kein automatisches Linking per E-Mail — Nutzer muss Verknüpfung
explizit im eingeloggten Zustand bestätigen** (klassisches „Settings → Verbundene Konten →
Google verknüpfen"-UI). Sicherer gegen Fehlverknüpfungen, aber deutlich mehr UI-Aufwand (neuer
Settings-Bereich) und widerspricht dem im Meta-Plan-Wortlaut für P3.3 bereits vorgegebenen
„Linking über verifizierte E-Mail" (Zeile 182–184 im Meta-Plan) — der Meta-Plan-Auftrag selbst legt
also bereits automatisches E-Mail-Linking nahe; diese Alternative wird trotzdem dokumentiert, falls
der Nutzer bei der Bestätigung dieser Entscheidung das explizite Modell doch bevorzugt.

### 3.5 `password_hash` nullable machen — ✅ übernommen (wörtlich in ADR-015 antizipiert, keine gesonderte Rückfrage nötig)

**Empfehlung:** Migration `ALTER TABLE account ALTER COLUMN password_hash DROP NOT NULL;` (neue
Flyway-Datei, z. B. `V2__make_password_hash_nullable_for_federated_accounts.sql`), `AccountEntity`
(Abschnitt 2.11) bekommt `String passwordHash` als `@Nullable`, `AccountService` eine neue Methode
`createFederatedAccount(memberId)` (kein Passwort-Parameter, setzt `passwordHash = null`,
`emailVerified` sofort `true` — die externe, bereits verifizierte E-Mail des Providers ersetzt die
eigene Verifikationsmail, siehe unten). `AccountUserDetailsService.loadUserByUsername` muss dann
tolerieren, dass `passwordHash` `null` ist — ein `formLogin()`-Versuch für einen rein föderierten
Account (Nutzer versucht fälschlich, sein „Passwort" einzugeben) darf **nicht** mit einer
`NullPointerException` scheitern, sondern soll wie ein regulärer `BadCredentialsException`-Fall
behandelt werden (kein ENUM1-Verstoß: ein rein föderierter Account soll nicht erkennbar
unterschiedlich reagieren als „falsches Passwort").

**`emailVerified` bei federated-only Accounts sofort `true`:** Da der externe Provider die E-Mail
bereits selbst verifiziert (Voraussetzung fürs Linking, siehe 3.4), ist ein zusätzlicher, eigener
Verifikations-Mail-Versand (P2.2-Mechanismus) für diesen Fall unnötig und würde nur Verwirrung
stiften („warum verifiziere ich eine E-Mail, mit der ich mich gerade schon über Google
angemeldet habe").

**Alternative (verworfen): Sentinel-Wert statt `NULL`** (z. B. ein fester String wie
`"$federated$"` als `password_hash`, um `NOT NULL` beizubehalten). Verworfen: Ein Sentinel-Wert ist
fehleranfällig (ein Bug könnte ihn versehentlich als gültigen Hash an `passwordEncoder.matches(...)`
übergeben, was zwar nicht zu einem erfolgreichen Login führen würde — Argon2id würde ihn als
Nicht-Hash zurückweisen — aber unnötiges Risiko für wenig Nutzen ist) und widerspricht dem
Prinzip „NULL heißt, es gibt keinen Wert", das für nullable Fremdschlüssel/Optionalfelder im
gesamten Projekt bereits (z. B. `AccountEntity.verificationDeletionWarningSentAt`) üblich ist —
ADR-015 selbst spricht wörtlich von „no password hash **at all**", nicht von einem Platzhalter.

### 3.6 Nicht-OIDC-Provider-Unterstützung (GitHub) — ✅ übernommen (im Meta-Plan-Wortlaut und im P3.7-Auftrag explizit gefordert)

**Empfehlung:** Zusätzlich zum bestehenden `oidcUserService(...)` (Abschnitt 2.1/2.12) einen
zweiten `OAuth2UserService<OAuth2UserRequest, OAuth2User>` in `SecurityConfig`s
`.oauth2Login(...)`-Konfiguration verdrahten (`.userInfoEndpoint(userInfo ->
userInfo.oidcUserService(oidcUserService).userService(oauth2UserService))`) — Spring Security
wählt zur Laufzeit automatisch zwischen beiden, je nachdem, ob die jeweilige `ClientRegistration`
den `openid`-Scope hat (OIDC-Provider) oder nicht (GitHub-artige, plain-OAuth2-Provider). Der neue
`oauth2UserService` delegiert an denselben Account-Lookup/-Linking-Mechanismus wie
`AccountOidcUserService` (Abschnitt 2.12/3.4), muss aber `OAuth2User` statt `OidcUser` als
Eingabetyp akzeptieren (kein `getIdToken()`, keine standardisierten `email`/`email_verified`-
Claims — bei GitHub müssen `email`/Verifikationsstatus über einen zusätzlichen REST-Call auf
`https://api.github.com/user/emails` nachgeladen werden, siehe Recherche Frage 2, mit
`user:email`-Scope zusätzlich zum Default-`read:user`).

**Alternative (verworfen): GitHub vorerst nicht unterstützen, nur echte OIDC-Provider.** Würde den
Implementierungsaufwand für P3.2/P3.3 spürbar reduzieren (kein zweiter `OAuth2UserService`-Pfad,
kein GitHub-spezifischer E-Mail-Nachlade-Call), widerspricht aber dem expliziten Meta-Plan-Wortlaut
(„Provider auf der Login-Seite des Auth-Servers... z. B. Google, GitHub, generisches OIDC", Zeile
178/187) und dem P3.7-Auftrag, der GitHub explizit als einen der sechs zu dokumentierenden Provider
nennt. Nur als Rückfalloption zu verstehen, falls der zusätzliche Aufwand beim Abwägen der
Entscheidung als unverhältnismäßig bewertet wird.

### 3.7 Apple-Client-Secret: Generierung & Rotation — ✅ aufgelöst: Apple aus Scope genommen

**Entscheidung (vom Nutzer):** „Lass das erstmal weg. Apple muss nicht unbedingt sein." — Apple
wird in dieser Phase **nicht** implementiert und **nicht** in P3.7 (README) dokumentiert (siehe
Scope-Absatz am Dokumentanfang). Die ursprünglich hier zur Wahl gestellte Frage („selbst
schreiben" vs. Community-Bibliothek `patrickbussmann/oauth2-apple`) stellt sich damit nicht mehr.

Die Recherche zu Apples Besonderheiten bleibt unten in „Offene Nacharbeit außerhalb dieses Plans"
festgehalten, falls Apple später als eigener Punkt nachgezogen wird — insbesondere die harte
6-Monats-Rotationsgrenze für das JWT-Client-Secret und die Tatsache, dass Apple Name/E-Mail nur
beim allerersten Autorisierungsversuch übermittelt, sind nicht offensichtliche Fallstricke, die ein
künftiger Anlauf nicht neu recherchieren sollte.

### 3.8 Gestaltung des Discover-Endpoints (P3.4) — ✅ Empfehlung übernommen (geringe Tragweite, keine gesonderte Rückfrage gestellt)

**Empfehlung:** `GET /auth/discover` (neuer, unauthentifizierter Pfad, `security: []`), Response-
Schema:

```yaml
AuthDiscovery:
  type: object
  required: [localEnabled, socialProviders]
  properties:
    localEnabled:
      type: boolean
      example: true
    socialProviders:
      type: array
      items:
        type: object
        required: [id, displayName]
        properties:
          id:
            type: string
            description: Entspricht der registrationId, wird als Pfadsegment in /oauth2/authorization/{id} verwendet
            example: "google"
          displayName:
            type: string
            example: "Google"
```

Implementierung als neues, eigenständiges Delegate (`AuthDiscoveryApiDelegateImpl`, eigenes
`auth`-Package auf Root-Ebene, **nicht** `session` — es hat keinen Bezug zu einer Session/einem
Principal, rein statische, prozessweite Konfiguration, siehe Abschnitt 1/2.14), liest direkt aus
der `SocialLoginProperties`-Bean (Abschnitt 3.2) und dem bestehenden `librehousehold.security.*`-
Schalter für lokale Registrierung (neu: `librehousehold.security.local-login.enabled`, Default
`true`, aus dem Meta-Plan-Wortlaut „`local`/`social`/`both`"-Schalter, Zeile 180). Kein Caching
nötig (reine In-Memory-Konfigurationsdaten, kein DB-Zugriff) — kann bei jedem Request neu aus der
`@ConfigurationProperties`-Bean gebaut werden, das ist kein Performance-Thema.

**Sicherheitsaspekt:** Der Endpoint gibt **keine** Client-Secrets/internen IDs preis, nur
öffentliche `registrationId`+Anzeigename — unkritisch, auch unauthentifiziert abrufbar (das SPA
braucht diese Information bereits, bevor überhaupt ein Login stattgefunden hat, um die Login-Seite
zu rendern).

**Alternative (verworfen): Der Discover-Endpoint liegt unter dem bestehenden `session`-Tag/-
Package.** Verworfen, da `session` laut eigenem Klassenkommentar (Abschnitt 2.14) explizit für
Session-nahe, aber prinzipiell authentifizierungsbezogene Endpoints gedacht ist (`GET /me`,
Passwort-Reset) — der Discover-Endpoint hat keinerlei Bezug zu einer (potenziellen) Session,
sondern ist reine, öffentliche App-Konfiguration; ein eigenes `auth`-Package hält diese
Unterscheidung sauber, analog zur bereits bestehenden Asymmetrie zwischen OpenAPI-Tag und
Backend-Package (`checkEmailAvailability` liegt unter Tag `members`, aber im selben
`MembersApiDelegateImpl` wie `changePassword`, siehe Vorgänger-Plan).

### 3.9 Frontend: dynamische Auth-UI (P3.5) — ✅ Empfehlung übernommen (geringe Tragweite, keine gesonderte Rückfrage gestellt)

**Empfehlung:** `frontend/src/lib/api/authDiscovery.ts` (neuer, kleiner API-Wrapper analog
bestehenden Store-Mustern) lädt `AuthDiscovery` einmal (z. B. via eine neue Svelte-5-Rune,
`authDiscoveryState.svelte.ts`, analog `sessionState.svelte.ts`) und cached das Ergebnis für die
Dauer der SPA-Session (kein Bezug zu Auth-Session-Zustand, reine App-Konfiguration — unproblematisch
im Speicher zu halten, kein localStorage nötig). `frontend/src/routes/login/+page.svelte`,
`frontend/src/routes/setup/+page.svelte` und `frontend/src/routes/invite/[token]/+page.svelte`
rendern je einen Button pro Eintrag in `socialProviders` (Klick ruft
`redirectToOAuth2Login(providerId)` auf, erweiterte Signatur von `oauth2Login.ts`, Abschnitt 2.3:
`window.location.href = '/oauth2/authorization/' + providerId`), plus vorher `POST
/auth/social-context` mit dem passenden Kontext (Setup/Invite, siehe 3.3) — sowie das bestehende
lokale Formular, **ausgeblendet**, falls `localEnabled === false`. Fehler beim Rücksprung (z. B.
Provider lehnt ab, oder Linking schlägt fehl, siehe 3.4) werden über einen neuen Query-Parameter
auf der Rücksprung-Landing-Page ausgelesen und als Toast angezeigt (`toastStore`, AGENTS.md „Error
Handling") — kein natives `alert()`.

**Alternative (verworfen): Eine komplett separate `/login/social`-Route statt Buttons direkt auf
den bestehenden Login-/Setup-/Invite-Seiten.** Verworfen: würde einen zusätzlichen Klick/
Seitenwechsel erzwingen, bevor der Nutzer overhaupt sieht, welche Optionen existieren — die
Discover-Antwort ist bereits beim ersten Rendern der bestehenden Seiten bekannt, es gibt keinen
Grund, Provider-Buttons hinter einer weiteren Navigation zu verstecken.

### 3.10 Setup/Invite ausschließlich via Social (P3.6): Schema- und Session-Konsequenzen — ✅ übernommen (im Meta-Plan-Wortlaut wörtlich „rein über Social" gefordert)

Baut auf 3.3/3.4/3.5 auf, hier die konkreten Schema-/Code-Konsequenzen:

**Empfehlung:** `HouseholdSetup.localRegistration`/`MemberRegistration.localRegistration` werden
von `required` zu **optional** (OpenAPI-Änderung, Abschnitt 2.14). Serverseitig gilt: Ist
`localRegistration` vorhanden → bestehender Passwort-Pfad (unverändert). Ist es **nicht**
vorhanden → der Request muss innerhalb einer bereits extern authentifizierten (aber noch nicht
verknüpften) Session ankommen (der Kontext-Mechanismus aus 3.3 hat den Federation-Login bereits
vor diesem Request durchlaufen) — `HouseholdSetupService`/`MemberManagementService` lesen in
diesem Fall den externen `OidcUser`/`OAuth2User` aus dem `SecurityContextHolder` (statt Name/
E-Mail aus dem Request-Body zu übernehmen — **Konsequenz:** `Household.member.name`/`.email` in
`HouseholdSetup`/`MemberRegistration` würden dann aus der externen Identität vorbefüllt/überschrieben,
nicht vom Nutzer frei eingebbar, um zu verhindern, dass ein Nutzer eine andere E-Mail angibt als
die, mit der er sich gerade extern authentifiziert hat — sonst würde das Linking-Modell aus 3.4
unterlaufen). `AccountService.createFederatedAccount(memberId)` (aus 3.5) statt
`createAccount(memberId, password)`; **kein** Aufruf von
`accountSessionAuthenticator.authenticateAndPersistSession(email, password)`, da die AS-Session für
diesen Principal bereits durch den vorherigen Federation-Login besteht — stattdessen muss die
**bereits bestehende** Session lediglich um `memberId`/`householdId`/`isAdmin` anreichert werden
(vergleichbar mit dem, was `AccountOidcUserService.enrich(...)` heute für den internen Loop-Back
tut, siehe Abschnitt 2.12) — kein neuer `AccountSessionAuthenticator`-Aufruf nötig, sondern eine
Neubewertung/Aktualisierung des bereits laufenden `SecurityContext`.

**Alternative (verworfen): Setup/Invite bleibt für Social-Only-Nutzer zweistufig** — zuerst
Setup/Join mit einem temporären, vom Nutzer selbst gewählten Passwort (wie heute), danach optional
„Mit Google verknüpfen" im eingeloggten Zustand. Verworfen: widerspricht dem expliziten
Meta-Plan-Wortlaut für P3.6 („Erster Admin bzw. Invitee kann sich **rein über Social**
registrieren", Zeile 190/199) — ein Passwort wäre dann nicht „rein" sozial, sondern ein
Umgehungs-Workaround.

### 3.11 Security-Controls-Tabelle: neuer Eintrag `FED1` (+ `RED1`/`RED2`-Anpassung) — ✅ reine Dokumentationskonsequenz, keine gesonderte Rückfrage nötig

**Empfehlung:** Neue Zeile `FED1` (direkt nach `PKCE1`, vor `AC1`, siehe Tabelle Abschnitt 2.5):
Control „Externe Provider-Redirects nutzen ausschließlich vorab registrierte, providerseitig
hinterlegte Redirect-URIs (kein dynamischer `redirect_uri`-Parameter); externe `id_token`/
`OAuth2User`-Claims werden nur für Konten-Linking verwendet, wenn der Provider die E-Mail explizit
als verifiziert kennzeichnet; State-Parameter-CSRF-Schutz durch Spring Security automatisch",
ASVS-Referenz V10.4.1/V6.3.8 (je nach finaler ASVS-5.0-Nummerierung prüfen), Status „Decided nach
Umsetzung von P3.1/P3.3/P3.4". Zusätzlich `RED1`s Beschreibung um einen Klammerzusatz ergänzen
(„...gilt für die eigenen Registered Clients **und** — ab P3.2 — für jede externe
Provider-`ClientRegistration`, deren `redirect-uri` ebenfalls serverseitig fix konfiguriert ist,
nie clientseitig beeinflussbar"), `RED2` bleibt „Open", aber mit einem Hinweis auf den neuen
Kontext-Mechanismus aus 3.3 als zusätzlicher Angriffsfläche für zukünftige Bearbeitung.

**Kein Alternativvorschlag** — dieser Punkt ist reine Dokumentationskonsequenz aus den bereits in
3.1–3.4 getroffenen (bzw. hier zur Bestätigung vorgelegten) technischen Entscheidungen, keine
eigenständige Design-Frage.

### 3.12 Lokale Dev-Umgebung: Dex-Integration — ✅ vollständig entschieden (Dex + `wf.garnier:testcontainers-dex`, beides vom Nutzer bestätigt)

**Vom Nutzer bestätigt (in zwei Schritten):** Dex (`dexidp/dex`, Apache 2.0) als lokaler
Stand-in-OIDC-Provider für `TestLibrehouseholdApplication`, ausgewählt nach Vorstellung von 14
Alternativen — und, nach expliziter Rückfrage zur neuen Test-Dependency (siehe unten), die
technische Verdrahtung über `wf.garnier:testcontainers-dex`.

**Entscheidung (vom Nutzer bestätigt): wie empfohlen.** `wf.garnier:testcontainers-dex:4.0.1`
(verifiziert Spring-Boot-4-kompatibel, zuletzt released Februar 2026, siehe Recherche Frage 6) plus
das Begleitmodul `wf.garnier:spring-boot-testcontainers-dex:4.0.1` als weiterer
`<dependency>`-Eintrag in `backend/pom.xml` (Test-Scope, analog
`ch.martinelli.oss:testcontainers-mailpit`, Abschnitt 2.8) — **das war eine neue Test-Dependency und
brauchte laut AGENTS.md „Dependency Management" eine explizite Rückfrage vor dem Hinzufügen**,
Begründung (vom Nutzer akzeptiert): Kein Bedarf für eine Eigenbau-Testcontainers-Integration, da das
Modul aktiv gepflegt wird und exakt das Issuer/Port-Henne-Ei-Problem bereits löst (siehe unten).

Neuer Bean in `TestcontainersConfiguration` (Abschnitt 2.7), **sofern** das
`spring-boot-testcontainers-dex`-Begleitmodul eine passende `@ServiceConnection`-Integration
mitbringt (zu verifizieren bei Umsetzungsbeginn anhand der dann aktuellen Moduldokumentation — die
Recherche konnte die exakte Autokonfigurations-API des Begleitmoduls nicht bis ins Detail
verifizieren):

```java
@Bean
@ServiceConnection // falls vom Begleitmodul unterstützt
DexContainer dexContainer() {
    var container = new DexContainer(DexContainer.DEFAULT_IMAGE_NAME.withTag(DexContainer.DEFAULT_TAG));
    container.withUser(new DexContainer.User("dev@example.com", "dev@example.com", "devpassword"));
    container.withClient(new DexContainer.Client(
            RegisteredClientSeeder.CLIENT_ID + "-dev-provider", "dev-secret",
            "http://localhost:8080/login/oauth2/code/dev-provider"));
    return container;
}
```

**Falls kein `@ServiceConnection`-Support existiert (Fallback):** Manuelles Setzen von
`spring.security.oauth2.client.provider.dev-provider.issuer-uri` über einen
`ApplicationContextInitializer`/`@DynamicPropertySource` in `TestcontainersConfiguration`, das
`dexContainer.getIssuerUri()` (bereits vom Modul bereitgestellt, korrekt mit dem
Docker-gemappten Port templated — siehe unten) nach Containerstart ausliest. Dieser Fallback ist in
jedem Fall funktional korrekt, nur weniger elegant als eine native `@ServiceConnection`-Integration.

**Das eigentliche technische Kernproblem (Issuer kennt seinen eigenen, erst zur Laufzeit
feststehenden Port) ist bereits vom Modul gelöst**, nicht mehr Teil dieser Entscheidung: Dex wird
nicht direkt gestartet, sondern über eine Shell-Warteschleife (`while [[ ! -f /var/dex/dex.yml ]];
do sleep 1; done; dex serve /var/dex/dex.yml`); der Testcontainers-Java-Lifecycle-Hook
`containerIsStarting(...)` — der feuert, **nachdem** Docker den Port bereits gemappt hat, aber
**bevor** der eigentliche Dex-Prozess im Container läuft — schreibt die vollständige `config.yml`
(inkl. korrektem `issuer: http://<host>:<gemappter Port>/dex`) erst in diesem Moment in den
Container; erst danach erkennt die Shell-Schleife die Datei und startet Dex tatsächlich. Clients/
User werden **nicht** per YAML, sondern zur Laufzeit über Dex' eingebaute gRPC-Admin-API
angelegt (`DexContainer.Client`/`.User`-Java-API, siehe oben) — abweichend von der ursprünglichen
Annahme im Auftrag („YAML-Konfiguration mit statischen Test-Usern/-Clients"), aber funktional
gleichwertig und aus Java heraus sogar bequemer parametrisierbar (z. B. pro Test unterschiedliche
Nutzer, falls später gebraucht).

**Alternative 1 (verworfen): Eigenbau via `GenericContainer` + `dexidp/dex`-Image direkt**, ohne
das Community-Modul. Würde dieselbe Lösung (Config-Injektion via `containerIsStarting`-Hook +
File-Gate) manuell nachbauen — vertretbar, falls die Rückfrage zur neuen Dependency verneint wird,
aber strikt mehr Code/Wartungsaufwand für ein bereits gelöstes Problem. **Nur als Fallback
empfohlen, nicht als Erstwahl.**

**Alternative 2 (verworfen): Keycloak/Authentik als Dev-Provider (Dogfooding-Option).** War Teil der
14 verglichenen Alternativen und wurde vom Nutzer bereits verworfen (siehe Auftrag) — hier nur zur
Vollständigkeit erwähnt, keine erneute Bewertung nötig.

### 3.13 README-Struktur für die Social-Login-How-Tos (P3.7) — ✅ Empfehlung übernommen (geringe Tragweite, keine gesonderte Rückfrage gestellt) — Apple entfällt, siehe unten

**Empfehlung:** **Ein** gemeinsamer `== How to Configure Social Login`-Abschnitt (statt sechs
komplett getrennter Top-Level-Abschnitte), direkt nach dem bestehenden „How to Customize Email
Templates"-Abschnitt (README.adoc, Zeile 91, vor „Software Architecture Overview", siehe Abschnitt
2.9) platziert, mit folgendem Aufbau:

1. Kurzer Einleitungstext: Was ist Social Login, wo wird es aktiviert
   (`librehousehold.security.social-login.*`, Verweis auf P3.2), Verweis auf die generelle
   Property-Struktur.
2. Eine zusammenfassende Tabelle „Provider" × „Discovery-Typ (OIDC/Plain-OAuth2)" ×
   „Besonderheiten" (Google: OIDC, Standard; GitHub: Plain-OAuth2, `user:email`-Scope für
   E-Mail-Zugriff nötig; Microsoft: OIDC, Single-Tenant empfohlen; Keycloak/Authentik: OIDC,
   generische `issuer-uri`-Konfiguration). **Apple bewusst nicht enthalten** (siehe Scope-Absatz am
   Dokumentanfang — aus dieser Phase herausgenommen).
3. Je Provider ein `===`-Unterabschnitt („Google", „GitHub", „Microsoft (Entra ID)", „Keycloak",
   „Authentik" — **fünf** Unterabschnitte, kein Apple) mit: (a) wo/wie eine App/ein Client beim
   Provider registriert wird (Link zur jeweiligen Provider-Konsole), (b) die exakte Redirect-URI,
   die dort hinterlegt werden muss (`{issuer}/login/oauth2/code/{registrationId}`, siehe Abschnitt
   3.1 — **wichtig:** die Basis-URL des eigenen Backends, nicht die des Frontends), (c) welche
   `application.yaml`-Properties (bzw. Env-Variablen, konsistent mit dem `LIBREHOUSEHOLD_...`-
   Muster aus dem bestehenden Email-Abschnitt) zu setzen sind, (d) provider-spezifische
   Fallstricke (Microsofts Tenant-Wahl, GitHub-E-Mail-Scope).
4. Genau wie beim Email-Templates-Abschnitt: „During Development" (lokal, Verweis auf P3.0/Dex als
   Alternative zu echten Provider-Credentials für reine Entwicklung) / „In Production (Docker
   Compose)"-Unterteilung (Env-Variablen als Compose-`environment`-Block).

**Alternative (verworfen): Ein eigener Top-Level-Abschnitt pro Provider** (sechs separate
`==`-Überschriften). Verworfen: bläht das Inhaltsverzeichnis der README unnötig auf (aktuell nur
sechs `==`-Überschriften insgesamt, siehe Abschnitt 2.9) und verstreut den gemeinsamen
Einleitungs-/Konfigurationskontext (Property-Namespace, Discover-Endpoint-Erklärung) redundant über
sechs Abschnitte, statt ihn einmal zentral zu erklären und danach nur die Unterschiede
aufzuführen.

---

## 4. Empfohlene Umsetzungsreihenfolge

**Korrektur nach Umsetzungsbeginn (siehe unten):** Die ursprüngliche Fassung dieses Abschnitts
sah P3.0 (Dex-Dev-Umgebung) als allerersten Schritt vor, mit der Begründung „reine Infrastruktur,
keine Abhängigkeit zu den übrigen Punkten". Beim Start der Umsetzung stellte sich heraus, dass
P3.0 Aufgabe 2 (Abschnitt 5) tatsächlich auf das `librehousehold.security.social-login.*`-Schema
verweist, das erst in P3.2 entsteht — also doch eine Abhängigkeit, die hier ursprünglich übersehen
wurde. Auf Rückfrage hat der Nutzer entschieden, die Reihenfolge anzupassen: **P3.1 → P3.2 → P3.0
→ P3.3 → ...** (statt P3.0 zuerst). P3.0 Aufgabe 2 kann dadurch wie ursprünglich in Abschnitt 5
beschrieben umgesetzt werden, ohne Abstriche oder eine vorgezogene Sonderlösung.

1. **P3.1 (ADR: Social-Integration)** zuerst — dokumentiert die in Abschnitt 3.1/3.3 getroffenen
   (bzw. hier vorgelegten) Architekturentscheidungen als drei separate ADRs (ein ADR = eine
   Architekturentscheidung, nicht gebündelt): `docs/architecture/adrs/adr-016.adoc`
   (Federation-Architektur), `adr-017.adoc` (Kontext-Transport) und `adr-018.adoc`
   (Account-Linking-Regel), bevor Code geschrieben wird (konsistent mit dem Muster
   ADR-013/014/015 vor P1/P2-Umsetzung).
2. **P3.2 (Konfigurationsmodell)** direkt danach — legt `SocialLoginProperties` und das
   `librehousehold.security.social-login.*`-Schema an, das sowohl P3.0 Aufgabe 2 als auch P3.3 für
   die daraus gebauten `ClientRegistration`s braucht.
3. **P3.0 (Lokale Dev-Umgebung: Dex)** jetzt erst — Aufgabe 2 registriert Dex reibungsfrei als
   `dev-provider` im in Schritt 2 angelegten Schema, Voraussetzung für jeden folgenden IT-Test ab
   P3.3. Voraussetzung: Rückfrage aus Abschnitt 3.12 (neue Test-Dependency) muss beantwortet sein,
   bevor Aufgabe 1 beginnt (bereits erledigt, siehe dort).
4. **P3.3 (Backend: Provider-Federation & Account-Mapping)** — der mit Abstand größte Block dieser
   Phase (Filter-Chain-Erweiterung aus 3.1, Kontext-Mechanismus aus 3.3, `account_identity` aus
   3.4, `password_hash` nullable aus 3.5, GitHub-Nicht-OIDC-Pfad aus 3.6 — kein Apple-Client-Secret
   mehr, siehe Scope-Absatz). Login-Kontext (bestehende Mitglieder) sollte **vor** Setup/Invite-Kontext umgesetzt
   werden — er ist strukturell einfacher (kein Schema-Umbau von `HouseholdSetup`/
   `MemberRegistration` nötig) und validiert den Kern-Mechanismus (Filter-Chains, Account-Linking),
   bevor P3.6 darauf aufbaut.
5. **P3.4 (OpenAPI: Discover-Endpoint)** kann **parallel** zu P3.3 begonnen werden (reine
   Konfigurationsabfrage, keine Abhängigkeit zum Linking-Mechanismus selbst) — inhaltlich aber erst
   sinnvoll *abschließbar*, wenn P3.2s Property-Schema (Abschnitt 3.2) steht, da der Endpoint genau
   daraus liest.
6. **P3.5 (Frontend: dynamische Auth-UI)** nach P3.4 (braucht den generierten `AuthDiscovery`-
   Client-Typ) und nach dem Login-Teil von P3.3 (damit ein Login-Button überhaupt gegen etwas
   Funktionierendes läuft).
7. **P3.6 (Setup/Invite ausschließlich via Social)** zuletzt unter den Code-Punkten — baut
   direkt auf dem in P3.3 etablierten Kontext-Mechanismus (Abschnitt 3.3) und den
   OpenAPI-Schema-Änderungen (Abschnitt 3.10) auf; ohne einen bereits funktionierenden,
   getesteten Login-Federation-Pfad aus P3.3 wäre P3.6 nicht sinnvoll zu verifizieren.
8. **P3.7 (README-How-Tos)** ganz zuletzt — dokumentiert konkrete Property-Namen/Redirect-URI-
   Muster, die erst nach P3.2/P3.3 final feststehen; eine vorzeitige Dokumentation würde bei jeder
   Schema-Anpassung während der Umsetzung sofort veralten.

---

## 5. Aufgaben je P-Punkt

Für jeden Schritt gilt strikt Rot-Grün-Refactor. Dateipfade sind neu anzulegende Dateien, sofern
nicht anders vermerkt. Alle Aufgaben setzen voraus, dass die zugehörige Entscheidung in Abschnitt 3
vom Nutzer bestätigt ist (siehe Prioritätenliste in der Abschlussantwort).

### P3.0 — Lokale Dev-Umgebung: Dex als Test-OIDC-Provider

**Voraussetzung:** Rückfrage aus Entscheidung 3.12 (neue Test-Dependencies
`wf.garnier:testcontainers-dex`/`spring-boot-testcontainers-dex`) muss beantwortet sein.

**Aufgabe 1: Dependency + Container-Bean**

- `backend/pom.xml`: `wf.garnier:testcontainers-dex:4.0.1` und (falls die Verifikation aus
  Entscheidung 3.12 sie als geeignet bestätigt) `wf.garnier:spring-boot-testcontainers-dex:4.0.1`
  als Test-Scope-Dependencies ergänzen, analog `ch.martinelli.oss:testcontainers-mailpit`
  (`backend/pom.xml:232-251`).
- `TestcontainersConfiguration.java` (Abschnitt 2.7) um `dexContainer()`-Bean erweitern (siehe
  Codebeispiel Abschnitt 3.12).
- Kein TDD-Rot/Grün für die reine Container-Bean-Definition — Verifikation erfolgt implizit über
  die IT-Tests der folgenden Aufgabe (Container muss beim Testlauf erfolgreich hochfahren und ein
  gültiges OIDC-Discovery-Dokument liefern).

**Aufgabe 2: Registrierung als generischer `dev-provider` in `TestLibrehouseholdApplication`**

- `TestLibrehouseholdApplication.java` (Abschnitt 2.7) um ein weiteres Default-Argument
  `librehousehold.security.social-login.providers.dev-provider.issuer-uri=<dexContainer.getIssuerUri()>`
  ergänzen — unter Beachtung des bestehenden Filtermechanismus gegen bereits übergebene Keys
  (Zeile 29–33), damit ein DevTools-Neustart nicht doppelte Argumente erzeugt (siehe Abschnitt
  2.7). Falls die `@ServiceConnection`-Variante (Entscheidung 3.12) funktioniert, entfällt dieser
  Schritt zugunsten der automatischen Property-Injektion.
- Neuer Eintrag `dev-provider` mit `type: oidc`, `client-id`/`client-secret` passend zum in
  Aufgabe 1 registrierten `DexContainer.Client`.
- **IT-Test** `DexIntegrationIT` (neuer, minimaler Test, `@SpringBootTest` mit
  `TestcontainersConfiguration`): `it('applicationContext_startsWithDexProvider_discoveryDocumentReachable')`
  — ruft `{dexContainer.getIssuerUri()}/.well-known/openid-configuration` direkt per
  `RestClient`/`WebClient` auf und prüft einen 200er-Status, als reiner Rauchtest, dass die
  Config-Injektion (Abschnitt 3.12) tatsächlich funktioniert, bevor P3.1–P3.6 sich darauf
  verlassen.

### P3.1 — ADR: Social-Integration

**Umgesetzt als drei separate ADRs statt eines gebündelten Dokuments** (Nutzer-Feedback bei
Review: „ein ADR beschreibt immer genau eine Architekturentscheidung" — die ursprünglich hier
geplante Fassung bündelte drei; Stil/Länge zusätzlich an ADR-006/ADR-012 angeglichen, kein
Verweis auf dieses Planungsdokument in den ADRs selbst, da Detailpläne nicht dauerhaft erhalten
bleiben):

**Aufgabe 1: `docs/architecture/adrs/adr-016.adoc` — Federation-Architektur**

- Titel „Authorization-Server-Side Social Login Federation". Kontext: ADR-013-Zitat. Entscheidung:
  Spring-Authorization-Server-Federation (Entscheidung 3.1) statt Multi-Client-Registrierung, mit
  Verweis auf den offiziellen Spring-Guide und Begründung über ADR-014 (BFF).

**Aufgabe 2: `docs/architecture/adrs/adr-017.adoc` — Kontext-Transport**

- Titel „Pre-Redirect Session Endpoint for Social Login Context". Entscheidung: Session-Endpoint
  vor dem Redirect (Entscheidung 3.3) statt Per-Kontext-Registrierung oder Query-Parameter, xref
  auf ADR-016.

**Aufgabe 3: `docs/architecture/adrs/adr-018.adoc` — Account-Linking-Regel**

- Titel „Automatic Verified-Email Account Linking for Social Login". Entscheidung: automatisches
  Linking per providerseitig verifizierter E-Mail (Entscheidung 3.4) statt explizitem
  Verknüpfen-Schritt, xref auf ADR-012/ADR-016.

- `docs/architecture/adrs/index.adoc` um alle drei neuen Einträge ergänzen (Zeile nach dem
  bestehenden letzten Eintrag, Zeile 20).
- Kein Code, keine Tests — reine ADR-Erstellung, wie bei ADR-013/014/015 vor der jeweiligen
  Umsetzung.

### P3.2 — Konfigurationsmodell

**Korrekturen aus dem Nutzer-Review (gelten vorrangig vor den Aufgaben unten):**

- Kein `SocialLoginPropertiesTest`: Der Record hat keine eigene Logik (`@DefaultValue` statt
  null-Default im Compact-Constructor); ein Binding-Test würde nur Spring Boots Binder testen.
- Neue kohäsive Klasse `CommonProvider` (`isCommonProvider(...)`) statt `isKnownProvider` in der
  Factory; der Validator hängt **nicht** mehr an der Factory (keine DRY-Kopplung).
- Neue Adapter-Klasse `OidcIssuerDiscovery` kapselt `ClientRegistrations.fromIssuerLocation`
  (echtes HTTP). Im Factory-Unit-Test wird sie gemockt, die echte Discovery testet ein eigenes
  `OidcIssuerDiscoveryIT`. Unit-Tests starten keine Server.
- Validator ist kein `ApplicationRunner` mehr. `SecurityConfig.clientRegistrationRepository()` ruft
  `validate(...)` vor dem Bauen der Registrierungen auf, damit eine kaputte Konfiguration mit einer
  Meldung scheitert, die den Provider beim Namen nennt. Neu: Unbekannte `oauth2`-Provider müssen
  `authorization-uri`, `token-uri`, `user-info-uri` und `user-name-attribute` setzen.
- Weitere Validierungsregeln (Nutzerentscheidung, zweite Review-Runde): `client-id` und
  `client-secret` sind für **jeden** Provider Pflicht (der eigene AS ist immer Confidential
  Client). Leere Werte (z. B. aus `${GOOGLE_CLIENT_ID:}`) gelten wie fehlende. Ein fehlender `type`
  scheitert nur bei unbekannten Provider-Namen; bei `google`/`github` usw. darf er fehlen. Die
  Factory behandelt eine leere `issuer-uri` wie eine fehlende.
- Vollständige Zweigabdeckung statt nur der unten gelisteten Mindest-Testnamen; keine Verweise auf
  dieses Planungsdokument im Code.

**Aufgabe 1: `SocialLoginProperties`**

- Neue Datei `.../config/SocialLoginProperties.java`, `@ConfigurationProperties(prefix =
  "librehousehold.security.social-login")`, Record-basiert (`enabled: boolean`,
  `providers: Map<String, ProviderConfig>`), `ProviderConfig` als Sealed-Interface oder
  diskriminiertes Record mit `type` (`OIDC`, `OAUTH2`), siehe Schema Entscheidung 3.2. Kein
  `APPLE`-Typ in dieser Phase (siehe Scope-Absatz) — das Sealed-Interface/diskriminierte Schema
  lässt sich aber ohne Bruch um einen weiteren Typ erweitern, falls Apple später nachgezogen wird.
- Test zuerst, `SocialLoginPropertiesTest` (Unit, reines Binding-Verhalten, `@EnableConfigurationProperties`
  in einem minimalen Test-Kontext oder direktes Objekt-Binding via
  `Binder`/`ConfigurationPropertiesTestBinder`):
  - `it('bind_oidcProviderWithIssuerUri_bindsTypeAndIssuerUri')`
  - `it('bind_oauth2ProviderWithExplicitEndpoints_bindsAllEndpointProperties')`
  - Rot: Klasse fehlt.
  - Grün: minimales Binding.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 2: `ClientRegistrationRepository`-Erweiterung**

- `SecurityConfig.clientRegistrationRepository()` (Zeile 140–161, Abschnitt 2.1) erweitern: Neben
  dem bestehenden statischen SPA-Client zusätzlich für jeden in `SocialLoginProperties` aktiven
  Provider eine `ClientRegistration` bauen (`ClientRegistrations.fromIssuerLocation(...)` für
  `type: oidc` mit `issuer-uri`, `CommonOAuth2Provider.valueOf(...).getBuilder(...)` für bekannte
  Kurznamen wie `google`/`github`, sonst manueller `ClientRegistration.withRegistrationId(...)`-
  Aufbau).
- Test zuerst (Unit, `SocialLoginProperties` direkt konstruiert, keine Spring-Context-Bindung
  nötig):
  - `it('buildClientRegistrations_googleProviderConfigured_resolvesViaCommonOAuth2Provider')`
  - `it('buildClientRegistrations_genericOidcProviderConfigured_resolvesViaIssuerDiscovery')`
  - `it('buildClientRegistrations_githubProviderConfigured_resolvesViaCommonOAuth2ProviderGithub')`
  - `it('buildClientRegistrations_socialLoginDisabled_returnsOnlySpaClientRegistration')`
  - Rot: Erweiterung fehlt.
  - Grün: wie oben.
  - Refactor: Provider-Typ-zu-Builder-Mapping in eine eigene, testbare Methode/Klasse extrahieren
    (`SocialLoginClientRegistrationFactory`), falls `clientRegistrationRepository()` sonst zu groß
    würde (AGENTS.md „Keep functions short").

**Aufgabe 3: Startvalidierung**

- Neuer `ApplicationRunner` (oder Erweiterung von `RegisteredClientSeeder`, Empfehlung: **neue,
  eigene Klasse** `SocialLoginConfigurationValidator`, da inhaltlich unabhängig von der
  SPA-Client-Seedierung, SRP), wirft beim Start eine aussagekräftige Exception, falls ein
  konfigurierter Provider unvollständig ist (siehe Entscheidung 3.2).
- Test zuerst (Unit):
  - `it('validate_oidcProviderMissingIssuerUriAndUnknownType_throwsIllegalStateException')`
  - `it('validate_completeConfiguration_doesNotThrow')` (`assertThatCode(...).doesNotThrowAnyException()`)
  - Rot: Klasse fehlt.
  - Grün: wie oben.
  - Refactor: keiner nötig.

### P3.3 — Backend: Provider-Federation & Account-Mapping

**Aufgabe 1: Migration `password_hash` nullable + `account_identity`-Tabelle**

- Neue Flyway-Datei `V2__federated_accounts.sql`:
  `ALTER TABLE account ALTER COLUMN password_hash DROP NOT NULL;` plus die
  `account_identity`-Tabelle aus Entscheidung 3.4.
- Kein TDD-Rot/Grün für reine Migration — Verifikation über die IT-Tests der folgenden Aufgaben.

**Aufgabe 2: `AccountIdentityEntity`/`AccountIdentityRepository`, `AccountEntity` um nullable `passwordHash`**

- `AccountEntity`: `passwordHash` als `@Nullable String`, alle sieben Konstruktor-Aufrufstellen
  (`AccountService.createAccount`, neue `createFederatedAccount`) anpassen.
- Neue Datei `.../household/model/AccountIdentityEntity.java` (Record, analog
  `AccountTokenEntity`).
- Neue Datei `.../household/repository/AccountIdentityRepository.java`:
  `findByProviderIdAndExternalSubject(String providerId, String externalSubject)`,
  `save(AccountIdentityEntity)` (Standard-`CrudRepository`, `id` bleibt `null` bis erstem Save,
  kein `Persistable` nötig, analog `InviteEntity`/`AccountTokenEntity`).
- Kein eigener Unit-Test für reines Repository-Interface (Derived Queries, AGENTS.md-Konvention),
  Verifikation über IT-Test der folgenden Aufgabe.

**Aufgabe 3: `AccountService.createFederatedAccount`, `linkExternalIdentity`, `findMemberByExternalIdentity`**

- `AccountService`: `createFederatedAccount(memberId): void` (siehe Entscheidung 3.5, setzt
  `passwordHash=null`, `emailVerified=true`), `linkExternalIdentity(memberId, providerId,
  externalSubject): void`, `findMemberIdByExternalIdentity(providerId, externalSubject):
  Optional<UUID>`.
- Test zuerst, Erweiterung `AccountServiceIT` (Testcontainers, echte DB, kein Mocking laut
  AGENTS.md-Pflicht für DB-Interaktion):
  - `it('createFederatedAccount_validCall_persistsAccountWithNullPasswordHashAndVerifiedEmail')`
  - `it('linkExternalIdentity_validCall_persistsAccountIdentityRow')`
  - `it('linkExternalIdentity_alreadyLinkedToAnotherMember_throwsDataIntegrityViolationException')`
    — verifiziert das UNIQUE-Constraint (`provider_id`, `external_subject`).
  - `it('findMemberIdByExternalIdentity_linkedIdentity_returnsMemberId')`
  - `it('findMemberIdByExternalIdentity_unlinkedIdentity_returnsEmpty')`
  - Rot: Methoden fehlen.
  - Grün: minimale Implementierung wie oben.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 4: Kontext-Mechanismus (Entscheidung 3.3)**

- Neuer Endpoint `POST /auth/social-context` (OpenAPI: neues Schema `SocialLoginContext {
  context: enum[SETUP, INVITE], inviteToken?: uuid }`, `security: []`), neue Service-Klasse
  `.../household/service/SocialLoginContextStore.java`, die den Kontext in der aktuellen
  `HttpSession` ablegt (`RequestContextHolder`, analog `AccountSessionAuthenticator`, Abschnitt
  2.13) und wieder ausliest.
- Test zuerst (Unit, `HttpSession` gemockt via `MockHttpServletRequest`):
  - `it('store_setupContext_persistsInSession')`
  - `it('store_inviteContextWithToken_persistsTokenAlongsideContext')`
  - `it('read_noContextStored_returnsLoginDefault')`
  - Rot: Klasse fehlt.
  - Grün: wie oben.
  - Refactor: keiner nötig.

**Aufgabe 5: `FederatedIdentityAuthenticationSuccessHandler` + Chain-1-`oauth2Login()`-Erweiterung**

- `SecurityConfig`: `authorizationServerSecurityFilterChain` (Zeile 184–202) um `.oauth2Login(...)`
  ergänzen (siehe Codebeispiel Entscheidung 3.1), neuer
  `FederatedIdentityAuthenticationSuccessHandler`-Bean (analog Spring-Guide-Referenzcode), der je
  nach `SocialLoginContextStore`-Zustand (Aufgabe 4) entweder Login (Lookup via `AccountIdentityRepository`),
  Setup (`createFederatedAccount` + neuen Haushalt/Mitglied bauen, siehe Aufgabe 6/P3.6) oder
  Invite-Join durchführt.
- **IT-Test** `SocialLoginFilterChainIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `TestcontainersConfiguration`, echter HTTP-Redirect-Flow gegen den Dex-Container aus P3.0, analog
  `AuthorizationServerConfigurationIT`):
  - `it('login_existingMemberWithLinkedGoogleIdentity_establishesAuthenticatedSession')`
  - `it('login_verifiedEmailMatchesExistingMember_autoLinksAndEstablishesSession')`
  - `it('login_unverifiedEmailFromProvider_doesNotAutoLinkAndRejects')` — deckt den in
    Entscheidung 3.4 beschriebenen Sicherheitsvorbehalt ab.
  - `it('login_noMatchingMemberAndNoSetupInviteContext_rejectsWithNoAccountFound')`
  - Rot: Mechanismus fehlt.
  - Grün: wie oben beschrieben.
  - Refactor: Lookup-/Linking-Entscheidungslogik als eigene, testbare Methode extrahieren, falls
    der `AuthenticationSuccessHandler` sonst zu viele Verzweigungen bekommt (AGENTS.md „Avoid deep
    nesting").

**Aufgabe 6: GitHub-/Nicht-OIDC-`OAuth2UserService`**

- Neue Datei `.../household/service/FederatedOAuth2UserService.java`
  (`OAuth2UserService<OAuth2UserRequest, OAuth2User>`), lädt bei GitHub zusätzlich
  `https://api.github.com/user/emails` (per `RestClient`, `user:email`-Scope), extrahiert
  verifizierte primäre E-Mail.
- `SecurityConfig`: `.userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService)
  .userService(federatedOAuth2UserService))` (siehe Entscheidung 3.6).
- Test zuerst (Unit, `RestClient`/`OAuth2UserRequest` gemockt):
  - `it('loadUser_githubUser_fetchesVerifiedPrimaryEmailFromEmailsEndpoint')`
  - `it('loadUser_githubUserNoVerifiedEmail_doesNotAutoLink')`
  - Rot: Klasse fehlt.
  - Grün: wie oben.
  - Refactor: keiner nötig.

**Aufgabe 7 entfällt:** Ein Apple-Client-Secret-Generator (`AppleClientSecretSupplier`,
ES256-JWT-Signierung + Rotations-Job) war hier ursprünglich vorgesehen — Apple wurde vom Nutzer
aus dieser Phase herausgenommen (Entscheidung 3.7), siehe Scope-Absatz und „Offene Nacharbeit
außerhalb dieses Plans" am Dokumentende.

**Aufgabe 8: Security-Checkliste (`FED1`) + Risk-Dokument**

- `docs/architecture/chapters/08_concepts.adoc` (Abschnitt 2.5): neue Zeile `FED1` einfügen (siehe
  Entscheidung 3.11), `RED1`-Beschreibung ergänzen.
- `docs/architecture/chapters/11_technical_risks.adoc`: Verweis auf TD1 im Kontext der neuen
  Federation-Logik ergänzen (Abschnitt 2.6).
- Kein Code, keine Tests — reine Doku-Änderung.

### P3.4 — OpenAPI: Discover-Endpoint

**Aufgabe 1: Schema + Pfad in `api/openapi.yml`**

- Neues Schema `AuthDiscovery` (siehe Entscheidung 3.8).
- Neuer Pfad `GET /auth/discover`, `security: []`, Tag `auth` (neuer Tag, analog bestehendem
  Tag-Verzeichnis am Dateianfang).
- `npm run openapi` / `./mvnw clean compile` danach.

**Aufgabe 2: `AuthDiscoveryApiDelegateImpl`**

- Neue Datei `.../auth/controller/AuthDiscoveryApiDelegateImpl.java` (neues, eigenständiges
  `auth`-Package auf Root-Ebene, siehe Entscheidung 3.8), liest `SocialLoginProperties`
  (Konstruktor-Injektion, direkte Dependency) und ein neues `librehousehold.security.local-
  login.enabled`-Property (`@Value`, Default `true`).
- Test zuerst (Unit, `SocialLoginProperties` direkt konstruiert):
  - `it('getAuthDiscovery_socialLoginEnabledWithTwoProviders_returnsBothProvidersAndLocalEnabled')`
  - `it('getAuthDiscovery_socialLoginDisabled_returnsEmptyProvidersList')`
  - `it('getAuthDiscovery_localLoginDisabled_returnsLocalEnabledFalse')`
  - Rot: Klasse fehlt (Compile-Fehler durch die neue OpenAPI-Vertragsänderung, erwartetes Rot wie
    im Vorgänger-Plan Abschnitt 2.2 dokumentiert).
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig bei dieser Größe.

### P3.5 — Frontend: dynamische Auth-UI

**Aufgabe 1: `authDiscoveryState.svelte.ts`**

- Neue Datei `frontend/src/lib/stores/authDiscoveryState.svelte.ts` (Runes-State, analog
  `sessionState.svelte.ts`), lädt einmalig `AuthApi.getAuthDiscovery()` und cached das Ergebnis im
  Modul-Scope (kein localStorage, reine In-Memory-App-Konfiguration für die SPA-Laufzeit, siehe
  Entscheidung 3.9).
- Test zuerst, `authDiscoveryState.svelte.spec.ts`:
  - `it('loadAuthDiscovery_successfulResponse_populatesProvidersAndLocalEnabled')`
  - `it('loadAuthDiscovery_calledTwice_onlyFetchesOnce')` — deckt das Caching-Verhalten ab.
  - Rot: Modul fehlt.
  - Grün: minimale Implementierung.
  - Refactor: keiner nötig.

**Aufgabe 2: `oauth2Login.ts` um Provider-Parameter erweitern**

- `frontend/src/lib/oauth2Login.ts` (Abschnitt 2.3): `redirectToOAuth2Login(providerId?: string)`
  — Default bleibt `spa-backend-client` (interner Loop-Back, unverändert für bestehenden
  `completeSilentOAuth2Login`-Anwendungsfall), neuer Parameter für externe Provider-IDs.
- Test zuerst, Erweiterung bestehender Testdatei für `oauth2Login.ts` (falls vorhanden, sonst neu):
  - `it('redirectToOAuth2Login_withProviderId_navigatesToProviderSpecificPath')`
  - `it('redirectToOAuth2Login_withoutProviderId_navigatesToDefaultSpaClientPath')` (Regression).
  - Rot: Parameter fehlt.
  - Grün: wie oben.
  - Refactor: keiner nötig.

**Aufgabe 3: Provider-Buttons auf Login/Setup/Invite**

- `frontend/src/routes/login/+page.svelte`, `frontend/src/routes/setup/+page.svelte`,
  `frontend/src/routes/invite/[token]/+page.svelte`: je einen Button pro
  `authDiscoveryState.socialProviders`-Eintrag ergänzen (Klick ruft ggf. zuerst `POST
  /auth/social-context`, Abschnitt 5/P3.3 Aufgabe 4, dann `redirectToOAuth2Login(provider.id)`
  auf); lokales Formular ausgeblendet, falls `!authDiscoveryState.localEnabled`.
- Test zuerst, Erweiterung der jeweiligen `*-page.svelte.spec.ts`-Dateien:
  - `it('zeigt einen Button pro konfiguriertem Social-Provider an')`
  - `it('blendet das lokale Formular aus, wenn localEnabled false ist')`
  - `it('ruft social-context mit dem Setup-Kontext auf, bevor zum Provider weitergeleitet wird')`
    (nur auf der Setup-Seite).
  - Rot: Logik fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig bei dieser Größe.
- Neue i18n-Keys `auth.social_login_button` (mit Platzhalter für Providernamen),
  `auth.social_login_error_toast`. `npm run paraglide` danach.

### P3.6 — Setup/Invite ausschließlich via Social

**Aufgabe 1: OpenAPI — `localRegistration` optional**

- `HouseholdSetup`/`MemberRegistration` (`api/openapi.yml`, Abschnitt 2.14): `localRegistration`
  aus `required` entfernen. Neuer Kommentar im Schema, der die Wechselbeziehung mit dem
  Social-Context-Mechanismus (P3.3 Aufgabe 4) erklärt.
- `npm run openapi` / `./mvnw clean compile`.

**Aufgabe 2: `HouseholdSetupService`/`MemberManagementService` — Verzweigung Social vs. Local**

- Beide Services (Abschnitt 2.13) verzweigen: `setup.getLocalRegistration()` vorhanden → bestehender
  Pfad unverändert. Nicht vorhanden → liest den authentifizierten `OAuth2AuthenticationToken` aus
  dem `SecurityContextHolder` (muss durch den vorherigen Federation-Login gesetzt sein, sonst
  `IllegalStateException`/neue `NoFederatedIdentityException`), übernimmt Name/E-Mail von dort
  (nicht aus dem Request-Body, siehe Entscheidung 3.10), ruft `accountService.createFederatedAccount(...)`
  statt `createAccount(...)`, **kein** Aufruf von `accountSessionAuthenticator
  .authenticateAndPersistSession(...)` — stattdessen eine neue Methode
  `accountSessionAuthenticator.enrichExistingFederatedSession(memberId, householdId, isAdmin)`,
  die den bereits bestehenden `SecurityContext` um die Business-relevanten Claims anreichert
  (analog zu dem, was `AccountOidcUserService.enrich(...)` bereits für den internen Loop-Back tut,
  Abschnitt 2.12), statt eine komplett neue Authentication zu bauen.
- Test zuerst (Unit, Services/Repositories gemockt, analog bestehenden Tests für diese Klassen):
  - `it('setupHousehold_withLocalRegistration_usesExistingPasswordPath')` (Regression)
  - `it('setupHousehold_withoutLocalRegistrationAndFederatedPrincipalPresent_createsFederatedAccountFromPrincipalData')`
  - `it('setupHousehold_withoutLocalRegistrationAndNoFederatedPrincipal_throwsNoFederatedIdentityException')`
  - `it('joinHousehold_withoutLocalRegistrationAndFederatedPrincipalPresent_createsFederatedAccountInInviteHousehold')`
  - Rot: Verzweigung fehlt.
  - Grün: wie oben beschrieben.
  - Refactor: gemeinsame Verzweigungslogik (Local-vs-Federated-Account-Erstellung) in eine private
    Hilfsmethode extrahieren, falls sie in beiden Services near-identisch dupliziert würde
    (AGENTS.md DRY-Abwägung — hier eher für Extraktion, da es sich um Sicherheits-/Kernlogik
    handelt, nicht um Testdaten, wo Duplikation laut AGENTS.md ausdrücklich bevorzugt wird).

**Aufgabe 3: `AccountSessionAuthenticator.enrichExistingFederatedSession`**

- Neue Methode in `AccountSessionAuthenticator` (Abschnitt 2.13), die den bestehenden
  `OAuth2AuthenticationToken` im `SecurityContextHolder` **nicht** ersetzt, sondern das
  resultierende Prinzip für nachfolgende Requests (insbesondere den anschließenden
  `AccountOidcUserService.enrich(...)`-Aufruf beim internen Loop-Back, Abschnitt 2.12) korrekt mit
  `memberId`/`householdId`/`isAdmin` verknüpfbar macht — exaktes Vorgehen hängt von der
  endgültigen P3.3-Implementierung (Aufgabe 5, `FederatedIdentityAuthenticationSuccessHandler`) ab
  und sollte erst nach deren Abschluss final entworfen werden, da beide Mechanismen denselben
  `SecurityContext` betreffen.
- **IT-Test** `SocialSetupFlowIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `TestcontainersConfiguration`, echter Federation-Login gegen Dex aus P3.0, gefolgt vom
  eigentlichen `POST /household/setup`-Request ohne `localRegistration`):
  - `it('setupViaDex_completeFlow_createsHouseholdMemberAccountAndEstablishesBusinessSession')`
  - Rot: Mechanismus fehlt.
  - Grün: wie oben beschrieben.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 4: Frontend — Setup-/Invite-Seite ohne Passwortfeld bei Social-Wahl**

- `frontend/src/routes/setup/+page.svelte`/`frontend/src/routes/invite/[token]/+page.svelte`:
  Wählt der Nutzer einen Social-Button (statt das lokale Formular auszufüllen), wird kein
  `PasswordField.svelte` angezeigt/kein `localRegistration` im Request mitgeschickt — nach
  erfolgreichem Rücksprung vom Provider (Aufgabe 3, IT-Test-Pfad) ruft die Seite `POST
  /household/setup`/`POST /invite/{token}/join` **ohne** `localRegistration` auf.
- Test zuerst, Erweiterung der jeweiligen `*-page.svelte.spec.ts`:
  - `it('ruft setupHousehold ohne localRegistration auf, wenn per Social registriert wurde')`
  - Rot: Logik fehlt.
  - Grün: minimale Verdrahtung.
  - Refactor: keiner nötig.

### P3.7 — README: Diátaxis-How-To-Guides für Social-Login-Konfiguration

**Voraussetzung:** P3.2/P3.3 abgeschlossen (konkrete Property-Namen/Redirect-URI-Muster müssen
feststehen, siehe Abschnitt 4).

**Aufgabe 1: Struktur anlegen**

- `README.adoc`: neuer `== How to Configure Social Login`-Abschnitt nach „How to Customize Email
  Templates" (Zeile 91, siehe Entscheidung 3.13), mit Einleitung + Vergleichstabelle (siehe
  Entscheidung 3.13, Punkt 2).

**Aufgabe 2: Je Provider ein Unterabschnitt (Englisch)**

- Fünf `===`-Unterabschnitte: Google, GitHub, Microsoft (Entra ID), Keycloak, Authentik — **kein**
  Apple-Unterabschnitt (siehe Scope-Absatz) — je mit App-Registrierung, exakter Redirect-URI,
  `application.yaml`-Properties/Env-Var-Namen, provider-spezifischen Fallstricken (siehe
  Entscheidung 3.13, Punkt 3). Google/GitHub/Keycloak/Authentik zuerst (einfachere Fälle),
  Microsoft (Tenant-Wahl) danach mit ausführlicherer Warnbox.

**Aufgabe 3: Dev/Docker-Compose-Aufteilung**

- „During Development" (Verweis auf P3.0/Dex als Alternative zu echten Provider-Credentials) /
  „In Production (Docker Compose)" (Env-Variablen-Block), analog dem bestehenden
  Email-Templates-Abschnitt (README.adoc:63-91).
- Kein Code, keine Tests — reine Dokumentation, UI-only-Äquivalent auf Doku-Ebene (AGENTS.md:
  „UI-only changes... do not require tests", hier analog für reine Prosa-Dokumentation ohne
  Verhaltensänderung).

---

## Offene Nacharbeit außerhalb dieses Plans

- **Apple („Sign in with Apple") als weiterer Provider.** Vom Nutzer explizit aus Phase 3
  herausgenommen (Entscheidung 3.7: „Lass das erstmal weg. Apple muss nicht unbedingt sein.").
  Recherche-Stand für einen späteren Anlauf: `client_secret` ist ein selbstsigniertes ES256-JWT
  (Claims `iss`=Team-ID, `sub`=Services-ID/`client_id`, `aud=https://appleid.apple.com`,
  Header-`kid`=Key-ID), signiert mit dem privaten Schlüssel aus einer einmalig herunterladbaren
  `.p8`-Datei, mit einer harten Obergrenze von **6 Monaten** Gültigkeit und **keiner**
  Auto-Rotations-API bei Apple — eine verpasste Rotation führt zu einem stillen, flächendeckenden
  `invalid_client` für alle Nutzer. `CommonOAuth2Provider.APPLE` existiert nicht; Nimbus-JOSE-JWT
  ist bereits transitiv über `spring-security-oauth2-jose` vorhanden und würde für die
  JWT-Signierung wahrscheinlich ausreichen (vor Umsetzung verifizieren). Name/E-Mail werden von
  Apple nur beim **ersten** Autorisierungsversuch übermittelt (separates `user`-Feld im
  `form_post`-Response, nicht im `id_token`) — muss synchron im allerersten Callback abgegriffen
  werden. Bei Umsetzung: gleiche Rückfrage „selbst schreiben vs.
  `patrickbussmann/oauth2-apple`" wie ursprünglich hier vorgesehen erneut stellen.
- Account-Unlinking (einen einzelnen verknüpften Provider wieder entfernen, ohne den ganzen
  Account zu löschen) ist nicht Teil des Meta-Plan-Wortlauts P3.1–P3.6 und wurde hier bewusst
  nicht mitgeplant (siehe Scope-Abgrenzung am Dokumentanfang) — falls gewünscht, ein neuer,
  eigenständiger Meta-Plan-Punkt.
- Eine `CurrentUser`-Erweiterung um „welche Provider sind mit diesem Account verknüpft" (für eine
  mögliche künftige Settings-Anzeige) ist ebenfalls nicht Teil dieses Plans (siehe Entscheidung
  3.4) — bei Bedarf als eigener, kleiner Folgepunkt.
