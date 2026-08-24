# Faz 6 — İkinci servis & senkron iletişim (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** `order-service` (kendi DB'si, kendi migration'ları), `product-service`'e senkron REST çağrısı, dayanıklılık katmanı (timeout + retry + bulkhead), dağıtık N+1'in toplu endpoint ile çözümü, token propagation, tek compose ile üç konteynerlik yığın, CI matrix.

**Ölçümler:** Bağımlılık kapalıyken istek 14 ms → 782 ms (3 deneme + backoff) — retry'ın kalıcı kesintideki bedeli, circuit breaker'ın gerekçesi. 5 satırlı sipariş: 5 HTTP + 5 SQL → **1 + 1**.

**Kalıcı kararlar:** `OrderService.create` bilinçli olarak `@Transactional` **değil** — uzak çağrı açık transaction içinde DB bağlantısını rehin alır. Servis sınırı = bütünlük sınırı: `order_item.product_id` foreign key değil. Fiyat ve ürün adı snapshot olarak saklanıyor. Uzak 401/403 → **502** (kullanıcı bizde yetkiliydi, arıza servisler arası atlamada).

**Açık zayıflıklar (bilinçli):** HS256 sırrı iki serviste — sahte `ROLE_ADMIN` token'ı üretilerek canlı gösterildi: simetrik imzada doğrulama yeteneği = üretme yeteneği. İki servis aynı DB kullanıcısını paylaşıyor.

**Sonraki faza taşınan bağlam (Faz 7):** Senkron beklemenin doğru olduğu yeri gördük (fiyat lazım) — Faz 7 bunun tersini, beklenmemesi gereken yan etkiyi (bildirim) ele alıyor. `SecurityContextHolder`'ın ThreadLocal olması ve bağlamın servis sınırını kendiliğinden geçmemesi, Faz 8.2'deki tracing'de birebir tekrarlanacak ders.

---

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
  - Build yavaşlığı: container host'un `~/.m2`'sini görmez, bağımlılıklar sıfırdan iner. Çözüm `RUN --mount=type=cache,target=/root/.m2` — her iki Dockerfile'da uygulandı
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


**Faz 6 tamamlandı.** İki servis, iki veritabanı, servisler arası kimlik taşıma, dayanıklılık katmanı ve tek komutla ayağa kalkan yığın — hepsi ölçülerek doğrulandı. Kalan `[teori]` maddeler (circuit breaker, RS256 + JWKS, monolit vs mikroservis) ROADMAP'te.
