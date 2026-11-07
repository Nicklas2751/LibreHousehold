# Plan: Dokumentation von docToolchain/jBake auf Antora umstellen

Status: Entscheidungen getroffen (Abschnitt 9), Dependencies (Abschnitt 4) freigegeben. Bereit zur Umsetzung.

## 1. Ziele

- Die Site wird weiterhin über GitHub Pages ausgeliefert (Custom Domain `librehousehold.wiegandt.eu`).
- Drei eigenständige Bereiche mit eigener Navigation:
    1. **Architekturüberblick** (heute `overview.adoc`, Startseite der Site)
    2. **ADRs** (heute `adrs/`)
    3. **Vollständige arc42** (heute `chapters/`)
- Die Site ist auf den Ausbau zur Projekt-Website vorbereitet (Landingpage, Anleitungen, siehe
  Abschnitt 8): Die Architektur-URLs bleiben dabei stabil, nur `/` bekommt später einen anderen Inhalt.
- Die AsciiDoc-Dokumente selbst ändern sich so wenig wie möglich: Inhalte, Dateinamen, Anker und
  Tags bleiben; geändert werden nur Header, Pfade und modulübergreifende Referenzen.

## 2. Ist-Zustand

| Aspekt | Heute |
|---|---|
| Generator | docToolchain (`dtcw generateSite`) → jBake mit Docsy-Theme, `docToolchainConfig.groovy` |
| Navigation | `:jbake-menu:` (`overview`, `arc42`, `adrs`) + `:jbake-order:` |
| Seitentitel | `:jbake-title:`; Kapitel und ADR-Index haben **keinen** Doc-Title (`= …`), Kapitel starten mit `==` |
| Pfad-Hacks | `:imagesdir:`, `:relfileprefix:`, `:filename:`, `:toc:` pro Datei |
| Diagramme | 5 PlantUML-Dateien in `images/`, eingebunden per `plantuml::{imagesdir}/…` bzw. `[plantuml]` + `include::../images/…puml[]` |
| Referenzen | ~160 `xref:`; innerhalb eines Ordners (`xref:adr-016.adoc`, `xref:01_….adoc#QG2`) und ordnerübergreifend (`xref:../adrs/adr-011.adoc`, `xref:../chapters/…`) |
| Includes | `overview.adoc` zieht getaggte Abschnitte aus Kapitel 1, 3, 4; Kapitel 9 inkludiert `adrs/index.adoc` |
| Nicht publiziert | `personas/`, `domain-stories/`, `08-concepts-EN.drawio.png` (nirgends referenziert), `adrs/template.adoc` |
| Deployment | `.github/workflows/gh-pages.yml` auf `main` → `build/microsite/output` → `deploy-pages` |

## 3. Zielstruktur

Eine Antora-Komponente `architecture` (versionslos) mit **drei Modulen**, die genau den drei
geforderten Bereichen entsprechen. Ordnerinhalt wird nur per `git mv` verschoben, nicht umbenannt.

```text
docs/
├── antora-playbook.yml              # neu: Site-Konfiguration
├── package.json / package-lock.json # neu: Antora + Extensions, gepinnt
├── supplemental-ui/                 # neu: UI-Anpassungen (GitHub-Link im Header); später eigene Layouts (z. B. home.hbs)
└── architecture/                    # = Antora-Komponente
    ├── antora.yml                   # neu: name: architecture, version: ~, nav-Liste
    ├── modules/
    │   ├── ROOT/                    # Architekturüberblick
    │   │   ├── nav.adoc
    │   │   └── pages/overview.adoc            ← overview.adoc
    │   ├── arc42/                   # vollständige arc42
    │   │   ├── nav.adoc
    │   │   ├── pages/01_…adoc … 12_…adoc     ← chapters/*.adoc
    │   │   ├── partials/*.puml                ← images/*.puml
    │   │   └── images/08-concepts-EN.drawio.png
    │   └── adrs/                    # ADRs
    │       ├── nav.adoc
    │       ├── pages/index.adoc, adr-0xx.adoc ← adrs/*.adoc
    │       └── partials/template.adoc         ← adrs/template.adoc (nicht publiziert)
    ├── personas/                    # bleibt unverändert, Antora ignoriert alles außerhalb von modules/
    └── domain-stories/              # bleibt unverändert
```

**Warum drei Module statt eines Moduls mit Unterordnern:** Antora löst `xref:` relativ zum
`pages/`-Wurzelverzeichnis des Moduls auf, nicht relativ zur aktuellen Datei. Bei einem Modul
mit Unterordnern müssten auch alle ordnerinternen Referenzen (`xref:01_….adoc#QG2`,
`xref:adr-016.adoc`, ~100 Stück) angepasst werden. Mit drei Modulen bleiben sie unverändert; nur
die ~60 ordnerübergreifenden `../`-Referenzen werden mechanisch umgeschrieben. Zusätzlich bilden
die Module die drei geforderten Bereiche 1:1 in Navigation und URL ab:

