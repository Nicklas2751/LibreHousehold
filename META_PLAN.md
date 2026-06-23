# Meta-Plan: Schrittweise Backend-Implementierung LibreHousehold

## Kontext

Das Frontend ist vollständig ausgebaut und arbeitet mit einem Mock-Backend. Das echte Backend hat erst einen einzigen
funktionalen Endpunkt: `POST /household/setup`. Alle anderen Endpunkte sind in der OpenAPI-Spec definiert, aber im
Backend als `501 Not Implemented` belassen.

**Ziel:** Den Nutzer Schritt für Schritt in einen vollständigen Nutzungsfluss bringen — beginnend beim Setup, dann
Aufgaben und Ausgaben, dann Abrechnung, dann Statistiken. Authentifizierung wird in diesem Plan bewusst ausgeklammert;
der Einstieg bleibt immer das Setup.

**Prinzip:** Jeder Abschnitt ist kompilierfähig, schließt mit einer lauffähigen Anwendung ab und bringt einen spürbaren
Fortschritt in der Benutzeroberfläche.

---

## Aktueller Stand

| Modul                              | Status          |
|------------------------------------|-----------------|
| `household/` (Setup)               | ✅ Implementiert |
| `household/` (CRUD, Invite, Admin) | ✅ Implementiert |
| `household/` (Members)             | ✅ Implementiert |
| `tasks/`                           | ✅ Implementiert |
| `expenses/` (inkl. Reimbursements) | ✅ Implementiert |
| `statistics/` (Aggregation)        | ✅ Implementiert |
| `usersettings/`                    | ⬜ Fehlt noch    |

---

## Abschnitt 0 — Modul-Kommunikations-Analyse ✅

**Ergebnis:** ADR-011 dokumentiert das Kommunikationsmuster.

