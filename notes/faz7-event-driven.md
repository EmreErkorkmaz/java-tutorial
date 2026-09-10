# Faz 7 — Message queue & event-driven mimari (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** RabbitMQ eklendi; `order-service` sipariş oluşunca `order.created` event'i yayınlıyor (senkron bildirim çağrısı yerine). Yeni, DB'siz `notification-service` bunu `@RabbitListener` ile tüketiyor — kendi exchange/queue/binding topolojisini `@QueueBinding` ile bağımsız deklare ediyor, `order-service`'in koduna hiç bağımlı değil. Üstüne idempotency (in-memory `Set<UUID>` dedup) ve DLQ (dead-letter queue + Spring Boot'un kendi retry config'i, harici kütüphane yok) eklendi. Faz, teach-back formatında bir teori turuyla kapandı. Bu fazda ayrıca `notes/kartlar.md` (mülakat kart destesi) ve oturum şablonu (açılış quiz'i + kapanış teach-back'i) kuruldu — bkz. [CLAUDE.md](../CLAUDE.md).

**Ölçümler/kanıtlar:** Kuyruk derinliği 1 → 0 (7.1/7.2). RabbitMQ durdurulup sipariş oluşturulunca istemci **500** aldı ama `customer_order` tablosunda satır **vardı** — "sipariş var, event yok" boşluğu soyut değil, canlı kanıtlandı. Aynı `eventId` iki kez publish edilince: birinci "Notifying", ikincisi "Duplicate skipped". Bozuk JSON gövdeli mesaj 3 denemeden sonra `order.created.dlq`'ya düştü, ana kuyruk boşaldı ve sonraki sağlam mesaj normal işlendi.

**Kalıcı kararlar:** İki servis arasında paylaşılan şey **JSON wire contract**, derlenmiş Java kodu değil — `OrderCreatedEvent` her iki serviste ayrı ayrı tanımlı. DLQ + retry limiti tamamen `application.properties` konfigürasyonu (`spring.rabbitmq.listener.simple.retry.*`), Faz 6'daki "harici resilience kütüphanesi yok" kararının devamı. Outbox pattern **bilinçli olarak uygulanmadı** — platform kararı, teoride kaldı.

**Tuzaklar / açık zayıflıklar:** In-memory idempotency `Set` restart'ta sıfırlanır ve instance başına ayrıdır (Faz 4'teki `LoginAttemptService` ile aynı sınırlama, bilerek). "DB'ye yaz + event yayınla" hâlâ atomik değil — `RabbitMQ` düşerse sipariş DB'de kalıyor ama istemci 500 alıyor (outbox olmadan kapanmayan bir açık). `notification-service`'in hiç testi yok. RabbitMQ'da **var olan bir queue'nun argümanları redeclare ile değiştirilemiyor** — 7.4'te DLQ eklerken her iki servis de `PRECONDITION_FAILED` ile düştü, queue silinip yeniden oluşturularak çözüldü (dev'de kabul edilebilir, prod'da queue versiyonlama gerekir).

**Sonraki faza taşınan bağlam:** Faz 8'in "Mimari karar konuları" bloğundaki **Veri tutarlılığı** maddesi outbox/saga'ya, **Senkron vs asenkron iletişim** maddesi doğrudan bu faza bağlanıyor. Retrieval döngüsü (açılış quiz'i, kart destesi, kapanış teach-back'i) Faz 8'de aynen sürüyor — 8'in "Mimari karar konuları" bloğu zaten teach-back formatında yürütülecek şekilde tasarlandı.

---

## Faz 7 — Message queue & event-driven mimari

Referans akış: sipariş oluşturulur → `order.created` event'i yayınlanır → `notification-service` bildirim gönderir. Sipariş, bildirimin cevabını **beklemez**.

Kapsam kararı: ince bir uygulama dilimi (7.1-7.4) yapılır, gerisi teoride kalır — broker'ın kendisi ekiplerin kurduğu/satın aldığı katman, ama publish/consume'u bir kez gözle görmek async'in ne kazandırdığını somutlaştırıyor.

