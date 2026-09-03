# Auth Meta-Plan

Status: Entwurf / Referenzdokument. Dieses Dokument ist **kein Arc42-Kapitel und kein ADR** —
es ist die Arbeits-Roadmap für die Einführung von Authentifizierung/Autorisierung in
LibreHousehold. Jeder Punkt bekommt vor der Umsetzung einen eigenen Implementierungsplan
(TDD-Pflicht, siehe `AGENTS.md`); dieses Dokument selbst wird nicht implementiert.

Referenzierung: Ein Detailplan-Auftrag kann direkt auf eine ID hier verweisen (z. B. „erstelle
den Implementierungsplan für P1.4"). IDs sind stabil und werden nicht umnummeriert; erledigte
Punkte werden mit ✅ markiert, nicht gelöscht.

Bindende Leitplanken für alle Punkte:
- ADR-009 (Argon2id), ADR-011 (Named Interfaces vs. Domain Events), ADR-012 (1 Account : 1 Household)
- Arc42 Kap. 8 „Authentication and Authorization": kein eigenes Auth-Modul, keine Named Interface
  für Framework-Typen (`PasswordEncoder` etc. direkt injizieren), `AccountRegistration` bleibt im
  `household`-Modul.
- OWASP Top 10 / ASVS als Sicherheits-Minimum; SOTA-Referenzen wo einschlägig (OWASP Cheat Sheets,
  IETF BCP „OAuth 2.0 for Browser-Based Apps").
- TDD verpflichtend für jeden Detailplan (Unit: nur direkte Dependencies mocken; Integration:
  echte Infrastruktur/Testcontainers; Mapper nie gemockt).
- Bereits getroffene Entscheidungen für diesen Plan: Session-Modell = **BFF-Pattern mit
  httpOnly/SameSite-Cookies** (Tokens verlassen das Backend nie), passend zur bestehenden
  „kein localStorage/sessionStorage"-Regel. Lifecycle-Härtung (E-Mail-Verifikation,
  Passwort-Reset, Rate-Limiting) wird als vollwertige Phase (P2) mit eingeplant, nicht auf
  „später" verschoben.
- **Sofort-Rückfrage-Pflicht, kein eigenmächtiges "Out of Scope".** Fällt während der Umsetzung
  eines Punkts etwas auf — ein Bug, eine Lücke, ein Zielkonflikt mit einer bereits getroffenen
  Entscheidung, eine fehlende Spezifikation o. Ä. — wird die Umsetzung sofort unterbrochen und der
  Nutzer aktiv gefragt, wie weiter verfahren werden soll. Es ist **nicht** erlaubt, einen solchen
  Punkt eigenmächtig als "Out of Scope"/"Follow-up-Task" zu dokumentieren und ohne Rückfrage
  weiterzumachen. Grund: genau das ist bereits einmal passiert (`createMember`/`removeMember` beim
  P1.6-Review wurden zunächst als Folge-Tasks vermerkt statt sofort zu klären; siehe
  `auth-plan-p1.3-p1.6.md`) und musste in einer eigenen, nachträglichen Runde aufgearbeitet werden.
  "Out of Scope" darf ausschließlich für Punkte verwendet werden, die im Auftrag/Plan bereits
  explizit als bewusst-nicht-jetzt markiert sind, nicht für neu entdeckte, ungeklärte Fragen.

Hinweis: `frontend/src/generated-sources` ist gitignored und enthielt (Stand 4. Juli, nicht in
`api/openapi.yml`/git) bereits einen verwaisten Entwurf (`AuthApi`, `LocalRegistration`,
`joinHouseholdAuthenticated`, `AuthProviders`). Kann als Namens-Inspiration für P1.1/P3.4 dienen,
muss aber sauber neu aus der überarbeiteten OpenAPI-Spec generiert werden.

---

## Phase 0 — Fundament & Entscheidungen (nur Doku/ADRs, kein Feature-Code)

- ✅ **P0.1 — ADR: Auth-Architektur.** Spring Authorization Server als Authorization Server *und*
  Resource Server im selben Monolithen; Authorization Code Flow + PKCE für das SPA. Abgrenzung zu
  Alternativen (reiner Resource-Server-Ansatz, externer IdP). Bezug: QG2/QG4. Siehe
  [ADR 013](architecture/adrs/adr-013.adoc).
- ✅ **P0.2 — ADR: Session-Strategie (BFF-Pattern).** httpOnly/SameSite-Cookies, Tokens verbleiben
  serverseitig, CSRF-Schutzmechanismus, Logout-/Revocation-Semantik. *(Entscheidung: BFF+Cookies,
  siehe oben.)* Siehe [ADR 014](architecture/adrs/adr-014.adoc).
- 🟡 **P0.3 — Datenmodell & Flyway-Migrationen.** Ablage der Credentials im `household`-Schema:
  eigene `account`-Tabelle (1:1 zu `member`) statt Spalten direkt an `member`, siehe
  [ADR 015](architecture/adrs/adr-015.adoc). Offen: Argon2id-Parameter, `email_verified`,
  plus die von Spring Authorization Server benötigten Tabellen explizit per Flyway (kein
  Schema-Autogen, siehe Findings zu `event_publication`) — das ist Umsetzungsdetail für den
  P1.3-Feinplan, nicht mehr Teil von Phase 0.
- ✅ ~~**P0.4 — Dependency-Spike.**~~ Entfällt: Kompatibilität von `spring-security` /
  `spring-authorization-server` mit Spring Boot 4 / Modulith / Java 25 ist bereits bekannt,
  keine gesonderte Verifikation nötig.
- ✅ **P0.5 — Threat Model & OWASP-ASVS-Checkliste.** Kontrollen festgelegt (Passwort-Policy,
  sichere Cookies, CSRF, CORS, Redirect-Allow-List für Invite/OAuth-Callbacks,
  nicht-enumerierende Fehlermeldungen bei Login/Registrierung, Rate-Limiting bewusst auf P2.7
  verschoben). Liefert die Akzeptanzkriterien für P1–P3. Die Checkliste selbst lebt dauerhaft in
  [Kapitel 8](architecture/chapters/08_concepts.adoc#section-security-controls-authentication)
  (arc42-Doku, nicht hier), da sie kein Umsetzungsplan ist, sondern ein Security-Konzept.

## Phase 1 — Lokale Accounts: Kernflow (Authorization Code + PKCE, BFF-Cookie-Session)

- ✅ **P1.1 — OpenAPI: Registrierung & Credentials.** Passwort in `HouseholdSetup` und
  `MemberRegistration` (Invite-Join) ergänzen, neuer Endpoint „E-Mail bereits registriert?"
  (bisher fehlend, siehe AGENTS.md), Passwort-Policy, non-enumerating Error-Responses.
  Siehe [Detailplan](auth-plan-p1.1-p1.2.md).
- ✅ **P1.2 — OpenAPI: Session-Oberfläche.** Vertrag für „current user/me" und Logout festlegen
  (Login/Callback selbst laufen über Spring-Authorization-Server-Endpunkte, nicht im
  Business-Contract). Siehe [Detailplan](auth-plan-p1.1-p1.2.md).
- ✅ **P1.3 — Backend: `AccountRegistration` im `household`-Modul.** Argon2id-Hashing bei Setup &
  Join, `UserDetailsService` auf Member/Account, E-Mail-Verfügbarkeitsprüfung. TDD.
  Siehe [Detailplan](auth-plan-p1.3-p1.6.md).
- ✅ **P1.4 — Backend: Spring Authorization Server Setup.** Filter-Chain-Reihenfolge, Registered
  Client fürs SPA, PKCE, Token-Settings, Login gegen lokale Accounts.
  Siehe [Detailplan](auth-plan-p1.3-p1.6.md).
- ✅ **P1.5 — Backend: Resource-Server-/BFF-Absicherung bestehender Endpunkte.** Cookie-Session,
  CSRF, CORS — alle heute offenen Endpunkte werden geschlossen.
  Siehe [Detailplan](auth-plan-p1.3-p1.6.md).
- ✅ **P1.6 — Backend: Access Control je Haushalt/Rolle.** Jeder haushaltsgebundene Endpunkt prüft
  Zugehörigkeit; Admin-only-Operationen (Invite, Ownership-Transfer, Löschen). OWASP „Broken
  Access Control". TDD + IT. Siehe [Detailplan](auth-plan-p1.3-p1.6.md).
- ✅ **P1.7 — Frontend: SetupWizard um Passwort erweitern.** Inkl. E-Mail-Verfügbarkeits-Check,
  „Account existiert bereits"-Fehlerbild. Siehe [Detailplan](auth-plan-p1.7-p1.10.md).
- ✅ **P1.8 — Frontend: Login, Session-Bootstrap, Route-Guards, Logout.** `/login`-Route real
  angebunden, Current-User-Hydration nach Reload, Guards für `/app/*`. Umfasst auch **P1.10 Teil B**
  (`sessionExpiredMiddleware`). Siehe [Detailplan](auth-plan-p1.7-p1.10.md). Dabei mehrere echte,
  beim Live-Testen gefundene Bugs behoben (siehe Detailplan für Details): fehlender CORS-Origin für
  lokale Frontend-Dev-Origin, Stacktrace-Leak bei ungültiger E-Mail, `ClassCastException` in
  `GET /me` bei nicht-OIDC-Principal, hängender OAuth2-Code-Exchange durch Server-zu-Server-Aufruf
  über den eigenen Dev-Proxy, sowie eine `HttpSessionRequestCache`-Kollision zwischen der
  Business-API- und der Authorization-Server-Filterkette.
- ✅ **P1.9 — Frontend: Invite-Join mit Passwortvergabe.** Siehe [Detailplan](auth-plan-p1.7-p1.10.md).
- ✅ **P1.10 — Frontend: API-Client & Guards.** Cookie-Requests (`credentials: 'include'`),
  CSRF-Header, zentrales 401-Handling/Redirect statt der aktuell verstreuten
  `new Configuration({...})`-Instanzen. Teil A (zentrale `apiConfiguration`, CSRF-Middleware,
  Umstellung aller Fundstellen) und Teil B (`sessionExpiredMiddleware`, mit P1.8) umgesetzt.
  Siehe [Detailplan](auth-plan-p1.7-p1.10.md).

## Phase 2 — Lokale Accounts: Lifecycle-Härtung (OWASP ASVS)

- **P2.1 — OpenAPI: E-Mail-Verifikation.** Verifikations-Token, Resend-Endpoint,
  Verifikationsstatus am Member/Account.
- **P2.2 — Backend: Verifikations-Versand & -Prüfung.** Integration über das
  Notifications-Modul (Domain Event bei Registrierung), Ablauf-/Resend-Regeln.
- **P2.3 — Frontend: Verifikationshinweis & Resend-UI.**
- **P2.4 — OpenAPI: Passwort-Reset-Flow.** Request- und Confirm-Endpoint mit Einmal-Token.
- **P2.5 — Backend: Passwort-Reset.** Token-Ausstellung/-Einlösung, Invalidierung bestehender
  Sessions nach Reset.
- **P2.6 — Frontend: „Passwort vergessen"-Flow.**
- **P2.7 — Backend: Rate-Limiting & Lockout.** Brute-Force-Schutz für Login-, Reset- und
  Verifikations-Endpunkte.

## Phase 3 — Social Login (föderiert, konfigurierbar)

- **P3.1 — ADR: Social-Integration.** Spring-Authorization-Server-Federation
  (Provider auf der Login-Seite des Auth-Servers) vs. Multi-Client-Registrierung;
  Account-Linking über verifizierte E-Mail; Konsequenzen aus dem 1:1-Modell (ADR-012).
- **P3.2 — Konfigurationsmodell.** `application.yml`-Schalter (`local`/`social`/`both`),
  Provider-Registrierungen (z. B. Google, GitHub, generisches OIDC), Startvalidierung.
- **P3.3 — Backend: Provider-Federation & Account-Mapping.** Externe Identität → lokaler
  Member/Account, Anlage bei Erst-Login im Setup-/Invite-Kontext, Linking per verifizierter
  E-Mail, 1:1-Regel bleibt erzwungen.
- **P3.4 — OpenAPI: Discover-Endpoint.** Liefert konfigurierte Methoden/Provider
  (`{ localEnabled, socialProviders: [...] }`).
- **P3.5 — Frontend: dynamische Auth-UI.** Login/Setup/Invite rendern abhängig vom
  Discover-Ergebnis (lokales Formular und/oder Provider-Buttons); lokale Registrierung
  ausblenden, wenn deaktiviert.
- **P3.6 — Setup/Invite ausschließlich via Social.** Erster Admin bzw. Invitee kann sich
  rein über Social registrieren, korrekte Haushalts-Bindung bleibt gewahrt.

## Querschnittlich (laufend, kein eigener Implementierungsplan)

- **X.1 — Doku-Pflege.** `AGENTS.md`, Arc42 Kap. 8/11, neue ADRs nach jeder Phase aktuell halten.
