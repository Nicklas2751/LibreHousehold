# Meta-Plan: Schrittweise Backend-Implementierung LibreHousehold

## Kontext

Das Frontend ist vollständig ausgebaut und arbeitet mit einem Mock-Backend. Das echte Backend hat erst einen einzigen funktionalen Endpunkt: `POST /household/setup`. Alle anderen Endpunkte sind in der OpenAPI-Spec definiert, aber im Backend als `501 Not Implemented` belassen.

**Ziel:** Den Nutzer Schritt für Schritt in einen vollständigen Nutzungsfluss bringen — beginnend beim Setup, dann Aufgaben und Ausgaben, dann Abrechnung, dann Statistiken. Authentifizierung wird in diesem Plan bewusst ausgeklammert; der Einstieg bleibt immer das Setup.

**Prinzip:** Jeder Abschnitt ist kompilierfähig, schließt mit einer lauffähigen Anwendung ab und bringt einen spürbaren Fortschritt in der Benutzeroberfläche.

---

## Aktueller Stand (Ausgangslage)

| Modul | Status |
|---|---|
| `household/` (Setup) | ✅ Implementiert |
| `household/` (CRUD, Invite, Admin) | ❌ `501 Not Implemented` |
| `household/` (Members) | ❌ `501 Not Implemented` |
| `tasks/` | ❌ Modul leer |
| `expenses/` (inkl. Reimbursements) | ❌ Modul leer |

---

## Abschnitt 0 — Modul-Kommunikations-Analyse ✅

**Warum zuerst?**
Die spätere Implementierung von `tasks/`, `expenses/` und `household/` wird Situationen erzeugen, in denen Module Daten anderer Module benötigen — z.B. muss `expenses/` wissen, ob ein Haushalt existiert, oder `tasks/` braucht keine echte FK-Referenz auf `member`, muss aber sinnvoll auf Löschungen reagieren. Diese Entscheidung jetzt zu treffen verhindert, dass jeder Abschnitt eine eigene Ad-hoc-Lösung erfindet.

**Was wird analysiert und entschieden?**

Folgende Optionen für modul-übergreifende Kommunikation stehen zur Verfügung und müssen bewertet werden:

| Option | Beschreibung | Geeignet für |
|---|---|---|
| **Spring Application Events** | Synchrones/asynchrones Eventing via `ApplicationEventPublisher`. Einfach, kein Framework-Overhead. | Zustandsänderungen, die andere Module informieren sollen (z.B. Household gelöscht → Tasks aufräumen) |
| **Spring Modulith Events** | `@ApplicationModuleListener`, mit Transaktionssicherheit und optionaler Externalisierung. Modulith-native Lösung. | Wie Application Events, aber mit Modulith-Garantien (at-least-once, Event-Tabelle) |
| **Explizite Public-API-Interfaces** | Modul A definiert ein Interface in seinem `public`-Package; Modul B injiziert dieses Interface. Klare, testbare Schnittstelle. | Synchrone Abfragen, z.B. „Existiert dieser Haushalt?" |
| **Keine Java-Grenze, nur DB-Constraint** | DB-FK auf anderes Schema. Module kennen sich auf Java-Ebene nicht, die DB erzwingt die Integrität. | **Wird hier ausgeschlossen** — jedes Modul bekommt sein eigenes DB-Schema, keine modul-übergreifenden FKs |
| **Denormalisierung / Kopieren** | IDs werden als einfache UUIDs ohne Constraint gespeichert; Konsistenz liegt in der Applikationslogik. | Als Fallback wenn Kopplung bewusst locker sein soll |

**Ergebnis dieses Abschnitts:**
- Eine schriftliche Entscheidung (ADR) welche Kommunikationsform in welcher Situation verwendet wird
- Klärung: Reagieren Module reaktiv (Events) oder imperativ (Interface-Call) auf Änderungen in anderen Modulen?
- Konkret für den vorliegenden Plan: Wie kommuniziert `tasks/` mit `household/`, wie `expenses/` mit `household/` und `tasks/`, wie `expenses/` intern zwischen Expenses und Reimbursements?

**UI-Fortschritt:** Keiner — reine Architektur-Entscheidung.

**Abhängigkeiten:** Keine.

---

## Abschnitt 1 — Household-Verwaltung (Kern-CRUD + Invite-Erneuerung)

**Warum jetzt?**
Direkterweiterung des einzigen bereits implementierten Moduls. Alle vier Endpunkte liegen im selben Modul, keine neuen Modul-Grenzen, höchste Dichte pro Aufwand. Der Invite-Link-Endpunkt (`POST /household/{id}/invite`) ist in `AGENTS.md` explizit als fehlend markiert.

