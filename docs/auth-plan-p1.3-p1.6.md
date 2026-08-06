# Implementierungsplan P1.3–P1.6: Backend-Auth (Accounts, Authorization Server, BFF-Absicherung, Access Control)

## Referenz

Feinplan zu [`docs/auth-meta-plan.md`](auth-meta-plan.md), Punkte **P1.3–P1.6** ("Phase 1 — Lokale
Accounts: Kernflow"). Baut auf dem bereits umgesetzten Vertrag aus P1.1/P1.2 auf (`LocalRegistration`,
`EmailAvailability`, `CurrentUser`, `Password`-Schema, `GET /members/availability`, `GET /me`,
`POST /logout`, `components.securitySchemes.sessionCookie` in `api/openapi.yml`). P1.7–P1.10
(Frontend) werden parallel geplant; dieses Dokument referenziert deren Schnittstellen nur, dupliziert
sie aber nicht. P0.x, P2.x, P3.x sind explizit nicht Teil dieses Plans.

## Status

Entwurf. Noch nicht umgesetzt. Enthält eine zentrale, vom Nutzer zu bestätigende Architektur-
entscheidung (siehe DD-P1.4-1).

## Bindende Vorgaben

- **ADR-009** — Argon2id für Passwort-Hashing.
- **ADR-011** — Cross-Modul-Kommunikation: Named Interfaces für synchrone Abfragen ("Existiert X /
  gehört X zu Y?"), Domain Events für Lifecycle-Reaktionen. Keine Cross-Schema-DB-FKs; FKs innerhalb
  eines Schemas (hier: `account` → `member`, beide im `household`-Schema) sind erlaubt.
- **ADR-012** — 1 Account : 1 Household. `AccountRegistration` bleibt im `household`-Modul.
- **ADR-013** — Spring Authorization Server als Authorization Server *und* Resource Server im selben
  Monolithen; Authorization Code Flow + PKCE.
- **ADR-014** — BFF-Pattern: httpOnly/SameSite Session-Cookie, Tokens verlassen das Backend nie,
  Cookie-basiertes CSRF (`XSRF-TOKEN`/`X-XSRF-TOKEN`), Logout mit serverseitiger Revocation.
- **ADR-015** — Credentials in eigener `account`-Tabelle, 1:1 zu `member`, nicht als Spalten auf
  `member` selbst (nicht jedes Mitglied hat/braucht einen Account; künftige rein föderierte Accounts
  haben keinen Passwort-Hash).
- **Arc42 Kap. 8, "Authentication and Authorization"** — kein eigenes Auth-Modul; Framework-Typen
  (`PasswordEncoder` etc.) werden direkt injiziert, keine Named Interface dafür.
- **Arc42 Kap. 8, Security Controls (Authentication)** — insb. HASH1 (Argon2id, ADR-009), SES1–SES3
  (Cookies/CSRF/kein localStorage, ADR-014), CORS1 (Origin-Allowlist trotz Nginx-Same-Origin), RED1
  (`redirect_uri`-Allowlist, nativ durch Spring Authorization Server), PKCE1 (ADR-013), ENUM1
  (keine Existenz-Rückschlüsse aus Fehlermeldungen), AC1 (Objekt-/Feld-Zugriffskontrolle je Haushalt,
  **Status: Open** — genau das ist P1.6).
- **Arc42 Kap. 11** — TD1 (Modulgrenzen noch nicht technisch erzwungen, kein `package-info.java`
  irgendwo im Backend); relevant, weil dieser Plan die ersten funktionalen Cross-Modul-Aufrufe
  außerhalb des bereits bestehenden `HouseholdQuery`/`MemberQuery`-Gebrauchs einführt (siehe P1.6).
- **AGENTS.md „Findings & Notes"** — `event_publication` muss per Flyway angelegt werden statt
  Schema-Autogen (Präzedenzfall für die Spring-Authorization-Server-Tabellen); `Persistable<UUID>` /
  `markExisting()`-Pattern für Entities mit clientseitig vergebenen bzw. bekannten IDs; Update-Pattern
  mit `@MappingTarget` + `CoreOptionalMapper`; Spring-Data-JDBC-Regel "abgeleitete Query bevorzugen,
  `@Query` nur für Joins/Subqueries".
- **TDD-Pflicht** (global + AGENTS.md): Unit-Tests mocken nur direkte Dependencies, Mapper nie
  gemockt, Integrationstests mit echter Infrastruktur (Testcontainers), `*ServiceIT`-Klassen ohne
  `@BeforeEach`, alles inline im `// given`-Block, `methodName_input_expectedOutput`-Namensschema,
  `@Nested` ab drei Testmethoden je Methode, Instancio außer bei Mapper-Tests, ein `expected`-Objekt +
  `usingRecursiveComparison()` statt vieler Einzel-Assertions, parametrisierte Tests ab drei
  ähnlichen Fällen.

## Ist-Zustand (Stand: Branch `feat/auth_new`)

### Datenmodell / Migrationen

- `backend/src/main/resources/db/migration/household/V1__create_household_and_member.sql` legt
  `household`, `member` (inkl. globaler `email`-UNIQUE-Constraint, `is_admin BOOLEAN`) und `invite`
  an. **Keine `account`-Tabelle existiert.**
- Migrationsordner sind pro Modul benannt (`household`, `tasks`, `expenses`, `usersettings`) und
  werden von Spring Modulith automatisch als Schema aufgelöst (`spring.modulith.runtime.flyway-
  enabled: true` in `backend/src/main/resources/application.yaml`); `CREATE TABLE`-Anweisungen
  innerhalb dieser Ordner sind unqualifiziert (kein `CREATE SCHEMA`, kein `household.`-Präfix), das
  Zielschema ergibt sich aus dem Ordnernamen. Ausnahme: `usersettings/V1__create_user_preferences.sql`
  enthält zusätzlich ein explizites `CREATE SCHEMA IF NOT EXISTS usersettings;` (vermutlich historisch/
  defensiv) — neue Migrationen in diesem Plan folgen dem einfacheren, unqualifizierten `household`-V1-
  Stil.
- `backend/src/main/resources/db/migration/__root/V1__create_event_publication.sql` legt die
  modulübergreifende, nicht fachliche `event_publication`-Tabelle im Default-Schema an — das ist der
  im Auftrag referenzierte Präzedenzfall für "technische Infrastruktur-Tabelle, keinem Fachmodul
  zugehörig". Die drei Spring-Authorization-Server-Tabellen gehören in dieselbe Kategorie und damit
  in denselben `__root`-Ordner, nicht in `household`.
- `MemberEntity` (`backend/src/main/java/eu/wiegandt/librehousehold/household/model/MemberEntity.java`)
  ist ein Record, das `Persistable<UUID>` implementiert mit `isNew() { return true; }` (client-
  generierte UUID, siehe AGENTS.md-Finding). `UserPreferencesEntity`
  (`.../usersettings/model/UserPreferencesEntity.java`) zeigt das alternative, für `account` direkt
  übertragbare Muster: 1:1-Relation zu `member` über `member_id` als **gemeinsamen Primärschlüssel**,
  Klasse (kein Record) mit `markExisting()`+`isNew`-Flag, weil Updates nötig sind.

### OpenAPI-Vertrag (bereits fixiert, P1.3–P1.6 dürfen ihn nicht ändern)

- `LocalRegistration` (`password`-Pflichtfeld, `api/openapi.yml:2451`) ist bereits Pflichtbestandteil
  von `HouseholdSetup` (`api/openapi.yml:2472`, `required: [household, member, localRegistration]`)
  und `MemberRegistration` (`api/openapi.yml:2564`, `required: [..., localRegistration]`).
- `GET /members/availability` (`api/openapi.yml:1496`), `GET /me` (`:1534`, bereits
  `security: [sessionCookie]`), `POST /logout` (`:1565`, bereits `security: [sessionCookie]`) sind
  spezifiziert, aber **backend-seitig nicht implementiert** (siehe unten).
- `PUT /household/{householdId}/members/{memberId}/password` (`:1623`, `changePassword`) ist
  ebenfalls spezifiziert und **nicht implementiert** — siehe „Out of Scope".

### Backend-Implementierung — was bereits existiert

- `HouseholdSetupService.setupHousehold` (`.../household/service/HouseholdSetupService.java:39-70`)
  und `MemberManagementService.joinHousehold`
  (`.../household/service/MemberManagementService.java:73-91`) legen `MemberEntity` an, **ignorieren
  aber `setup.getLocalRegistration()` bzw. `registration.getLocalRegistration()` vollständig** — das
  Passwort wird aktuell nirgends gehasht oder gespeichert. Das ist die zentrale Lücke, die P1.3
  schließt.
- Auffälligkeit beim Code-Lesen: `HouseholdSetupServiceIT`
  (`backend/src/test/java/eu/wiegandt/librehousehold/household/service/HouseholdSetupServiceIT.java:52`)
  ruft `new HouseholdSetup(household, member)` mit zwei Argumenten auf, obwohl `localRegistration`
  laut Spec ein drittes Pflichtfeld ist. Das deutet darauf hin, dass die generierten Sourcen seit der
  P1.1-Erweiterung nicht neu erzeugt/die Tests nicht nachgezogen wurden. **Muss bei der P1.3-Umsetzung
  mitkorrigiert werden** (der Task fasst ohnehin genau diese Konstruktoraufrufe an), vor Beginn der
  eigentlichen Implementierung mit `./mvnw clean compile` verifizieren.
- Keine `PasswordEncoder`-, `UserDetailsService`- oder sonstige Spring-Security-Bean existiert.
  `backend/pom.xml` enthält **keine** `spring-boot-starter-security`,
  `spring-boot-starter-oauth2-client` oder `spring-boot-starter-oauth2-authorization-server`
  Dependency — diese drei müssen für P1.4/P1.5 neu hinzugefügt werden (siehe Hinweis zu
  Dependency-Freigabe unten). Spring Boot Parent-Version: `4.1.0` (`backend/pom.xml:7`), Java 25.
- Es existiert **keine** Sicherheitskonfiguration jeglicher Art: keine `SecurityFilterChain`, kein
  CORS, kein CSRF. Alle ~24 REST-Endpunkte in `api/openapi.yml` sind aktuell ungeschützt erreichbar.
- Es existiert **keine** Zugriffskontrolle je Haushalt/Rolle. Beispiel:
  `HouseholdApiDelegateImpl.transferOwnership`
  (`.../household/controller/HouseholdApiDelegateImpl.java:55-58`) und
  `HouseholdManagementService.transferOwnership`
  (`.../household/service/HouseholdManagementService.java:79-86`) validieren nur, dass die IDs
  existieren — nicht, dass der Aufrufer Admin dieses Haushalts ist. Dasselbe gilt für alle anderen
  haushaltsgebundenen Endpunkte (`tasks`, `expenses`, `reimbursements`, `categories`, `statistics`,
  `members`, `usersettings`). Jeder beliebige Client kann aktuell mit einer fremden `householdId`/
  `memberId` in der URL lesen/schreiben (IDOR, siehe Kap. 8 Control AC1, Status "Open").
- Bereits vorhandene Named Interfaces im `household`-Root-Package
  (`backend/src/main/java/eu/wiegandt/librehousehold/household/`):
  - `HouseholdQuery` (`HouseholdQuery.java`): `boolean householdExists(UUID householdId)`.
  - `MemberQuery` (`MemberQuery.java`): `findMemberNamesByIds`, `findMemberIdsByHouseholdId`,
    `memberExistsById`, `isAdmin(UUID memberId)`. **Es fehlt eine direkte "gehört Member X zu
    Household Y?"-Abfrage** — aktuell müsste ein Aufrufer ineffizient über
    `findMemberIdsByHouseholdId(householdId).contains(memberId)` gehen. Wird in P1.6 ergänzt.
  - `MemberDeletion`, `HouseholdDeleted` (Event), `MemberRemoved` (Event) — für P1.3–P1.6 nicht
    direkt relevant, aber zeigen das etablierte Muster.
  - Implementiert durch `HouseholdQueryService` und `MemberManagementService`.
- `eu.wiegandt.librehousehold.core.CoreOptionalMapper` ist bislang die einzige Klasse im `core`-
  Package — ein MapStruct-Mixin, kein Spring-Bean. Kein `package-info.java` existiert in
  irgendeinem Modul (TD1) — Spring Modulith kann Abhängigkeiten also aktuell nicht verifizieren.
- Exceptions folgen dem Muster `extends ErrorResponseException` mit `HttpStatus` + optional
  `setDetail(...)` (z. B. `HouseholdAlreadyExistsException`, `MemberAlreadyExistsException`), kein
  zentraler `@ControllerAdvice` sichtbar im Code — Problem-Details werden also pro Exception-Klasse
  gesetzt.
- Referenz-IT-Muster: `TaskServiceIT`
  (`backend/src/test/java/eu/wiegandt/librehousehold/tasks/service/TaskServiceIT.java:34-59`) zeigt
  das `@DataJdbcTest` + `@AutoConfigureTestDatabase(replace=NONE)` +
  `@Import({...})` + `@ImportAutoConfiguration(FlywayAutoConfiguration.class)` +
  `@TestPropertySource(spring.flyway.locations=classpath:db/migration)` + `@ServiceConnection
  PostgreSQLContainer`-Muster inklusive `@MockitoBean` für `HouseholdQuery`/`MemberQuery`. Alternativ
  nutzen `HouseholdSetupServiceIT`/`MemberManagementServiceIT` den volleren
  `@SpringBootTest(webEnvironment=NONE) + @Import(TestcontainersConfiguration.class) +
  @ExtendWith(InstancioExtension.class)`-Stil mit echten Repositories statt gemockter Named
  Interfaces. Für die neuen `AccountService`-Tests wird der zweite Stil übernommen (kein Named-
  Interface-Mocking nötig, da `AccountService` intern bleibt).

## Modul-Abhängigkeitsregel (neu, vom Nutzer während dieser Planungsrunde festgelegt)

Zusätzlich zu ADR-011 (Named Interfaces vs. Domain Events) gilt ab sofort eine explizite Regel zur
**Richtung** erlaubter Modul-Abhängigkeiten, um zirkuläre Abhängigkeiten zwischen Modulen
auszuschließen:

- **`core`** darf **niemanden** aufrufen (keine ausgehenden Abhängigkeiten zu anderen Modulen) und
  darf von **allen** anderen Modulen aufgerufen werden.
- **`household`** darf **nur** `core` aufrufen, keinen anderen fachlichen Modul, und darf nicht von
  anderen Modulen abhängig sein. Über Domain Events **informieren** (z. B. `HouseholdDeleted`) ist
  ausdrücklich erlaubt, da dabei keine Abhängigkeit von `household` zu den Konsumenten entsteht
  (`household` kennt seine Listener nicht).
- Für die übrigen fachlichen Module (`tasks`, `expenses`, `reimbursements`, `categories`,
  `statistics`, `usersettings`, …) ist noch keine abschließende Regel festgelegt — Grundsatz ist
  "so wenig Abhängigkeiten wie möglich".
- Diese Regel wirkt sich auf DD-8 aus (siehe dort: `HouseholdAccessGuard` liegt deshalb in
  `household`, nicht in `core`) und auf die Paketwahl der Spring-Security-Verdrahtung in P1.4.4
  (Root-Package statt `core`, da diese Klassen zwangsläufig `household`-Typen referenzieren).

**Erledigt:** Diese Regel ist jetzt dauerhaft in Arc42 Kapitel 5 (Building Block View, neuer
Abschnitt "Module Dependency Direction") sowie als Notiz im Bounded-Context-Diagramm
(`context_map.puml`) festgehalten.

## Offene Design-Entscheidungen

Jede Entscheidung: **Empfehlung** zuerst, danach die verworfene/offene Alternative.

### DD-1 (P1.3) — Primärschlüssel-Strategie für `account` — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** `member_id UUID PRIMARY KEY REFERENCES member(id) ON DELETE CASCADE` als gemeinsamer
Schlüssel, exakt wie bereits bei `usersettings.user_preferences` (`member_id` als PK, kein eigener
UUID-Surrogatschlüssel). Das drückt die 1:1-Kardinalität (ADR-015) direkt im Schema aus, macht einen
JOIN über einen Fremdschlüssel trivial und lässt `ON DELETE CASCADE` die Aufräumarbeit beim Löschen
eines Members übernehmen (kein zusätzlicher Code in `MemberManagementService.removeMember` nötig; die
FK ist erlaubt, da `account` und `member` im selben `household`-Schema liegen, ADR-011).

**Alternative (verworfen):** eigener `id UUID PRIMARY KEY` + `member_id UUID UNIQUE NOT NULL
REFERENCES member(id)`. Bringt keinen Vorteil, da nie unabhängig von `member` referenziert; nur mehr
Boilerplate.

### DD-2 (P1.3) — `email_verified`-Spalte jetzt oder erst in P2.1 — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt, präzisiert):** Spalte **weglassen**, bis P2.1 tatsächlich
ansteht. Klarstellung des Nutzers: Da es noch **kein Release** gibt, entfällt das ursprüngliche
"zwei Migrationen auf ggf. produktiv befüllter Tabelle"-Argument vollständig — es gibt aktuell nur
eine einzige, noch unveröffentlichte Migrationsdatei für den gesamten lokalen Auth-Datenbestand
(siehe DD-9/P1.4.2, dort auch die `account`-Tabelle). Wenn P2.1 ansteht, wird `email_verified`
direkt **in dieser bestehenden Datei** ergänzt (kein `ALTER TABLE`, kein neuer Migrations-Zeitstempel
nötig) — sofern bis dahin immer noch kein Release erfolgt ist. Erfolgt vorher doch ein Release,
kippt diese Prämisse und eine reguläre additive `ALTER TABLE`-Migration wird nötig; das ist dann
Sache des P2.1-Feinplans, nicht mehr dieses Plans.

**Verworfene Alternative:** Spalte bereits jetzt mit anlegen, weil es ohnehin dieselbe Datei ist.

### DD-3 (P1.3) — Argon2id-Parameter — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt, verschärft):** Explizite OWASP-Parameter, aber bewusst nicht
die leichteste der von OWASP im „Password Storage Cheat Sheet" gelisteten Argon2id-Konfigurationen,
sondern die **stärkste**, die OWASP dort selbst als Erstwahl nennt ("use this if you can afford the
memory"): `new Argon2PasswordEncoder(16, 32, 1, 47 * 1024, 1)` (Salt-Länge 16 Byte, Hash-Länge
32 Byte, Parallelität 1, Speicher 46 MiB, 1 Iteration). OWASP listet mehrere gleichwertig
akzeptierte Abstufungen (Speicher runter, Iterationen rauf); `m=46 MiB, t=1` ist darin die oberste,
speicherstärkste Empfehlung — stärker als die zuvor angesetzten `m=19 MiB, t=2`. Bleibt weiterhin
exakt innerhalb der in ADR-009 zitierten OWASP-Quelle, nur an deren oberem statt unterem Ende.

**Verworfene Alternativen:** `m=19 MiB, t=2` (OWASP-Minimalabstufung) sowie Spring Securitys
`Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` (`m=16 MiB, t=2`, unterschreitet sogar die
OWASP-Minimalabstufung).

### DD-4 (P1.3) — `PasswordEncoder`-Bean: einfach vs. delegierend — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** Ein einzelner `Argon2PasswordEncoder`-Bean (siehe DD-3), **kein**
`PasswordEncoderFactories.createDelegatingPasswordEncoder()`. ADR-009 hat sich bereits fest auf
Argon2id festgelegt; es gibt keine Alt-Hashes (Greenfield-Auth), für die Algorithmus-Agilität nötig
wäre. Ein `DelegatingPasswordEncoder` wäre eine spekulative Abstraktion ohne aktuellen Nutzen
(Projektregel: keine Features über das Angefragte hinaus).

**Alternative:** `DelegatingPasswordEncoder` mit nur `{argon2}` registriert. Erlaubt verlustfreie
spätere Migration auf einen anderen Algorithmus (Prefix-Tagging), aber das ist YAGNI, solange kein
zweiter Algorithmus geplant ist.

### DD-5 (P1.3) — Semantik von "E-Mail verfügbar" — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** "Verfügbar" = **kein `member`-Datensatz mit dieser E-Mail existiert** (Prüfung über
`MemberRepository`, nicht über `AccountRepository`). Begründung: `member.email` ist bereits
systemweit `UNIQUE` (ADR-012-Konsequenz); sobald irgendein `member` — mit oder ohne zugehörigem
`account` — diese E-Mail trägt, kann kein zweiter `member` (und damit kein zweiter `account`) sie
je verwenden. Ein rein auf `account`-Existenz geprüfter Check wäre sogar falsch: er würde
fälschlich "verfügbar" für eine E-Mail melden, die zwar noch keinen Account, aber bereits einen
`member`-Datensatz hat — der anschließende Setup-/Join-Versuch würde dann trotzdem an der
`member.email`-UNIQUE-Constraint scheitern (aktuell abgebildet über
`MemberAlreadyExistsException`, ausgelöst durch `DataIntegrityViolationException` in
`HouseholdSetupService`/`MemberManagementService`).