| Szenario                                                | Muster                                                                        |
|---------------------------------------------------------|-------------------------------------------------------------------------------|
| Synchrone Abfrage (z.B. „Existiert Haushalt X?")        | **Named Interface** im Root-Package des Zielmoduls                            |
| Reaktion auf Lifecycle-Event (z.B. „Haushalt gelöscht") | **Domain Event** (`ApplicationEventPublisher` + `@ApplicationModuleListener`) |

Event-Carried State Transfer (lokale Datenkopien pro Modul) wurde bewusst abgelehnt — zu viel Overhead für einen
Monolithen. Stärkere Kopplung durch Named Interfaces wird akzeptiert; sie ist explizit und von Spring Modulith
verifizierbar.

Modul-übergreifende DB-FKs sind verboten. Jedes Modul bekommt ein eigenes PostgreSQL-Schema.

**UI-Fortschritt:** Keiner — reine Architektur-Entscheidung.

---

## Abschnitt 1 — Household-Verwaltung (Kern-CRUD + Invite) ✅

**Was wurde implementiert:**

- `GET /household/{id}/invite` — aktuellen Invite-Token abrufen
- `PATCH /household/{id}` — Haushaltsname ändern (204 No Content)
- `DELETE /household/{id}` — Haushalt löschen (inkl. Invite-Cascade via `HouseholdDeleted`-Event)
- `POST /household/{id}/invite` — Invite-Token invalidieren und neu generieren
- `PUT /household/{id}/admin` — Admin-Rolle atomar übertragen (204 No Content)

**OpenAPI-Änderungen:**

- `HouseholdSetupResponse` hat jetzt `inviteValidUntil` (date)
- `InviteResponse` enthält `inviteToken` (UUID) + `inviteValidUntil` (date) statt URL
- `PATCH /household/{id}` und `PUT /household/{id}/admin` geben 204 zurück (kein Body)

**Design-Entscheidungen:**

- Keine unnötigen Vor-SELECTs: `updateName`, `updateAdminId` und `deleteHouseholdById` geben `int` (betroffene Zeilen)
  zurück — 0 wirft direkt `HouseholdNotFoundException`
- `regenerateInvite` nutzt FK-Violation-Catching statt `existsById`-Vorprüfung
- `HouseholdManagementService` hat keine Abhängigkeit mehr zum `HouseholdSetupMapper`

**Frontend-Ergänzung:**

- Household-Settings lädt den Invite-Token per `GET /household/{id}/invite`
- Gültiger Token: QR-Code + URL + Hinweis „Gültig noch X Tage"
- Abgelaufener/fehlender Token: Meldung + Button zum Neu-Generieren
- Lokale State-Updates nach `saveName()` und `confirmTransfer()` (kein Rückgabewert mehr)

**UI-Fortschritt:** Household-Settings vollständig funktional. Invite-Token-Anzeige mit Gültigkeitsstatus. Haushaltsname
ändern, Admin übertragen, Haushalt löschen.

---

## Abschnitt 2 — Member-Verwaltung + Invite-Join-Flow ✅

**Entscheidung:** Option A — `member.email` global eindeutig, 1 Member = 1 Haushalt.

**Was wurde implementiert:**

- `member.is_admin` (boolean) ersetzt `household.admin_id`; kein zirkulärer FK mehr
- `GET /household/{householdId}/members` — Mitgliederliste
- `GET /household/{householdId}/members/{memberId}` — einzelner Member
- `GET /invite/{token}` — Token zu Haushaltsdaten auflösen (`InviteInfo`)
- `POST /invite/{token}/join` — Haushalt per Invite-Token beitreten (201 Created)
- `PATCH /household/{householdId}/members/{memberId}` — Name/E-Mail aktualisieren (204)
- `DELETE /household/{householdId}/members/{memberId}` — Member entfernen (204, publiziert `MemberRemoved`-Event)
- `MembersApiDelegateImpl` übernimmt alle member-bezogenen Endpunkte; invite/join-Endpunkte ebenfalls im `members`-Tag
- `HouseholdRepository.findNameById` — lädt nur den Namen (kein Full-Entity-Load für `InviteInfo`)
- `Household`-Schema: `admin`-Feld entfernt; Admin-Status ausschließlich über `member.isAdmin`
- Frontend: `JoinWizard.svelte` + Route `/invite/[token]`; `isOwner`-Prüfung nutzt `$userState?.isAdmin`

**UI-Fortschritt:** Mitgliederliste in Settings funktioniert. Invite-Link führt zu Join-Wizard. Profil-Seite kann
Name/E-Mail speichern. Admin-Krone über `isAdmin`-Flag.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsform), Abschnitt 1 (InviteEntity vorhanden).

---

## Abschnitt 3 — Tasks (vollständiger Zyklus) ✅

**Warum jetzt?**
Tasks sind das einfachste Feature mit eigenem Modul, hohem Alltagsnutzen und nur drei Endpunkten. Das `tasks/`-Modul ist
leer und kann als erstes vollständiges Modul-Beispiel für das vereinbarte Kommunikationsmuster dienen.

**Was wird implementiert?**

- Neues Modul `tasks/` mit eigenem DB-Schema: Entity, Repository, Mapper, Service, ApiDelegateImpl
- Flyway-Migration: `task`-Tabelle im Schema `tasks` (kein FK zu `household` oder `member` auf DB-Ebene)
    - Felder: `id` (UUID), `household_id` (UUID, kein FK), `assigned_to` (UUID, kein FK), `title`, `due_date`, `done`,
      `recurring`, `recurrence_unit`, `recurrence_interval`
- `GET /household/{id}/tasks` — alle Tasks eines Haushalts
- `POST /household/{id}/tasks` — Task erstellen (client-generierte UUID)
- `PATCH /household/{id}/tasks/{taskId}` — `done`-Status setzen/zurücksetzen inkl. Recurring-Logik
- **Task-Statistics im gleichen Modul:** `tasksByMember` (done + open-Zähler)

**Modul-Grenze:** Kein Java-Bean-Import aus `household/`. Kein DB-FK auf andere Schemas.

**UI-Fortschritt:** Tasks-Seite vollständig funktional. Dashboard zeigt echte offene Tasks und Task-Completion-Werte.

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsmuster definiert), Abschnitt 2 (Member-IDs bekannt).

---

## Abschnitt 4 — Kategorien + Expenses + Reimbursements + Financials ✅

**Warum zusammen?**
Expenses und Reimbursements sind inhaltlich eng verbunden: Reimbursements referenzieren Expenses, die `isMutable`-Logik
hängt vom Reimbursement-Status ab. Ein gemeinsames Modul `expenses/` vermeidet künstliche Modul-Grenzen.

**Was wurde implementiert?**

- Modul `expenses/` mit eigenem DB-Schema; Subpackages: `controller`, `exception`, `mapper`, `model`, `repository`,
  `service`
- Flyway-Migrationen: `category`-, `expense`-, `expense_split`-, `reimbursement`-, `settlement_expense`-Tabellen
- `ExpensesApiDelegateImpl` — Kategorien + Expenses CRUD
- `ReimbursementsApiDelegateImpl` — Reimbursements erstellen, Status setzen, zurückziehen
- `FinancialsApiDelegateImpl` — Summary + Member-Balances
- `CategoryService`, `ExpenseService`, `ReimbursementService`, `FinancialService`
- `isMutable`-Logik: Expense ist nur editierbar, solange kein aktives Reimbursement existiert
- `SettlementExpenseRef`-Tracking: welche Expenses durch welches Reimbursement abgedeckt sind
- **Expense-Statistics im gleichen Modul:** `ExpenseStatisticsProvider` + `ExpenseStatisticsService` (Interface für
  Abschnitt 5 vorbereitet)