### 7.1 RabbitMQ + `order.created` publisher — [uygulama] ✅ 2026-08-25

**Problem:** Faz 6'da `product-service` kapalıyken sipariş **oluşturulamıyordu** — orada beklemek doğruydu (fiyat lazım). Bildirim öyle değil: bildirim servisi çökünce siparişin de düşmesi kabul edilemez. Senkron çağrı bunu ayıramaz.

**Yaklaşım:** Event yayınla, cevabını bekleme. Broker araya girince gönderen ile alan birbirini tanımaz (decoupling) ve alan ayakta olmasa bile mesaj kuyrukta bekler (dayanıklılık). Bedeli: eventual consistency + operasyonel yeni bir bileşen.

**Adımlar:** compose.yaml'a `rabbitmq:4-management` (5672 + 15672 UI, healthcheck) → `order-service`'e `spring-boot-starter-amqp` → `config/RabbitConfig.java` (topic exchange `order.events`, JSON message converter) → `event/OrderCreatedEvent.java` (record: `eventId` (UUID), `orderId`, `userId`, `totalAmount`, `createdAt`) → `OrderService.create()` sonunda `RabbitTemplate.convertAndSend(...)`.

**Kritik ayrıntı:** publish **DB commit'inden sonra** yapılır. `create` bilinçli olarak `@Transactional` değil (Faz 6 kararı), yani save zaten commit edilmiş oluyor. Yine de "DB'ye yaz + event yayınla" atomik değildir — publish başarısız olursa sipariş var, event yok. Outbox pattern'in var oluş sebebi tam olarak bu.

**Sürüm notu (doğrulandı):** Spring AMQP 4.1.0'da JSON converter sınıfı `JacksonJsonMessageConverter` (eski `Jackson2JsonMessageConverter` hâlâ var ama yenisi kullanıldı) — jar içeriğinden teyit edildi.

**Kabul kriteri:** ✅ Sipariş oluşturulunca RabbitMQ UI'da kuyrukta 1 mesaj görünüyor; consumer henüz yokken bile sipariş 201 dönüyor.

### 7.2 `notification-service` consumer — [uygulama] ✅ 2026-09-10

**Yaklaşım:** Yeni Maven modülü, **DB'siz** — `spring-boot-starter-amqp` + `spring-boot-starter-webmvc` (health için) + tek `@RabbitListener` sınıfı. `@QueueBinding` ile kendi exchange/queue/binding'ini deklare ediyor; `order-service`'in `RabbitConfig`'ine hiç bağımlı değil (RabbitMQ'nun aynı queue'yu aynı argümanlarla iki kez deklare etmeyi no-op saymasına dayanıyor).

**Dosyalar:** `notification-service/` (elle scaffold — Initializr yerine mevcut iki servisten kopyalanan `mvnw`/`.mvn`/`Dockerfile`), `listener/OrderCreatedListener.java`, `event/OrderCreatedEvent.java` (order-service'tekiyle aynı alanlar, **ayrı sınıf** — JSON wire contract paylaşılıyor, kod değil), `compose.yaml` girdisi (`order`'a hiçbir `depends_on` bağı yok), `.github/workflows/ci.yml` matrix'ine ekleme.

