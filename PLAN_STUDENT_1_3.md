# Plan dovršavanja projekta — PKI 2026 (Student 1 i Student 3)

> Dokument napravljen analizom specifikacije (`BSEP 2026 - Public Key Infrastructure 2026.docx`)
> i trenutnog stanja koda na `main` grani (poslednji commit `9534263`).
>
> Cilj: spisak svega što **fali** za funkcionalnosti Studenta 1 i Studenta 3, sa uputstvom **kako to implementirati**.
> Na kraju dokumenta je odvojen spisak stvari koje **Student 2** mora da odradi (neke su blokirajuće za nas).

---

## 0. Podela funkcionalnosti (iz specifikacije)

| Student | Funkcionalnosti |
|---|---|
| **Student 1 (ja)** | 2.1 Registracija · 2.2 Prijava · 2.4 Praćenje aktivnih tokena · 2.5 Izdavanje sertifikata · 2.10 Povlačenje sertifikata |
| **Student 2** | 2.3 Oporavak naloga · 2.6 Čuvanje CA sertifikata · 2.7 Čuvanje EE sertifikata · 2.9 Pregled i pristup sertifikatima |
| **Student 3 (ja)** | 2.8 CSR · 2.11 Šabloni · 2.12 Password manager · 2.12.1 Deljenje lozinki |

---

## 1. Brzi pregled statusa (Student 1 i 3)

| # | Funkcionalnost | Status | Ostalo da se uradi |
|---|---|---|---|
| 2.1 | Registracija | ✅ Gotovo | — *(org polje obavezno — urađeno u Sprintu 1)* |
| 2.2 | Prijava (login + reCAPTCHA) | 🟢 Gotovo | — |
| 2.4 | Praćenje aktivnih tokena | ✅ Gotovo | — *(urađeno u Sprintu 1: sesije, lista, opoziv)* |
| 2.5 | Izdavanje sertifikata | 🟠 ~25% | Samo ROOT radi. Fali Intermediate, EE, lanac, izbor issuera, validacija issuera, ekstenzije |
| 2.10 | Povlačenje sertifikata | 🔴 ~5% | Revoke endpoint, razlog (X.509), CRL/OCSP, provera pri izdavanju |
| 2.8 | CSR | 🟠 ~50% | Parsiranje radi; fali stvarno potpisivanje/izdavanje iz CSR-a |
| 2.11 | Šabloni | 🔴 0% | Cela funkcionalnost (entitet, CRUD, primena na formu izdavanja) |
| 2.12 | Password manager | 🟢 ~85% | Core radi (save/read, Web Crypto). Fali brisanje TODO-a, sitnice |
| 2.12.1 | Deljenje lozinki | ✅ Gotovo | — *(frontend UI + backend hardening — urađeno u Sprintu 1)* |

Legenda: ✅ završeno (Sprint) · 🟢 gotovo (ranije) · 🟠 delimično · 🔴 nije rađeno

> **Sprint 1 (završen):** 2.1 organizacija obavezna · 2.12.1 deljenje lozinki (frontend UI + backend guard protiv self/duplo deljenja) · 2.4 praćenje aktivnih tokena (kompletno). Backend i frontend build prolaze.

---

## 2. Zavisnosti od Studenta 2 (šta nas blokira)

Naše funkcionalnosti **2.5**, **2.8** i **2.10** dele infrastrukturu sa funkcijama Studenta 2 (čuvanje CA u keystore, čuvanje EE, pregled/pristup). Trenutno stanje:

- ✅ **Postoji osnova** koju možemo da koristimo odmah:
  - `KeystoreService` — snimanje/čitanje PKCS12 keystore-a (`backend/.../service/KeystoreService.java`).
  - `KeyEncryptionService` — po-korisnički simetrični ključ (PBKDF2 iz master lozinke + `userId` kao salt), AES-GCM enkripcija lozinke keystore-a. Ovo je tačno ono što spec traži u 2.6.
  - `Certificate` entitet ima `issuer` (self-reference), `keystoreAlias`, `certificateData` (PEM), `status`, `type`.

