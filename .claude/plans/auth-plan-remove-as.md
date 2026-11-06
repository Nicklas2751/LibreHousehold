# Implementierungsplan: Eingebetteten Authorization Server ausbauen (Branch `feat/auth_new`)

> Grundlage: Branch `feat/auth_new` (38 Commits über `main`). Der Ist-Zustand ist mit `Datei:Zeile` belegt. Stand: nach
> fünf Review-Runden und dem Owner-Feedback vom 2026-11-03.
> Abkürzungen: `B` = `../../backend/src/main/java/eu/wiegandt/librehousehold`, `T` =
> `backend/src/test/java/eu/wiegandt/librehousehold`, `F` = `frontend/src`.

---

## 1. Ziel, Nicht-Ziele, Phasen

**Ziel**

- Der eingebettete Spring Authorization Server (AS) und der Loopback-Client `spa-backend-client` werden vollständig
  entfernt.
- Danach gibt es **eine** `SecurityFilterChain`:
    - `formLogin()` unter `POST /v1/login`. Antwort `204` oder ein RFC-9457-ProblemDetail über den bestehenden
      `ErrorResponseExceptionHandler`.
    - `oauth2Login()` nur für externe Provider.
    - `logout()` unter `POST /v1/logout`, Antwort `204`.
- Die HTTP-Session ist das einzige Credential. Das BFF-Muster aus ADR-014 und CSRF im SPA-Modus bleiben.
- Es gibt einen einzigen Principal, `AccountPrincipal`, identifiziert über die `memberId`.
- Social Login wird über Boots `spring.security.oauth2.client.*` konfiguriert; dazu kommt ein minimaler Fail-fast-Check.
- Das Frontend arbeitet ohne Loopback.

**Nicht-Ziele**

- Keine neuen Dependencies. Der AS-Starter fällt weg; der Client-Starter wird auf
  `spring-boot-starter-security-oauth2-client` umgestellt (F9, freigegeben).
- Es gibt noch kein Release. Daher:
    - Keine neue Flyway-Migration; Schemaänderungen gehen direkt in V1.
    - Keine Abwärtskompatibilität für API, Konfigurationsschlüssel oder gespeicherte Daten, keine Migrationspfade, keine
      Release-Notes.
- `../../api/mokapi.js` wird nicht angefasst.
- Die heutige Lockout-Policy bleibt, wie sie ist (E8).
- Weitere Abgrenzungen stehen in Abschnitt 9.

**Phasen**

Alles läuft auf `feat/auth_new`; es gibt keine eigenen PRs. Ablauf je Phase:

1. Alle Schritte der Phase umsetzen, ohne zu committen. `./mvnw clean verify` und
   `npm run test && npm run check && npm run lint` sind grün.
2. Dem Owner den Stand vorstellen: Diff, Testergebnis, Abweichungen vom Plan.
3. Owner-Feedback einarbeiten, bis der Owner freigibt.
4. Erst dann committen, mit den je Schritt geplanten Commit-Messages. Die nächste Phase beginnt erst danach.

Doku- und ASVS-Anteile gehören jeweils zu der Phase, deren Verhalten sie beschreiben. Die Abnahme steht je Phase in
Abschnitt 7.

| Phase        | Inhalt                                                                                                                                                                                                                                               | Abhängigkeit                                     |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| **A**        | 429 als ProblemDetail mit `Retry-After`, IP-Limit für den Login, Passwort-Reset entsperrt sofort, RATE1 vollständig                                                                                                                                  | keine                                            |
| **B** (Kern) | ADR-013, OpenAPI, AS-Ausbau, Sign-in nach Commit, Boot-Social-Config, Frontend-Pflichtanteile (JSON-Login, Guard → `/login`, Fehlerklassifikation inkl. 429 und Netzwerk, `replaceState`, Logout per Reload), Doku-Zwischenstand, Starter-Umstellung | **A ist Pflicht** (`Retry-After`, Problem-Typen) |
| **F**        | Sessions entfernter Mitglieder und gelöschter Haushalte beenden                                                                                                                                                                                      | B; direkt nach B                                 |
| **C**        | Härtung gegen Enumeration (E10)                                                                                                                                                                                                                      | B                                                |
| **D**        | Issuer-/URI-Schlüssel für föderierte Identitäten (E11)                                                                                                                                                                                               | B                                                |
| **E**        | Frontend-UX: `returnTo`, `session-expired`, Fokus, Bootstrap-Fehler, Wizard-Status, Textvarianten                                                                                                                                                    | B                                                |

Reihenfolge: A → B → F → C → D → E. Der Stand B ohne F ist nicht schlechter als heute, weil der Guard entfernten
Mitgliedern per DB-Abfrage den Zugriff verweigert. F kommt trotzdem direkt nach B: V7.4.2 ist eine L1-Anforderung.

---

## 2. Zielarchitektur (Endstand)

```
Browser (SPA, same origin) — JSESSIONID (HttpOnly,Secure,Lax), XSRF-TOKEN
  ▼ EINE SecurityFilterChain
  Cors → Csrf(spa, Repo-Bean, CSRF-SessionAuthenticationStrategy) → CsrfCookieFilter
  → RateLimitingFilter(login: email|IP und IP; sensible Endpunkte: IP)
  → ConcurrentSessionFilter(sessionConcurrency) → UsernamePasswordAuthenticationFilter [nur wenn local-login an]
  → OAuth2-Redirect-/Login-Filter [nur wenn Registrierungen da] → LogoutFilter(/v1/logout)
  → AuthorizationFilter(anyRequest: AccountPrincipalAuthorizationManager) → @PreAuthorize(HouseholdAccessGuard)
```

**A) Lokaler Login**

```
SPA: (kein XSRF-Cookie? → GET /api/auth/discover) → POST /api/login  form: email,password + X-XSRF-TOKEN
  Limit überschritten        → 429 too-many-requests + Retry-After
  Dao(pre: –, Passwort, post: isEnabled)           [Check-Reihenfolge ab Phase C]
    ok                       → genau ein changeSessionId + Registry + CSRF neu (materialisiert) → 204
    falsch/unbekannt/ohne PW/unverifiziert+falsch → 401 invalid-credentials (identischer Body ab Phase C)
    unverifiziert + richtig  → 403 email-not-verified
    gesperrt                 → 403 account-locked {lockedUntil} + Retry-After
  local-login aus: kein formLogin → Delegate → 403 local-login-disabled
SPA: GET /api/me → goto(safeReturnTo(returnTo), {replaceState:true})   [returnTo ab Phase E]
```

**B) Social Login**

```
POST /api/auth/social-context → GET /oauth2/authorization/{id} (state, nonce, PKCE) → Provider
→ GET /login/oauth2/code/{id}: oauth2Login-Filter (changeSessionId, Registry, CSRF neu)
→ FederatedIdentityAuthenticationSuccessHandler (Authorized Client sofort entfernen):
   LOGIN : AccountSignIn.signInByExternalIdentity(providerKey, sub)
             (SecurityContext + sessionRegistry.registerNewSession(id, AccountPrincipal); kein zweiter ID-Wechsel)
           present → 302 /app/dashboard | empty → Session invalidieren → 302 /login?error&reason=social-login-no-account
   SETUP/INVITE: Pending-Registrierung → 302 /setup?social | /invite/{t}?social
   Exception: Session invalidieren → 302 /login?error&reason=social-login-failed
   providerKey: Phase B = registrationId; ab Phase D = iss (OIDC) | User-Info-URI ohne Query/Fragment
```

**C) Setup/Join**

```
POST setup/join → Service (@Transactional) → nach Commit im Delegate:
AccountSessionSignIn.signIn(memberId)  (@Primary-Composite: ChangeSessionId, Registry, CSRF; isEnabled bewusst nicht geprüft)
→ SPA: bootstrapSession() → goto('/app/dashboard', {replaceState:true})
```

**D) Mitglied entfernt / Haushalt gelöscht (Phase F)**

```
leaveHousehold/removeMember/deleteHousehold (auch via UnverifiedAccountExpiryJob)
→ memberSessions.expireAll(...) direkt vor publishEvent (im Tx) → nächster Request mit alter Session: 401
```

**E) Logout**

```
POST /api/logout → 204 (Session invalidiert, Cookie gelöscht, neues XSRF-TOKEN) → window.location.replace('/')
```

---

## 3. Entscheidungen

**E1 – Kein AS, eine Chain (B).**

- Es gibt keinen Token-Konsumenten. Die Autorisierung läuft über `HouseholdAccessGuard.java:37-50` gegen die Session.
- Entfallen: `SecurityConfig.java:82-85`, `:128-132`, `:210-228`, `RegisteredClientSeeder`, `spa-backend-client`.
- `defaultSecurityFilterChain` delegiert an `configureFormLogin`, `configureOAuth2Login`, `configureSessionManagement`,
  `configureLogout` und `configureExceptionHandling`.
- Sprechende Lambda-Parameter, Konstante `UNLIMITED_SESSIONS = -1`.

**E2 – Login und Logout unter `/v1` (B).**

- Hintergrund: `/logout` steht in der OpenAPI unter `/v1` (`api/openapi.yml:1623-1640`), das Backend lauscht aber auf
  `/logout` (`SecurityConfig.java:325`; vgl. `F/lib/stores/sessionLogout.ts:9-14`).
