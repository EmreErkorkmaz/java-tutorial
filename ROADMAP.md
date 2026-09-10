# Roadmap & Checklist

Java + Spring Boot ve genel backend/mimari öğrenme sürecinin ilerleme takibi.
**Bu dosya oturum başında okunacak tek dosyadır** — bağlamı baştan anlatmaya gerek yok.

**Hedef:** mid-level fullstack rolüne geçiş, orta vadede solutions architecture yönü.
**Yaklaşım:** Java'yı ve backend/altyapı konularını ayrı ayrı teorik olarak değil, **tek bir sistem** (`product-service` + `order-service`) üzerinde birlikte ilerletmek. Her faz hem yeni bir Java/Spring yeteneği hem bir backend kavramı ekler.

**Etiketler:** **[uygulama]** kod yazılır ve entegre edilir · **[teori]** anlatım + quiz, kod yazılmaz · **[opsiyonel]** istenirse uygulamaya çevrilir

---

## Şu anki durum (2026-08-24)

```
java-tutorial/
├── product-service/   # 8080 — ürün kataloğu, auth (JWT üretimi), productdb
├── order-service/     # 8081 — sipariş, product-service'e senkron REST, orderdb
├── compose.yaml       # postgres + iki servis, üçü de healthcheck'li
├── docker/init-db.sql # orderdb'yi oluşturur (yalnızca boş volume'de çalışır)
└── notes/             # tamamlanmış fazların öğrenme notları
```

Stack: Spring Boot 4.1.0, Java 21, Maven, PostgreSQL 18, Flyway, Spring Security (JWT HS256), Testcontainers, GitHub Actions (matrix ile iki servis paralel).

