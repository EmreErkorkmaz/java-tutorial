# Öğrenme notları

Tamamlanmış fazların ayrıntılı notları. Bunlar **arşiv değil çalışma materyali** — bir konuya
geri dönüldüğünde okunur; her oturumda baştan okunmasına gerek yok.

Her dosya **`## Faz özeti — devir notu`** bölümüyle başlar: yeni bir oturum açıldığında okunacak
kısım odur (`sed -n '1,25p' notes/faz6-ikinci-servis.md`), dosyanın tamamı değil.

Güncel durum ve kalan işler: [ROADMAP.md](../ROADMAP.md)

**[kartlar.md](kartlar.md) ayrı bir iş görür:** faz notları *ne yaptığımızı* anlatır, kartlar *kendini test etmek* içindir. Kağıt defterin aranabilir kopyası; her oturum açılışındaki 3 soruluk quiz oradan seçilir.

| Dosya | İçerik |
|---|---|
| [kartlar.md](kartlar.md) | **Mülakat kartları** — tüm fazlardan soru→cevap→çapa→projedeki karşılığı. Quiz kaynağı, `[zayıf]` etiketiyle tekrar takibi |
| [faz1-spring-temelleri.md](faz1-spring-temelleri.md) | Ortam kurulumu, katmanlı mimari, DI, JPA entity, DTO, validation, GlobalExceptionHandler, JVM/JIT notu |
| [faz2-test.md](faz2-test.md) | Unit test (Mockito), `@WebMvcTest` + MockMvc, integration test, Spring Boot 4 test API değişiklikleri |
| [faz3-veritabani.md](faz3-veritabani.md) | Postgres + Docker, Flyway, ilişkiler, N+1 ve `@EntityGraph`, index + `EXPLAIN ANALYZE`, transaction, OSIV, pagination, Testcontainers |
| [faz4-guvenlik.md](faz4-guvenlik.md) | Spring Security filter chain, BCrypt, JWT (HS256), rol bazlı yetki, OWASP denetimi + **fullstack güvenlik teorik özeti** |
| [faz5-docker-devops.md](faz5-docker-devops.md) | Multi-stage Dockerfile, compose, env bazlı konfigürasyon, GitHub Actions CI, Actuator |
| [faz6-ikinci-servis.md](faz6-ikinci-servis.md) | order-service, senkron REST çağrısı, timeout/retry/bulkhead, dağıtık N+1, token propagation, HS256'nın sınırı |
| [faz7-event-driven.md](faz7-event-driven.md) | RabbitMQ, notification-service consumer, idempotency (dedup), DLQ + poison message, outbox/saga/Kafka teorisi |
| [faz8-mimari-olgunluk.md](faz8-mimari-olgunluk.md) | Redis cache-aside, distributed tracing (Zipkin), nginx API gateway + rate limiting, CAP teoremi, system design egzersizleri, sekiz maddelik mimari karar konuları (teach-back) |
