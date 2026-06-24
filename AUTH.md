# LibreHousehold — Authentifizierung & Autorisierung: Brainstorming

> **Status:** Laufendes Brainstorming / Entscheidungsfindung — noch kein ADR erstellt  
> **Zuletzt aktualisiert:** 2026-06-23 (Architektur ausgearbeitet, Rollenmodell definiert)  
> **Kontext:** Architektur-Entscheidung für Auth-Strategie unter Berücksichtigung von Selbst-Hosting, UX und Wartbarkeit

---

## 1. Zielbild & Randbedingungen

### Qualitätsziele (aus Arc42 / AGENTS.md)

| Ziel | Relevanz für Auth |
|---|---|
| **Selbst-hostbar** | Auth-Lösung muss ohne externe Dienste betrieben werden können |
| **Einfach für Nutzer** | Login mit bekannten Providern (Google, Apple) soll möglich sein |
| **Open Source** | Keine Lock-ins zu proprietären Diensten |
| **Modular** | Auth ist ein Modul — klar getrennt vom Fachcode |
| **Sicherheit** | Argon2id für Passwörter bereits entschieden (ADR-009); Token-Sicherheit |

### Kernforderungen

1. Unterstützung von **OAuth 2.0 / OpenID Connect (OIDC)** als Protokoll
2. Konfigurierbar: **lokal**, **extern** oder **beides** — je nach Selfhoster-Präferenz
3. Nutzer können zwischen lokalem Account und externem IdP wählen
4. Unterstützung für **Google, Apple** und mindestens **einem europäischen Nicht-BigTech-Anbieter**
5. Spring Boot 4 Ökosystem bevorzugt

---

## 2. Auth-Strategien (Überblick)

Fünf grundlegende Ansätze, wie LibreHousehold Authentication aufbauen kann:

### Option A: Lokal-only (Username + Passwort)
**Beschreibung:** Spring Security mit eigenem Passwort-Login, JWTs selbst ausgestellt, Argon2id für Hashing.

| Kriterium | Bewertung |
|---|---|
| Selbst-hosting | ✅ Ideal — keine externe Abhängigkeit |
| Setup-Aufwand | ✅ Gering |
| OIDC-Support | ❌ Nicht vorhanden |
| Externe Provider | ❌ Nicht vorhanden |
| Wartung | ⚠️ Eigene Accountverwaltung (Passwort-Reset, E-Mail-Verifikation etc.) selbst bauen |

**Für wen geeignet:** Minimale Setups, Offline-Betrieb, wenn externe Provider ausgeschlossen sein sollen.

---

### Option B: OIDC-only (reines Delegation)
**Beschreibung:** Kein lokaler Login, alles wird an externe OIDC-Provider delegiert. App agiert als reiner Resource Server.

| Kriterium | Bewertung |
|---|---|
| Selbst-hosting | ⚠️ Erfordert externen IdP (eigenen oder Dienst) |
| Setup-Aufwand | ✅ Einfach für Nutzer, komplexer für Hoster |
| OIDC-Support | ✅ Vollständig |
| Externe Provider | ✅ Beliebige OIDC-Provider |
| Wartung | ✅ Account-Verwaltung beim IdP |

**Für wen geeignet:** Wenn Nutzer ohnehin einen IdP haben (z. B. Firmen mit Entra ID / Keycloak).

---

### Option C: Hybrid — Lokal + externe OIDC-Provider ⭐
**Beschreibung:** Embedded Spring Authorization Server für lokale Accounts + Federation zu externen OIDC-Providern. Per Konfiguration wählbar, was aktiv ist.

| Kriterium | Bewertung |
|---|---|
| Selbst-hosting | ✅ Funktioniert ohne externe Dienste |
| Setup-Aufwand | ✅ Für Nutzer transparent |
| OIDC-Support | ✅ Vollständig |
| Externe Provider | ✅ Google, Apple, europäische Provider |
| Wartung | ⚠️ Komplexer in Entwicklung, aber etabliertes Framework |

**Für wen geeignet:** LibreHousehold — bietet maximale Flexibilität für verschiedene Hoster-Szenarien.

---

### Option D: Externer selbst-gehosteter IdP (Keycloak, Authentik etc.)
**Beschreibung:** Separater Identity Provider als Docker-Dienst, LibreHousehold nutzt ihn nur als Resource Server.

| Kriterium | Bewertung |
|---|---|
| Selbst-hosting | ⚠️ Zusätzlicher Dienst — höhere Komplexität für Hoster |
| Setup-Aufwand | ⚠️ Docker Compose mit 2-3 Diensten |
| OIDC-Support | ✅ Vollständig, IdP-seitig |
| Externe Provider | ✅ Der IdP brokert zu Google/Apple etc. |
| Wartung | ⚠️ Zwei zu wartende Systeme |

**Für wen geeignet:** Nutzer die bereits einen IdP betreiben oder im Homelab professionelle Setups wollen.

---

### Option E: API-Key / Session-only (kein OIDC)
**Beschreibung:** Stateful Sessions oder langlebige API-Keys, kein Token-Standard.

| Kriterium | Bewertung |
|---|---|
| Selbst-hosting | ✅ |
| Setup-Aufwand | ✅ Sehr einfach |
| OIDC-Support | ❌ |
| Externe Provider | ❌ |
| Wartung | ⚠️ Session-Management, kein Standard |

**Für wen geeignet:** Nicht für LibreHousehold empfohlen — zu limitiert für PWA + mobile Nutzung.

### ✅ Empfehlung

**Option C (Hybrid)** ist die beste Wahl für LibreHousehold:
- Selfhoster können per `application.yaml` konfigurieren, was sie wollen
- Kein Zwang zu externen Diensten
- Nutzer können selbst wählen (lokaler Account oder externer Provider)
- Spring Authorization Server macht das im Java-Ökosystem elegant möglich

---

## 3. Spring Boot 4 Unterstützung (Details)

Spring bietet drei relevante Module, die perfekt zusammenspielen:

### 3.1 Spring Authorization Server ⭐

**Was es ist:** Vollständige OAuth 2.1 + OIDC 1.0 Implementierung, direkt **einbettbar in den Monolith** — kein separater Dienst.

**Kernkonzepte:**

| Komponente | Funktion |
|---|---|
| `RegisteredClientRepository` | Verwaltet OAuth2-Clients (InMemory oder JDBC) |
| `OAuth2AuthorizationService` | Speichert ausgestellte Tokens (`JdbcOAuth2AuthorizationService` für Produktion) |
| `UserDetailsService` | Lokale Benutzer (Formular-Login) |
| RSA Key Pair | JWT-Signierung — muss selbst verwaltet werden |