| Komut | Ne yapar |
|---|---|
| `docker compose up -d --build` | Tüm yığın (kök dizinden) |
| `./mvnw clean verify` | Tek servisin testleri (servis dizininden; Testcontainers kendi Postgres'ini açar) |
| `product-service/api.http` | IntelliJ HTTP Client istek koleksiyonu |

Test durumu: 23 `@Test` (13 product-service, 10 order-service), CI yeşil.

| Faz | Durum |
|---|---|
| 0-1 Ortam & Spring temelleri | ✅ [notes/faz1-spring-temelleri.md](notes/faz1-spring-temelleri.md) |
| 2 Test kültürü | ✅ [notes/faz2-test.md](notes/faz2-test.md) |
| 3 Veritabanı derinliği | ✅ [notes/faz3-veritabani.md](notes/faz3-veritabani.md) |
| 4 Auth & güvenlik | ✅ [notes/faz4-guvenlik.md](notes/faz4-guvenlik.md) |
| 5 Docker & DevOps | ✅ [notes/faz5-docker-devops.md](notes/faz5-docker-devops.md) |
| 6 İkinci servis & senkron iletişim | ✅ [notes/faz6-ikinci-servis.md](notes/faz6-ikinci-servis.md) |
| 7 Message queue & event-driven | ⬜ sıradaki |
| 8 Mimari olgunluk & system design | ⬜ |
| 9 Portfolyo & mülakat hazırlığı | ⬜ |

## Çalışma tarzı (her oturumda geçerli)

- **Problem → yaklaşım → kod.** Yeni konu önce çözdüğü problemle, mümkünse **gözle görülür şekilde** açılır (başarısız test, SQL logu, query planı, ölçülen süre). Sonra yaklaşım ve alternatifleri, en son bağımlılık + kod.
- **Kodu Emre yazar.** Sohbette kısa comment'li, **olduğu gibi yapıştırılabilir** snippet paylaşılır (```java / ```properties — asla ```diff). Yerini `// ---- ADD: inside JwtConfig, above jwtDecoder() ----` gibi marker comment gösterir. Silmeler düz yazıyla anlatılır. Config, tooling, docs doğrudan yazılabilir.
- **Dil:** sohbet Türkçe, teknik terimler İngilizce ("load balancer", "horizontal scaling"). **Kodun içi tamamen İngilizce** — değişken/metot/test isimleri ve tüm comment'ler dahil. Test isimleri `method_whenCondition_expectedResult`.
- **Comment yoğunluğu bilinçli olarak yüksek** — dosyalar aynı zamanda tekrar notu. (Faz 9 portfolyo projesinde normal seviyeye çekilecek.)
- **Diyagram metin olarak** (ASCII/kutu çizimi), uzun paragraf yerine. Mevcut kodu anlatırken `ProductClient.java:34` gibi file:line ver.
- **"Not al:"** — oturum başına 2-3 tane, index-kart boyunda mülakat notu. Trade-off'lar, karışan tanımlar, sürprizli mekanizmalar; sürüm/tooling trivia değil.
- **Karşılaştırmalar Express/Mongoose üzerinden** (NestJS değil).
- **SQL ve sıra dışı shell komutları açıklanır** — çıplak SQL verilmez, `--` comment veya parantez içi kısa not eklenir.
- **Uygulama zamanı üretim olasılığına göre bütçelenir:** mid-level bir dev'in işte gerçekten yazacağı şey uygulanır (test, query tuning, cache, API tasarımı, hata yönetimi); ekiplerin konfigüre ettiği/satın aldığı şey teoride kalır (JWKS, key rotation, broker iç yapısı, service discovery).

## Oturum protokolü

**Her faz kendi oturumunda çalışılır.** Yeni oturum sıfırdan başlar; geçmiş sohbet taşınmaz, bağlam **önceki fazın devir notundan** gelir.

**Oturum başında oku (bu kadarı yeterli):**
1. Bu dosya (ROADMAP.md).
2. Bir önceki fazın devir notu — ilgili `notes/fazN-*.md` dosyasının **ilk bölümü**: `sed -n '1,25p' notes/faz6-ikinci-servis.md`
3. Gerisi ihtiyaç halinde: bir karara "neden böyle yapmıştık" diye takılınca ilgili `notes/` dosyasının tamamı, koda takılınca dosyanın kendisi. Baştan hepsini okuma.

**Faz sonunda yaz (devir notu — bir sonraki oturumun tek girdisi):**
İlgili `notes/fazN-*.md` dosyasının başına, başlığın hemen altına `## Faz özeti — devir notu` bölümü. Beş başlık, her biri 1-3 cümle:
- **Ne yapıldı** — maddeler değil, tek paragraf
- **Ölçümler** — sayı varsa sayı (öncesi → sonrası)
- **Kalıcı kararlar** — sonraki fazları bağlayan kurallar
- **Tuzaklar / açık zayıflıklar** — bilinçli bırakılanlar dahil
- **Sonraki faza taşınan bağlam** — bir sonraki fazın hangi maddesine bağlanıyor

**Faz sonu checklist:**
- [ ] `./mvnw clean verify` iki (üç) serviste de yeşil
- [ ] ROADMAP'te checkbox'lar ve faz tablosu güncel
- [ ] Öğrenilenler `notes/fazN-*.md` içine yazıldı (ROADMAP şişirilmedi), devir notu güncellendi
- [ ] `notes/README.md` satırı güncel
- [ ] Commit atıldı

---

## Kalan iş — eski fazlardan artanlar

- [ ] **Hata response body'sini tipli `record`'a çevir** — [uygulama] [~30 dk, ısınma işi]
  - Şu an `GlobalExceptionHandler` `Map<String, Object>` dönüyor: alan adları derleyici tarafından kontrol edilmiyor, contract yalnızca kodu okuyarak anlaşılıyor.
  - Dosyalar: her iki servisteki `exception/GlobalExceptionHandler.java`, yeni `dto/ApiError.java` (+ validation hatası için alan listesi taşıyan varyant).
  - Kabul kriteri: 404/400/409/429 yanıt gövdeleri aynı kalır (mevcut testler değişmeden geçer), tip güvenli hale gelir.
- [ ] **Test piramidi** — [teori] Faz 2'de pratikte ölçüldü (mock'lu 3 test 0.08 s vs context ayağa kalkan test ~1 s); teorisi konuşulacak.
- [ ] **Isolation level'lar & optimistic locking (`@Version`)** — [teori] READ COMMITTED varsayılanı, lost update senaryosu, optimistic vs pessimistic locking.
- [ ] **Refresh token** — [teori] access 15 dk + sunucuda saklanan, iptal edilebilir refresh token. Uygulanmaz: production'da IdP işi.
- [ ] **Monolit vs mikroservis trade-off'ları** — [teori] Faz 7 girişinde, kendi iki servisimizin bedeli üzerinden konuşulur.
- [ ] **Circuit breaker** — [teori] Spring core'da yok (Resilience4j gerekir). CLOSED → OPEN → HALF_OPEN; retry geçici hatayı, breaker kalıcı kesintiyi çözer. Faz 6'da ölçülen "kesintide 14 ms → 782 ms" bunun gerekçesi.
- [ ] **RS256 + JWKS** — [teori] Faz 6'da HS256'nın sınırı canlı gösterildi (doğrulama yeteneği = üretme yeteneği). Asimetrikte private key yalnızca üreticide; JWKS ile public key `/.well-known/jwks.json`'dan dağıtılır, `kid` header'ı hangi anahtar olduğunu söyler; Spring tarafı tek property: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (decoder'ı Boot kendisi kurar, elle `JwtConfig` gerekmez). Keycloak/Auth0/Cognito hepsi böyle çalışır — gerçek projede bu mekanizma yazılmaz, yapılandırılır.
- [ ] **Kubernetes temelleri** — [teori] pod/service/deployment, ne zaman gerekir.
- [ ] **Prometheus + Grafana** — [opsiyonel] Faz 8.2 (tracing) ile aynı oturumda değerlendirilir; Actuator zaten ayakta.
- [ ] **Image registry'e push + deploy** — [uygulama] Faz 9'daki portfolyo projesiyle birleştirilecek (bkz. 9.1).

---

## Faz 7 — Message queue & event-driven mimari

Referans akış: sipariş oluşturulur → `order.created` event'i yayınlanır → `notification-service` bildirim gönderir. Sipariş, bildirimin cevabını **beklemez**.

Kapsam kararı: ince bir uygulama dilimi (7.1-7.4) yapılır, gerisi teoride kalır — broker'ın kendisi ekiplerin kurduğu/satın aldığı katman, ama publish/consume'u bir kez gözle görmek async'in ne kazandırdığını somutlaştırıyor.

### 7.1 RabbitMQ + `order.created` publisher — [uygulama] [~1 oturum] ✅ tamamlandı (2026-08-25)

**Problem:** Faz 6'da `product-service` kapalıyken sipariş **oluşturulamıyordu** — orada beklemek doğruydu (fiyat lazım). Bildirim öyle değil: bildirim servisi çökünce siparişin de düşmesi kabul edilemez. Senkron çağrı bunu ayıramaz.

**Yaklaşım:** Event yayınla, cevabını bekleme. Broker araya girince gönderen ile alan birbirini tanımaz (decoupling) ve alan ayakta olmasa bile mesaj kuyrukta bekler (dayanıklılık). Bedeli: eventual consistency + operasyonel yeni bir bileşen.

**Adımlar:** compose.yaml'a `rabbitmq:4-management` (5672 + 15672 UI, healthcheck) → `order-service`'e `spring-boot-starter-amqp` → `config/RabbitConfig.java` (topic exchange `order.events`, JSON message converter) → `event/OrderCreatedEvent.java` (record: `eventId` (UUID), `orderId`, `userId`, `totalAmount`, `createdAt`) → `OrderService.create()` sonunda `RabbitTemplate.convertAndSend(...)`.

**Kritik ayrıntı:** publish **DB commit'inden sonra** yapılır. `create` bilinçli olarak `@Transactional` değil (Faz 6 kararı), yani save zaten commit edilmiş oluyor. Yine de "DB'ye yaz + event yayınla" atomik değildir — publish başarısız olursa sipariş var, event yok. Outbox pattern'in var oluş sebebi tam olarak bu (7.5'te teori).

**Sürüm tuzağı (doğrulanacak):** Spring AMQP'nin yeni sürümünde JSON converter sınıf adı değişmiş olabilir (`Jackson2JsonMessageConverter` → `JacksonJsonMessageConverter`). Eski tutorial'lara güvenme, IDE'de complete ettir.

**Kabul kriteri:** Sipariş oluşturulunca RabbitMQ UI'da (localhost:15672) kuyrukta 1 mesaj görünür; consumer henüz yokken bile sipariş 201 döner.

### 7.2 `notification-service` consumer — [uygulama] [~1 oturum] ✅ tamamlandı (2026-09-10)

**Yaklaşım:** Yeni Maven modülü ama **DB'siz** — sadece `spring-boot-starter-amqp` + `spring-boot-starter-web` (health için) + tek `@RabbitListener` sınıfı. Amaç üçüncü bir CRUD servisi yazmak değil, tüketici tarafını görmek.

**Dosyalar:** `notification-service/` (Initializr), `listener/OrderCreatedListener.java`, `Dockerfile` (mevcut iki servisten kopya — `--mount=type=cache` dahil), `compose.yaml` girdisi, `.github/workflows/ci.yml` matrix'ine ekleme.

**Kabul kriteri (asıl ders):** `docker compose stop notification-service` → sipariş oluştur → **201 döner** (Faz 6'daki senkron çağrının aksine) → servisi başlat → mesaj tüketilir ve loglanır. Kuyruk derinliği UI'da 1 → 0.

**Not al:** Senkron çağrı çağrılanın ayakta olmasını şart koşar; event yalnızca broker'ın ayakta olmasını şart koşar. Bağımlılık kaybolmaz, yer değiştirir.

### 7.3 Idempotency demo — [uygulama] [~30 dk]

**Problem:** Broker'lar pratikte **at-least-once** teslim eder: ack kaybolursa aynı mesaj tekrar gelir. Consumer "bildirim gönder" yapıyorsa kullanıcı iki mail alır.

**Yaklaşım:** Event'i taşıyan `eventId` ile dedup. Consumer'da `Set<UUID> seen` (in-memory yeterli — gerçek çözüm Redis/DB tablosu, çünkü in-memory restart'ta sıfırlanır ve instance başına ayrıdır; Faz 4'teki `LoginAttemptService` ile aynı sınırlama).

**Kabul kriteri:** RabbitMQ UI'dan aynı mesaj elle tekrar publish edilir; log ikinci kez "işlendi" yazmaz, "duplicate skipped" yazar.

**Not al:** At-least-once + idempotent consumer = pratikte exactly-once. Broker'dan exactly-once beklemek yerine consumer'ı tekrara dayanıklı yazmak standart çözümdür.

### 7.4 DLQ + poison message — [uygulama] [~30 dk]

**Problem:** İşlenemeyen bir mesaj sonsuz retry'a girerse kuyruğu kilitler (poison message) — arkasındaki sağlam mesajlar da işlenemez.

**Yaklaşım:** Kuyruğa dead letter exchange bağla (`x-dead-letter-exchange`), listener retry sayısını sınırla; tüketilemeyen mesaj DLQ'ya düşer, insan bakar.

**Kabul kriteri:** Bozuk gövdeli mesaj publish edilir; N denemeden sonra `order.created.dlq` kuyruğunda görünür, ana kuyruk boşalır ve sonraki mesaj normal işlenir.

### 7.5 Event-driven teori bloğu — [teori] [~1 oturum]

- [ ] Senkron vs asenkron karar kriteri — hangi çağrı gerçekten beklemeli (cevabı akışı belirliyorsa), hangisi event'e dönüşebilir (yan etkiyse). Kendi iki örneğimiz: fiyat sorgusu (senkron) vs bildirim (async).
- [ ] Kuyruk kavramları: producer/consumer, exchange + routing key (RabbitMQ) vs topic/partition/consumer group (Kafka).
- [ ] Teslim garantileri: at-most-once / at-least-once / exactly-once; 7.3'te uygulanan dedup buraya bağlanır.
- [ ] **Outbox pattern** — 7.1'de yaşadığımız atomiklik sorununun standart çözümü: event aynı transaction'da bir `outbox` tablosuna yazılır, ayrı bir süreç kuyruğa taşır. Mülakat favorisi.
- [ ] **Saga pattern** — servis sınırını aşan iş akışında transaction; compensating action; 2PC neden kullanılmıyor.
- [ ] **RabbitMQ vs Kafka** — task/command kuyruğu vs replay edilebilir event stream; retention ve consumer group farkı.
- [ ] Eventual consistency'nin API/UX'e yansıması — "sipariş alındı, stok henüz düşmedi" durumunu frontend nasıl gösterir (FE deneyimiyle doğal köprü).

### 7.6 Opsiyonel — varsayılan: yapılmaz

- [ ] **[opsiyonel]** `product-service`'te `stock` kolonu (V5 migration) + event ile stok düşme. Şu an `product` tablosunda stok yok, ek migration gerektiriyor.
- [ ] **[opsiyonel]** Aynı akışı Kafka ile kurup karşılaştırma.

---

## Faz 8 — Mimari olgunluk & system design

Üç uygulama maddesi birbirinden bağımsız, sırası değişebilir.

### 8.1 Redis cache — cache-aside — [uygulama] [~1 oturum]

**Problem:** Aynı ürün tekrar tekrar okunuyor ve her seferinde DB'ye gidiyor. Önce **ölçülür**: `spring.jpa.show-sql` açıkken aynı `GET /api/products/{id}` çağrısı N kez → N sorgu. (Faz 3'teki N+1 ile aynı refleks: önce logda gör, sonra çöz.)

**Yaklaşım:** Cache-aside — uygulama önce cache'e bakar, yoksa DB'den okur ve cache'e yazar. Write-through'a göre daha basit ve cache çökse de sistem çalışır; bedeli ilk isteğin (cold miss) yavaş olması ve **invalidation sorumluluğunun uygulamada kalması**.

**Dosyalar:** `product-service/pom.xml` (`spring-boot-starter-data-redis`, `spring-boot-starter-cache`), `config/CacheConfig.java` (TTL — süresiz cache yok), `service/ProductService.java` (`@Cacheable` / `@CacheEvict`), `compose.yaml` (`redis:8-alpine`).

**Tuzaklar:** (1) Entity değil **DTO** cache'le — `Product` lazy `category` proxy'si taşıyor, serialize edilirken patlar veya OSIV kapalı olduğu için `LazyInitializationException` verir. (2) `@Cacheable` de proxy tabanlı: self-invocation'da çalışmaz (Faz 3'teki `@Transactional` tuzağının aynısı). (3) `Page` dönen liste endpoint'i cache'lenmez — key patlaması.

