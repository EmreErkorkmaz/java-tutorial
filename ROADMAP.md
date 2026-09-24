# Roadmap & Checklist

Java + Spring Boot ve genel backend/mimari öğrenme sürecinin ilerleme takibi.
**Bu dosya oturum başında okunacak tek dosyadır** — bağlamı baştan anlatmaya gerek yok.

**Hedef:** mid-level fullstack rolüne geçiş, orta vadede solutions architecture yönü.
**Yaklaşım:** Java'yı ve backend/altyapı konularını ayrı ayrı teorik olarak değil, **tek bir sistem** (`product-service` + `order-service`) üzerinde birlikte ilerletmek. Her faz hem yeni bir Java/Spring yeteneği hem bir backend kavramı ekler.

**Etiketler:** **[uygulama]** kod yazılır ve entegre edilir · **[teori]** anlatım + quiz, kod yazılmaz · **[opsiyonel]** istenirse uygulamaya çevrilir

---

## Şu anki durum (2026-09-15)

```
java-tutorial/
├── product-service/      # 8080 — ürün kataloğu, auth (JWT üretimi), productdb, Redis cache
├── order-service/        # 8081 — sipariş, product-service'e senkron REST, orderdb,
│                         #        order.created event publisher
├── notification-service/ # 8082 — DB'siz, tek @RabbitListener (event consumer)
├── nginx/nginx.conf       # tek giriş noktası (:8090) — routing + rate limiting
├── compose.yaml          # postgres + rabbitmq + redis + zipkin + gateway + üç servis
├── docker/init-db.sql    # orderdb'yi oluşturur (yalnızca boş volume'de çalışır)
└── notes/                # faz notları + kartlar.md (quiz kaynağı)
```

Stack: Spring Boot 4.1.0, Java 21, Maven, PostgreSQL 18, Flyway, Spring Security (JWT HS256), RabbitMQ 4, Redis 8, Zipkin (Micrometer Tracing + Brave), nginx (API gateway), Testcontainers, GitHub Actions (matrix ile üç servis paralel).

