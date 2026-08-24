# CLAUDE.md — bu repoda çalışma kuralları

Java + Spring Boot öğrenme projesi (Emre'nin fullstack geçişi). İki servis, tek sistem.
**Bu bir öğretim repo'su:** amaç çalışan kod değil, savunulabilir kod.

## Oturum başlangıcı

1. [ROADMAP.md](ROADMAP.md) oku — durum, kalan iş, oturum protokolü orada.
2. Bir önceki fazın devir notunu oku: `sed -n '1,25p' notes/faz6-ikinci-servis.md` (ilgili faz dosyası).
3. Gerisini **ihtiyaç halinde** oku. Baştan bütün `notes/` veya bütün `src/` okuma — token yakar, faydası yok.

Her faz kendi oturumunda çalışılır; geçmiş sohbet taşınmaz, bağlam devir notundan gelir.

## Çalışma kuralları

- **Kodu Emre yazar.** Sohbette kısa comment'li, olduğu gibi yapıştırılabilir snippet ver; dosyayı sen yazma. İstisna: config, tooling, docs, mekanik düzeltme (dosya taşıma, tek satır fix).
- **Asla ```diff bloğu yok.** Normal ```java / ```properties / ```sql blokları; yeri `// ---- ADD: inside JwtConfig, above jwtDecoder() ----` gibi marker comment ile göster. Silmeleri düz yazıyla anlat.
- **Problem → yaklaşım → kod.** Yeni konu önce çözdüğü problemle açılır, mümkünse gözle görülür şekilde (başarısız test, SQL logu, query planı, ölçülen süre). Sonra yaklaşım + alternatifler, en son bağımlılık ve kod.
- **Dil:** sohbet Türkçe, teknik terimler İngilizce ("load balancer", "horizontal scaling"). **Kodun içi tamamen İngilizce** — değişken, metot, test adları ve tüm comment'ler dahil. Test adı: `method_whenCondition_expectedResult`.
- **Comment yoğunluğu bilinçli olarak yüksek** — dosyalar aynı zamanda tekrar notu. "Kod kendini anlatır" kuralı burada geçerli değil.
- **Diyagram metin olarak** (ASCII/kutu çizimi), uzun paragraf yerine. Mevcut kodu anlatırken `ProductClient.java:20` gibi file:line ver — önce satırı doğrula.
- **"Not al:"** — oturum başına 2-3 tane, index-kart boyunda mülakat notu. Trade-off, karışan tanım, sürprizli mekanizma; sürüm/tooling trivia değil.
- **Karşılaştırmalar Express/Mongoose üzerinden** (NestJS değil).
- **SQL ve sıra dışı shell komutları açıklanır** — çıplak SQL verilmez.
- **Uygulama zamanı üretim olasılığına göre bütçelenir:** mid-level bir dev'in işte yazacağı şey uygulanır; ekiplerin konfigüre ettiği/satın aldığı şey teoride kalır.

## Sürüm doğrulama (bu repoda sık ısıran hata)

Spring Boot **4.1**, Java 21, Testcontainers 2.x, JUnit 6. Eğitim verisindeki örneklerin çoğu Boot 3 dünyasından ve **burada derlenmiyor** (yaşanmış örnekler: `@MockBean` → `@MockitoBean`, `@WebMvcTest` paketi değişti, `PostgreSQLContainer` artık generic değil).

Kural: **sınıf/annotation/paket adı hatırlanmaz, doğrulanır.** `./mvnw dependency:tree`, jar içeriği veya IDE completion ile teyit et; emin değilsen snippet'te bunu açıkça söyle.

## Referans haritası

**Dokümanlar**

| Yol | İçerik |
|---|---|
| [ROADMAP.md](ROADMAP.md) | Durum, kalan iş (problem/yaklaşım/dosya/kabul kriteri), oturum protokolü, faz sonu checklist |
| [notes/README.md](notes/README.md) | Tamamlanmış fazların not indeksi |
| `notes/fazN-*.md` | Faz notları; her dosya `## Faz özeti — devir notu` ile başlar |

**Sistem**

| Ne | Nerede |
|---|---|
| Ürün kataloğu + auth (token üreten servis) | `product-service/` — 8080, `productdb` |
| Sipariş | `order-service/` — 8081, `orderdb` |
| Tüm yığın | kök `compose.yaml` (postgres + product + order, üçü healthcheck'li), `docker/init-db.sql` `orderdb`'yi açar |
| CI | `.github/workflows/ci.yml` — matrix: iki servis paralel, `mvnw -B clean verify` + `docker build` |
| İstek koleksiyonu | `product-service/api.http` (IntelliJ HTTP Client) |

**product-service — konu → dosya**

| Konu | Dosya |
|---|---|
| CRUD + transaction sınırı + `@EntityGraph` (N+1) | `service/ProductService.java`, `repository/ProductRepository.java` |
| REST contract, pagination, toplu okuma (`/by-ids`) | `controller/ProductController.java` |
| Güvenlik kuralları (URL seviyesi, CORS, `/error`) | `config/SecurityConfig.java` |
| JWT üretimi/doğrulaması (HS256) | `config/JwtConfig.java`, `service/TokenService.java` |
| Kayıt/login, brute force sayacı | `controller/AuthController.java`, `service/AppUserService.java`, `service/LoginAttemptService.java` |
| Kimlik modeli | `model/AppUser.java`, `model/Role.java`, `service/AppUserDetailsService.java` |
| Hata → HTTP eşlemesi | `exception/GlobalExceptionHandler.java` |
| Şema | `src/main/resources/db/migration/V1..V4` (product, category, name index, user tabloları) |

**order-service — konu → dosya**

| Konu | Dosya |
|---|---|
| Sipariş kuralları, uzak fiyat okuma, iade | `service/OrderService.java` (bilinçli olarak `@Transactional` **değil**) |
| Domain | `model/Order.java`, `model/OrderItem.java`, `model/OrderStatus.java` |
| product-service çağrısı + token propagation | `client/ProductClient.java`, `config/RestClientConfig.java` |
| Timeout / retry / bulkhead | `config/ResilienceConfig.java` |
| Güvenlik + JWT doğrulama | `config/SecurityConfig.java`, `config/JwtConfig.java` |
| Şema | `db/migration/V1..V3` (order, order_items ayrımı, user+created index) |

**Testler**

| Tip | Örnek |
|---|---|
| Unit (mock'lu, context yok) | `product-service/.../service/ProductServiceTest.java`, `order-service/.../service/OrderServiceTest.java` |
| Web layer (`@WebMvcTest`) | `.../controller/ProductControllerTest.java`, `.../controller/OrderControllerTest.java` |
| Integration (Testcontainers) | `product-service/.../ProductServiceIntegrationTest.java`, `TestcontainersConfiguration.java` (her iki serviste) |

**Komutlar**

| Komut | Ne yapar |
|---|---|
| `docker compose up -d --build` | Tüm yığın (kök dizinden) |
| `./mvnw clean verify` | Tek servisin testleri (servis dizininden; Testcontainers kendi Postgres'ini açar) |
| `docker exec -it product-db psql -U product -d productdb` | DB'ye bağlan (`-d orderdb` diğeri) |
| `docker compose logs -f order` | Tek servisin logu |

**Bilinen ve bilinçli zayıflıklar** (öğretim amaçlı, "bug" diye düzeltmeye kalkma — gerekçeleri `notes/faz6-ikinci-servis.md` devir notunda):
JWT sırrı iki serviste paylaşık (HS256'nın sınırı gösterildi), iki servis aynı DB kullanıcısını kullanıyor, sırlar repoda düz metin (dev-only), `compose.yaml`'da `container_name` sabit.