**Lokaler Login + Social Login gleichzeitig** — offiziell unterstütztes Pattern:

```java
// Federation + lokales Login parallel in einer Konfiguration:
http
    .formLogin(Customizer.withDefaults())   // lokale Accounts (Argon2id)
    .oauth2Login(Customizer.withDefaults()); // Social Login (Google, Apple...)
```

Der Spring Authorization Server bietet einen offiziellen Guide (`how-to-social-login`) für genau dieses Muster:

1. Nutzer wählt beim Login: lokaler Account oder externer Provider
2. Beim **ersten** Social Login: automatischer Account-Anlage via `FederatedIdentityAuthenticationSuccessHandler` (JIT Provisioning)
3. Der lokale AS stellt danach **immer sein eigenes JWT** aus — das Frontend kennt nur einen Issuer
4. Die REST API validiert nur gegen diesen einen vertrauenswürdigen Issuer

**Spring Boot 4 Kompatibilität:** Vollständig — offizielles Spring-Projekt.

**Einschränkungen:**
- Kein Admin-UI — alles Java-Code oder `application.yml`
- RSA-Schlüsselverwaltung und -Rotation muss selbst gelöst werden
- Kein grafisches Client-Management für Endnutzer

---

### 3.2 Spring Security OAuth2 Resource Server

Die REST API von LibreHousehold wird als **Resource Server** konfiguriert — validiert JWTs stateless.

**Minimale Konfiguration:**
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://my-auth-server.com  # Lokaler AS oder externer
```

**Multi-Tenancy** (`JwtIssuerAuthenticationManagerResolver`): Erlaubt mehrere vertrauenswürdige Issuer parallel — relevant wenn LibreHousehold direkt externen Providern vertrauen würde. **Bei der Embedded-AS-Architektur nicht benötigt** — es gibt nur einen Issuer (den lokalen AS).

---

### 3.3 Spring Security OAuth2 Client

Wird intern vom Spring Authorization Server genutzt, um sich bei externen Providern (Google, Apple etc.) zu authentifizieren.

**Eingebaute Auto-Config für:** Google, GitHub, Facebook, Okta  
**Alle anderen OIDC-Provider** (Apple, Authentik, Zitadel...): generische `ClientRegistration`-Properties

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
          custom-zitadel:           # Beliebiger OIDC-Provider
            client-id: ${ZITADEL_CLIENT_ID}
            client-secret: ${ZITADEL_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            provider: zitadel
        provider:
          zitadel:
            issuer-uri: ${ZITADEL_ISSUER_URI}
```

**Hinweis:** Das Frontend (SvelteKit als SPA) führt den Browser-Teil des OIDC-Flows durch — Spring OAuth2 Client wird nur server-seitig für Federation im Authorization Server benötigt.

### Spring Boot 4 Besonderheiten
- Spring Boot 4 basiert auf Spring Framework 7 (Jakarta EE 11)
- Spring Authorization Server läuft auf Spring Boot 4
- Virtual Threads (Project Loom) kompatibel
- Native Compilation (GraalVM): noch begrenzte Unterstützung für OAuth2-Module

---

## 4. Selbst-gehostete Identity Provider (Vergleich)

Optionen für Selfhoster, die einen eigenen IdP betreiben wollen (für Option D oder als Alternative zu embedded Spring AS):

### 4.1 Keycloak
- **Entwickler:** Red Hat / JBoss
- **Sprache:** Java (Quarkus)
- **Verbreitung:** Sehr hoch — de-facto Standard in Enterprise
- **Features:** OIDC, SAML 2.0, Social Login, Admin UI, User Federation (LDAP/AD), Themes, Realm-Konzept
- **RAM-Bedarf:** ~500 MB – 1 GB im Betrieb
- **Setup-Komplexität:** Hoch — viele Konzepte (Realms, Clients, Flows)
- **Docker:** Ja, offizielles Image
- **Für LibreHousehold:** Zu schwer für typische Selfhoster; Konfiguration schreckt ab

### 4.2 Authentik
- **Entwickler:** Beryju.io (europäisches Startup, Deutschland)
- **Sprache:** Python (Django) + Go (Proxy)
- **Verbreitung:** Mittel, stark wachsend im Homelab-Bereich
- **Features:** OIDC, SAML, LDAP, Flow-basierte Auth (visuell konfigurierbar), Social Login, MFA
- **RAM-Bedarf:** ~400–600 MB (inkl. Worker + Redis)
- **Setup-Komplexität:** Mittel — Docker Compose mit 3-4 Diensten
- **Docker:** Ja, gut dokumentiert
- **Für LibreHousehold:** Gut für technikaffine Selfhoster; europäisches Projekt

### 4.3 Zitadel
- **Entwickler:** CAOS AG (Schweiz) 🇨🇭
- **Sprache:** Go
- **Verbreitung:** Wachsend, besonders im Developer-Bereich
- **Features:** OIDC, SAML, MFA, Organizations/Tenants, gute REST API
- **RAM-Bedarf:** ~200–400 MB
- **Setup-Komplexität:** Mittel — gute Dokumentation, Cloud oder self-hosted
- **Docker:** Ja, auch als Single-Binary ohne externe DB möglich (CockroachDB oder PostgreSQL)
- **Für LibreHousehold:** Starker Kandidat — europäisch, Developer-freundlich, gute API

### 4.4 Authelia
- **Entwickler:** Open Source Community
- **Sprache:** Go
- **Verbreitung:** Hoch im Homelab/Selfhosting-Bereich
- **Features:** 2FA/MFA, Forward Auth (Nginx/Traefik), OIDC (seit 4.35), LDAP
- **RAM-Bedarf:** ~50–100 MB — sehr leichtgewichtig
- **Setup-Komplexität:** Niedrig — einzelner Dienst
- **Docker:** Ja
- **Für LibreHousehold:** Gut als leichter IdP, aber primär für Forward-Auth konzipiert — OIDC ist sekundär

### 4.5 Kanidm
- **Entwickler:** Open Source (Australien)
- **Sprache:** Rust
- **Verbreitung:** Niedrig — noch rel. jung
- **Features:** OIDC, LDAP, Unix-Integration, Security-first Design, No-Passwort-Login
- **RAM-Bedarf:** ~50–100 MB
- **Setup-Komplexität:** Niedrig
- **Docker:** Ja
- **Für LibreHousehold:** Interessant wegen Security-Fokus, aber geringe Verbreitung = weniger Community-Support

