# Roadmap & Checklist

Java + Spring Boot ve genel backend/mimari öğrenme sürecinin ilerleme takibi.
Yeni bir oturuma başlarken bu dosya referans alınabilir — bağlamı baştan anlatmaya gerek kalmaz.

**Hedef:** mid-level fullstack rolüne geçiş, orta vadede solutions architecture yönü.
**Yaklaşım:** Java'yı ve backend/altyapı konularını ayrı ayrı teorik olarak değil, **tek bir proje (`product-service`) üzerinde birlikte** ilerletmek. Her faz hem yeni bir Java/Spring yeteneği hem bir backend kavramı ekler.

**Etiketler:**
- **[uygulama]** — kod yazılıp çalıştırılacak, projeye gerçekten entegre edilecek
- **[teori]** — öğrenme amacı için uygulaması overkill; anlatım + kısa quiz ile kavram düzeyinde bilinecek, kod yazılmayacak
- **[opsiyonel]** — istenirse uygulamaya çevrilebilir, zorunlu değil

---

## Faz 0 — Ortam (tamamlandı)

- [x] IntelliJ IDEA 2026.2 + Claude Code JetBrains plugin kurulumu (`Cmd+Esc` ile açılıyor)
- [x] `claude` CLI global kurulum (`npm install -g @anthropic-ai/claude-code`)
- [x] Maven wrapper ile projeyi ayağa kaldırma (`./mvnw spring-boot:run`)

## Faz 1 — Spring Boot temelleri & katmanlı mimari

Stack: Spring Boot 4.1.0, Java 21, Maven, H2 (in-memory), Spring Data JPA

