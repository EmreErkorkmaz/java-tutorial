# Öğrenme notları

Tamamlanmış fazların ayrıntılı notları. Bunlar **arşiv değil çalışma materyali** — bir konuya
geri dönüldüğünde okunur; her oturumda baştan okunmasına gerek yok.

Her dosya **`## Faz özeti — devir notu`** bölümüyle başlar: yeni bir oturum açıldığında okunacak
kısım odur (`sed -n '1,25p' notes/faz6-ikinci-servis.md`), dosyanın tamamı değil.

Güncel durum ve kalan işler: [ROADMAP.md](../ROADMAP.md)

| Dosya | İçerik |
|---|---|
| [faz1-spring-temelleri.md](faz1-spring-temelleri.md) | Ortam kurulumu, katmanlı mimari, DI, JPA entity, DTO, validation, GlobalExceptionHandler, JVM/JIT notu |
| [faz2-test.md](faz2-test.md) | Unit test (Mockito), `@WebMvcTest` + MockMvc, integration test, Spring Boot 4 test API değişiklikleri |
| [faz3-veritabani.md](faz3-veritabani.md) | Postgres + Docker, Flyway, ilişkiler, N+1 ve `@EntityGraph`, index + `EXPLAIN ANALYZE`, transaction, OSIV, pagination, Testcontainers |
| [faz4-guvenlik.md](faz4-guvenlik.md) | Spring Security filter chain, BCrypt, JWT (HS256), rol bazlı yetki, OWASP denetimi + **fullstack güvenlik teorik özeti** |
| [faz5-docker-devops.md](faz5-docker-devops.md) | Multi-stage Dockerfile, compose, env bazlı konfigürasyon, GitHub Actions CI, Actuator |
| [faz6-ikinci-servis.md](faz6-ikinci-servis.md) | order-service, senkron REST çağrısı, timeout/retry/bulkhead, dağıtık N+1, token propagation, HS256'nın sınırı |
