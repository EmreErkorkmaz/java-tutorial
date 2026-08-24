# Faz 0-1 — Ortam & Spring Boot temelleri (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** Katmanlı CRUD (`Product` entity → repository → service → controller), record tabanlı DTO'lar, `@RestControllerAdvice` ile merkezi hata yönetimi, bean validation, `api.http` istek koleksiyonu.

**Kalıcı kararlar (sonraki fazları bağlar):** Entity dışarı sızmaz, controller yalnız DTO konuşur (mass assignment'ı da bu kesiyor). Bağımlılıklar constructor injection ile. Hata yanıtı tek yerden üretilir.

**Yaşanan tuzak:** Body'de `id` gönderilse bile yok sayıldığı elle doğrulandı — DTO sınırının işe yaradığının kanıtı.

**Sonraki faza taşınan bağlam:** Hata gövdesi hâlâ `Map<String, Object>` — tipli `record`'a çevirme işi ROADMAP'te kalan iş olarak duruyor.

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

**Kazanılan kavramlar:** DI/IoC container, annotation-driven konfigürasyon, katmanlı mimari (repository/service/controller sorumluluk ayrımı), ORM mapping, merkezi hata yönetimi, DTO pattern, `record`, `Optional`, `final`, `static` factory method, generics, Stream API (lazy + tek geçişli pipeline), method reference

**Yan konular (mülakat notu):** JVM'de derleme akışı — `javac` → bytecode → JVM; interpreter + JIT, tiered compilation (C1/C2), inlining & escape analysis, spekülatif optimizasyon ve deoptimization, warm-up etkisi, JIT vs AOT (GraalVM Native Image) trade-off'u