**Kabul kriteri (asıl ders):** ✅ `docker compose stop notification` → sipariş oluştur → **201 döner** (Faz 6'daki senkron çağrının aksine) → servisi başlat → mesaj tüketilir ve loglanır. Kuyruk derinliği UI'da 1 → 0.

**Not al:** Senkron çağrı çağrılanın ayakta olmasını şart koşar; event yalnızca broker'ın ayakta olmasını şart koşar. Bağımlılık kaybolmaz, yer değiştirir.

### 7.3 Idempotency demo — [uygulama] ✅ 2026-09-10

**Problem:** Broker'lar pratikte **at-least-once** teslim eder: ack kaybolursa aynı mesaj tekrar gelir. Consumer "bildirim gönder" yapıyorsa kullanıcı iki mail alır.

**Yaklaşım:** Event'i taşıyan `eventId` ile dedup. Consumer'da `Set<UUID> seen` (`ConcurrentHashMap.newKeySet()`) — in-memory yeterli, gerçek çözüm Redis/DB tablosu (Faz 4'teki `LoginAttemptService` ile aynı sınırlama: restart'ta sıfırlanır, instance başına ayrı).

**Yaşanan hata:** İlk yazımda `if (!seen.add(...))` bloğunun içine "işlendi" logu, dışına hiçbir şey konmuştu — mantık ters çalışıyordu (yeni mesaj sessiz kalıyor, duplicate "işlendi" yazıyordu). `Set.add()`'ın "yeniyse ekler+`true`, zaten varsa `false`" sözleşmesi netleşince düzeltildi.

**Kabul kriteri:** ✅ RabbitMQ UI'dan aynı `eventId`'li mesaj elle iki kez publish edildi; log ilkinde "Notifying...", ikincisinde "Duplicate skipped" yazdı.

**Not al:** At-least-once + idempotent consumer = pratikte exactly-once. Broker'dan exactly-once beklemek yerine consumer'ı tekrara dayanıklı yazmak standart çözümdür.

### 7.4 DLQ + poison message — [uygulama] ✅ 2026-09-10

**Problem:** İşlenemeyen bir mesaj sonsuz retry'a girerse kuyruğu kilitler (poison message) — arkasındaki sağlam mesajlar da işlenemez.

**Yaklaşım:** `order-service/RabbitConfig.java`'da `QueueBuilder.deadLetterExchange("")`/`.deadLetterRoutingKey("order.created.dlq")` + ayrı bir `orderCreatedDlq()` bean'i. Retry limiti **hiç Java kodu gerektirmedi** — Spring Boot 4.1'in kendi AMQP retry desteği (`spring.rabbitmq.listener.simple.retry.*` + `default-requeue-rejected=false`), harici kütüphane (spring-retry) yok; Faz 6'daki `@Retryable` kararıyla aynı çizgide.

**Yaşanan hata (gerçek ders):** Dead-letter argümanlarını eklerken hem `order-service` hem `notification-service` `PRECONDITION_FAILED` ile düştü. Sebep: `order.created.queue` Faz 7.1'den beri argümansız duruyordu ve **RabbitMQ var olan bir queue'nun argümanlarını redeclare ile değiştirmeye izin vermiyor.** Dev'de çözüm: `curl -X DELETE .../api/queues/%2f/order.created.queue` ile silinip restart edilince düzeldi. Prod'da canlı trafik varken silinemez — yeni isimli queue'ya (`v2`) geçiş gerekir.

**Kabul kriteri:** ✅ Bozuk gövdeli mesaj publish edildi; 3 denemeden sonra `order.created.dlq`'da göründü, ana kuyruk boşaldı ve sonraki sağlam mesaj normal işlendi.

### 7.5 Event-driven teori bloğu — teach-back formatında ✅ 2026-09-10

Format: Emre anlattı, asistan follow-up sordu, eksik kalan tamamlandı.

- Senkron vs asenkron karar kriteri, teslim garantileri — sağlam anlatıldı (idempotency ile bağlantı ilk turda kurulamadı, ikinci turda kuruldu).
- Outbox pattern — **semptomunu** ("orphan order: işlem başarılı ama kimse bilmiyor") doğru teşhis etti, **ismini hatırlayamadı**. `notes/kartlar.md`'de `[zayıf]` işaretli.
- Kafka vs RabbitMQ, Saga pattern, eventual consistency/UX — zaman baskısıyla teach-back yerine doğrudan anlatıldı, kart olarak eklendi.

### 7.6 — kapsam dışı (2026-09-10 budaması)

`stock` kolonu + event ile stok düşme ve aynı akışı Kafka ile kurma **yapılmadı**. Gerekçe: ikisi de zaten öğrenilmiş bir dersi ikinci kez ödemek; RabbitMQ vs Kafka farkı 7.5'te teoride konuşuldu.

**Faz 7 tamamlandı.** İki yeni servis davranışı (event publish + consume), idempotency, DLQ — hepsi canlı doğrulandı. Kalan `[teori]` ve mimari karar maddeleri Faz 8'e devrediyor.