**Kabul kriteri:** İkinci okuma SQL üretmiyor (logda sorgu yok); `PUT`/`DELETE` sonrası okuma **bayat veri dönmüyor**; TTL dolunca tekrar DB'ye gidiyor.

**Ölçüm:** Aynı endpoint'e 10 istek, öncesi/sonrası sorgu sayısı ve süre.

**Not al:** Cache invalidation zor olduğu için değil, **doğruluk sınırı belirsiz** olduğu için zor: ne kadar bayat veri kabul edilebilir sorusunun cevabı teknik değil, ürün kararıdır.

### 8.2 Distributed tracing — [uygulama] [~1 oturum]

**Problem:** Bir sipariş isteği iki servise yayılıyor. Yavaşlık veya hata olduğunda iki ayrı log dosyasına bakıp isteği elle eşleştirmek gerekiyor — üçüncü servis (notification) eklendikten sonra bu iyice imkânsız.

**Yaklaşım:** Micrometer Tracing + Brave; her istek bir `traceId` alır ve servis sınırını HTTP header'ı ile geçer, span'lar Zipkin'de tek ağaç olarak toplanır. Faz 6'daki token propagation ile aynı ders: **bağlam servis sınırını kendiliğinden geçmez, taşınır.**

**Dosyalar:** iki (veya üç) servisin `pom.xml`'i (`micrometer-tracing-bridge-brave`, `zipkin-reporter-brave`), `application.properties` (`management.tracing.sampling.probability`, logging pattern'e `traceId`/`spanId`), `compose.yaml` (`openzipkin/zipkin`).

