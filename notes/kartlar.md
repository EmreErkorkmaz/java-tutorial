# Mülakat kartları

Kağıt defterin **aranabilir dijital ikizi**. Elle yazmaya devam ediyorsun (yazmak öğrenmenin parçası); bu dosya "o notu nereye yazmıştım" sorununu çözmek ve oturum açılışındaki quiz'e kaynak olmak için var.

**Kart yapısı:** `**S:**` soru · `**C:**` cevap · `**Çapa:**` (varsa) hep aynı kalan benzetme · `**Projede:**` bu projedeki karşılığı.

**Quiz protokolü:** her oturum başında buradan 3 soru — biri son fazdan, biri ortadan, biri eskiden. Bilemediğin kartın başına `[zayıf]` eklenir ve sonraki oturumlarda önce o sorulur; iki kez üst üste doğru cevaplayınca etiket düşer. `[zayıf]` yığılan bir konu, yol haritasında budadığımız yere geri dönme sinyalidir.

**Kanonik benzetme kuralı:** bir kavram için bir benzetme. Her seferinde yeni metafor uydurmak yapışmayı bozar; `**Çapa:**` satırındaki benzetme her anlatımda aynı kalır.

---

## Spring & Java temelleri (Faz 1)

**S:** Bağımlılığı pom.xml'e eklemek, tek satır kod yazmadan davranışı nasıl değiştiriyor?
**C:** Auto-configuration. Her jar'ın içinde `META-INF/spring/...AutoConfiguration.imports` dosyası var; `@ConditionalOnClass` (bu sınıf classpath'te mi) ve `@ConditionalOnMissingBean` (kullanıcı kendi bean'ini koymuş mu) ile şartlı devreye giriyor. Kendi bean'ini tanımladığın an Spring geri çekilir.
**Çapa:** Kutudan çıkınca kendini kuran mobilya — ama sen kendi vidanı takarsan o durur, karışmaz.
**Projede:** Faz 4, `spring-boot-starter-security` eklenince tüm endpoint'ler bir anda korumaya girdi.

**S:** Entity'yi controller'dan dışarı vermek neden yanlış, DTO ne kazandırıyor?
**C:** İki şey: (1) mass assignment kesilir — istemcinin gönderdiği alanlar doğrudan entity'ye yazılmaz, (2) API contract entity şemasından ayrışır, DB değişikliği istemciyi kırmaz.
**Projede:** Faz 1 — `ProductRequest`/`ProductResponse`; body'de `id` gönderilse bile yok sayıldığı elle doğrulandı.

**S:** Neden constructor injection, field injection değil?
**C:** Bağımlılık `final` olabiliyor (immutable), zorunlu bağımlılık derleme zamanında belli oluyor, ve testte mock'u constructor'dan geçirebiliyorsun — Spring olmadan da nesneyi kurabiliyorsun. Enjekte edilen bir bağımlılık **asla `static` olmaz**; `static` sadece gerçek sabitler için (routing key, exchange adı gibi).
**Çapa:** Constructor = malzemeyi kapıda teslim almak. `static`/field injection = mutfağa arkadan gizlice bırakılması; kim ne koydu bilemezsin.
**Projede:** Faz 7 — `OrderService`'e `RabbitTemplate` eklenince testler patladı, çünkü `@InjectMocks` yeni parametreyi mock'suz bıraktı. Constructor sayesinde hata derleme/test aşamasında görünür oldu.

**S:** JVM kodu nasıl çalıştırıyor, "warm-up" neden bir maliyet kalemi?
**C:** `javac` → bytecode → JVM. JVM önce interpreter ile yürütür, sık çalışan kodu JIT ile makine koduna çevirir (tiered: C1 hızlı derler, C2 iyi optimize eder). Yani ilk istekler yavaş, sistem "ısındıkça" hızlanır. Serverless/kısa ömürlü container'da bu bedel her soğuk başlangıçta yeniden ödenir — GraalVM AOT native image bu yüzden var.
**Projede:** Faz 1 yan konu; Faz 8'de runtime seçimi tartışmasına bağlanıyor (Go tek binary, JVM warm-up).

---

## Test (Faz 2)

**S:** Test piramidini neden bu şekilde kuruyoruz — ölçülmüş gerekçe ne?
**C:** Maliyet farkı ölçüldü: mock'lu 3 unit test 0.08 s, Spring context ayağa kaldıran 1 test ~1 s. Yani altta çok sayıda hızlı test, üstte az sayıda pahalı test. Üst katman bir şeyi kanıtlamıyorsa yazılmaz.
**Çapa:** Piramit: taban geniş ve ucuz, tepe dar ve pahalı. Ters piramit = her commit'te dakikalar bekleyen CI.
**Projede:** Faz 2 — `ProductServiceTest` (mock), `ProductControllerTest` (`@WebMvcTest`), `ProductServiceIntegrationTest` (Testcontainers).

**S:** Testcontainers'ın kazandırdığı asıl ilke ne?
**C:** Test kendi bağımlılığını **kendisi ayağa kaldırır**, ortamda hazır bulmayı beklemez. Lokal Postgres kurulu olsun/olmasın aynı çalışır ve CI'da ekstra kurulum gerekmez (runner'da Docker zaten var). Yan kazanç: migration'lar her koşumda sıfırdan uygulandığı için migration'ın kendisi de test edilmiş oluyor.
**Projede:** Faz 2'de eklendi, Faz 5'te CI'da bedava kazanca dönüştü.