**Alternative:** Prüfung über `AccountRepository.existsById(...)` nach Join über `member.email`.
Semantisch näher an "hat schon einen Login", aber liefert wie beschrieben falsch-positive
"verfügbar"-Antworten und ist unnötig komplizierter (Join für denselben Effekt).

### DD-6 (P1.3) — Principal-Typ für `UserDetailsService` — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt):** Eigener, im `household`-Root-Package (öffentliche API des
Moduls) definierter Typ `AccountPrincipal implements UserDetails`, der `memberId`, `householdId`,
`isAdmin` und `email` (als Username) trägt. Dieser Typ wird von
`AccountUserDetailsService.loadUserByUsername(email)` zurückgegeben und ist die Grundlage für den
in P1.4 beschriebenen zweiten Anreicherungsschritt (siehe DD-7, Abschnitt "Claims/Principal-Kette").
Er lebt im `household`-Root-Package (siehe die Restrukturierung unter DD-8: `HouseholdAccessGuard`
liegt jetzt ebenfalls dort, nicht mehr in `core` — beide sind household-eigene Named Interfaces,
gelesen von anderen Modulen, analog zum bestehenden Muster der Event-Records wie `HouseholdDeleted`).

**Verworfene Alternative:** Spring Securitys eingebauten `org.springframework.security.core.userdetails.User`
mit `memberId`/`householdId`/`isAdmin` kodiert als `GrantedAuthority`-Strings (z. B.
`"HOUSEHOLD_" + householdId`, `"ROLE_ADMIN"`). Vermeidet eine eigene Klasse, ist aber unlesbar/
fehleranfällig (String-Parsing statt typisierter Felder) und passt schlechter zu AssertJ-
Testassertions.