- ⚠️ **Problemi koje moramo da rešimo zajedno sa Studentom 2** (detaljan spisak u sekciji 9):
  1. Trenutno se enkriptovana keystore lozinka čuva na `User.keystorePasswordEncrypted` (jedno polje po korisniku, **prepisuje se** pri svakom novom sertifikatu). Za lanac i više CA to ne valja → treba lozinku/alias vezati za **`Certificate`** (ili poseban `Keystore` entitet), ne za usera.
  2. `GET /api/certificates` vraća **sve** sertifikate **svakom** ulogovanom korisniku → access control (2.9) nije implementiran. Ovo Student 2 radi, ali nas direktno dodiruje (revoke, CSR liste).
  3. EE sertifikati se još ne čuvaju (2.7).

**Zaključak:** Možemo odmah da krenemo sa 2.5/2.8/2.10 jer keystore infrastruktura postoji. Da bi sve bilo konzistentno, dogovoriti sa Studentom 2 da se keystore-metapodaci (alias, enkriptovana lozinka, putanja) presele na nivo sertifikata/CA. Dok se to ne uradi, koristimo postojeći obrazac (enkripcija po `ownerId` CA korisnika).

---

# STUDENT 1

## 2.1 — Registracija 🟢

**Urađeno:** forma (ime, prezime, email, lozinka 2x, organizacija), zxcvbn estimator jačine, frontend validacija (min 12, velika/mala/broj/specijalni), backend `@ValidPassword`, aktivacioni token (jednokratan, 24h), generička poruka za postojeći email (OWASP).

**Fali / doraditi:**
- [ ] Spec kaže da je **organizacija obavezna** za običnog korisnika. U `register.component.ts` polje `organization` je opciono → dodati `Validators.required`. Na backendu u `RegisterRequest` dodati `@NotBlank` na `organization`.
- [ ] (Opciono) Resend aktivacionog linka ako istekne.

**Gde:** `frontend/src/app/auth/register/register.component.ts`, `backend/.../model/dto/RegisterRequest.java`.

---

## 2.2 — Prijava na sistem 🟢

**Urađeno:** login (email+lozinka), reCAPTCHA v2 (`CaptchaService` + `ng-recaptcha`), generička greška „Invalid credentials“, JWT izdavanje.

**Fali:** ništa kritično. (Napomena: nakon što se uradi 2.4, login mora da **registruje sesiju** — vidi dole.)

---

## 2.4 — Praćenje aktivnih tokena 🔴 (NIJE RAĐENO — velika stavka)

**Zahtev:** korisnik u profilu vidi listu aktivnih JWT sesija (uređaj, browser, IP, vreme poslednje aktivnosti); može pojedinačno da opozove token; **tokeni se NE čuvaju u bazi**, identifikuju se preko polja (`jti`).

**Trenutno:** `JwtService` već stavlja `jti` claim (UUID) u token — to je osnova. Ali nema evidencije sesija ni opoziva.

### Kako implementirati

1. **Entitet `UserSession`** (ne čuva sam token, samo metapodatke + `jti`):
   ```java
   // backend/.../model/entity/UserSession.java
   @Entity @Table(name = "user_sessions")
   class UserSession {
     @Id @GeneratedValue Long id;
     @Column(unique = true, nullable = false) String jti;   // veza ka tokenu
     @ManyToOne User user;
     String ipAddress;
     String userAgent;          // iz njega parsiraj device/browser
     String deviceLabel;        // npr. "Chrome na Windows"
     LocalDateTime issuedAt;
     LocalDateTime lastActivityAt;
     LocalDateTime expiresAt;
     boolean revoked;           // opoziv = true
   }
   ```
   Repo: `UserSessionRepository extends JpaRepository<UserSession,Long>` sa `findByUserAndRevokedFalse`, `findByJti`, `deleteByExpiresAtBefore`.

2. **Pri loginu** (`AuthService.login`): nakon generisanja tokena, izvuci `jti` (`jwtService.extractJti(token)`), pročitaj `IP` i `User-Agent` (proslediti `HttpServletRequest` ili `LoginRequest` obogatiti), snimi `UserSession`. Parsiranje UA: jednostavan util ili biblioteka (`ua-parser`/ručno regexom za browser+OS).

