# Implementierungsplan P2.8–P2.9: Mitglieder-Benachrichtigung bei Löschung, konfigurierbare/mehrsprachige HTML-Mails

Referenz: [`docs/auth-meta-plan.md`](auth-meta-plan.md), Abschnitt „Phase 2 — Lokale Accounts:
Lifecycle-Härtung (OWASP ASVS)", Punkte **P2.8–P2.9**. Dieses Dokument ist ein Arbeitsdokument
(kein Arc42-Kapitel, kein ADR) und wird nach Umsetzung nicht dauerhaft gepflegt (siehe Vorgänger
`auth-plan-p2.1-p2.7.md`, nach Umsetzung aus dem Repo entfernt — dieses Dokument folgt demselben
Muster).

**Scope:** Mitglieder-/Haushalts-Löschungs-Benachrichtigung per E-Mail (P2.8) und die dafür
(und für alle bereits bestehenden E-Mails aus P2.2/P2.5) nötige Infrastruktur für mehrsprachige,
selfhoster-anpassbare HTML+Text-Mails (P2.9). Beide Punkte sind eng gekoppelt: P2.8 führt zwei
neue E-Mail-Typen ein, die sinnvollerweise direkt auf der P2.9-Infrastruktur aufgebaut werden,
statt sie hartkodiert zu bauen und kurz danach zu refactoren (siehe Abschnitt 4).

Status: Entwurf, noch nicht umgesetzt. Alle in Abschnitt 3 aufgeführten Design-Entscheidungen
wurden mit dem Nutzer besprochen und sind **entschieden**.

---

## 1. Bindende Vorgaben

- **ADR-011 (Named Interfaces vs. Domain Events):** Wie bei P2.2/P2.5 gilt: `household` publiziert
  Domain-Events, `notifications` konsumiert sie per `@ApplicationModuleListener`. Neu in diesem
  Plan: `notifications` bekommt zusätzlich eine **synchrone** Abhängigkeit auf das bereits
  bestehende Named Interface `usersettings.PreferencesQuery` (siehe Abschnitt 2.4) — das ist
  keine neue Musterentscheidung, sondern die Anwendung des bereits etablierten Named-Interface-
  Patterns auf ein zweites Zielmodul (`notifications` hängt dadurch von `household` **und**
  `usersettings` ab; das erzeugt keinen Zyklus, siehe Abschnitt 2.7).
- **Arc42 Kapitel 5, Modul-Abhängigkeitsrichtung** (`docs/architecture/chapters/05_building_block_view.adoc:71-77`):
  „`household` darf von keinem anderen Business-Modul abhängen." Das betrifft `household` selbst
  — `household` darf also **nicht** `usersettings.PreferencesQuery` injizieren, um die
  Sprachpräferenz in ein Event zu packen. Für alle anderen Module (inkl. `notifications`) gilt nur
  „so wenige Abhängigkeiten wie möglich, nur über Named Interfaces" — keine feste Verbots-Regel.
  Das ist der Grund, warum die Sprachauflösung in diesem Plan bewusst in `notifications` verortet
  wird, nicht in `household` (siehe Entscheidung 3.1).
