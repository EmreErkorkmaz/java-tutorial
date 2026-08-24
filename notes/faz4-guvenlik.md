# Faz 4 — Auth & güvenlik (tamamlandı)

Öğrenme notları + fullstack güvenlik teorik özeti. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** Spring Security filter chain, `AppUser` + BCrypt kayıt, JWT (HS256) login + resource server doğrulaması, `@PreAuthorize` ile rol bazlı yetki, OWASP gözüyle denetim (üç gerçek eksik kapatıldı: `/error`, CORS, rate limiting).

**Ölçüm:** HTTP Basic her istekte ~68 ms (DB + BCrypt) → JWT ~6.5 ms; BCrypt bedeli kaybolmadı, login'de bir kez ödeniyor (~65 ms).

**Kalıcı kararlar:** Stateless (`JSESSIONID` üretilmiyor), bu yüzden `csrf.disable()` güvenli — cookie oturumuna dönülürse geri açılmalı. Roller token'ın içinde taşınıyor: yetki değişikliği ancak yeniden login'de yansır (token ömrü 15 dk kadar gecikme).

**Tuzaklar:** `@EnableMethodSecurity` yoksa `@PreAuthorize` **sessizce** yok sayılır. `NimbusJwtEncoder` header verilmezse RS256 varsayar. Controller içinde oluşan hata filtre zincirinden geçerken 401'e dönüşüp kök nedeni sakladı.