3. **JWT filter / validacija**: u `JwtService.validateToken` (ili u `JwtAuthenticationFilter`) **proveri da sesija sa tim `jti` postoji i nije `revoked` i nije istekla**. Ako jeste opozvana → 401. Time opoziv trenutno izbacuje korisnika s tog uređaja.
   - Takođe pri svakom zahtevu osveži `lastActivityAt` (može throttle-ovano, npr. ako je prošlo > 1 min).

4. **Endpointi** (`SessionController`, `/api/sessions`):
   - `GET /api/sessions` → lista aktivnih sesija ulogovanog korisnika (IP, deviceLabel, lastActivityAt, da li je „trenutna“).
   - `DELETE /api/sessions/{jti}` → opoziv (`revoked=true`) te sesije (samo svoje).
   - (Opciono) `DELETE /api/sessions` → odjavi sve osim trenutne.

5. **Čišćenje**: `@Scheduled` job koji briše istekle/opozvane sesije (rotacija).

6. **Frontend**: stranica „Aktivne sesije“ u profilu — tabela + dugme „Opozovi“. Servis `session.service.ts`, ruta `/sessions` (guard).

**Napomena o „ne čuvati tokene u bazi“:** čuvamo samo `jti` (identifikator), nikako sam JWT string — to je u skladu sa zahtevom.

---

## 2.5 — Izdavanje sertifikata 🟠 (samo ROOT radi)

**Urađeno:** `CertificateService.generateRootCertificate` — generiše RSA par, X500Name, BasicConstraints(CA), keyUsage(keyCertSign|cRLSign), SKI/AKI, self-sign, snima u keystore i bazu. Frontend forma postoji ali šalje samo na `/root` i vidljiva je samo ADMIN-u.

**Fali (glavni deo funkcionalnosti):**

### a) Izdavanje INTERMEDIATE i END_ENTITY (potpisuje izabrani CA)

Novi endpoint i metoda `issueCertificate(IssueCertificateRequest req, User caller)`:

- **Request DTO** `IssueCertificateRequest`: `issuerCertificateId` (koji CA potpisuje), `type` (INTERMEDIATE|END_ENTITY), X500Name polja (CN, O, OU, C, ST, L, email), `validFrom`/`validTo` ili `validDays`, lista ekstenzija (keyUsage, extendedKeyUsage, BasicConstraints, SAN…), `keySize`, opciono `templateId` (vidi 2.11).
- **Postupak**:
  1. Učitaj issuer `Certificate` iz baze po `issuerCertificateId`.
  2. **Validiraj issuera** (ključno — spec to izričito traži):
     - period važenja (`validFrom <= now <= validTo`),
     - status != `REVOKED` i nije expired,
     - issuer je CA (BasicConstraints isCA=true; ROOT ili INTERMEDIATE),
     - **novi sertifikat ne sme da traje duže od issuera** (`req.validTo <= issuer.validTo`),
     - (preporuka) proveri ceo lanac do root-a da nijedan nije povučen/istekao.
  3. Učitaj issuer **privatni ključ** iz keystore-a: dekriptuj keystore lozinku (`KeyEncryptionService.decryptPassword` sa ključem CA korisnika), `KeystoreService.loadFromKeystore`.
  4. Generiši novi par ključeva (za INTERMEDIATE; za EE iz CSR-a se koristi javni ključ iz CSR-a — vidi 2.8).
  5. Build sertifikata: subject = uneti podaci, issuer = issuer subject, potpiši **issuer privatnim ključem**. AKI = issuer SKI.
  6. Ekstenzije po tipu:
     - INTERMEDIATE: BasicConstraints(true, pathLen opc.), KeyUsage(keyCertSign|cRLSign).
     - END_ENTITY: BasicConstraints(false), KeyUsage(digitalSignature|keyEncipherment…), ExtendedKeyUsage, SAN.
     - **CRL Distribution Point** ekstenzija (za 2.10) — URL ka našem CRL endpoint-u.
  7. Snimi: INTERMEDIATE → u keystore (privatni ključ + ceo lanac) sa svojom nasumičnom lozinkom, enkriptovanom ključem CA korisnika; bazu (`type`, `issuer`, `owner`, `keystoreAlias`, PEM, status ACTIVE).
  8. Vrati `CertificateResponse`.

