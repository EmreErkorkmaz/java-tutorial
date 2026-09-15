# Faz 8 — Mimari olgunluk & system design (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** Üç bağımsız uygulama maddesi — Redis cache-aside (`product-service`, 8.1), üç servise (order/product/notification) yayılan distributed tracing (Zipkin, 8.2), nginx ile API gateway + rate limiting (8.3) — ve teori tarafında CAP teoremi + iki system design egzersizi (rate limiter, bildirim sistemi, 8.4). Ama fazın asıl ağırlığı, sekiz maddelik **"Mimari karar konuları"** bloğuydu: her biri teach-back formatında (Emre anlattı, zorlayıcı follow-up geldi, eksik kalan tamamlandı) işlendi ve kendi projemizdeki somut bir karara bağlandı. Roadmap dışı iki konu da bu fazda ek olarak işlendi: hexagonal mimari, Kafka'nın derinlemesine teorisi (partition/consumer group/CDC).

**Ölçümler:** Cache: miss 1 sorgu → hit 0 sorgu, TTL (60s) sonrası tekrar 1 sorgu. Tracing: tek `traceId`, üç serviste, hem senkron (order→product) hem asenkron (order→RabbitMQ→notification) sınırı geçti. Gateway: rate limit aşımı 429, aynı anda doğrudan servise (8080) atılan istek 200 (gateway'in limiti sadece kendinden geçen trafiği görüyor).

**Kalıcı kararlar:** Tekrarlayan tek prensip — **Spring sadece kendi yönettiği (context'e bean olarak kayıtlı) nesnelere otomatik özellik ekleyebilir.** Self-invocation (proxy'ye uğramayan çağrı), `RestClient.builder()` statik kullanımı (tracing customizer'ı görmüyor), `@Cacheable`'ın final sınıf/self-invocation kısıtı — hepsi aynı kökten. Mimari karar sorularının hepsi "bu projedeki somut bir karara bağlanarak" işlendi; bu format (teach-back + proje çapası) Faz 9'da da sürecek.

**Tuzaklar / açık zayıflıklar (bilinçli veya yaşanmış):** Spring Cloud Gateway, Boot 4.1.0 ile **binary uyumsuz** çıktı (`NoClassDefFoundError`, minimal probe uygulamasında bile) — ekosistem araçlarının ana framework'ün en yeni sürümünden geride kalması riski somut olarak yaşandı, nginx'e indirgendi. `@ConcurrencyLimit`'in varsayılan politikası `REJECT` değil `BLOCK` çıktı — süresiz bekleme riski taşıyordu, `ProductClient.java`'da düzeltildi. URL shortener ve feed system design egzersizleri **bilinçli olarak teoride bırakıldı** (zaman/getiri kararı, rate limiter + bildirim sistemi yeterli görüldü).

**Sonraki faza taşınan bağlam:** Faz 9 — portfolyo projesi & mülakat hazırlığı. Bu fazın kartları (CAP, system design, sekiz mimari karar konusu) mülakat provasının doğrudan malzemesi. Retrieval döngüsü (`notes/kartlar.md`, açılış quiz'i, kapanış teach-back'i) aynen sürüyor.

---

## Faz 8 — Mimari olgunluk & system design

Üç uygulama maddesi birbirinden bağımsız, sırası değişebilir.

### 8.1 Redis cache — cache-aside — [uygulama] ✅ 2026-09-11

**Problem:** Aynı ürün tekrar tekrar okunuyor ve her seferinde DB'ye gidiyor. Önce ölçülür: `spring.jpa.show-sql` açıkken aynı `GET /api/products/{id}` çağrısı N kez → N sorgu (Faz 3'teki N+1 ile aynı refleks).

**Yaklaşım:** Cache-aside — uygulama önce cache'e bakar, yoksa DB'den okur ve cache'e yazar. Write-through'a göre daha basit ve cache çökse de sistem çalışır; bedeli cold miss ve invalidation sorumluluğunun uygulamada kalması.

**Dosyalar:** `product-service/pom.xml` (`spring-boot-starter-data-redis`, `spring-boot-starter-cache`), `config/CacheConfig.java` (TTL 60s), `service/ProductService.java` (`@Cacheable`/`@CacheEvict`), `compose.yaml` (`redis:8-alpine`).

**Tuzaklar (canlı yaşandı):**
1. `ProductResponse` (record, implicit `final`) `GenericJacksonJsonRedisSerializer` ile cache'lenince tip bilgisi JSON'a gömülmedi (Jackson'ın `DefaultTyping.NON_FINAL`'i final sınıfları atlıyor) — geri okumada `ClassCastException`. Çözüm: tek bilinen tip için `JacksonJsonRedisSerializer<ProductResponse>` (non-generic).
2. `@EnableCaching`, `ProductControllerTest`'i (`@WebMvcTest`) bozdu — slice cache autoconfig'i yüklemiyor, `CacheManager` bean'i bulunamadı. `@MockitoBean CacheManager` ile düzeltildi (Faz 7.1'deki `RabbitTemplate` testi bozması ile aynı desen).
3. Entity değil DTO cache'lenir (lazy `category` proxy'si serialize edilemez), `@Cacheable` de self-invocation'da çalışmaz, `Page` dönen liste cache'lenmez (key patlaması).