### DD-7 (P1.4) — Zentrale Architekturfrage: Wer führt die PKCE/Token-Journey aus? — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt, nach externer Recherche):** Backend (Spring) führt die
gesamte PKCE/Token-Journey serverseitig aus; die SPA bekommt ausschließlich eine Session-Cookie.
Bestätigt durch vier unabhängige Quellen: OWASP Cheat Sheet Series (Session Management/HTML5
Security — keine Tokens in `localStorage`/`sessionStorage`, BFF/httpOnly-Cookie empfohlen), die
IETF-BCP "OAuth 2.0 for Browser-Based Apps", die offizielle Spring-Authorization-Server-Doku
(empfiehlt BFF explizit als Alternative zu einem Public Client) sowie mehrere unabhängige
Industriequellen (Curity Token-Handler-Pattern, Duende BFF Security Framework, ein konkretes
Spring-Security-BFF-Tutorial). Als zusätzliche Option wurde geprüft, ob stattdessen der SvelteKit-
Node-Server (statt Spring) die PKCE-Journey ausführen könnte — verworfen, weil das (a) eine
zusätzliche, dauerhaft zu betreibende Infrastrukturkomponente einführen würde (Kapitel 7 sieht das
Frontend explizit als rein statische, über Nginx ausgelieferte Dateien vor — dieselbe
KISS/QG3-Logik, mit der ADR-013 bereits Keycloak verworfen hat), (b) zwingend einen Token-Relay
zwischen SvelteKit-Node und Spring einführen würde (Spring müsste dann zusätzlich als echter
Resource-Server Bearer-Tokens validieren, statt wie hier nur Session-Cookies zu prüfen) und (c)
keinen UX-Vorteil bringt, den die geplante eigene, statisch ausgelieferte `/login`-Seite (siehe
Frontend-Plan, Entscheidung 3.1) nicht ohnehin schon hätte.

**Naheliegende, aber falsche Annahme:** Die SPA führt PKCE selbst durch (erzeugt
`code_verifier`/`code_challenge`, empfängt den Redirect mit `code`, tauscht ihn per `fetch` gegen
Tokens). Das widerspricht ADR-014 direkt: Tokens würden dabei transient in den JS-Kontext gelangen
(als Fetch-Response-Body), auch wenn sie danach sofort verworfen würden — genau die Angriffsfläche
(XSS-Exfiltration), die ADR-014 explizit vermeiden will.

**Empfehlung:** Die SPA führt **nichts** von PKCE/Code-Exchange selbst aus. Stattdessen nutzt das
Backend selbst Spring Securitys servletbasierten OAuth2-Client-Support (`oauth2Login()` aus
`spring-boot-starter-oauth2-client`), konfiguriert als Client gegen die **eigene, lokal eingebettete**
Authorization-Server-Instanz (self-referencing Client-Registration, `issuer-uri` zeigt auf sich
selbst). Das Backend durchläuft die komplette Authorization-Code+PKCE-Journey serverseitig selbst.
PKCE bleibt aktiv (Defense-in-Depth, auch wenn Client und Server im selben Prozess laufen). Das
Ergebnis — die normale, von Spring Security verwaltete `HttpSession` — **ist** die in ADR-014
beschriebene opake, httpOnly, SameSite Session-Cookie. Access-/Refresh-Token liegen ausschließlich
serverseitig (`OAuth2AuthorizedClientService`) und erreichen den Browser nie.

*Konsequenz für den Login-Einstiegspunkt:* Der Browser navigiert per **voller Seitennavigation**
(kein `fetch`) entweder direkt zu `/oauth2/authorization/{registrationId}` (Spring Securitys
eingebauter Redirect-Startpunkt für `oauth2Login()`) oder zu einer von der SPA gerenderten `/login`-
Seite mit einem echten HTML-`<form method="post">`, das gegen Spring Securitys Form-Login-
Processing-URL postet. Nach erfolgreichem Login leitet das Backend zurück in die SPA (z. B. `/app`),
die daraufhin `GET /me` aufruft. Kein eigener Callback-Controller nötig.

*Detail, das über die reine "nutze `oauth2Login()`"-Empfehlung hinausgeht (Claims-/Principal-Kette):*
Innerhalb des Backends laufen dabei **zwei unterschiedliche Authentifizierungen** nacheinander ab,
und das muss beim Bauen der `@PreAuthorize`-Grundlage für P1.6 berücksichtigt werden:

1. Das interne, vom Authorization Server verlangte Login (Formular gegen
   `AccountUserDetailsService` + `Argon2PasswordEncoder` + `DaoAuthenticationProvider`) authentifiziert
   den Nutzer *für den Authorization-Server-Endpunkt* `/oauth2/authorize`. Das Ergebnis ist ein
   `UsernamePasswordAuthenticationToken` mit `AccountPrincipal` (DD-6) als Principal.
2. Nach Code-Austausch überschreibt Spring Securitys `oauth2Login()`-Mechanismus den
   `SecurityContext` derselben Session mit einem `OAuth2AuthenticationToken`, dessen Principal ein
   `OidcUser` ist, gebaut aus den Claims des ID-Tokens. **Dieses zweite Principal ist es, das für
   alle nachfolgenden Business-API-Aufrufe (und damit für `@PreAuthorize` in P1.6) tatsächlich
   verwendet wird — nicht der `AccountPrincipal` aus Schritt 1.**

   Damit `memberId`/`householdId`/`isAdmin` an diesem zweiten Principal verfügbar sind, ohne die
   Claims doppelt zu pflegen, wird ein **eigener, clientseitiger `OidcUserService`-Bean** registriert,
   der beim Laden des `OidcUser` (Standard-Claim `sub` = E-Mail, siehe unten) per direktem
   `MemberRepository`-Zugriff (selbes Modul, kein Cross-Modul-Aufruf nötig, da beides im
   `household`-Modul liegt) genau einmal anreichert und einen `AccountOidcPrincipal implements
   OidcUser` mit `memberId()`/`householdId()`/`isAdmin()` zurückgibt. Das ist bewusst einfacher als
   die Alternative, die Claims bereits beim Token-Issuing per `OAuth2TokenCustomizer<JwtEncodingContext>`
   in das ID-Token einzubetten und sie dann rein aus dem Token zu lesen — letzteres würde einen
   zusätzlichen Customizer *und* einen entsprechenden Claim-Mapper erfordern, für denselben Effekt.
   `sub` wird dazu beim `AccountUserDetailsService.loadUserByUsername` auf die E-Mail gesetzt
   (Login-Username = E-Mail, siehe DD-6), sodass der `OidcUserService` per
   `memberRepository.findByEmail(sub)` anreichern kann.

*Klarstellung zur "Resource Server"-Rolle aus ADR-013:* Da der Browser laut ADR-014 nie ein
Bearer-Token besitzt, sondern nur die Session-Cookie, wird die Business-API in P1.5 über normale
**session-basierte Authentifizierung** abgesichert (der bereits im `HttpSession` liegende
`OAuth2AuthenticationToken` aus Schritt 2), **nicht** über `oauth2ResourceServer().jwt()`. Die in
ADR-013 benannte Resource-Server-Rolle ist mit dem aktuellen Funktionsumfang faktisch ungenutzte
Infrastruktur (kein Client außer dem eigenen Browser-BFF sendet je ein Bearer-Token an die
Business-API) — relevant erst, falls künftig ein Nicht-Browser-Client (native App, Drittintegration)
direkt gegen die API spricht. Für P1–P3 ist das nicht geplant und wird hier nicht gebaut.

**Alternative (verworfen, aber genannt, weil naheliegend):** SPA-getriebenes PKCE mit
Token-Exchange über einen dedizierten Backend-Proxy-Endpunkt (`POST /auth/token-exchange`, der die
SPA-seitig erzeugten PKCE-Parameter entgegennimmt und serverseitig gegen den Authorization Server
tauscht). Nachteile: mehr eigener Code (ein kompletter Zusatz-Controller nur für diesen einen
Zweck), die SPA muss `code_verifier` zumindest kurzzeitig JS-seitig halten (Session-Storage-Verbot!),
und der Zwischenschritt bringt keinen Sicherheitsgewinn gegenüber der Empfehlung — er verlagert nur
Komplexität von Spring Security (das den Standardfall bereits abdeckt) in projekteigenen Code.

### DD-8 (P1.6) — Access-Control-Baustein — ✅ entschieden

**Entscheidung (vom Nutzer bestätigt, restrukturiert wegen der neuen Modul-Abhängigkeitsregel — siehe
"Modul-Abhängigkeitsregel" vor den Design-Entscheidungen):** Ursprünglich war
`HouseholdAccessGuard` im `core`-Package vorgesehen (analog zu `CoreOptionalMapper`). Das würde aber
bedeuten, dass `core` seinerseits `HouseholdQuery`/`MemberQuery` aus `household` aufruft — verboten,
da `core` laut der neuen Regel niemanden aufrufen darf. Stattdessen: Ein wiederverwendbarer
`@Component("householdAccessGuard")` — Klasse `HouseholdAccessGuard` — liegt im
`household`-Root-Package (öffentliche API des Moduls, gleiche Ebene wie `AccountPrincipal`, DD-6),
als weitere Named Interface neben `HouseholdQuery`/`MemberQuery` (die er intern, also modul-eigen,
nutzt — keine Cross-Modul-Abhängigkeit an dieser Stelle mehr). Andere Module (`tasks`, `expenses`, …)
hängen dadurch von `household` ab — exakt das bereits durch ADR-011 etablierte, akzeptierte Muster
für Cross-Modul-Abfragen, keine neue Abhängigkeitsart. Er stellt zwei Methoden bereit, die aus
`@PreAuthorize`-SpEL-Ausdrücken heraus aufgerufen werden:

```java
public boolean isMember(UUID householdId, Authentication authentication) {
    var principal = (AccountOidcPrincipal) authentication.getPrincipal();
    return memberQuery.isMemberOfHousehold(principal.memberId(), householdId);
}

public boolean isAdminOfHousehold(UUID householdId, Authentication authentication) {
    var principal = (AccountOidcPrincipal) authentication.getPrincipal();
    return principal.isAdmin() && memberQuery.isMemberOfHousehold(principal.memberId(), householdId);
}

public boolean isSelf(UUID memberId, Authentication authentication) {
    var principal = (AccountOidcPrincipal) authentication.getPrincipal();
    return principal.memberId().equals(memberId);
}
```

Verwendung an den Controllern (Beispiel, `TasksApiDelegateImpl`):

```java
@PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")
@Override
public ResponseEntity<List<Task>> getTasks(UUID householdId) { ... }
```

*Wichtiger Kompromiss, der explizit benannt werden muss:* `@PreAuthorize`-SpEL-Bean-Referenzen
(`@householdAccessGuard.…`) erzeugen **keine** Java-Compile-Time-Abhängigkeit zwischen dem
aufrufenden Modul (`tasks`, `expenses`, …) und `household` — Spring löst den Bean-Namen zur
Laufzeit über den `ApplicationContext` auf. Das bedeutet: Spring Moduliths
`ApplicationModules.verify()` (aktuell ohnehin noch nicht eingerichtet, TD1) würde diese Kopplung
**nicht** erkennen können, selbst wenn TD1 später behoben wird — obwohl die Abhängigkeitsrichtung
selbst (Modul → `household`) korrekt und regelkonform ist, bleibt sie durch den SpEL-Umweg
unverifiziert. Das ist ein akzeptabler Kompromiss für diesen Plan (die Kopplung ist schmal — zwei
Boolean-Methoden — und der Zugriffskontroll-Mechanismus soll bewusst nicht in jedem Modul einzeln
nachgebaut werden), sollte aber bei der TD1-Behebung als bekannte Lücke dokumentiert werden (z. B.
ein Kommentar an der `HouseholdAccessGuard`-Klasse, der auf die SpEL-Verwendungsstellen hinweist).

`MemberQuery` wird um eine neue Methode erweitert:

```java
boolean isMemberOfHousehold(UUID memberId, UUID householdId);
```

implementiert über eine neue abgeleitete Repository-Methode
`MemberRepository.existsByIdAndHouseholdId(UUID id, UUID householdId)` (Spring-Data-JDBC-Deriving,
kein `@Query` nötig) — effizienter als der bestehende Umweg über
`findMemberIdsByHouseholdId(...).contains(...)`.

**Alternative (verworfen):** Manuelle `if (!memberQuery.isMemberOfHousehold(...)) throw ...`-Prüfung
am Anfang jeder Service-Methode. Funktional gleichwertig, aber ~15 Stellen mit dupliziertem
Boilerplate statt einer deklarativen Annotation; erschwert zudem den in P1.6 vorgesehenen
parametrisierten Integrationstest (der genau gegen den HTTP-Layer, nicht gegen einzelne
Service-Methoden, testen soll).

