# Faz 3 — Gerçek veritabanı & DB derinliği (tamamlandı)

Öğrenme notları. Güncel durum ve kalan işler için [ROADMAP.md](../ROADMAP.md).

## Faz özeti — devir notu

**Ne yapıldı:** H2 → Postgres (Docker), Flyway migration disiplini, `Category` ilişkisi, N+1 tespiti ve çözümü, index + `EXPLAIN ANALYZE`, transaction sınırı, OSIV kapatma, pagination, Testcontainers.

**Ölçümler:** N+1: 21 sorgu → `@EntityGraph` ile 1. Index: 200k satırda `Seq Scan` → `Index Scan`; planlayıcı maliyet tabanlı, index'in varlığı kullanılacağını garanti etmiyor.

**Kalıcı kararlar:** `ddl-auto=validate` + Flyway — uygulanmış migration **asla düzenlenmez**, yeni dosya eklenir. `open-in-view=false` — fetch planı servis katmanında bilinçli veriliyor. Liste endpoint'i `Page` döner (contract kırıldı, testler güncellendi).

**Sonraki faza taşınan bağlam:** OSIV kapalı olduğu için lazy `category` proxy'si servis dışında çözülemez — Faz 8.1'de cache'lenecek şey entity değil **DTO** olmak zorunda.

---

## Faz 3 — Gerçek veritabanı & DB derinliği

- [x] H2 → **PostgreSQL** geçişi, Postgres'i **Docker** ile ayağa kaldırma (postgres:18-alpine, named volume, healthcheck — compose sonradan kök seviyeye taşındı: [compose.yaml](../compose.yaml))
- [x] `ddl-auto=create-drop` yerine **Flyway** ile şema migration'ları — `V1__create_product_table.sql`, `ddl-auto=validate`, `flyway_schema_history` tablosu; DB seviyesinde `NOT NULL` + `CHECK (price > 0)` ile katmanlı savunma (API baypas edilerek doğrulandı)
- [x] İlişkiler — `Category` entity + `@ManyToOne(fetch = LAZY)` + `@JoinColumn`; `V2__add_category.sql` (dolu tabloya zorunlu FK ekleme: nullable ekle → backfill → `SET NOT NULL`; FK index'i Postgres'te elle açılır)
- [x] **N+1 query problemi** — logda ölçüldü: 20 ürün / 20 farklı kategori → **21 sorgu**. Çözüm `@EntityGraph(attributePaths = "category")` ile **1 sorguya** düştü (join'li tek sorgu, doğrulandı).
  - Önemli ayrıntı: sorgu sayısı ürün sayısı değil, **farklı ilişkili kayıt sayısı** + 1 (persistence context aynı entity'yi tekrar sorgulamaz) — bu yüzden az veriyle çalışan dev ortamında problem görünmez
  - Seçenek karşılaştırması: `EAGER` (asla — global ve N+1'i zaten çözmez), `JOIN FETCH` (açık, JPQL), `@EntityGraph` (deklaratif, seçilen), `@BatchSize` (koleksiyonlar için `IN` sorgusu), DTO projection (salt okunur listelerde en hızlısı)
  - `@EntityGraph` sorgu bazlıdır: `findByName` gibi diğer metotlar bilinçli olarak N+1 üretmeye devam ediyor
- [x] Index'ler, `EXPLAIN ANALYZE` ile query planı okuma — `V3__add_product_name_index.sql`, 200k satırla ölçüldü
  - `Seq Scan` → `Index Scan` farkı görüldü; planlayıcı **maliyet tabanlı**, index'in varlığı kullanılacağını garanti etmez
  - Seçicilik (selectivity) kavramı: düşük seçicilikli kolonda index genelde israf. Ama ölçümde category_id sorgusu yine de index scan seçti — sebep fiziksel korelasyon (sıralı insert) + tablonun tamamen cache'te olması (`Buffers: shared hit`, `read=0`). Ezber kural yok, `EXPLAIN ANALYZE` ile ölçülür
  - `ANALYZE` ile istatistik tazeleme; index maliyeti = her INSERT/UPDATE'te ek yazma + disk + bloat/VACUUM
  - Production notu: büyük tabloda `CREATE INDEX CONCURRENTLY` (normal `CREATE INDEX` tabloyu yazmaya kilitler)
- [x] Transaction yönetimi — sınıf seviyesinde `@Transactional(readOnly = true)`, yazma metotlarında `@Transactional`; yetim kayıt senaryosu integration testiyle önce kanıtlandı sonra çözüldü
  - `@Transactional` sınırı yukarı taşır: her `save()` kendi transaction'ı yerine servis metodu tek transaction olur
  - Rollback varsayılanı: **sadece unchecked** exception'larda; checked exception'da commit edilir (`rollbackFor` ile değişir)
  - Self-invocation tuzağı: aynı sınıf içinden `this.method()` çağrısı proxy'yi baypas eder, annotation hiç çalışmaz
  - Import tuzağı: `org.springframework...Transactional` (jakarta olanında `readOnly`/`propagation` yok)
- [x] **OSIV kapatıldı** (`spring.jpa.open-in-view=false`) — Hibernate oturumu artık transaction ile birlikte kapanıyor, isteğin sonuna kadar açık kalmıyor
  - Kapatınca `GET /api/products/{id}` 500 verdi (`LazyInitializationException`): OSIV, `findById`'de eksik olan fetch planını bugüne kadar maskeliyormuş. `findById`'ye de `@EntityGraph` eklenerek çözüldü
  - Kazanım: DB bağlantısı isteğin sonuna kadar tutulmuyor; veri yükleme kararı servis katmanında bilinçli veriliyor
- [x] Pagination & sorting — `Page<Product> findAll(Pageable)` (graph'li), controller'da `@PageableDefault`; `?page=&size=&sort=price,desc` çalışıyor, `api.http`'ye örnek istek eklendi
  - API contract kırıldı (dizi → `content`/`totalElements` nesnesi), `ProductControllerTest` buna göre güncellendi — öngörülen churn
  - Notlar: `OFFSET` derin sayfada yavaşlar (atılan satırları da okur) → gerçek çözüm keyset/cursor pagination; `spring.data.web.pageable.max-page-size` ile istemcinin istediği sayfa boyutu sınırlanmalı; fetch join + pagination `@ManyToOne`'da güvenli ama koleksiyonlarda bozulur
- [x] **Testcontainers** — `TestcontainersConfiguration` + `@ServiceConnection`; test kendi `postgres:18-alpine` container'ını başlatıyor, Flyway migration'ları her koşumda sıfırdan uygulanıyor (migration'ın kendisi de test edilmiş oluyor). Testler artık lokal Postgres'ten bağımsız, CI'da da çalışır.
  - Sürüm notu: Testcontainers 2.x'te modül adları `testcontainers-postgresql` / `testcontainers-junit-jupiter` oldu ve `PostgreSQLContainer` artık generic değil (`<?>` yazılmıyor) — eski örnekler uymuyor.

**Kazanılan kavramlar:** SQL/ilişkisel modelleme, migration disiplini, ORM'in gizli maliyetleri, transaction semantiği, index stratejisi