- `formLogin()` wird konfiguriert mit:
    - `.loginPage("/login")`
    - `.loginProcessingUrl(basePath + "/login")`
    - `.usernameParameter("email")`, als Konstante, die auch der Rate-Limit-Key nutzt.
- `{basePath}/login` steht in `permitAll` (siehe E9).
- `"/login"` fällt aus der Liste (`SecurityConfig.java:270`).

**E3 – Fehlerantworten (A, B).**

- Failure-Handler und `RateLimitingFilter` rufen
  `@Qualifier("handlerExceptionResolver") HandlerExceptionResolver.resolveException(request, response, null, problem)`
  auf.
- Problem-Typen sind `ErrorResponseException`-Subklassen in `config` mit dem Suffix `Problem`.
- `detail` wird nie aus `getMessage()` übernommen.
- `EmailNotVerifiedException` und `LocalLoginDisabledException` ziehen von `household.exception` in den `household`
  -Root. Grund: Ein Zugriff von außen würde `ApplicationModules.verify()` brechen. Präzedenzfall:
  `AccountLockedException.java:13-14`.

| Ursache                                                       | Status | Typ                                                           | Extra                        |
|---------------------------------------------------------------|--------|---------------------------------------------------------------|------------------------------|
| `BadCredentialsException`, sonstige `AuthenticationException` | 401    | `/problems/invalid-credentials` (`InvalidCredentialsProblem`) | –                            |
| `DisabledException`                                           | 403    | `/problems/email-not-verified`                                | –                            |
| `AccountLockedException` direkt/als Cause                     | 403    | `/problems/account-locked` (`AccountLockedProblem`)           | `lockedUntil`, `Retry-After` |
| `(Internal)AuthenticationServiceException` ohne Lock-Cause    | 500    | ohne Typ/Detail                                               | –                            |
| Rate-Limit                                                    | 429    | `/problems/too-many-requests` (`TooManyRequestsProblem`, A)   | `Retry-After`                |
| Lokaler Login aus                                             | 403    | `/problems/local-login-disabled`                              | –                            |

- Kein 423, wegen WebDAV-Semantik.
- `ProblemDetailLoginFailureHandler` ersetzt `UnverifiedAccountLoginFailureHandler.java:45-60`.
- Behebt den Bug, dass 429 als Erfolg gewertet wurde (`F/routes/login/+page.svelte:53-72`).

**E4 – Ein Principal (B).**

- `AccountPrincipal` wird von einem Record (`AccountPrincipal.java:15`) zu einer Klasse, die `UserDetails` und
  `CredentialsContainer` implementiert.
- Felder:
    - `memberId`
    - `householdId` (unveränderlich nach ADR-012)
    - `passwordHash` (wird erased)
    - `emailVerified` (nur als Snapshot für `isEnabled()`, ohne Getter)
- `getUsername()` liefert die memberId als String. Der Login-Name bleibt die E-Mail; das steht im Javadoc.
- `equals`/`hashCode` vergleichen nur die `memberId`. `toString()` enthält keinen Hash.
- `identifiedBy(UUID)` liefert einen Principal für die Registry-Suche.
- `isAdmin` entfällt.
- Konstruktion ausschließlich in `AccountUserDetailsService`:
    - `loadUserByUsername(email)`
    - `loadUserByMemberId(UUID)`, bewusst ohne Lock- und isEnabled-Prüfung.
- Entfallen: `AccountOidcPrincipal`, `AccountOidcUserService`, `SpaClientAware*` und alle `FactorGrantedAuthority`
  -Injektionen (`AccountOidcPrincipal.java:50-55`, `AccountSessionAuthenticator.java:88-90,100-101`,
  `FederatedIdentityAuthenticationSuccessHandler.java:148-151`).
- Guard und `/me` casten direkt. E7 sorgt dafür, dass kein fremder Principal dort ankommt.

**E5 – Sign-in modulintern (B).**

- Das Named Interface `household.AccountSignIn` hat nur
  `Optional<UUID> signInByExternalIdentity(String providerId, String externalSubject)`.
    - Das ist eine dokumentierte CQS-Ausnahme: Ein credential-loses `signIn(memberId)` darf nicht modulübergreifend
      aufrufbar sein.
- `signIn(UUID memberId)` liegt in der internen `household.service.AccountSessionSignIn` (umbenannt aus
  `AccountSessionAuthenticator`). Aufrufer sind nur `HouseholdApiDelegateImpl` und `MembersApiDelegateImpl`.
- `MemberQuery.findEmailByExternalIdentity` entfällt (`MemberQuery.java:71-78`, Implementierung
  `MemberManagementService.java:249-254`). Ebenso Passwort-Neuprüfung, `@Lazy`-Zyklus und `RequestCache`
  (`AccountSessionAuthenticator.java:77-91,117`).
- **Sign-in erst nach dem Commit:**
    - `HouseholdApiDelegateImpl.setupHousehold` (`:28-30`) ruft `signIn(setup.getMember().getId())` auf.
    - `MembersApiDelegateImpl.joinHousehold` (`:59-60`) ruft `signIn(member.getId())` auf.
    - Wirft der Service, findet kein Sign-in statt.

**E6 – Session-Fixation, Registry, CSRF (B).**

- Heute registriert nur der AS Sessions (`SecurityConfig.java:107-113`). Ohne ihn läuft RESET1 ins Leere
  (`PasswordResetService.java:76`).