- **TDD-Pflicht** (AGENTS.md): wie in allen vorherigen Runden, Rot-Grün-Refactor je Aufgabe.
- **Dependency-Rückfrage bereits erledigt** (AGENTS.md „Dependency Management"): Thymeleaf wurde
  dem Nutzer vor Erstellung dieses Plans vorgelegt (Alternative: Eigenbau-Platzhalter-Ersetzung)
  und **bestätigt** (siehe Entscheidung 3.3) — keine erneute Rückfrage beim Umsetzen nötig.
- **Doku auf Englisch:** alle neuen/geänderten Abschnitte in `docs/architecture/chapters/*.adoc`
  müssen auf Englisch sein (in einer früheren Runde wurden versehentlich deutsche Sätze eingefügt
  und mussten nachträglich übersetzt werden — dieses Mal von Anfang an Englisch).
- **`AGENTS.md` „Mapper-Tests"/Test-Konventionen** wie gehabt: Unit-Tests mocken nur direkte
  Dependencies, Happy-Path-IT-Test für jede DB-lesende/schreibende Methode.

---

## 2. Ist-Zustand (mit Datei-/Zeilenreferenzen)

### 2.1 `HouseholdDeleted`-Event & `HouseholdManagementService.deleteHousehold`

`backend/src/main/java/eu/wiegandt/librehousehold/household/HouseholdDeleted.java:12`:
```java
public record HouseholdDeleted(UUID householdId) {}
```
Kein Mitglieder-Bezug. `backend/src/main/java/eu/wiegandt/librehousehold/household/service/HouseholdManagementService.java:48-57`:
```java
@Transactional
public void deleteHousehold(UUID householdId) {
    inviteRepository.deleteByHouseholdId(householdId);      // Zeile 50
    memberRepository.deleteByHouseholdId(householdId);      // Zeile 51 — Mitglieder weg, BEVOR das Event kommt
    var deletedRows = householdRepository.deleteHouseholdById(householdId); // Zeile 52
    if (deletedRows == 0) { throw new HouseholdNotFoundException(); }
    eventPublisher.publishEvent(new HouseholdDeleted(householdId));         // Zeile 56
}
```
`@ApplicationModuleListener` (`@TransactionalEventListener(phase = AFTER_COMMIT)`) läuft erst
**nach** dem Commit — zu diesem Zeitpunkt sind Mitglieder- und Haushaltszeile bereits weg, ein
Listener kann die Daten nicht mehr nachträglich laden. Genau das ist die in P2.2 identifizierte
Lücke, die P2.8 schließt.

**Aufrufer** (2, per `grep` verifiziert):
- `household/controller/HouseholdApiDelegateImpl.java:43` — manuelle Admin-Löschung (P1.6).
- `household/service/UnverifiedAccountExpiryJob.java:88` — Auto-Löschung bei abgelaufener
  Verifikationsfrist, **nur falls der betroffene Account Admin ist** (`deleteExpiredAccount`,
  Zeilen 85-92). Bei **Nicht**-Admin ruft dieselbe Methode stattdessen `removeMember` auf
  (Zeile 90) — kein `deleteHousehold`, kein `HouseholdDeleted`, sondern `MemberRemoved` (siehe 2.2).

**Konsumenten von `HouseholdDeleted`** (nutzen nur `householdId()`, eine Erweiterung des Records
um weitere Felder bricht sie nicht, da die Accessor-Methode erhalten bleibt):
- `tasks/service/TaskService.java:117-120` — `taskRepository.deleteByHouseholdId(...)`.
- `expenses/service/ExpenseService.java:103-108` — Reimbursement-/Expense-/Category-Cleanup.

### 2.2 `MemberRemoved`-Event & Mitglieds-Entfernung

`backend/src/main/java/eu/wiegandt/librehousehold/household/MemberRemoved.java:12`:
```java
public record MemberRemoved(UUID memberId) {}
```
`backend/src/main/java/eu/wiegandt/librehousehold/household/service/MemberManagementService.java`:
```java
@Transactional
public void leaveHousehold(UUID memberId) {                 // Zeile 129-136, Self-Service-Austritt
    if (!memberRepository.existsById(memberId)) { throw new MemberNotFoundException(); }
    assertMemberIsNotHouseholdAdmin(memberId);
    deleteMemberAndPublishEvent(memberId);
}

@Transactional
public void removeMember(UUID householdId, UUID memberId) {  // Zeile 138-145, Admin-initiierte Entfernung
    if (!memberRepository.existsByIdAndHouseholdId(memberId, householdId)) { throw new MemberNotFoundException(); }
    assertMemberIsNotHouseholdAdmin(memberId);
    deleteMemberAndPublishEvent(memberId);
}

private void deleteMemberAndPublishEvent(UUID memberId) {    // Zeile 153-156
    memberRepository.deleteById(memberId);                   // Zeile 154 — Mitglied weg, BEVOR das Event kommt
    eventPublisher.publishEvent(new MemberRemoved(memberId)); // Zeile 155
}
```
**Dieselbe Timing-Lücke wie bei `HouseholdDeleted`** (Löschung vor Publish). Drei Aufrufpfade
teilen sich `deleteMemberAndPublishEvent`:
1. `leaveHousehold` — Self-Service („Konto verlassen", `DELETE .../members/{memberId}/account`).
2. `removeMember` — Admin entfernt ein Mitglied manuell (P1.6).
3. `UnverifiedAccountExpiryJob.deleteExpiredAccount` (Zeile 90) — automatische Entfernung eines
   unverifizierten **Nicht**-Admin-Mitglieds nach Ablauf der Grace-Period (P2.2), ruft intern
   `removeMember` auf.

Aktueller Konsument: `usersettings/service/UsersettingsService.java:53` (`onMemberRemoved`,
löscht nur die Preferences-Zeile — reines Cleanup, kein Mail-Bezug, bleibt unverändert
funktionsfähig, da nur `memberId()` genutzt wird).

`MemberEntity` (`household/model/MemberEntity.java`) trägt bereits `id, name, email, avatar,
householdId, isAdmin` — Name/E-Mail/`householdId` sind also vor dem Löschen greifbar.

### 2.3 `notifications`-Modul, aktueller Stand

Drei Dateien unter `notifications/internal/`:
- **`EmailSenderService.java`** — kapselt `JavaMailSender`, nutzt ausschließlich
  `SimpleMailMessage` (kein `MimeMessageHelper`, kein HTML, kein Multipart). Methoden:
  `sendVerificationEmail(String toEmail, UUID token)`,
  `sendPasswordResetEmail(String toEmail, UUID token)`,
  `sendVerificationDeletionWarningEmail(String toEmail)`. Betreff/Text sind **hartkodierte
  englische Strings direkt im Code**. Klassenkommentar begründet den Verzicht auf Templating
  explizit als „speculative addition for no current benefit" — diese Einschätzung gilt für den
  damaligen Umfang (P2.2), nicht mehr für die drei jetzt gleichzeitig vorliegenden Anforderungen
  (Mehrsprachigkeit + Selfhoster-Overrides + HTML), siehe Entscheidung 3.3.
- **`AccountRegistrationListener.java`** — reagiert auf `AccountRegistered`,
  `VerificationEmailRequested`, `VerificationDeletionWarningRequested`.
- **`PasswordResetRequestedListener.java`** — reagiert auf `PasswordResetRequested`.

Kein `HouseholdDeletedListener`/`MemberRemovedListener` vorhanden.

### 2.4 Sprache/`UserPreferences` — zentraler Fund für P2.9

Sprache liegt in `usersettings.model.UserPreferencesEntity` (Schema `usersettings`, Tabelle
`user_preferences`, Spalte `language`), Werte laut generiertem `UserPreferences.LanguageEnum`:
`EN("en")`, `DE("de")` — deckungsgleich mit Paraglide im Frontend
(`frontend/project.inlang/settings.json`: `baseLocale: "en"`, `locales: ["en", "de"]`).

**Bereits vorhandenes Named Interface:** `usersettings/PreferencesQuery.java` —
`UserPreferences getPreferencesOrDefault(UUID memberId)`, implementiert von
`UsersettingsService`, liefert bei fehlenden Preferences ein Default-Objekt (kein Fehler). Aktuell
**kein** Konsument außerhalb von `usersettings` selbst.

Der Meta-Plan-Text zu P2.9 spekulierte, `household` müsse die Sprache in die Events packen — das
widerspricht der oben zitierten Modul-Grenzen-Regel (`household` darf `usersettings` nicht
konsumieren). **Auflösung (Entscheidung 3.1):** `notifications` fragt `PreferencesQuery` selbst
ab, wenn es eine Mail zusammenbaut — `household` muss nichts über Sprachen wissen.

Kein serverseitiges Java-i18n (`MessageSource`/Thymeleaf/Freemarker/`ResourceBundle`) vorhanden.

### 2.5 `MemberRepository`/`HouseholdRepository`

`MemberRepository.findByHouseholdId(UUID): List<MemberEntity>` liefert bereits alle Mitglieder
eines Haushalts inkl. Name/E-Mail. `HouseholdRepository` (im selben `household`-Modul) liefert den
Haushaltsnamen — beide sind normale, modul-interne Repository-Dependencies, kein Named-Interface-
Bedarf, da `HouseholdManagementService`/`MemberManagementService` bereits im `household`-Modul
liegen.

### 2.6 Doku-Querverweise

- `docs/architecture/chapters/08_concepts.adoc`: keine bestehenden Einträge zu Event-Timing,
  Templating oder Server-i18n. `VERIFY1`/`RATE1` sind thematisch benachbart, aber nicht direkt
  betroffen.
- `docs/architecture/chapters/11_technical_risks.adoc`, **TD1**: behauptet, es gäbe noch keine
  `ApplicationModules.verify()`-Prüfung — das ist **veraltet**: `ApplicationTests.java:12` ruft
  das bereits auf (hat in einer früheren Runde tatsächlich einen Modul-Zyklus gefangen). Kleiner,
  von P2.8/P2.9 unabhängiger Doku-Fehler; wird im Zuge von Aufgabe P2.9-6 mitkorrigiert, da ohnehin
  dieselbe Datei angefasst wird.

### 2.7 Dependencies

`backend/pom.xml:73` `spring-boot-starter-mail` (Produktion), `:199` `spring-boot-starter-mail-test`
(Test) — beide bereits vorhanden. Kein Thymeleaf/Freemarker/`MessageSource` vorhanden.

**Modul-Graph nach diesem Plan** (zur Zyklus-Kontrolle): `household` ← `usersettings` (bereits
heute, wegen `MemberRemoved`-Konsum) und `household` ← `notifications` (bereits heute, wegen
`AccountTokenIssuer`) sowie neu `usersettings` ← `notifications` (`PreferencesQuery`). Kein Zyklus:
`notifications` ist Blattknoten, der von zwei anderen Modulen liest, aber selbst von niemandem
konsumiert wird.

---

## 3. Design-Entscheidungen (alle mit dem Nutzer besprochen und entschieden)

### 3.1 Sprachauflösung: `notifications` fragt `usersettings.PreferencesQuery` selbst ab — ✅ entschieden

Siehe Begründung in Abschnitt 2.4. Kein neues Feld in bestehenden oder neuen Domain-Events nötig.
Für den Scheduled-Grace-Period-Job (`VerificationDeletionWarningRequested`, kein interaktiver
Request) funktioniert derselbe Mechanismus identisch (die zuletzt gespeicherte Präferenz des
betroffenen Mitglieds wird verwendet) — löst die im Meta-Plan offen gelassene Frage „woher nimmt
der Scheduled Job die Sprache" ohne Sonderfall.

**Konsequenz:** `EmailSenderService` (bzw. eine neue, davor geschaltete Komponente, siehe 3.4)
bekommt `PreferencesQuery` als neue Konstruktor-Dependency.

### 3.2 P2.8-Empfänger: alle betroffenen Mitglieder, inklusive des auslösenden Admins — ✅ entschieden (Nutzervorgabe)

Jeder, dessen Account durch die Operation gelöscht/entfernt wird, bekommt eine Mail — auch der
Admin, der die Löschung manuell ausgelöst hat, bzw. das Mitglied, dessen abgelaufene Verifikation
die Auto-Löschung ausgelöst hat. Keine Sonderbehandlung/kein Ausschluss eines „Auslösers" nötig,
das vereinfacht die Umsetzung erheblich (kein Bedarf, den „aktuell angemeldeten Nutzer" vom
Empfängerkreis auszunehmen) und dient zugleich als Bestätigungs-/Nachweis-Mail.

### 3.3 P2.8-Scope: gilt auch für einzelne Mitglieds-Entfernung, nicht nur ganze Haushalts-Löschung — ✅ entschieden (Nutzervorgabe, erweitert ggü. Meta-Plan-Wortlaut)

Der Meta-Plan-Text sprach nur von „Haushalts-Löschung"; der Nutzer hat den Scope explizit auf alle
drei `deleteMemberAndPublishEvent`-Aufrufpfade erweitert (Self-Service-Austritt, Admin-Entfernung,
automatische Nicht-Admin-Entfernung nach Grace-Period, siehe 2.2). Auch hier gilt Entscheidung 3.2
konsistent: das entfernte/austretende Mitglied selbst bekommt die Mail, unabhängig davon, ob es
selbst „Auslöser" war (Self-Service-Austritt) oder nicht.

### 3.4 Templating: Thymeleaf + Spring `MessageSource`, HTML- und Text-Version aus denselben Übersetzungs-Keys — ✅ entschieden (Rückfrage laut AGENTS.md gestellt und beantwortet)

**Entscheidung (vom Nutzer bestätigt): Thymeleaf, neue Dependency.**

- **Übersetzungstexte:** Spring-eigene `MessageSource`/`ResourceBundle`
  (`backend/src/main/resources/messages/email_en.properties`,
  `backend/src/main/resources/messages/email_de.properties`) — **kein** zusätzliches Dependency,
  da bereits Teil von `spring-context`. Schlüssel-Konvention analog zu Paraglides
  `verification.banner_text`-Stil, z. B. `verification.subject`, `verification.body`,
  `household-deleted.subject`, `household-deleted.body`.
- **HTML-Struktur:** `spring-boot-starter-thymeleaf` (neue Produktions-Dependency), aber
  **eigenständig konfiguriert für E-Mail-Templates**, nicht über die Web-MVC-View-Resolution
  (kein Web-Rendering-Bezug) — ein dedizierter `SpringTemplateEngine`-Bean mit
  `ClassLoaderTemplateResolver` (`prefix: templates/email/`, `suffix: .html`,
  `templateMode: HTML`). Thymeleafs `SpringMessageResolver` bindet sich automatisch an den
  vorhandenen `MessageSource`-Bean, sodass Templates `#{verification.subject}`-Syntax direkt
  nutzen können, ohne dass jede Übersetzung einzeln als Template-Variable durchgereicht werden
  muss.
- **Text-Version:** wird **nicht** über ein zweites Thymeleaf-Template (TemplateMode TEXT)
  gebaut, sondern direkt aus denselben `MessageSource`-Strings zusammengesetzt (ein Template pro
  Mail wäre unnötige Duplikation für eine reine Absatz-Struktur ohne Layout-Bedarf). Versand über
  `MimeMessageHelper` mit `setText(plainText, htmlBody)` (Multipart/alternative).
- **Selfhoster-Override:** ein zweiter, mit `order(1)` vorrangig eingehängter
  `FileTemplateResolver`, der auf ein konfigurierbares, standardmäßig leeres Verzeichnis zeigt
  (`librehousehold.notifications.templates.override-dir`) — existiert dort keine Datei, fällt
  Thymeleaf automatisch auf den `ClassLoaderTemplateResolver` (Bundled-Default, `order(2)`)
  zurück (Standard-Thymeleaf-Mechanismus, kein Custom-Code nötig). Für die Übersetzungstexte
  analog: `MessageSource` mit `setParentMessageSource(...)`, ein
  `ReloadableResourceBundleMessageSource` auf das Override-Verzeichnis zeigend als primäre
  Quelle, der bereits vorhandene klassenpfad-basierte `MessageSource` als Parent/Fallback.
- **Alternative (verworfen):** Eigenbau-Platzhalter-Ersetzung ohne neue Dependency. Verworfen, da
  mehrzeiliges HTML mit Bedingungen/Wiederverwendung (gemeinsames Layout für alle Mails, z. B.
  Header/Footer mit Logo) ohne echte Template-Engine deutlich mehr Boilerplate und Fehleranfälligkeit
  bedeutet hätte — bei drei gleichzeitig zusammenkommenden Anforderungen (i18n + Overrides + HTML)
  ist eine etablierte Bibliothek hier klar im Vorteil, anders als bei der ursprünglichen P2.2-
  Einschätzung mit nur einer einzigen Anforderung (einfacher Text).

---

## 4. Empfohlene Umsetzungsreihenfolge

**P2.9 zuerst, dann P2.8** — abweichend von der numerischen Meta-Plan-Reihenfolge, da P2.8 zwei
neue E-Mail-Typen einführt, die sinnvollerweise direkt auf der neuen Templating-/i18n-
Infrastruktur aufgebaut werden. Würde man P2.8 zuerst mit hartkodierten Strings umsetzen, müsste
das unmittelbar danach für P2.9 wieder angefasst werden — reine Doppelarbeit.

1. **P2.9 Aufgabe 1–2** (Infrastruktur: `MessageSource` + Thymeleaf-Konfiguration, Selfhoster-
   Override-Mechanismus).
2. **P2.9 Aufgabe 3** (bestehende drei E-Mails aus P2.2/P2.5 auf die neue Infrastruktur migrieren —
   beweist, dass die Infrastruktur funktioniert, ohne gleichzeitig neue Fachlogik einzuführen).
3. **P2.8 Aufgabe 1–2** (Events erweitern, Daten vor Löschung einsammeln).
4. **P2.8 Aufgabe 3** (neue Listener + neue E-Mail-Typen, direkt auf der P2.9-Infrastruktur).
5. **P2.9 Aufgabe 4 / P2.8 Aufgabe 4** (Doku-Updates, jeweils am Ende ihres Teilbereichs).

---

## 5. Aufgaben je P-Punkt

### P2.9 — Backend: Konfigurierbare, mehrsprachige HTML+Text-Mails

**Aufgabe 1: `MessageSource` + Thymeleaf-Grundgerüst**

- Neue Dependency in `backend/pom.xml`: `spring-boot-starter-thymeleaf` (Produktion).
- Neue Properties (`librehousehold.notifications.*`-Namespace, Muster wie bestehende
  `librehousehold.security.*`-Einträge inkl. erklärendem Kommentar):
  `librehousehold.notifications.templates.override-dir` (Default leer/nicht gesetzt).
- Neue `@Configuration`-Klasse `.../notifications/internal/EmailTemplatingConfig.java`:
  `MessageSource`-Bean (`ReloadableResourceBundleMessageSource`, Basename
  `classpath:messages/email`, ggf. mit `setParentMessageSource` auf eine zweite Instanz für das
  Override-Verzeichnis, falls die Property gesetzt ist), `SpringTemplateEngine`-Bean mit den zwei
  `ITemplateResolver`n (Override zuerst, Bundled-Default danach, siehe Entscheidung 3.4).
- Neue Ressourcen `backend/src/main/resources/messages/email_en.properties`,
  `.../email_de.properties` (zunächst nur mit Platzhalter-Keys für Aufgabe 3 befüllt).
- Kein klassischer Rot-Grün-Zyklus für reine Konfiguration — Verifikation über einen neuen
  Smoke-Test: `EmailTemplatingConfigIT` (`@SpringBootTest`), der `messageSource.getMessage(...)`
  und `templateEngine.process(...)` für einen Dummy-Key/ein Dummy-Template erfolgreich aufruft.

**Aufgabe 2: Selfhoster-Override nachweisen**

- Test zuerst, `EmailTemplatingConfigIT` erweitert: mit `@DynamicPropertySource` das
  Override-Verzeichnis auf ein `@TempDir` zeigen lassen, dort eine Test-`.properties`-Datei bzw.
  ein Test-Template ablegen, das einen bekannten Schlüssel überschreibt — prüfen, dass der
  überschriebene Wert zurückkommt; ohne Override-Datei muss weiterhin der Bundled-Default kommen.
  - Rot: Override-Mechanismus fehlt/ist falsch verdrahtet.
  - Grün: Resolver-Reihenfolge wie in Entscheidung 3.4 beschrieben.
  - Refactor: keiner nötig bei dieser Größe.

**Aufgabe 3: Bestehende drei E-Mails migrieren**

- `EmailSenderService` umbauen: `sendVerificationEmail`, `sendPasswordResetEmail`,
  `sendVerificationDeletionWarningEmail` nutzen jetzt `MimeMessageHelper` statt
  `SimpleMailMessage`, bauen Betreff/Text über `MessageSource.getMessage(key, args, locale)` und
  den HTML-Body über `templateEngine.process("verification-email", context)` (ein Thymeleaf-
  `Context` mit den nötigen Variablen, z. B. `verificationLink`).
- Neue Konstruktor-Dependency: `PreferencesQuery` (aus `usersettings`, siehe Entscheidung 3.1) —
  jede `send*`-Methode bekommt zusätzlich `UUID memberId` als Parameter (bisher nur `toEmail`),
  um die Sprache aufzulösen (`preferencesQuery.getPreferencesOrDefault(memberId).getLanguage()`).
  **Wichtig:** Alle drei bestehenden Aufrufstellen (`AccountRegistrationListener`,
  `PasswordResetRequestedListener`) müssen entsprechend angepasst werden — die konsumierten
  Events (`AccountRegistered`, `PasswordResetRequested`, `VerificationEmailRequested`,
  `VerificationDeletionWarningRequested`) tragen bereits `memberId`, kein Event muss dafür
  erweitert werden.
- Neue Thymeleaf-Templates `backend/src/main/resources/templates/email/{verification,
  password-reset,verification-deletion-warning}.html` — schlankes gemeinsames Layout (Logo/Header/
  Footer als Thymeleaf-Fragment, `th:insert`/`th:replace`), Farbschema angelehnt an
  `frontend/src/routes/login/+page.svelte` (Primary-Farbe, „LH"-Logo-Kachel) als visuelles
  Vorbild, ohne die Tailwind/DaisyUI-Klassen selbst zu übernehmen (E-Mail-Clients unterstützen
  kein Tailwind — Inline-Styles oder eine minimale, e-mail-sichere CSS-Teilmenge verwenden).
- Test zuerst, je migrierter Methode (Unit, `MessageSource`/`TemplateEngine`/`PreferencesQuery`
  gemockt, `JavaMailSenderImpl` gemockt wie bisher):
  - `sendVerificationEmail_germanPreference_sendsGermanSubjectAndBody`
  - `sendVerificationEmail_noPreferenceSet_fallsBackToEnglish` (deckt `getPreferencesOrDefault`s
    Default-Verhalten ab)
  - analog für die anderen beiden Methoden.
  - Rot: Methoden nutzen noch `SimpleMailMessage`/hartkodierte Strings.
  - Grün: Umbau wie oben beschrieben.
  - Refactor: gemeinsame Helper-Methode `buildAndSend(String toEmail, UUID memberId, String
    templateName, String subjectKey, Map<String, Object> templateVariables)` in
    `EmailSenderService`, um Duplikation zwischen den `send*`-Methoden zu vermeiden.
- **IT-Test:** den bestehenden `EmailDeliveryIT` (Mailpit-Testcontainer, siehe P2.2) erweitern
  oder unverändert lassen, je nachdem ob er bereits parametrisiert ist — muss weiterhin grün
  bleiben und beweist, dass eine echte Mail mit dem neuen `MimeMessageHelper`-Aufbau tatsächlich
  zugestellt wird (Mailpit unterscheidet HTML/Text-Teile, das kann optional mitgeprüft werden).

**Aufgabe 4: Sicherheits-/Technical-Risk-Doku aktualisieren — AUF ENGLISCH**

- `docs/architecture/chapters/11_technical_risks.adoc`, **TD1**: die veraltete Behauptung „no
  `ApplicationModules.verify()` exists" korrigieren/entfernen, da `ApplicationTests.java:12` das
  bereits seit einer früheren Runde aufruft (siehe Ist-Zustand 2.6) — unabhängiger Doku-Fund,
  hier miterledigt, da dieselbe Datei ohnehin für RATE1 (P2.7) zuletzt schon anfasst wurde.
- Kein neuer Sicherheits-Control-Eintrag nötig (P2.9 ist keine Sicherheitsmaßnahme im ASVS-Sinne,
  sondern eine Konfigurierbarkeits-/i18n-Anforderung) — keine `08_concepts.adoc`-Änderung.

### P2.8 — Backend: Mitglieder-Benachrichtigung bei Löschung/Entfernung

**Aufgabe 1: `HouseholdDeleted`-Event erweitern, Daten vor Löschung einsammeln**

- `HouseholdDeleted.java` um `String householdName` und `List<DeletedMember> members` erweitern
  (`DeletedMember` als nested Record `(UUID memberId, String name, String email)` in derselben
  Datei).
- `HouseholdManagementService.deleteHousehold`: **vor** Zeile 50 (`inviteRepository.deleteByHouseholdId`)
  `householdRepository.findById(householdId)` (Name) und `memberRepository.findByHouseholdId(householdId)`
  (Mitgliederliste) laden, in die neue `HouseholdDeleted`-Instanz packen (Zeile 56 entsprechend
  anpassen).
- Test zuerst, Erweiterung von `HouseholdManagementServiceTest`/`...IT` (falls vorhanden, sonst
  neuer Test analog bestehendem Muster):
  - `deleteHousehold_multipleMembers_publishesHouseholdDeletedWithAllMemberData`
  - Rot: Event trägt die Daten noch nicht.
  - Grün: wie oben.
  - Refactor: keiner nötig.
- **Regressionscheck:** `TaskService`/`ExpenseService` (Konsumenten, Abschnitt 2.1) müssen
  weiterhin kompilieren/grün bleiben (nutzen nur `householdId()`, sollte unverändert funktionieren).

**Aufgabe 2: `MemberRemoved`-Event erweitern, Daten vor Löschung einsammeln**

- `MemberRemoved.java` um `String memberName`, `String email`, `String householdName` erweitern.
- `MemberManagementService.deleteMemberAndPublishEvent`: **vor** Zeile 154
  (`memberRepository.deleteById`) das `MemberEntity` laden (Name/E-Mail/`householdId`), darüber
  `householdRepository.findById(member.householdId())` (Name) — neue Konstruktor-Dependency
  `HouseholdRepository` in `MemberManagementService` (selbes Modul, kein Named-Interface-Bedarf).
- Test zuerst, Erweiterung von `MemberManagementServiceTest` (Unit, Repositories gemockt):
  - `leaveHousehold_validCall_publishesMemberRemovedWithMemberAndHouseholdData`
  - `removeMember_validCall_publishesMemberRemovedWithMemberAndHouseholdData`
  - Rot: Event trägt die Daten noch nicht.
  - Grün: wie oben.
  - Refactor: gemeinsame private Helper-Methode, falls Duplikation zwischen den beiden
    Aufrufpfaden entsteht (aktuell teilen sie sich bereits `deleteMemberAndPublishEvent`).
- **Regressionscheck:** `UsersettingsService.onMemberRemoved` (nutzt nur `memberId()`) und
  `UnverifiedAccountExpiryJob`s `removeMember`-Aufrufpfad müssen weiterhin kompilieren/grün
  bleiben.

**Aufgabe 3: Neue `notifications`-Listener + E-Mail-Typen**

- `EmailSenderService` um zwei neue Methoden erweitern:
  `sendHouseholdDeletedEmail(String toEmail, UUID memberId, String memberName, String householdName)`,
  `sendMemberRemovedEmail(String toEmail, UUID memberId, String memberName, String householdName)` —
  nutzen dieselbe in P2.9-Aufgabe 3 gebaute `buildAndSend`-Infrastruktur (Sprache über
  `PreferencesQuery`, Template+Message-Keys `household-deleted.*`/`member-removed.*`).
- Neue Templates `templates/email/{household-deleted,member-removed}.html`, neue Message-Keys in
  `email_en.properties`/`email_de.properties`.
- Neue Datei `.../notifications/internal/HouseholdDeletedListener.java` (`@ApplicationModuleListener`
  für `HouseholdDeleted`, iteriert `event.members()`, ruft je Mitglied `sendHouseholdDeletedEmail`
  auf).
- Neue Datei `.../notifications/internal/MemberRemovedListener.java` (`@ApplicationModuleListener`
  für `MemberRemoved`, ruft `sendMemberRemovedEmail` einmal auf).
- Test zuerst (Unit, `EmailSenderService` gemockt):
  - `HouseholdDeletedListenerTest.on_householdDeletedWithThreeMembers_sendsThreeEmails`
  - `MemberRemovedListenerTest.on_memberRemoved_sendsOneEmail`
  - Rot: Klassen fehlen.
  - Grün: wie oben.
  - Refactor: keiner nötig bei dieser Größe.
- **IT-Test** (Happy-Path-Pflicht, analog `AccountRegistrationListenerIT`):
  `HouseholdDeletedListenerIT`/`MemberRemovedListenerIT` — Event über den echten
  `ApplicationEventPublisher` publizieren, `JavaMailSenderImpl` mocken (Muster aus P2.2/P2.5,
  siehe `AGENTS.md`-Findings „Testing JavaMailSender"), prüfen dass `send(...)` mit der/den
  erwarteten Empfänger-Adresse(n) aufgerufen wurde.

**Aufgabe 4: Sicherheits-/Risiko-Doku aktualisieren — AUF ENGLISCH**

- `docs/architecture/chapters/11_technical_risks.adoc`: falls die in P2.2 aufgedeckte Lücke
  („`HouseholdDeleted` trägt keine Mitgliederdaten") dort als offenes Risiko/TD-Eintrag vermerkt
  wurde, jetzt als erledigt markieren. (Nach aktueller Recherche existiert dafür noch kein
  expliziter TD-Eintrag — falls beim Umsetzen keiner gefunden wird, ist hier nichts zu tun,
  nicht eigenmächtig einen neuen Eintrag erfinden.)
- Kein neuer `08_concepts.adoc`-Sicherheits-Control-Eintrag nötig (P2.8 ist eine
  Nutzerfreundlichkeits-/Transparenz-Maßnahme, kein ASVS-Kontrollpunkt).

---

## Offene Nacharbeit außerhalb dieses Plans

- Keine bekannt. Falls beim Umsetzen weitere `deleteMemberAndPublishEvent`-ähnliche Lücken
  auffallen (z. B. weitere Stellen, die Mitgliederdaten vor einer Löschung bräuchten), gilt die
  Sofort-Rückfrage-Pflicht aus `docs/auth-meta-plan.md` — nicht eigenmächtig als Folge-Task
  vermerken.