- **Proizvoljna dubina lanca**: pošto issuer biramo iz baze i čuvamo ceo `chain` u keystore, dubina je automatski proizvoljna — samo dozvoli da issuer bude bilo koji INTERMEDIATE.

### b) Autorizacija po ulogama (spec 1.1/1.2)
- **ADMIN**: može da koristi **bilo koji** CA iz bilo kog lanca.
- **CA korisnik**: može da izdaje INTERMEDIATE/EE **samo iz svog lanca** (issuer mora pripadati njegovoj organizaciji/njegovim sertifikatima). Dodati proveru vlasništva nad issuerom.

### c) Frontend
- Proširiti `certificate-form.component` da podržava: izbor tipa (ROOT/INTERMEDIATE/EE), dropdown za izbor issuer CA (lista CA sertifikata dostupnih korisniku), unos ekstenzija, prikaz max trajanja na osnovu issuera.
- `certificate.service.ts`: dodati `issueCertificate(...)` → `POST /api/certificates/issue`.

**Napomena:** za čuvanje treba dogovor sa Studentom 2 (2.6) — vidi sekciju 9, tačka 1 (keystore metapodaci po sertifikatu, a ne po useru).

---

## 2.10 — Povlačenje sertifikata (Revocation) 🔴

**Zahtev:** svaki korisnik može da povuče sertifikat uz **razlog iz X.509**; povučeni se ne nude za dalje izdavanje (ni privatni ključ); Revocation servis — **CRL** (preko CRL Distribution Point ekstenzije) ili **OCSP**.

**Trenutno:** postoji `CertificateStatus.REVOKED`, ali nema endpointa, razloga, CRL/OCSP, ni provere pri izdavanju.

### Kako implementirati

1. **Model**: na `Certificate` dodati `revocationReason` (enum po RFC 5280: `UNSPECIFIED, KEY_COMPROMISE, CA_COMPROMISE, AFFILIATION_CHANGED, SUPERSEDED, CESSATION_OF_OPERATION, CERTIFICATE_HOLD, PRIVILEGE_WITHDRAWN, AA_COMPROMISE`), `revokedAt`, `revokedBy`.

2. **Endpoint** `POST /api/certificates/{id}/revoke` (telo: `{ reason }`):
   - autorizacija: ADMIN sve; CA korisnik svoje iz lanca; običan korisnik svoj EE.
   - postavi `status=REVOKED`, `revocationReason`, `revokedAt`.
   - **Kaskadno**: ako se povuče CA sertifikat, povuci (ili označi nevažećim) sve sertifikate koje je on izdao (rekurzivno niz lanac).

3. **Provera pri izdavanju (2.5/2.8)**: pre potpisivanja proveri da issuer (i ceo lanac) nije `REVOKED`. Povučeni privatni ključ se ne sme koristiti.

4. **CRL servis** (preporučeno, jednostavnije od OCSP):
   - Endpoint `GET /api/crl/{caId}.crl` (ili `/crl/{caId}`) koji generiše **X509 CRL** potpisan tim CA: BouncyCastle `X509v2CRLBuilder`, dodaj sve povučene serijske brojeve koje je taj CA izdao (sa razlogom i datumom), potpiši CA privatnim ključem.
   - U sertifikate koje izdajemo ubaci **CRLDistributionPoints** ekstenziju sa URL-om ka ovom endpoint-u.
   - (Opciono za bodove: OCSP responder endpoint.)

5. **Frontend**: dugme „Povuci“ na listi sertifikata → dijalog za izbor razloga (X.509). Prikaz statusa REVOKED + razloga.

**Gde:** `CertificateController`, `CertificateService` (revoke + CRL), novi `CrlController`, `certificate-list.component`.

---

# STUDENT 3