- `config/SessionManagementConfig` enthält:
    - `SessionRegistry` und `HttpSessionEventPublisher` (verschoben).
    - `CsrfTokenRepository` = `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
    - `@Qualifier("csrfSessionAuthenticationStrategy")`: Composite aus `CsrfAuthenticationStrategy(repo)` und
      `CsrfTokenMaterializingStrategy`. Wird nur in `config` per Qualifier verwendet.
    - `@Primary SessionAuthenticationStrategy`: programmatisches Composite aus `ChangeSessionId`,
      `RegisterSession(sessionRegistry)` und der CSRF-Strategie. Das ist das einzige Bean, das `household` per Typ
      bekommt.
- Chain:
  -
  `csrf(csrf -> csrf.spa().csrfTokenRepository(repo).sessionAuthenticationStrategy(csrfSessionAuthenticationStrategy))`
  -
  `sessionManagement(session -> session.sessionConcurrency(...maximumSessions(UNLIMITED_SESSIONS).sessionRegistry(...)))`
    - Der manuelle `ConcurrentSessionFilter` (`SecurityConfig.java:305-311`) entfällt.
- **Kein `sessionManagement().sessionAuthenticationStrategy(composite)`.** Geprüft am `SessionManagementConfigurer`: Er
  stellt die übergebene Strategie den Defaults nur voran und hängt die CSRF-Strategie hinten an. Das Ergebnis wäre ein
  doppelter ID-Wechsel, eine doppelte Registrierung und ein wieder gelöschtes Token.
- **Materialisierung an genau einer Stelle:** `CsrfTokenMaterializingStrategy` ruft `getToken()` auf dem `CsrfToken` aus
  dem Request-Attribut auf. Weil `CookieCsrfTokenRepository` nach `saveToken(null)` das Token als entfernt markiert,
  wird ein neues erzeugt und gesetzt.
- Logout: `repo.loadDeferredToken(request, response).get()` im Success-Handler.
- **Pro Login-Pfad genau ein Session-ID-Wechsel:**
    - formLogin und oauth2Login über die Filter-Strategien.
    - Setup/Join über das `@Primary`-Composite.
    - `signInByExternalIdentity` läuft nach dem `oauth2Login`-Filter. Es setzt deshalb nur den `SecurityContext` mit
      `AccountPrincipal`, speichert ihn und ruft direkt `sessionRegistry.registerNewSession(session.getId(), principal)`
      auf. Das ersetzt den `OAuth2User`-Eintrag; es braucht keine eigene Strategie-Instanz.
- Behebt nebenbei: Der programmatische Login hat bisher keinen ID-Wechsel gemacht
  (`AccountSessionAuthenticator.java:104-118`).

**E7 – Autorisierung nur für `AccountPrincipal` (B).**

- Neue Klasse `config/AccountPrincipalAuthorizationManager`, eingebunden über `anyRequest().access(...)`.
- Ein liegengebliebenes `OAuth2AuthenticationToken` oder ein anonymer Zugriff wird abgewiesen.
- Der Social-Success-Handler invalidiert bei jeder Exception die Session.

**E8 – Rate-Limit und Lockout (A, B).**

- **(A)** `RateLimiter.isRateLimited` wird zu `Optional<Duration> acquire(String key)`. Die Wartezeit kommt aus
  `tryConsumeAndReturnRemaining(1).getNanosToWaitForRefill()` und wird als `Retry-After` gesetzt. Heute liefert
  `RateLimitingFilter.java:31-34` ein 429 ohne Body.
- **(A)** Zusätzliche Login-Regel mit reinem IP-Key (Standard 20/min). Der Key wird auf 254 Zeichen gekürzt
  (`RateLimitingConfig.java:50-52`).
- **(A) Lockout-Policy bleibt unverändert und wird als gewollt dokumentiert** (`AccountRepository.java:35-37`,
  `AccountLockoutListener.java:43-52`).
    - Nach Ablauf einer Sperre sperrt jeder weitere Fehlversuch erneut.
    - Erst ein erfolgreicher Login oder ein Passwort-Reset setzt den Zähler zurück.
    - Das ist die strengere und einfachere Policy.
- **(A) Gegen Lockout-DoS:** `confirmPasswordReset` ruft `accountRepository.resetFailedLoginAttempts(memberId)` auf
  (vorhanden, `AccountRepository.java:39-41`). Ein Reset entsperrt sofort, und Phase E zeigt diesen Weg in der
  Sperrmeldung.
- **Restrisiko:** Ein Angreifer mit bekannter E-Mail kann ein Konto dauerhaft gesperrt halten; der Nutzer entsperrt dann
  per Reset. Dokumentiert in RATE1 und Kap. 11.
- **(B)** Login-Matcher wird `{basePath}/login` (`RateLimitingConfig.java:32`), der Key-Parameter `email`.
- **(B)** `AccountLockoutListener.onAuthenticationSuccess` (`:42-45`) wertet
  `instanceof AccountPrincipal accountPrincipal` aus.

**E9 – Lokaler Login aus (B).**

- `formLogin()` wird nur registriert, wenn `localLoginProperties.enabled()`.
- Andernfalls wirft `SessionApiDelegateImpl.login` eine `LocalLoginDisabledException`.
- `LocalLoginDisabledFilter` entfällt.

**E10 – Härtung gegen Enumeration (C).**

- `config/AuthenticationProviderConfig` liefert einen `DaoAuthenticationProvider(userDetailsService)`:
    - `setPasswordEncoder(passwordEncoder)` (Argon2)
    - Pre-Checks leer
    - Post-Checks = Default + `isEnabled`
- `loadUserByUsername` wirft für Accounts ohne Passwort `UsernameNotFoundException`, und zwar vor der Lock-Prüfung. So
  läuft der Dummy-Hash.
- Der Lock bleibt ein Pre-Check, damit während einer Sperre kein Passwort geprüft wird.
- Der Listener zählt nur Accounts mit Passwort.
- Bleibendes Signal: Ein gesperrter Account ist ohne Passwort erkennbar, und `/members/availability` verrät die
  Existenz.

**E11 – Stabiler Schlüssel für föderierte Identitäten (D).**

- `account_identity.provider_id` speichert bei OIDC den `iss`, sonst die vollständige User-Info-URI ohne Query und
  Fragment. Nicht mehr die `registrationId`.
- Umsetzung in `config/FederatedProviderKey`; `ExternalIdentity.registrationId` wird zu `providerId`.
- Gilt für Lookup und Verknüpfung (`AccountService.java:71-77`).

**E12 – Social-Login-Konfiguration (B).**

- Entfallen: `SocialLoginProperties`, `SocialLoginClientRegistrationFactory`, `SocialLoginConfigurationValidator`,
  `OidcIssuerDiscovery`, `CommonProvider` und `SecurityConfig.java:159-187`.
- `oauth2Login()` wird nur aktiviert, wenn `ObjectProvider<ClientRegistrationRepository>` vorhanden ist.
- `config/SocialLoginRegistrationCheck` prüft beim Start nur die effektiven `ClientRegistration`s:
    - (a) Issuer gesetzt ⇒ Scope `openid`
    - (b) `openid` ⇒ Issuer gesetzt
    - (c) ohne `email` bzw. bei Host `api.github.com` ohne `user:email` ⇒ Start schlägt fehl
- Bekannte Defaults: Google liefert per `CommonOAuth2Provider` `openid,profile,email`; GitHub nur `read:user`;
  Issuer-Discovery setzt keine Scopes.
- PKCE ist Pflicht (Spring Security 7: `requireProofKey=true`).
- GitHub-Erkennung über `URI.create(userInfoUri).getHost().equals("api.github.com")`
  (`GitHubEmailAwareOAuth2UserService.java:42`).
- `OAuth2AuthorizedClientRepository` wird als HttpSession-Bean registriert; der Handler entfernt den Authorized Client
  in jedem Pfad.
- Redirect-URI: in Produktion absolut, in Dev und ITs `{baseUrl}`.
- `user-name-attribute` muss unveränderlich sein (`sub`, bei GitHub `id`).

**E13 – Social-Redirects (B).**

- Top-Level-Redirects mit Query-Grund.
- Erfolg führt auf `/app/dashboard` statt zurück in den Loopback
  (`FederatedIdentityAuthenticationSuccessHandler.java:62,116`).
- `RequestCache`/`NonSavingRequestCache` entfallen (`:96`); stattdessen `NullRequestCache`.
- Der `$effect` in `F/lib/WelcomeScreen.svelte:11-16` (bei `authenticated` → Dashboard) bleibt. Mit dem Logout per
  Reload (E14) entsteht kein Race.

**E14 – Logout (B).**

- Backend:
    - Antwort `204`, `deleteCookies(${server.servlet.session.cookie.name:JSESSIONID})`, neues CSRF-Token per
      Materialisierung.
    - `RevokeAuthorizedClientLogoutHandler` entfällt; er hat nie widerrufen
      (`RevokeAuthorizedClientLogoutHandler.java:20-22`).
    - Entry-Point unverändert (`SecurityConfig.java:331-337`).
- Frontend: `logout()` ruft nach dem API-Aufruf `window.location.replace('/')` auf.
    - Damit entfällt der Navigations-Race mit Guard und WelcomeScreen-Effect.
    - In-Memory-Stores werden geleert (relevant für geteilte Geräte).
    - Der Bootstrap nach dem Reload holt ohnehin ein frisches `XSRF-TOKEN`. Frontendseitige CSRF-Maßnahmen nach dem
      Logout entfallen deshalb; die Backend-Materialisierung bleibt für API-Clients ohne Reload.

**E15 – Integrationstests auf `RANDOM_PORT` (B).**

- Gründe: echte `Set-Cookie`-Attribute, echter Session-ID-Wechsel, echte Dex-Redirects.
- Dex-ITs registrieren den Client pro Test mit `@LocalServerPort` (wie heute in
  `SocialLoginFilterChainIT.java:122-123`).

**E16 – Sessions entfernter Mitglieder beenden (B: Baustein, F: Wirkung).**

- **(B)** Interne Klasse `household.service.MemberSessions` mit `expireAll(UUID)` und `expireAll(Collection<UUID>)`. Sie
  ist die einzige Stelle mit `getAllSessions(AccountPrincipal.identifiedBy(id), false)` → `expireNow`.
  `PasswordResetService` nutzt sie.
- **(F)** `leaveHousehold` und `removeMember` rufen `memberSessions.expireAll(member.id())` im gemeinsamen
  `deleteMemberAndPublishEvent` auf, unmittelbar vor `publishEvent` (`MemberManagementService.java:202-207`).
  `deleteHousehold` ruft nach der `deletedRows`-Prüfung und vor `publishEvent` `memberSessions.expireAll(memberIds)` auf
  (`HouseholdManagementService.java:49-61`).
    - Der `UnverifiedAccountExpiryJob` ist abgedeckt, weil er dieselben Services nutzt
      (`UnverifiedAccountExpiryJob.java:110-117`).
    - Die Services kennen nur `MemberSessions`, nicht die `SessionRegistry`.
- **Warum kein Event-Listener:** Auch ein synchroner `@TransactionalEventListener(AFTER_COMMIT)` erzeugt
  `event_publication`-Einträge und kann ohne Transaktion resubmitted werden. Das ist genau die stille Fehlerklasse aus
  `../../AGENTS.md`.
- **Folge der gewählten Lösung:** Sessions enden vor dem Commit. Ein Rollback danach erzwingt nur eine erneute
  Anmeldung, was harmlos ist.

**E17 – DB und ADR (B, D).**

- V1 verliert die `oauth2_*`-Tabellen (`V1__create_household_and_member.sql:56-123`). D ergänzt einen Kommentar zu
  `provider_id`.
- ADR-013, ADR-016 und ADR-018 werden im Bestand überarbeitet (F1). Jede überarbeitete ADR zeigt in „Decision History“,
  wann und warum umentschieden wurde.

---

## 4. Umsetzungsschritte

Regeln für alle Schritte:

- TDD: rot → grün → refactor.
- `@Nested` je Methode, sobald es mehr als zwei Tests gibt; Testnamen ohne Methodenpräfix.
- Ab drei gleichartigen Fällen parametrisieren.
- Eine Assertion pro Test; Rekursivvergleich gegen `expected`.
- Kein `@BeforeEach` in ITs.
- Jeder Commit ist grün: `./mvnw verify` (inkl. `ApplicationModules.verify()`) sowie
  `npm run test && npm run check && npm run lint`.
- In Service-Unit-Tests ist `MemberSessions` out of scope und wird per `@Mock` gemockt; der Service entsteht weiter per
  `@InjectMocks` (Owner-Feedback Phase F). Das Verhalten von `MemberSessions` selbst prüft `MemberSessionsTest`.

### Phase A – Rate-Limit und Lockout

**A1**

- Tests:
    - `Bucket4jRateLimiterTest @Nested acquire { belowLimit_empty, aboveLimit_returnsWaitTime }`
    -
  `RateLimitingFilterTest @Nested doFilterInternal { limited_resolvesTooManyRequestsProblem, limited_setsRetryAfter, notLimited_continuesChain }`
    - `RateLimitingIT login { tooManyFailedAttempts_returns429Problem, tooManyFailedAttempts_setsRetryAfter }`
- Commit: `feat(auth): Return rate-limit rejections as problem details with Retry-After`

**A2**

- Tests:
    - `RateLimitingConfigTest @Nested loginKey { emailLongerThan254Chars_truncated, normalEmail_unchanged }`
    - `RateLimitingIT login.manyDistinctEmailsSameIp_returns429`
- Commit: `feat(auth): Limit logins per IP and cap rate-limit key length`

**A3**

- Tests:
  -
  `PasswordResetServiceTest @Nested confirmPasswordReset { validToken_resetsLockout, invalidToken_doesNotResetLockout }`
    - `PasswordResetServiceIT @Nested confirmPasswordReset { lockedAccount_unlocks }`
- Doku:
    - Kap. 8 RATE1 vollständig: Bucket4j-Rate pro `email|IP` und IP-Regel, `Retry-After`. Wirksam nur, wenn der
      Reverse-Proxy `X-Forwarded-For` mit der Client-IP überschreibt statt anhängt (Kap. 7). Status: abhängig von der
      Deployment-Konfiguration. Lockout-Policy nach E8 als gewollt; Reset entsperrt. V6.1.1 (L1) nur teilweise erfüllt:
      Die Policy erlaubt ein bösartiges Sperren bekannter Konten (bewusste Abweichung, R14/Kap. 11); Gegenmaßnahme ist
      der sofort entsperrende Reset. V6.3.1 (L1) abhängig von der Deployment-Konfiguration (XFF).
    - Kap. 7: XFF-Anforderung an den Reverse-Proxy. Eine produktive Proxy-Konfiguration gibt es noch nicht (F8); Kap. 7
      beschreibt die Anforderungen, die eine künftige Konfiguration erfüllen muss.
    - Kap. 11: DoS-Restrisiko.
- Commit: `fix(auth): Unlock accounts on password reset and document rate-limit and lockout policy`

### Phase B – Kern (setzt Phase A voraus)

**B1 – ADR-013 + Index**

- ADR-013 wird im Bestand überarbeitet (F1); Entwurf in Abschnitt 5. Der Abschnitt „Decision History“ macht sichtbar,
  wann und warum umentschieden wurde.
- Index: Titel 013 anpassen.
- Commit: `docs(auth): Revise ADR-013 to session-based login without embedded authorization server`

**B2 – OpenAPI**

- `POST /login`, `LoginCredentials {email,password}`, `AccountLockedProblem` (allOf `Problem` + `lockedUntil`), Header
  `Retry-After`; Texte von `/logout` und `/auth/social-context` anpassen; alles mit `example`.

```yaml
  /login:
    post:
      tags: [ session ]
      operationId: login
      security: [ ]
      description: Form login (Spring Security filter). Renews session ID and XSRF-TOKEN cookie. Requires X-XSRF-TOKEN.
      requestBody: { required: true, content: { application/x-www-form-urlencoded: { schema: { $ref: '#/components/schemas/LoginCredentials' } } } }
      responses:
        "204": { description: Logged in. }
        "401": { description: /problems/invalid-credentials }
        "403": { description: /problems/email-not-verified | /problems/local-login-disabled | /problems/account-locked (AccountLockedProblem, Retry-After) }
        "429": { description: /problems/too-many-requests, headers: { Retry-After: { schema: { type: integer } } } }
        default: { $ref Problem }