**Kabul kriteri:** ✅ İkinci okuma SQL üretmiyor; `PUT`/`DELETE` sonrası bayat veri dönmüyor; TTL dolunca tekrar DB'ye gidiyor.

**Not al:** Cache invalidation zor olduğu için değil, **doğruluk sınırı belirsiz** olduğu için zor — "ne kadar bayat veri kabul edilebilir" sorusunun cevabı teknik değil ürün kararıdır.

### 8.2 Distributed tracing — [uygulama] ✅ 2026-09-11

**Problem:** Bir sipariş isteği üç servise yayılıyor. Ayrı log dosyalarını elle eşleştirmek gittikçe imkânsızlaşıyor.

**Yaklaşım:** Micrometer Tracing + Brave + Zipkin. Tek bağımlılık yeterli: `spring-boot-starter-zipkin` (roadmap'in önerdiği iki ayrı dependency eski bir kalıp). `management.tracing.export.zipkin.endpoint` (property adı sürüm değiştirmiş), log'a `traceId`/`spanId` otomatik ekleniyor.

**Kapsam genişletildi (order+product+notification):** Spring AMQP'nin observation desteği (`spring.rabbitmq.template.observation-enabled` / `...listener.simple.observation-enabled`) sayesinde tek `traceId` hem senkron HTTP sınırını hem asenkron RabbitMQ sınırını geçti.

**Tuzak 1 (roadmap'in önceden işaret ettiği, doğrulandı):** `RestClientConfig.java` istemciyi statik `RestClient.builder()` ile kuruyordu — Spring'in yönettiği `RestClient.Builder` değil, `ObservationRestClientCustomizer` ona hiç uygulanmıyor. Inject edilen `builder`'a geçilerek düzeltildi.

**Tuzak 2 (yeni, kod düzeltilince ortaya çıktı):** `order-service`'te `RestClient.Builder` bean'i **hiç yoktu** — `spring-boot-starter-webmvc` (sunucu) `spring-boot-starter-restclient`'ı (istemci) içermiyor, Boot 4.1'de ayrı starter'lar (Boot 3.x'te bedavaydı). Eklenince context açıldı.

**Kabul kriteri:** ✅ Zipkin'in API'sinden doğrulandı: tek `traceId`, `product-service`'in span'i `order-service`'in `http get` span'inin, `notification-service`'in span'i publish span'inin doğrudan çocuğu.

**Not al:** Sampling oranı maliyet kararıdır — %100 üretim yükünü ciddi artırır, %1 nadir hatayı kaçırır.

### 8.3 API Gateway — [uygulama] ✅ 2026-09-12

**Problem:** İstemci ayrı portlara gitmek zorunda; rate limiting Faz 4'te uygulama içinde (`LoginAttemptService`) yanlış katmandaydı.

**Risk gerçekleşti:** Spring Cloud Gateway (4.3.0 / `spring-cloud 2025.0.0`), Boot 4.1.0 ile `NoClassDefFoundError` verdi — minimal bir probe uygulamasıyla canlı doğrulandı, context hiç açılmadı. Roadmap'in önceden yazdığı yedek plana (nginx) geçildi.

**Yaklaşım (nginx):** Tek giriş noktası (`:8090`), `location /api/products` ve `/api/orders` `proxy_pass` ile yönlendiriyor; `limit_req_zone` ile IP bazlı rate limiting (10r/s, burst 5). Auth'un gateway'e taşınması teoride kaldı — defense in depth ihlali riski canlı gösterildi (gateway limit'e girerken doğrudan servise atılan istek sorunsuz geçti).

**Tuzak (canlı yaşandı):** `location /api/products/` (sonunda `/`) `/api/products` isteğiyle eşleşmiyor — prefix eşleşmesi kısa string'in uzun string'i başlatması mantığıyla çalışır. Sonunda `/` kaldırılarak düzeltildi.

**Kabul kriteri:** ✅ Tek porttan iki servis de çalışıyor; hızlı art arda istek 429; aynı anda doğrudan servise atılan istek 200.

### 8.4 Dağıtık sistem teorisi + system design egzersizleri — [teori] ✅ 2026-09-15

**CAP teoremi:** Consistency, Availability, Partition tolerance — pratikte P sabit, gerçek seçim P bölündüğünde C mi A mı. Bizim RabbitMQ deneyimimiz (Faz 7) AP tarafına yakın durduğumuzun kanıtı: broker düşünce sipariş kabul edildi (available) ama event yayınlanamadı (tutarsız kaldı).

**System design — rate limiter:** Dağıtık ortamda nginx'in `limit_req_zone`'u neden yetmez (her instance kendi belleğinde ayrı sayar) → çözüm paylaşılan atomik sayaç (Redis `INCR`). Dört algoritma karşılaştırıldı: fixed window (sınırda burst riski), sliding window log (en hassas, en pahalı), sliding window counter (pratik uzlaşma), token bucket (endüstri standardı — Stripe/AWS/Google).

**System design — bildirim sistemi:** E-ticaret sipariş bildirimi (AP'ye yakın, bizim gerçek sistemimiz) ile bankacılık ödeme onayı (CP'ye yakın, teslim onayı + audit zorunlu) karşılaştırıldı — aynı "bildirim sistemi" sorusu, gereksinime göre tamamen farklı mimariye götürüyor.

**Service discovery (tek paragraf):** `PRODUCT_SERVICE_BASE_URL` (env variable + compose DNS) küçük ölçekte yeterli; Eureka/Spring Cloud Config dinamik instance sayısı ve çok ortamlı deploy olunca gerekir.

### Mimari karar konuları — Faz 8'in ana malzemesi ✅ 2026-09-14

Teach-back formatında işlendi, sekiz madde, hepsi kart oldu (`notes/kartlar.md`):

- **Runtime/dil seçimi** — I/O-bound (Node) vs CPU-bound (Java/Go/Rust); JVM warm-up serverless maliyeti, Go'nun tek binary avantajı.
- **Eşzamanlılık modelleri** — concurrency vs parallelism; `@ConcurrencyLimit(20)` bulkhead'i. **Yol boyunca gerçek bir kod hatası bulundu:** varsayılan politika `REJECT` değil `BLOCK` — süresiz bekleme riski, `ProductClient.java`'da düzeltildi.
- **Senkron vs asenkron iletişim** — Faz 7.1'deki ayrım.
- **Veri tutarlılığı** — outbox/saga, `order_item.product_id`'nin neden FK olmadığı.
- **Ölçekleme asimetrisi** — cache → read replica → sharding sırası, 8.1 bu sıranın ilk adımı.
- **Cache stratejileri** — 8.1.
- **Monolit → mikroservis geçiş kararı** — dağıtık monolit anti-pattern'i (paylaşılan DB, lockstep deploy).
- **Build vs buy** — Faz 4'teki auth örneği, IdP'nin güvenlik kapsaması + fırsat maliyeti.

**Ek konular (roadmap dışı, kendi isteğiyle eklendi):** Hexagonal mimari (ports & adapters, bizim katmanlı mimariyle farkı), Kafka'nın derinlemesine teorisi (partition/consumer group, RabbitMQ'nun fan-out'una göre gerçek farkı — geç katılan tüketicinin geçmişe erişebilmesi, CDC/Debezium'un outbox pattern'le bağlantısı).