- `/architecture/overview.html` (Startseite; `/` leitet **übergangsweise** dorthin weiter, bis die
  ROOT-Komponente der Projekt-Website existiert)
- `/architecture/arc42/01_introduction_and_goals.html`
- `/architecture/adrs/adr-011.html`

## 4. Neue Dependencies (Freigabe nötig)

| Paket | Zweck | Alternative |
|---|---|---|
| `antora` (`@antora/cli` + `@antora/site-generator`) | Site-Generator | – (Ziel der Aufgabe) |
| `asciidoctor-kroki` | PlantUML-Rendering; unterstützt `plantuml::partial$…[]` und Antora-Resource-IDs | `asciidoctor-plantuml` (braucht ebenfalls einen PlantUML-Server, weniger gepflegt); PlantUML lokal per Java vorrendern (zusätzlicher Build-Schritt, SVGs würden generiert/eingecheckt) |
| `@antora/lunr-extension` | Statische Volltextsuche, funktioniert mit dem Default UI | keine Suche (docToolchain hatte auch keine); verworfen, weil die Site mit Anleitungen wachsen soll |

Alle MIT bzw. MPL-2.0, kompatibel mit der Projektlizenz. Die Pakete liegen in `docs/package.json`, nicht im Frontend, damit
sich die Lebenszyklen nicht vermischen. UI-Bundle: Antora Default UI (per URL im Playbook,
Snapshot-Bundle gepinnt).

**Kroki:** `kroki-fetch-diagram: true`, damit die SVGs beim Build geholt und als Dateien in die
Site gelegt werden (keine Laufzeitabhängigkeit der Leser zu kroki.io). Server per Attribut
`kroki-server-url` konfigurierbar: Default im Playbook ist kroki.io (lokaler Build ohne Setup), im
CI wird per `--attribute kroki-server-url=http://localhost:8000` der Service-Container
`yuzutech/kroki` genutzt, damit der Build nicht von Verfügbarkeit und Rate-Limits von kroki.io
abhängt (Entscheidung 9.2).

## 5. Umsetzungsschritte

### Schritt 1 – Gerüst
1. `docs/package.json` mit gepinnten Versionen, `npm ci`-fähiges Lockfile.
   Scripts: `npm run build` (`antora --fetch antora-playbook.yml`) und
   `npm run build:strict` (`--log-failure-level=warn`, für CI).
2. `docs/antora-playbook.yml`:
   - `site.title: LibreHousehold` (nicht „Architecture Documentation“: der Titel steht später auf
     allen Seiten der Projekt-Website), `site.url: https://librehousehold.wiegandt.eu`,
     `site.start_page: architecture::overview.adoc` (übergangsweise, entfällt mit der ROOT-Komponente)
   - `content.sources`: `url: ./..` (lokales Repo), `branches: HEAD`, `start_paths: [docs/architecture]`
     (als Liste, damit weitere Komponenten wie `docs/website` nur eine Zeile sind),
     `edit_url: https://github.com/Nicklas2751/LibreHousehold/edit/main/{path}`
   - `antora.extensions: ['@antora/lunr-extension']`
   - `asciidoc.extensions: [asciidoctor-kroki]`, `asciidoc.attributes: { kroki-fetch-diagram: true, … }`
   - `urls.redirect_facility: static` (Redirect-Seiten für `/` → Startseite, funktioniert auf GitHub Pages)
   - `output.dir: ../build/site`
3. `docs/architecture/antora.yml`: `name: architecture`, `title: Architecture`, `version: ~`,
   `nav:` in Reihenfolge ROOT → arc42 → adrs.
4. `.gitignore`: `build/`, `docs/node_modules/`, `docs/.cache/`.

### Schritt 2 – Dateien verschieben (`git mv`, Historie bleibt erhalten)
Wie in Abschnitt 3. Keine Umbenennungen, damit `git log --follow` und alle ordnerinternen xrefs
funktionieren.

### Schritt 3 – Header pro Datei anpassen (mechanisch, per Skript)
- `:jbake-title: X` → Doc-Title `= X` (Antora braucht ihn für Seitentitel, Navigation und
  automatischen xref-Linktext). Kapitel behalten ihre `==`-Überschrift als ersten Abschnitt.
  Bei ADRs, die schon `= ADR …` haben, entfällt nur die jbake-Zeile.