## 2.8 — CSR (Certificate Signing Request) 🟠

**Urađeno:** upload `.pem/.csr`, validacija fajla, parsiranje (`CsrService.parseCsr` → CN/O/OU/C/ST/L/email, alg, key size, provera potpisa), preview na frontendu, izbor CA (`caId`) i `validDays` u formi. `csr-upload.component` postoji.

**Fali:** **stvarno izdavanje sertifikata iz CSR-a** — u `CsrController.uploadCsr` stoji `// TODO: implement signing for final defense`.

### Kako implementirati

1. U `CsrService` (ili pozvati `CertificateService.issueCertificate`) dodati `issueFromCsr(PKCS10CertificationRequest csr, Long caId, int validDays, User caller)`:
   - validiraj CSR potpis (već postoji),
   - učitaj izabrani CA, **validiraj issuera** (period, potpis, status — kao u 2.5),
   - **trajanje EE ne sme da pređe trajanje CA** (spec izričito),
   - subject = iz CSR-a (X500Name), javni ključ = iz CSR-a (`csr.getSubjectPublicKeyInfo()`), **privatni ključ se NE generiše** (ostaje kod korisnika),
   - ekstenzije: BasicConstraints(false), KeyUsage/EKU, SAN (iz CSR atributa ako postoje), CRLDistributionPoint,
   - potpiši CA privatnim ključem (iz keystore-a),
   - snimi EE sertifikat (vidi 2.7 — Student 2; do tada: čuvaj PEM u bazi i u keystore kao `TrustedCertificateEntry`),
   - vrati sertifikat (PEM) za preuzimanje.

2. `CsrController.uploadCsr` → pozovi `issueFromCsr`, vrati `CertificateResponse`/PEM (umesto samo `CsrInfoDto`).

3. Frontend `csr-upload.component`: nakon upload-a + izbora CA + trajanja → prikaži izdat sertifikat, dugme „Preuzmi“.

---

## 2.11 — Šabloni (Templates) 🔴 (NIJE RAĐENO)

**Zahtev:** CA korisnik kreira šablon koji definiše ekstenzije/politiku; pri izdavanju, na osnovu izabranog issuera, forma se auto-popunjava predefinisanim ekstenzijama. Polja šablona: Naziv, CA issuer, CN regex (validacija), SAN regex, TTL (max trajanje), Key Usage (default), Extended Key Usage (default). Korisnik može da odstupi od šablona ali **ukupan skup ekstenzija ne sme da pređe politiku CA** koji potpisuje.

### Kako implementirati

1. **Entitet `CertificateTemplate`**:
   ```java
   @Entity class CertificateTemplate {
     Long id;
     String name;
     @ManyToOne Certificate caIssuer;     // CA na koji se odnosi
     @ManyToOne User owner;               // CA korisnik koji ga je napravio
     String cnRegex;                      // npr. .*\.ftn\.com
     String sanRegex;
     Integer maxTtlDays;                  // maksimalno trajanje
     String defaultKeyUsage;              // npr. "digitalSignature,keyEncipherment"
     String defaultExtendedKeyUsage;      // npr. "serverAuth,clientAuth"
   }
   ```
   Repo + DTO-ovi.

2. **`TemplateController`** (`/api/templates`), `@PreAuthorize` da je CA korisnik/ADMIN:
   - `POST /api/templates` — kreiranje (samo za svoje CA sertifikate).
   - `GET /api/templates?caId=...` — šabloni vezani za dati issuer.
   - `GET /api/templates/{id}`, `PUT`, `DELETE`.

3. **Validacija pri izdavanju** (u 2.5/2.8): ako je prosleđen `templateId`:
   - CN novog sertifikata mora da prođe `cnRegex`, SAN mora da prođe `sanRegex`,
   - trajanje ≤ `maxTtlDays`,
   - ako korisnik ne pošalje KU/EKU, uzmi default iz šablona,
   - proveri da skup ekstenzija **ne prevazilazi politiku CA** (npr. EE ne može imati keyCertSign ako ga CA politika ne dozvoljava).