### 4.6 Ory (Hydra + Kratos)
- **Entwickler:** Ory Corp (Deutschland 🇩🇪)
- **Sprache:** Go
- **Verbreitung:** Mittel, Enterprise-Fokus
- **Features:** Hydra = OAuth2/OIDC Server; Kratos = Identity/User Management; Oathkeeper = API Gateway; Keto = Permissions
- **RAM-Bedarf:** ~200 MB (mehrere Dienste)
- **Setup-Komplexität:** Hoch — modulares System, viele Einzelkomponenten
- **Docker:** Ja
- **Für LibreHousehold:** Zu modular/komplex für einfache Setups; interessant für fortgeschrittene Hoster

### 4.7 Spring Authorization Server (embedded)
- **Entwickler:** Spring Team (VMware/Broadcom)
- **Sprache:** Java
- **Verbreitung:** Mittel, wächst
- **Features:** OAuth 2.1, OIDC 1.0, Federation zu externen Providern, vollständig konfigurierbar
- **RAM-Bedarf:** 0 extra (in der App eingebettet)
- **Setup-Komplexität:** Niedrig für Nutzer — kein extra Dienst
- **Für LibreHousehold:** ⭐ Ideal für Hybrid-Ansatz — keine extra Infrastruktur

### ✅ Empfehlung für Selfhoster die externen IdP wollen

**Zitadel** als beste Option für technikaffine Nutzer:
- Europäisch (Schweiz)
- Developer-freundlich
- Gute Dokumentation
- Single-Binary möglich (kein komplexes Setup)

**Authentik** als zweite Wahl für Homelab-Nutzer (visuelle Konfiguration, deutsch).

---

## 5. Externe OIDC-Provider (für Nutzer-Login)

### 5.1 Google
- **Verbreitung:** Sehr hoch — nahezu jeder hat einen Google-Account
- **OIDC-Support:** ✅ Vollständig, gut dokumentiert
- **Datenschutz:** ⚠️ US-Unternehmen, GDPR-Spannung
- **Spring Boot:** Auto-konfiguriert, `spring.security.oauth2.client.registration.google.*`
- **Besonderheiten:** `sub` als User-ID, `email_verified` claim

### 5.2 Apple (Sign in with Apple)
- **Verbreitung:** Hoch bei iOS/macOS-Nutzern
- **OIDC-Support:** ✅ Aber proprietäre Eigenheiten (JWT als `client_secret`, kein UserInfo Endpoint)
- **Datenschutz:** ⚠️ US-Unternehmen, aber privacy-focused
- **Spring Boot:** Nicht auto-konfiguriert — manuell als Custom Provider, komplexer
- **Besonderheiten:** E-Mail-Relay (Hide My Email), Tokens nur beim ersten Login, kein Standard-UserInfo

### 5.3 Microsoft / Entra ID (Azure AD)
- **Verbreitung:** Hoch im Enterprise-Bereich
- **OIDC-Support:** ✅ Multi-Tenant möglich
- **Datenschutz:** ⚠️ US-Unternehmen
- **Spring Boot:** Auto-konfiguriert mit `registration.microsoft`
- **Besonderheiten:** Tenant-Konzept; für Consumer: Microsoft-Accounts

### 5.4 GitHub
- **Verbreitung:** Hoch bei Entwicklern
- **OIDC-Support:** ⚠️ OAuth2 aber KEIN vollständiges OIDC (kein `id_token`)
- **Datenschutz:** ⚠️ US-Unternehmen (Microsoft)
- **Spring Boot:** Auto-konfiguriert
- **Für LibreHousehold:** Weniger relevant (kein OIDC, Developer-Nische)

### 5.5 GitLab / Codeberg (europäisch)
- **GitLab.com:** US-Hosting, aber Open Source — selbst-hostbar
- **Codeberg.org:** 🇩🇪 Deutschland, Gitea-basiert, gemeinnützig
  - Unterstützt OAuth2/OIDC als Provider
  - Klein, aber privacy-respektierend
- **OIDC-Support:** ✅ (Gitea/Forgejo-basiert)
- **Für LibreHousehold:** Geeignet als "europäischer Alternative"-Option, auch wenn Verbreitung gering

### 5.6 Nextcloud (selbst-hostbar, europäisch)
- **Entwickler:** Nextcloud GmbH 🇩🇪 (Deutschland)
- **OIDC-Support:** ✅ Als Provider über Social Login App
- **Selbst-hostbar:** ✅ Viele Hoster betreiben bereits Nextcloud
- **Für LibreHousehold:** Interessant für Selfhoster die beides betreiben — Nextcloud als IdP für LibreHousehold
- **Einschränkung:** Nur relevant wenn Nutzer bereits Nextcloud hat

### 5.7 Proton (Schweiz, in Entwicklung)
- **Entwickler:** Proton AG 🇨🇭 (Schweiz), privacy-first
- **OIDC-Support:** ⚠️ Momentan nicht als Third-Party-OIDC-Provider verfügbar
- **Status:** Proton ist selbst OAuth-Client, bietet aber keine OIDC-Provider-Funktionalität für Dritte
- **Für LibreHousehold:** Aktuell nicht integrierbar als OAuth-Provider

### 5.8 eIDAS / Nationale eID-Systeme (gouvernemental, europäisch)
- **Beispiele:** ID Austria, France Connect, BankID (Nordics), eID Deutschland
- **OIDC-Support:** ✅ Über OIDC-Broker
- **Verbreitung:** National begrenzt
- **Für LibreHousehold:** Interessant perspektivisch, aber zu komplex und fragmentiert für v1

### ✅ Empfehlung Provider-Set für LibreHousehold

**Tier 1 — Standard:**
- Google (hohe Verbreitung)
- Apple (iOS-Nutzer)

**Tier 2 — Europäisch / Alternativ:**
- Zitadel (selbst-gehosteter europäischer IdP)
- Authentik (deutsches Open-Source-Projekt)
- Codeberg (europäische Open-Source-Alternative für Developer-affine Nutzer)

**Konfigurierbar per YAML** — Hoster schalten Provider ein/aus. Kein Provider ist erzwungen.

---

## 6. Architektur-Vorschlag: Hybrid-Modell

