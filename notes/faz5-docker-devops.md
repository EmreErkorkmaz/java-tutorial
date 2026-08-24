# Faz 5 — Docker & DevOps (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** Multi-stage `Dockerfile` (126 MB runtime image, root olmayan kullanıcı), compose ile app + Postgres, env variable tabanlı konfigürasyon, GitHub Actions CI (`mvnw clean verify` + `docker build`), Actuator health.

**Kalıcı kararlar:** Konfigürasyon repoya değil ortama ait — `SPRING_DATASOURCE_URL` gibi env variable'lar `application.properties`'i eziyor. `compose.yaml` **lokal geliştirme aracı**, CI onu kullanmaz; CI temiz makinede build + test yapar. Dockerfile katman sırası: önce `pom.xml` + `dependency:go-offline`, kaynak sonra.

**Sonraki faza taşınan bağlam:** Image registry'e push ve gerçek deploy yapılmadı — Faz 9.1'deki portfolyo projesiyle birleştirildi. `liveness` vs `readiness` ayrımı Faz 8'deki K8s/gateway tartışmasının girişi.

---

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
- [x] **Actuator** — `/actuator/health` + compose healthcheck; sadece `health,info` expose ediliyor (diğer endpoint'ler bilgi sızdırır)
  - `liveness` = "öldür ve yeniden başlat", `readiness` = "trafiği kes ama bekle". Load balancer/K8s Service readiness'a bakar