**Tuzak (doğrulandı, iş çıkacak):** `RestClientConfig.java:31` istemciyi **statik** `RestClient.builder()` ile kuruyor — Spring'in yönettiği `RestClient.Builder` değil, yani observation registry bağlı değil ve trace header'ı **taşınmaz**. Enjekte edilen `RestClient.Builder`'a geçilecek; timeout'lu `requestFactory` ve token interceptor'ı korunacak. Aynı şekilde `@Async`/executor'a geçen iş de bağlamı kaybeder (ThreadLocal — Faz 6'da `SecurityContextHolder` için aynısını gördük).

**Kabul kriteri:** Zipkin UI'da bir sipariş oluşturma isteği, `order-service` → `product-service` span'leriyle **tek trace** olarak görünüyor; log satırlarında aynı `traceId` var.

**Not al:** Sampling oranı maliyet kararıdır: %100 trace üretim yükünü ve depolamayı ciddi artırır, %1 nadir hatayı kaçırır.

### 8.3 API Gateway — [uygulama] [~1 oturum]

**Problem:** İstemci iki (yakında üç) ayrı porta gitmek zorunda; rate limiting Faz 4'te **uygulama içinde** yazıldı (`LoginAttemptService`) ve orada notunu düştük: instance başına ayrı çalışır, restart'ta sıfırlanır — **yanlış katman**.

**Yaklaşım:** Spring Cloud Gateway ile tek giriş noktası; routing + merkezi rate limiting (Redis tabanlı, 8.1'de zaten Redis var). Auth'un gateway'e taşınması **teoride** kalır: gateway token'ı doğrulayıp servisleri sadeleştirebilir, ama servisler "gateway'den geldi" varsayımına bağlanırsa iç ağdan gelen istek korumasız kalır (defense in depth ihlali).

**Dosyalar:** yeni `api-gateway/` modülü (Spring Cloud BOM + `spring-cloud-starter-gateway`), route tanımları, compose girdisi.

**Risk (önce doğrula):** Spring Cloud'un Boot 4.1 ile uyumlu sürümü var mı — yoksa bu madde ertelenir veya nginx ile basit reverse proxy'ye indirgenir.

**Kabul kriteri:** Tek porttan (`:8090`) hem `/api/products/**` hem `/api/orders/**` çalışıyor; gateway'de tanımlı rate limit aşılınca 429, servisler doğrudan çağrıldığında kendi kuralları hâlâ geçerli.

### 8.4 Dağıtık sistem teorisi — [teori]

- [ ] Service discovery — Eureka vs Docker/K8s DNS; bizim `PRODUCT_SERVICE_BASE_URL` yaklaşımımız ne zaman yetmez.
- [ ] Merkezi konfigürasyon — Spring Cloud Config vs env/secret tabanlı basit yaklaşım.
- [ ] CAP teoremi, eventual consistency, idempotency, split-brain.
- [ ] Klasik system design egzersizleri (URL shortener, rate limiter, feed, bildirim sistemi) — çizim + trade-off tartışması.

### Mimari karar konuları (solutions architecture yönü)

Her biri "hangi durumda hangisi ve neden" formatında, **bu projedeki somut bir karara bağlanarak**.

- [ ] **Runtime/dil seçimi** — iş yükünün şekli belirler: I/O-yoğun + çok bağlantı → Node (event loop); CPU-yoğun veya ağır domain/transaction → Go/Java/Rust. JVM warm-up serverless'ta maliyet, Go tek binary ile container'da avantaj. Pratikte ekip bilgisi ve ekosistem teknik farktan baskın.
- [ ] **Eşzamanlılık modelleri** — tek thread + event loop vs thread-per-request; bloklamanın maliyeti; thread-safety (Java'da gerekli, Node'da değil); Java 21 virtual threads farkı nasıl kapatıyor. → Bağlanacağı karar: Faz 6'daki `@ConcurrencyLimit(20)` bulkhead'i.
- [ ] **Senkron vs asenkron iletişim** — → Faz 7.1'deki ayrım (fiyat senkron, bildirim event).
- [ ] **Veri tutarlılığı** — güçlü vs eventual; saga, outbox; dağıtık sistemde foreign key'in kaybı. → `order_item.product_id`'nin neden FK olmadığı.
- [ ] **Ölçekleme asimetrisi** — stateless app yatayda ucuz, database değil; read replica, cache, sharding sırası ve maliyetleri. → 8.1'deki cache bu sıranın ilk adımı.
- [ ] **Cache stratejileri** — cache-aside vs write-through; invalidation; stale data ne zaman kabul edilebilir. → 8.1.
- [ ] **Monolit → mikroservis geçiş kararı** — ne zaman bölünür, ne zaman bölünmez; dağıtık monolit anti-pattern'i. → İki servisimizin bize ödettiği bedel (dağıtık N+1, token propagation, ayrı DB).
- [ ] **Build vs buy** — auth (IdP), ödeme, arama, bildirim. → Faz 4'te auth'u kendimiz yazdık ama production'da yazılmayacağının notunu düştük.

---

## Faz 9 — Portfolyo projesi & mülakat hazırlığı

- [ ] **9.1 Fullstack portfolyo projesi** — React/Next.js frontend + Spring Boot backend, Dockerize **ve deploy edilmiş** (Railway / Fly.io / Render; image registry'e push burada yapılır — Faz 5'ten devreden madde). README'de mimari kararlar yazılı. Comment yoğunluğu bu repodakinden düşük tutulur (production repo tarzı).
- [ ] **9.2 Java'da DSA pratiği** — Collections API'yi (List/Map/Set/Stream) akıcı kullanacak kadar problem çözümü.
- [ ] **9.3 Java/Spring mülakat soruları** — JVM (heap/stack, GC), `equals`/`hashCode`, immutability, concurrency temelleri (thread, `synchronized`, `CompletableFuture`), Spring bean lifecycle & scope'lar.
- [ ] **9.4 System design mock mülakatları.**
- [ ] **9.5 Behavioral** — FE deneyimini fullstack anlatısına dönüştürme (design system, PWA, yüksek trafik hikâyeleri).