**Sonraki faza taşınan bağlam:** Rate limiting uygulama içinde yazıldı ve **yanlış katman** olduğunun notu düşüldü (instance başına ayrı, restart'ta sıfırlanır) — Faz 8.3'te gateway'e taşınacak. Refresh token ve RS256/JWKS teoriye bırakıldı.

---

## Faz 4 — Auth & güvenlik

**Karar (2026-08-08):** auth önce `product-service` içine yazılacak, ayrı servise çıkarma Faz 6'ya bırakıldı. Sebep: Spring Security tek başına büyük bir konu, üstüne servisler arası token taşımayı eklemek ikisini birden bulanıklaştırır. Ayırma işlemi Faz 6'da asıl mimari soruyu somutlaştıracak: token'ı diğer servisler nasıl doğrulayacak — simetrik sır (HS256) mı, asimetrik imza + JWKS (RS256) mi, gateway devreye girmeli mi. Not: production'da auth genelde elle yazılmaz, IdP (Keycloak/Auth0/Cognito) kullanılır; biz mekanizmayı anlamak için yazıyoruz.

- [x] Spring Security'ye giriş — filter chain, `SecurityContextHolder`, 401 (kimlik yok) vs 403 (yetki yok)
  - Bağımlılık eklenince davranış tek satır kod yazmadan değişti → **auto-configuration**: her jar'ın içindeki `META-INF/spring/...AutoConfiguration.imports` dosyası + `@ConditionalOnClass` / `@ConditionalOnMissingBean`. Flyway'in neden `flyway-core` tek başına çalışmadığının da cevabı bu
  - Kendi `SecurityFilterChain` bean'imiz varsayılanı geri çektiriyor; `GET /api/products/**` ve `/api/auth/**` açık, gerisi kimlik istiyor
  - `csrf.disable()` stateless API'de güvenli (tarayıcı `Authorization` başlığını kendiliğinden eklemez); cookie oturumuna dönülürse tekrar açılmalı
  - `@WebMvcTest` dilimi `@Configuration` sınıflarını yüklemez → gerçek kuralları test etmek için `@Import(SecurityConfig.class)`, kimlikli test için `@WithMockUser`
- [x] `AppUser` entity + kayıt endpoint'i + BCrypt
  - Tablo `app_user` (`user` Postgres'te ayrılmış kelime); roller `@ElementCollection` ile `user_role` tablosunda, `@Enumerated(STRING)` (ordinal asla yazılmaz)
  - Entity'ye `UserDetails` implement ettirilmedi — `AppUserDetailsService` adaptör olarak çeviriyor, domain framework'e bağlanmıyor
  - `POST /api/auth/register` → 201 / tekrar → 409 / kısa şifre → 400; yanıtta hash yok. Kayıt kontrolü atomik değil, gerçek koruma `UNIQUE` kısıtı (`DataIntegrityViolationException` yakalanıyor)
  - Yaşanan bug: `.disabled(user.isEnabled())` → `!` eksikti, hesap devre dışı sayılıp doğru şifre bile 401 dönüyordu
- [x] **JWT** — login endpoint'i + token üretimi + resource server ile doğrulama; HTTP Basic kaldırıldı
  - Ölçüm: HTTP Basic her istekte **~68 ms** (DB + BCrypt), JWT ile **~6.5 ms** (imza doğrulama ~1 ms). BCrypt bedeli kaybolmadı, login'de **bir kez** ödeniyor (~65 ms)
  - Kütüphane: `spring-boot-starter-oauth2-resource-server` (Nimbus). JWT ayrıştırma/imza doğrulama elle yazılmaz — kripto kütüphane işidir
  - `JwtEncoder`/`JwtDecoder` HS256 simetrik anahtarla; `SessionCreationPolicy.STATELESS` (artık `JSESSIONID` üretilmiyor, doğrulandı)
  - `JwtAuthenticationConverter` şart: varsayılan `scope` claim'ini okur ve `SCOPE_` öneki ekler; bizim claim `roles` ve değerler zaten `ROLE_` önekli
  - Token payload'ı **şifreli değil**, sadece base64 — anahtarsız okuduk. İmza bütünlük garantisi verir, gizlilik değil. Token'a gizli bilgi konmaz
  - Doğrulanan: token yok → 401, tek karakteri değişmiş token → 401, Basic → 401
  - **Yaşanan iki hata:** (1) `NimbusJwtEncoder` header verilmezse RS256 varsayar → simetrik anahtarla `Failed to select a JWK signing key`; `JwsHeader.with(MacAlgorithm.HS256)` şart. (2) Bu hata controller içinde oluşup filtre zincirinden geçerken **401'e** dönüştü — status kodu yanılttı, kök neden yalnızca loglarda görünüyordu
  - Spring Security 7, şifreyle doğrulanan oturumlara `FACTOR_PASSWORD` yetkisi de ekliyor; token'a sadece `ROLE_` önekli olanlar filtrelenerek yazılıyor
  - `@WebMvcTest` bağımlılık kaskadı: `@Import({SecurityConfig, JwtConfig})` + `@MockitoBean UserDetailsService`. Kaskad büyüdüyse sinyal tasarımda — `SecurityConfig` kural/kimlik-doğrulama olarak ikiye ayrılabilir
- [x] **Rol bazlı yetkilendirme** — `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` (create/update/delete); GET herkese açık
  - Doğrulandı: tokensiz → 401, `ROLE_USER` → **403**, `ROLE_ADMIN` → 201/204. 401 = kimlik yok, 403 = kimlik var yetki yok
  - `@EnableMethodSecurity` olmadan `@PreAuthorize` **sessizce yok sayılır**
  - `hasRole('ADMIN')` başa `ROLE_` ekler; `hasAuthority('ROLE_ADMIN')` ile aynı şey. `@WithMockUser(roles = "USER")` de öneki kendisi ekler — `ROLE_` yazılırsa `ROLE_ROLE_USER` olur
  - İlk admin elle bootstrap edildi (`INSERT ... SELECT` ile `user_role`'a satır). Roller token'ın içinde taşındığı için yetki değişikliği **ancak yeniden login'de** yansır — token ömrü kadar (15 dk) gecikme
  - URL seviyesi kural (`SecurityConfig`) vs metot seviyesi (`@PreAuthorize`) karşılaştırması yapıldı; ince kurallar (örn. "kaydın sahibi veya admin") ancak metot seviyesinde yazılabilir
- Not (frontend köprüsü): Bearer token JS'te saklanır ve elle eklenir → XSS'e açık, CSRF'e kapalı. `httpOnly` cookie'yi tarayıcı otomatik ekler → XSS'e kapalı, CSRF'e açık (o durumda `csrf.disable()` geri alınmalı)
- [x] **OWASP temelleri** — kod güvenlik gözüyle denetlendi, üç gerçek eksik kapatıldı
  - **İyi durumdaydı:** SQL injection (JPA parametrik), mass assignment (DTO), BCrypt, yanıtta hash yok, hata gövdesinde stack trace yok, `X-Frame-Options`/`nosniff` başlıkları var
  - **Bulgu 1 — `/error` korumalıydı:** hata oluşunca servlet container `/error`'a **yeniden dispatch** ediyor ve bu geçiş filtre zincirinden tekrar geçiyor; kuralda açık olmadığı için anonim kullanıcı 400 yerine **401** alıyordu. `.requestMatchers("/error").permitAll()` ile çözüldü (doğrulandı: artık 400)
  - **Bulgu 2 — CORS yoktu:** preflight 401 dönüyordu. `CorsConfigurationSource` + `.cors()` eklendi. Yaşanan hata: kalıp `"*/**"` yazılmıştı, hiçbir yola uymaz → `"/**"`. Doğrulandı: izinli origin 200 + `Access-Control-*` başlıkları (+ `Vary: Origin`, cache'lerin yanlış origin'e cevap servis etmesini engeller), izinsiz origin 403
  - **Bulgu 3 — rate limiting yoktu:** 10 hatalı login 1 saniyede geçiyordu. `LoginAttemptService` (kullanıcı bazlı, 5 deneme / 1 dk). Doğrulandı: 6. deneme 429, kilitliyken doğru şifre bile 429, başka kullanıcı etkilenmiyor
    - Takas: kullanıcı bazlı kilit, o kullanıcıya karşı **DoS aracına** dönüşebilir (saldırgan bilerek kilitler). Gerçek çözüm IP+kullanıcı kombinasyonu, üstel gecikme veya CAPTCHA
    - Katman notu: uygulama içi sayaç instance başına ayrıdır ve restart'ta sıfırlanır — doğru yer gateway/proxy veya Redis gibi paylaşılan store
  - HSTS eklenmiyor çünkü sadece HTTPS üzerinden eklenir (TLS proxy'de sonlanacak); CSP ağırlıklı olarak HTML sunan uygulamalar için

**Faz 4 tamamlandı.** 11 test yeşil.

### Güvenlik — fullstack teorik özet (mülakat notu)

Kısa tanımlar; her biri projede karşılığıyla birlikte görüldü.

**Kimlik ve yetki**
- **Authentication** (kimlik doğrulama) = "sen kimsin". **Authorization** (yetkilendirme) = "bunu yapabilir misin". Karşılıkları **401** ve **403**
- **Session vs token:** session sunucuda durum tutar (iptal kolay, ölçeklemek için paylaşılan store gerekir), token stateless'tır (ölçeklenir, iptal edilemez → kısa ömür + refresh token)
- **Bearer token** = "taşıyan kimse odur". Çalınırsa süresi bitene kadar saldırgan o kullanıcıdır

**Kriptografi ayrımları** (sık karıştırılır)
- **Encoding** (base64) = geri döndürülebilir, güvenlik değil, sadece taşıma formatı. JWT payload'ı budur
- **Hashing** (BCrypt) = tek yönlü, geri döndürülemez. Şifre saklamak için. Şifre hash'i **kasten yavaş** olmalı (BCrypt/Argon2); SHA-256 hızlı olduğu için şifreye uygun değil
- **Encryption** = anahtarla geri döndürülebilir. Taşınan/duran veriyi gizlemek için
- **Signature** (HMAC/RSA) = bütünlük + kaynak doğrulama; **gizlilik sağlamaz**
- **Salt** = her şifreye eklenen rastgele değer; aynı şifrelerin aynı hash'i üretmesini ve rainbow table saldırısını engeller (BCrypt salt'ı hash'in içinde taşır)
- **Simetrik (HS256)** tek anahtar hem imzalar hem doğrular; **asimetrik (RS256)** private key imzalar, public key doğrular → çok servisli mimaride gerekli olan budur

**Tarayıcı tarafı**
- **CORS** = tarayıcının, bir sayfadaki JS'in başka origin'den gelen **yanıtı okumasını** kısıtlaması. Sunucu tarafı erişim kontrolü **değil** — `curl`/Postman/mobil onu hiç uygulamaz
- **Preflight** = "simple request" olmayan istekler için tarayıcının önce `OPTIONS` ile izin sorması. `Authorization` başlığı veya `application/json` gövde eklendiği an tetiklenir. `Access-Control-Max-Age` bu cevabı cache'ler; reddedilirse asıl istek hiç gönderilmez
- **CSRF** = tarayıcının kimliği (cookie) **otomatik eklemesinden** doğar. `Authorization` başlığı otomatik eklenmediği için token tabanlı API'da yapısal olarak yoktur → `csrf.disable()` bu yüzden güvenli. Cookie'ye geçilirse geri açılmalı
- **XSS** = sayfaya saldırgan JS enjekte edilmesi; `localStorage`'daki token'ı okur. `httpOnly` cookie okunamaz — ama o da CSRF'i geri getirir (takas)
- **SameSite** cookie özniteliği modern tarayıcılarda CSRF'i büyük ölçüde azaltır

**Sunucu tarafı**
- **SQL injection:** JPA/prepared statement parametreyi veri olarak gönderir, SQL olarak yorumlamaz. `@Query` içinde string birleştirirsen açık geri gelir
- **Mass assignment:** istemcinin gönderdiği alanların doğrudan entity'ye yazılması. DTO + elle alan kopyalama bunu keser (body'de `id` göndermeyi deneyip yok sayıldığını doğruladık)
- **Rate limiting:** kaba kuvvet ve credential stuffing'e karşı. Kullanıcı bazlı sayaç tek hesaba yoğunlaşan saldırıyı, IP bazlı sayaç dağıtık denemeyi durdurur. Doğru katman genelde uygulama değil, gateway/proxy (uygulama içi sayaç instance başına ayrı çalışır ve restart'ta sıfırlanır)
- **Güvenlik başlıkları:** `X-Content-Type-Options: nosniff` (tarayıcı content-type tahmin etmesin), `X-Frame-Options: DENY` (clickjacking), `Strict-Transport-Security` (sadece HTTPS'te eklenir), `Content-Security-Policy` (XSS azaltma, ağırlıklı olarak HTML sunan uygulamalar için)
- **Hata sızıntısı:** stack trace, SQL metni veya iç yol bilgisi istemciye dönmemeli (`server.error.include-stacktrace`)
- **Kullanıcı keşfi (user enumeration):** "kullanıcı yok" ile "şifre yanlış" farklı cevaplanmamalı — login'de ikisine de aynı 401 dönüyoruz

**TLS**
- Üç garanti: gizlilik, bütünlük, sunucu kimliği (sertifika). Asimetrik kripto sadece ortak simetrik anahtarı belirlemek için kullanılır, veri simetrik şifreyle taşınır
- Yalnızca **yoldaki** veriyi korur; sunucuda çözülür. Token URL'e konursa TLS'e rağmen erişim loglarına düz metin yazılır → token her zaman header'da
- Production'da TLS genelde load balancer / ingress'te sonlanır, uygulama iç ağda düz HTTP konuşur