**Was wird implementiert?**
- `PATCH /household/{id}` — Haushaltsname ändern
- `DELETE /household/{id}` — Haushalt löschen (Cascade-Logik via Events aus Abschnitt 0)
- `POST /household/{id}/invite` — Invite-Token invalidieren und neu generieren
- `PUT /household/{id}/admin` — Admin-Rolle atomar übertragen

**OpenAPI-Anpassung (vor Backend-Implementierung):**
- `HouseholdSetupResponse` bekommt ein neues Feld `inviteValidUntil` (date-Format)
- `InviteResponse` bekommt ebenfalls `inviteValidUntil`
- Das Backend befüllt dieses Feld aus der `InviteEntity.validUntil`

**Frontend-Ergänzung (im gleichen Abschnitt):**
Die Household-Settings-Seite (`/app/settings/household`) zeigt den Invite-Token nur noch dann an, wenn er gültig ist. Konkret:
- Gültiger Token: QR-Code + URL + Hinweis „Gültig noch X Tage"
- Abgelaufener Token: Kein Code, stattdessen Meldung „Kein gültiger Invite-Link vorhanden" + Button zum Neu-Generieren
- Relevant: Die Anzeige nutzt `inviteValidUntil` aus der API-Response; eine eigene API zum Abrufen des aktuellen Tokens ist ebenfalls nötig oder wird über das Household-Objekt gelöst — das ist Teil der Implementierungsentscheidung in diesem Abschnitt

**UI-Fortschritt:** Household-Settings vollständig funktional. Invite-Token-Anzeige mit Gültigkeitsstatus. Haushaltsname ändern, Admin übertragen, Haushalt löschen.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsmuster für Delete-Cascade entschieden).

---

## Abschnitt 2 — Member-Verwaltung + ADR: Account-Modell

**Warum jetzt?**
Members sind ein Cross-Cutting-Concern: Tasks brauchen "assignedTo", Expenses brauchen "paid by" und "split between", Settings zeigt die Mitgliederliste. Ohne `GET /household/{id}/members` bleibt fast jede Seite mit leeren Dropdowns.

### ADR-Voranalyse: Email-Eindeutigkeit und Account-Haushalt-Modell

Vor der Implementierung muss eine Architekturentscheidung getroffen werden. Hier sind die Abwägungen:

#### Option A: Email global eindeutig, ein Account = ein Haushalt (aktueller Stand)

**Datenmodell:** `member.email UNIQUE` systemweit. Ein Member gehört zu genau einem Household.

| Vorteile | Nachteile |
|---|---|
| Einfachstes Datenbankschema (bestehende Migration passt) | Nutzer mit mehreren Haushalten nicht möglich |
| Klare 1-zu-1-Beziehung: Login-Identität = Household-Mitglied | Realitätsfern für viele Use Cases (WG wechseln, Familien + Freundeskreis) |
| Kein Konzept eines separaten „Account"-Objekts notwendig | Beim Household löschen geht die gesamte Identität verloren |
| Weniger Komplexität in Auth (kein Account-Lookup nötig) | Email-Änderung eines Members ist gleichzeitig eine globale Identitätsänderung |
| Passt gut zur aktuellen `admin_id UNIQUE`-Constraint | `member.email` ist nach außen sichtbar → Datenschutzrisiko bei Mehrfachnutzung |

**Architekturauswirkung:** Minimale Änderung. Das bestehende Schema (`V1`) bleibt unberührt. Member-Management ist rein haushaltsintern.

#### Option B: Email global eindeutig, ein Account kann mehreren Haushalten angehören

**Datenmodell:** Separates `account`-Objekt (mit Email + Passwort). `household_member`-Join-Tabelle verbindet Account mit Household und definiert dort die Rolle (Admin/Member). Der `member` wird zum „Profil innerhalb eines Haushalts" (Name, Avatar, haushaltsspezifische Rolle).

| Vorteile | Nachteile |
|---|---|
| Realistischer für echte Nutzer (WG, Familie, Verein) | Erheblich komplexeres Datenbankschema |
| Ein Login für alle Haushalte | „Account"-Konzept braucht eigenes Modul oder Erweiterung von `household/` |
| Account-Löschung und Household-Verlassen sind unabhängige Operationen | `PATCH /members/{id}` muss zwischen Profil- und Account-Änderungen unterscheiden |
| Email-Änderung betrifft Login, nicht haushaltsspezifische Daten | Auth wird erheblich komplexer (welcher Haushalt ist aktiv?) |
| Vorbereitung für Auth ist strukturell sauber | Flyway-Migration muss die bestehende `member`-Tabelle umbenennen/aufteilen |
| Passt zu den offenen Design-Fragen in `AGENTS.md` | Höherer Aufwand jetzt, obwohl Auth ausgeklammert ist |