- [x] `model.Product` — JPA entity (id, name, price: BigDecimal)
- [x] `repository.ProductRepository` — JpaRepository + derived query methods (`findByName`, `findByNameContaining`, `findByPriceLessThan`)
- [x] `service.ProductService` — constructor injection, CRUD metodları
- [x] `controller.ProductController` — REST endpoints (`/api/products`), curl ile end-to-end doğrulandı
- [x] `application.properties` — H2 + `/h2-console` konfigürasyonu
- [x] `exception.ProductNotFoundException` + `GlobalExceptionHandler` (`@RestControllerAdvice`) — 404 düzgün JSON hata objesi dönüyor
- [x] `dto.ProductRequest` / `dto.ProductResponse` — record tabanlı DTO'lar, entity ↔ API contract ayrımı
- [x] Controller DTO'lara geçirildi — entity artık dışarı sızmıyor; doğru status kodları (POST 201, DELETE 204); curl ile doğrulandı: body'de `id` gönderilse bile yok sayılıyor
- [x] **Input validation** — `spring-boot-starter-validation`, DTO'da `@NotBlank`/`@NotNull`/`@Positive`, controller'da `@Valid`; `MethodArgumentNotValidException` handler'ı ile alan bazlı 400 response
- [x] `api.http` — IntelliJ HTTP Client istek koleksiyonu (Postman yerine; istekler repo'da versiyonlanıyor, response handler ile id zincirleme)
- [ ] Hata response body'sini `Map<String, Object>` yerine tipli bir `record`'a çevir

**Kazanılan kavramlar:** DI/IoC container, annotation-driven konfigürasyon, katmanlı mimari (repository/service/controller sorumluluk ayrımı), ORM mapping, merkezi hata yönetimi, DTO pattern, `record`, `Optional`, `final`, `static` factory method, generics, Stream API (lazy + tek geçişli pipeline), method reference

**Yan konular (mülakat notu):** JVM'de derleme akışı — `javac` → bytecode → JVM; interpreter + JIT, tiered compilation (C1/C2), inlining & escape analysis, spekülatif optimizasyon ve deoptimization, warm-up etkisi, JIT vs AOT (GraalVM Native Image) trade-off'u

## Faz 2 — Test kültürü

- [x] Unit test: `ProductService` — repository mock'lu (JUnit 6 + Mockito 5), Spring context yok → 3 test 0.08 s
- [x] Web layer test: `ProductController` — `@WebMvcTest` + `MockMvc`, service `@MockitoBean` ile mock'lu; status kodu + JSON contract + validation 400'ü assert ediyor
- [x] Integration test: gerçek DB ile tüm stack — `ProductServiceIntegrationTest` (`@SpringBootTest` + Testcontainers), transaction rollback davranışını gerçek Postgres kısıtı (`CHECK price > 0`) tetikleyerek doğruluyor
- [ ] Test piramidi: hangi katmanda ne test edilir — pratikte gözlendi (mock'lu 3 test 0.08 s vs context ayağa kalkan 1 test ~1 s), teorisi ayrıca konuşulacak

**Bu sürüme özgü tuzaklar (eski tutorial'lar yanlış):** `@MockBean` kaldırıldı → `@MockitoBean`; `@WebMvcTest` paketi `org.springframework.boot.webmvc.test.autoconfigure` oldu. Test bağımlılıkları `<scope>test</scope>` olduğu için test dosyaları **`src/test/java`** altında olmalı, `src/main` altında derlenmez.

**Not:** Playwright/Cypress/RTL deneyimi burada avantaj — kavramlar tanıdık, sadece araçlar (JUnit/Mockito/MockMvc) yeni.

## Faz 3 — Gerçek veritabanı & DB derinliği

- [x] H2 → **PostgreSQL** geçişi, Postgres'i **Docker** ile ayağa kaldırma (postgres:18-alpine, named volume, healthcheck — compose sonradan kök seviyeye taşındı: [compose.yaml](compose.yaml))
- [x] `ddl-auto=create-drop` yerine **Flyway** ile şema migration'ları — `V1__create_product_table.sql`, `ddl-auto=validate`, `flyway_schema_history` tablosu; DB seviyesinde `NOT NULL` + `CHECK (price > 0)` ile katmanlı savunma (API baypas edilerek doğrulandı)
- [x] İlişkiler — `Category` entity + `@ManyToOne(fetch = LAZY)` + `@JoinColumn`; `V2__add_category.sql` (dolu tabloya zorunlu FK ekleme: nullable ekle → backfill → `SET NOT NULL`; FK index'i Postgres'te elle açılır)
- [x] **N+1 query problemi** — logda ölçüldü: 20 ürün / 20 farklı kategori → **21 sorgu**. Çözüm `@EntityGraph(attributePaths = "category")` ile **1 sorguya** düştü (join'li tek sorgu, doğrulandı).
  - Önemli ayrıntı: sorgu sayısı ürün sayısı değil, **farklı ilişkili kayıt sayısı** + 1 (persistence context aynı entity'yi tekrar sorgulamaz) — bu yüzden az veriyle çalışan dev ortamında problem görünmez
  - Seçenek karşılaştırması: `EAGER` (asla — global ve N+1'i zaten çözmez), `JOIN FETCH` (açık, JPQL), `@EntityGraph` (deklaratif, seçilen), `@BatchSize` (koleksiyonlar için `IN` sorgusu), DTO projection (salt okunur listelerde en hızlısı)
  - `@EntityGraph` sorgu bazlıdır: `findByName` gibi diğer metotlar bilinçli olarak N+1 üretmeye devam ediyor
- [x] Index'ler, `EXPLAIN ANALYZE` ile query planı okuma — `V3__add_product_name_index.sql`, 200k satırla ölçüldü
  - `Seq Scan` → `Index Scan` farkı görüldü; planlayıcı **maliyet tabanlı**, index'in varlığı kullanılacağını garanti etmez
  - Seçicilik (selectivity) kavramı: düşük seçicilikli kolonda index genelde israf. Ama ölçümde category_id sorgusu yine de index scan seçti — sebep fiziksel korelasyon (sıralı insert) + tablonun tamamen cache'te olması (`Buffers: shared hit`, `read=0`). Ezber kural yok, `EXPLAIN ANALYZE` ile ölçülür
  - `ANALYZE` ile istatistik tazeleme; index maliyeti = her INSERT/UPDATE'te ek yazma + disk + bloat/VACUUM
  - Production notu: büyük tabloda `CREATE INDEX CONCURRENTLY` (normal `CREATE INDEX` tabloyu yazmaya kilitler)
- [x] Transaction yönetimi — sınıf seviyesinde `@Transactional(readOnly = true)`, yazma metotlarında `@Transactional`; yetim kayıt senaryosu integration testiyle önce kanıtlandı sonra çözüldü
  - `@Transactional` sınırı yukarı taşır: her `save()` kendi transaction'ı yerine servis metodu tek transaction olur
  - Rollback varsayılanı: **sadece unchecked** exception'larda; checked exception'da commit edilir (`rollbackFor` ile değişir)
  - Self-invocation tuzağı: aynı sınıf içinden `this.method()` çağrısı proxy'yi baypas eder, annotation hiç çalışmaz
  - Import tuzağı: `org.springframework...Transactional` (jakarta olanında `readOnly`/`propagation` yok)
- [x] **OSIV kapatıldı** (`spring.jpa.open-in-view=false`) — Hibernate oturumu artık transaction ile birlikte kapanıyor, isteğin sonuna kadar açık kalmıyor
  - Kapatınca `GET /api/products/{id}` 500 verdi (`LazyInitializationException`): OSIV, `findById`'de eksik olan fetch planını bugüne kadar maskeliyormuş. `findById`'ye de `@EntityGraph` eklenerek çözüldü
  - Kazanım: DB bağlantısı isteğin sonuna kadar tutulmuyor; veri yükleme kararı servis katmanında bilinçli veriliyor
- [ ] Isolation level'lar ve concurrency (optimistic locking / `@Version`) — **[teori]**
- [x] Pagination & sorting — `Page<Product> findAll(Pageable)` (graph'li), controller'da `@PageableDefault`; `?page=&size=&sort=price,desc` çalışıyor, `api.http`'ye örnek istek eklendi
  - API contract kırıldı (dizi → `content`/`totalElements` nesnesi), `ProductControllerTest` buna göre güncellendi — öngörülen churn
  - Notlar: `OFFSET` derin sayfada yavaşlar (atılan satırları da okur) → gerçek çözüm keyset/cursor pagination; `spring.data.web.pageable.max-page-size` ile istemcinin istediği sayfa boyutu sınırlanmalı; fetch join + pagination `@ManyToOne`'da güvenli ama koleksiyonlarda bozulur
- [x] **Testcontainers** — `TestcontainersConfiguration` + `@ServiceConnection`; test kendi `postgres:18-alpine` container'ını başlatıyor, Flyway migration'ları her koşumda sıfırdan uygulanıyor (migration'ın kendisi de test edilmiş oluyor). Testler artık lokal Postgres'ten bağımsız, CI'da da çalışır.
  - Sürüm notu: Testcontainers 2.x'te modül adları `testcontainers-postgresql` / `testcontainers-junit-jupiter` oldu ve `PostgreSQLContainer` artık generic değil (`<?>` yazılmıyor) — eski örnekler uymuyor.

**Kazanılan kavramlar:** SQL/ilişkisel modelleme, migration disiplini, ORM'in gizli maliyetleri, transaction semantiği, index stratejisi

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
- [ ] Refresh token (access token 15 dk; iptal edilebilirlik için sunucuda saklanan refresh token) — **[teori]** veya opsiyonel uygulama
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

## Faz 5 — Docker & DevOps

- [x] Uygulamayı Dockerize et — multi-stage `Dockerfile`, 126 MB runtime image
  - Katman sıralaması: önce `pom.xml` + `dependency:go-offline`, kaynak **sonra** → kod değişince bağımlılıklar yeniden inmiyor
  - Runtime aşamasında sadece JRE + jar; root olmayan kullanıcı (`USER app`)
- [x] `compose` ile app + Postgres birlikte — `docker compose up -d --build`
  - Container içinde `localhost` = container'ın kendisi. DB'ye compose servis adıyla ulaşılıyor: `postgres:5432` (compose kendi DNS'ini kurar)
  - Konfigürasyon env variable ile geliyor (`SPRING_DATASOURCE_URL` → `spring.datasource.url`) — repo'ya dokunmadan ortam değiştirmenin yolu
  - `depends_on: condition: service_healthy` ilk gün yazdığımız healthcheck'i kullanıyor
  - `docker compose down` sonrası veri korundu (named volume) — doğrulandı
- [x] Ortam bazlı konfigürasyon — env variable'lar `application.properties`'i eziyor (`SPRING_DATASOURCE_URL` → `spring.datasource.url`); compose bunu kullanıyor
- [x] **CI pipeline** (GitHub Actions) — her push/PR'da `mvnw clean verify` + `docker build`; ilk çalıştırmada geçti
  - Testcontainers CI'da ekstra kurulum istemiyor (runner'da Docker hazır) — testleri kendi DB'sini açacak şekilde kurmanın karşılığı
  - Öğrenilen: `git add` bulunduğun dizine göredir; `.github/` kökten eklenmeliydi
- [ ] Image registry'e push, basit bir yere deploy (Railway / Fly.io / Render gibi)
- [x] **Actuator** — `/actuator/health` + compose healthcheck; sadece `health,info` expose ediliyor (diğer endpoint'ler bilgi sızdırır)
  - `liveness` = "öldür ve yeniden başlat", `readiness` = "trafiği kes ama bekle". Load balancer/K8s Service readiness'a bakar
- [ ] Prometheus + Grafana dashboard — **[opsiyonel]**
- [ ] **[teori]** Kubernetes temelleri — pod/service/deployment kavramları, neden ve ne zaman gerekir (mid-level fullstack için uygulaması overkill)

## Faz 6 — İkinci servis & senkron iletişim

Hedef repo yapısı (her servis kendi Maven projesi, bağımsız deploy edilebilir — mikroservisin asıl noktası bu):

```
java-tutorial/
├── product-service/      # mevcut: ürün kataloğu + stok
├── order-service/        # yeni: sipariş oluşturma
├── notification-service/ # sonra: event tüketici
├── api-gateway/          # sonra
└── docker-compose.yml    # hepsi + Postgres + queue tek komutla
```

- [ ] **[teori]** Monolit vs mikroservis trade-off'ları — ne zaman hangisi, mikroservisin gizli maliyetleri (network, veri tutarlılığı, operasyonel yük)
- [x] **`order-service` iskeleti** — 8081, kendi `orderdb`'si, Flyway V1+V2, `ddl-auto=validate` geçiyor
  - Domain: `Order` (başlık) + `OrderItem` (satırlar), `@OneToMany(cascade = ALL, orphanRemoval = true)`; satırlar yalnızca `order.addItem()` ile doğabilir (package-private constructor)
  - İade akışı: `OrderStatus` (CREATED/RETURNED), kural `Order.markReturned()` içinde — durumu tutan sınıf kuralı da tutar
  - `product_id` foreign key **değil** (tablo başka serviste); `order_item.order_id` foreign key **olabiliyor** (aynı servis) — servis sınırı = bütünlük sınırı
  - Fiyat ve ürün adı snapshot olarak saklanıyor: ürün fiyatı değişince geçmiş siparişler değişmemeli
  - JWT `sub` username'den **user id**'ye çevrildi; `username` yalnızca görüntü snapshot'ı
  - V1 düzenlenmedi, V2 eklendi (uygulanmış migration'ın checksum'ı tutulur)
- [x] **`order-service` → `product-service` senkron REST çağrısı** — `RestClient`, `client/` paketinde `ProductClient`
  - Fiyat ve ürün adı sunucudan geliyor; istemci yalnızca `productId` + `quantity` gönderebiliyor (metot imzası güvenlik sınırı)
  - Uzak 404 → kendi domain exception'ımıza çevriliyor (`ProductNotFoundException` → 400); bağlantı hatası → 503
  - `create` bilinçli olarak `@Transactional` **değil**: uzak çağrı açık transaction içinde DB bağlantısını rehin alırdı
  - ~~Bilinen eksik: satır başına bir HTTP çağrısı~~ → **çözüldü** (aşağıda)
- [x] **Dayanıklılık** — timeout + retry + bulkhead. Ölçüldü:
  - Timeout: `JdkClientHttpRequestFactory` (connect 2s, read 3s). **Zorunlu** — yoksa yavaş bağımlılık thread'leri tüketir (cascading failure)
  - `@Retryable` (Spring Framework 7, harici kütüphane yok): `includes` ile sadece bağımlılık arızası, `maxRetries=2`, `multiplier=2.0`, `jitter` (thundering herd'ü önler)
  - `@ConcurrencyLimit(20)` = bulkhead: yavaş bağımlılık en fazla 20 thread tutabilir
  - `@EnableResilientMethods` olmadan ikisi de **sessizce yok sayılır**
  - **Ölçüm:** servis kapalıyken 14 ms → 782 ms (3 deneme + 200/400 ms gecikme). Yani retry'ın bedeli kalıcı kesintide gecikme ve 3x yük → circuit breaker'ın gerekçesi bu
  - Kısmi bozulma doğrulandı: `product-service` kapalıyken sipariş **oluşturulamıyor** ama liste/okuma ve health çalışmaya devam ediyor
- [x] **Dağıtık N+1 çözüldü** — `GET /api/products/by-ids?ids=...` toplu endpoint. Ölçüldü: 5 satırlı sipariş **5 HTTP çağrısı + 5 SQL → 1 + 1**
  - Aynı problem, bir katman yukarıda: Faz 3'te `@EntityGraph` ile çözdüğümüz N+1'in ağ üzerindeki hali. Kalıp aynı — döngü içinde tek tek sorma, hepsini bir kere iste
  - API tasarım kararı: `GET ?ids=` vs `POST /search`. İkisi de sektörde yaygın (Spotify `?ids=` / Elasticsearch `_mget` body). Seçim kriterleri: id sayısı (URL ~2000 karakter sınırı), HTTP cache isteniyor mu, public API mi iç servis mi. **GET seçildi**, ek gerekçe: `@Retryable` açık ve GET spec gereği safe+idempotent
  - Ayrı path (`/by-ids`) çünkü mevcut koleksiyon endpoint'i `Page` dönüyor; aynı URL'in bazen `Page` bazen `List` dönmesi istemci için kötü
  - Eksik id'ler yanıttan **düşürülüyor**, tüm istek reddedilmiyor; çağıran farkı görüp kendi hatasını üretiyor. `MAX_BATCH_SIZE=100` sınırı (doğrulandı: 101 id → 400)
  - `ParameterizedTypeReference` gerekli: generics runtime'da silinir, `List.class` ile Jackson eleman tipini bilemez
- [x] `order-service` testleri — `OrderServiceTest` (uzak çağrı mock'lu: fiyatın istemciden değil servisten geldiğini, iki kez iadenin engellendiğini, başkasının siparişinin 404 davrandığını doğruluyor) + `OrderControllerTest` (`@WebMvcTest` + `jwt()` post-processor: anonim 401, kimlikli 201, boş items 400)
  - `jwt()` gerçek imza üretmeden `SecurityContext`'e çözülmüş token koyar — test kriptografiyi değil controller'ın kimliği doğru okumasını doğrular
- [x] **Tek compose ile tüm yığın** — kök `compose.yaml`: postgres + product (8080) + order (8081), üçü de healthcheck'li
  - Tek Postgres **instance**, iki **database** (`productdb`, `orderdb`). Database-per-service veritabanı seviyesinde sağlanıyor; production'da genelde ayrı instance olur (yük/kesinti/ölçekleme/yedekleme ayrışsın diye)
  - Bilinen zayıflık: iki servis de aynı `product` DB kullanıcısını kullanıyor. Tam izolasyon ayrı kullanıcı ister — sınır iki katmanlı olmalı (ayrı database + ayrı kullanıcı)
  - `docker/init-db.sql` `orderdb`'yi otomatik oluşturuyor (yalnızca boş volume'de çalışır) → kurulum tekrarlanabilir, doğrulandı: sıfırdan ayağa kalkıp 3 migration uygulandı
  - Volume adı **proje adıyla** öneklenir (`java-tutorial_product-pgdata`); compose dosyasını taşımak yeni volume demek → eski veri gelmez
  - `container_name` çakışmaya yol açtı (eski projeden kalan container aynı adı tutuyordu) ve `--scale`'i imkânsız kılar; gerçek projede kullanılmaz, burada `docker exec product-db` kolaylığı için tutuldu
  - `PRODUCT_SERVICE_BASE_URL` env variable'ı `@Value("${product-service.base-url}")` ile eşleşti (relaxed binding `@Value`'da da çalıştı) — adres kod değil konfigürasyon
  - Build yavaşlığı: container host'un `~/.m2`'sini görmez, bağımlılıklar sıfırdan iner. Çözüm `RUN --mount=type=cache,target=/root/.m2` (henüz uygulanmadı)
- [ ] **[teori]** Circuit breaker — Spring core'da yok (Resilience4j gerekir). CLOSED → OPEN → HALF_OPEN; retry geçici hatayı, breaker kalıcı kesintiyi çözer
- [x] `order-service` Dockerfile + CI'a dahil edildi — workflow artık **matrix** ile iki servisi paralel build ediyor (`fail-fast: false`)
  - Yaşanan hata: Initializr'ın varsayılan `@SpringBootTest` testi DB istiyordu, CI'da Postgres yok → Testcontainers eklendi. Prensip: **test bağımlılığını kendi ayağa kaldırır**, ortamdan hazır bulmayı beklemez
  - `compose.yaml` CI'da kullanılmaz; o lokal geliştirme aracı. CI temiz makinede build+test yapar
- [x] **Token propagation** — `product-service`'te ürün okuma korumaya alındı, `ProductClient` gelen token'ı iletiyor
  - `RestClient.requestInterceptor` ile `SecurityContextHolder`'daki **doğrulanmış** token iletiliyor (ham header değil)
  - `SecurityContextHolder` **ThreadLocal**: `@Async`/executor ile başka thread'e geçen iş context'i göremez, token sessizce eklenmez → `DelegatingSecurityContext*` sarmalayıcıları
  - İki model karşılaştırıldı: **kullanıcı token'ı taşıma** (kullanıcı isteğinden doğan çağrılar) vs **service account** (cron, kuyruk tüketicisi). Propagation'da yetki kuralı aşağıdaki serviste kalır, service account'ta yetki mantığı dağılır
  - Hata eşlemesi: uzak 401/403 → `ProductAccessDeniedException` → **502**. Kullanıcı bizde yetkiliydi, arıza servisler arası atlamada; 401 dönmek "tekrar giriş yap" der ve hiçbir şeyi çözmez
  - İki ayrı çeviri sınırı: `onStatus` (gelen HTTP → Java istisnası), `@ExceptionHandler` (Java istisnası → giden HTTP). Ham `HttpClientErrorException`'ı sızdırmak hata yönetimini transport'a bağlar
- [x] **HS256'nın sınırı — canlı gösterildi.** `order-service`'e "doğrulasın diye" verilen sır ile `ROLE_ADMIN` token'ı üretildi; `emre` gerçek token'ıyla **403** alırken sahte token **201** aldı
  - **Simetrik imzada doğrulama yeteneği = üretme yeteneği.** Anahtarı paylaştığın her servis token basabilir; biri ele geçirilirse blast radius tüm sistem

- [ ] **[teori]** **RS256 + JWKS** — asimetrik imzaya geçiş (uygulanmadı; production'da IdP'nin işi)
  - Private key yalnızca üreticide (imzalar), public key dağıtılır (doğrular) → doğrulayan **üretemez**. Public key sızsa da değersiz
  - **JWKS**: üretici public anahtarı `/.well-known/jwks.json`'da yayınlar, doğrulayanlar çekip cache'ler. Kazanç: anahtar rotasyonunda servisleri yeniden deploy etmek gerekmez (`kid` header'ı hangi anahtar olduğunu söyler)
  - Spring tarafında karşılığı tek property: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` — decoder'ı Boot kendisi kurar, elle `JwtConfig` gerekmez
  - Keycloak/Auth0/Cognito hepsi böyle çalışır; gerçek projede bu mekanizma yazılmaz, yapılandırılır

## Faz 7 — Message queue & event-driven mimari

Kuyruk entegrasyonu öğrenme hedefi için zorunlu değil — **kavramları bilmek yeterli**, mülakatta sorulan da bu. Bu faz ağırlıklı olarak teori; istenirse minimal bir uygulamaya çevrilir.

Referans akış (kod yazmadan üzerinde konuşacağımız senaryo): sipariş oluşturulur → event yayınlanır → `product-service` stok düşer, `notification-service` bildirim gönderir. Sipariş, diğer servislerin cevabını beklemez.

- [ ] **[teori]** Senkron vs asenkron iletişim — hangi çağrı gerçekten beklemeli, hangisi event'e dönüşebilir; async'in kazandırdığı (decoupling, dayanıklılık) ve maliyeti (karmaşıklık, eventual consistency)
- [ ] **[teori]** Temel kuyruk kavramları: producer/consumer, exchange + routing key (RabbitMQ), topic/partition/consumer group (Kafka)
- [ ] **[teori]** Teslim garantileri: at-least-once ne demek, aynı mesaj iki kez gelirse ne olur → **idempotency** (event id ile dedup)
- [ ] **[teori]** Hata yönetimi: retry politikası, **dead letter queue (DLQ)**, poison message problemi
- [ ] **[teori]** **RabbitMQ vs Kafka** — ne zaman task/command kuyruğu, ne zaman event stream + replay ihtiyacı
- [ ] **[teori]** **Outbox pattern** — "DB'ye yaz + event yayınla" atomik değil; ikisinden biri başarısız olursa sistem tutarsız kalır (dağıtık sistemlerin en klasik tuzağı, mülakat favorisi)
- [ ] **[teori]** **Saga pattern** — servis sınırlarını aşan iş akışında transaction, compensating action; neden 2PC kullanılmıyor
- [ ] **[teori]** Eventual consistency'nin API/UX'e yansıması — "sipariş alındı ama stok henüz düşmedi" durumunu frontend nasıl gösterir (FE tarafını bildiğin için doğal köprü)
- [ ] **[opsiyonel]** Minimal RabbitMQ entegrasyonu (Docker + tek publisher + tek consumer) — sadece "gerçekten görmek istiyorum" dersen
- [ ] **[opsiyonel]** Kafka'yı ayağa kaldırıp aynı akışı karşılaştırma

## Faz 8 — Mimari olgunluk & system design

- [ ] **[uygulama]** **Caching:** Redis — cache-aside pattern, TTL, cache invalidation stratejileri, stale data trade-off'u
- [ ] **[opsiyonel]** **API Gateway** (Spring Cloud Gateway) — tek giriş noktası, routing, rate limiting, auth'un gateway'e taşınması
- [ ] **[teori]** Service discovery — Eureka vs Docker/K8s DNS tabanlı çözüm; gerçekten gerekli mi tartışması
- [ ] **[teori]** Merkezi konfigürasyon yönetimi (Spring Cloud Config veya env/secret tabanlı basit yaklaşım)
- [ ] **[opsiyonel]** **Distributed tracing** — Micrometer Tracing + Zipkin; correlation ID ile bir isteği servisler arası takip etmek
- [ ] **[teori]** Dağıtık sistem temelleri: CAP teoremi, eventual consistency, idempotency, split-brain
- [ ] **[teori]** Klasik system design egzersizleri (URL shortener, rate limiter, feed, bildirim sistemi) — çizim + trade-off tartışması

### Mimari karar konuları (ağırlık verilecek — solutions architecture yönü)

Her biri "hangi durumda hangisi ve neden" formatında, tercihen bu projedeki bir karara bağlanarak.

- [ ] **Runtime/dil seçimi** — iş yükünün şekli belirler: I/O-yoğun + çok bağlantı → Node (event loop); CPU-yoğun veya ağır domain/transaction → Go/Java/Rust. JVM warm-up serverless'ta maliyet, Go tek binary ile container'da avantaj. Pratikte ekip bilgisi ve ekosistem teknik farktan baskın. Yaygın kombinasyon: web/BFF Node, domain çekirdek JVM
- [ ] **Eşzamanlılık modelleri** — tek thread + event loop vs thread-per-request; bloklamanın maliyeti; paylaşılan state ve thread-safety (Java'da gerekli, Node'da değil); Java 21 virtual threads farkı nasıl kapatıyor
- [ ] **Senkron vs asenkron iletişim** — ne zaman REST, ne zaman event; eventual consistency'nin API/UX'e yansıması
- [ ] **Veri tutarlılığı** — güçlü tutarlılık vs eventual; saga, outbox; dağıtık sistemde foreign key'in kaybı
- [ ] **Ölçekleme asimetrisi** — stateless app yatayda ucuz, database değil; read replica, cache, sharding sırası ve maliyetleri
- [ ] **Cache stratejileri** — cache-aside vs write-through; invalidation; stale data ne zaman kabul edilebilir
- [ ] **Monolit → mikroservis geçiş kararı** — ne zaman bölünür, ne zaman bölünmez; dağıtık monolit anti-pattern'i
- [ ] **Build vs buy** — auth (IdP), ödeme, arama, bildirim: hangi durumda hazır çözüm alınır

## Faz 9 — Portfolyo projesi & mülakat hazırlığı

- [ ] Fullstack portfolyo projesi: React/Next.js frontend + Spring Boot backend, Dockerize + deploy edilmiş, README'de mimari kararlar yazılı
- [ ] Java'da DSA pratiği — Collections API'yi (List/Map/Set/Stream) akıcı kullanacak kadar problem çözümü
- [ ] Java/Spring mülakat soruları: JVM (heap/stack, GC), `equals`/`hashCode`, immutability, concurrency temelleri (thread, `synchronized`, `CompletableFuture`), Spring bean lifecycle & scope'lar
- [ ] System design mock mülakatları
- [ ] Behavioral: FE deneyimini fullstack anlatısına dönüştürme (design system, PWA, yüksek trafik hikâyeleri)

---

## Çalışma tarzı notları

- Karşılaştırmalar **Express/Mongoose** üzerinden yapılıyor (NestJS değil)
- Yeni class/dosyalar: sohbette kısa comment'li kod snippet'i paylaşılıyor, kod IntelliJ'de kendisi yazılıyor — dosyayı ben yazmıyorum
- **Dil kuralı:** sohbet Türkçe, **kodun içi tamamen İngilizce** — değişken/metot/test isimleri ve **tüm comment'ler dahil**. Kod içinde Türkçe kullanılmıyor.
- **Kod içi comment'ler bilinçli olarak not defteri görevi görüyor:** her yeni kavram, onu ilk kullandığı satırın yanında kısa bir açıklamayla işaretleniyor. Amaç, dosyaların aynı zamanda tekrar edilebilir bir çalışma notu olması — bu yüzden "kod kendini anlatır, comment gereksiz" kuralı bu repoda geçerli değil.
  - Yeni snippet'ler bu yoğunlukta comment'lenmeye devam edecek
  - Not: gerçek bir production repo'sunda bu comment yoğunluğu tercih edilmez; portfolyo projesi (Faz 9) yazılırken comment'ler normal seviyeye çekilecek
- Seviye: beginner değil; loop/OOP/API gibi temeller atlanıyor, Java/JVM/Spring'e özgü mekanizmalar derinlemesine anlatılıyor
- Fazlar katı sıralı değil — istenen konudan devam edilebilir; her fazın çıktısı aynı proje üzerinde birikiyor
