# Realtime Meta-Plan

Status: Entwurf / Entscheidungsgrundlage. Dieses Dokument ist **kein Arc42-Kapitel und kein ADR**.
Es ist die Arbeits-Roadmap dafür, dass Änderungen eines Haushaltsmitglieds (Task angelegt, Ausgabe
erfasst, Mitglied beigetreten, …) bei allen anderen Mitgliedern ohne Neuladen sichtbar werden. Jeder
Punkt bekommt vor der Umsetzung einen eigenen Implementierungsplan (TDD-Pflicht, siehe `../../AGENTS.md`).
Die Transportentscheidung selbst wird in R0.1 als ADR festgehalten.

Referenzierung und Pflege wie im [Auth-Meta-Plan](auth-meta-plan.md): IDs sind stabil, erledigte
Punkte werden mit ✅ markiert, nicht gelöscht. Die dort festgelegte **Sofort-Rückfrage-Pflicht**
(kein eigenmächtiges „Out of Scope") gilt hier genauso.

Bindende Leitplanken:

- ADR-004 (Modulith), ADR-010 (API first), ADR-011 (Domain Events für Lifecycle-/Statusänderungen),
  ADR-012 (1 Account : 1 Household, also genau ein Haushalt pro Verbindung), ADR-013/ADR-014
  (session-basierte Spring Security, BFF, httpOnly-Cookie, keine Tokens im JS-Kontext, kein
  localStorage für Session-/Auth-Zustand).
- **Security-Baseline:** OWASP Top 10 **2025** als Minimum, **OWASP ASVS 5.0 Level 2** als
  Prüfmaßstab wie in Arc42 Kap. 8 („Security Controls"). Die für den Stream nötigen Controls stehen
  in Abschnitt 4 (`RT1`–`RT12`) und werden in R4.2 in die Control-Tabelle von Kap. 8 übernommen.
  Grundsatz: Der Push-Kanal darf keine Daten ausliefern, die der Empfänger nicht auch per REST
  lesen dürfte.
- `../../api/mokapi.js` ist grundsätzlich tabu. **Für R3.4 hat der Nutzer die Erweiterung um einen
  SSE-Endpoint ausdrücklich freigegeben (2026-11-07).** Die Freigabe gilt nur für diesen Zweck.
- **Bereits getroffene Entscheidungen:**
    - 2026-10-11: Transport ist **SSE**. Schreibzugriffe bleiben REST.
    - 2026-10-11: Events sind **dünn** (Notify-then-Refetch, siehe Abschnitt 3).
    - 2026-10-11: Der gesamte SSE-Teil wird mit **AsyncAPI** beschrieben (siehe R0.2).
    - 2026-11-07: Stream-Pfad ist **`/v1/household/{householdId}/events`**, geschützt durch
      `HouseholdAccessGuard.isMember` wie jeder REST-Endpoint (siehe Abschnitt 3, „Kanal = Haushalt").
    - 2026-11-07: Ein offener Tab ist **keine Aktivität**. Der Stream darf die Session nicht
      verlängern, der Client trennt bei Leerlauf (siehe R3.2, RT4).
    - 2026-11-07: Scope v1 umfasst Tasks, Expenses, Erstattungen, Kategorien, Mitglieder und Haushalt.
    - 2026-11-07: Fremde Änderungen werden **still** aktualisiert, ohne Toast (siehe R3.5).
    - 2026-11-07: mokapi wird um einen SSE-Endpoint erweitert (siehe R3.4).
    - 2026-11-07: `realtime` hört per synchronem `@TransactionalEventListener(AFTER_COMMIT)` **ohne**
      Registry-Eintrag, festgehalten in ADR-019 als Abweichung von ADR-011 (siehe R0.4).
    - 2026-11-07: AsyncAPI-Tooling ist **nur die CLI-Validierung** (`@asyncapi/cli`), keine
      Codegenerierung (siehe R0.5).
    - 2026-11-07: Neues Modul `realtime`, das leere Package `events` wird gelöscht (siehe R0.3).

---

## 1. Ist-Zustand (Stand 2026-11-07)

| Bereich      | Befund                                                                                                                                                                                                                                                                                                                                  | Konsequenz für Realtime                                                                                                                                                                                          |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Web-Stack    | Spring Boot 4.1.2, **Spring MVC/Tomcat** (kein WebFlux), Virtual Threads **nicht** aktiv                                                                                                                                                                                                                                                | `SseEmitter` (MVC) passt ohne Stack-Wechsel. Async-Servlet hält beim Warten keinen Thread fest.                                                                                                                  |
| Modulith     | 2.1.2, JDBC-Event-Publication-Registry, keine Externalisierung                                                                                                                                                                                                                                                                          | Domain Events sind etabliert, nur `household` publiziert welche.                                                                                                                                                 |
| Events       | Nur `household` publiziert (`AccountRegistered`, `MemberRemoved`, `HouseholdDeleted`, `PasswordResetRequested`, …). **`tasks` und `expenses` publizieren nichts.**                                                                                                                                                                      | Neue Events für CRUD in `tasks`/`expenses` (inkl. Kategorien, Erstattungen) nötig.                                                                                                                               |
| Transaktion  | **`tasks` und `expenses` haben kein einziges `@Transactional`.** Ohne aktive Transaktion überspringt Spring jeden `AFTER_COMMIT`-Listener lautlos, das betrifft `@ApplicationModuleListener` und reinen `@TransactionalEventListener` gleichermaßen (AGENTS.md, Findings & Notes).                                                          | Jede publizierende Service-Methode braucht `@Transactional` (R2.0). Tests müssen die Ankunft im Stream prüfen, nicht die Publikation.                                                                            |
| Auth         | BFF mit `JSESSIONID` (httpOnly, Secure, SameSite=Lax), CSRF über `XSRF-TOKEN`, Same-Origin über Nginx. Zugriff pro Haushalt per `@PreAuthorize` + `HouseholdAccessGuard`. Anfragen mit `Accept` ≠ `text/html` bekommen 401 statt Login-Redirect.                                                                                       | `EventSource` sendet Same-Origin-Cookies automatisch, es ist kein Token-Handling nötig. Der Guard kann für den Stream wiederverwendet werden.                                                                     |
| Session-Ende | Logout invalidiert die Session (SES5). Member entfernt, Haushalt gelöscht (SES7) und Passwort-Reset (RESET1) beenden Sessions dagegen per `SessionRegistry` → `expireNow()` (`MemberSessions`). Das zerstört die Session **nicht**, erst der `ConcurrentSessionFilter` lehnt sie beim **nächsten Request** ab. Session-Timeout: Tomcat-Default 30 min. | Ein offener Stream stellt keinen neuen Request und würde ein `expireNow()` überleben. Ein `HttpSessionDestroyedEvent` kommt dabei nicht. Der Stream muss die Session-Gültigkeit selbst prüfen (R1.2, RT3).        |
| Zustand      | Sessions, `SessionRegistry` und Rate-Limiter (Bucket4j) liegen im Prozessspeicher                                                                                                                                                                                                                                                       | Das System ist faktisch **Single-Instance** (oder braucht Sticky Sessions). Eine In-Memory-Verteilung der Events ist damit konsistent. In Arc42 Kap. 11 ist das noch **nicht** dokumentiert.                       |
| Frontend     | SvelteKit 3 (Imports über `#lib/…` mit Dateiendung, kein `$lib`). `svelte/store`-Writables pro Entität mit `loadX()` und optimistischen Updates, abgeleitete Stores `financialStore`/`statisticsStore`. Rune-basierter Session-Zustand (`sessionState.svelte.ts`, `sessionGuard.ts`, `sessionLogout.ts`). Keine Polling-, Visibility-, SSE- oder WebSocket-Logik, **kein Service Worker** | Die vorhandenen `loadX()`-Funktionen sind der Andockpunkt für „neu laden". `sessionState` steuert, wann der Stream läuft. Bei Session-Ende greift der vorhandene `sessionGuard`-Flow.                             |
| Deployment   | Arc42 Kap. 7: Nginx (TLS, Reverse Proxy) + Backend + PostgreSQL. Kein Dockerfile, keine Nginx-Config im Repo, aber Abschnitt `section-reverse-proxy-requirements` mit verbindlichen Proxy-Vorgaben                                                                                                                                      | Streaming-Vorgaben werden dort ergänzt (R4.1).                                                                                                                                                                   |
| Doku         | Arc42 und ADRs liegen als Antora-Komponente unter `docs/architecture/modules/` (`arc42/pages/`, `adrs/pages/`), Build mit `npm run build:strict` in `docs/`                                                                                                                                                                             | Ein neues ADR muss in `adrs/pages/index.adoc` (zwischen den `adr-list`-Tags) **und** in `adrs/nav.adoc` eingetragen werden. Modulübergreifende Xrefs sind qualifiziert (`xref:arc42:…`).                          |

---

## 2. SOTA-Überblick und Optionen

### Option A: Polling / Refetch on Focus

Periodisch bzw. bei `visibilitychange`/`focus` neu laden.

- **+** Trivial, keine Infrastruktur, robust auf Mobile.
- **−** Nicht „direkt" (Latenz = Intervall), unnötige Last, skaliert schlecht mit Intervall ↓.
- **Einordnung:** Kein Ziel an sich, aber **Refetch beim Zurückkehren in den Tab ist auch bei jeder
  Push-Lösung Pflicht** (siehe Mobile-Befund unten).

### Option B: Server-Sent Events (SSE)

Eine langlebige `GET`-Antwort mit `text/event-stream`. Der Server pusht, der Client nutzt die
Browser-API `EventSource` mit eingebautem Auto-Reconnect und `Last-Event-ID`.

- **+** Reines HTTP: Cookies, Same-Origin, Spring Security, Nginx und Observability funktionieren wie
  bei jedem anderen Endpoint. Kein Protokoll-Upgrade, kein zusätzliches Messaging-Protokoll.
- **+** Mutationen laufen weiter über die bestehende REST-API (OpenAPI, CSRF, Validierung,
  generierter Client). Der Push-Kanal ist reiner **Benachrichtigungskanal**, genau der Anwendungsfall,
  für den SSE gebaut ist.
- **+** In Spring MVC ohne neue Dependency (`SseEmitter` ist Teil von `spring-webmvc`).
- **−** Nur Server→Client, nur Text. Für unseren Use-Case kein Nachteil.
- **−** Unter HTTP/1.1 max. ~6 Verbindungen pro Origin und Browser (mehrere Tabs!). Unter **HTTP/2 entfällt das
  praktisch** (Multiplexing), also HTTP/2 in Nginx aktivieren.
- **−** Die Servlet-API meldet Client-Abbrüche nicht. Ein **Heartbeat** (Kommentarzeile) ist nötig,
  damit tote Verbindungen beim nächsten Write auffallen (offizielle Spring-Doku).
- **−** `EventSource` liefert bei Fehlern keinen HTTP-Statuscode. Eine Antwort ≠ 200 löst nur `error`
  aus und beendet die Verbindung (`readyState = CLOSED`, kein Auto-Reconnect). Der Client muss die
  Ursache selbst per REST klären (R3.2).
- **SOTA-Einordnung:** Konsens 2025/26 ist „SSE für unidirektionale Updates, WebSockets nur bei echter
  Bidirektionalität". SSE erlebt durch LLM-Token-Streaming eine Renaissance, die Tooling- und
  Proxy-Unterstützung ist entsprechend gut.

### Option C: WebSocket (roh oder STOMP über Spring Messaging)

- **+** Bidirektional mit niedriger Latenz. STOMP bringt Topics, Heartbeats und einen Broker-Relay
  mit (Spring `spring-websocket`/`spring-messaging`).
- **−** Eigener Protokollpfad neben REST: Spring Security muss zusätzlich auf Message-Ebene
  konfiguriert werden (`@EnableWebSocketSecurity`, CSRF beim CONNECT). Cross-Site WebSocket
  Hijacking muss über Origin-Checks abgedeckt werden (ASVS 5.0 V4.4). Nginx braucht `Upgrade`-Header.
- **−** Neue Dependency und mehr Konzeptlast für Contributors (QG4), ohne dass wir die
  Rückrichtung brauchen: Schreiben läuft per REST.
- **Einordnung:** Erst sinnvoll bei kollaborativem Live-Editieren, Präsenz („Anna tippt…") oder
  Chat. Nichts davon steht im Feature-Set.

### Option D: Externer Realtime-Hub (Mercure, Centrifugo)

Der Backend-Code publiziert per HTTP an einen Hub, Clients abonnieren beim Hub (Mercure: SSE,
Centrifugo: WebSocket/SSE/…).

- **+** History/Replay, Presence und horizontale Skalierung sind gelöst.
- **−** Zusätzlicher Container. Der Auth-Pfad wird komplexer: Der Hub braucht eigene
  Subscriber-JWTs, was dem BFF-Prinzip „keine Tokens im Browser" (ADR-014) zuwiderläuft. Lizenzfragen (Mercure AGPL,
  Centrifugo MIT + PRO). Widerspricht QG3 (Easy to host).
- **Einordnung:** Überdimensioniert für Haushalte mit 2–10 Personen.

### Option E: Local-First Sync Engine (PowerSync, Electric, Zero)

Postgres wird per Logical Replication in eine Client-DB (SQLite/IndexedDB) synchronisiert,
Live-Queries machen Realtime „gratis".

- **+** Löst Realtime **und** Offline-First (Arc42 Kap. 1 nennt „Work offline and sync later", bisher
  nicht umgesetzt; ADR-006 legt nur die PWA fest). Aktuelle SOTA für local-first Apps. PowerSync ist
  der einzige der drei mit vollwertigem Offline-Write-Support. Zero (1.0 in 2026) hat Offline
  explizit nicht im Scope. Electric ist clientseitig read-only.
- **−** Fundamentaler Architekturwechsel: Umgeht die API-first-Schicht (ADR-010) für Lesezugriffe,
  eigene Sync-Rules/Autorisierung neben Spring Security, zusätzlicher Sync-Service, starke
  Abhängigkeit von einem Hersteller.
- **Einordnung:** Gehört in eine **eigene Offline-First-Entscheidung**, nicht in dieses Feature.
  Wichtig: Option B verbaut diesen Weg nicht.

### Nicht betrachtet

- **WebTransport (HTTP/3):** Die Browser- und Servlet-Container-Unterstützung reicht für einen
  Produktiveinsatz in einem Spring-MVC-Stack noch nicht.
- **Web Push (Push API / Service Worker):** Ist ein **anderes Feature** (Benachrichtigung bei
  geschlossener App) und ergänzt, ersetzt aber nicht. Wenn gewünscht, als eigener Meta-Plan.

### Entscheidungsmatrix

Bewertung: ++ sehr gut, + gut, o neutral, − schlecht, −− sehr schlecht.

| Kriterium                          | A Polling | **B SSE**                | C WebSocket/STOMP | D Hub | E Sync Engine |
|------------------------------------|-----------|--------------------------|-------------------|-------|---------------|
| Latenz „sofort"                    | −         | ++                       | ++                | ++    | ++            |
| Passt zu BFF/Cookie-Auth (ADR-014) | ++        | ++                       | o                 | −     | −             |
| Passt zu API-first (ADR-010)       | ++        | ++                       | +                 | +     | −−            |
| Neue Infrastruktur/Dependencies    | ++        | ++                       | +                 | −−    | −−            |
| Komplexität für Contributors       | ++        | +                        | o                 | −     | −−            |
| Mobile/PWA-Robustheit              | ++        | + (mit Resync)           | + (mit Resync)    | +     | ++            |
| Offline-First                      | o         | o                        | o                 | o     | ++            |
| Horizontale Skalierung             | ++        | o (später LISTEN/NOTIFY) | o (Broker-Relay)  | ++    | ++            |

### Empfehlung

**Option B: SSE als reiner Invalidierungskanal („notify, then refetch"), plus Resync bei
Reconnect und Sichtbarkeitswechsel.** Begründung:

1. Der Use-Case ist unidirektional, weil Schreibzugriffe bereits per REST laufen. Damit ist SSE das
   einfachste Werkzeug, das ihn vollständig löst (KISS).
2. Es fügt sich in die bestehende Security ein: Same-Origin-Cookie, Spring-Security-Filter-Chain,
   vorhandener `HouseholdAccessGuard`. CSRF ist für einen seiteneffektfreien GET nicht relevant
   (RT8). Die neuen Angriffsflächen (Stream überlebt Session-Ende, Session-Verlängerung durch
   Reconnect) sind in Abschnitt 4 adressiert.
3. Keine neue Dependency, kein neuer Container.
4. Der Weg zu Offline-First (Option E) oder WebSocket (Option C) bleibt offen. Der Event-Vertrag (R0.2)
   ist transportunabhängig.

---

## 3. Querschnittliche Designentscheidungen (in R0 im ADR festzuhalten)

- **Dünne Events („Invalidation Signals") statt Fat Events.** Ein Event besteht aus dem Event-Typ
  (SSE-Feld `event:`) und dem Payload `{entity, entityId, occurredAt}` (SSE-Feld `data:`), keine
  Fachdaten. Der Client lädt die betroffene Ressource per REST nach.
    - `householdId` steht nicht im Payload: Der Kanal **ist** der Haushalt. `actorMemberId` entfällt,
      weil fremde Änderungen still aktualisiert werden (R3.5) und das Feld sonst niemand auswertet
      (Datensparsamkeit, RT11). Ein späterer Toast kann es wieder einführen.
    - Autorisierung bleibt **an genau einer Stelle** (REST-Endpoints). Der Stream kann keine Daten
      leaken.
    - Keine doppelte Serialisierungslogik und kein Versionsdrift zwischen Event- und REST-Schema.
    - Abgeleitete Daten (Salden, Statistiken) werden über ihre normalen Endpoints korrekt neu
      berechnet. Es gibt **kein eigenes Event `financials.changed`**: Der Client lädt bei
      `expense.*`, `reimbursement.*` und `member.*` die abgeleiteten Stores (`financialStore`,
      `statisticsStore`) mit.
    - Der Mehraufwand eines Requests pro Änderung ist bei Haushaltsgrößen vernachlässigbar.
    - **Bewusst verworfen: fette Events** (Event trägt die geänderten Daten). Sie sparen den
      Refetch, bringen aber diese Kosten mit:
        - Abgeleitete Daten fließen nicht mit, etwa `Expense.isMutable` nach einer Erstattung oder die
          Salden, die relativ zum anfragenden Nutzer berechnet werden.
        - Sie brauchen Entitäts-Versionen gegen Reihenfolgefehler. Aktuell gibt es keine
          `version`/`updated_at`-Spalten.
        - Die Autorisierung muss pro Empfänger geprüft werden.
        - Base64-Bilder (`Household.image`, `Member.avatar`) landen ggf. im Payload.
        - Jeder Store braucht eine Merge-Logik.
        - Der Resync nach einem Verbindungsabbruch bleibt trotzdem nötig.

      Bei wenigen Nutzern pro Haushalt überwiegt der Nutzen nicht. Ein späterer Umstieg einzelner
      Event-Typen auf fette Payloads bleibt möglich.
    - **Bewusst verworfen: Commands per WebSocket** (Client sendet Commands über den Socket, das
      Backend broadcastet das Ergebnis-Event). Gründe:
        - Die Schreibrichtung verließe OpenAPI und den generierten Client.
        - Ack-, Error- und Correlation-Handling, Idempotenz bei Abbrüchen und ein zweiter
          Security-Pfad (CSRF beim CONNECT, Autorisierung pro Nachricht, Rate-Limiting) müssten
          selbst gebaut werden.
        - Die erste Aktion nach dem Aufwachen auf Mobile wartet auf den Reconnect.
        - Ein Service Worker kann keine Socket-Nachrichten puffern (Offline-First).

      Lohnt sich erst bei Live-Kollaboration, Präsenzanzeige oder Chat.
- **Kein Replay im ersten Schritt.** Nach (Re-)Connect lädt der Client die aktiven Stores komplett
  neu. Das ist robuster als `Last-Event-ID`-Replay, das einen serverseitigen Event-Puffer
  bräuchte. Replay bleibt Option (R5.2).
- **Kanal = Haushalt, Pfad `/v1/household/{householdId}/events`.** Wegen ADR-012 hat jede Verbindung
  genau einen Haushalt. Der Pfad folgt dem Schema aller REST-Endpoints. Die `householdId` aus dem
  Pfad wird **vor dem ersten Byte** per `@PreAuthorize("@householdAccessGuard.isMember(...)")` gegen
  den Principal geprüft und erst danach für die Registrierung des Emitters verwendet (RT1, IDOR).
  Endet die Mitgliedschaft später, schließen `MemberRemoved` und der Heartbeat-Check den Stream
  (R1.4, RT3).
- **Veröffentlichung erst nach Commit.** Andere Clients dürfen keinen Stand nachladen, der nie
  committet wurde. Voraussetzung: Die publizierende Methode läuft in einer Transaktion (R2.0).
- **Eigenes Echo:** Der auslösende Client hat bereits optimistisch aktualisiert. Ein Refetch ist
  idempotent und damit unkritisch. Optional unterdrückt eine `X-Client-Id` pro Tab das Echo (R3.3).
- **Lebensdauer koppelt an Session und Mitgliedschaft, und der Stream verlängert die Session nicht.**
  Bei Logout, Session-Ablauf, `expireNow()` (SES7, RESET1), `MemberRemoved` und `HouseholdDeleted`
  wird der Emitter serverseitig geschlossen. Ein offener Tab gilt nicht als Aktivität. Das
  Inaktivitäts-Timeout der Session bleibt wirksam (RT3, RT4).

---

## 4. Security-Controls für den Stream (ASVS 5.0 L2, OWASP Top 10 2025)

Die Controls werden in R4.2 in die Tabelle „Security Controls" von Arc42 Kap. 8 übernommen,
jeweils mit Status und verifizierendem Test. Kap. 8 führt bisher nur Authentifizierungs-Controls.
Für den Stream bekommt die Tabelle eine eigene Gruppe oder einen eigenen Abschnitt („Security
Controls (Realtime)"). Die ASVS-Referenzen werden bei der Übernahme gegen ASVS 5.0 geprüft.
Angaben auf Kapitelebene (z. B. „V16") werden dabei auf Einzelanforderungen präzisiert.

| ID   | Control                                                                                                                                                                                                                                                                                                                        | Top 10 2025 | ASVS 5.0                         | Umsetzung   |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------|----------------------------------|-------------|
| RT1  | Zugriff nur für Mitglieder des Haushalts aus dem Pfad: `authenticated()` plus `HouseholdAccessGuard.isMember`, geprüft vor dem ersten Byte. Ein fremder Haushalt bekommt 403 und keinen Stream. Ergänzt das noch offene Control AC1 in Kap. 8.                                                                              | A01         | V8.2.1, V8.2.2                   | R1.1, R1.2  |
| RT2  | Der Stream liefert nur dünne Signale ohne Fachdaten. Was der Empfänger sehen darf, entscheidet ausschließlich der REST-Endpoint beim Refetch.                                                                                                                                                                                  | A01         | V8.2.2, V14 (Datenminimierung)   | R0.2, R2.4  |
| RT3  | Kein Stream überlebt das Ende seiner Session oder Mitgliedschaft: Der Emitter merkt sich seine Session-ID. Bei jedem Heartbeat-Tick wird geprüft, ob die Session noch gültig und in der `SessionRegistry` nicht abgelaufen ist (`SessionInformation.isExpired()`). Sonst wird der Emitter geschlossen. Das deckt Logout (SES5), Member entfernt bzw. Haushalt gelöscht (SES7) und Passwort-Reset (RESET1) ab, auch künftige `expireNow()`-Pfade. `MemberRemoved`/`HouseholdDeleted` schließen zusätzlich sofort (R1.4). | A07         | V7.4.1, V7.4.2, V7.4.3           | R1.2, R1.4  |
| RT4  | Der Stream verlängert die Session nicht über ihr Inaktivitäts-Timeout hinaus (Entscheidung 2026-11-07). Der Client trennt bei Leerlauf (R3.2). Das Emitter-Timeout liegt unter dem Session-Timeout. Ein Spike (R1.3) verifiziert, ob ein laufender Async-Request und der Reconnect die Session berühren.                    | A07         | V7.3.1                           | R1.3, R3.2  |
| RT5  | Begrenzte Ressourcen: Limit pro Session und pro Member plus ein globales Limit. Bei Überschreitung 429 mit `Retry-After`, analog zu RATE1. Reconnect mit serverseitigem `retry:` und clientseitigem Jitter, damit nach einem Neustart nicht alle Clients gleichzeitig verbinden und resyncen.                              | A06         | V2.4 (Anti-Automation), V6.1.1   | R1.5, R3.2  |
| RT6  | Kein Framing-Injection: `event:` stammt nur aus einem festen Enum. `data:` ist ausschließlich serialisiertes JSON ohne Zeilenumbrüche. Der Client wertet `data` nur als Daten aus, nie als HTML oder Skript, und rendert nichts davon direkt (HTML5 Security Cheat Sheet, „Server-Sent Events").                         | A05         | V1 (Encoding), V3                | R1.1, R3.1  |
| RT7  | Feste Stream-URL im Client (same-origin, nie aus Nutzereingaben gebaut), Prüfung von `event.origin` gegen den eigenen Origin. CORS bleibt wie CORS1 auf eine Allow-List beschränkt, keine Wildcard mit Credentials. Bei Cross-Origin-Betrieb braucht `EventSource` `withCredentials`.                                  | A01, A02    | V3.4.2                           | R3.1        |
| RT8  | Der Stream-GET hat **keine Seiteneffekte** (er markiert z. B. nichts als gelesen). Nur dann ist es unkritisch, dass CSRF-Schutz für GET nicht greift und ein SameSite=Lax-Cookie bei Top-Level-Navigationen mitgeschickt wird.                                                                                               | A01         | V3.5 (CSRF)                      | R1.1        |
| RT9  | Sichere Stream-Konfiguration: `Cache-Control: no-store`, `X-Accel-Buffering: no`, keine puffernde Kompression am Proxy. Der Stream-Pfad liegt unter `/v1/**` und wird damit von den bestehenden Proxy-Vorgaben in Kap. 7 erfasst.                                                                                            | A02         | V13 (Konfiguration), V14 (Caching) | R1.1, R4.1 |
| RT10 | Security-Logging: Öffnen und Schließen eines Streams (memberId, Grund: Logout, abgelaufen, entfernt, Timeout, Client weg), abgelehnte Verbindungen (401/403) und Limit-Überschreitungen. Keine Session-IDs und keine Payloads im Log.                                                                                         | A09         | V16                              | R1.5        |
| RT11 | Datensparsamkeit im Payload: nur Felder, die der Client auswertet (`entity`, `entityId`, `occurredAt`).                                                                                                                                                                                                                         | A01         | V14                              | R0.2        |
| RT12 | Robuste Fehlerpfade: Eine `IOException` bei einem toten Client entfernt nur diesen Emitter und bricht den Broadcast an andere nicht ab. Fehler im Listener rollen die fachliche Transaktion nicht zurück (läuft nach Commit). Fehler beim Verbindungsaufbau liefern eine definierte Antwort (Status plus ProblemDetail, sofern verhandelbar) ohne interne Details (ERR1). | A10         | V16.5.1                          | R1.1, R1.6  |

Ergänzend für neue Dependencies (R0.5): Versionen pinnen, GitHub Actions per Commit-SHA einbinden,
Dependabot-Abdeckung sicherstellen, Lizenz prüfen (A03 Software Supply Chain Failures, ASVS V15).

Maßgebliche OWASP Cheat Sheets (in den Implementierungsplänen jeweils als Checkliste nutzen):
HTML5 Security („Server-Sent Events"), Session Management, Authorization, Logging, Denial of
Service. Das WebSocket Security Cheat Sheet dient nur zur Abgrenzung (Option C).

---

## Phase R0: Entscheidungen (nur Doku, kein Feature-Code)

- ✅ **R0.1 ADR-019: Realtime-Transport.** Erledigt 2026-11-07: `adr-019.adoc` („Realtime Updates
  via Server-Sent Events (Notify, then Refetch)"), eingetragen in Index und Navigation. ADR-011 hat
  einen `NOTE`-Nachtrag zur Ausnahme für `realtime`. Ursprüngliche Aufgabe: Optionen A–E aus diesem Dokument, Entscheidung SSE +
  Notify-then-Refetch, Pfad und Kanal (Abschnitt 3), „offener Tab ≠ Aktivität", Abgrenzung zu Web
  Push und Offline-First. Bezug: QG2, QG3, QG4. Ablage `docs/architecture/modules/adrs/pages/adr-019.adoc`
  nach `adrs/partials/template.adoc`, eingetragen in `adrs/pages/index.adoc` (zwischen den
  `adr-list`-Tags, von Kap. 9 inkludiert) **und** in `adrs/nav.adoc`. Falls R0.4 vom
  `@ApplicationModuleListener` abweicht, hält ADR-019 die Abweichung von ADR-011 ausdrücklich fest
  (ggf. mit Nachtrag in ADR-011).
- ✅ **R0.2 Event-Vertrag in AsyncAPI.** Erledigt 2026-11-07: `api/asyncapi.yml` (AsyncAPI 3.1.0),
  validiert ohne Befund. Abweichung vom Entwurf unten: `session.terminated` trägt nur
  `{occurredAt}`, weil die einzige passende ID die Session-ID wäre, die das httpOnly-Cookie nie
  verlassen darf (ADR-014, RT11). `member.removed` geht an die verbleibenden Mitglieder, das entfernte
  Mitglied bekommt `session.terminated`. Ursprüngliche Aufgabe: Der gesamte SSE-Teil wird in einer neuen Spezifikation
  `api/asyncapi.yml` (AsyncAPI 3.x) beschrieben, analog zu `../../api/openapi.yml` für REST (API first,
  ADR-010). Die AsyncAPI-Spec ist die **Single Source of Truth** für den Stream. Der Endpoint wird **nicht** zusätzlich
  in `openapi.yml` gepflegt, damit nichts doppelt existiert. Inhalt:
    - **Server/Channel:** `/v1/household/{householdId}/events` mit Channel-Parameter `householdId`
      (UUID, benannt wie in `openapi.yml`) und HTTP-Binding (`GET`, Response
      `Content-Type: text/event-stream`). Dazu Security-Beschreibung (Session-Cookie, siehe ADR-014,
      Membership-Prüfung, RT1) und Fehlerfälle beim Verbindungsaufbau (401, 403, 429).
    - **Operation:** `receive`, ausschließlich Server→Client.
    - **Messages:** ein Message-Typ pro Event, Scope v1:
        - `task.created|updated|deleted|done`
        - `expense.created|updated|deleted`
        - `reimbursement.created|updated|deleted`
        - `category.created|updated|deleted`
        - `member.joined|updated|removed`
        - `household.updated|deleted`
        - `session.terminated`

      Der Event-Typ entspricht dem SSE-Feld `event:` (festes Enum, RT6), der Payload
      `{entity, entityId, occurredAt}` dem Feld `data:` (RT11). Dazu `retry:`, Heartbeat-Kommentare
      und die Semantik „bei `open` immer Resync" dokumentieren.
    - **Konventionen wie bei OpenAPI:** Jedes Schema und jede Property bekommt ein `example`. Die
      Spec wird vor jedem Commit validiert (siehe R0.5).
    - Fachdaten-Schemas werden wegen der dünnen Events nicht referenziert.
- ✅ **R0.3 Modulschnitt.** Erledigt 2026-11-07: Neues Modul `realtime` (in ADR-019 beschrieben,
  angelegt wird es in R1.1 samt `package-info.java`). Das leere `events`-Verzeichnis ist gelöscht. Das
  Modul `session` (ADR-017, Pre-Redirect-Session) hat keine Überschneidung. Ursprüngliche Aufgabe: Vorschlag: neues Modul `realtime` (kennt die öffentlichen Event-Records von
  `tasks`/`expenses`/`household`, wird von niemandem referenziert). Fachmodule wissen nichts von
  Realtime (ADR-011). Die Abhängigkeiten von `realtime` werden gleich per `package-info.java` mit
  `@ApplicationModule(allowedDependencies = …)` deklariert, damit TD1 nicht wächst. Das leere
  `events`-Package (nur das Verzeichnis existiert) ist zu klären: löschen oder fachlich belegen
  (Kalender-Events), um Verwechslungen mit `realtime` zu vermeiden. Zu prüfen ist auch die Abgrenzung
  zum Modul `session`.
- ✅ **R0.4 Listener-Semantik klären.** Erledigt 2026-11-07, Entscheidung: synchroner
  `@TransactionalEventListener(phase = AFTER_COMMIT)` ohne Registry-Eintrag. **Spike-Ergebnis**
  (Quellcode `spring-modulith-events-core` 2.1.2, `EventListenerMethodMetadata`): Standardmäßig
  erzeugt **jeder** `AFTER_COMMIT`-Transactional-Listener einen Eintrag in `event_publication`, nicht nur
  `@ApplicationModuleListener`. Seit 2.1.2 schränkt
  `spring.modulith.events.registry-trigger-annotation=org.springframework.modulith.events.ApplicationModuleListener`
  das auf annotierte Listener ein. Alle bestehenden `AFTER_COMMIT`-Listener nutzen bereits
  `@ApplicationModuleListener`, `AccountLockoutListener` ist ein reiner `@EventListener` und nicht
  betroffen. Die Property wird mit dem ersten `realtime`-Listener gesetzt (R2.4). Ein IT muss
  belegen, dass für ihn keine Zeile in `event_publication` entsteht. Ursprüngliche Aufgabe: `@ApplicationModuleListener` persistiert jede Publikation in
  `event_publication` und wiederholt sie nach einem Neustart. Für flüchtige Push-Signale ist das
  unnötig (Clients resyncen ohnehin beim Reconnect). Zu entscheiden: synchroner
  `@TransactionalEventListener(phase = AFTER_COMMIT)` ohne Registry-Eintrag vs. Modulith-Standard
  (ADR-011). Mit Spike verifizieren, ob die Modulith-Registry auch reine `@TransactionalEventListener`
  erfasst. Beide Varianten brauchen eine aktive Transaktion beim Publizieren (R2.0).
- ✅ **R0.5 AsyncAPI-Tooling.** Erledigt 2026-11-07: `@asyncapi/cli` 6.2.3 (Apache-2.0, exakt
  gepinnt, älter als der Dependabot-Cooldown von 5 Tagen) als devDependency in `api/package.json`,
  `npm run validate` mit `--fail-severity=info`. CI-Job `API` (`api-ci.yml`) wird aus `ci.yml`
  aufgerufen und steht in `ci-result.needs`. Dependabot deckt `/api` ab. Keine Codegenerierung,
  kein Doku-Rendering, weil die OpenAPI in der Antora-Site auch nicht gerendert wird. Die CLI sendet
  lokal sonst anonyme Metriken (nur bei `CI=true` nicht). Das Skript setzt deshalb
  `ASYNCAPI_METRICS_CONFIG_PATH` auf die eingecheckte `api/asyncapi-analytics.json` mit
  deaktivierter Analytics. Ursprüngliche Aufgabe: Festlegen, wie die Spec validiert und ob Code generiert wird. **Jede Option ist eine neue
  Dependency und braucht vorher Rückfrage** (globale Regel). Bei jeder Option gelten die
  Supply-Chain-Vorgaben aus Abschnitt 4 (Pinning, SHA, Dependabot, Lizenz):
    - **Validierung:** AsyncAPI CLI (`asyncapi validate`) lokal und in CI. Der CI-Job wird aus
      `ci.yml` aufgerufen und in `ci-result.needs` aufgenommen (siehe AGENTS.md, „CI result is the only
      required check").
    - **Codegenerierung** (optional, nur wenn sie den Aufwand rechtfertigt):
        - Typen für Frontend (TypeScript) und Backend (Java-Records für die Push-Payloads), z. B.
          über Modelina.
        - **Zu verifizieren:** AsyncAPI-3.x-Unterstützung der aktuellen Versionen. In der Recherche
          tauchten teils nur 2.x-Angaben auf.
        - Bei wenigen, flachen Payloads ist handgeschriebener Code, gegen die Spec reviewt, eine
          legitime Alternative.
    - **Kein code-first Tooling** (z. B. Springwolf, das AsyncAPI aus Code erzeugt). Das widerspricht
      API first.
    - **Doku-Rendering** (z. B. AsyncAPI-HTML-Template) in die Antora-Site einbinden, analog zur
      OpenAPI-Doku, falls diese dort gerendert wird.

## Phase R1: Backend-Fundament

- **R1.1 Stream-Endpoint und Emitter-Registry.** Implementierungsplan für R1.1–R1.6: [`realtime-plan-r1.md`](realtime-plan-r1.md). `GET /v1/household/{householdId}/events` liefert
  `SseEmitter`, geschützt durch `@PreAuthorize` mit `HouseholdAccessGuard.isMember` (RT1).
  Thread-sichere Registry `householdId → Set<Emitter>`. Jeder Emitter kennt Session-ID und memberId
  (für RT3, R1.4). Cleanup über `onCompletion`/`onTimeout`/`onError`. Response-Header nach RT9.
  Event-Typen nur aus dem Enum, `data` nur als JSON (RT6). Keine Seiteneffekte (RT8).
- **R1.2 Spring-Security-Integration.** Der Endpoint liegt hinter `authenticated()` (bzw. dem
  `AccountPrincipalAuthorizationManager`). Der SecurityContext muss auch bei `ASYNC`-Dispatches
  erhalten bleiben (bekannte Falle: ein Access Denied nach dem ersten Event bzw. „response is already
  committed"). Authentifizierung und Membership werden **vor** dem ersten Byte geprüft. Wegen
  `Accept: text/event-stream` greift der 401-Entry-Point, nicht der Login-Redirect. Das muss ein Test
  absichern. Session-Ende: **nicht** auf `HttpSessionDestroyedEvent` verlassen, weil `expireNow()`
  (SES7, RESET1) keins auslöst. Stattdessen der Session-Check im Heartbeat (RT3).
- **R1.3 Heartbeat, Timeouts und Session-Verhalten.** Kommentar-Heartbeat (Vorschlag: alle 25 s, unter Nginx'
  Default `proxy_read_timeout` 60 s). Jeder Tick prüft die Session (RT3). Server-seitiges
  `retry:`-Feld für das Reconnect-Backoff. **Spike vorab (RT4):** Berührt ein laufender
  Async-Request bzw. der Reconnect die Tomcat-Session (`lastAccessedTime`), und läuft die Session
  während eines offenen Streams ab? Daraus folgt:
    - Emitter-Timeout **unter** dem Session-Timeout (heute 30 min Tomcat-Default). Ein offener
      Stream darf eine abgelaufene Session nie weiter bedienen.
    - Der Stream darf die Session nicht verlängern. Falls der Spike zeigt, dass der Reconnect sie
      verlängert, reicht das clientseitige Trennen bei Leerlauf (R3.2). Der Server darf dem nicht
      widersprechen, etwa durch Session-Zugriffe im Heartbeat.
    - Das Session-Timeout wird explizit konfiguriert (`server.servlet.session.timeout`) und an das
      Frontend kommuniziert (z. B. per `/auth/discover` oder `/me`, in der OpenAPI zu ergänzen),
      damit der Client seine Leerlauf-Grenze daran ausrichtet.
- **R1.4 Lifecycle-Kopplung.** Listener auf `MemberRemoved` und `HouseholdDeleted` schließen
  Emitter sofort (vorher `session.terminated` senden). Logout schließt die Emitter der Session. Für
  alle übrigen Fälle (Passwort-Reset, Session-Timeout, künftige `expireNow()`-Pfade) greift der
  Heartbeat-Check aus RT3.
- **R1.5 Ressourcen, Logging und Observability.** Limits pro Session, pro Member und global (RT5),
  Antwort 429 mit `Retry-After`. Security-Logging nach RT10. Micrometer-Gauge „aktive Streams",
  Counter „gesendete Events" und „geschlossene Streams nach Grund". Prüfen, ob
  `spring.threads.virtual.enabled` für Heartbeat/Broadcast sinnvoll ist (nicht zwingend, da
  Async-Servlet).
- **R1.6 Integrationstests.** `*IT` mit echtem Server (`RANDOM_PORT`) und HTTP-Client, der den
  Stream liest. Ausführen mit `./mvnw verify`, nicht `test`. Fälle:
    - Event kommt nach Commit an, kein Event bei Rollback
    - fremder Haushalt im Pfad → 403, kein Stream (RT1)
    - unauthentifiziert → 401, kein Login-Redirect
    - Member entfernt → `session.terminated` und Stream endet
    - Passwort-Reset bzw. `expireNow()` → Stream endet spätestens beim nächsten Heartbeat (RT3)
    - Limit überschritten → 429 (RT5)
    - toter Client bricht den Broadcast an andere nicht ab (RT12)
    - Payload mit Zeilenumbruch erzeugt kein zusätzliches Event (RT6)

  Gesendete Events werden gegen die Message-Schemas aus `api/asyncapi.yml` geprüft (Vertragstreue).

## Phase R2: Domain Events in den Fachmodulen

- **R2.0 Transaktionen.** Jede Service-Methode in `tasks` und `expenses`, die ein Event publiziert,
  bekommt `@Transactional` (Aufruf über den Spring-Proxy). Ohne Transaktion verwirft Spring das
  Event für `AFTER_COMMIT`-Listener lautlos (AGENTS.md, Findings & Notes). Die Tests prüfen den
  echten Effekt (Event kommt im Stream an, R1.6), nicht nur `publishEvent`.
- **R2.1 `tasks`:** Events für Create, Update, Delete und Done-Toggle im Root-Package publizieren.
- **R2.2 `expenses`:** Events für Expenses, Kategorien und Erstattungen.
- **R2.3 `household`:** fehlende Events ergänzen (Household umbenannt, Member beigetreten oder
  geändert, Admin gewechselt). `MemberRemoved`/`HouseholdDeleted` existieren bereits.
- **R2.4 `realtime`-Listener:** Mapping Fach-Event → Push-Signal (nur Felder nach RT11) und
  Broadcast an den Haushalt. Dazu `spring.modulith.events.registry-trigger-annotation` setzen (siehe
  R0.4). Der Broadcast darf nie in den Publisher werfen und ihn bei langsamen Clients nicht
  blockieren, weil der Listener synchron im publizierenden Thread läuft (ADR-019). `ApplicationModules.verify()` muss grün bleiben (Zyklusfalle siehe
  AGENTS.md, P2.2-Befund).

## Phase R3: Frontend

- **R3.1 Realtime-Client (`#lib/realtime/…`).** Kapselt `EventSource` mit fester, same-origin URL
  (RT7), prüft `event.origin` und behandelt `data` nur als Daten (RT6). Event-Typen und Payloads
  kommen aus `api/asyncapi.yml`, generiert oder handgeschrieben je nach R0.5. Mappt Event-Typ →
  vorhandene `loadX()`-Funktionen, inklusive der abgeleiteten Stores (`financialStore`,
  `statisticsStore`) bei `expense.*`, `reimbursement.*` und `member.*`. Debounce/Coalescing, damit
  viele Events in kurzer Zeit nur einen Refetch pro Store auslösen. Lebenszyklus gekoppelt an
  `sessionState`: verbinden bei `authenticated`, trennen bei Logout bzw. `guest`.
- **R3.2 Resync, Fehler und Leerlauf.**
    - Bei `open` (auch Reconnect) und bei `visibilitychange → visible` alle aktiven Stores neu laden,
      mit Jitter (RT5).
    - Mobile-Befund: iOS-PWAs töten Verbindungen im Hintergrund, ohne zuverlässig `error` zu feuern
      (iOS 18). Daher bei Rückkehr in den Vordergrund nach längerer Hintergrundzeit die alte Instanz
      schließen und neu verbinden.
    - **Fehler:** `EventSource` sieht keinen Statuscode. Bei `error` mit `readyState === CLOSED` ruft
      der Client `/me` per REST auf. Damit greift der vorhandene `sessionGuard`-Flow (401 → Login mit
      `reason=session-expired`). Bei 403 oder 429 wird mit Backoff erneut verbunden, bei 403 erst nach
      dem Resync.
    - **Leerlauf (RT4):** Ohne Nutzerinteraktion (Eingabe, Zeigen, Tab sichtbar) schließt der Client
      den Stream kurz vor Ablauf des Session-Timeouts (Wert aus R1.3). Bei der nächsten Interaktion
      verbindet er neu und resynct. Ist die Session inzwischen abgelaufen, führt das über den Fehlerpfad
      zum Login. Konsequenz, bewusst akzeptiert: Ein unbedientes Gerät (z. B. Wand-Tablet) zeigt nach
      dem Session-Timeout keine Live-Änderungen mehr.
- **R3.3 Konflikt mit optimistischen Updates.** Ein eingehender Refetch darf ein noch laufendes
  optimistisches Update nicht überschreiben bzw. „zurückflackern" lassen. Strategie festlegen (z. B. Refetch für
  Entitäten mit ausstehender eigener Mutation zurückstellen, oder Echo per
  `X-Client-Id` unterdrücken).
- **R3.4 Mock-Server.** `npm run dev` nutzt mokapi, das bisher keinen Event-Stream liefert.
  **Entscheidung (2026-11-07): mokapi wird um einen SSE-Endpoint erweitert** (Freigabe für
  `api/mokapi.js` nur für diesen Zweck). Der Endpoint liefert die Message-Typen aus
  `api/asyncapi.yml`, inklusive Heartbeat und `retry:`, und schickt nach mokapi-Mutationen das
  passende Signal. Die genaue Umsetzung (unterstützt mokapi `text/event-stream`, oder ist ein
  Vite-Dev-Middleware-Endpoint nötig?) wird im Implementierungsplan geklärt. Falls mokapi es nicht
  kann, gibt es eine Rückfrage, bevor auf eine Alternative ausgewichen wird.
- **R3.5 UX.** **Entscheidung (2026-11-07): stilles Aktualisieren**, keine Toasts, keine
  Hervorhebung. Damit entfallen Paraglide-Keys und `actorMemberId` im Payload. Ein späterer Toast
  bräuchte `actorMemberId` wieder und darf Texte nur aus per REST nachgeladenen Daten bilden, nie
  aus dem Event (RT6).
- **R3.6 Mehrere Tabs.** Unter HTTP/2 unkritisch. Optional eine Verbindung pro Browser via
  `BroadcastChannel`-Leader-Election, nur falls Messungen das rechtfertigen.

## Phase R4: Betrieb und Doku

- **R4.1 Reverse-Proxy-Vorgaben:** im Abschnitt `section-reverse-proxy-requirements` von
  `docs/architecture/modules/arc42/pages/07_deployment_view.adoc` ergänzen. Für den Stream-Pfad gelten:
    - `proxy_buffering off` (bzw. Header `X-Accel-Buffering: no` vom Backend)
    - `proxy_read_timeout` größer als das Heartbeat-Intervall
    - HTTP/2 am Client-Listener
    - keine Komprimierung, die puffert

  Die Vorgaben müssen ohne Nachdenken übernehmbar sein (QG3/USC8).
- **R4.2 Arc42** (Antora, `docs/architecture/modules/arc42/pages/`, Build mit
  `npm run build:strict`):
    - Kap. 5: Baustein `realtime`, Kanten in der Bounded Context Map
    - Kap. 6: Laufzeitsicht „Task anlegen → andere Clients", inklusive Session-Ende → Stream
      geschlossen
    - Kap. 8: Controls RT1–RT12 aus Abschnitt 4 mit Status und verifizierenden Tests. Den Status von
      AC1 aktualisieren.
    - Kap. 11: Single-Instance-Einschränkung als neues Risiko, weil Sessions, Rate-Limiter und
      Emitter-Registry im Prozessspeicher liegen (mit Verweis auf R5.1)
    - Kap. 11: die Abweichung aus R0.4 vom Modulith-Standard und ihre Konsequenz (keine
      Wiederholung verlorener Push-Signale, abgefangen durch Resync)
- **R4.3 AGENTS.md:** Der AsyncAPI-Workflow (Ort der Spec, Validierung, Regel „Vertrag zuerst",
  Telemetrie) steht seit R0.5 im Abschnitt „API Updates". Offen bleiben die Findings aus der Umsetzung (z. B. Security-Async-Dispatch, Session-Verhalten
  aus dem Spike in R1.3, Testmuster für SSE). Dazu der AsyncAPI-Workflow analog zu „API Updates":
    - wo die Spec liegt
    - wie sie validiert wird
    - wie Code neu generiert wird (falls R0.5 Generierung vorsieht)
    - Regel: Änderungen am Event-Vertrag immer zuerst in `api/asyncapi.yml`
    - die mokapi-Ausnahme aus R3.4 (welcher Teil von `api/mokapi.js` geändert werden darf)

## Phase R5: Ausbau (bewusst später, nur auf Entscheidung)

- **R5.1 Multi-Instance-Fan-out** über PostgreSQL `LISTEN/NOTIFY` (keine neue Infrastruktur). Erst
  relevant, wenn Sessions/Rate-Limiting ebenfalls clusterfähig werden. Sonst ist es Scheinarbeit.
- **R5.2 Replay via `Last-Event-ID`** mit kurzem Ringpuffer, falls Voll-Resync messbar stört.
- **R5.3 Web Push** für Benachrichtigungen bei geschlossener App (eigener Meta-Plan).
- **R5.4 Offline-First/Sync-Engine** (Option E) als eigene Architekturentscheidung zu Kap. 1
  („Work offline and sync later") und ADR-006.
- **R5.5 Hinweise auf fremde Änderungen** (Toast oder Hervorhebung), falls sich stilles
  Aktualisieren als zu unauffällig erweist. Braucht `actorMemberId` im Payload (RT11 neu bewerten).

---

## Offene Fragen an den Nutzer (vor R0.1)

1. ✅ ~~Ist die Empfehlung (SSE + Notify-then-Refetch) als ADR-Richtung bestätigt?~~ Ja, plus
   AsyncAPI für den SSE-Teil (2026-10-11).
2. ✅ ~~Welche Entitäten gehören in Scope v1?~~ Tasks, Expenses, Erstattungen, Kategorien,
   Mitglieder, Haushalt (2026-11-07).
3. ✅ ~~Mock-Server (R3.4): Wie soll ohne echtes Backend entwickelt werden?~~ mokapi wird um einen
   SSE-Endpoint erweitert, Freigabe erteilt (2026-11-07).
4. ✅ ~~Sollen fremde Änderungen sichtbar angekündigt werden?~~ Nein, still aktualisieren (2026-11-07).
5. ✅ ~~Stream-Pfad mit oder ohne `householdId`?~~ Mit, `/v1/household/{householdId}/events` plus
   `HouseholdAccessGuard` (2026-11-07).
6. ✅ ~~Zählt ein offener Tab als Aktivität?~~ Nein, der Client trennt bei Leerlauf, das
   Inaktivitäts-Timeout bleibt wirksam (2026-11-07).

## Quellen

- Spring Framework Reference, „Async Requests": SSE, Disconnects/Heartbeat:
  https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html
- SSE vs. WebSockets 2026: https://oneuptime.com/blog/post/2026-01-27-sse-vs-websockets/view
- WebSocket vs. SSE: https://websocket.org/comparisons/sse/
- HTTP/2-Multiplexing für SSE: https://dev.to/stevenacoffman/http2-streaming-and-you-http3-streaming-and-wheee-23hb
- MDN `EventSource.withCredentials`: https://developer.mozilla.org/en-US/docs/Web/API/EventSource/withCredentials
- WHATWG HTML, Server-Sent Events (Fehlerbehandlung, keine Wiederverbindung bei Status ≠ 200):
  https://html.spec.whatwg.org/multipage/server-sent-events.html
- Spring Security + SSE,
  Async-Dispatch: https://dev.to/junyoungmoon/solving-access-denied-issues-after-sse-implementation-in-spring-security-3nd4,
  https://github.com/spring-projects/spring-security/issues/12758
- iOS 18 EventSource im Hintergrund: https://developer.apple.com/forums/thread/765183
- Notify-then-Refetch-Muster: https://tkdodo.eu/using-web-sockets-with-react-query
- Spring Modulith 2.1 Release: https://spring.io/blog/2026/06/11/spring-modulith-2-1-ga-2-0-7-and-1-4-12-released/
- Postgres LISTEN/NOTIFY in Spring Boot: https://dzone.com/articles/leveraging-postgres-listennotify-in-spring-boot
- Mercure vs.
  Centrifugo: https://www.pistack.xyz/posts/centrifugo-vs-mercure-vs-soketi-self-hosted-websocket-push-server-2026/,
  https://mercure.rocks/docs/1.0/reference/faq
- AsyncAPI: SSE über HTTP-Bindings
  (Beispiel): https://buildwithfern.com/learn/api-definitions/asyncapi/channels/bindings,
  Modelina: https://www.asyncapi.com/tools/modelina, CLI in CI: https://www.asyncapi.com/docs/tools/cli/github-action
- OpenAPI Media Type Registry `text/event-stream`: https://spec.openapis.org/registry/media-type/sse
- Sync Engines: https://zero.rocicorp.dev/docs/when-to-use, https://powersync.com/blog/electricsql-vs-powersync,
  https://infoq.com/news/2026/06/zero-version-1
- OWASP Top 10 2025: https://owasp.org/Top10/
- OWASP ASVS 5.0: https://github.com/OWASP/ASVS
- OWASP Cheat Sheets:
  [HTML5 Security („Server-Sent Events")](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html),
  [Session Management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html),
  [Authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html),
  [Logging](https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html),
  [Denial of Service](https://cheatsheetseries.owasp.org/cheatsheets/Denial_of_Service_Cheat_Sheet.html),
  [WebSocket Security](https://cheatsheetseries.owasp.org/cheatsheets/WebSocket_Security_Cheat_Sheet.html)
  (nur Abgrenzung)