- `ExpenseSplitRef`: eigener Anteil + zwischen wem eine Expense aufgeteilt ist
- Frontend: Expenses-Seite, Settle-Seite, Kategorie-Verwaltung vollständig funktional

**Design-Entscheidungen:**

- Reimbursement-Entwurf ermöglicht Withdrawal (Zurückziehen); `settlement_expense` speichert welche Expenses durch
  ein Reimbursement gedeckt werden, um doppelte Anzeige als „ausgeglichen" zu vermeiden
- Kein DB-FK zu `household` oder `member`; IDs als UUID ohne Constraint

**UI-Fortschritt:** Expenses-Seite + Settle-Seite vollständig funktional. Dashboard-Financial-Card zeigt echte Salden.
Kategorie-Verwaltung mit Erstellen, Umbenennen und Löschen (mit Nutzungsprüfung).

**Abhängigkeiten:** Abschnitt 0 (Kommunikationsmuster), Abschnitt 2 (Member-IDs).

---

## Abschnitt 5 — Statistics-Endpunkt (Aggregation) ✅

**Warum kein eigenes Modul?**
Statistics sind ein reines Read-Aggregat. Da Tasks und Expenses ihre eigenen Statistics-Service-Methoden mitbringen, ist
ein separates `statistics/`-Modul eine unnötige Schicht.

**Was wurde implementiert?**

- `GET /household/{id}/statistics?period=...` — Controller delegiert an Read-Interfaces aus `tasks/` und `expenses/`
- Perioden-Utility: `LAST_7_DAYS`, `LAST_14_DAYS`, `THIS_MONTH`, `LAST_3_MONTHS`, `LAST_6_MONTHS`, `THIS_YEAR`,
  `LAST_YEAR`
- Kein eigenes Datenbankschema, keine eigenen Entities
- `TaskCompletionEntity` + `task_completion`-Tabelle: speichert wer welchen Task wann erledigt hat (für korrekte
  Member-Zählung bei wiederkehrenden Tasks)
- `TaskCompletionRepository` mit Abfragen nach Haushalt und Zeitraum
- `TaskStatisticsProvider`-Interface + Implementierung im `tasks/`-Modul
- `ExpenseStatisticsProvider`-Interface + Implementierung im `expenses/`-Modul
- `StatisticsApiDelegateImpl` aggregiert Daten aus beiden Modulen

**Design-Entscheidungen:**

- `task_completion`-Tabelle statt `done`-Feld-Auswertung, da wiederkehrende Tasks mehrfach erledigt werden können —
  ohne separates Tracking wurden Completions doppelt gezählt
- Named Interfaces (`TaskStatisticsProvider`, `ExpenseStatisticsProvider`) halten die Modul-Grenze: kein direkter
  Zugriff des Statistics-Controllers auf Repository-Klassen anderer Module

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

Jedes Modul bekommt ein eigenes PostgreSQL-Schema (`household`, `tasks`, `expenses`). Keine modul-übergreifenden
FK-Constraints. Fremd-IDs (z.B. `household_id` in `tasks`) werden als einfache UUIDs ohne DB-FK gespeichert. Konsistenz
über Applikationslogik und/oder Events.

### Records und Updates

Alle Updates über explizite `@Modifying @Query`-Methoden im Repository, die `int` (betroffene Zeilen) zurückgeben.
`save()` nur für neue Entities. Rückgabewert 0 → `*NotFoundException` werfen.

### TDD-Rhythmus pro Abschnitt

1. `*ApiDelegateImplTest` (Unit, Mockito)
2. `*ServiceTest` (Unit, Mockito)
3. `*ServiceIT` (Integration, Testcontainers + Flyway)

---

## Reihenfolge auf einen Blick

| # | Abschnitt                                           | Modul                  | Status | Freigeschaltete UI / Ergebnis                  |
|---|-----------------------------------------------------|------------------------|--------|------------------------------------------------|
| 0 | Modul-Kommunikations-Analyse                        | —                      | ✅      | ADR-011: Kommunikationsmuster                  |
| 1 | Household-CRUD + Invite + Frontend-Gültigkeit       | `household/`           | ✅      | Household-Settings, Invite-Gültigkeit          |
| 2 | Member-Verwaltung + Invite-Join-Flow                | `household/` (Members) | ✅      | Settings (Profil), Join-Wizard, alle Dropdowns |
| 3 | Tasks (inkl. Task-Statistics)                       | `tasks/` (neu)         | ✅      | Tasks, Dashboard-Tasks                         |
| 4 | Kategorien + Expenses + Reimbursements + Financials | `expenses/` (neu)      | ✅      | Expenses, Settle, Dashboard-Finanzen           |
| 5 | Statistics-Endpunkt (Aggregation)                   | Kein neues Modul       | ✅      | Statistics-Seite                               |
| 6 | User Settings                                       | `usersettings/` (neu)  | ⬜      | User-Settings                                  |