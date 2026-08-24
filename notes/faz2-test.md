# Faz 2 — Test kültürü (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** Üç katmanda test: unit (Mockito, Spring context yok), web layer (`@WebMvcTest` + MockMvc), integration (`@SpringBootTest` + Testcontainers, gerçek Postgres).

**Ölçüm:** Mock'lu 3 test 0.08 s; context ayağa kalkan 1 test ~1 s. Piramidin gerekçesi teorik değil, ölçülmüş.

**Kalıcı kararlar:** Test isimleri İngilizce ve `method_whenCondition_expectedResult` formunda. Test kendi bağımlılığını ayağa kaldırır, ortamdan hazır bulmayı beklemez — bu karar CI'da bedava kazanç oldu (Faz 5).

**Sürüm tuzağı:** `@MockBean` → `@MockitoBean`; `@WebMvcTest` paketi değişti. Eski tutorial'lar Boot 4'te uymuyor.

---

## Faz 2 — Test kültürü

- [x] Unit test: `ProductService` — repository mock'lu (JUnit 6 + Mockito 5), Spring context yok → 3 test 0.08 s
- [x] Web layer test: `ProductController` — `@WebMvcTest` + `MockMvc`, service `@MockitoBean` ile mock'lu; status kodu + JSON contract + validation 400'ü assert ediyor
- [x] Integration test: gerçek DB ile tüm stack — `ProductServiceIntegrationTest` (`@SpringBootTest` + Testcontainers), transaction rollback davranışını gerçek Postgres kısıtı (`CHECK price > 0`) tetikleyerek doğruluyor

**Bu sürüme özgü tuzaklar (eski tutorial'lar yanlış):** `@MockBean` kaldırıldı → `@MockitoBean`; `@WebMvcTest` paketi `org.springframework.boot.webmvc.test.autoconfigure` oldu. Test bağımlılıkları `<scope>test</scope>` olduğu için test dosyaları **`src/test/java`** altında olmalı, `src/main` altında derlenmez.

**Not:** Playwright/Cypress/RTL deneyimi burada avantaj — kavramlar tanıdık, sadece araçlar (JUnit/Mockito/MockMvc) yeni.