**Architekturauswirkung:** Neue Flyway-Migration, neues `account`-Konzept, Join-Tabelle. Das bestehende `member`-Objekt bleibt als „Haushaltsprofil" erhalten, verweist aber auf einen `account`. Die `admin_id`-FK müsste auf `account_id` umgebaut werden.

#### Option C: Email haushaltsbezogen eindeutig (kein globales Account-Konzept)

**Datenmodell:** `member.email` ist eindeutig pro Household, nicht global. Kein separates Account-Objekt.

| Vorteile | Nachteile |
|---|---|
| Einfacher als Option B | Dieselbe Person kann sich mit derselben Email in mehreren Haushalten anmelden |
| Haushaltsisolation ist maximal | Kein einheitlicher Login möglich |
| Gut für reine „Haushaltslisten"-Use-Cases | Inkonsistente Identität — schwer mit Auth zu vereinbaren |

**Architekturauswirkung:** Kleiner Eingriff (UNIQUE-Constraint auf `(household_id, email)` statt global). Aber langfristig eine Sackgasse bei späterer Auth-Einführung.

**Empfehlung:** Option A oder B. Option C scheidet aus. Die Entscheidung zwischen A und B beeinflusst maßgeblich, wie Auth später gebaut wird — auch wenn Auth jetzt ausgeklammert ist.

---

**Was wird implementiert (nach ADR-Entscheidung):**
- Flyway-Migration (je nach Entscheidung ggf. neue Tabellen)
- `GET /household/{id}/members`
- `GET /household/{id}/members/{memberId}`
- `POST /household/{id}/members` (Verknüpfung mit Invite-Token aus Abschnitt 1)
- `PATCH /household/{id}/members/{memberId}`
- `DELETE /household/{id}/members/{memberId}`

Members bleiben Teil des `household/`-Moduls.

**UI-Fortschritt:** Mitgliederliste in Settings funktioniert. Alle Dropdowns in Tasks und Expenses bekommen echte Namen. Profil-Seite kann Name/Avatar speichern.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsform), Abschnitt 1 (InviteEntity vorhanden).

---

## Abschnitt 3 — Tasks (vollständiger Zyklus)

**Warum jetzt?**
Tasks sind das einfachste Feature mit eigenem Modul, hohem Alltagsnutzen und nur drei Endpunkten. Das `tasks/`-Modul ist leer und kann als erstes vollständiges Modul-Beispiel für das vereinbarte Kommunikationsmuster aus Abschnitt 0 dienen.

**Was wird implementiert?**
- Neues Modul `tasks/` mit eigenem DB-Schema: Entity, Repository, Mapper, Service, ApiDelegateImpl
- Flyway-Migration: `task`-Tabelle **im Schema `tasks`** (kein FK zu `household` oder `member` auf DB-Ebene — Integrität über Applikationslogik und ggf. Events)
  - Felder: `id` (UUID), `household_id` (UUID, kein FK), `assigned_to` (UUID, kein FK), `title`, `due_date`, `done`, `recurring`, `recurrence_unit`, `recurrence_interval`
- `GET /household/{id}/tasks` — alle Tasks eines Haushalts
- `POST /household/{id}/tasks` — Task erstellen (client-generierte UUID)
- `PATCH /household/{id}/tasks/{taskId}` — `done`-Status setzen/zurücksetzen inkl. Recurring-Logik
- **Task-Statistics im gleichen Modul:** `tasksByMember` (done + open-Zähler)

**Modul-Grenze:** Kein Java-Bean-Import aus `household/`. Kein DB-FK auf andere Schemas.

**UI-Fortschritt:** Tasks-Seite vollständig funktional. Dashboard zeigt echte offene Tasks und Task-Completion-Werte.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsmuster definiert), Abschnitt 2 (Member-IDs bekannt).

---

## Abschnitt 4 — Kategorien + Expenses + Reimbursements + Financials