```

- `npm run openapi` und `./mvnw clean compile`; die `example`-Werte im Review prüfen.
- Commit: `feat(api): Specify JSON login endpoint and login problem types`

**B3 – AS raus**

Pro Testgruppe rot → grün. Feinere Commits sind nur zulässig, wenn jeder einzeln grün ist.

1. `AccountPrincipalTest`
    - `@Nested equalsAndHashCode`, parametrisiert über gleiche ID / andere ID
    - `getUsername_anyPrincipal_memberIdString`
    - `eraseCredentials_withHash_passwordNull`
    - `toString_anyPrincipal_containsNoHash`
    - `isEnabled_unverified_false`
    - `identifiedBy_sameMemberId_equalsFullPrincipal`
2. `AccountUserDetailsServiceTest`
   -
   `@Nested loadUserByMemberId { unverified_returnsPrincipal, unknownMember_throwsUsernameNotFound, memberWithoutAccount_throwsUsernameNotFound }`
    - Bestehende `@Nested loadUserByUsername`-Tests auf den neuen Principal umstellen.
3. `ProblemDetailLoginFailureHandlerTest @Nested onAuthenticationFailure`, parametrisiert über alle Zeilen der
   E3-Tabelle außer „Rate-Limit“ und „Lokaler Login aus“; jeweils gegen `expected`.
4.

`CsrfTokenMaterializingStrategyTest @Nested onAuthentication { deferredToken_responseSetsXsrfCookie, noTokenAttribute_noCookie }`

5. `AccountPrincipalAuthorizationManagerTest @Nested check`, parametrisiert: `accountPrincipal_granted`,
   `oauth2AuthenticationToken_denied`, `anonymous_denied`
6. `AccountSessionSignInTest`
    - `@Nested signIn`:
        - `existingMember_contextHoldsExpectedPrincipal` (expected = `AccountPrincipal.identifiedBy(memberId)`)
        - `existingMember_invokesSessionAuthenticationStrategy`
        - `unknownMember_throws`
        - `noResponseInRequestContext_throwsIllegalStateException`
    - `@Nested signInByExternalIdentity`:
        - `linkedIdentity_returnsExpectedMemberId`
        - `linkedIdentity_contextHoldsExpectedPrincipal`
        - `linkedIdentity_registersSessionForPrincipal` (gegen expected:
          `registerNewSession(sessionId, AccountPrincipal.identifiedBy(memberId))`)
        - `linkedIdentity_doesNotChangeSessionIdAgain`
        - `unknownIdentity_returnsEmpty`
        - `unknownIdentity_leavesContextEmpty`
7. `MemberSessionsTest @Nested expireAll { existingSessions_expired, noSessions_noInteraction }`;
   `PasswordResetServiceTest` nutzt `MemberSessions`.
8. `HouseholdApiDelegateImplTest @Nested setupHousehold { anySetup_signsInSetupMemberId, serviceThrows_doesNotSignIn }`
   und
   `MembersApiDelegateImplTest @Nested joinHousehold { anyRegistration_signsInJoinedMemberId, serviceThrows_doesNotSignIn }`
    - Muster: `doThrow`, `catchThrowable`, `verifyNoInteractions`.
    - Die Service-Tests enthalten keinen Sign-in mehr.
9.

`AccountLockoutListenerTest @Nested onAuthenticationSuccess { accountPrincipal_resetsAttemptsForMemberId, otherPrincipal_noInteraction }`

10. `FederatedIdentityAuthenticationSuccessHandlerTest`
    - `linkedIdentity_signsInMember`
    - `linkedIdentity_redirectsToDashboard`
    - `noLinkedIdentity_invalidatesSession`
    - `noLinkedIdentity_redirectsWithNoAccountReason`
    - `signInThrows_invalidatesSession`
    - `signInThrows_redirectsWithSocialLoginFailedReason`
    - `anyContext_removesAuthorizedClient` (parametrisiert)
11. `SessionApiDelegateImplTest`
    - `/me` mit `AccountPrincipal`
    - `login_anyRequest_throwsLocalLoginDisabled`
    - `@Nested getAuthDiscovery { withRegistrations_listsClientNames, withoutRepository_empty }`
12. `SocialLoginRegistrationCheckTest @Nested check`
    - `issuerWithoutOpenid_throwsIllegalStateException`
    - `openidWithoutIssuer_throwsIllegalStateException`
    - `registrationWithoutEmailScope_throwsIllegalStateException`
    - `githubWithoutUserEmailScope_throwsIllegalStateException`
    - `googleCommonProviderDefaults_doesNotThrow`
    - `completeGoogleAndGithub_doesNotThrow`
    - Die `doesNotThrow`-Fälle mit `assertThatCode`.
13. `GitHubEmailAwareOAuth2UserServiceTest.nonGithubUserInfoUri_returnsUserUnchanged`
14. **`FormLoginIT`** (RANDOM_PORT; Accounts mit echtem Argon2-Hash)
    - `@Nested login`:
        - `validCredentials_returns204`
        - `validCredentials_meReturns200`
        - `oldSessionId_rejected` (Session vorher per `POST /v1/auth/social-context` erzwingen)
        - `newSessionIdDiffersFromOld`
        - `responseSetsNewXsrfCookie`
        - `postDirectlyAfterLogin_notForbidden`
        - `wrongPassword_returns401InvalidCredentials`
        - `unknownEmail_returns401InvalidCredentials`
        - `unverifiedCorrectPassword_returns403EmailNotVerified`
        - `lockedAccount_returnsLockedProblem`
        - `lockedAccount_setsRetryAfter`
        - `missingCsrfHeader_returns403`
    - `@Nested logout`:
        - `authenticatedSession_returns204`
        - `oldSessionCookieReplayedAfterLogout_returns401`
        - `responseSetsNewXsrfCookie`
        - `thenLoginWithSameCookieJar_returns204`
    - `@Nested sessionCookie { afterLogin_isSecureHttpOnlySameSiteLax }`
    - `@Nested passwordReset { sessionFromFormLogin_rejected }`
    - `@Nested emailChange { sessionStaysValid }`
    - Übernommen: `protectedBusinessEndpoint_noSession_returns401`, `publicEndpoints_noSession_reachable`
      (parametrisiert), `me_noSession_returns401`
15. `PasswordSetupFlowIT`
    -
    `@Nested householdSetup { localRegistration_meReturns200, localRegistration_oldSessionIdRejected, localRegistration_postDirectlyAfterSetup_notForbidden, localRegistration_verifyEmailThenPasswordReset_invalidatesSetupSession }`
    - `@Nested joinHousehold { localRegistration_meReturns200, localRegistration_oldSessionIdRejected }`
16. Auf RANDOM_PORT umstellen und Issuer/Redirect-Properties entfernen in: `LocalLoginDisabledIT` (formLogin fehlt →
    403), `HouseholdAccessControlIT`, `CsrfProtectionIT`, `CorsConfigurationIT`, `RateLimitingIT`.
17. `SocialLoginFilterChainIT` / `SocialSetupFlowIT`
    - `linkedIdentity_redirectsToDashboard`
    - `linkedIdentity_meReturns200`
    - `linkedIdentity_oldSessionIdRejected`
    - `callback.linkedIdentity_responseBodyContainsNoToken` (portiert aus `AuthorizationServerConfigurationIT.java:348`)
    - `callback.verifiedEmailInSetupContext_businessEndpointWithSameSession_returns401`
    - `authorizationRedirect_containsCodeChallengeStateAndNonce`
    - `noLinkedIdentity_rejectsWithNoAccountReason`
    - `providerDeniedAccess_rejectsWithSocialLoginFailedReason`
    - Setup/Join per Social: `/me` 200, alte Session-ID wird abgelehnt
18. `DexTestcontainersConfigurationIT` (bleibt bei `WebEnvironment.NONE`)
    - Konfiguration über `spring.security.oauth2.client.registration.dev-provider.*` (Scope `openid,profile,email`) und
      `provider.dev-provider.issuer-uri`.
    - `SessionValidationIT` anpassen.

Produktivcode nach E1–E9 und E12–E16:

- Neu in `config`: `SessionManagementConfig`, `CsrfTokenMaterializingStrategy`, `AccountPrincipalAuthorizationManager`,
  `ProblemDetailLoginFailureHandler`, `InvalidCredentialsProblem`, `AccountLockedProblem`,
  `SocialLoginRegistrationCheck`.
- Neu in `household`: `AccountSignIn` (Named Interface), `service/MemberSessions`; `AccountSessionAuthenticator` →
  `AccountSessionSignIn`.
- Gelöscht: `UnverifiedAccountLoginFailureHandler`, `LocalLoginDisabledFilter`, `RegisteredClientSeeder`,
  `RevokeAuthorizedClientLogoutHandler`, `NonSavingRequestCache`, `SpaClientAware*`, `SocialLoginProperties`,
  `SocialLoginClientRegistrationFactory`, `SocialLoginConfigurationValidator`, `OidcIssuerDiscovery`, `CommonProvider`,
  `AccountOidcPrincipal`, `AccountOidcUserService` samt Tests und `AuthorizationServerConfigurationIT` (Fälle →
  `FormLoginIT`).
- `SessionApiDelegateImpl`: `AccountPrincipal`, `login` wirft, Discovery aus `ClientRegistrationRepository`
  (`:6-7,64,136-149`).
- `PasswordResetService` ohne `OAuth2AuthorizedClientService` (`:27-30,38,77`), nutzt `MemberSessions`.
- `pom.xml`: AS-Starter raus (`:87-90`), `sortpom:sort`. `application.yaml`: `oauth2-authorization-server`,
  `oauth2-client`, `social-login` raus (`:63-69,82-98`), Boot-Beispiel, SameSite-Begründung (`:56-59`).
- `TestLibrehouseholdApplication` (`:10-28`) und `DexTestcontainersConfiguration` auf Boot-Properties.
  Commit: `refactor(auth)!: Replace embedded authorization server with session-based form and social login`

**B4 – Aufräumen**

- V1 `:56-123` und `client-secret` aus 33 Dateien entfernen.
- Commit: `chore(auth): Drop authorization server tables and obsolete client-secret test properties`

**B5 – Frontend-Pflichtanteile inkl. Vite**

- Tests:
    - `problemMapping.spec`: alle Typen; `403 unbekannter Typ → unexpected`; `Body ohne JSON → unexpected`;
      `FetchError → network`; `429 ohne Retry-After → rate-limited ohne Sekunden`
    - Login-Spec:
        - `success_navigatesToDashboardWithReplaceState`
        - `invalidCredentials_doesNotNavigate`
        - `locked_doesNotNavigate`
        - `rateLimited_doesNotNavigate`
        - `rateLimited_showsRetryAfterSeconds`
        - `rateLimitedWithoutRetryAfter_showsFallback`
        - `network_showsNetworkMessage`
        - `missingXsrfCookie_fetchesDiscoverFirst`
        - `authenticated_redirectsToDashboard`
    - `httpClient.spec`: 401 → nur `setGuest`
    - Guard-Spec: Gast → `goto('/login', {replaceState:true})`
    - `sessionLogout.spec`: `logout_anySession_replacesLocationWithRoot`,
      `logout_apiRequestFails_stillReplacesLocationWithRoot`
    - `WelcomeScreen.spec`: `authenticated_navigatesToDashboard`, `guest_staysOnWelcome`,
      `guest_loginButtonLinksToLogin`
    - Wizard- und `socialLogin`-Specs
- Umsetzung:
    - `F/lib/oauth2Login.ts` + Spec löschen.
    - `problemMapping.ts`: `classifyLoginProblem(status, problem, retryAfter)` → `invalid-credentials`, `unverified`,
      `locked`, `local-login-disabled`, `rate-limited`, `network`, `unexpected`.
    - Login-Seite (`:13-78`): generiertes `SessionApi.login`, vorher ggf. XSRF-Token holen.
    - `httpClient.ts` (`:5,25-38`): Middleware ruft nur `setGuest()`.
    - Guard in `F/routes/app/+layout.svelte` (`:7,19-23`).
    - `SetupWizard.svelte` (`:40,257-262`), `JoinWizard.svelte` (`:24,164-169`).
    - `WelcomeScreen.svelte`: Import `:7` raus, Login-Button `href="/login"` (`:51`), der `$effect` `:11-16` bleibt.
    - `socialLogin.ts` (`:5,22`), `sessionLogout.ts`: generiertes `logout`, danach `window.location.replace('/')`.
    - `vite.config.ts` (`:23-54`): `'/login'` mit Bypass → `'/login/oauth2'` ohne Bypass; `'/logout'` raus.
    - Messages: `login.error` → `login.error_invalid_credentials`; neu `login.error_rate_limited` (Plural),
      `login.error_rate_limited_unknown`, `login.error_network`, `login.error_unexpected`; `npm run paraglide`.
- Svelte-MCP: `list-sections` → `get-documentation` → `svelte-autofixer`.
- Manuell mit `TestLibrehouseholdApplication` und `VITE_API_URL=http://localhost:8080/v1 npm run dev`: Login, sechs
  Fehlversuche, Logout, Setup, Join, Dex-Login, Dex-Setup.