- Entfernen: `:jbake-*:`, `:imagesdir:`, `:relfileprefix:`, `:filename:`, `:toc:`
  (Antora verwaltet Bildpfade selbst; das Default-UI zeigt das Seiten-Inhaltsverzeichnis rechts).
- `chapters/config.adoc` entfällt (nur `:toc:`/`:imagesdir:`).

### Schritt 4 – Referenzen umschreiben (mechanisch, per `sed`)
| Vorher | Nachher |
|---|---|
| `xref:../adrs/adr-0xx.adoc` (in arc42) | `xref:adrs:adr-0xx.adoc` |
| `xref:../chapters/0x_….adoc#anchor` (in ADRs) | `xref:arc42:0x_….adoc#anchor` |
| `xref:0x_….adoc` in `overview.adoc` (lief über `:relfileprefix:`) | `xref:arc42:0x_….adoc` |
| `xref:adr-0xx.adoc` in `adrs/index.adoc` | `xref:adrs:adr-0xx.adoc` (weil Kapitel 9 die Datei inkludiert und xrefs relativ zur **inkludierenden** Seite aufgelöst werden) |
| `<<decision-history>>`, `<<section-subdomain-classification>>` (seitenintern) | unverändert |
| ordnerinterne xrefs (`xref:adr-016.adoc`, `xref:01_….adoc#QG2`) | unverändert |

### Schritt 5 – Includes und Diagramme
- `overview.adoc`: `include::chapters/01_….adoc[tag=…]` → `include::arc42:page$01_….adoc[tag=…]`
  (Tags bleiben unverändert).
- Kapitel 9: `include::../adrs/index.adoc[]` → `include::adrs:page$index.adoc[]`.
- PlantUML, immer **modul-qualifiziert**, weil `overview.adoc` (Modul ROOT) Kapitel 3 inkludiert
  und Resource-IDs sonst im ROOT-Modul gesucht würden:
    - `plantuml::{imagesdir}/system_context.puml[…]` → `plantuml::arc42:partial$system_context.puml[…]`
    - `include::../images/deployment_view.puml[]` → `include::arc42:partial$deployment_view.puml[]`
    - Kapitel 10 (`quality_tree.puml`): verlinkt per `[[#QG1]]` auf Anker der Seite → braucht
      Inline-SVG (`[plantuml,options=inline]`), damit die Links klickbar bleiben.

### Schritt 6 – Navigation
- `ROOT/nav.adoc`: `* xref:overview.adoc[]`
- `arc42/nav.adoc`: Kapitel 1–12 in Reihenfolge (ersetzt `:jbake-order:`)
- `adrs/nav.adoc`: `xref:index.adoc[]` + ADR 001–018 (ersetzt `:jbake-order:`).
  **Neue Pflege-Regel:** Ein neues ADR muss in `adrs/index.adoc` **und** `adrs/nav.adoc`
  eingetragen werden → in `AGENTS.md` und `adrs/template.adoc` dokumentieren.

### Schritt 7 – Lokaler Build und Prüfung
- `cd docs && npm ci && npm run build:strict` → keine Warnungen (Antora meldet jede kaputte
  xref, fehlende Includes/Tags und Bilder).
- Stichproben im Browser: Startseite-Redirect, Overview mit eingebetteten Abschnitten und
  Kontextdiagramm, Kapitel 9 mit ADR-Liste, QG-Links im Qualitätsbaum, Anker `#QG2`,
  „Edit this Page“-Link, Suche findet Begriffe aus ADRs und Kapiteln.

### Schritt 8 – GitHub Pages / CI
- Beide Workflows (`gh-pages.yml`, `docs-ci.yml`) starten `yuzutech/kroki` als Service-Container
  (Port 8000) und bauen mit `--attribute kroki-server-url=http://localhost:8000`.
- `gh-pages.yml`: `dtcw`-Schritte ersetzen durch `actions/setup-node` (mit npm-Cache auf
  `docs/package-lock.json`) → `npm ci` → `npm run build:strict` → `upload-pages-artifact`
  mit `path: ./build/site`. Checkout mit `fetch-depth: 0` ist nicht nötig (Antora nutzt den
  Worktree bei `branches: HEAD`). `.nojekyll` wird nicht gebraucht, weil per Actions deployt
  wird (kein Jekyll-Lauf); schadet aber nicht, falls je auf Branch-Deployment umgestellt wird.
  Custom Domain bleibt in den Repo-Settings, kein `CNAME`-File nötig.
- Neuer reusable Workflow `docs-ci.yml` (nur Build, kein Deploy) für PRs, aufgerufen aus
  `ci.yml`, wenn sich `docs/**` oder der Workflow ändert, und in `ci-result.needs`
  eingetragen (sonst blockiert er keinen Merge, siehe AGENTS.md „CI result“). Damit fallen
  kaputte xrefs schon im PR auf statt erst nach dem Merge.