### DD-9 (P1.4) — Persistenz des `RegisteredClient`

Kein "offener" Punkt im Sinne einer Nutzerentscheidung (der Auftrag verlangt Flyway-Tabellen
explizit), aber ein Implementierungsdetail: Der `RegisteredClient` für die SPA wird per
`JdbcRegisteredClientRepository` in `oauth2_registered_client` gehalten. Da es nur einen einzigen,
statischen Client gibt (die SPA) und `redirect-uri`/`client-id` je nach Self-Hosting-Umgebung
unterschiedlich sein können, wird der Client beim Start per `ApplicationRunner` idempotent
"upserted" (Lookup per festem `clientId`, Insert falls nicht vorhanden, sonst unverändert lassen) —
nicht bei jedem Start neu erzeugt/dupliziert. Konfigurationswerte (`redirect-uri`, und seit der
DD-7-Korrektur zusätzlich `client-secret`) kommen aus `application.yaml`
(`librehousehold.security.oauth2-client.redirect-uri`/`.client-secret`), damit Selbst-Hoster nur
diese Properties setzen müssen (passt zu QG3). Das `client-secret` wird beim Seeding wie jedes
Passwort über den in P1.3.3 definierten `PasswordEncoder` gehasht abgelegt (Spring Authorization
Server erwartet den Secret-Wert in `RegisteredClient.clientSecret` bereits gehasht, nicht im
Klartext) — nie im Klartext geloggt oder ausgegeben.

## Aufgaben je P-Punkt

> Hinweis zu neuen Dependencies: `spring-boot-starter-security`,
> `spring-boot-starter-oauth2-client`, `spring-boot-starter-oauth2-authorization-server` existieren
> aktuell nicht in `backend/pom.xml`. Per globaler Vorgabe ("Always ask before adding a new
> dependency") muss die Umsetzung diese drei explizit beim Nutzer anfragen, bevor sie hinzugefügt
> werden — auch wenn sie architektonisch durch ADR-013 bereits vorausgesetzt sind.

### P1.3 — `AccountRegistration` im `household`-Modul

**Ziel:** Argon2id-Hashing bei Setup & Join, `UserDetailsService` auf Member/Account,
E-Mail-Verfügbarkeitsprüfung, Datenmodell (`account`-Tabelle + Spring-Authorization-Server-Tabellen).

#### P1.3.1 — Kompilierstatus vor Beginn verifizieren

Vor jeder Änderung: `./mvnw clean compile` im `backend/`-Verzeichnis laufen lassen, um zu prüfen, ob
`HouseholdSetupServiceIT`/weitere Stellen bereits gegen den (durch P1.1 geänderten) generierten
`HouseholdSetup`-Konstruktor nicht mehr kompilieren (siehe Ist-Zustand-Befund oben). Falls ja: wird
im Zuge von P1.3.5 mitkorrigiert, da dort ohnehin dieselben Testfälle um `LocalRegistration`
erweitert werden.

#### P1.3.2 — Datenmodell: `account`-Tabelle

Kein klassischer TDD-Rot-Grün-Zyklus (reine Schema-Migration), aber pflichtgemäß über die
Integrationstests aus P1.3.4/P1.3.5 abgesichert (ein Migrationsfehler lässt jeden `@DataJdbcTest`/
`@SpringBootTest` mit Testcontainers sofort beim Kontextstart fehlschlagen — das *ist* die rote
Phase für Migrationscode).

Neue Datei `backend/src/main/resources/db/migration/household/V2__local_auth.sql`:

```sql
CREATE TABLE account
(
    member_id     UUID PRIMARY KEY REFERENCES member (id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL
);
```

(siehe DD-1, DD-2 für die Begründung der beiden Design-Entscheidungen, die hier einfließen —
`email_verified` kommt bewusst erst bei P2.1 hinzu)

**Hinweis zur Datei (siehe DD-2/DD-9-Klarstellung):** Da es noch kein Release gibt, ist dies bewusst
die **einzige** Migrationsdatei für den gesamten lokalen-Auth-Datenbestand von Phase 1 — P1.4 fügt
die Spring-Authorization-Server-Tabellen (`oauth2_registered_client`, `oauth2_authorization`,
`oauth2_authorization_consent`) per Nachtrag in **dieselbe** `V2__local_auth.sql` ein, statt eine
eigene Migrationsdatei anzulegen (siehe P1.4.2). Der Dateiname wurde deshalb bereits jetzt allgemein
(`local_auth`, nicht `create_account`) gewählt, statt ihn nach P1.4 umzubenennen.

Neue Entity `backend/src/main/java/eu/wiegandt/librehousehold/household/model/AccountEntity.java`
nach dem `UserPreferencesEntity`-Vorbild (Klasse statt Record, `member_id` als `@Id`, `markExisting()`
+ `isNew`-Flag, da Updates — z. B. künftiges `changePassword` — nötig sein werden).

#### P1.3.3 — Argon2id-`PasswordEncoder`-Bean

Kein eigener TDD-Zyklus nötig (Bean-Deklaration, keine eigene Logik) — wird transitiv durch P1.3.4
getestet. `@Configuration`-Klasse (z. B. `eu.wiegandt.librehousehold.household.config
.PasswordEncoderConfig` oder zentral, falls andere Module ihn direkt injizieren wollen — Arc42 Kap. 8
erlaubt direktes Injizieren von Framework-Typen aus jedem Modul, daher genügt ein einzelner globaler
Bean, keine Named-Interface-Kapselung):

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 47 * 1024, 1);
}
```

#### P1.3.4 — `AccountService.createAccount` (Kernlogik, TDD)

**Testklasse:** `eu.wiegandt.librehousehold.household.service.AccountServiceTest` (Unit,
`@ExtendWith(MockitoExtension.class)`).

- **Rot:** Testmethode `createAccount_rawPassword_storesArgon2idHash` existiert, `AccountService`
  existiert noch nicht → Kompilierfehler/roter Test.
  ```java
  @Test
  void createAccount_rawPassword_storesArgon2idHash() {
      // given
      var memberId = UUID.randomUUID();
      var rawPassword = "correct horse battery staple";
      var hashedPassword = "$argon2id$v=19$m=19456,t=2,p=1$...";
      var expectedAccount = new AccountEntity(memberId, hashedPassword, false);
      doReturn(hashedPassword).when(passwordEncoder).encode(rawPassword);

      // when
      accountService.createAccount(memberId, rawPassword);

      // then
      var captor = ArgumentCaptor.forClass(AccountEntity.class);
      verify(accountRepository).save(captor.capture());
      assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expectedAccount);
  }
  ```
  Mocks: `AccountRepository`, `PasswordEncoder` (beide direkte Dependencies von `AccountService`,
  laut TDD-Regel zulässig zu mocken).
- **Grün:** `AccountService` mit Konstruktor-Injection von `AccountRepository`+`PasswordEncoder`
  anlegen, `createAccount(UUID memberId, String rawPassword)` implementieren
  (`accountRepository.save(new AccountEntity(memberId, passwordEncoder.encode(rawPassword),
  false))`).
- **Refactor:** keiner erwartet.

Zweiter Testfall (negativ, `@Nested` sobald ≥3 Methoden in dieser Klasse existieren — aktuell reicht
eine flache Methode, da vorerst nur ein Verhalten getestet wird; kein Negativpfad nötig, da
`createAccount` selbst keine Fehlerbedingung hat — die Duplikat-Prüfung läuft über die aufrufende
Seite, siehe P1.3.6).

**IT-Testklasse:** `AccountServiceIT` (`@SpringBootTest(webEnvironment=NONE) +
@Import(TestcontainersConfiguration.class)`, echter `Argon2PasswordEncoder`-Bean aus dem
Kontext, kein Mock — Zweck ist genau die Verifikation, dass real gehasht statt Klartext gespeichert
wird):

```java
@Test
void createAccount_validPassword_persistsHashedNotPlaintextPassword() {
    // given
    var household = Instancio.create(Household.class);
    var member = Instancio.create(Member.class);
    setupService.setupHousehold(new HouseholdSetup(household, member, someLocalRegistration));
    var rawPassword = "correct horse battery staple";

    // when
    accountService.createAccount(member.getId(), rawPassword);

    // then
    assertThat(accountRepository.findById(member.getId()))
            .hasValueSatisfying(account -> {
                assertThat(account.getPasswordHash()).startsWith("$argon2id$");
                assertThat(passwordEncoder.matches(rawPassword, account.getPasswordHash())).isTrue();
            });
}
```

(Hinweis: setzt bereits einen existierenden `member`-Datensatz voraus, wegen der FK aus DD-1 —
inline im `given`-Block über `setupService.setupHousehold(...)` erzeugt, kein `@BeforeEach`.)

#### P1.3.5 — Wiring in `HouseholdSetupService`/`MemberManagementService`

**Testklasse-Erweiterung:** `HouseholdSetupServiceIT`, neue Testmethode
`setupHousehold_validSetup_persistsHashedPasswordInAccountTable`.

- **Rot:** Test ruft `service.setupHousehold(new HouseholdSetup(household, member,
  localRegistration))` auf (3-Arg-Konstruktor, behebt gleichzeitig den in P1.3.1 festgestellten
  Kompilierstand alter Tests) und erwartet einen `AccountEntity`-Eintrag für `member.getId()`. Schlägt
  fehl, weil `HouseholdSetupService.setupHousehold` `localRegistration` aktuell komplett ignoriert.
- **Grün:** In `HouseholdSetupService.setupHousehold` nach erfolgreichem `memberRepository.save(...)`
  einen `AccountService.createAccount(savedMember.getId(), setup.getLocalRegistration()
  .getPassword())`-Aufruf ergänzen (`AccountService` per Konstruktor injizieren), innerhalb derselben
  `@Transactional`-Methode (Setup + Account-Anlage müssen atomar sein — sonst könnte ein Member ohne
  Account entstehen, falls das Hashing/die Account-Anlage fehlschlägt).
- **Refactor:** keiner erwartet; alle bestehenden 2-Arg-`new HouseholdSetup(...)`-Aufrufe in
  `HouseholdSetupServiceIT` werden im selben Zug auf 3 Argumente korrigiert (mit einem Instancio-
  erzeugten `LocalRegistration`), da sie sonst nicht mehr kompilieren.

Analog für `MemberManagementService.joinHousehold`: neue Testmethode in
`MemberManagementServiceIT`, `@Nested class joinHousehold`:
`joinHousehold_validRegistration_persistsHashedPasswordInAccountTable`, gleiches Rot-Grün-Muster,
Implementierung ergänzt den `AccountService.createAccount(...)`-Aufruf nach dem
`memberRepository.save(...)` in `joinHousehold`.

#### P1.3.6 — E-Mail-Verfügbarkeit — ⚠️ überholt, siehe finale Fassung unten

**Diese Unterpunkt-Beschreibung ist durch mehrere Review-Runden überholt.** Ursprünglich war hier
`AccountService.isEmailAvailable` + eine eigene `AccountsApiDelegateImpl` (Tag `accounts`)
vorgesehen. Nach Diskussion wurde erkannt, dass "existiert bereits ein Member mit dieser E-Mail?"
eine reine `member`/`household`-Fachfrage ist (unabhängig von der Authentifizierungsart, siehe auch
P3), nicht Teil der Account-Domäne. **Finaler Stand:**

- `boolean isEmailAvailable(String email)` lebt auf `MemberQuery`/`MemberManagementService`
  (nicht mehr auf `AccountService`), implementiert über das bereits vorhandene
  `MemberRepository.existsByEmail`.
- `api/openapi.yml`: der Endpunkt wurde von `GET /accounts/availability` (Tag `accounts`) auf
  `GET /members/availability` (Tag `members`) verschoben; der jetzt leere `accounts`-Tag wurde aus
  der Spec entfernt.
- Die separate Klasse `AccountsApiDelegateImpl` entfällt; `checkEmailAvailability` wird stattdessen
  eine weitere Methode auf der bereits bestehenden `MembersApiDelegateImpl`.
- `AccountService` hat dadurch **keine** Abhängigkeit zu `MemberQuery` mehr (löst den zuvor per
  `@Lazy` überbrückten Zirkelbezug zwischen `AccountService` und `MemberManagementService` an der
  Wurzel auf, statt ihn nur zu verdecken).

`checkEmailAvailability(String email)` wird eine weitere Methode auf der bereits bestehenden
`MembersApiDelegateImpl`, ruft `memberQuery.isEmailAvailable(email)` (oder die konkrete
`MemberManagementService`-Methode) auf und mapped auf `EmailAvailability`. Kein eigener
Service-Test nötig für den Delegate selbst (dünner Adapter, analog zu den bestehenden
`*ApiDelegateImpl`-Klassen).

#### P1.3.7 — `AccountUserDetailsService` — ⚠️ überholt, siehe finale Fassung unten

**Diese Unterpunkt-Beschreibung ist überholt.** Ursprünglich war ein `@Query`-JOIN über
`member`↔`account` (`AccountRepository.findAccountDetailsByEmail`) vorgesehen, der Ergebnis in eine
`AccountDetailsProjection` liefert, aus der ein `AccountPrincipal` mit `memberId`/`householdId`/
`isAdmin`/`email`/`passwordHash` gebaut wird. Nach Analyse, wofür `AccountPrincipal` tatsächlich
gebraucht wird (nur für den internen Login-Schritt gegen den eingebetteten Authorization Server,
siehe DD-7 — `memberId`/`householdId`/`isAdmin` werden dabei nie gelesen und in P1.4.5 ohnehin
erneut und unabhängig aus `MemberRepository.findByEmail` geladen), wurde das vereinfacht:

- **`AccountPrincipal`** trägt nur noch `email` und `passwordHash` (`implements UserDetails`,
  `getUsername()`→`email`, `getPassword()`→`passwordHash`).
- **`AccountDetailsProjection` entfällt vollständig.**
- **`AccountRepository.findAccountDetailsByEmail` (der `@Query`-JOIN) entfällt vollständig** —
  `AccountRepository` bleibt ein reines `CrudRepository<AccountEntity, UUID>` ohne eigene Queries.
- **`AccountUserDetailsService.loadUserByUsername(email)`** löst stattdessen zweistufig auf, ohne
  jemals einen SQL-JOIN über zwei Aggregate zu bilden (konsistent mit Spring Data JDBCs
  "ein Repository = ein Aggregat"-Prinzip):
  1. `memberId = memberQuery.findMemberIdByEmail(email)` (neue Methode auf `MemberQuery`/
     `MemberManagementService`, delegiert an eine neue abgeleitete Query auf `MemberRepository`) —
     `UsernameNotFoundException`, falls kein Member mit dieser E-Mail existiert.
  2. `account = accountRepository.findById(memberId)` (Standard-`CrudRepository`-Methode, reiner
     PK-Zugriff) — `UsernameNotFoundException`, falls der Member (noch) keinen Account hat.
  3. `new AccountPrincipal(email, account.passwordHash())` — direkte Konstruktion, kein
     MapStruct-Mapper nötig (kein Feld-für-Feld-Mapping eines übereinstimmenden Quellobjekts,
     sondern Komposition aus zwei unabhängigen Werten, analog zu
     `AccountService.createAccount`s `new AccountEntity(memberId, passwordEncoder.encode(...))`).
- Ein bereits angelegter `AccountPrincipalMapper`/`AccountPrincipalMapperTest` aus einer früheren
  Runde entfällt ersatzlos (siehe oben, kein Mapping-Fall mehr).

**IT-Testklasse:** `AccountUserDetailsServiceIT`, Happy-Path-Test
`loadUserByUsername_memberWithAccount_returnsAccountPrincipalWithPasswordHash` gegen echtes
Testcontainers-Postgres (deckt beide Repository-Zugriffe ab, kein JOIN mehr zu verifizieren).

### P1.4 — Spring Authorization Server Setup

**Ziel:** Filter-Chain-Reihenfolge, Registered Client fürs SPA, PKCE, Token-Settings, Login gegen
lokale Accounts. Siehe DD-7 für die zentrale, zu bestätigende Architekturentscheidung — die folgenden
Aufgaben setzen diese Entscheidung als bereits getroffen voraus.

#### P1.4.1 — Dependencies (Nutzerfreigabe einholen)

`spring-boot-starter-security`, `spring-boot-starter-oauth2-client`,
`spring-boot-starter-oauth2-authorization-server` zu `backend/pom.xml` hinzufügen (Versionen über
den bereits importierten `spring-boot-starter-parent:4.1.0` verwaltet, keine explizite Version
nötig). **Vor dem Hinzufügen beim Nutzer nachfragen** (globale Dependency-Regel).

#### P1.4.2 — Spring-Authorization-Server-Tabellen (Flyway, kein Schema-Autogen)

**Entscheidung (vom Nutzer bestätigt, siehe DD-2-Klarstellung):** Keine eigene, neue Migrationsdatei.
Da es noch kein Release gibt, werden die folgenden drei Tabellen als Nachtrag an die bereits in
P1.3.2 angelegte `backend/src/main/resources/db/migration/household/V2__local_auth.sql` angehängt
(gleiche Datei, gleiches `household`-Flyway-Verzeichnis wie `account` — bewusst nicht in einem
separaten `__root`-Verzeichnis, um nicht zwei Migrationsdateien für denselben, noch unveröffentlichten
Auth-Datenbestand zu haben). Tabellennamen und Spaltennamen von `oauth2_registered_client` sind über die offizielle
Spring-Authorization-Server-Referenz (`JdbcRegisteredClientRepository`) verifiziert:

```sql
CREATE TABLE oauth2_registered_client
(
    id                            VARCHAR(100)                            NOT NULL,
    client_id                     VARCHAR(100)                            NOT NULL,
    client_id_issued_at           TIMESTAMP WITH TIME ZONE DEFAULT NOW()  NOT NULL,
    client_secret                 VARCHAR(200) DEFAULT NULL,
    client_secret_expires_at      TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    client_name                   VARCHAR(200)                           NOT NULL,
    client_authentication_methods VARCHAR(1000)                          NOT NULL,
    authorization_grant_types     VARCHAR(1000)                          NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000)                          NOT NULL,
    client_settings                VARCHAR(2000)                         NOT NULL,
    token_settings                 VARCHAR(2000)                         NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE oauth2_authorization ( ... );          -- siehe Hinweis unten