- Commit: `feat(auth): Log in via JSON session endpoint and drop OAuth2 loop-back in the SPA`

**B6 – Doku (Zwischenstand)**

- **ADR-014:** Kontext ohne AS-Tokens. Logout (`:24`): Session-Invalidierung statt Revocation; eine Revocation hat es
  nie gegeben. Provider-Tokens überleben den Callback nicht.
- **ADR-016:** im Bestand überarbeitet zu „Backend-Side (BFF) Social Login Federation“ via `oauth2Login()`, mit
  „Decision History“ wie ADR-013 (F1).
- **ADR-Index:** Titel 016; Eintrag 018 korrigieren (`:23` widerspricht dem ADR).
- **Kap. 4:** ADR-013-Titel (`:32,44`).
- **Kap. 5:** `household` nutzt zur Laufzeit die Framework-Beans `SessionAuthenticationStrategy`/`SessionRegistry` aus
  `config`.
- **Kap. 7:** Anforderungen an den Reverse-Proxy (F8: noch keine Konfiguration vorhanden): Routen `/v1/**`,
  `/oauth2/authorization/**`, `/login/oauth2/code/**`; `X-Forwarded-*` überschreiben statt anhängen. Pflicht für
  Provider: absolute `redirect-uri`, Scopes nach E12, unveränderliches `user-name-attribute`, PKCE-fähiger Provider.
- **Kap. 11:** TD1 (`:28`) „`session` hängt nur noch über `SocialLoginContextStore` an `config`“. Risiken: keine
  Token-API, MFA (V6.3.3), Session-Lebensdauer/Restart, client-erzeugte IDs (R17).
