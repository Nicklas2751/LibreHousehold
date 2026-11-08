# Implementierungsplan R1.1–R1.6: Backend-Fundament für den Realtime-Stream

Referenz: [`realtime-meta-plan.md`](realtime-meta-plan.md), „Phase R1: Backend-Fundament", Punkte
**R1.1–R1.6**, sowie ADR-019 und der Vertrag `api/asyncapi.yml`. Dieses Dokument ist ein
Arbeitsdokument (kein Arc42-Kapitel, kein ADR) und wird nach der Umsetzung nicht dauerhaft gepflegt.

**Scope:** Der Stream-Endpoint `GET /v1/household/{householdId}/events` mit Emitter-Registry,
Security-Integration, Heartbeat samt Session-Prüfung, Lifecycle-Kopplung an `MemberRemoved` und
`HouseholdDeleted`, Limits, Logging, Metriken und den zugehörigen Tests. Am Ende von R1 liefert der
Stream nur `household.deleted` und `session.terminated` aus, weil noch kein Fachmodul andere Events
publiziert.

Explizit **nicht** Teil dieses Plans:

- Fach-Events aus `tasks`, `expenses` und `household` (Create, Update, Delete) und deren Broadcast:
  R2. Dort liegen auch die IT-Fälle „Event kommt nach Commit an, kein Event bei Rollback" aus R1.6,
  weil es in R1 kein Fach-Event gibt, dessen Transaktion sich gezielt zurückrollen ließe.
- `member.removed` an die verbleibenden Mitglieder: `MemberRemoved` trägt keine `householdId`.
  Das Event wird in R2.3 erweitert, der Broadcast folgt in R2.4. R1.4 schließt nur die Streams des
  entfernten Mitglieds.
- Frontend, mokapi, Reverse-Proxy-Doku und Arc42 (R3, R4).

Status: Umgesetzt (2026-11-07, Commits `7f9269a` bis `f228107`). Die Rückfragen aus Abschnitt 6 sind
entschieden. Offene Punkte und Findings stehen im Meta-Plan unter R1.6 und R4.3.

**Nachtrag 2026-11-08:** Der Code liegt nicht im Modul `realtime`, sondern im Modul
`notifications` unter `notifications.realtime` (siehe Meta-Plan R0.3). Paketnamen in diesem Dokument
entsprechend lesen.

---

## 1. Bindende Vorgaben

- **ADR-019:** SSE über `SseEmitter`, dünne Signale, kein Replay, Kanal = Haushalt, der Stream
  verlängert die Session nicht. `realtime`-Listener sind synchrone
  `@TransactionalEventListener(AFTER_COMMIT)` ohne Registry-Eintrag.
- **`api/asyncapi.yml`** ist die Quelle der Wahrheit für Event-Namen, Payload, Header und
  Fehlerfälle. Abweichungen werden zuerst dort geändert und validiert (`npm run validate` in `api/`).
- **Security-Controls RT1–RT12** aus Abschnitt 4 des Meta-Plans. Jeder Control bekommt mindestens
  einen verifizierenden Test (Abschnitt 4 dieses Plans).
- **TDD** (AGENTS.md, globale Regeln): erst der Test, rot sehen, dann implementieren. Unit-Tests
  ohne Server und Netzwerk, mit `doReturn(...).when(...)`, ohne `lenient()` und ohne
  `@BeforeEach`. Testnamen und Kommentare auf Englisch. Integrationstests `*IT` laufen nur mit
  `./mvnw verify`.
- **Keine neue Dependency.** `SseEmitter` gehört zu Spring MVC. Die ITs lesen den Stream mit dem
  JDK-`HttpClient`. Micrometer kommt bereits über den Actuator. Der Vertragstest liest
  `asyncapi.yml` mit SnakeYAML, das Spring Boot mitbringt.