| Komut | Ne yapar |
|---|---|
| `docker compose up -d --build` | Tüm yığın (kök dizinden) |
| `./mvnw clean verify` | Tek servisin testleri (servis dizininden; Testcontainers kendi Postgres'ini açar) |
| `product-service/api.http`, `order-service/api.http` | IntelliJ HTTP Client istek koleksiyonu (Ultimate gerektirir; Community'de curl ile) |
| `http://localhost:8090` | nginx gateway — tek porttan `/api/products`, `/api/orders` |
| `http://localhost:9411` | Zipkin UI — trace ağacı |
| `http://localhost:15672` | RabbitMQ management UI |

Test durumu: 23 `@Test` (13 product-service, 10 order-service, notification-service'te henüz test yok), CI yeşil.

| Faz | Durum |
|---|---|
| 0-1 Ortam & Spring temelleri | ✅ [notes/faz1-spring-temelleri.md](notes/faz1-spring-temelleri.md) |
| 2 Test kültürü | ✅ [notes/faz2-test.md](notes/faz2-test.md) |
| 3 Veritabanı derinliği | ✅ [notes/faz3-veritabani.md](notes/faz3-veritabani.md) |
| 4 Auth & güvenlik | ✅ [notes/faz4-guvenlik.md](notes/faz4-guvenlik.md) |
| 5 Docker & DevOps | ✅ [notes/faz5-docker-devops.md](notes/faz5-docker-devops.md) |
| 6 İkinci servis & senkron iletişim | ✅ [notes/faz6-ikinci-servis.md](notes/faz6-ikinci-servis.md) |
| 7 Message queue & event-driven | ✅ [notes/faz7-event-driven.md](notes/faz7-event-driven.md) |
| 8 Mimari olgunluk & system design | ✅ [notes/faz8-mimari-olgunluk.md](notes/faz8-mimari-olgunluk.md) |
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

**Oturum şekli (2026-09-10'dan itibaren):** açılış quiz'i (3 kart) → mimari çapa → ana blok → kapanış teach-back'i. Ayrıntısı [CLAUDE.md](CLAUDE.md#oturum-şablonu-2026-09-10dan-itibaren)'de; quiz kaynağı [notes/kartlar.md](notes/kartlar.md).

**Faz sonunda yaz (devir notu — bir sonraki oturumun tek girdisi):**
İlgili `notes/fazN-*.md` dosyasının başına, başlığın hemen altına `## Faz özeti — devir notu` bölümü. Beş başlık, her biri 1-3 cümle:
- **Ne yapıldı** — maddeler değil, tek paragraf
- **Ölçümler** — sayı varsa sayı (öncesi → sonrası)
- **Kalıcı kararlar** — sonraki fazları bağlayan kurallar
- **Tuzaklar / açık zayıflıklar** — bilinçli bırakılanlar dahil
- **Sonraki faza taşınan bağlam** — bir sonraki fazın hangi maddesine bağlanıyor

**Faz sonu checklist:**
- [ ] `./mvnw clean verify` üç serviste de yeşil
- [ ] ROADMAP'te checkbox'lar ve faz tablosu güncel
- [ ] Öğrenilenler `notes/fazN-*.md` içine yazıldı (ROADMAP şişirilmedi), devir notu güncellendi
- [ ] Fazın "Not al" kartları `notes/kartlar.md`'ye eklendi (`[zayıf]` etiketleri güncel)
- [ ] `notes/README.md` satırı güncel
- [ ] Commit atıldı

---

## Günlük Java turu (2026-09-22'den itibaren)

Sebep: iş ilanı taramasında (Notion → İş takibi → [Learning Roadmap](https://app.notion.com/p/2767699cf7f34d6e87f811eafb9105be)) Java 3 ilan / öncelik **Orta** / mülakat sırası 4 çıkıyor; AWS 7 ilan / **Yüksek** / sıra 1. Sekiz faz Java yapıldı, cloud tarafına hiç girilmedi. Bu repo artık ana blok değil, **günlük kuyruk**: öğrenilen soğumasın diye her gün kısa bir geri çağırma turu yapılır, sıradaki asıl konu Notion'dan gelir.

```
Günlük blok (1.5 saat)
├─ 0-75 dk   Notion sırasındaki konu (AWS, Kubernetes, tRPC, ...)
└─ 75-90 dk  java-tutorial turu:
              ├─ 3 kart (notes/kartlar.md, [zayıf] olanlar önce)
              └─ şunlardan BİRİ: mock mülakatın bir adımı · bir [teori] maddesi · ApiError refactor'ü
```

**Sıradaki uzun oturumun adayları** (günlük turda çıktı, 15 dakikaya sığmadı):
- [ ] **RS256 + JWKS + `aud` tek diyagramda** — `aud` kartı 3 kez `[zayıf]` kaldı (2026-09-19/22/23): "üretme yetkisi" ile "amaç dışı kullanım" hâlâ karışıyor. AWS Gün 1'deki GitHub OIDC akışı (imza + `aud: sts.amazonaws.com` kontrolü) örnek olarak kullanılır. Aşağıdaki RS256 [teori] maddesini de kapatır. Encoding/signature kartı da buraya bağlanır (2026-09-24: tanımlar doğru ama "payload base64 = herkes okur" uygulaması üçüncü kez kurulamadı).
- [ ] **`readOnly` transaction + `save()` deneyi** — sınıf seviyesi `@Transactional(readOnly = true)` altında annotation'sız bir `updatePrice()` yazılır, SQL logu açılır: `save()` sessizce mi yazmıyor, `saveAndFlush()` Postgres'ten `cannot execute UPDATE in a read-only transaction` alıyor mu? Sonuç kesinleşince `@Transactional` rollback kartına eklenir (2026-09-23'te ezberden kesin konuşulmadı).

**Kural: tur 15 dakikayı aşarsa kesilir**, kalan kısım ertesi güne. Burnout'u yaratan şey blokların uzaması. Uzun oturum (açılış quiz'i + mimari çapa + ana blok + teach-back) haftada en fazla bir kez yapılır; ayrıntısı [CLAUDE.md](CLAUDE.md#oturum-şablonu-2026-09-10dan-itibaren)'de.

---

## Kalan iş — eski fazlardan artanlar

Hepsi günlük tura sığacak boyutta. Cloud/altyapı maddeleri buradan **çıkarıldı** (2026-09-22): Kubernetes temelleri, 9.6'daki managed servis listesi, Prometheus + Grafana ve "image registry'e push" artık Notion'daki *AWS temel servisleri* / *GCP temel servisleri* / *Kubernetes temelleri* kartlarında takip ediliyor — Java işi değiller ve iki yerde takip edilmeleri çift bakım demekti.

- [ ] **Hata response body'sini tipli `record`'a çevir** — [uygulama] [~30 dk, ısınma işi]
  - Şu an `GlobalExceptionHandler` `Map<String, Object>` dönüyor: alan adları derleyici tarafından kontrol edilmiyor, contract yalnızca kodu okuyarak anlaşılıyor.
  - Dosyalar: her iki servisteki `exception/GlobalExceptionHandler.java`, yeni `dto/ApiError.java` (+ validation hatası için alan listesi taşıyan varyant).
  - Kabul kriteri: 404/400/409/429 yanıt gövdeleri aynı kalır (mevcut testler değişmeden geçer), tip güvenli hale gelir.
- [ ] **Test piramidi** — [teori] Faz 2'de pratikte ölçüldü (mock'lu 3 test 0.08 s vs context ayağa kalkan test ~1 s); teorisi konuşulacak.
- [x] **Isolation level'lar & optimistic locking (`@Version`)** — [teori] ✅ 2026-09-19'da flash sale mock'unda kapandı (kartlar: lost update, locking seçimi). READ COMMITTED varsayılanı, lost update senaryosu, optimistic vs pessimistic locking.
- [ ] **Refresh token** — [teori] access 15 dk + sunucuda saklanan, iptal edilebilir refresh token. Uygulanmaz: production'da IdP işi.
- [ ] **Cookie tabanlı authentication** — [teori] 2026-09-24'te istendi. Sunucu tarafı session (`JSESSIONID`, Express'te `express-session`) ile header'daki Bearer JWT'nin karşılaştırması: state nerede tutuluyor, iptal etme ve yatay ölçekleme bedeli. Cookie bayrakları: `HttpOnly` (XSS'e karşı), `Secure`, `SameSite`. Cookie'ye geçince CSRF'in geri gelmesi (Faz 4'teki "token tabanlı API'da CSRF yapısal olarak yok" kartının tersi). Refresh token maddesiyle birlikte çalışılır, çünkü refresh token genelde `HttpOnly` cookie'de taşınır.
- [ ] **Monolit vs mikroservis trade-off'ları** — [teori] kendi üç servisimizin bedeli üzerinden konuşulur.
- [ ] **Circuit breaker** — [teori] Spring core'da yok (Resilience4j gerekir). CLOSED → OPEN → HALF_OPEN; retry geçici hatayı, breaker kalıcı kesintiyi çözer. Faz 6'da ölçülen "kesintide 14 ms → 782 ms" bunun gerekçesi.
- [ ] **RS256 + JWKS** — [teori] Faz 6'da HS256'nın sınırı canlı gösterildi (doğrulama yeteneği = üretme yeteneği). Asimetrikte private key yalnızca üreticide; JWKS ile public key `/.well-known/jwks.json`'dan dağıtılır, `kid` header'ı hangi anahtar olduğunu söyler; Spring tarafı tek property: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (decoder'ı Boot kendisi kurar, elle `JwtConfig` gerekmez). Keycloak/Auth0/Cognito hepsi böyle çalışır — gerçek projede bu mekanizma yazılmaz, yapılandırılır.

---

## Faz 9 — mülakat hazırlığı

- [ ] **9.1 Fullstack portfolyo projesi** — ⏸️ **askıya alındı (2026-09-22).** Gerekçe: cloud konuları teorik ilerleme kararı alındı (mülakatta canlı deployment istenmiyor), ve bu repo zaten GitHub'da üç servis + CI + Testcontainers kanıtı sunuyor. Tekrar açılırsa "React frontend + mevcut backend, deploy edilmiş" olarak açılır.
- [ ] **9.2 Java'da Collections akıcılığı** — [drill] sıraya alınmaz; somut bir mülakat öncesinde tekrar edilir. Hedef geniş DSA **değil**: List/Map/Set/Stream API'yi duraksamadan kullanabilmek.
- [x] **9.3 Java/Spring mülakat soruları** ✅ tamamlandı (2026-09-16) — JVM (heap/stack, GC), `equals`/`hashCode`, immutability, concurrency temelleri (thread, `synchronized`, `CompletableFuture`), Spring bean lifecycle & scope'lar. Hepsi kart oldu (`notes/kartlar.md`). En değerli sentez: bean lifecycle diyagramı — proxy'nin tam olarak `BeanPostProcessor.postProcessAfterInitialization()`'da sarıldığını göstermesi, sömestr boyunca tekrar eden self-invocation/`RestClient.builder()`/final-sınıf tuzaklarının ortak kökünü tek diyagramda birleştirdi.
- [ ] **9.4 System design mock mülakatları** — günlük turun ana aktivitesi. Yapılanlar: URL kısaltıcı, chat sistemi (2026-09-17), flash sale (2026-09-19 + 2026-09-22, tamamlandı: kapı/hakem ayrımı, lost update, kilit seçimi, rezervasyon süresi ve sweeper yarışı, Redis'in çökmesi). Sonraki mock için aday konular: haber akışı (fan-out), dosya/medya depolama.
- [ ] **9.5 Behavioral** — 2026-09-22'de Notion'a taşındı (*Mülakat anlatısı (behavioral)* kartı): iş arama işi, Java işi değil.
- [ ] **9.6 Yaygın cloud/managed servisler** — 2026-09-22'de Notion'a taşındı. Sekiz maddelik sıra (CDN → managed LB/API Gateway → S3 → RDS → SQS/SNS → Secrets Manager → ECS vs K8s → CloudWatch) ve her maddenin bu repodaki self-hosted karşılığı *AWS temel servisleri* kartının gövdesinde.