- **`../../AGENTS.md`:** `:293-297` → Auth-ITs mit RANDOM_PORT, Login = XSRF-GET + `POST /v1/login` (Feld `email`).
  `:299-301` löschen. `:400-402` → `AccountSessionSignIn` (isEnabled-Bypass, Sign-in nach Commit, nur modulintern).
  `:57-64` Dex über Boot-Properties, Vite `/login/oauth2`, ITs mit Zufallsport.

Kap. 8:

- A&A-Absatz (`:24`).
- Z. 27: `AccountRegistration` (existiert nicht) → `AccountSignIn`.
- **RESET1:** Passwort-Reset beendet Sessions und entsperrt; V7.4.3 (L2), V6.4.1 (L1).
- **VERIFY1:** V6.4.1 statt V6.2.3/V16.5.1.
- **RED1:** V10.4.1 n/a (kein eigener AS); absolute Redirect-URI, exakte Prüfung beim Provider.
- **PKCE1:** V10.2.1 (L2); V10.4.6 n/a.
- **SES1:** V3.3.1/V3.3.3 nicht erfüllt (`JSESSIONID`).
- **SES4:** Session-ID-Erneuerung, genau einmal pro Login-Pfad; V7.2.4 (L1).
- **SES5:** Logout invalidiert serverseitig; V7.4.1 (L1).
- **SES6:** App-Session unabhängig vom IdP; V7.1.3 (L2).
- **AUTH1:** V6.1.3/V6.3.4 (L2). Genau drei Sign-in-Pfade (formLogin, `signInByExternalIdentity`, `signIn` aus zwei
  Delegates), inklusive isEnabled-Bypass.
- **RATE1:** nur Pfad `/v1/login`.
- **ENUM1:** Status „nicht erfüllt“ (V6.3.8 L3). Unverifizierte und gesperrte Accounts sind ohne gültiges Passwort
  erkennbar, Accounts ohne Passwort per Timing, und `/members/availability` verrät die Existenz. Härtung folgt in Phase
  C.
- **FED1:** BFF als einziger Client; Provider-Tokens überleben den Callback nicht; V10.5.3 (L2, Issuer per
  Startup-Check). V6.8.1 und V10.2.2 folgen in Phase D.
- ASVS-IDs gegen ASVS 5.0 prüfen.

Commit: `docs(auth): Update ADRs, security controls and agent notes for session-based login`

**B7 – Client-Starter umstellen (F9)**

- `spring-boot-starter-oauth2-client` (in Boot 4.1 deprecated, `pom.xml:85`) →
  `spring-boot-starter-security-oauth2-client`; `sortpom:sort`.
- Abhängige Test-Starter prüfen und analog umstellen.
- `./mvnw clean verify` grün; keine Codeänderung erwartet.
- Commit: `chore(deps): Switch to spring-boot-starter-security-oauth2-client`

### Phase F – Sessions entfernter Mitglieder beenden (direkt nach B)

**F1**

- Tests:
    - `MemberManagementServiceTest`:
        - `@Nested leaveHousehold { anyMember_expiresMemberSessions, householdAdmin_doesNotExpireSessions }`
        - `@Nested removeMember { anyMember_expiresMemberSessions, householdAdmin_doesNotExpireSessions }`
    -
  `HouseholdManagementServiceTest @Nested deleteHousehold { existingHousehold_expiresSessionsOfAllMembers, unknownHousehold_doesNotExpireSessions }`
    - In beiden Service-Tests ist `MemberSessions` ein `@Mock`, der Service kommt per `@InjectMocks`; geprüft wird
      `verify(memberSessions).expireAll(...)` bzw. `verifyNoInteractions(memberSessions)`.
    - `MemberSessionInvalidationIT` (RANDOM_PORT):
        - `leaveHousehold_existingSession_rejected`
        - `removeMember_existingSession_rejected`
        - `deleteHousehold_existingSessions_rejected`
        - `unverifiedAccountExpiry_existingSession_rejected`
        - `removeHouseholdAdmin_returns4xx`
        - `removeHouseholdAdmin_existingSessionStaysValid`
- Commit: `fix(auth): Expire sessions of removed members and deleted households`

**F2**

- Kap. 8, neuer Eintrag **SES7**: Sessions enden bei Leave, Remove, Household-Delete und Expiry; V7.4.2 (L1); belegt
  durch `MemberSessionInvalidationIT`.
- Commit: `docs(auth): Document session termination for removed members`

### Phase C – Härtung gegen Enumeration

**C1**

- Tests:
  -
  `AuthenticationProviderConfigTest @Nested authenticationProvider { verifiedCorrectPassword_authenticates, unverifiedWrongPassword_throwsBadCredentials, unverifiedCorrectPassword_throwsDisabled }`
  -
  `AccountUserDetailsServiceTest @Nested loadUserByUsername { federatedAccount_throwsUsernameNotFound, lockedFederatedAccount_throwsUsernameNotFound, lockedAccount_throwsAccountLocked }`
    - `AccountLockoutListenerTest onAuthenticationFailure.accountWithoutPassword_noIncrement`
- Argon2 ist über die ITs mit echtem Hash belegt.
- Commit: `fix(auth): Check e-mail verification after the password and hide password-less accounts at login`

**C2**

- `FormLoginIT login.indistinguishableFailures_sameProblemBody`, parametrisiert über
  `wrongPassword|unknownEmail|federatedWithoutPassword|unverifiedWrongPassword`.
- Ersetzt `login.wrongPassword_…` und `login.unknownEmail_…` aus B3.14.
- Commit: `test(auth): Prove indistinguishable login failures`

**C3**

- ENUM1 auf das verbleibende Restsignal setzen: Ein gesperrter Account ist ohne Passwort erkennbar,
  `/members/availability` verrät die Existenz. V6.3.8 (L3) ist bewusst nicht erfüllt.
- Commit: `docs(auth): Document login enumeration controls`

### Phase D – Schlüssel für föderierte Identitäten

**D1**

- Tests:
  -
  `FederatedProviderKeyTest @Nested of { oidc_returnsIssuer, oauth2_returnsUserInfoUriWithoutQueryAndFragment, oauth2SameOriginDifferentPath_differentKeys }`
    - `SocialLoginFilterChainIT`: `sameSubjectDifferentIssuer_notLinked`, `renamedRegistrationId_stillLinked`
    - `SocialSetupFlowIT.setupWithoutLocalRegistration_storesIssuerAsProviderId`
    - Anpassen: `PendingSocialRegistrationStore`-, `MemberManagementService`-, `HouseholdSetupService`- und
      `SessionApiDelegateImpl`-Tests auf `providerId`
- Commit: `fix(auth): Key federated identities by issuer instead of registration id`

**D2**

- V1-Kommentar zu `account_identity.provider_id`.
- ADR-018: Schlüssel = Issuer bzw. User-Info-URI + Subject; im Bestand überarbeitet mit „Decision History“ (F1).
- Kap. 8 FED1: Schlüssel; V6.8.1 (L2); V10.2.2 Mix-up (eigene Redirect-URI pro Registrierung + Issuer-Schlüssel).
- Hinweis auf Dev-DB-Reset in `../../AGENTS.md`.
- Commit: `docs(auth): Document issuer-based federated identity key`

### Phase E – Frontend-UX

**E1 – Tests**

- `safeReturnTo.spec`:
    - `/app/x` wird erlaubt.
    - Abgelehnt: `//evil`, `/\\evil.example`, `/%5Cevil.example`, `/\t/evil.example`, `/login`, `https://…`, leerer
      Wert.
    - Implementierung: `new URL(returnTo, location.origin)`; gleicher Origin und Pfad beginnt mit `/app/`, sonst
      `/app/dashboard`.
- Guard-Spec: `/login?returnTo=…`, bei Ablauf zusätzlich `&reason=session-expired`.
- Login-Specs:
    - `success_navigatesToSafeReturnTo`
    - `sessionExpiredReason_showsInfo`
    - `error_alertFocused`
    - `invalidCredentialsWithProviders_showsSocialHintWithProviderNames`
    - `locked_showsResetLink` (parametrisiert: `lockedUntil` vorhanden / fehlend)
- `forgot-password`-Spec: Hinweis nur, wenn Provider konfiguriert sind.
- `sessionBootstrap.spec.networkError_keepsStatusAndShowsToast`
- `SetupWizard.spec.submitting_disablesSubmit`

**E2 – Umsetzung**

Texte. `{provider}` steht für die displayNames, verbunden mit „ / “.