```
┌─────────────────────────────────────────────────────────────────┐
│                    LibreHousehold Backend                        │
│                                                                 │
│  ┌─────────────────────────────┐  ┌──────────────────────────┐  │
│  │  Spring Authorization Server│  │  Resource Server          │  │
│  │  (optional, per config)     │  │  (immer aktiv)            │  │
│  │                             │  │                           │  │
│  │  - Lokale Accounts          │  │  - JWT-Validierung        │  │
│  │  - Federation zu externen   │  │  - Multi-Issuer-Support   │  │
│  │    OIDC-Providern           │  │  - Rollen/Scopes          │  │
│  └─────────────────────────────┘  └──────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
              ▲                               ▲
              │ OIDC Federation               │ JWT Bearer Token
              │                               │
    ┌─────────┴──────────┐         ┌──────────┴─────────┐
    │  Externe Provider  │         │  SvelteKit Frontend │
    │  - Google          │         │  - PKCE Flow        │
    │  - Apple           │         │  - Token Storage    │
    │  - Zitadel (self)  │         │  - Silent Refresh   │
    └────────────────────┘         └────────────────────┘
```

### Konfigurationsbeispiel (Selfhoster-Sicht)

```yaml
# application.yaml — Selfhoster wählt was er will

librehousehold:
  auth:
    local:
      enabled: true          # Lokale Accounts an/aus
    oidc:
      providers:
        google:
          enabled: true
          client-id: ${GOOGLE_CLIENT_ID}
          client-secret: ${GOOGLE_CLIENT_SECRET}
        apple:
          enabled: false     # Standard: aus
        custom:
          enabled: true      # Eigener Zitadel/Keycloak etc.
          issuer-uri: ${OIDC_ISSUER_URI}
```

### Token-Flow (PKCE, SPA-kompatibel)

```
SvelteKit Frontend                Spring Auth Server / Ext. Provider
       │                                     │
       │── Authorization Request (PKCE) ────►│
       │◄─ Authorization Code ───────────────│
       │                                     │
       │── Code + code_verifier ────────────►│
       │◄─ access_token + id_token ──────────│
       │                                     │
       │── API Request (Bearer token) ──────►│ (Resource Server)
       │◄─ Protected Resource ───────────────│
```

---

## 7. Entschiedene Fragen

### 7.1 Account-Verknüpfung (entschieden: nein)

Lokale Accounts und OIDC-Accounts bleiben getrennt. Kein automatisches Zusammenführen bei gleicher E-Mail. Ein Nutzer hat entweder einen lokalen Account oder einen über einen externen Provider — nicht beides.

**Konsequenz:** Beim Onboarding wählt der Nutzer einmalig seinen Login-Weg. Wechsel nachträglich nicht vorgesehen (MVP).

---

### 7.2 Token-Lebensdauer (OWASP-Empfehlungen)

Quellen: OWASP ASVS, OWASP OAuth2 Cheat Sheet, IETF RFC 9700 (OAuth 2.0 Security BCP)

| Token-Typ | Empfohlene Lebensdauer | Begründung |
|---|---|---|
| Access Token | **5–15 Minuten** | Kleines Angriffsfenster bei Kompromittierung; stateless JWTs können nicht revoked werden |
| Refresh Token | **30 Tage** | Für eine Haushalts-App vertretbar; OWASP gibt keine normative Vorgabe |
| Refresh Token absolut | **Max. 30 Tage** | Danach erzwungener Re-Login unabhängig von Nutzung |

**Refresh Token Rotation:** Bei jedem Token-Refresh wird ein neues Refresh Token ausgegeben und das alte sofort invalidiert. Wird ein bereits genutztes (altes) Refresh Token erneut verwendet, werden **alle** aktiven Sessions des Nutzers invalidiert — automatische Erkennung von Token-Diebstahl. Spring Authorization Server unterstützt das nativ.

**BFF vs. direkte SPA-Token-Verwaltung:**

IETF Draft "OAuth 2.0 for Browser-Based Apps" (2026) unterscheidet zwei Muster:

| Muster | Ansatz | Schutz |
|---|---|---|
| **BFF** (Backend for Frontend) | Backend ist confidential OAuth-Client; gibt nur HttpOnly-Session-Cookie an Browser; kein Token im JS | Stärkster Schutz (empfohlen) |
| **Direkt / TMB** | Frontend erhält Token via PKCE, speichert nur im Memory (nie localStorage) | Gut, solange kein XSS |

**Für LibreHousehold:** BFF würde bedeuten, dass das Backend Sessions verwaltet — das widerspricht dem stateless JWT-Ansatz. **Empfehlung: PKCE + Token im Memory (nie localStorage/sessionStorage)** mit kurzen Access-Token-Lifetimes und Refresh-Token-Rotation. Das entspricht dem bestehenden State-Management-Prinzip aus AGENTS.md ("No localStorage writes").

---

### 7.3 PKCE — was ist das?

**PKCE** (Proof Key for Code Exchange, RFC 7636) ist eine Sicherheitserweiterung für den OAuth2 Authorization Code Flow, speziell für Public Clients (SPAs, mobile Apps) die kein `client_secret` geheimhalten können.

**Das Problem ohne PKCE:** Ein Angreifer könnte den Authorization Code (der kurz in der URL auftaucht) abfangen und gegen ein Access Token eintauschen.

**PKCE-Ablauf:**

```
1. Frontend erzeugt:
   code_verifier = zufälliger String (min. 43 Zeichen)
   code_challenge = BASE64URL(SHA256(code_verifier))

2. Authorization Request enthält: code_challenge
   → Server speichert die Challenge

3. Token Request enthält: code_verifier
   → Server prüft: SHA256(code_verifier) == gespeicherte Challenge
   → Nur der ursprüngliche Client kann das lösen
```

**Für LibreHousehold:** PKCE ist Pflicht für die SvelteKit-SPA. Spring Authorization Server erzwingt das für public clients automatisch.

---

### 7.4 Offline-Verhalten von JWTs

Kein wirkliches Problem: JWTs sind selbst-validierend (Signaturprüfung ohne Server-Roundtrip). Solange das Access Token nicht abgelaufen ist, funktioniert die App offline. Bei Ablauf braucht es eine Netzwerkverbindung für den Token-Refresh — das ist für eine Haushalts-App akzeptabel.

---

### 7.5 OIDC Account-Löschung

Das Problem: Löscht ein Nutzer seinen Google-Account, bekommt LibreHousehold keine sofortige Benachrichtigung — da JWTs selbst-validierend sind, läuft die aktive Session einfach weiter bis zum Ablauf.