4. **Frontend**: 
   - stranica za kreiranje/listanje šablona (vidljivo CA korisniku/ADMIN-u),
   - u `certificate-form`: kad se izabere issuer, ponudi njegove šablone; izbor šablona auto-popuni polja (KU, EKU, TTL), uz mogućnost izmene.

---

## 2.12 — Password manager 🟢 (~85%)

**Urađeno:** entiteti `PasswordEntry` + `PasswordShare`; `PasswordManagerService.savePassword/getMyPasswords/deletePassword`; `PasswordManagerController` (`/api/password-manager`); frontend `password-list.component` sa: generisanje RSA-2048 para (Web Crypto), download privatnog/javnog ključa, registracija javnog ključa na server (`UserController` PUT `/me/public-key`), enkripcija lozinke javnim ključem na frontendu, dekripcija privatnim ključem lokalno. `CryptoService` kompletan (RSA-OAEP, SPKI/PKCS8 PEM).

**Fali / doraditi:**
- [ ] Ukloniti `// TODO (KT2)` komentare i potvrditi da je logika finalna (jeste funkcionalna).
- [ ] Metapodaci: `created_at`, `created_by` već postoje na entitetima — proveriti da se vraćaju u DTO (jesu u `mapToResponse`).
- [ ] (Spec) Password manager dostupan **samo EE korisnicima koji imaju svoj par ključeva** — dodati proveru/`@PreAuthorize("hasAuthority('END_ENTITY')")` na kontroleru i UI uslov.
- [ ] (Veza sa CSR) Spec predlaže da se za enkripciju koristi par ključeva generisan **kroz CSR** — trenutno se generiše zaseban par. Za maksimalne bodove povezati sa sertifikatom korisnika (opciono).

---

## 2.12.1 — Deljenje lozinki 🟠 (backend gotov, frontend fali)

**Zahtev:** korisnik deli lozinku sa drugim; lozinka se na frontendu **dekriptuje** (svojim privatnim ključem) pa **ponovo enkriptuje javnim ključem primaoca**; rezultat ide backendu i čuva se kao novi `PasswordShare`. Treba omogućiti preuzimanje javnih ključeva EE korisnika.

**Urađeno (backend):**
- `PasswordManagerService.sharePassword` + `POST /api/password-manager/{id}/share`.
- `UserController`: `GET /api/users` (lista sa javnim ključevima), `GET /api/users/{id}/public-key`.
- `CryptoService` ima sve potrebno (import public key, encrypt).

**Fali (frontend — glavni deo):**
- [ ] U `password-list.component` dodati dugme **„Podeli“** po unosu.
- [ ] Dijalog za deljenje:
  1. učitaj listu EE korisnika (`GET /api/users`) → padajući izbor primaoca,
  2. zahtevaj da je **privatni ključ već učitan** (treba za dekripciju),
  3. dekriptuj `encryptedPassword` ovog unosa privatnim ključem (`cryptoService.decryptPassword`),
  4. importuj javni ključ primaoca (`importPublicKey` iz `publicKeySpki`),
  5. re-enkriptuj plaintext javnim ključem primaoca (`encryptPassword`),
  6. `POST /api/password-manager/{id}/share` sa `{ targetUserId, encryptedPassword }`.
- [ ] `password-manager.service.ts`: dodati `sharePassword(id, body)` i `getUsers()`.
- [ ] (Provera) backend `sharePassword` — sprečiti duplo deljenje istom korisniku (update umesto insert) i deljenje samom sebi.

---

# OPŠTI ZAHTEVI (dele se, ali utiču na nas)

> Ovo nije eksplicitno ničija stavka u podeli; dogovoriti ko radi. Bitno za bodove i za KT/odbranu.