---

## Veritabanı & JPA (Faz 3)

**S:** N+1 nedir ve sorgu sayısı neye bağlıdır?
**C:** İlişkili veriyi döngü içinde tek tek çekmek. Ölçüldü: 20 ürün / 20 farklı kategori → **21 sorgu**. Kritik ayrıntı: sorgu sayısı ürün sayısı değil, **farklı ilişkili kayıt sayısı + 1** (persistence context aynı entity'yi tekrar sorgulamaz). Bu yüzden az veriyle çalışan dev ortamında problem görünmez.
**Çapa:** Markete 20 kez ayrı ayrı gitmek vs tek listeyle bir kez gitmek.
**Projede:** Faz 3 — `@EntityGraph(attributePaths = "category")` ile 21 → 1 sorgu.

**S:** N+1 çözüm seçeneklerini ne zaman hangisini seçerek ayırıyorsun?
**C:** `EAGER` asla (global karar, N+1'i zaten çözmez), `JOIN FETCH` açık ve JPQL içinde, `@EntityGraph` deklaratif (biz bunu seçtik), `@BatchSize` koleksiyonlar için `IN` sorgusuna çevirir, DTO projection salt okunur listelerde en hızlısı. `@EntityGraph` **sorgu bazlıdır** — eklemediğin metotlar N+1 üretmeye devam eder.
**Projede:** Faz 3 — `findByName` bilinçli olarak graph'sız bırakıldı.

**S:** Index eklediysen sorgu onu kullanır mı?
**C:** Garanti yok. Planlayıcı **maliyet tabanlıdır**: seçicilik, tablo boyutu, istatistiklerin tazeliği, verinin fiziksel sıralılığı ve tablonun cache'te olup olmaması kararı değiştirir. Ezber kural yok — `EXPLAIN ANALYZE` ile ölçülür.
**Projede:** Faz 3 — 200k satırda `Seq Scan` → `Index Scan` geçişi gözlendi; düşük seçicilikli `category_id` sorgusu beklenenin aksine index'i kullandı (fiziksel korelasyon + `Buffers: shared hit`).

**S:** Index'in bedeli nedir?
**C:** Okumayı hızlandırır, **yazmayı yavaşlatır**: her INSERT/UPDATE'te index de güncellenir, disk yer tutar, zamanla bloat olur (VACUUM). Production'da büyük tabloda `CREATE INDEX CONCURRENTLY` kullanılır, çünkü normal `CREATE INDEX` tabloyu yazmaya kilitler.

**S:** `@Transactional` hangi durumda rollback yapar?
**C:** Varsayılan olarak **sadece unchecked** (RuntimeException) exception'larda. Checked exception atılırsa transaction **commit edilir** — `rollbackFor` ile değiştirilir. Sık karıştırılan nokta budur.
**Projede:** Faz 3 — yetim kayıt senaryosu integration testiyle önce kanıtlandı, sonra çözüldü.

**S:** `[zayıf]` Proxy tabanlı annotation'ların self-invocation tuzağı nedir? (2026-09-10 tur 2: JPA'ya atfedildi — mekanizma Spring AOP proxy'si, JPA'nın bununla hiçbir ilgisi yok)
**C:** `@Transactional`/`@Cacheable`/`@Async` Spring proxy'si üzerinden çalışır. Aynı sınıf içinden `this.method()` çağırırsan proxy devreye girmez ve annotation **sessizce** hiç çalışmaz — hata da almazsın, en tehlikeli tarafı bu.
**Çapa:** Kendi ofisinden kendine telefon etmek — santral (proxy) araya girmez, yani santralin yaptığı hiçbir şey olmaz.
**Projede:** Faz 3'te `@Transactional` için görüldü; Faz 8.1'de `@Cacheable` için aynısı geçerli olacak.

**S:** OSIV (open-in-view) nedir, kapatınca ne oldu ve ne kazandık?
**C:** Açıkken Hibernate oturumu isteğin sonuna kadar açık kalır, lazy alanlar controller'da bile yüklenebilir. Kapattığımızda `GET /api/products/{id}` 500 verdi (`LazyInitializationException`) — yani OSIV, `findById`'de **eksik olan fetch planını maskeliyormuş**. Kazanç: DB bağlantısı isteğin sonuna kadar tutulmuyor ve ne yükleneceği servis katmanında bilinçli karar oluyor.
**Projede:** Faz 3 — `spring.jpa.open-in-view=false`; Faz 8.1'de cache'lenecek şeyin entity değil **DTO** olmasının sebebi de bu (lazy proxy serialize edilemez).

**S:** `OFFSET` tabanlı pagination'ın sınırı nedir?
**C:** Derin sayfalarda yavaşlar, çünkü DB atladığı satırları da okumak zorunda (`OFFSET 100000` → 100k satır okunup çöpe atılır). Gerçek çözüm keyset/cursor pagination ("son gördüğüm id'den sonrasını ver"). Ayrıca istemcinin istediği sayfa boyutu sınırlanmalı.
**Projede:** Faz 3 — `Page` + `@PageableDefault`.

**S:** Uygulanmış bir migration'da hata görürsen ne yaparsın?
**C:** Dosyayı **asla düzenlemezsin** — Flyway uygulanan dosyanın checksum'ını tutar, değişirse başlatmayı reddeder. Düzeltme yeni bir migration dosyası olarak eklenir. Şemanın sahibi Flyway, Hibernate sadece doğrular (`ddl-auto=validate`).

**S:** Dolu bir tabloya zorunlu (NOT NULL) foreign key nasıl eklenir?
**C:** Tek statement'la eklenemez, mevcut satırların değeri yok. Standart üç adım: **nullable ekle → backfill → SET NOT NULL**, sonra FK constraint. Ayrıca Postgres foreign key'i otomatik indexlemez (MySQL/InnoDB indexler) — FK index'i elle açılır.
**Projede:** Faz 3 — `V2__add_category.sql`.

---

## Güvenlik (Faz 4)

**S:** Authentication ve authorization farkı, HTTP karşılıkları?
**C:** Authentication = "sen kimsin" → **401**. Authorization = "bunu yapabilir misin" → **403**. 401 kimlik eksik/geçersiz, 403 kimlik var ama yetki yok.

**S:** `[zayıf]` Encoding, hashing, encryption ve signature arasındaki fark? (2026-09-10 tur 2: imza/bearer kavramı netleşti, ama "base64 şifreleme mi" sorusu hâlâ var — encoding'in şifreleme olmadığı netleşmeli)
**C:** **Encoding** (base64) geri döndürülebilir, güvenlik değil taşıma formatı — JWT payload'ı budur, herkes okuyabilir. **Hashing** (BCrypt) tek yönlü, şifre saklamak için. **Encryption** anahtarla geri döndürülebilir, veriyi gizlemek için. **Signature** (HMAC/RSA) bütünlük + kaynak doğrular ama **gizlilik sağlamaz**.
**Çapa:** Encoding = şeffaf zarf · hashing = kıyma makinesi (geri döndüremezsin) · encryption = kasa (anahtarı olan açar) · signature = mühür (içeriği gizlemez, sahteliği gösterir).
**Projede:** Faz 4 — BCrypt (hash) + HS256 (signature); JWT payload'ı base64, gizli veri konmaz.

**S:** Şifre hash'i neden **kasten yavaş** olmalı?
**C:** Saldırgan hash'leri ele geçirirse saniyede kaç deneme yapabileceği hız belirler. BCrypt/Argon2 yavaş ve maliyeti ayarlanabilir olduğu için uygundur; SHA-256 **çok hızlı** olduğu için şifreye uygun değildir (hızlı olması diğer işlerde erdem, burada kusur).

**S:** Salt ne işe yarar?
**C:** Her şifreye eklenen rastgele değer. Aynı şifrelerin aynı hash'i üretmesini ve önceden hesaplanmış rainbow table saldırılarını engeller. BCrypt salt'ı hash string'inin içinde taşır, ayrı kolon gerekmez.

**S:** Session ve token yaklaşımının takası nedir?
**C:** **Session** sunucuda durum tutar → iptal etmek kolay, ama yatay ölçeklemek için paylaşılan store gerekir. **Token** stateless → ölçeklenir, ama **iptal edilemez**; çözüm kısa ömür (15 dk) + sunucuda saklanan refresh token.
**Projede:** Faz 4 — stateless seçildi, `JSESSIONID` hiç üretilmiyor.

**S:** CORS ne yapar, ne **yapmaz**?
**C:** Tarayıcının, bir sayfadaki JS'in **başka origin'den gelen yanıtı okumasını** kısıtlamasıdır. Sunucu tarafı erişim kontrolü **değildir** — `curl`, Postman, mobil istemci CORS'u hiç uygulamaz. Yani CORS bir güvenlik duvarı değil, tarayıcı politikasıdır.
**Projede:** Faz 4 — OWASP denetiminde eksik olarak bulunup eklendi.

**S:** Preflight isteği neyi tetikler?
**C:** "Simple request" olmayan her istek: `Authorization` header'ı eklediğin veya `application/json` gövde gönderdiğin an tarayıcı önce `OPTIONS` ile izin sorar. Reddedilirse asıl istek hiç gönderilmez. `Access-Control-Max-Age` bu cevabı cache'ler.

**S:** `[zayıf]` CSRF neden token tabanlı API'da yapısal olarak yok?
**C:** CSRF, tarayıcının kimliği (cookie) isteğe **otomatik eklemesinden** doğar. `Authorization` header'ı otomatik eklenmediği için saldırganın sitesinden gelen istek kimlik taşımaz — bu yüzden `csrf.disable()` bizim kurulumda güvenli. Cookie tabanlı oturuma dönülürse CSRF koruması **geri açılmalı**.
**Projede:** Faz 4 — `SecurityConfig`.

**S:** Token'ı `localStorage`'da mı cookie'de mi tutmalı?
**C:** Takas: `localStorage`'daki token XSS ile okunabilir. `httpOnly` cookie JS ile okunamaz ama otomatik gönderildiği için **CSRF'i geri getirir** (SameSite bunu büyük ölçüde azaltır). Yani tek doğru cevap yok, hangi saldırıya karşı hangi korumayı aldığın sorusu var.

**S:** JWT'ye rol koyduk — yetki değişikliği ne zaman etkili olur?
**C:** Ancak kullanıcı yeniden login olduğunda. Roller token'ın içinde taşındığı için, admin yetkisini geri aldığında kullanıcı token ömrü boyunca (bizde 15 dk) hâlâ yetkili davranır. Stateless token'ın iptal edilemezliğinin somut sonucu budur.

**S:** HS256'nın çok servisli mimarideki sınırı nedir?
**C:** Simetrik imzada **doğrulama yeteneği = üretme yeteneği**. Token'ı doğrulaması için sırrı verdiğin her servis token da basabilir; biri ele geçirilirse blast radius tüm sistem. Çözüm RS256 + JWKS: private key yalnızca üreticide, servisler public key ile sadece doğrular.
**Projede:** Faz 6'da canlı gösterildi — `order-service`'in sırrıyla sahte `ROLE_ADMIN` token'ı üretildi, gerçek kullanıcı 403 alırken sahte token **201** aldı.

**S:** JWT'ye geçmek performansta ne kazandırdı, BCrypt maliyeti nereye gitti?
**C:** HTTP Basic her istekte DB + BCrypt demekti: ~68 ms. JWT ile istek başına ~6.5 ms. BCrypt bedeli kaybolmadı — login'de **bir kez** ödeniyor (~65 ms). Yani kazanç maliyeti silmek değil, tekrar etmeyi bırakmak.

**S:** Rate limiting'in doğru katmanı neresi?
**C:** Genelde uygulama değil, gateway/proxy — ya da paylaşılan bir store (Redis). Uygulama içi sayaç **instance başına ayrı** çalışır (3 replika = 3x limit) ve restart'ta sıfırlanır. Ek takas: kullanıcı bazlı kilit, saldırganın bilerek hesabı kilitlediği bir DoS aracına dönüşebilir → IP + kullanıcı kombinasyonu, üstel gecikme veya CAPTCHA.
**Projede:** Faz 4 — `LoginAttemptService` bilinçli olarak yanlış katmanda yazıldı, Faz 8.3'te gateway'e taşınacak.

**S:** TLS neyi korur, neyi korumaz?
**C:** Üç garanti verir: gizlilik, bütünlük, sunucu kimliği (sertifika). Ama yalnızca **yoldaki** veriyi korur — sunucuda çözülür, uygulama loglarına ne yazdığını umursamaz. Bu yüzden token URL'e konursa TLS'e rağmen access loglarına düz metin yazılır; token her zaman header'da taşınır. Production'da TLS genelde load balancer/ingress'te sonlanır, iç ağda düz HTTP konuşulur.

**S:** Login'de "kullanıcı yok" ile "şifre yanlış" neden ayrı ayrı söylenmez?
**C:** User enumeration: saldırgan hangi kullanıcı adlarının kayıtlı olduğunu öğrenir ve saldırıyı daraltır. İkisine de aynı 401 dönülür.

---

## Docker & DevOps (Faz 5)

**S:** Dockerfile'da katman sırası neden önce `pom.xml` + `dependency:go-offline`, sonra kaynak kod?
**C:** Docker katmanları cache'ler ve bir katman değişince **sonraki hepsi** yeniden çalışır. Kaynak kod her commit'te değişir, bağımlılıklar nadiren — sırayı ters kurarsan her build'de tüm bağımlılıklar yeniden iner.
**Çapa:** Sık değişeni en üste koymazsın; en üste değişmeyeni koyarsın.
**Projede:** Her üç servisin Dockerfile'ı + `RUN --mount=type=cache,target=/root/.m2` (container host'un `~/.m2`'sini görmez).

**S:** Container içinde `localhost` ne anlama gelir?
**C:** Container'ın **kendisi**, host makine değil. Başka bir container'a compose servis adıyla ulaşılır (`postgres:5432`, `rabbitmq:5672`) — compose kendi iç DNS'ini kurar. Adres kod değil **konfigürasyondur**, env variable ile verilir.
**Projede:** `PRODUCT_SERVICE_BASE_URL: http://product:8080`, `SPRING_RABBITMQ_HOST: rabbitmq`.

**S:** liveness ve readiness probe farkı?
**C:** **Liveness** = "bu process bozuldu, öldür ve yeniden başlat". **Readiness** = "şu an trafik gönderme, ama bekle, düzelecek" (örn. henüz warm-up bitmedi, DB bağlantısı yok). Load balancer/K8s Service readiness'a bakar; ikisini karıştırmak sağlıklı ama meşgul bir servisi sürekli restart etmeye yol açar.

**S:** Konfigürasyon repoya mı ortama mı ait?
**C:** Ortama. Spring'de env variable `application.properties`'i ezer (`SPRING_DATASOURCE_URL` → `spring.datasource.url` — relaxed binding). Böylece aynı image dev/staging/prod'da farklı davranır, kod değişmez. `compose.yaml` bir **lokal geliştirme aracıdır**, CI onu kullanmaz; CI temiz makinede build + test yapar.

---

## Dağıtık sistemler — senkron sınır (Faz 6)

**S:** "Servis sınırı = bütünlük sınırı" ne demek?
**C:** Foreign key ancak aynı veritabanındaki tablolar arasında kurulabilir. `order_item.order_id` FK olabilir (aynı servis), `order_item.product_id` **olamaz** (tablo başka serviste). Yani servisleri ayırdığın an DB'nin sana bedava verdiği referans bütünlüğünü kaybedersin ve doğrulamayı uygulama katmanına taşırsın.
**Projede:** Faz 6 — `order-service` şeması.

**S:** Siparişte ürün adı ve fiyatı neden kopyalanarak saklanıyor?
**C:** Snapshot: ürünün fiyatı yarın değişince geçmiş siparişlerin tutarı değişmemeli. Sipariş, o an geçerli olan gerçeği kaydeder — canlı bir referans tutmaz. Bu aynı zamanda `product-service` çökse bile eski siparişlerin okunabilmesini sağlar.

**S:** `OrderService.create` neden bilinçli olarak `@Transactional` değil?
**C:** İçinde uzak HTTP çağrısı var. Açık bir transaction içinde uzak çağrı yapmak, DB bağlantısını ağ gecikmesi boyunca **rehin tutar** — bağımlılık yavaşladığında connection pool tükenir ve DB'ye hiç ihtiyacı olmayan istekler de ölür. Sondaki tek `save()` kendi transaction'ında çalışıyor, atomiklik kaybedilmiyor.

**S:** Uzak çağrıda timeout neden opsiyonel değil?
**C:** Timeout yoksa yavaş bir bağımlılık senin thread'lerini süresiz tutar; thread pool dolar ve **senin servisin de** ölür (cascading failure). Yani timeout kendini korumak için, karşı tarafı değil.
**Projede:** Faz 6 — `JdkClientHttpRequestFactory` (connect 2 s, read 3 s).

**S:** Retry'ın kalıcı kesintideki bedeli nedir ve bu neyin gerekçesi?
**C:** Ölçüldü: bağımlılık kapalıyken istek 14 ms → **782 ms** (3 deneme + backoff), üstüne karşı tarafa 3x yük. Retry **geçici** hatayı çözer; kalıcı kesintide sadece gecikme ve yük üretir. Circuit breaker'ın var oluş sebebi tam olarak bu: hattı kesip belirli aralıklarla tek deneme yapmak.
**Çapa:** Sigortanın atması — sürekli düğmeye basmak yerine hattı kesip bir süre sonra bir kez denemek.

**S:** Bulkhead nedir?
**C:** Bir bağımlılığın tüketebileceği kaynağı tavanlamak. `@ConcurrencyLimit(20)`: `product-service` yavaşlasa bile en fazla 20 thread orada bekleyebilir, kalan thread'ler diğer isteklere hizmet etmeye devam eder. Kısmi bozulma böyle sağlanır — Faz 6'da doğrulandı: `product-service` kapalıyken sipariş oluşturulamıyordu ama liste/okuma ve health çalışıyordu.
**Çapa:** Geminin su geçirmez bölmeleri — bir bölme su alır, gemi batmaz.

**S:** Dağıtık N+1 nedir, nasıl çözüldü?
**C:** Faz 3'teki N+1'in bir katman yukarısı: sipariş satırı başına bir HTTP çağrısı. Ölçüldü: 5 satırlı sipariş **5 HTTP + 5 SQL → 1 + 1** (toplu `GET /api/products/by-ids?ids=...` endpoint'i). Kalıp aynı: döngü içinde tek tek sorma, hepsini bir kere iste.
**Projede:** Faz 6 — `ProductClient.findByIds`. Eksik id'ler yanıttan düşürülür, tüm istek reddedilmez; `MAX_BATCH_SIZE=100`.

**S:** Uzak servisten 401/403 alırsan istemciye ne dönersin?
**C:** **502**, 401 değil. Kullanıcı bizde yetkiliydi; arıza servisler arası atlamada (yanlış/eksik token propagation). 401 dönmek kullanıcıya "tekrar giriş yap" der ve hiçbir şeyi çözmez — üstelik gerçek sorunu gizler.
**Projede:** Faz 6 — `ProductAccessDeniedException` → 502. İki ayrı çeviri sınırı var: `onStatus` (gelen HTTP → Java exception), `@ExceptionHandler` (Java exception → giden HTTP).

**S:** `SecurityContextHolder`'ın ThreadLocal olması neden önemli?
**C:** Kimlik bağlamı thread'e bağlıdır. İş `@Async`/executor ile başka bir thread'e geçtiğinde bağlam **görünmez** ve token sessizce eklenmez — hata almazsın, sadece çalışmaz. Çözüm `DelegatingSecurityContext*` sarmalayıcıları. Genel ders: **bağlam sınırı kendiliğinden geçmez, taşınır.**
**Projede:** Faz 6 token propagation; Faz 8.2'de tracing `traceId` için birebir aynı ders tekrarlanacak.

**S:** Kullanıcı token'ını taşımak ile service account kullanmak arasındaki fark?
**C:** **Propagation** (kullanıcı token'ı) kullanıcı isteğinden doğan çağrılar için — yetki kuralı aşağıdaki serviste kalır, "kim adına" bilgisi korunur. **Service account** cron job, kuyruk tüketicisi gibi kullanıcısı olmayan işler için — ama yetki mantığı dağılır ve "kim adına yapıldı" izi zayıflar.

**S:** `ParameterizedTypeReference` neden gerekiyor?
**C:** Java generics runtime'da silinir (type erasure). `List.class` dersen Jackson listenin **eleman** tipini bilemez ve `LinkedHashMap` üretir. `ParameterizedTypeReference<List<ProductView>>` tip bilgisini runtime'a taşır.

---

## Event-driven & message queue (Faz 7)

**S:** Bir çağrı senkron mu kalmalı, event'e mi dönüşmeli — karar kriteri ne?
**C:** Cevap akışı belirliyorsa senkron kalır (fiyatı bilmeden sipariş kuramazsın). Yan etkiyse event olur (bildirim gitmese de sipariş geçerli). Soru "hızlı mı olsun" değil, **"cevabını beklemek zorunda mıyım"**.
**Çapa:** Telefon vs SMS. Telefonda karşı taraf açmazsa işin durur; SMS'te uykudaysa bile mesaj bekler.
**Projede:** Faz 6 `ProductClient` (senkron) vs Faz 7 `order.created` (event).

**S:** Broker koyarak bağımlılığı ortadan kaldırdın mı? (2026-09-10 tur 2: "RabbitMQ'ya taşındı" doğru cevaplandı, `[zayıf]` düştü — kalan nüans: notification-service down ile RabbitMQ'nun kendisi down olması farklı sonuç verir, biri 201+gecikmiş bildirim, diğeri 500+DB'de yetim sipariş)
**C:** Hayır — **yer değiştirdin**. Senkron çağrı çağrılanın ayakta olmasını şart koşar; event yalnızca broker'ın ayakta olmasını şart koşar. Kazanç: bağımlılık sayısı azalmadı ama tek bir dayanıklı bileşende toplandı ve tüketicinin kesintisi üreticiyi etkilemiyor. Bedel: yeni operasyonel bileşen + eventual consistency.
**Çapa:** Postane. Gönderen alıcıyı tanımaz, alıcı tatildeyse mektup kutuda bekler — ama postane yanarsa hiçbir şey akmaz.
**Projede:** Faz 7.2 — `notification-service` durdurulmuşken sipariş 201 döndü, mesaj kuyrukta bekledi.

**S:** Event'i DB commit'inden önce mi sonra mı yayınlarsın?
**C:** **Sonra.** Önce yayınlarsan, transaction rollback olduğunda var olmayan bir sipariş için bildirim gitmiş olur. Ama sonrasında da atomik değildir: save başarılı olup publish patlarsa sipariş var, event yok — **ve şu anki kodumuzda bunun üstüne kullanıcı da 500 alıyor**, çünkü `convertAndSend` etrafında catch yok ve `GlobalExceptionHandler`'da AMQP'ye özel bir handler tanımlı değil. Yani sipariş DB'de duruyor ama istemci "başarısız" sinyali görüyor — muhtemelen tekrar dener ve ikinci bir sipariş daha açar. Bu boşluğun standart çözümü outbox pattern.
**Projede:** Faz 7.1 — `create()` `@Transactional` olmadığı için `save()` zaten commit edilmiş oluyor, publish ondan sonra. 2026-09-10'da canlı doğrulandı: RabbitMQ durdurulup sipariş oluşturuldu → istemci 500 aldı, `customer_order` tablosunda satır **vardı**.

**S:** `[zayıf]` Outbox pattern nedir, hangi problemi çözer? (2026-09-10 teach-back: "orphan order" semptomunu doğru teşhis etti ama çözümün adını hatırlayamadı)
**C:** "DB'ye yaz + kuyruğa yayınla" iki ayrı sistem olduğu için atomik değildir. Outbox'ta event, iş kaydıyla **aynı transaction içinde** bir `outbox` tablosuna yazılır (yani ya ikisi de olur ya hiçbiri); ayrı bir süreç (poller veya CDC/Debezium) bu tablodan yayınlanmamış satırları okuyup kuyruğa taşır. Kritik nokta: mekanizma reaktif değil — "yayınlama başarısız oldu mu" diye kontrol etmez, sadece "tabloda hâlâ yayınlanmamış satır var mı" sorar; satır durdukça (ilk deneme hiç yapılmamış olsun ya da patlamış olsun fark etmez) tekrar dener. Mülakat favorisi.
**Çapa:** Çıkış sepeti. Mektubu kaydın yanına, aynı çekmeceye koyarsın; kurye sonra gelip alır. "Kayıt var ama mektup yok" durumu oluşmaz.

**S:** Teslim garantileri: at-most-once, at-least-once, exactly-once?
**C:** Broker'lar pratikte **at-least-once** verir — ack kaybolursa aynı mesaj tekrar teslim edilir. Exactly-once'ı broker'dan beklemek yerine consumer'ı tekrara dayanıklı (idempotent) yazmak standart çözümdür: **at-least-once + idempotent consumer = pratikte exactly-once.**
**Çapa:** Aynı mektubun iki kopyası gelir; üstündeki takip numarasına (`eventId`) bakıp "bunu zaten işledim" der, çöpe atarsın.
**Projede:** Faz 7.1 — `OrderCreatedEvent.eventId` (UUID) bu iş için taşınıyor. 2026-09-10'da canlı kanıtlandı: aynı `eventId`'yle aynı mesaj RabbitMQ UI'dan iki kez publish edildi, broker ikisini de teslim etti (engellemedi) — at-least-once'ın kendisi budur; duplicate'i durduran broker değil, `OrderCreatedListener`'daki `seen.add()` kontrolü.

**S:** Var olan bir queue'ya yeni bir argüman (örn. dead-letter-exchange) eklemek için ne yapman gerekir?
**C:** Var olan bir queue'yu **redeclare ederek değiştiremezsin** — RabbitMQ bir queue'nun argümanlarını oluşturulduktan sonra sabit kabul eder. Aynı isimde farklı argümanlarla tekrar tanımlamaya çalışırsan `PRECONDITION_FAILED` hatası alırsın (hem üreten hem tüketen servis aynı hatayla düşer, ikisi de aynı queue'yu tanımlamaya çalıştığı için). Dev'de çözüm: queue'yu sil, yeniden oluşsun. Prod'da: canlı trafik varken silinemez, yeni isimli bir queue'ya (`v2`) geçiş yapılır.
**Çapa:** Queue argümanları dövme (tattoo) gibi — sonradan "güncellenmez", ancak silinip yeniden yapılır.
**Projede:** Faz 7.4 — `order.created.queue`'ya dead-letter argümanı eklenince hem `order-service` hem `notification-service` `PRECONDITION_FAILED` ile düştü, çünkü queue Faz 7.1'den beri argümansız duruyordu. `curl -X DELETE .../api/queues/%2f/order.created.queue` ile silinip restart edilince düzeldi.

**S:** DLQ (dead letter queue) neden var?
**C:** İşlenemeyen bir mesaj sonsuz retry'a girerse kuyruğu kilitler (poison message) ve **arkasındaki sağlam mesajlar da** işlenemez. Kuyruğa dead letter exchange bağlanır, N denemeden sonra mesaj DLQ'ya düşer, ana kuyruk akmaya devam eder, insan DLQ'ya bakar.
**Çapa:** Bantta sıkışan bozuk kutuyu yan rafa alırsın ki arkadaki sağlam kutular geçebilsin.

**S:** Consumer'ı ayrı servis yapmak yerine üreticinin içine bir `@RabbitListener` koymak neden yetmez?
**C:** İki sebep: (1) aynı JVM'de olurlarsa consumer'ı üreticiyi durdurmadan durduramazsın — yani decoupling iddiası **test edilemez**, sadece teoride kalır; (2) bildirim kodundaki bir bug veya yavaş üçüncü parti çağrısı aynı thread pool'u ve belleği paylaştığı için sipariş isteklerini de etkiler. Sınırı nereye çekersen arıza ve deploy bağımsızlığı o sınırda durur.
**Projede:** Faz 7.2 — `notification-service` ayrı modül, `order`'a hiçbir `depends_on` bağı yok.

**S:** İki servis bir event'in şemasını nasıl paylaşır?
**C:** **JSON wire contract** ile, derlenmiş kod ile değil. Ortak bir sınıfı kütüphane olarak paylaşmak, event şeması değiştiğinde iki servisi birlikte deploy etmeye (lockstep) zorlar ve bağımsız deploy edilebilirliği bitirir. Bu yüzden `OrderCreatedEvent` her iki serviste ayrı ayrı tanımlı — aynı alanlar, ayrı sınıflar.
**Projede:** Faz 7 — `order_service.event.OrderCreatedEvent` ve `notification_service.event.OrderCreatedEvent`.

---

**S:** RabbitMQ ve Kafka'nın temel farkı ne?
**C:** RabbitMQ'da mesaj **tüketilince kuyruktan silinir** (task/command kuyruğu). Kafka'da mesaj retention süresi boyunca kalır, tüketilmiş olsa da — consumer kendi okuma pozisyonunu (offset) tutar, farklı bir consumer group aynı veriyi baştan okuyabilir (**replay edilebilir event stream**). RabbitMQ routing key ile hedefe yönlendirir; Kafka'da eşdeğeri topic + partition (paralellik birimi) + consumer group.
**Çapa:** RabbitMQ = posta kutusu (okununca boşalır). Kafka = ses kayıt bandı (dinleyen bandı silmez, başka biri baştan dinleyebilir).

**S:** Saga pattern nedir, 2PC neden kullanılmıyor?
**C:** Servis sınırını aşan bir iş akışında (sipariş → ödeme → stok) tek bir DB transaction'ı olamaz. Saga, akışı adım zinciri olarak yönetir; bir adım başarısız olursa önceki adımlar **rollback edilmez**, yerine **compensating action** (telafi edici işlem, örn. "stok düş" başarısız olduysa "ödeme"yi geri iade et) çalıştırılır. 2PC (two-phase commit) dağıtık kilit gerektirir, servisleri kısa süre de olsa birbirine bloklar — mikroservisin bağımsızlık amacına aykırı.
**Çapa:** Rollback = kaseti geri sarmak. Compensating action = yapılanın tersini yeni bir hareketle üstüne kaydetmek, kaset geri sarılmaz.

**S:** Eventual consistency arayüze nasıl yansır?
**C:** Sunucu tarafında "şu an tutarlı değil, birkaç saniyede tutarlı olacak" durumu varsa, arayüz bunu **gizlemez, gösterir** — kesin "Tamamlandı" yerine "Onaylanıyor" gibi bir ara durum. FE analojisi: optimistic UI update + arka planda gerçek durumu polling/websocket ile senkronize etmek aynı problemin çözümü.

## Henüz görülmedi — Faz 8'de gelecek

Bu kartlar önceden yazıldı, ilgili faz gelince "Projede" satırı doldurulacak.

**S:** Cache-aside nedir, alternatifine göre neden seçilir?
**C:** Uygulama önce cache'e bakar, yoksa DB'den okur ve cache'e yazar. Write-through'a göre daha basit ve cache çökse bile sistem çalışır; bedeli ilk isteğin yavaş olması (cold miss) ve **invalidation sorumluluğunun uygulamada kalması**.
**Çapa:** Buzdolabı vs market. Önce dolaba bakarsın, yoksa markete gidip dönüşte dolaba koyarsın. Bayat kalma riski invalidation problemidir.

**S:** Cache invalidation neden zor?
**C:** Teknik olarak zor olduğu için değil, **doğruluk sınırı belirsiz** olduğu için: "ne kadar bayat veri kabul edilebilir" sorusunun cevabı teknik değil ürün kararıdır.

**S:** Distributed tracing ne çözer, sampling oranı neden bir karar?
**C:** Bir istek birden fazla servise yayıldığında ayrı log dosyalarını elle eşleştirmek imkânsızlaşır; her istek bir `traceId` alır ve bu id servis sınırını header ile geçer, span'lar tek ağaç olarak görülür. Sampling bir maliyet kararıdır: %100 üretim yükünü ve depolamayı ciddi artırır, %1 nadir hatayı kaçırır.
**Çapa:** Kargo takip numarası — aynı numara her durakta görünür.