**Drei etablierte Patterns:**

**Pattern 1: OIDC Back-Channel Logout** (RFC 9456 / OpenID Connect Spec)  
Der IdP sendet bei Logout/Account-Löschung einen signierten `logout_token` per HTTP POST an eine registrierte Callback-URI. Spring Security unterstützt das mit `OidcBackChannelServerLogoutHandler`.  
*Problem:* Provider-Support variiert stark — Google: ✅ unterstützt; Apple: ⚠️ eingeschränkt; kleinere Provider: ❌ oft nicht.

**Pattern 2: Refresh-Token-Fehler-Detection** (de-facto Standard, universell)  
Wenn der Access-Token abläuft (nach 5–15 Min.) und der Refresh-Token-Call `invalid_grant` zurückgibt → federated Identity ist weg. App invalidiert die Session.  
Da Access-Tokens so kurz leben, wird der Verlust automatisch innerhalb von Minuten erkannt — ohne aktive Push-Benachrichtigung. Das nutzen GitHub, Netlify und die meisten anderen Apps.

**Pattern 3: Soft-Delete der federated Identity**  
Der LibreHousehold-Account bleibt mit allen Haushaltsdaten erhalten. Nur die `federated_identity`-Verknüpfung in der DB wird als `revoked = true` markiert. Beim nächsten Login-Versuch bekommt der Nutzer eine klare Meldung statt eines generischen Fehlers.

**Empfehlung für LibreHousehold:**
1. **Back-Channel Logout registrieren** für Provider die es unterstützen (Google) — wenig Aufwand, schnellere Erkennung
2. **Refresh-Token-Fehler** als universellen Fallback-Mechanismus nutzen
3. Bei Account-Verlust: Soft-Delete der federated_identity, LibreHousehold-Daten bleiben erhalten
4. Fehlermeldung: "Dein Google-Account ist nicht mehr verknüpft. Bitte kontaktiere den Haushalts-Admin."

*Da Accounts nicht verknüpft werden (Entscheidung 7.1), ist für Nutzer mit lokalem Account kein separater Fallback nötig.*

---

### 7.6 Rollenmodell (entschieden: fest kodiert via Spring Annotations, Rolle im JWT)

Zwei Rollen + Author-Check, **nicht konfigurierbar**, hart in Spring Method Security kodiert:

| Rolle | Beschreibung |
|---|---|
| `HOUSEHOLD_MEMBER` | Lesen und Schreiben im eigenen Haushalt |
| `HOUSEHOLD_ADMIN` | Alles was MEMBER kann + Haushalt verwalten (einladen, Members entfernen, umbenennen) |
| Author-Check | Bestimmte Ressourcen (z. B. Ausgaben, Aufgaben) darf nur der Ersteller bearbeiten/löschen |

Rollen werden **im JWT gespeichert** (`role` Claim). Die Rolle ändert sich selten; beim Admin-Transfer werden beide betroffenen Nutzer abgemeldet (alle Refresh Tokens invalidiert), sodass das nächste JWT die korrekte Rolle enthält.

Der Author-Check bleibt **DB-basiert**, da die Autor-ID einer Ressource nicht im Token stehen kann:

```java
// Rolle aus JWT-Claim lesen — kein DB-Lookup:
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public void removeMember(UUID householdId, UUID memberId) { ... }

// household_id aus JWT-Claim lesen:
@PreAuthorize("@householdSecurity.isInHousehold(#householdId)")
public List<Task> getTasks(UUID householdId) { ... }

// Author-Check: DB-Lookup unvermeidlich:
@PreAuthorize("@resourceSecurity.isAuthor(#expenseId)")
public void deleteExpense(UUID expenseId) { ... }
```

**`JwtAuthenticationConverter`** extrahiert `role` und `household_id` aus dem Token und macht sie als `GrantedAuthority` bzw. Custom-Attribute verfügbar.

---

## 8. Rollenmodell (Detail)

### 8.1 Berechtigungsmatrix

| Aktion | MEMBER | ADMIN | Author |
|---|---|---|---|
| Haushalt ansehen | ✅ | ✅ | — |
| Aufgaben anlegen | ✅ | ✅ | — |
| Eigene Aufgabe bearbeiten/löschen | ✅ | ✅ | ✅ |
| Fremde Aufgabe bearbeiten/löschen | ❌ | ✅ | ❌ |
| Ausgaben anlegen | ✅ | ✅ | — |
| Eigene Ausgabe bearbeiten/löschen | ✅ | ✅ | ✅ |
| Fremde Ausgabe bearbeiten/löschen | ❌ | ✅ | ❌ |
| Haushalt umbenennen | ❌ | ✅ | — |
| Mitglied einladen | ❌ | ✅ | — |
| Mitglied entfernen | ❌ | ✅ | — |
| Eigenes Profil bearbeiten | ✅ | ✅ | — |

### 8.2 Spring Security Implementierung

```java
// eu.wiegandt.librehousehold.auth — Security-Beans (modulübergreifend)

@Component("householdSecurity")
public class HouseholdSecurityEvaluator {

    private final HouseholdMembershipProvider membershipProvider; // Named Interface

    public boolean isMember(UUID householdId) {
        var userId = getCurrentUserId();
        return membershipProvider.isMember(householdId, userId);
    }

    public boolean isAdmin(UUID householdId) {
        var userId = getCurrentUserId();
        return membershipProvider.isAdmin(householdId, userId);
    }
}

@Component("resourceSecurity")
public class ResourceSecurityEvaluator {

    public boolean isAuthor(UUID resourceId, ResourceType type) {
        var userId = getCurrentUserId();
        // Delegiert an jeweiliges Modul per Named Interface
    }
}
```

**Modul-Kommunikation:** Die `SecurityEvaluator`-Beans rufen Named Interfaces auf (gemäß ADR-011). Kein direkter Datenbankzugriff aus dem `auth`-Modul auf fremde Tabellen.

### 8.3 Rollenermittlung beim Login

Der Haushalt-Admin ist der Ersteller des Haushalts (oder explizit per Transfer gesetzt). Rolle und `household_id` werden **beim Token-Ausstellungszeitpunkt aus der DB gelesen** und als Custom-Claims in das JWT geschrieben.

**Warum im JWT?** Die Rolle ändert sich praktisch nie im laufenden Betrieb. Beim Admin-Transfer — dem einzigen Szenario mit sofortiger Rollenwechslung — werden beide Nutzer aktiv abgemeldet: der `JdbcOAuth2AuthorizationService` revoked alle Tokens beider User, sodass das nächste JWT die korrekte Rolle enthält. Das 5–15-Minuten-Fenster entfällt damit als Problem.