- `dependabot.yml`: npm-Ökosystem für `/docs` ergänzen (gleiche Gruppierung wie Frontend).

### Schritt 9 – Aufräumen und Doku
- Löschen: `dtcw`, `docToolchainConfig.groovy`, `chapters/config.adoc`.
- `AGENTS.md`: „docToolchain“ → Antora; Build-Befehle, Modulstruktur, xref-Regeln
  (`modul:datei.adoc`, modul-qualifizierte Resource-IDs in inkludierbaren Abschnitten),
  Pflege-Regel für ADRs; Hinweis auf `build/site`.
- Kapitel 2 / 8 / 12 auf Erwähnungen der Doku-Toolchain prüfen (aktuell keine gefunden).
- README-Link `https://librehousehold.wiegandt.eu` bleibt gültig.

## 6. Commit-Schnitt (ein PR, mehrere Commits)

1. `chore(docs): move architecture docs into Antora module layout` (nur `git mv`, damit die
   Historie sauber verfolgbar bleibt)
2. `docs: convert jbake headers and cross-module references for Antora`
3. `chore(docs): add Antora playbook, component descriptor and navigation`
4. `ci(docs): build documentation with Antora and verify it in pull requests`
5. `chore(docs): remove docToolchain and document the Antora workflow in AGENTS.md`

## 7. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| Kroki-Server nicht erreichbar → Build bricht | Service-Container im CI; lokal Default kroki.io oder `docker run yuzutech/kroki` |
| Alte URLs (`/chapters/…html`, `/adrs/…html`) brechen externe Links | Bewusst akzeptiert (Entscheidung 9.3): Die Site ist jung, die README verlinkt nur die Domain-Wurzel. |
| Includes mit Tags über Modulgrenzen | `--log-failure-level=warn` meldet fehlende Tags |
| ADR-Liste doppelt gepflegt (index + nav) | Pflege-Regel in AGENTS.md und ADR-Template |

## 8. Ausblick: Projekt-Website (nicht Teil dieser Migration)

`librehousehold.wiegandt.eu` soll später die Website des Projekts werden: Landingpage mit
Funktionen und Screenshots, Anleitungen (Installation, Contributing, ggf. Benutzerhandbuch) und
die Architekturdoku. Das ist mit Antora ohne zweiten Generator möglich:

- **ROOT-Komponente** (`docs/website/antora.yml` mit `name: ROOT`, `version: ~`): Antora legt sie
  direkt in die Site-Wurzel, ihre `index.adoc` wird automatisch die Startseite `/`.
  `site.start_page` entfällt dann. Unterseiten wie `/installation.html` und `/contributing.html`
  sind normale Seiten dieser Komponente. Größere Anleitungen können eine eigene Komponente werden
  (z. B. `/user-guide/…`).
- **Eigenes Layout für die Landingpage:** `:page-layout: home` im Seiten-Header, Template
  `supplemental-ui/layouts/home.hbs` plus CSS (Hero, Feature-Kacheln, Screenshot-Galerie, ohne
  Doku-Navigation). Alle anderen Seiten behalten das Doku-Layout. Das ist der größte Aufwand:
  eigenes HTML/CSS, weil das Default UI auf Doku ausgelegt ist.
- **Architektur bleibt unter `/architecture/…`**, keine URL ändert sich.
- **Contributing:** Antora liest nur Dateien innerhalb der `start_paths`, ein `CONTRIBUTING.md` im
  Repo-Root kann nicht eingebunden werden. Die Anleitung liegt deshalb in Antora, `CONTRIBUTING.md`
  verlinkt nur darauf.
- **Personas** können als Quelle für einen Abschnitt „Für wen ist LibreHousehold?“ dienen.

Diese Migration bereitet das vor durch: neutralen `site.title`, `start_paths` als Liste,
`supplemental-ui/` als Ort für Layouts und die nur übergangsweise gesetzte `start_page`.

## 9. Entscheidungen

1. **Komplette arc42 auf einer Seite:** Nein. Bei Bedarf (z. B. PDF) später als eine Seite mit
   `include::…[leveloffset=+1]` nachrüstbar.
2. **Kroki:** Eigener Service-Container `yuzutech/kroki` im CI; lokal kroki.io oder ein per
   `docker run` gestarteter Container.
3. **Alte URLs:** Werden aufgegeben, keine Redirects.
4. **Suche:** `@antora/lunr-extension` wird aufgenommen.
5. **Personas/Domain Stories:** Bleiben unpubliziert (außerhalb von `modules/`).