**Warum zusammen?**
Expenses und Reimbursements sind inhaltlich und fachlich eng verbunden: Reimbursements referenzieren Expenses, die `isMutable`-Logik von Expenses hängt vom Reimbursement-Status ab. Ein gemeinsames Modul `expenses/` vermeidet künstliche Modul-Grenzen innerhalb einer eng gekoppelten Domäne.

**Was wird implementiert?**
- Neues Modul `expenses/` mit eigenem DB-Schema (`expenses`)
- Flyway-Migrationen:
  - `category`-Tabelle, `expense`-Tabelle, `reimbursement`-Tabelle (alle ohne FK zu anderen Schemas)
- Endpunkte: Kategorien, Expenses (CRUD), Reimbursements (CRUD + Status), Financials (Summary + Balances)
- `isMutable`-Aktivierung als interne Modullogik (kein Modul-Grenz-Problem)
- **Expense-Statistics im gleichen Modul:** `totalExpenses`, `avgExpensesPerMonth`, `expensesByCategory`, `expensesByMember`

**UI-Fortschritt:** Expenses-Seite + Settle-Seite vollständig funktional. Dashboard-Financial-Card zeigt echte Salden.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsmuster), Abschnitt 2 (Member-IDs).

---

## Abschnitt 5 — Statistics-Endpunkt (Aggregation)

**Warum kein eigenes Modul?**
Statistics sind ein reines Read-Aggregat. Da Tasks und Expenses ihre eigenen Statistics-Service-Methoden mitbringen, ist ein separates `statistics/`-Modul eine unnötige Schicht.

**Was wird implementiert?**
- `GET /household/{id}/statistics?period=...` — Controller delegiert an Read-Interfaces aus `tasks/` und `expenses/`
- Perioden-Utility: `LAST_7_DAYS`, `LAST_14_DAYS`, `THIS_MONTH`, `LAST_3_MONTHS`, `LAST_6_MONTHS`, `THIS_YEAR`, `LAST_YEAR`
- Kein eigenes Datenbankschema, keine eigenen Entities

**UI-Fortschritt:** Statistics-Seite zeigt echte Daten. Zeitraum-Selector funktioniert.

**Abhängigkeiten:** Abschnitte 3 und 4 abgeschlossen.

---

## Abschnitt 6 — User Settings (Präferenzen + Account-Verwaltung)

**Was wird implementiert?**
- Neues Modul `usersettings/`
- Flyway-Migration: `theme`, `language`, `password_hash` (Argon2id, Vorbereitung auf Auth)
- `PATCH /household/{id}/members/{memberId}/preferences`
- `PUT /household/{id}/members/{memberId}/password`
- `DELETE /household/{id}/members/{memberId}/account`

**UI-Fortschritt:** User-Settings vollständig funktional.

**Abhängigkeiten:** Abschnitte 0, 1, 2 abgeschlossen.

---

## Querschnittsthemen

### DB-Schema-Strategie
Jedes Modul bekommt ein eigenes PostgreSQL-Schema (`household`, `tasks`, `expenses`). Keine modul-übergreifenden FK-Constraints. Fremd-IDs (z.B. `household_id` in `tasks`) werden als einfache UUIDs ohne DB-FK gespeichert.

### Records und Updates
Alle Updates über explizite `@Modifying @Query`-Methoden im Repository. `save()` nur für neue Entities (gemäß `AGENTS.md`-Muster).

### TDD-Rhythmus pro Abschnitt
1. `*ApiDelegateImplTest` (Unit, Mockito)
2. `*ServiceTest` (Unit, Mockito)
3. `*ServiceIT` (Integration, Testcontainers + Flyway)

---

## Reihenfolge auf einen Blick

| # | Abschnitt | Modul | Freigeschaltete UI / Ergebnis |
|---|---|---|---|
| 0 | Modul-Kommunikations-Analyse | — | ADR: Kommunikationsmuster |
| 1 | Household-CRUD + Invite + Frontend-Gültigkeit | `household/` (vollst.) | Household-Settings, Invite-Gültigkeit |
| 2 | Member-Verwaltung + ADR Account-Modell | `household/` (Members) | Settings (Profil), alle Dropdowns |
| 3 | Tasks (inkl. Task-Statistics) | `tasks/` (neu, eigenes Schema) | Tasks, Dashboard-Tasks |
| 4 | Kategorien + Expenses + Reimbursements + Financials | `expenses/` (neu, eigenes Schema) | Expenses, Settle, Dashboard-Finanzen |
| 5 | Statistics-Endpunkt (Aggregation) | Kein neues Modul | Statistics-Seite |
| 6 | User Settings | `usersettings/` (neu) | User-Settings |