- [ ] **HTTPS** za sve servise — generisati sertifikat (idealno **našim PKI sistemom** = dodatni bodovi), konfigurisati Spring Boot (`server.ssl.*`) i Angular dev proxy/HTTPS. Trenutno je sve `http://localhost`.
- [ ] **Logging/Audit**: bezbednosni događaji (login uspeh/neuspeh, registracija, izdavanje/povlačenje sertifikata, deljenje lozinki, opoziv sesije) u standardizovanom formatu, sa dovoljно podataka za neporecivost; **rotacija logova** (Logback `RollingFileAppender`). Trenutno samo default slf4j.
- [ ] **Zaštita od napada**:
  - SQL injection — koristi se JPA/Spring Data (parametrizovani upiti) ✅, ne praviti ručne konkatenacije.
  - XSS — Angular podrazumevano escape-uje; izbegavati `[innerHTML]`/`bypassSecurityTrust*`. Validacija/sanitizacija svih unosa.
  - Provera ranjivosti zavisnosti (npr. `npm audit`, OWASP Dependency-Check / Snyk) — dodatni bodovi.
- [ ] **Kontrola pristupa na svakom endpointu** (i front i back). Trenutno `GET /api/certificates` je otvoren svim ulogovanim → mora po ulozi (vezano za 2.9, Student 2).

---

# 9. SPISAK ZA STUDENTA 2 (blokira ili dopunjuje naš rad)

> Ove stavke su Studentove 2 po podeli, ali neke su **preduslov** da naše (2.5/2.8/2.10) budu potpune i konzistentne.

### Blokirajuće / zajedničke (uraditi ranije)
1. **Refaktor čuvanja keystore lozinki (2.6).** Sada se enkriptovana lozinka drži na `User.keystorePasswordEncrypted` i prepisuje se pri svakom sertifikatu. Premestiti keystore-metapodatke (alias, putanja, enkriptovana lozinka) na nivo **sertifikata** (ili poseban `Keystore`/`CaKey` entitet), uz **jedan simetrični ključ po CA korisniku** (kako spec kaže). `KeyEncryptionService` je već spreman za to. — *Bez ovoga, izdavanje lanca (2.5) i potpisivanje iz CSR-a (2.8) ne mogu pouzdano da pronađu privatni ključ issuera.*
2. **Pregled i pristup sertifikatima sa kontrolom pristupa (2.9).** `GET /api/certificates` mora da filtrira:
   - ADMIN → svi,
   - CA korisnik → samo sertifikati iz svog lanca,
   - običan korisnik → samo svoji EE.
   Dodati i `GET /api/certificates/{id}/download`. — *Naše liste (za revoke i CSR) zavise od ovoga.*
3. **Čuvanje EE sertifikata (2.7).** EE bez privatnog ključa — `TrustedCertificateEntry` u keystore ili DER/PEM. — *Potrebno da 2.8 (izdavanje iz CSR-a) ima gde da snimi rezultat.*

### Samostalne (ne blokiraju nas direktno)
4. **Oporavak naloga (2.3).** „Zaboravljena lozinka“ — link na email, jednokratan i vremenski ograničen (isti mehanizam kao aktivacioni token; `TokenType` već ima vrednosti, `ActivationToken` se može reupotrebiti za `PASSWORD_RESET`). Endpoint za zahtev + endpoint za postavljanje nove lozinke.

---

# 10. Predloženi redosled rada (za nas, Student 1 i 3)

**Faza A (brzo, nezavisno):**
1. 2.1 dopuna (org obavezan).
2. 2.12.1 frontend deljenja lozinki (backend već gotov).
3. 2.4 Praćenje aktivnih tokena (nezavisno od keystore-a).

**Faza B (zahteva dogovor/oslonac na Studenta 2 — tačke 1–3 iznad):**
4. 2.5 Izdavanje Intermediate/EE + validacija issuera.
5. 2.8 Potpisivanje iz CSR-a (nadovezuje se na 2.5).
6. 2.10 Revocation + CRL (koristi izdavanje za CRLDistributionPoint).
7. 2.11 Šabloni (nadograđuje formu izdavanja).

**Faza C (zajedničko):**
8. HTTPS, logging/rotacija, provera ranjivosti.

---

*Napomena: KT1 zahtevi (registracija, login, init admin, root sertifikat, model password manager-a) i najveći deo KT2 (CAPTCHA, Web Crypto enkripcija, CSR parsiranje, pregled) su već pokriveni. Ostatak iz ovog dokumenta je za konačnu odbranu.*