| Key                                                                                                        | DE                                                                                                                                          | EN                                                                                                                     |
|------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `login.error_invalid_credentials_with_social_hint`                                                         | E-Mail oder Passwort ist falsch. Falls du dich über einen Anbieter registriert hast, nutze „Weiter mit {provider}“.                         | Incorrect email or password. If you signed up with a provider, use ‘Continue with {provider}’.                         |
| `login.error_rate_limited` (Paraglide-Plural; aus B)                                                       | one: Zu viele Anmeldeversuche. Bitte versuche es in 1 Sekunde erneut. / other: … in {seconds} Sekunden erneut.                              | one: Too many login attempts. Please try again in 1 second. / other: … in {seconds} seconds.                           |
| `login.error_locked` und `login.error_locked_unknown_time` (Zusatz, beide mit Link auf `/forgot-password`) | … Achtung: Auch danach sperrt schon ein weiterer falscher Versuch das Konto erneut. Setze dein Passwort zurück, um es sofort zu entsperren. | … Note: even after that, a single wrong attempt locks the account again. Reset your password to unlock it immediately. |
| `login.error_social_login_no_account_social_only`                                                          | Für dieses Anbieter-Konto gibt es kein LibreHousehold-Konto. Lass dich in einen Haushalt einladen.                                          | There is no LibreHousehold account for this provider account. Ask to be invited to a household.                        |
| `login.info_session_expired`                                                                               | Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.                                                                                   | Your session has expired. Please log in again.                                                                         |
| `forgot_password.success_message_social_hint`                                                              | Hast du dich über einen Anbieter registriert, hast du kein Passwort. Melde dich auf der Anmeldeseite mit „Weiter mit {provider}“ an.        | If you signed up with a provider, you have no password. Log in with ‘Continue with {provider}’ on the login page.      |

Weitere Umsetzung:

- `lockedUntil` per `Intl.DateTimeFormat(getLocale(), { timeStyle: 'short' })`, dazu ein Link auf `/forgot-password`.
- Die Variante „kein Konto“ hängt von `localEnabled` ab.
- `role="alert"` und `tabindex="-1"`, Fokus per `tick()`.
- `sessionBootstrap` setzt `setGuest()` nur bei 401.

Commit: `feat(auth): Improve login UX with safe return target, session-expiry notice and accessible errors`

---

## 5. ADR-013-Entwurf

```asciidoc
:jbake-title: ADR 013: Session-Based Authentication with Spring Security (no embedded Authorization Server)
:jbake-type: page_toc
:jbake-status: published
:jbake-menu: adrs
:jbake-order: 13

[[adr-013]]
= ADR 013: Session-Based Authentication with Spring Security (no embedded Authorization Server)

IMPORTANT: This decision was revised on 2026-11-03. The original decision (2026-07-30) chose an embedded Spring Authorization Server; see <<decision-history>> for what changed and why.

== Context

Authentication needs an architectural basis: how users log in, how the logged-in state is carried between requests, and how local password accounts and federated social login (xref:adr-016.adoc[ADR 016]) fit together.
The only client is the same-origin SPA/PWA, and xref:adr-014.adoc[ADR 014] keeps all tokens out of the browser.
The implemented Authorization Server variant issued tokens that nothing consumed; its costs are listed under option 1.

== Considered Options

. Embedded Spring Authorization Server with the backend as its own OAuth2/OIDC client (loop-back).
. Spring Security session authentication: `formLogin()` for local accounts and `oauth2Login()` as confidential client of external providers; the HTTP session is the only credential.
. External identity provider (e.g. Keycloak).
. Social login only.

== Decision Outcome

Option 2, in a single filter chain.
Local accounts log in via `POST /v1/login` with JSON responses (RFC 9457 problem details, see `api/openapi.yml`, `POST /login`).
External providers are configured with the standard `spring.security.oauth2.client.*` properties; a successful external login is mapped to the local account (xref:adr-016.adoc[ADR 016], xref:adr-018.adoc[ADR 018]) and yields the same session as a local login.
After household setup or invite join the new member is signed in programmatically with the same session-fixation protection.
There is one principal type, identified by the immutable member ID.
Towards external providers, Spring Security applies PKCE, `state`, and for OIDC registrations (scope `openid`) `nonce` and ID-token validation.

Option 1 is rejected because no token consumer exists.
Option 3 contradicts xref:../chapters/01_introduction_and_goals.adoc#QG3[QG3] and xref:../chapters/01_introduction_and_goals.adoc#QG4[QG4].
Option 4 forces a third-party account on every user.

== Pros and Cons of the Options

[cols="2a,2a"]
|===
|Pros |Cons

2+^s|Embedded Authorization Server with loop-back client
|
- Token issuance in place should a consumer appear
|
- Tokens without consumer; authorization still against the session
- Two filter chains sharing one saved request; framework workarounds (`auth_time` factors, separate dev authorization URI)
- Custom client registration repository disabling Spring Boot's social-login configuration
- Two principal types, port-bound tests, mandatory issuer/redirect/secret configuration, unused tables
- Login errors only as redirect query parameters

2+^s|Session authentication with `formLogin()` + `oauth2Login()` (chosen)
|
- One filter chain, one principal, one credential
- Distinguishable JSON login errors, including rate limiting
- Standard Spring Boot provider configuration; no token/issuer setup for self-hosters
|
- No token API for non-browser or third-party clients

2+^s|External identity provider
|
- Full-featured IdP without own code
|
- Second service to operate (QG3, QG4)

2+^s|Social login only
|
- No password handling
|
- Mandatory third-party account
|===

== Consequences

**Positive:** standard Spring Security building blocks only; every login path renews the session ID and registers the session, so password resets end all sessions; e-mail changes do not orphan sessions.

**Negative:** no token-based access for other clients; provider scopes must be configured explicitly (xref:../chapters/07_deployment_view.adoc[Chapter 7]); session lifetime is the container default (30 min idle) and sessions do not survive restarts (tracked in xref:../chapters/11_technical_risks.adoc[Chapter 11]).

== Conditions for Reintroducing an Authorization Server

Only when a real token consumer appears: a native or other non-browser client, third-party integrations needing delegated scoped access, SSO across first-party apps on different domains, or LibreHousehold acting as IdP.
The affected APIs then become an OAuth2 resource server (`oauth2ResourceServer()`) with the member ID as subject; the SPA stays on the session-based BFF model, and no loop-back is reintroduced.

[[decision-history]]
== Decision History

[cols="1,2a,3a"]
|===
|Date |Decision |Reason for the change

|2026-07-30
|Option 1: embedded Spring Authorization Server; the backend logs in against itself as OAuth2/OIDC client (loop-back). Expected benefit: local and federated login through the same Authorization Code + PKCE flow, ready for future token clients.
|Initial decision.

|2026-11-03
|Option 2: session authentication with `formLogin()` and `oauth2Login()`, no Authorization Server.
|
- The implementation showed that no component consumes the issued tokens: there is no resource server, and authorization runs against the session.
- The loop-back required workarounds: two filter chains sharing one saved request, `auth_time` factor injection, a separate dev authorization URI, a custom client registration repository, port-bound integration tests.
- Session registration only happened in the Authorization Server chain, so programmatic sign-ins were not covered by the password-reset session termination.
- The change happened before the first release; no data or clients had to be migrated.
|===
```

Für ADR-016 (B6) und ADR-018 (D2) gilt dasselbe Muster: `IMPORTANT`-Hinweis unter dem Titel und Abschnitt „Decision
History“ mit ursprünglicher Entscheidung (ADR-016: 2026-10-07), neuer Entscheidung und Grund.

---

## 6. Risiken & Gegenmaßnahmen

| #   | Risiko                                                                                                                                                                                                                                                                                                                                                    | Gegenmaßnahme                                                                                                                                                                                    |
|-----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1  | RESET1 läuft still ins Leere, weil heute nur der AS registriert (`SecurityConfig.java:107-113`).                                                                                                                                                                                                                                                          | E6, E16 (`MemberSessions`); ITs für FormLogin, Setup mit Verifikation, Social                                                                                                                    |
| R2  | Lockout-Reset bricht, weil `getName()` nun die memberId liefert (`AccountLockoutListener.java:42-45`).                                                                                                                                                                                                                                                    | E8; Unit-Tests                                                                                                                                                                                   |
| R3  | Passwort-Hash bleibt in der Session.                                                                                                                                                                                                                                                                                                                      | `CredentialsContainer`; Test                                                                                                                                                                     |
| R4  | Nach dem Login fehlt das XSRF-Token, oder Strategien laufen doppelt bzw. es kommt zu Bean-Mehrdeutigkeit.                                                                                                                                                                                                                                                 | E6 (`@Primary`-Composite, CSRF-Strategie nur per Qualifier, Social-Sign-in nur `registerNewSession`); ITs zu XSRF und `newSessionIdDiffersFromOld`; `linkedIdentity_doesNotChangeSessionIdAgain` |
| R5  | Self-Hoster konfigurieren Provider falsch.                                                                                                                                                                                                                                                                                                                | `SocialLoginRegistrationCheck`, Kap. 7                                                                                                                                                           |
| R6  | Ein Provider lehnt PKCE für vertrauliche Clients ab.                                                                                                                                                                                                                                                                                                      | Bewusst kein Opt-out. Google, GitHub (seit 07/2025) und Dex unterstützen es; andere Provider werden nicht unterstützt (Kap. 7).                                                                  |
| R7  | Innerhalb von Phase B ist das Frontend zwischen B3 und B5 nicht Ende-zu-Ende nutzbar.                                                                                                                                                                                                                                                                     | Phase B wird als Ganzes vorgestellt, freigegeben und committet                                                                                                                                   |
| R8  | Dev-DBs: Die V1-Checksumme ändert sich (B4), `provider_id` bekommt neue Semantik (D).                                                                                                                                                                                                                                                                     | Testcontainers starten frisch; Reset-Hinweis in `../../AGENTS.md`; kein Release, also keine Migration                                                                                            |
| R9  | `ClientRegistrationRepository` bei `WebEnvironment.NONE`                                                                                                                                                                                                                                                                                                  | geklärt, greift                                                                                                                                                                                  |
| R10 | Falsche Redirect-URI oder Client-IP hinter dem Reverse-Proxy. Eine produktive Proxy-Konfiguration gibt es noch nicht (F8).                                                                                                                                                                                                                                | Absolute `redirect-uri`; Kap. 7 legt fest, dass der Proxy `X-Forwarded-*` überschreibt                                                                                                           |
| R11 | Ein fremdes Authentication-Objekt erreicht Endpunkte.                                                                                                                                                                                                                                                                                                     | E7                                                                                                                                                                                               |
| R12 | Der `handlerExceptionResolver` wird erst nach dem Schreiben der Response aufgerufen.                                                                                                                                                                                                                                                                      | Aufruf nur vorher; ITs prüfen Content-Type und Body                                                                                                                                              |
| R13 | `Retry-After` verrät die Fensterlänge.                                                                                                                                                                                                                                                                                                                    | akzeptiert                                                                                                                                                                                       |
| R14 | Lockout-DoS: Ein Angreifer mit bekannter E-Mail hält ein Konto dauerhaft gesperrt (gewollte Policy, E8).                                                                                                                                                                                                                                                  | Reset entsperrt sofort (A3); die Sperrmeldung zeigt den Reset-Weg (Phase E); dokumentiert in Kap. 8 RATE1 und Kap. 11                                                                            |
| R15 | Zwischen B und F behalten entfernte Mitglieder ihre Sessions.                                                                                                                                                                                                                                                                                             | Der Guard verweigert per DB; Phase F folgt direkt auf B                                                                                                                                          |
| R16 | Rollback nach `expireAll` (F): Sessions sind beendet, obwohl die Löschung nicht stattfindet.                                                                                                                                                                                                                                                              | Akzeptiert: Es erzwingt nur eine erneute Anmeldung (E16)                                                                                                                                         |
| R17 | `memberId` und `householdId` bleiben client-erzeugt (F10): (a) eine neue Registrierung mit der ID eines gelöschten Mitglieds erbt dessen Sessions; (b) Tasks und Expenses referenzieren `memberId` ohne Fremdschlüssel und werden vererbt; (c) ein neu angelegter Haushalt mit alter `householdId` erbt bei `FAILED`-Aufräum-Events Daten anderer Module. | (a) Phase F. (b) und (c) akzeptiert, in Kap. 11 dokumentiert (B6)                                                                                                                                |