**`JwtAuthenticationConverter`** extrahiert `role` und `household_id` aus dem JWT und macht sie im Spring `SecurityContext` verfügbar — ohne DB-Roundtrip pro Request.

---

## 9. Architektur (ausgearbeitet)

### 9.1 Gesamtübersicht

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         LibreHousehold (Docker Container)                │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                    Spring Boot 4 Modular Monolith                  │  │
│  │                                                                    │  │
│  │  ┌──────────────────────┐    ┌───────────────────────────────────┐ │  │
│  │  │   Spring Auth Server  │    │         REST API (Resource Server)│ │  │
│  │  │   (optional, config)  │    │                                   │ │  │
│  │  │                       │    │  /v1/household/*                  │ │  │
│  │  │  • formLogin (lokal)  │    │  /v1/tasks/*                      │ │  │
│  │  │  • oauth2Login        │    │  /v1/expenses/*                   │ │  │
│  │  │    (Google, Apple...) │    │                                   │ │  │
│  │  │  • JIT Provisioning   │    │  JWT-Validierung (stateless)      │ │  │
│  │  │  • Token Rotation     │    │  @PreAuthorize Annotations        │ │  │
│  │  └──────────────────────┘    └───────────────────────────────────┘ │  │
│  │           │                                  ▲                      │  │
│  │    Issues JWT                         Bearer Token                  │  │
│  │           │                                  │                      │  │
│  │  ┌────────┴──────────────────────────────────┤                      │  │
│  │  │              Module (ADR-011)              │                      │  │
│  │  │  household │ tasks │ expenses │ members   │                      │  │
│  │  └────────────────────────────────────────────┘                     │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
         ▲  Federation (OIDC)                    ▲  PKCE Flow
         │                                       │
┌────────┴─────────┐                  ┌──────────┴──────────┐
│  Externe Provider│                  │  SvelteKit Frontend  │
│  • Google        │                  │  • oidc-client-ts    │
│  • Apple         │                  │  • Token in Memory   │
│  • Zitadel etc.  │                  │  • Silent Refresh    │
└──────────────────┘                  └─────────────────────┘
```

### 9.2 Login-Flows im Detail

#### Flow 1: Lokaler Account (Username + Passwort)

```
SvelteKit             Spring Auth Server        PostgreSQL
    │                        │                      │
    │── POST /oauth2/token ──►│                      │
    │   (password grant /    │──── loadUser ────────►│
    │    authorization code) │◄─── UserDetails ──────│
    │                        │── verify Argon2id ───►│
    │◄── access_token ───────│                       │
    │    refresh_token       │                       │
    │                        │                       │
    │── GET /v1/tasks ───────────────────────────────►│ (Resource Server)
    │   Authorization: Bearer <token>                │
```

#### Flow 2: OIDC Social Login (z. B. Google) — PKCE

```
SvelteKit                Spring Auth Server          Google
    │                            │                      │
    │ code_verifier = random()   │                      │
    │ code_challenge = SHA256(v) │                      │
    │                            │                      │
    │── GET /oauth2/authorize ──►│                      │
    │   ?code_challenge=...      │── Redirect ─────────►│
    │                            │   (Spring federated) │
    │◄── Redirect from Google ───────────────────────── │
    │   ?code=AUTH_CODE          │                      │
    │                            │                      │
    │── POST /oauth2/token ─────►│                      │
    │   code + code_verifier     │── Verify challenge   │
    │                            │── JIT: create user   │
    │◄── access_token ───────────│   if first login     │
    │    refresh_token           │                      │
```

#### Flow 3: Token Refresh

```
SvelteKit             Spring Auth Server
    │                        │
    │── POST /oauth2/token ──►│
    │   grant_type=refresh   │
    │   refresh_token=<old>  │── Invalidate old RT
    │                        │── Issue new RT (Rotation)
    │◄── new access_token ───│
    │    new refresh_token   │
```

### 9.3 Token-Struktur (JWT Claims)

```json
{
  "iss": "https://meine-instanz.example.com",
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "iat": 1750000000,
  "exp": 1750000900,
  "email": "user@example.com",
  "name": "Max Mustermann",
  "provider": "google",
  "household_id": "7f3a1200-dead-beef-cafe-000000000000",
  "role": "ADMIN"
}
```

**Entschiedene Claims:**

| Claim | Wert | Begründung |
|---|---|---|
| `household_id` | UUID \| `null` | Ein Nutzer gehört genau einem Haushalt — spart DB-Lookup bei jedem Request |
| `role` | `"ADMIN"` \| `"MEMBER"` | Ändert sich selten; bei Admin-Transfer werden beide Sessions invalidiert (Refresh Tokens revoked) |
| `provider` | `"local"` \| `"google"` \| `"apple"` \| … | Für Debugging und Fehlerfall bei OIDC-Account-Verlust |
| `sub` | UUID (intern) | Interne User-ID, unabhängig vom OIDC-Provider |

**`household_id` = `null`** bis der Nutzer einem Haushalt beitritt oder einen anlegt (Onboarding-Flow).

**Admin-Transfer:** Admin-Rolle wird übertragen → beide betroffenen Nutzer werden abgemeldet (Refresh Tokens in der DB invalidiert) → beim nächsten Login enthält das neue JWT die aktualisierte Rolle.

**Was nicht im Token steht:** Passwort-Hash, Member-Liste, Haushalt-Details — nur bei Bedarf per API abrufbar.

### 9.4 Auth-Modul im Modular Monolith

```
backend/src/main/java/eu/wiegandt/librehousehold/
├── auth/                          ← Auth-Modul (package-privat intern)
│   ├── AuthorizationServerConfig.java   ← Spring Auth Server Konfiguration
│   ├── SecurityConfig.java              ← Resource Server + formLogin Config
│   ├── LocalUserDetailsService.java     ← Argon2id-basierte UserDetails
│   ├── FederatedIdentityHandler.java    ← JIT Account Provisioning
│   ├── HouseholdSecurityEvaluator.java  ← @PreAuthorize Bean
│   ├── ResourceSecurityEvaluator.java   ← Author-Check Bean
│   └── package-info.java
│
├── HouseholdMembershipProvider.java    ← Named Interface (public, ADR-011)
│                                         (im Root-Package des household-Moduls)
```

### 9.5 Selfhoster-Konfiguration

```yaml
# application.yaml — vollständiges Beispiel für verschiedene Szenarien

# --- Szenario 1: Nur lokale Accounts ---
spring:
  security:
    oauth2:
      authorizationserver:
        enabled: true
      client:
        registration: {}   # keine externen Provider

# --- Szenario 2: Nur Google + Apple ---
spring:
  security:
    oauth2:
      authorizationserver:
        enabled: true
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
          apple:
            client-id: ${APPLE_CLIENT_ID}
            client-secret: ${APPLE_CLIENT_SECRET}
            authorization-grant-type: authorization_code

# --- Szenario 3: Eigener Zitadel/Keycloak als Broker ---
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${EXTERNAL_OIDC_ISSUER}
      authorizationserver:
        enabled: false   # Spring AS deaktiviert, externer IdP übernimmt alles
```

### 9.6 RSA-Schlüsselverwaltung

Spring Authorization Server benötigt RSA-Schlüsselpaare für JWT-Signierung. Optionen:

| Option | Aufwand | Empfehlung |
|---|---|---|
| Auto-generiert beim Start | Minimal | ❌ Schlüssel wechseln bei Neustart (alle JWTs ungültig) |
| Datei-basiert (PEM im Volume) | Niedrig | ✅ Für MVP: PEM-Datei im Docker-Volume, per Env-Var Pfad konfigurieren |
| Externe Key-Store (PKCS12/JKS) | Mittel | ✅ Produktionsempfehlung |
| HashiCorp Vault / AWS KMS | Hoch | Für Unternehmenssetups |

**MVP-Empfehlung:** PEM-Datei, generiert beim ersten Start und persistiert im Volume.

```yaml
# Env-Variable für Selfhoster:
LIBREHOUSEHOLD_AUTH_RSA_KEY_PATH=/data/auth/private.pem
```

### 9.7 E-Mail-Kollision: was Spring mitbringt

**Szenario:** Jemand hat einen lokalen Account mit `max@example.com` und versucht sich mit Google anzumelden — Google liefert dieselbe E-Mail-Adresse.

**Spring Authorization Server bietet dafür nichts out-of-the-box.** Die gesamte Logik liegt im eigenen `Consumer<OAuth2User>`, der dem `FederatedIdentityAuthenticationSuccessHandler` übergeben wird. Spring ruft den Consumer nach erfolgter Provider-Authentifizierung auf — was dann passiert, ist vollständig selbst implementiert.

```java
Consumer<OAuth2User> oauth2UserHandler = (oauth2User) -> {
    String email = oauth2User.getAttribute("email");
    String providerId = oauth2User.getName(); // "sub" beim IdP

    // Lokaler Account mit dieser E-Mail vorhanden → kein Auto-Linking
    if (userRepository.existsByEmailAndProvider(email, "local")) {
        throw new OAuth2AuthenticationException(
            new OAuth2Error(
                "email_already_registered",
                "Ein lokaler Account mit dieser E-Mail existiert bereits.", null)
        );
    }

    // Federated Account neu anlegen (JIT Provisioning)
    if (!userRepository.existsByProviderAndProviderId("google", providerId)) {
        userRepository.save(new User(email, "google", providerId));
    }
};
```

Wirft man die `OAuth2AuthenticationException`, bricht Spring den Flow ab und leitet zur Login-Seite mit `?error=email_already_registered` um. Dort kann eine verständliche Meldung angezeigt werden:

> *"Für diese E-Mail-Adresse existiert bereits ein lokaler Account. Bitte melde dich mit deinem Passwort an."*

**Was Spring liefert / was selbst gebaut werden muss:**

| Was | Spring | Selbst |
|---|---|---|
| Einstiegspunkt nach Provider-Auth | ✅ `FederatedIdentityAuthenticationSuccessHandler` | |
| E-Mail-Kollisions-Erkennung | ❌ | ✅ im `Consumer<OAuth2User>` |
| Fehlerweiterleitung zur Login-Seite | ✅ via `OAuth2AuthenticationException` | Error-Code selbst definieren |
| Fehlermeldung auf Login-Seite | ❌ | ✅ eigenes Login-Template |

---

### 9.8 Frontend-Integration: SvelteKit + Spring Authorization Server

#### Die Kernrealität: Die Login-Seite gehört Spring, nicht SvelteKit

Das ist die wichtigste Erkenntnis für die Frontend-Umsetzung. Spring Authorization Server hat **kein einbettbares Login-Formular** — er rendert eine eigenständige HTML-Seite. Eine SPA kann Credentials nicht per `fetch()` an Spring schicken, weil Spring Security einen Form-POST (`application/x-www-form-urlencoded`) erwartet und mit `302 Redirect` antwortet — das bricht `fetch()` kaputt.

Das klingt nach einer Einschränkung, ist aber eigentlich der **Standard wie OIDC funktioniert** — auch Google und GitHub zeigen beim Login ihre eigene Seite. Der Nutzer verlässt kurz die App, authentifiziert sich, und kehrt mit einem Token zurück.

#### Gesamtfluss für LibreHousehold

```
┌─────────────────────────────────────────────────────────────────┐
│  SvelteKit (Browser)                                            │
│                                                                 │
│  /login-Seite (Svelte):                                        │
│  ┌─────────────────────────────────────┐                       │
│  │  [ Mit Google anmelden ]            │  ← nur Buttons/Links  │
│  │  [ Mit Apple anmelden  ]            │    kein echtes Formular│
│  │  [ Mit E-Mail anmelden ]            │                       │
│  └─────────────────────────────────────┘                       │
│         │ oidc-client-ts.signinRedirect()                      │
│         │ Browser-Redirect (kein fetch)                         │
└─────────┼───────────────────────────────────────────────────────┘
          │
          ▼ GET /oauth2/authorize?code_challenge=...
┌─────────────────────────────────────────────────────────────────┐
│  Spring Authorization Server (Thymeleaf, styled)                │
│                                                                 │
│  ┌─────────────────────────────────────┐                       │
│  │  E-Mail: [_______________]          │  ← eigenes Template,  │
│  │  Passwort: [____________]           │    DaisyUI-CSS als    │
│  │  [ Anmelden ]                       │    static asset       │
│  │                                     │                       │
│  │  ─── oder ───                       │                       │
│  │  [ Mit Google anmelden ]            │                       │
│  └─────────────────────────────────────┘                       │
└─────────┬───────────────────────────────────────────────────────┘
          │ Login erfolgreich → 302 Redirect
          │
          ▼ GET /callback?code=AUTH_CODE
┌─────────────────────────────────────────────────────────────────┐
│  SvelteKit /callback Route:                                     │
│  oidc-client-ts.signinRedirectCallback()                        │
│  → tauscht code + code_verifier gegen access_token + refresh_token│
│  → speichert Tokens im Memory                                   │
│  → Redirect zur App (/app/tasks)                                │
└─────────────────────────────────────────────────────────────────┘
```

#### Die SvelteKit Login-Seite: nur ein Sprungbrett

Die `/login`-Seite in SvelteKit rendert **keine Formular-Felder** — nur Buttons, die den OIDC-Flow starten:

```typescript
// frontend/src/lib/auth.ts
import { UserManager } from 'oidc-client-ts';

export const userManager = new UserManager({
  authority: '/auth',               // Spring AS issuer (gleiche Domain, anderer Pfad)
  client_id: 'librehousehold-spa',
  redirect_uri: `${window.location.origin}/callback`,
  scope: 'openid profile email',
  // PKCE ist Standard in oidc-client-ts (S256)
});
```

```svelte
<!-- /login +page.svelte — nur Buttons, kein Formular -->
<button onclick={() => userManager.signinRedirect()}>
  Mit E-Mail anmelden
</button>
<button onclick={() => userManager.signinRedirect({ extraQueryParams: { provider: 'google' } })}>
  Mit Google anmelden
</button>
```

#### Welche Provider sind konfiguriert? — zwei Lösungen

**Lösung A (einfacher):** SvelteKit leitet immer zur Spring-Login-Seite weiter — Spring zeigt dort nur die konfigurierten Buttons. SvelteKit zeigt selbst gar keine Provider-Buttons (nur einen "Anmelden"-Button).

**Lösung B (bessere UX):** Eigener Endpoint `/api/auth/providers` im Backend, den SvelteKit beim Start aufruft:

```java
// GET /api/auth/providers — nicht authentifiziert
@GetMapping("/api/auth/providers")
public AuthProviders getProviders() {
    return new AuthProviders(
        localAuthEnabled,           // aus application.yaml
        googleRegistrationExists,   // ob Google konfiguriert ist
        appleRegistrationExists
    );
}
```

```typescript
// SvelteKit zeigt nur konfigurierte Buttons
const providers = await fetch('/api/auth/providers').then(r => r.json());
// { local: true, google: true, apple: false }
```

**Empfehlung: Lösung B** — bessere UX, SvelteKit kann die Login-Seite ansprechend gestalten, bevor der Redirect passiert. Die eigentliche Authentifizierung (Formular, Social-Button-Logik) liegt trotzdem auf der Spring-Seite.

#### Die Spring-Login-Seite stylen

Die Spring-Login-Seite ist ein eigenes Thymeleaf-Template (kein SvelteKit). Sie wird **einmalig gebaut** und muss zum App-Design passen:

```java
// Spring AS Konfiguration:
http.formLogin(form -> form.loginPage("/auth/login"));

// Eigener Controller:
@Controller
@RequestMapping("/auth")
class LoginController {
    @GetMapping("/login")
    String loginPage(Model model, @RequestParam(required = false) String error) {
        // Konfigurierte Social-Provider dynamisch übergeben:
        model.addAttribute("googleEnabled", googleEnabled);
        model.addAttribute("appleEnabled", appleEnabled);
        return "login"; // → resources/templates/login.html
    }
}
```

Das Template bekommt statische Assets (CSS) aus dem Spring-Backend serviert — hier kann man DaisyUI/Tailwind via CDN oder Build-Artefakt einbinden. Die Seite sieht dann exakt wie die App aus, ist aber kein Svelte-Komponent.

#### `/callback` Route in SvelteKit

```typescript
// frontend/src/routes/callback/+page.ts
import { userManager } from '$lib/auth';
import { redirect } from '@sveltejs/kit';

export async function load() {
  const user = await userManager.signinRedirectCallback();
  // Tokens jetzt im Memory via oidc-client-ts
  throw redirect(302, '/app/tasks');
}
```

#### Zusammenfassung: Was SvelteKit macht, was Spring macht

| Aufgabe | SvelteKit | Spring AS |
|---|---|---|
| "Anmelden"-Seite mit Buttons | ✅ Svelte-Komponente | |
| Welche Provider sind aktiv? | ✅ via `/api/auth/providers` | |
| Passwort-Formular | | ✅ Thymeleaf-Template |
| Social-Login-Buttons (Google etc.) | | ✅ Thymeleaf-Template |
| PKCE-Code-Challenge erzeugen | ✅ `oidc-client-ts` | |
| Authorization-Code eintauschen | ✅ `oidc-client-ts` | |
| Token im Memory halten | ✅ `oidc-client-ts` | |
| Token Refresh | ✅ `oidc-client-ts` | |
| JWT ausstellen | | ✅ Spring AS |
| JIT Account Provisioning | | ✅ Spring AS |

---

## 10. Nächste Schritte

- [ ] ADR erstellen: "Embedded Spring Authorization Server als Auth-Strategie"
- [ ] ADR erstellen: "Rollenmodell mit Spring Method Security"
- [ ] Prototyp: Spring Authorization Server + Google Federation
- [ ] Frontend: OIDC-Library evaluieren (`oidc-client-ts` oder `@auth/sveltekit`)
- [ ] Account-Modell im OpenAPI-Schema (`/v1/auth/*` Endpunkte) definieren
- [ ] RSA Key Management-Strategie für Docker-Deploy festlegen

---

## 8. Quellenverzeichnis & weiterführende Links

- [Spring Authorization Server Docs](https://docs.spring.io/spring-authorization-server/reference/)
- [Spring Security OAuth2](https://docs.spring.io/spring-security/reference/servlet/oauth2/)
- [Zitadel](https://zitadel.com) — Schweizer Open Source IdP
- [Authentik](https://goauthentik.io) — Deutsches Open Source IAM
- [Authelia](https://www.authelia.com)
- [Kanidm](https://kanidm.com)
- [Codeberg OIDC](https://codeberg.org) — Europäische GitHub-Alternative mit OIDC
- [OpenID Connect Spec](https://openid.net/connect/)
- ADR-009: Argon2id für Password Hashing

---

*Dieses Dokument wird während des Brainstormings kontinuierlich aktualisiert. Es ist kein ADR — Entscheidungen werden separat als ADR dokumentiert.*