- **Modulgrenzen:** `realtime` hängt nur vom Root-Package von `household` ab (`AccountPrincipal`,
  `MemberRemoved`, `HouseholdDeleted`, `HouseholdAccessGuard` per SpEL). Kein anderes Modul
  referenziert `realtime`. `ApplicationTests.writeDocumentationSnippets` bleibt grün.
- Ein-Parameter-Lambdas ohne Klammern, keine Code-Blöcke in Lambdas, benannte Konstanten statt
  Magic Numbers.

## 2. Befunde aus dem Code (Stand 2026-11-07)

| Thema | Befund | Folge für R1 |
|---|---|---|
| Autorisierung | `anyRequest().access(AccountPrincipalAuthorizationManager)`. `authorizeHttpRequests` filtert in Spring Security 6+ **alle** Dispatcher-Typen, also auch den `ASYNC`-Dispatch, mit dem `SseEmitter` endet. | Der `ASYNC`-Dispatch wird erneut autorisiert. Endet die Session vorher (Logout), schlägt das fehl („response is already committed"). Siehe 3.3. |
| 401 statt Redirect | `browserNavigation()` matcht nur `text/html` und ignoriert `*/*`. `EventSource` sendet `Accept: text/event-stream`. | Unauthentifiziert gibt es 401. Das wird per IT abgesichert, eine Code-Änderung ist nicht nötig. |
| Session-Ende | `MemberSessions.expireAll` ruft `expireNow()` auf, und zwar **in** der Transaktion vor dem Commit (`HouseholdManagementService` Z. 63, `MemberManagementService` Z. 204). Logout invalidiert die Session, `HttpSessionEventPublisher` meldet das an die `SessionRegistry`. | Der Heartbeat prüft über `SessionRegistry.getSessionInformation(sessionId)`: `null` heißt beendet, `isExpired()` heißt abgelaufen. Ein Tick zwischen `expireNow()` und Commit schließt den Stream ohne `session.terminated`. Das ist akzeptabel, der Client landet über `/me` beim Login. |
| Lifecycle-Events | `MemberRemoved(memberId, …)` ohne `householdId`, `HouseholdDeleted(householdId, …, members)`. Beide werden in `@Transactional`-Methoden publiziert. | Ein `AFTER_COMMIT`-Listener funktioniert. Das Schließen nach `memberId` geht über die Registry. |
| Registry-Trigger | Ohne Property landet jeder `AFTER_COMMIT`-Listener in `event_publication` (Spike R0.4). | Die Property `spring.modulith.events.registry-trigger-annotation` wird schon in **R1.4** gesetzt, mit dem ersten `realtime`-Listener, und nicht erst in R2.4. |
| Rate-Limiting | `RateLimitingFilter` begrenzt Anfragen pro Zeitfenster. `TooManyRequestsProblem` ist package-private in `config`. | RT5 braucht ein Limit für **gleichzeitig offene** Streams. Dafür gibt es ein eigenes Problem in `realtime`, die Antwort folgt aber demselben ProblemDetail-Format über `ErrorResponseExceptionHandler`. |
| Session-Timeout | Nicht konfiguriert, also Tomcat-Default 30 min. Weder `/me` noch `/auth/discover` liefern den Wert. | R1.3 setzt `server.servlet.session.timeout` explizit und gibt den Wert an den Client weiter (Entscheidung 6.1). |
| Scheduling | `@EnableScheduling` ist aktiv (`LibrehouseholdApplication`). | Der Heartbeat ist ein `@Scheduled`-Job. |
| Modul-Deklaration | Es gibt noch keine einzige `package-info.java` (TD1). | `realtime` wird das erste Modul mit `@ApplicationModule(allowedDependencies = "household")`. |
| IT-Muster | `RANDOM_PORT` + `TestcontainersConfiguration` + `RestTestClient`, Login über `GET /auth/discover` → `POST /login` (Formular, `email`) mit `X-XSRF-TOKEN` (siehe `MemberSessionInvalidationIT`). | Login und Cookies laufen wie dort. Den Stream selbst liest der JDK-`HttpClient` (`BodyHandlers.ofLines()`), weil `RestTestClient` die Antwort erst am Ende liefert. |

## 3. Designentscheidungen

### 3.1 Paketstruktur von `realtime`

Die Struktur folgt den bestehenden Modulen:

```text
realtime/
├── package-info.java                    @ApplicationModule(allowedDependencies = "household")
├── controller/HouseholdEventStreamController.java
├── model/SignalType.java                Enum, Wire-Name = AsyncAPI-Message-Name (RT6)
├── model/InvalidationSignal.java        record(entity, entityId, occurredAt)
├── model/SessionTerminatedSignal.java   record(occurredAt)
├── model/StreamConnection.java          record(householdId, memberId, sessionId, SseEmitter)
├── model/CloseReason.java               Enum für Logging und Metriken (RT10)
├── service/HouseholdEventStreams.java   Registry householdId → Set<StreamConnection>, Limits
├── service/SignalSender.java            Senden über einen Executor, Fehler isoliert (RT12)
├── service/StreamHeartbeat.java         @Scheduled: Session-Check + Kommentar (RT3)
├── service/StreamLifecycleListener.java MemberRemoved, HouseholdDeleted, HttpSessionDestroyedEvent
├── service/StreamMetrics.java           Micrometer-Gauge und Counter
├── exception/StreamLimitExceededProblem.java   429 + Retry-After
└── config/RealtimeProperties.java       librehousehold.realtime.*
```

Der Controller ist von Hand geschrieben, ein `@RestController` ohne generiertes Interface, weil der
Endpoint bewusst nicht in `openapi.yml` steht (ADR-019). Den Pfad bildet er aus
`${openapi.libreHousehold.base-path:/v1}` wie `SecurityConfig`.

### 3.2 Endpoint

- `@GetMapping(path = "{basePath}/household/{householdId}/events", produces = TEXT_EVENT_STREAM_VALUE)`
- `@PreAuthorize("@householdAccessGuard.isMember(#householdId, authentication)")` (RT1). Die Prüfung
  läuft, bevor die Methode einen Emitter erzeugt, also vor dem ersten Byte.
- Rückgabe `ResponseEntity<SseEmitter>` mit `Cache-Control: no-store` und `X-Accel-Buffering: no`
  (RT9).
- Die Session-ID kommt aus `request.getSession(false).getId()`. Der Controller liest sie nur und
  schreibt **nichts** in die Session (RT4, RT8).
- Das erste gesendete Feld ist `retry: <reconnect-delay>` (RT5), danach folgen nur noch Events und
  Heartbeats.
- Die Limit-Prüfung läuft nach der Autorisierung. Ein Unberechtigter kann so kein Kontingent
  verbrauchen.

### 3.3 Security bei `ASYNC`-Dispatch (R1.2)

Zuerst kommt der Test, rot (IT 4.2, Fälle „Stream nach Logout" und „Event nach dem ersten Byte").
Der erwartete Fix ist `.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` in
`SecurityConfig`. Begründung: Ein `ASYNC`-Dispatch setzt nur einen Request fort, der beim
`REQUEST`-Dispatch bereits authentifiziert und autorisiert wurde. Die Session-Bindung des Streams
sichert der Heartbeat (RT3), nicht die erneute Autorisierung. Vor der Umsetzung prüfe ich die
Spring-Security-Doku per Context7 („Authorize all dispatches"). Falls die Doku einen engeren Weg
empfiehlt, hat er Vorrang. Die Änderung betrifft `config`, ist aber unabhängig von `realtime`:
`SecurityConfig` bekommt keine Referenz auf `realtime`.

### 3.4 Senden und Fehlerisolation (RT6, RT12)

- `SignalSender` serialisiert über `SseEmitter.event().name(type.wireName()).data(signal, APPLICATION_JSON)`.
  Jackson schreibt standardmäßig einzeiliges JSON. Der Payload enthält nur Enum, UUID und
  `Instant`, also keine Nutzertexte.
- Gesendet wird über einen `TaskExecutor`, Boots `applicationTaskExecutor`. Der synchrone
  `AFTER_COMMIT`-Listener blockiert damit nie an einem langsamen Client (ADR-019, Konsequenzen).
- Eine `IOException` oder `IllegalStateException` beim Senden schließt nur diese Verbindung
  (`completeWithError`) und entfernt sie aus der Registry. Andere Verbindungen und der Publisher
  sehen davon nichts.
- Cleanup über `onCompletion`, `onTimeout` und `onError` des Emitters, jeweils mit eigenem
  `CloseReason`.

### 3.5 Heartbeat, Timeouts und Session (R1.3)

- **Spike zuerst** (Wegwerf-IT, wird nicht committet): Berührt ein offener Stream, sein Ende
  (Emitter-Timeout) oder ein Reconnect die `lastAccessedTime` der Tomcat-Session? Läuft die Session
  während eines offenen Streams ab, und kommt dann ein `HttpSessionDestroyedEvent`? Erwartung nach
  Tomcat-Default (`LAST_ACCESS_AT_START=false`, `ACTIVITY_CHECK=false`): Die Session kann während
  des Streams ablaufen. Das Ende des Requests (Emitter-Timeout) und jeder Reconnect aktualisieren
  aber den Zugriffszeitpunkt. Das Ergebnis kommt als Finding in AGENTS.md (R4.3).
- `server.servlet.session.timeout: 30m` wird explizit gesetzt, mit Kommentar, dass Stream und Client
  sich daran ausrichten.
- `librehousehold.realtime.emitter-timeout` (Default `25m`) bleibt **unter** dem Session-Timeout.
  Danach verbindet `EventSource` neu.
- `librehousehold.realtime.heartbeat-interval` (Default `25s`, unter Nginx' `proxy_read_timeout`
  von 60 s).
- Jeder Tick prüft pro Verbindung zuerst die Session über die `SessionRegistry` und schickt dann
  den Kommentar `: heartbeat`. Bei beendeter oder abgelaufener Session wird die Verbindung ohne
  Event geschlossen (`CloseReason.SESSION_ENDED` bzw. `SESSION_EXPIRED`). Ein Zugriff auf die
  `HttpSession` findet nicht statt (RT4).
- `HttpSessionDestroyedEvent` (Logout, Timeout) schließt die Streams der Session sofort. Der
  Heartbeat ist nur das Sicherheitsnetz für `expireNow()`, wo kein Destroy-Event kommt.
- Den Session-Timeout bekommt der Client über das neue Pflichtfeld `sessionTimeoutSeconds` in
  `CurrentUser` (`GET /me`, Entscheidung 6.1), mit `example`. Den Wert liest das Backend aus
  `server.servlet.session.timeout`. Danach `npm run openapi` (Frontend) und `./mvnw clean compile`
  ausführen. Ob die `/me`-Antwort von mokapi das Feld automatisch aus dem Schema bekommt, wird
  geprüft. Muss dafür `api/mokapi.js` geändert werden, gibt es vorher eine Rückfrage, weil die
  mokapi-Freigabe nur den SSE-Endpoint umfasst.

### 3.6 Lifecycle-Kopplung (R1.4)

- `StreamLifecycleListener` mit `@TransactionalEventListener(phase = AFTER_COMMIT)`:
    - `MemberRemoved`: an alle Verbindungen des `memberId` `session.terminated` senden, dann
      schließen (`MEMBER_REMOVED`).
    - `HouseholdDeleted`: an alle Verbindungen des Haushalts `household.deleted`, dann
      `session.terminated` senden, dann schließen (`HOUSEHOLD_DELETED`). So legt es der Vertrag fest.
- `@EventListener` auf `HttpSessionDestroyedEvent`: Streams der Session schließen.
- Im selben Schritt wird
  `spring.modulith.events.registry-trigger-annotation: org.springframework.modulith.events.ApplicationModuleListener`
  in `application.yaml` gesetzt, mit Kommentar und Verweis auf ADR-019.

### 3.7 Limits, Logging und Metriken (R1.5)

- `librehousehold.realtime.max-streams-per-session` (Default 5), `max-streams-per-member` (10) und
  `max-streams` (500, global). Bei Überschreitung `StreamLimitExceededProblem`: 429,
  `Retry-After: <retry-after>` (Default 30 s), Typ `/problems/too-many-streams`. Die Werte stehen in
  Entscheidung 6.3.
- Das Problem erweitert `ErrorResponseException` und läuft damit durch den vorhandenen
  `ErrorResponseExceptionHandler`. Bei `Accept: text/event-stream` kann Spring den ProblemDetail-Body
  womöglich nicht aushandeln. Der IT prüft deshalb verbindlich nur Status und `Retry-After`. Die
  AsyncAPI sagt bereits „ProblemDetail, sofern verhandelbar".
- Logging (RT10), SLF4J, Logger der jeweiligen Klasse:
    - `INFO` beim Öffnen und Schließen eines Streams, mit `memberId`, `householdId` und `CloseReason`.
    - `WARN` bei Limit-Überschreitung.
    - Nie Session-ID, nie Payload.
    - Abgelehnte Verbindungen (401/403, Entscheidung 6.2): Ein `SpringAuthorizationEventPublisher`-Bean
      in `config` veröffentlicht `AuthorizationDeniedEvent`s. Ein `@EventListener` in `realtime`
      loggt sie als `WARN`, aber nur, wenn das Event den Stream-Pfad (Request-Ebene, 401) oder die
      Controller-Methode (Methoden-Ebene, 403) betrifft. Ins Log kommen `memberId` (falls
      vorhanden), `householdId` aus dem Pfad und der Status.
- Micrometer: Gauge `librehousehold.realtime.streams.active`, Counter
  `librehousehold.realtime.signals.sent` (Tag `type`) und `librehousehold.realtime.streams.closed`
  (Tag `reason`).
- `spring.threads.virtual.enabled` wird **nicht** aktiviert: Das Async-Servlet hält beim Warten
  keinen Thread, und das Senden läuft über den Executor. Eine Neubewertung kommt nur, wenn Messungen
  das nahelegen.

## 4. Arbeitspakete in TDD-Reihenfolge

Jedes Paket endet mit grünem `./mvnw verify` und einem eigenen Commit (Conventional Commits, Scope
`realtime` bzw. `config`). Die Umsetzung übernimmt pro Paket der Agent `java-tdd-implementer`. Das
Briefing enthält jeweils die Regeln aus Abschnitt 1, die betroffenen Klassen aus Abschnitt 2 und die
Testfälle unten.

### 4.1 R1.1: Modul, Vertragstypen und Registry

Unit-Tests zuerst:

- `SignalTypeContractTest`: Die Wire-Namen von `SignalType` entsprechen genau den
  `components.messages.*.name` aus `api/asyncapi.yml`, und die Record-Komponenten von
  `InvalidationSignal`/`SessionTerminatedSignal` entsprechen den Schema-Properties (Vertragstreue,
  R1.6). Gelesen wird mit SnakeYAML, der Pfad ist relativ zum Modulverzeichnis (`../api/asyncapi.yml`).
- `InvalidationSignal`-Serialisierung: einzeiliges JSON mit genau den drei Feldern (RT6, RT11).
- `HouseholdEventStreamsTest` (`@Nested` pro Methode): `register`, `remove`,
  `connectionsOf(householdId)`, `connectionsOfMember`, `connectionsOfSession` und
  Thread-Sicherheit bei gleichzeitigem Registrieren und Entfernen.
- `SignalSenderTest`: Senden an alle Verbindungen. Eine Verbindung wirft eine `IOException`, die
  übrigen bekommen das Signal trotzdem, und die fehlerhafte wird entfernt (RT12).

Danach `HouseholdEventStreamController` und das Grundgerüst des ITs `HouseholdEventStreamIT`:

- Der Header-Fall wird zuerst rot gesehen: Ein Mitglied öffnet den Stream und bekommt 200,
  `Content-Type: text/event-stream`, `Cache-Control: no-store`, `X-Accel-Buffering: no` und ein
  `retry:`-Feld (RT9, RT5).
- Ein IT-Helfer `SseTestClient` (Testcode) mit JDK-`HttpClient`: Er liest Zeilen in eine
  `BlockingQueue`, parst `event:`/`data:`/Kommentare und bietet `awaitEvent(timeout)` und
  `awaitClosed(timeout)`.

### 4.2 R1.2: Security-Integration

IT-Fälle (`HouseholdEventStreamIT`, `@Nested class authorization`), zuerst rot:

- ohne Session → 401, kein `Location`-Header, kein Stream
- fremder Haushalt im Pfad → 403, kein Stream (RT1)
- abgelaufene Session (`expireNow()` vor dem Öffnen) → 401 über den `ConcurrentSessionFilter`
- Stream offen, dann Logout → der Stream endet, und im Log steht kein „response is already
  committed" bzw. kein `AccessDeniedException` (async dispatch, 3.3)
- `HouseholdDeleted` nach dem ersten Byte kommt an (prüft den `ASYNC`-Pfad mit gültiger Session)
- der Stream-GET ändert nichts am Zustand: Session-Attribute vorher und nachher gleich (RT8)

Danach der Fix aus 3.3 in `SecurityConfig`.

### 4.3 R1.3: Spike, Heartbeat und Session-Timeout

1. Den Spike aus 3.5 durchführen und das Ergebnis im Meta-Plan unter R1.3 festhalten.
2. Unit-Test `StreamHeartbeatTest`:
    - gültige Session → Kommentar wird gesendet
    - `getSessionInformation` liefert `null` → Verbindung wird ohne Event geschlossen
      (`SESSION_ENDED`)
    - `isExpired()` → Verbindung wird geschlossen (`SESSION_EXPIRED`)
    - eine fehlerhafte Verbindung bricht den Tick für die anderen nicht ab
3. `RealtimeProperties` mit `@Validated`: Emitter-Timeout muss kleiner als das Session-Timeout sein.
   Andernfalls schlägt der Start fehl. Dazu ein Test für beide Zweige.
4. IT mit kurzem Heartbeat-Intervall (z. B. `200ms` per `@SpringBootTest(properties=…)`):
    - offener Stream bekommt Heartbeat-Kommentare
    - `MemberSessions.expireAll` (Passwort-Reset-Pfad) → Stream endet spätestens nach einem Tick (RT3)
5. Session-Timeout an den Client: OpenAPI-Änderung gemäß Entscheidung 6.1, Mapper/Delegate-Test für das
   neue Feld, Clients neu generieren.

### 4.4 R1.4: Lifecycle-Kopplung

- Unit-Test `StreamLifecycleListenerTest`:
    - `MemberRemoved` → `session.terminated` nur an die Verbindungen dieses Mitglieds, dann
      schließen
    - `HouseholdDeleted` → `household.deleted` und `session.terminated` an alle des Haushalts, dann
      schließen
    - `HttpSessionDestroyedEvent` → Verbindungen der Session werden geschlossen
    - keine Verbindung vorhanden → kein Fehler
- IT:
    - Mitglied entfernen → dieses Mitglied bekommt `session.terminated`, sein Stream endet, der
      Stream eines anderen Mitglieds bleibt offen
    - Haushalt löschen → beide Mitglieder bekommen `household.deleted` und `session.terminated`
    - nach dem Haushalt-Löschen: **keine** Zeile in `event_publication` mit der Listener-ID von
      `StreamLifecycleListener`, aber weiterhin eine für den `HouseholdDeletedListener` aus
      `notifications` (Regression für die Registry-Property)
- Danach die Property aus 3.6 in `application.yaml` setzen.

### 4.5 R1.5: Limits, Logging und Metriken

- `HouseholdEventStreamsTest` erweitern:
    - pro Limit genau am Limit erlaubt, eins darüber `StreamLimitExceededProblem` (parameterisiert
      über Session, Member und global)
    - geschlossene Verbindungen geben das Kontingent wieder frei
- `StreamLimitExceededProblem`: Status, Typ und `Retry-After`.
- `StreamMetricsTest` mit `SimpleMeterRegistry`: Gauge folgt Registrieren und Entfernen, Counter
  pro Typ und Grund.
- Logging-Test mit einem Logback-`ListAppender` (ist im Test-Classpath, über Spring Boot): Öffnen
  und Schließen werden mit `memberId` und Grund geloggt, und die Session-ID taucht in keiner
  Logzeile auf (RT10).
- `StreamAccessDeniedLoggerTest`: Ein Denied-Event für den Stream-Pfad bzw. die Stream-Methode wird
  geloggt, ein Denied-Event für einen anderen Endpoint nicht.
- IT: Ein 403 auf den Stream erzeugt eine `WARN`-Zeile, ein 403 auf einen REST-Endpoint nicht. Der
  `AuthorizationDeniedEvent`-Publisher ändert an den bestehenden Security-ITs nichts (`./mvnw verify`).
- IT: `max-streams-per-session` per Property auf 1 gesetzt, zweiter Stream derselben Session →
  429 mit `Retry-After` (RT5).

### 4.6 R1.6: Abschluss-ITs und Verifikation

- Restliche Fälle aus dem Meta-Plan, sofern noch nicht abgedeckt:
    - toter Client bricht den Broadcast nicht ab: ein Client schließt seine Verbindung hart, dann
      Haushalt löschen, der andere bekommt trotzdem beide Events (RT12)
    - jedes empfangene `data:` ist genau eine Zeile und lässt sich gegen die Felder des Vertrags
      parsen (RT6, Vertragstreue)
- `./mvnw clean verify` vollständig grün, inklusive `ApplicationTests` (Modulgrenzen).
- Meta-Plan: R1.1–R1.6 mit ✅ markieren, dazu die Findings (Spike, Async-Dispatch, Testmuster für
  SSE) für R4.3 notieren.

## 5. Verifikation pro Control

| Control | Test |
|---|---|
| RT1 | IT 4.2 „fremder Haushalt → 403" |
| RT2/RT11 | Unit 4.1 Serialisierung, Vertragstest |
| RT3 | IT 4.3 `expireAll` → Stream endet, IT 4.2 Logout, IT 4.4 Lifecycle |
| RT4 | Spike 4.3, Properties-Validierung 4.3, Unit 4.3 (kein `HttpSession`-Zugriff im Heartbeat) |
| RT5 | Unit/IT 4.5 Limits, IT 4.1 `retry:` |
| RT6 | Unit 4.1 Enum/Serialisierung, IT 4.6 einzeiliges `data:` |
| RT8 | IT 4.2 Session-Attribute unverändert |
| RT9 | IT 4.1 Header |
| RT10 | Unit 4.5 Logging (plus 6.2) |
| RT12 | Unit 4.1 `SignalSenderTest`, IT 4.6 toter Client |

RT7 betrifft den Client (R3.1).

## 6. Entscheidungen (2026-11-07)

1. ✅ **Session-Timeout an den Client:** Pflichtfeld `sessionTimeoutSeconds` in `CurrentUser`
   (`GET /me`). Gilt nur für angemeldete Sessions, deshalb nicht in `/auth/discover`.
2. ✅ **Logging abgelehnter Stream-Verbindungen:** `SpringAuthorizationEventPublisher` in `config`
   plus ein filternder `AuthorizationDeniedEvent`-Listener in `realtime`. `@PreAuthorize` bleibt.
3. ✅ **Grenzwerte:** 5 Streams pro Session, 10 pro Mitglied, 500 global, `Retry-After` 30 s,
   `retry:` 5 s. Alle Werte per Property überschreibbar.