**Rollback:** Vor der Freigabe wird nichts committet; Änderungen einer Phase lassen sich bis dahin verwerfen. Nach dem
Commit sind die Commits einer Phase einzeln revertierbar, B nur als Ganzes. C, D, E und F hängen von B ab und werden vor
B revertiert. Nach einem Revert von B4 oder D muss die Dev-DB zurückgesetzt werden.

---

## 7. Abnahmekriterien

Gilt für alle Phasen: Der Owner hat den Stand freigegeben, bevor committet wird. `./mvnw clean verify` (inkl.
`ApplicationModules.verify()`) und `npm run test`/`check`/`lint` sind grün. Angefasste Produktivkommentare enthalten
keine Plan- oder Bug-IDs.

**Phase A**

- A1: 429 kommt als `too-many-requests` mit `Retry-After`, auch bei vielen E-Mails von derselben IP.
- A2: Ein Passwort-Reset entsperrt ein gesperrtes Konto sofort. Ein ungültiges Token entsperrt nicht. Die Lockout-Policy
  selbst ist unverändert und in RATE1 als gewollt beschrieben.

**Phase B**

- B1: Keine Treffer für
  `authorization-server|spa-backend-client|RegisteredClient|AccountOidcPrincipal|FactorGrantedAuthority|NonSavingRequestCache|DEFINED_PORT|findAvailableTcpPort|completeSilentOAuth2Login|LocalLoginDisabledFilter|SocialLoginProperties`
  außer in der „Decision History“ der ADRs.
- B2: Kein AS-Starter, keine `oauth2_*`-Tabellen, eine `SecurityFilterChain`, ein Principal-Typ, genau ein `@Primary`-
  `SessionAuthenticationStrategy`-Bean.
- B3: `POST /v1/login` liefert 204, 401 `invalid-credentials` und 403 `email-not-verified` / `account-locked`
  (`lockedUntil`, `Retry-After`) / `local-login-disabled`.
- B4: formLogin, Social Login, Setup und Join (lokal und sozial) lehnen die alte Session-ID ab, mit genau einem
  ID-Wechsel pro Login.
- B5: Ein Passwort-Reset beendet bestehende Sessions. Eine E-Mail-Änderung lässt die Session gültig.
- B6: Der Login setzt ein neues `XSRF-TOKEN`. Ein POST direkt nach Login oder Setup ist nicht 403.
- B7: Der Logout liefert 204 und ein neues `XSRF-TOKEN`. Die alte Session-ID liefert danach 401. Ein erneuter Login mit
  demselben Cookie-Jar liefert 204.
- B8: Die Social-Redirects enthalten `code_challenge`, `state` und `nonce`. Der Callback-Body enthält kein Token. Eine
  Session ohne `AccountPrincipal` erhält 401. Eine Fehlkonfiguration bricht den Start ab.
- B9: Frontend:
    - 429 (mit und ohne `Retry-After`) und Netzwerkfehler zeigen eigene Meldungen und navigieren nicht.
    - Guard und Login nutzen `replaceState`.
    - Der Logout lädt per `window.location.replace('/')` neu und endet auf `/`.

**Phase F**

- F1: Leave, Remove, Household-Delete und der Ablauf unverifizierter Accounts beenden bestehende Sessions. Ein
  abgelehntes Entfernen (Admin) liefert 4xx und lässt die Session gültig.

**Phase C**

- C1: Unbekannte E-Mail, falsches Passwort, Account ohne Passwort und unverifiziert mit falschem Passwort liefern einen
  byte-gleichen 401-Body. `email-not-verified` kommt nur bei richtigem Passwort.

**Phase D**

- D1: Die Identität hängt an Issuer bzw. User-Info-URI + Subject. Nach Umbenennen der Registration bleibt die
  Verknüpfung bestehen, bei anderem Issuer nicht.

**Phase E**

- E1: `returnTo` außerhalb von `/app/` wird ignoriert, inklusive Backslash- und Steuerzeichen-Varianten. Bei Ablauf geht
  es auf `/login?returnTo=…&reason=session-expired`.
- E2: Fehler stehen in `role="alert"` und erhalten den Fokus. Social-Hinweise nennen die Provider. Die Sperrmeldung
  verlinkt den Reset.

---

## 8. Entscheidungen des Owners

- **F1:** ADR-013, ADR-016 und ADR-018 werden im Bestand überarbeitet. Jede ADR macht in „Decision History“ sichtbar,
  wann und warum umentschieden wurde (Abschnitt 5).
- **F2–F7:** Login-Feld `email`; Fail-fast-Check (E12); `mokapi.js` bleibt unverändert; PKCE ist Pflicht; Sign-in nach
  Commit; `Retry-After`.
- **F8:** Eine produktive Reverse-Proxy-Konfiguration gibt es noch nicht. Kap. 7 beschreibt die Anforderungen (A3, B6);
  eine Konfiguration zu erstellen ist nicht Teil dieses Plans.
- **F9:** Umstellung auf `spring-boot-starter-security-oauth2-client` als Schritt B7.
- **F10:** `memberId` und `householdId` bleiben client-erzeugt (Offline-First). Die Folgen stehen als R17 in den
  Risiken.
- **Ablauf:** Keine PRs, sondern Phasen mit Owner-Freigabe vor dem Commit (Abschnitt 1). Kein Release vorhanden:
  Änderungen gehen in die bestehenden Migrationen und Konfigurationen.

---

## 9. Außerhalb des Scopes

- E-Mail-Änderung ohne Verifikation (`MemberManagementService.updateMember` → `updateEmail`).
- Eine produktive Reverse-Proxy-Konfiguration (F8) und Code-Änderungen am Rate-Limit-Key hinter dem Proxy. Die
  Abhängigkeit vom Deployment ist in RATE1 und Kap. 7 dokumentiert.
- Änderungen an der Lockout-Policy, etwa ein Zähler-Reset beim Sperren oder eine progressive Sperre. Die heutige Policy
  bleibt gewollt (E8, R14).
- Security-Logging und Audit-Events.
- `__Host-`-Prefix (SES1) und die Abweichung OpenAPI-`securitySchemes` (`SESSION`, `api/openapi.yml:3063-3070`)
  gegenüber `JSESSIONID`.
- MFA (V6.3.3) sowie Session-Lebensdauer und Persistenz; beides nur als Risiko in Kap. 11.
- Andere Sessions bei eigener Passwortänderung invalidieren.
- Rate-Limit für Social-Callbacks, RP-initiated Logout, Spring Session.
- Serverseitige IDs und ein geprüftes Aufräumen für R17 (b) und (c) (F10: nein).
- TD1 (`allowedDependencies`) wird nur textlich angepasst. AC1, RED2, ERR1 und PW4 bleiben offen.
- **Neu im Scope:** IP-Limit, `Retry-After`, Entsperren per Reset, Sessions entfernter Mitglieder (Phase F).