CREATE TABLE oauth2_authorization_consent ( ... );  -- siehe Hinweis unten
```

**Wichtiger Umsetzungshinweis:** Die Spaltenliste von `oauth2_authorization` (>30 Spalten,
Token-Werte/Metadaten für Authorization-Code/Access-/Refresh-/ID-Token/Device-/User-Code) und von
`oauth2_authorization_consent` (`registered_client_id`, `principal_name`, `authorities`,
zusammengesetzter Primärschlüssel) sollten bei der Umsetzung **nicht aus dem Gedächtnis abgetippt**,
sondern wörtlich aus den offiziellen, mit der exakten `spring-security-oauth2-authorization-server`-
Version ausgelieferten Schema-Dateien übernommen werden
(`org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql` bzw.
`.../oauth2-authorization-consent-schema.sql`, beide im Dependency-Jar enthalten und per
Context7/offizieller Doku nachschlagbar, wie im Auftrag gefordert). Grund: Diese Spaltenliste ändert
sich gelegentlich zwischen Minor-Versionen (z. B. neue Device-Code- oder PAR-Felder); ein manuell
nachgebauter DDL-Text riskiert Drift gegenüber der tatsächlich verwendeten Bibliotheksversion und
würde erst zur Laufzeit mit einer kryptischen SQL-Exception aus `JdbcOAuth2AuthorizationService`
auffallen. Datentypen werden beim Übernehmen auf PostgreSQL angepasst (`VARCHAR`→bleibt, `BLOB`
(sofern vorhanden) → `TEXT`, `TIMESTAMP` → `TIMESTAMP WITH TIME ZONE`, konsistent mit dem restlichen
Schema, siehe `event_publication`).

**TDD-Einordnung:** Reine Schema-Migration ohne eigene Logik — abgesichert durch das erfolgreiche
Hochfahren des `@SpringBootTest`-Kontexts in P1.4.4 (roter Fehlschlag bei fehlerhaftem SQL ist ein
Kontextstart-Fehler, keine Testassertion).

#### P1.4.3 — `RegisteredClient`-Seeding

**Testklasse:** `RegisteredClientSeederTest` (Unit, `RegisteredClientRepository` gemockt) oder als
IT direkt gegen `JdbcRegisteredClientRepository` mit Testcontainers (bevorzugt, da die Idempotenz —
"beim zweiten Start kein Duplikat" — eigentlich eine Datenbankfrage ist):

**Testklasse:** `RegisteredClientSeederIT`.

```java
@Test
void seed_calledTwice_doesNotDuplicateClient() {
    // given
    var redirectUri = "https://household.example.com/login/oauth2/code/spa-backend-client";

    // when
    seeder.seed(redirectUri);
    seeder.seed(redirectUri);

    // then
    assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM oauth2_registered_client WHERE client_id = ?",
            Integer.class, "spa-backend-client")).isEqualTo(1);
}
```

- **Rot:** `RegisteredClientSeeder` existiert nicht.
- **Grün:** `ApplicationRunner`/eigene Klasse `RegisteredClientSeeder`, die per
  `registeredClientRepository.findByClientId("spa-backend-client")` prüft und nur bei Abwesenheit
  ein `RegisteredClient` anlegt mit:
  - `ClientAuthenticationMethod.CLIENT_SECRET_BASIC` (**confidential** Client, nicht `NONE`) — siehe
    Korrektur unten.
  - `AuthorizationGrantType.AUTHORIZATION_CODE` **und** `AuthorizationGrantType.REFRESH_TOKEN`.
  - dem konfigurierten `redirect-uri`.
  - `ClientSettings.builder().requireProofKey(true).build()` (PKCE bleibt zusätzlich Pflicht, siehe
    RED1/PKCE1 — unabhängig von der Client-Authentifizierungsmethode).
- **Refactor:** keiner erwartet.

**Korrektur (vom Nutzer bestätigt, nach Recherche zu DD-7):** Ursprünglich war hier
`ClientAuthenticationMethod.NONE` (Public Client, kein Secret) mit der Begründung geplant, die
Vertraulichkeit ergebe sich schon aus der serverseitigen Ausführung. Das ist laut der offiziellen
Spring-Authorization-Server-Dokumentation falsch: *"Spring Authorization Server will not issue
refresh tokens for a public client. We recommend the backend for frontend (BFF) pattern as an
alternative to exposing a public client."* — ein Public Client bekommt unabhängig davon, wo er
läuft, grundsätzlich keine Refresh-Tokens ausgestellt. Da unser Backend ein Secret sicher halten
kann (Server-Prozess, nie im Browser), wird der Client stattdessen als **confidential** registriert:
Secret aus einer Konfigurations-Property (z. B. `librehousehold.security.oauth2-client.client-secret`,
per `PasswordEncoder` gehasht abgelegt wie jedes andere Secret bei Spring Authorization Server) statt
hartkodiert. PKCE bleibt trotzdem zusätzlich aktiv (Defense-in-Depth, von der Client-
Authentifizierungsmethode unabhängig konfigurierbar).

#### P1.4.4 — `SecurityFilterChain`-Konfiguration (Filter-Chain-Reihenfolge)

**Paketwahl (Restrukturierung wegen der Modul-Abhängigkeitsregel):** Diese Konfiguration lebt in
`eu.wiegandt.librehousehold.config.SecurityConfig` — im **Root-Package** der Anwendung, nicht in
`core` und nicht in `household`. Begründung: Die Beans referenzieren zwangsläufig
`household.AccountUserDetailsService`/`household.AccountPrincipal` (P1.3) — läge diese Klasse in
`core`, wäre das eine verbotene `core → household`-Abhängigkeit (`core` darf laut Vorgabe niemanden
aufrufen). Das Root-Package ist die Composition-Root der Anwendung (enthält bereits die
`@SpringBootApplication`-Klasse) und unterliegt keiner Spring-Modulith-Modulgrenze — reine
Verdrahtungs-/Konfigurationsklassen, die mehrere Module zusammenstecken, gehören dorthin, nicht in
ein fachliches Modul.

Zwei `SecurityFilterChain`-Beans, analog zum offiziellen Spring-Authorization-Server-Referenzmuster
("Default Security Configuration" mit zwei Chains), aber Chain 2 um `oauth2Login()` (statt reinem
`formLogin()`) erweitert:

```java
@Bean
@Order(1)
SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();
    http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
        .with(authorizationServerConfigurer, (as) -> as.oidc(Customizer.withDefaults()))
        .exceptionHandling((ex) -> ex.defaultAuthenticationEntryPointFor(
                new LoginUrlAuthenticationEntryPoint("/login"),
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
    return http.build();
}

@Bean
SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests((authorize) -> authorize
                .requestMatchers("/login", "/household/setup", "/invite/**",
                                  "/members/availability").permitAll()
                .anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .oauth2Login((login) -> login.userInfoEndpoint((userInfo) ->
                userInfo.oidcUserService(accountOidcUserService())));
    return http.build();
}
```

(Die konkreten `permitAll()`-Pfade und CSRF/CORS-Konfiguration werden erst in P1.5 final
ausgearbeitet — hier nur so weit nötig, um die Filter-Chain-Reihenfolge testbar zu machen. Chain 1
matched ausschließlich AS-Endpunkte (`/oauth2/**`, `/.well-known/**`, `/connect/**`) über
`getEndpointsMatcher()` und hat per `@Order(1)` Vorrang; Chain 2 fängt alles andere.)

**Testklasse:** `AuthorizationServerConfigurationIT`
(`@SpringBootTest(webEnvironment=RANDOM_PORT)` + Testcontainers + `TestRestTemplate` mit
deaktiviertem Redirect-Following, um jeden Schritt der Weiterleitungskette einzeln zu prüfen).

```java
@Test
void oauth2Authorize_noSession_redirectsToLogin() {
    // given
    var authorizeUri = "/oauth2/authorize?response_type=code&client_id=spa-backend-client"
            + "&redirect_uri=" + redirectUri + "&code_challenge=" + codeChallenge
            + "&code_challenge_method=S256&scope=openid";

    // when
    var response = restTemplate.getForEntity(authorizeUri, Void.class);

    // then
    assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/login");
}
```

Weitere Testmethoden (gleiche Klasse, `@Nested` je nach Zwischenschritt sinnvoll):
`login_validAccountCredentials_redirectsBackToOriginalAuthorizeRequest`,
`oauth2Authorize_afterLogin_redirectsToRegisteredRedirectUriWithCode`,
`loginCallback_validCode_setsSessionCookieAndNoTokenInResponseBody` (Assertion: Response-Body
enthält **nicht** die Substrings `"access_token"`/`"refresh_token"` — das ist der direkte,
testbare Nachweis für ADR-014 "Tokens verlassen das Backend nie"),
`getMe_withoutSession_returns401`, `getMe_afterSuccessfulLogin_returnsCurrentUser` (Ende-zu-Ende,
setzt `SessionApiDelegateImpl`/`AccountOidcUserService` aus P1.4.5/P1.4.6 voraus — wird ggf. erst
nach deren Umsetzung grün, was in TDD zulässig ist, da es sich um einen End-to-End-Test handelt, der
bewusst mehrere Teilschritte zusammen absichert).

- **Rot:** Alle Testmethoden schlagen fehl, da weder `SecurityFilterChain` noch AS-Konfiguration
  existieren (aktuell: jeder Request liefert 200/404 statt Redirect/401, da nichts geschützt ist).
- **Grün:** Konfiguration wie oben, inklusive `AuthorizationServerSettings`-Bean (Issuer-URI aus
  Konfiguration) und `TokenSettings` (siehe P1.4.6).
- **Refactor:** Sobald beide Chains stehen, prüfen, ob `permitAll()`-Liste und
  AS-Endpunkt-Matcher-Overlap sauber getrennt sind (keine versehentliche doppelte Behandlung eines
  Pfads durch beide Chains).

#### P1.4.5 — `AccountOidcUserService` (Principal-Anreicherung, siehe DD-7)

**Testklasse:** `AccountOidcUserServiceTest` (Unit, `OidcUserService`-Delegate gemockt oder
`loadUser`-Kern isoliert getestet — je nachdem, wie viel Spring-OIDC-Infrastruktur sich sinnvoll
mocken lässt; falls `OidcUserRequest` zu aufwendig zu konstruieren ist, den Anreicherungsschritt in
eine separate, leicht testbare Methode extrahieren, die nur `OidcUser` + `MemberRepository`
kombiniert — siehe AGENTS.md "wenn Extraktion für Testbarkeit nötig ist, vorher fragen": diese
Extraktion sollte vor der Umsetzung mit dem Nutzer kurz abgestimmt werden, falls sie über eine
reine Delegationsmethode hinausgeht).

```java
@Test
void enrich_oidcUserWithMemberEmail_returnsAccountOidcPrincipalWithMemberData() {
    // given
    var email = "max@example.com";
    var memberId = UUID.randomUUID();
    var householdId = UUID.randomUUID();
    var oidcUser = /* OidcUser-Testinstanz mit sub=email */;
    var member = new MemberEntity(memberId, "Max", email, null, householdId, true);
    var expectedPrincipal = new AccountOidcPrincipal(oidcUser, memberId, householdId, true);
    doReturn(Optional.of(member)).when(memberRepository).findByEmail(email);

    // when
    var result = accountOidcUserService.enrich(oidcUser);

    // then
    assertThat(result).usingRecursiveComparison().isEqualTo(expectedPrincipal);
}
```

- **Rot:** `AccountOidcUserService`/`AccountOidcPrincipal` existieren nicht.
- **Grün:** `MemberRepository` um abgeleitete Methode `Optional<MemberEntity> findByEmail(String
  email)` ergänzen (falls nicht bereits vorhanden — aktuell nicht vorhanden, nur `updateEmail` als
  Schreiboperation existiert); `AccountOidcPrincipal implements OidcUser` (delegiert die meisten
  `OidcUser`-Methoden an das umschlossene `OidcUser`, ergänzt `memberId()`/`householdId()`/
  `isAdmin()`).
- **Refactor:** keiner erwartet.

#### P1.4.6 — Token-Settings

Kein eigener TDD-Test nötig (reine Konfigurationswerte) — Teil der `RegisteredClient`-Konfiguration
aus P1.4.3, verifiziert implizit über `AuthorizationServerConfigurationIT`. Empfehlung für die
Werte: `accessTokenTimeToLive` kurz (z. B. 10 Minuten, Standard), `refreshTokenTimeToLive` länger
(z. B. 30 Tage, `reuseRefreshTokens(false)` für Rotation), `TokenSettings.builder()
.accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)` (JWT, da Resource-Server-Rolle laut ADR-013
grundsätzlich vorgesehen ist, auch wenn sie laut DD-7 aktuell nicht aktiv genutzt wird — ein
JWT-Format kostet nichts Zusätzliches und hält die Tür für spätere direkte Resource-Server-Nutzung
offen, ohne dass das YAGNI verletzt, da es keine zusätzliche Implementierung erfordert, nur eine
Konfigurationswahl ist).

#### P1.4.7 — `SessionApiDelegateImpl` (`/me`, `/logout`)

**Testklasse:** `SessionApiDelegateImplTest` (Unit) — testet nur das Mapping von
`AccountOidcPrincipal`/`Authentication` auf `CurrentUser`, keine eigene Fachlogik.

```java
@Test
void getCurrentUser_authenticatedPrincipal_returnsCurrentUserWithHouseholdIdFromPrincipal() {
    // given
    var member = Instancio.create(Member.class);
    var householdId = UUID.randomUUID();
    var principal = new AccountOidcPrincipal(someOidcUser, member.getId(), householdId, true);
    var authentication = new OAuth2AuthenticationToken(principal, List.of(), "spa-backend-client");
    doReturn(member).when(memberQuery).getMember(member.getId()); // oder passende bestehende Abfrage
    var expected = new CurrentUser(member, householdId, defaultPreferences);

    // when
    var result = delegate.getCurrentUser(authentication);

    // then
    assertThat(result.getBody()).usingRecursiveComparison().isEqualTo(expected);
}
```

(Exakte Signatur hängt vom generierten `SessionApiDelegate`-Interface ab — falls der generierte
Delegate keinen `Authentication`-Parameter automatisch injiziert, wird der Wert stattdessen über
`SecurityContextHolder.getContext().getAuthentication()` im Delegate gelesen; das ist ein
Implementierungsdetail, das erst nach `./mvnw clean compile` mit dem tatsächlich generierten
Interface final entschieden wird.)

Für `logout`: **Testklasse** `LogoutHandlerTest`/Erweiterung derselben Delegate-Testklasse.
Wichtig laut ADR-014: Logout muss aktiv das Token widerrufen, nicht nur die Session invalidieren.
Umsetzung über einen `LogoutHandler`, der `OAuth2AuthorizedClientService.removeAuthorizedClient(...)`
aufruft bzw. den Authorization Servers Revocation-Endpunkt (`/oauth2/revoke`) intern anspricht, bevor
`HttpSession.invalidate()` erfolgt.

```java
@Test
void logout_authenticatedSession_revokesAuthorizedClientBeforeInvalidatingSession() {
    // given
    var authentication = /* wie oben */;

    // when
    logoutHandler.logout(request, response, authentication);

    // then
    verify(authorizedClientService).removeAuthorizedClient("spa-backend-client",
            authentication.getName());
}
```

- **Rot/Grün/Refactor:** analog zu den vorherigen Mustern.

### P1.5 — Resource-Server-/BFF-Absicherung bestehender Endpunkte

**Ziel:** Cookie-Session, CSRF, CORS für alle bestehenden Endpunkte; explizite `permitAll`-Ausnahmen.

#### P1.5.1 — `permitAll`-Liste finalisieren

Baut auf der in P1.4.4 begonnenen `defaultSecurityFilterChain`-Konfiguration auf. Die
**verpflichtenden** Ausnahmen (kein Account existiert zum Aufrufzeitpunkt):

| Pfad | Methode | Grund |
|---|---|---|
| `/household/setup` | `POST` | Erster Admin hat noch keinen Account |
| `/invite/{token}` | `GET` | Invite-Vorschau vor Registrierung |
| `/invite/{token}/join` | `POST` | Neues Mitglied hat noch keinen Account |
| `/members/availability` | `GET` | Wird vor Setup/Join aufgerufen |

Zusätzlich technisch bedingt (nicht im Auftrag einzeln genannt, aber notwendig, da sonst der
Login-Flow selbst blockiert wäre): `/login` (Formular-Login-Seite und -Processing-URL),
`/oauth2/authorization/**` (Login-Startpunkt für `oauth2Login()`), `/login/oauth2/code/**`
(Callback des Backend-als-Client) — diese vier zählen streng genommen zur AS-/Client-Infrastruktur
aus P1.4, nicht zur Business-API, werden hier der Vollständigkeit halber aufgeführt, damit die
`permitAll()`-Liste in P1.5.1 nicht versehentlich verengt wird.

Bereits mit `security: sessionCookie` spezifiziert (P1.2, hier nur bestätigt, nicht geändert):
`GET /me`, `POST /logout`.

**Alles andere** verlangt eine authentifizierte Session (`anyRequest().authenticated()`, bereits in
P1.4.4 als Grundgerüst angelegt).

**TDD:** Testklasse `SecurityFilterChainIT` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` +
Testcontainers), parametrisiert über die Pfad-Tabelle:

```java
@ParameterizedTest
@MethodSource("publicEndpoints")
void publicEndpoint_noSession_returnsNon401(HttpMethod method, String path) {
    // given
    var request = RequestEntity.method(method, URI.create(path)).build();

    // when
    var response = restTemplate.exchange(request, String.class);

    // then
    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
}

@Test
void protectedEndpoint_noSession_returns401() {
    // given
    var request = RequestEntity.get("/household/" + UUID.randomUUID() + "/tasks").build();

    // when
    var response = restTemplate.exchange(request, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

- **Rot:** Aktuell (vor P1.5) liefert `protectedEndpoint_noSession_returns401` 200/404 statt 401, da
  nichts geschützt ist.
- **Grün:** `authorizeHttpRequests`-Block wie oben aus P1.5.1.
- **Refactor:** `publicEndpoints()`-Quelle und die in P1.6 benötigte Endpunkt-Tabelle (siehe dort)
  ggf. zusammenlegen, um Redundanz zwischen den beiden parametrisierten Tests zu vermeiden.

#### P1.5.2 — CSRF

`CookieCsrfTokenRepository.withHttpOnlyFalse()` (macht `XSRF-TOKEN` für JS lesbar, wie von ADR-014
gefordert), `CsrfTokenRequestAttributeHandler` Default. Keine `permitAll()`-Ausnahme von CSRF nötig,
außer für `/household/setup`, `/invite/{token}/join` (state-changing, aber ohne vorherige Session,
also ohne vorherigen `XSRF-TOKEN`-Cookie) — hierzu: Spring Security stellt bei jedem `GET`-Request
(z. B. dem vorgelagerten `GET /invite/{token}` bzw. `GET /members/availability`) bereits einen
frischen `XSRF-TOKEN`-Cookie aus, den die SPA für den darauffolgenden `POST` mitschicken kann — keine
CSRF-Ausnahme für diese beiden `POST`-Endpunkte nötig, nur sicherstellen, dass der vorgelagerte
`GET`-Aufruf im Frontend-Flow tatsächlich passiert (Abstimmungspunkt mit P1.7/P1.9, hier nur
erwähnt, nicht weiter ausgeplant).

**TDD:** Testklasse `CsrfProtectionIT`, Testmethoden `stateChangingRequest_missingCsrfHeader_returns403`,
`stateChangingRequest_validCsrfHeader_succeeds`, `getRequest_anyState_returnsXsrfTokenCookie`
(3 Methoden → als flache Klasse ohne `@Nested` zulässig, da keine gemeinsame Methode unter Test
steht, sondern ein Querschnittsverhalten geprüft wird).

#### P1.5.3 — CORS

`CorsConfigurationSource`-Bean mit Origin-Allowlist aus `application.yaml`
(`librehousehold.security.cors.allowed-origins`, Liste), `allowCredentials(true)` (nötig für
Cookie-Versand), kein Wildcard-Origin in Kombination mit Credentials (CORS1). Da laut Kap. 8 CORS1
die Kontrolle wegen Nginx-Reverse-Proxy (Same-Origin) "largely moot" ist, aber Defense-in-Depth
gefordert wird: Default-Wert der Property ist leer/nur `localhost`-Dev-Origin, Produktion setzt die
eigene Domain explizit.

**TDD:** Testklasse `CorsConfigurationIT`, Testmethoden
`preflightRequest_allowedOrigin_returnsAccessControlAllowOrigin`,
`preflightRequest_unknownOrigin_omitsAccessControlAllowOrigin`.

#### P1.5.4 — Cookie-Attribute

Verifikation, dass die Session-Cookie `HttpOnly`, `Secure`, `SameSite=Lax` (oder `Strict`, siehe
Abstimmung mit Frontend-Navigationsfluss aus DD-7 — volle Seitennavigation zum Login spricht für
`Lax`, das Cross-Site-`GET`-Navigationen erlaubt) gesetzt bekommt (`server.servlet.session.cookie.*`
in `application.yaml` bzw. `CookieSerializer`-Bean, falls nicht per Property abbildbar).

**TDD:** Testmethode (Erweiterung `AuthorizationServerConfigurationIT` oder eigene
`SessionCookieAttributesIT`) `login_successful_setsSessionCookieWithSecureHttpOnlySameSite` —
prüft den `Set-Cookie`-Response-Header per String-Assertion auf die drei Attribute.

#### P1.5.5 — Dokumentations-Nacharbeit an `api/openapi.yml` (nur benennen, nicht umsetzen)

`security: [sessionCookie]` fehlt aktuell auf allen Operationen außer `getCurrentUser`/`logout`
(P1.2-Ergebnis). Muss nachgetragen werden — entweder pro Operation oder global via
`security: [sessionCookie]` auf Root-Ebene mit `security: []`-Override für die vier
`permitAll`-Endpunkte plus `setupHousehold`/`resolveInvite`/`joinHousehold`/
`checkEmailAvailability`. **Diese Spec-Änderung ist ausdrücklich nicht Teil der Implementierung
dieses Plans** — wird als kleine Folgeaufgabe nach P1.5 vermerkt, damit der Vertrag den tatsächlichen
Sicherheitsstatus korrekt dokumentiert.

### P1.6 — Access Control je Haushalt/Rolle

**Ziel:** Jeder haushaltsgebundene Endpunkt prüft Zugehörigkeit; Admin-only-Operationen.

#### P1.6.1 — `MemberQuery` erweitern

**Testklasse:** `MemberManagementServiceTest` (falls noch nicht vorhanden als Unit-Test — aktuell nur
`MemberManagementServiceIT` gefunden; da `isMemberOfHousehold` reine Delegation an ein Repository
ist, reicht ein IT-Test, kein separater Unit-Test nötig, konsistent mit den bestehenden
`isAdmin`/`memberExistsById`-Implementierungen, die ebenfalls nur über die ITs abgedeckt sind).

Neue Testmethode in `MemberManagementServiceIT` (`@Nested class isMemberOfHousehold`):
`isMemberOfHousehold_memberBelongsToHousehold_true`,
`isMemberOfHousehold_memberBelongsToDifferentHousehold_false` (2 Fälle — parametrisierbar, aber bei
nur zwei Fällen nach Projektregel ("ab drei") noch als zwei einzelne Methoden zulässig).

- **Rot:** `MemberQuery.isMemberOfHousehold(UUID, UUID)` existiert nicht.
- **Grün:** Interface-Methode ergänzen, `MemberManagementService` implementiert sie über neue
  abgeleitete Repository-Methode `MemberRepository.existsByIdAndHouseholdId(UUID id, UUID
  householdId)`.
- **Refactor:** keiner erwartet.

#### P1.6.2 — `HouseholdAccessGuard` (siehe DD-8)

**Testklasse:** `HouseholdAccessGuardTest` (Unit, `MemberQuery` gemockt — direkte Dependency).

```java
@Nested
class isMember {

    @Test
    void isMember_principalBelongsToHousehold_true() {
        // given
        var householdId = UUID.randomUUID();
        var principal = new AccountOidcPrincipal(someOidcUser, UUID.randomUUID(), householdId, false);
        var authentication = new OAuth2AuthenticationToken(principal, List.of(), "spa-backend-client");
        doReturn(true).when(memberQuery).isMemberOfHousehold(principal.memberId(), householdId);

        // when
        var result = guard.isMember(householdId, authentication);

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isMember_principalBelongsToDifferentHousehold_false() {
        // given
        var householdId = UUID.randomUUID();
        var principal = new AccountOidcPrincipal(someOidcUser, UUID.randomUUID(), UUID.randomUUID(), false);
        var authentication = new OAuth2AuthenticationToken(principal, List.of(), "spa-backend-client");
        doReturn(false).when(memberQuery).isMemberOfHousehold(principal.memberId(), householdId);

        // when
        var result = guard.isMember(householdId, authentication);

        // then
        assertThat(result).isFalse();
    }
}
```

Analog `@Nested class isAdminOfHousehold` (drei Fälle: Admin dieses Haushalts → `true`; Mitglied,
aber nicht Admin → `false`; nicht Mitglied dieses Haushalts (auch wenn Admin eines anderen — kann
laut 1:1-Modell aus ADR-012 gar nicht vorkommen, aber die Methode muss trotzdem korrekt
kombinieren) → `false`), `@Nested class isSelf` (zwei Fälle: gleiche `memberId` → `true`,
unterschiedliche → `false`).

- **Rot:** `HouseholdAccessGuard` existiert nicht.
- **Grün:** Klasse wie in DD-8 beschrieben implementieren.
- **Refactor:** keiner erwartet.

#### P1.6.3 — Guard an Controllern anbringen (`@PreAuthorize`)

Kein neuer Testfall pro Controller-Methode — stattdessen **ein** parametrisierter
End-to-End-Integrationstest über alle betroffenen Endpunkte (Auftragsvorgabe: ">2 ähnliche Tests →
parametrisiert", hier sind es ~15 Endpunkte, also klar parametrisiert statt 15 Einzeltests).

**Testklasse:** `HouseholdAccessControlIT` (`@SpringBootTest(webEnvironment=RANDOM_PORT)` +
Testcontainers, drei vorbereitete authentifizierte Test-Clients: `memberOfHouseholdA` (kein Admin),
`adminOfHouseholdA`, `memberOfHouseholdB`, jeweils über den echten Login-Flow aus P1.4 oder — falls
das zu aufwendig für jeden Testlauf ist — durch direktes Einspeisen eines `OAuth2AuthenticationToken`
in eine `MockMvc`-Request via `.with(authentication(...))`, was für einen reinen
Autorisierungs-Test ausreichend ist, ohne den vollen OAuth2-Handshake zu wiederholen).

```java
record Endpoint(HttpMethod method, String pathTemplate, RequiredRole requiredRole) {}

static Stream<Endpoint> protectedEndpoints() {
    return Stream.of(
        new Endpoint(GET, "/household/{householdId}/tasks", MEMBER),
        new Endpoint(POST, "/household/{householdId}/tasks", MEMBER),
        new Endpoint(DELETE, "/household/{householdId}/tasks/{taskId}", MEMBER),
        new Endpoint(GET, "/household/{householdId}/expenses", MEMBER),
        new Endpoint(POST, "/household/{householdId}/expenses", MEMBER),
        new Endpoint(GET, "/household/{householdId}/statistics", MEMBER),
        new Endpoint(GET, "/household/{householdId}/members", MEMBER),
        new Endpoint(DELETE, "/household/{householdId}/members/{memberId}", ADMIN),
        new Endpoint(PUT, "/household/{householdId}/admin", ADMIN),
        new Endpoint(GET, "/household/{householdId}/invite", ADMIN),
        new Endpoint(POST, "/household/{householdId}/invite", ADMIN),
        new Endpoint(PUT, "/household/{householdId}", ADMIN),
        new Endpoint(DELETE, "/household/{householdId}", ADMIN)
        // vollständige Liste bei Umsetzung gegen die aktuelle api/openapi.yml abgleichen
    );
}

@ParameterizedTest
@MethodSource("protectedEndpoints")
void endpoint_memberOfDifferentHousehold_returns403(Endpoint endpoint) {
    // given
    var request = requestFor(endpoint, householdA.getId(), asPrincipal(memberOfHouseholdB));

    // when
    var response = restTemplate.exchange(request, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}

@ParameterizedTest
@MethodSource("adminOnlyEndpoints")
void adminOnlyEndpoint_memberButNotAdmin_returns403(Endpoint endpoint) {
    // given
    var request = requestFor(endpoint, householdA.getId(), asPrincipal(memberOfHouseholdA));

    // when
    var response = restTemplate.exchange(request, String.class);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
}
```

- **Rot:** Alle Endpunkte antworten aktuell mit ihrem regulären Erfolgscode statt `403`, weil keine
  Zugriffsprüfung existiert (das *ist* der aktuelle IDOR-Zustand, siehe Ist-Zustand-Abschnitt).
- **Grün:** `@PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")` bzw.
  `...isAdminOfHousehold(...)` an den jeweiligen `*ApiDelegateImpl`-Methoden ergänzen (Liste der
  betroffenen Klassen: `HouseholdApiDelegateImpl`, `MembersApiDelegateImpl`,
  `TasksApiDelegateImpl`, `ExpensesApiDelegateImpl`, `FinancialsApiDelegateImpl`,
  `ReimbursementsApiDelegateImpl`, `StatisticsApiDelegateImpl`, `UsersettingsApiDelegateImpl`),
  `@EnableMethodSecurity` an der Security-Konfigurationsklasse aktivieren.
- **Refactor:** Nach Grün prüfen, ob sich die SpEL-Ausdrücke durch eine Meta-Annotation (z. B.
  `@RequireHouseholdMembership` als zusammengesetzte Annotation über `@PreAuthorize`) lesbarer
  gestalten lassen — optionaler Polish-Schritt, kein Muss.

**Vorschlag für die Rollenzuordnung** (Empfehlung, da hier auch fachliche/Produktentscheidungen
hineinspielen, die über reine Architektur hinausgehen — **zur Bestätigung**):

| Endpunkt | Erforderliche Rolle | Begründung |
|---|---|---|
| `GET/POST /household/{id}/tasks`, `PUT/PATCH/DELETE .../tasks/{taskId}` | Mitglied | Aufgaben sind Haushalts-gemeinschaftlich |
| `GET/POST /household/{id}/expenses`, `.../expenses/{expenseId}` | Mitglied | dito |
| `GET/POST/PATCH/DELETE /household/{id}/categories(/{id})` | Mitglied | dito |
| `GET/POST/PATCH /household/{id}/reimbursements(/{id})` | Mitglied | dito |
| `GET /household/{id}/financials/**`, `/statistics` | Mitglied | Lesezugriff auf eigene Haushaltsdaten |
| `GET /household/{id}/members`, `GET .../members/{memberId}` | Mitglied | Mitgliederliste ist Haushalts-intern |
| `PATCH /household/{id}/members/{memberId}` | Mitglied (self) **oder** Admin | Eigenes Profil oder Admin pflegt fremde Profile |
| `DELETE /household/{id}/members/{memberId}` | Admin | Entfernen eines Mitglieds ist administrativ |
| `POST /household/{id}/members` | Admin | Direktes Anlegen ohne Invite-Flow ist administrativ |
| `PUT .../members/{memberId}/password` | Mitglied (self only) | Passwort nur für sich selbst änderbar |
| `DELETE .../members/{memberId}/account` | Mitglied (self only) | "Delete own account" laut Spec-Beschreibung |
| `PATCH .../members/{memberId}/preferences` | Mitglied (self only) | persönliche Einstellungen |
| `PUT /household/{id}` (Name ändern), `DELETE /household/{id}` | Admin | strukturverändernd |
| `GET/POST /household/{id}/invite` | Admin | Einladungen zu erzeugen ist administrativ |
| `PUT /household/{id}/admin` (Ownership-Transfer) | Admin | per Spec-Beschreibung |

#### P1.6.4 — Modulgrenzen-Hygiene (leichter Touch-up, kein vollständiges TD1)

Durch die DD-8-Restrukturierung (`HouseholdAccessGuard` liegt in `household`, nicht in `core`)
entsteht durch diesen Plan **keine neue Abhängigkeitsart**: `tasks`, `expenses`, `reimbursements`,
`categories`, `statistics` hängen für `@householdAccessGuard.…`-SpEL-Aufrufe von `household` ab —
exakt dieselbe Richtung (Modul → `household`), die bereits durch ADR-011 für `HouseholdQuery`/
`MemberQuery` etabliert und akzeptiert ist. `core` bleibt unverändert: Es hängt von nichts ab und
wird nur von `household` (für `CoreOptionalMapper`) genutzt — konsistent mit der neuen
Modul-Abhängigkeitsregel.

Damit entfällt der ursprünglich hier vorgesehene `package-info.java`-Eintrag für `core` (der hätte
eine `core → household`-Abhängigkeit deklariert, die es nach der Restrukturierung nicht mehr gibt).
Ein vollständiges `package-info.java` je Modul plus `ApplicationModules.verify()`-Test bleibt TD1 —
eine separate, größere, hier nicht mitzuerledigende Aufgabe. Dieser Plan fügt keine neue,
undokumentierte Abhängigkeitsrichtung hinzu, die TD1 bei seiner Behebung zusätzlich erschweren würde.

## Out of Scope

- **P0.x, P1.1/P1.2, P1.7–P1.10, P2.x, P3.x** — siehe Meta-Plan.
- **`PUT /household/{householdId}/members/{memberId}/password` (`changePassword`)** — bereits in
  `api/openapi.yml` spezifiziert, aber weder Delegate-Methode noch Service-Logik existieren aktuell
  (`UsersettingsApiDelegateImpl` implementiert nur `updatePreferences`/`deleteAccount`). Baut auf
  demselben `AccountRepository`/`PasswordEncoder`, den P1.3 hier einführt, wird aber bewusst nicht in
  diesem Plan mit umgesetzt, da er im Auftrag nicht als P1.3-Bestandteil genannt ist und eher zum
  bestehenden `usersettings`-Modul-Scope gehört als zu `AccountRegistration`. Empfehlung: als
  eigener kleiner Folge-Task nach P1.3 einplanen.
- **Vollständige TD1-Behebung** (Modulgrenzen für *alle* Module technisch erzwingen) — nur der durch
  diesen Plan neu entstehende `core`→`household`-Fall wird deklariert (P1.6.4).
- **`security: sessionCookie` global/pro Operation in `api/openapi.yml` nachtragen** — wird benannt
  (P1.5.5), aber nicht umgesetzt.
- **Spring Session JDBC / Cross-Instance-Session-Persistenz** — aktuell Single-Instance-Deployment
  (Docker, Nginx-Reverse-Proxy, siehe Kap. 7); In-Memory-`HttpSession` und in-memory
  `OAuth2AuthorizedClientService` gehen bei einem Neustart gemeinsam verloren, was konsistent und
  nicht schlimmer ist als der aktuelle Zustand ohne jede Session. Keine neue Lücke, daher keine
  Aufgabe in diesem Plan; ggf. als Risiko in Kap. 11 nachtragen, falls Multi-Instance-Deployment je
  relevant wird.
- **Rate-Limiting/Lockout, E-Mail-Verifikations-Logik, Passwort-Reset** — explizit P2.x.
- **Social Login / föderierte Provider** — explizit P3.x.

## Akzeptanzkriterien

- [ ] `account`-Tabelle existiert (1:1 zu `member`, `member_id` als PK+FK `ON DELETE CASCADE`,
      `password_hash NOT NULL`, ohne `email_verified` — kommt erst bei P2.1, siehe DD-2), angelegt
      per Flyway-Migration `household/V2__local_auth.sql`.
- [ ] `oauth2_registered_client`, `oauth2_authorization`, `oauth2_authorization_consent` existieren,
      angelegt als Nachtrag in derselben `household/V2__local_auth.sql` (keine eigene Migrationsdatei,
      siehe DD-2-Klarstellung/P1.4.2), mit den offiziellen, versionsgenau übernommenen Spaltennamen
      (kein Schema-Autogen).
- [ ] Setup (`POST /household/setup`) und Invite-Join (`POST /invite/{token}/join`) hashen das
      übergebene Passwort mit Argon2id (OWASP-Parameter, siehe DD-3) und legen einen `account`-
      Datensatz an; kein Klartext-Passwort wird je persistiert oder geloggt.
- [ ] `GET /members/availability` liefert korrekt `available: false`, sobald irgendein `member`
      (nicht nur `account`) mit der E-Mail existiert.
- [ ] `AccountUserDetailsService` lädt Login-Daten korrekt per E-Mail; unbekannte E-Mail wirft
      `UsernameNotFoundException` ohne Unterscheidung zu "falsches Passwort" in der resultierenden
      HTTP-Antwort (ENUM1: keine Existenz-Rückschlüsse).
- [ ] Backend fungiert als eigener OAuth2-Client gegen die eigene, eingebettete Authorization-
      Server-Instanz; der registrierte Client ist **confidential** (`CLIENT_SECRET_BASIC`, Secret
      aus Konfiguration, nicht `NONE`, siehe DD-7-Korrektur), zusätzlich ist PKCE erzwungen
      (`requireProofKey(true)`); kein Access-/Refresh-/ID-Token erscheint jemals im Response-Body
      oder Set-Cookie-Header einer Browser-gerichteten Antwort.
- [ ] Refresh-Tokens werden tatsächlich ausgestellt (Nachweis, dass die Confidential-Client-Korrektur
      wirkt — mit einem Public Client wären laut Spring-AS-Doku gar keine Refresh-Tokens verfügbar).
- [ ] Nach erfolgreichem Login besitzt der Browser ausschließlich eine `Secure`, `HttpOnly`,
      `SameSite`-Session-Cookie; `GET /me` liefert mit dieser Cookie korrekt den authentifizierten
      Nutzer, ohne Cookie `401`.
- [ ] `POST /logout` widerruft das zugehörige Authorized-Client-Token serverseitig (nicht nur
      Session-Invalidierung) und ist idempotent (wiederholter Aufruf ohne aktive Session → `204`).
- [ ] Alle Business-Endpunkte außer der vier explizit gelisteten `permitAll`-Ausnahmen (plus den
      technisch bedingten AS-/Client-Login-Pfaden) liefern ohne gültige Session `401`.
- [ ] State-changing Requests ohne oder mit falschem `X-XSRF-TOKEN`-Header liefern `403`; `GET`-
      Requests liefern einen lesbaren `XSRF-TOKEN`-Cookie.
- [ ] CORS akzeptiert nur konfigurierte Origins; kein Wildcard-Origin in Kombination mit
      `Access-Control-Allow-Credentials: true`.
- [ ] Jeder haushaltsgebundene Endpunkt lehnt Zugriffe von authentifizierten Nutzern ab, die nicht
      Mitglied des in der URL referenzierten Haushalts sind (`403`), verifiziert durch den
      parametrisierten `HouseholdAccessControlIT`-Test über alle in der Rollenzuordnungstabelle
      gelisteten Endpunkte.
- [ ] Admin-only-Operationen (Invite erzeugen/lesen, Ownership-Transfer, Mitglied entfernen,
      Haushalt umbenennen/löschen) lehnen Zugriffe von Nicht-Admin-Mitgliedern desselben Haushalts
      ab (`403`), ebenfalls über denselben parametrisierten Test verifiziert.
- [ ] Self-only-Operationen (Passwort ändern, eigenen Account löschen, eigene Preferences) lehnen
      Zugriffe von anderen Mitgliedern (auch Admins) auf eine fremde `memberId` ab (`403`).
- [ ] Alle neuen Service-Methoden mit DB-Zugriff haben mindestens einen Happy-Path-`*ServiceIT`-Test
      gegen echtes Postgres via Testcontainers; alle neuen reinen Logikbausteine
      (`HouseholdAccessGuard`, `AccountService`, `AccountOidcUserService`, `AccountUserDetailsService`)
      haben Unit-Tests mit gemockten direkten Dependencies.
- [ ] `./mvnw clean compile` und `./mvnw test` laufen fehlerfrei durch (inkl. der in P1.3.1
      identifizierten, vorher ggf. bereits gebrochenen Altbestand-Tests).