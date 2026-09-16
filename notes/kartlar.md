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

**S:** N+1 nedir ve sorgu sayısı neye bağlıdır? (2026-09-15 tur 2'de düzeldi — `[zayıf]` düştü)
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

**S:** Proxy tabanlı annotation'ların self-invocation tuzağı nedir? (2026-09-11'de doğru cevaplandı: proxy invoke olmuyor, hatasız sessizce çalışmıyor — `[zayıf]` düştü)
**C:** `@Transactional`/`@Cacheable`/`@Async` Spring proxy'si üzerinden çalışır. Aynı sınıf içinden `this.method()` çağırırsan proxy devreye girmez ve annotation **sessizce** hiç çalışmaz — hata da almazsın, en tehlikeli tarafı bu.
**Çapa:** Kendi ofisinden kendine telefon etmek — santral (proxy) araya girmez, yani santralin yaptığı hiçbir şey olmaz.
**Projede:** Faz 3'te `@Transactional` için görüldü; Faz 8.1'de `@Cacheable` için aynısı geçerli olacak.

**S:** `[zayıf]` OSIV (open-in-view) nedir, kapatınca ne oldu ve ne kazandık? (2026-09-16 tur 2: `@EntityGraph`'la karıştırıldı — OSIV oturumun NE KADAR SÜRE açık kaldığı, `@EntityGraph` hangi ilişkinin JOIN'le geleceği; ikisi ayrı katman)
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

**S:** `[zayıf]` Encoding, hashing, encryption ve signature arasındaki fark? (2026-09-11'de doğru gelmişti, 2026-09-16'da HS256 sorusunda regresyon oldu — "JWT'yi secret ile şifreliyoruz" dendi, imza ≠ şifreleme ayrımı tekrar bulanıklaştı)
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

**S:** CSRF neden token tabanlı API'da yapısal olarak yok? (2026-09-14'te header/cookie ayrımı doğru geldi — `[zayıf]` düştü, "otomatik ekleme" detayını tazelemeye devam et)
**C:** CSRF, tarayıcının kimliği (cookie) isteğe **otomatik eklemesinden** doğar. `Authorization` header'ı otomatik eklenmediği için saldırganın sitesinden gelen istek kimlik taşımaz — bu yüzden `csrf.disable()` bizim kurulumda güvenli. Cookie tabanlı oturuma dönülürse CSRF koruması **geri açılmalı**.
**Projede:** Faz 4 — `SecurityConfig`.

**S:** Token'ı `localStorage`'da mı cookie'de mi tutmalı?
**C:** Takas: `localStorage`'daki token XSS ile okunabilir. `httpOnly` cookie JS ile okunamaz ama otomatik gönderildiği için **CSRF'i geri getirir** (SameSite bunu büyük ölçüde azaltır). Yani tek doğru cevap yok, hangi saldırıya karşı hangi korumayı aldığın sorusu var.

**S:** JWT'ye rol koyduk — yetki değişikliği ne zaman etkili olur?
**C:** Ancak kullanıcı yeniden login olduğunda. Roller token'ın içinde taşındığı için, admin yetkisini geri aldığında kullanıcı token ömrü boyunca (bizde 15 dk) hâlâ yetkili davranır. Stateless token'ın iptal edilemezliğinin somut sonucu budur.

**S:** `[zayıf]` HS256'nın çok servisli mimarideki sınırı nedir? (2026-09-16: BCrypt/encryption/signature üçü birbirine karıştı — "parola şifreleme" dendi, asıl sınır olan "doğrulama=üretme" hiç bahsedilmedi)
**C:** Simetrik imzada **doğrulama yeteneği = üretme yeteneği**. Token'ı doğrulaması için sırrı verdiğin her servis token da basabilir; biri ele geçirilirse blast radius tüm sistem. Çözüm RS256 + JWKS: private key yalnızca üreticide, servisler public key ile sadece doğrular.
**Projede:** Faz 6'da canlı gösterildi — `order-service`'in sırrıyla sahte `ROLE_ADMIN` token'ı üretildi, gerçek kullanıcı 403 alırken sahte token **201** aldı.

**S:** JWT'ye geçmek performansta ne kazandırdı, BCrypt maliyeti nereye gitti?
**C:** HTTP Basic her istekte DB + BCrypt demekti: ~68 ms. JWT ile istek başına ~6.5 ms. BCrypt bedeli kaybolmadı — login'de **bir kez** ödeniyor (~65 ms). Yani kazanç maliyeti silmek değil, tekrar etmeyi bırakmak.

**S:** Rate limiting'in doğru katmanı neresi?
**C:** Genelde uygulama değil, gateway/proxy — ya da paylaşılan bir store (Redis). Uygulama içi sayaç **instance başına ayrı** çalışır (3 replika = 3x limit) ve restart'ta sıfırlanır. Ek takas: kullanıcı bazlı kilit, saldırganın bilerek hesabı kilitlediği bir DoS aracına dönüşebilir → IP + kullanıcı kombinasyonu, üstel gecikme veya CAPTCHA.
**Projede:** Faz 4 — `LoginAttemptService` bilinçli olarak yanlış katmanda yazıldı. Faz 8.3'te gateway'e (nginx `limit_req_zone`) taşındı ve canlı doğrulandı: 8090 üzerinden hızlı istekler 429 aldı, **aynı anda** doğrudan `product-service`'e (8080) atılan istek 200 döndü — gateway'in limiti sadece kendinden geçen trafiği görüyor, bu da "tek katmanlı savunmanın" sınırını gösteriyor (defense in depth ihlali, auth için de aynı risk — bilerek teoride bırakıldı).

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
**Tuzak (2026-09-12, bytecode'dan doğrulandı):** `@ConcurrencyLimit`'in varsayılan politikası `REJECT` değil **`BLOCK`** — limit dolunca 21. çağrı reddedilmez, `Condition.await()` ile **süresiz** bekler (timeout yok). Yani "limit dolarsa hata alırım" varsayımı yanlış; gerçekte thread kuyrukta asılı kalır ve bu, bulkhead'in önlemeye çalıştığı "bir bağımlılık her şeyi batırır" senaryosunu bir üst katmanda geri getirebilir. Kod düzeltildi: `policy = ConcurrencyLimit.ThrottlePolicy.REJECT` — artık `InvocationRejectedException` fırlıyor, `GlobalExceptionHandler` bunu 503'e çeviriyor (o handler zaten yazılıydı ama önceden hiç tetiklenmiyordu).
**Not al:** Bir kütüphanenin varsayılan davranışını "mantıken böyle olmalı" diye tahmin etmek yerine doğrulamak — burada sezgi tam ters çıktı.

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

**S:** Outbox pattern nedir, hangi problemi çözer? (2026-09-12'de doğru cevaplandı: "her transaction'da koşulsuz yazılır, başarısız olursa tabloda kalır, periyodik tekrar denenir" — üç turluk `[zayıf]` düştü)
**C:** "DB'ye yaz + kuyruğa yayınla" iki ayrı sistem olduğu için atomik değildir. Outbox'ta event, iş kaydıyla **aynı transaction içinde, publish hiç denenmeden önce, koşulsuz** bir `outbox` tablosuna yazılır (yani ya ikisi de olur ya hiçbiri). Ayrı bir süreç (poller veya CDC/Debezium) bu tablodan yayınlanmamış satırları okuyup kuyruğa taşır. Kritik nokta: mekanizma reaktif değil — "yayınlama başarısız oldu mu" diye kontrol etmez, sadece "tabloda hâlâ yayınlanmamış satır var mı" sorar; satır durdukça (ilk deneme hiç yapılmamış olsun ya da patlamış olsun fark etmez) tekrar dener. **Sık yapılan hata:** "publish başarısız olursa tabloya yaz" demek — bu, publish denemesi ile tabloya yazma arasında hâlâ bir boşluk bırakır, tam çözülmesi gereken problemi geri getirir. Mülakat favorisi.
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

**S:** Kafka'da partition ve consumer group nasıl birlikte çalışır?
**C:** Partition = bir topic'in **paralellik birimi**; her partition kendi içinde sıralı, topic genelinde sıra garantisi yok. Bir consumer group içinde, **bir partition en fazla bir consumer tarafından okunur** (paralel tüketim, partition sayısı = maks. paralellik derecesi). **Farklı bir consumer group** aynı partition'ı **baştan, bağımsız** okuyabilir — replay budur.
**Çapa:** Partition sayısı kasadaki gişe sayısı gibi — 3 gişe varsa aynı anda en fazla 3 kişiye hizmet verebilirsin, 4. kişi (consumer) boşta kalır.
**Projede:** Görmedik (RabbitMQ kullandık) — teoride kaldı, 7.6'da bilinçli olarak hands-on yapılmadı.

**S:** RabbitMQ zaten fan-out/topic exchange ile birden fazla queue'ya yayın yapabiliyorken, "birden fazla bağımsız tüketici" senaryosunda Kafka'nın gerçek farkı ne?
**C:** RabbitMQ da fan-out yapar (bizim `order.events` topic exchange'imize yeni bir queue bağlarsan o da mesajları alır) — ama sadece **o queue var olduktan sonra** yayınlanan mesajları. Kafka'da yeni bir consumer group **geç katılsa bile**, retention süresi boyunca duran **geçmişin tamamını** okuyabilir. Fark "birden fazla servise dağıtmak" değil (ikisi de yapar) — fark **geç katılan bir tüketicinin geçmişe erişebilmesi**.
**Projede:** Analytics gibi sonradan eklenen bir servis, geçmiş tüm `order.created` event'lerini görmek isteseydi, RabbitMQ'da yapamazdı (queue'su yoktu, mesajlar geçip gitti) — Kafka'da yapabilirdi.

**S:** CDC (Change Data Capture) nedir, outbox pattern'le bağlantısı ne?
**C:** Bir veritabanının kendi değişiklik günlüğünü (Postgres'te WAL) dinleyip, her değişikliği bir event olarak yayınlamak (örn. Debezium). Outbox pattern'de bir poller süreci tabloyu tarayıp yayınlanmamış satırları kuyruğa taşıyordu — CDC bu poller'ı **ortadan kaldırır**: Debezium doğrudan WAL'ı dinler, outbox tablosuna yazılan her satırı otomatik Kafka'ya akıtır, polling gecikmesi ve yükü olmaz.
**Projede:** Outbox'ı uygulamadık (teoride kaldı) — uygulasaydık, CDC bunun "production-grade" hâli olurdu.

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
**Projede:** Faz 8.1 — `ProductService.findResponseById()` `@Cacheable`, `update()`/`delete()` `@CacheEvict`. Canlı ölçüldü: cache miss 1 sorgu, sonraki 3 istek 0 sorgu; `PUT` sonrası ilk `GET` yine 1 sorgu (evict doğru çalıştı, bayat veri dönmedi).

**S:** Cache invalidation neden zor?
**C:** Teknik olarak zor olduğu için değil, **doğruluk sınırı belirsiz** olduğu için: "ne kadar bayat veri kabul edilebilir" sorusunun cevabı teknik değil ürün kararıdır.

**S:** Redis'te çok fazla key birikip belleği taşırmasını nasıl engellersin?
**C:** İki katman: **TTL** (her key'in bir ömrü olur, süresiz cache yok) ve **`maxmemory` + eviction policy** — Redis'e "en fazla şu kadar bellek kullan, dolunca şu key'i sil" denir. TTL'li key'lerin olduğu bir sistemde doğru politika `volatile-lru` (TTL'i olanlar arasında en az kullanılanı sil) ya da `allkeys-lru` (hepsi arasında). Bu ekibin/ops'un konfigüre ettiği bir production kararı, kod değil.
**Projede:** Faz 8.1 — `redis:8-alpine` şu an varsayılan (sınırsız) ile çalışıyor; `command: redis-server --maxmemory 256mb --maxmemory-policy volatile-lru` eklenerek sınırlanabilir.

**S:** Bir DTO'yu (özellikle Java record) Redis'te cache'lerken karşılaşabileceğin sinsi bir serialization tuzağı nedir?
**C:** Genel amaçlı bir JSON serializer (örn. `GenericJacksonJsonRedisSerializer`), farklı tipleri aynı cache'te tutabilmek için tip bilgisini JSON'a gömer — ama bu gömme genelde sadece **non-final** sınıflar için çalışır (`DefaultTyping.NON_FINAL`). Java **record'ları implicit olarak `final`**'dır, yani tip bilgisi hiç gömülmez. Redis'ten geri okurken Jackson elindeki JSON'ı hangi sınıfa çevireceğini bilemez, generic bir `Map` döndürür — sonraki cast `ClassCastException` ile patlar.
**Çapa:** Genel serializer, adresi olmayan bir kutuya "kime ait bilmiyorum ama JSON bu" yazmak gibi — final sınıfta o adres etiketi hiç yapıştırılmıyor.
**Projede:** Faz 8.1 — canlı yaşandı: `ProductResponse` (record) `GenericJacksonJsonRedisSerializer` ile cache'lenince ikinci okuma `ClassCastException` verdi. Çözüm: tek, bilinen bir tip için polymorphism'e hiç gerek yok — `JacksonJsonRedisSerializer<ProductResponse>` (tipi constructor'da açıkça belirten, non-generic serializer) kullanıldı.

**S:** Distributed tracing ne çözer, sampling oranı neden bir karar?
**C:** Bir istek birden fazla servise yayıldığında ayrı log dosyalarını elle eşleştirmek imkânsızlaşır; her istek bir `traceId` alır ve bu id servis sınırını header ile geçer, span'lar tek ağaç olarak görülür. Sampling bir maliyet kararıdır: %100 üretim yükünü ve depolamayı ciddi artırır, %1 nadir hatayı kaçırır.
**Çapa:** Kargo takip numarası — aynı numara her durakta görünür.
**Projede:** Faz 8.2 — Zipkin'de canlı doğrulandı: tek `traceId`, üç servis. `product-service`'in span'i `order-service`'in `http get` span'inin (senkron, Faz 6), `notification-service`'in span'i `order-service`'in publish span'inin (asenkron, Faz 7 — RabbitMQ observation açılarak) doğrudan çocuğu.

**S:** Spring'in yönettiği bir nesne (`RestClient.Builder` gibi) ile kendi kurduğun bir nesne (`RestClient.builder()`) arasındaki fark tracing'i nasıl etkiler? (2026-09-15 tur 2'de netleşti — `[zayıf]` düştü)
**C:** Boot, tracing/observation desteğini yalnızca **kendi yönettiği** (context'e bean olarak kayıtlı) nesnelere otomatik özellik ekleyebilir — `RestClient.Builder`'ı inject edip kullanırsan Boot ona bir `ObservationRestClientCustomizer` uygular, bu da her giden isteğe `traceId`/`spanId` header'ını otomatik ekler. Statik `RestClient.builder()` ile elle kurduğun bir istemci Spring'in hiç haberi olmayan bir nesnedir — hiçbir otomatik özellik ona uygulanmaz, trace zinciri tam o noktada kopar.
**Çapa:** Aynı self-invocation dersi, farklı kılıkta: Spring sadece **kendi elinden geçen** nesneleri geliştirebilir, arkadan gizlice kurduğun bir nesneye hiçbir şey ekleyemez.
**Projede:** Faz 8.2 — `RestClientConfig.java`, `RestClient.builder()` → injected `RestClient.Builder`. Düzeltilmeden önce `product-service`'e giden çağrılar trace'e hiç girmiyordu.

**S:** Aynı Spring Boot modülü (örn. "web" desteği) her zaman aynı bağımlılıkla mı gelir?
**C:** Hayır — Boot 4.1'de sunucu tarafı (`spring-boot-starter-webmvc`, gelen isteği karşılamak) ve istemci tarafı (`spring-boot-starter-restclient`, giden istek atmak) **ayrı starter'lar**. Eski sürümlerde (Boot 3.x) `RestClient.Builder`'ın auto-configure edildiği kod genel autoconfigure jar'ının içindeydi, herhangi bir web starter'ı ile bedavaydı. Boot 4.1 bunu ayrıştırdı: sadece sunucu olan bir servisin istemci tarafı autoconfig'e ihtiyacı yok, tersi de doğru.
**Projede:** Faz 8.2 — `order-service`'e `spring-boot-starter-restclient` eklenmeden `RestClient.Builder` bean'i context'te hiç yoktu, uygulama **hiç açılmadı** (`NoSuchBeanDefinitionException` yerine `UnsatisfiedDependencyException` — inject edilecek bean'in kendisi yoktu).

**S:** Bir ekosistem aracı (Spring Cloud gibi), altındaki framework'ün (Spring Boot) en yeni sürümüyle her zaman uyumlu mudur?
**C:** Hayır — büyük eklenti ekosistemleri genelde ana framework'ün **birkaç ay gerisinden** gelir; en yeni sürüm çıktığında henüz güncellenmemiş olabilirler. Bu, "en yeni sürümü kullanmalı mıyım" sorusunun cevabını teknik değil **risk yönetimi** sorusuna çeviriyor: bleeding-edge bir framework sürümü + ona henüz yetişmemiş bir ekosistem aracı = gerçek bir uyumsuzluk riski.
**Çapa:** Ana yolu asfaltlayan ekip ile kaldırımı döşeyen ekip aynı hızda ilerlemez — asfalt bitti diye kaldırım da bitmiş olmaz.
**Projede:** Faz 8.3 — Spring Cloud Gateway 4.3.0 (`spring-cloud 2025.0.0`), Boot 4.1.0 ile `NoClassDefFoundError` verdi (minimal bir probe uygulamasında bile context açılmadı). Roadmap bu riski önceden yazmıştı ("önce doğrula, yoksa nginx'e indirge") — gerçekleşti, nginx reverse proxy'ye geçildi.

**S:** nginx'te bir `location` tanımının sonunda `/` olması ne fark yaratır?
**C:** `location /api/products/` (sonunda `/`) sadece bu path'in **altındaki** isteklerle eşleşir (`/api/products/5` eşleşir) — path'in **kendisiyle** (`/api/products`, slash'sız) eşleşmez, çünkü prefix eşleşmesi "istek, location string'iyle başlıyor mu" sorusuna bakar ve daha kısa bir string daha uzun bir string'le başlayamaz. Eşleşmeyen istek nginx'in kendi varsayılan statik dosya sunucusuna düşer — bir `proxy_pass` beklerken sessizce 404/301 almanın klasik sebebi budur.
**Çapa:** "/api/products/" bir çekmecenin İÇİ, "/api/products" çekmecenin kendisi — çekmecenin içini arayan biri çekmecenin üstündeki etikete bakmaz.
**Projede:** Faz 8.3 — `nginx.conf`'ta `location /api/products/` yazınca `/api/products` isteği eşleşmedi, nginx'in varsayılan `root` handler'ına düştü ve 301 (trailing slash ekleme) döndürdü. Sonunda `/` kaldırılınca (`location /api/products`) düzeldi.

**S:** nginx'teki `upstream` bloğu ile Kubernetes'teki `Service` aynı işi mi yapıyor?
**C:** Aynı **iş** (bir isme giden trafiği, o an ayakta olan kopyalar arasında dağıtmak) ama farklı **güncelleme mekanizması**. nginx'in `upstream` listesi **statik** — sen elle yazarsın, bir kopya çökerse nginx bunu otomatik fark etmez (health check eklemeden). K8s `Service`, `Deployment`'ın yönettiği pod'ları **etiketle sürekli izler** — pod çökerse liste otomatik küçülür, `Deployment` yeni pod açınca otomatik büyür. `Deployment` = "bu pod'dan N kopya her zaman ayakta olsun" (self-healing + ölçekleme), `Service` = o kopyalar arasında trafiği dağıtan stabil isim.
**Çapa:** nginx upstream = elle güncellenen bir rehber; K8s Service = kendi kendini güncelleyen, canlı bir rehber.
**Projede:** Faz 8.3'te yaşadığımız kısıt (`container_name` sabit → `docker compose --scale` imkânsız, Faz 6 devir notu) K8s'in platform seviyesinde çözdüğü şey. nginx de K8s'in içinde kullanılabilir (nginx-ingress-controller) — orada statik değil, K8s API'sinden beslenir.

## Mimari karar konuları (Faz 8 — teach-back)

**S:** Concurrency (eşzamanlılık) ile parallelism (paralellik) arasındaki fark ne?
**C:** **Concurrency** = birden fazla işi aynı anda başlamış gibi yönetmek — ama bir anda gerçekten sadece biri ilerliyor olabilir (Node'un event loop'u: I/O beklerken bloklanmaz, başka işe geçer, sonra geri döner). **Parallelism** = birden fazla işi gerçekten aynı anda, farklı CPU çekirdeklerinde çalıştırmak (Java'nın thread-per-request modeli, çok çekirdekli makinede). Ayrım "multi-thread vs event loop" değil, **I/O-bound vs CPU-bound**: I/O-bound işte (çoğu zaman bekleyen bağlantı) Node'un tek thread'i yeterli; CPU-bound işte (ağır hesaplama) Node'un tek JS thread'i tıkanır, tüm event loop'u bloklar.
**Çapa:** Concurrency = bir aşçının birden fazla tencereyi aynı anda kaynatıp aralarında gidip gelmesi. Parallelism = birden fazla aşçının, her biri kendi tezgahında, gerçekten aynı anda pişirmesi.
**Projede:** `ProductClient.java` — `@ConcurrencyLimit(20)` (bulkhead) Java'nın thread-per-request modeline dayanıyor. Java 21 virtual thread'ler I/O-bound iş için Node'un event loop'una yakın bir hafiflik sağlıyor, CPU-bound için gerçek paralellik avantajı JVM'de kalıyor.

**S:** Dağıtık monolit nedir, nasıl ortaya çıkar? (2026-09-15'te sebepler doğru geldi — paylaşılan DB, bağımlı deployment — `[zayıf]` düştü; sonucu da ekle: mikroservisin karmaşıklığını alıp faydasını almamak)
**C:** Servisleri ayrı deploy edilebilir birimlere böldün, ama birbirlerine o kadar sıkı bağlılar ki **bağımsız deploy/ölçekleyemiyorsun** — mikroservisin tüm operasyonel yükünü (ağ, serialization, dağıtık debug) alıyorsun, hiçbir faydasını (bağımsızlık) almıyorsun. Klasik sebepleri: **paylaşılan veritabanı**, **lockstep deploy zorunluluğu**, **döngüsel bağımlılıklar**. Monorepo (aynı repo'da birden fazla servis) bununla ilgisiz — bir repo yapısı kararı, coupling kararı değil.
**Çapa:** Ayrı evlere taşınmışsın ama tek anahtarı paylaşıyorsun — resmi olarak ayrısınız, gerçekte hâlâ birbirinize muhtaçsınız.
**Projede:** Faz 6 devir notu — `order-service` ve `product-service` aynı `product` DB kullanıcısını paylaşıyor. Tam bir dağıtık monolit değiliz (ayrı veritabanları, bağımlılık keyfi değil gerçek bir ihtiyaçtan — fiyat bilgisi) ama bu paylaşılan kullanıcı küçük bir koku.

**S:** Runtime/dil seçimini ne belirler — Node mu Java/Go mu?
**C:** Trafik miktarı değil, **istek başına ne kadar CPU işi** yapıldığı. I/O-bound (çoğunlukla bekleme, az CPU — chat, bildirim, basit CRUD proxy) → Node'un event loop'u hafif ve verimli. CPU-bound (gerçek hesaplama — şifreleme, görüntü işleme, ağır iş kuralı) → Java/Go/Rust, çünkü gerçek multi-core paralellik sunuyorlar; Node'un tek JS thread'i ağır hesaplamada **tüm event loop'u** bloklar. Serverless/cold-start'ta JVM dezavantajlı (JIT ısınması her seferinde ödenir), Go avantajlı (tek statik binary, ayrı runtime yok, başlangıç anlık). Pratikte ekip bilgisi + ekosistem olgunluğu, teknik farktan çoğu zaman daha belirleyici.
**Çapa:** I/O-bound = garsonun sipariş alıp mutfağa iletmesi, beklerken başka masaya bakması (event loop). CPU-bound = aşçının kendisi — o an yemek pişiriyorsa başka hiçbir şeye bakamaz, birden fazla aşçı (çekirdek) lazım.
**Projede:** Bütün sistem Java/Spring — üç servisimiz de (fiyat hesaplama, sipariş, event tüketme) CPU-bound olmasa da domain mantığı + transaction bütünlüğü ağır basıyor; ekip bilgisi (Java öğrenme hedefi) zaten kararı belirledi.

**S:** Hexagonal mimari (Ports & Adapters) nedir?
**C:** İş mantığını (domain) merkeze koyup dış dünyadan (DB, HTTP, kuyruk, UI) tamamen izole eden bir desen. Domain ihtiyaç duyduğu şeyi bir **port** (interface) olarak tanımlar; gerçek teknoloji bunu bir **adapter** olarak implement eder (JPA adapter, REST adapter, test için in-memory adapter). İki port türü: **driving/primary** (dışarıdan içeri çağıranlar — controller), **driven/secondary** (dışarıya çıkanlar — repository). Kazanç: adapter değiştirilebilir (Postgres→Mongo) domain'e dokunmadan, çünkü bağımlılık yönü ters çevrilmiş — domain interface'i tanımlar, altyapı onu implement eder (dependency inversion).
**Çapa:** Elektrik prizi (port) standarttır; ülkene göre fişi (adapter) değiştirirsin, cihazın (domain) aynı kalır.
**Projede:** Kullanmıyoruz — klasik katmanlı mimarideyiz (`ProductService`, Spring Data JPA'nın `ProductRepository`'sine **doğrudan** bağımlı). Hexagonal'da bu bir port olurdu, JPA implementasyonu ayrı bir infrastructure paketinde kalırdı. Değer kazandığı yer: iş mantığı gerçekten karmaşık olduğunda ya da altyapıyı değiştirmeyi gerçekten beklediğinde — küçük/orta CRUD sistemlerde genelde gereksiz ekstra katman.

## System design egzersizleri (Faz 8.4)

**S:** CAP teoreminde gerçek seçim neden "üçünden ikisi" değil? (2026-09-16 tur 2'de trade-off doğru geldi — "geç ama doğru=C, hızlı=A" — küçük etiket hatası: "C-P arası" değil "C-A arası" denmeli, P zaten sabit)
**C:** Consistency (her okuma en son yazılanı görür), Availability (her istek cevap alır, güncel olmasa da), Partition tolerance (node'lar arası ağ kopsa bile sistem çalışmaya devam eder). Gerçek dağıtık sistemlerde ağ bölünmesi **er ya da geç olur** — bu bir seçenek değil, bir gerçek. Yani P'yi seçmezsin, P zaten var. Asıl karar bölünme **olduğunda**: cevap vermeyi reddedip tutarlı mı kalırsın (**CP**), yoksa bayat da olsa cevap mı verirsin (**AP**).
**Çapa:** P bir seçenek değil, hava durumu gibi — erken ya da geç yağmur yağar. Gerçek soru şemsiyeni mi açarsın (CP, ıslanma riskini göze alma), yoksa yürümeye devam mı edersin (AP, biraz ıslan ama dur kalma).
**Projede:** RabbitMQ'nun kendisi düştüğünde yaşadığımız 500 + DB'de yetim sipariş (Faz 7) — sistemimiz AP'ye yakın durduğumuzun kanıtı, sipariş kabul etmeyi (availability) anlık tam tutarlılığa tercih ettik.

**S:** Dağıtık rate limiting'de nginx'in `limit_req_zone`'u neden yetmez?
**C:** Her gateway instance'ı kendi belleğinde **ayrı** sayar, birbirini görmez — 3 instance varsa kullanıcı limitin 3 katını geçirebilir (her instance kendi 100'üne kadar sayar). Aynı kalıp: Faz 4'teki `LoginAttemptService` (instance başına ayrı sayaç) ve `container_name` sabit olduğu için `--scale` yapamamamız. Çözüm: sayacı **paylaşılan, atomik** bir depoya (Redis, `INCR`) taşımak — hangi gateway sorarsa sorsun aynı sayıyı görür.
**Çapa:** Üç kapıcı, ortak bir defter yerine kendi cebine not tutuyor — kimse toplamı bilmiyor.

**S:** Rate limiting algoritmaları — fixed window, sliding window log, sliding window counter, token bucket — hangisi ne zaman?
**C:** **Fixed window** (basit sayaç + pencere): en ucuz, ama pencere sınırında kısa sürede 2x trafiğe izin verebilir. **Sliding window log** (her isteğin zaman damgası): en hassas, ama bellek maliyeti yüksek (her istek kayıtlı). **Sliding window counter** (mevcut + önceki pencerenin ağırlıklı payı): fixed window'un O(1) belleğini korur, sınır zaafını yumuşatır — pratik uzlaşma. **Token bucket** (sabit hızda dolan kova, istek başına 1 token): burst'e izin verirken ortalama hızı korur, O(1) bellek, Redis'te atomik uygulaması kolay — **endüstri standardı** (Stripe, AWS, Google).
**Çapa:** Fixed window = ayın 1'inde sıfırlanan bütçe (ay sonu-ay başı açığı var). Token bucket = sabit hızda dolan su deposu (biriktiyse aniden çok su çekebilirsin, ama uzun vadede musluk hızını geçemezsin).
**Projede:** nginx'teki `limit_req ... burst=5 nodelay` (Faz 8.3) aslında basit bir token bucket — burst kadar birikmiş "izin" harcanabiliyor.

**S:** "Hangi rate limiting algoritması en iyisi" sorusuna doğru cevap nedir?
**C:** Tek isim değil — **burst'e izin vermek istiyor musun, hassasiyet mi öncelikli, bellek maliyeti neyi taşıyabiliyor** sorusuna bağlı. Token bucket varsayılan iyi cevap (pratikte en çok kullanılan) ama gerekçesiz söylenirse zayıf kalır — mülakatta "neden" kısmı puan getiren yer.

**S:** "Bir bildirim sistemi tasarla" sorusuna aynı cevap her senaryoda geçerli mi? E-ticaret sipariş bildirimi ile bankacılık ödeme onayını karşılaştır.
**C:** Hayır — gereksinimler tamamen farklı mimariye götürür. **E-ticaret:** tek kanal (email/push) yeterli, görülmese de sorun değil, gerçek zamanlı gerekmez, kayıp kritik değil → `at-least-once + idempotent consumer` yeterli, **AP'ye yakın** (bildirim gecikse/kaybolsa da sipariş etkilenmemeli). **Bankacılık ödeme onayı:** teslim onayı (delivery receipt) zorunlu, gerçek zamanlı kritik, kayıt asla kaybolmamalı (audit/compliance) → garantili teslim + timeout + retry + değiştirilemez log, **CP'ye yakın** (gerekirse kullanıcıyı bekletir/işlemi iptal eder ama yanlış/eksik bilgi vermez). Kullanıcı OTP'yi görüp girene kadar işlem senkron bekler — event değil, kesin cevap gerektiren bir adım (Faz 6'daki fiyat sorgusuyla aynı sınıf).
**Çapa:** E-ticaret bildirimi bir kartpostal (gecikse dünya batmaz), ödeme onayı taahhütlü mektup (imzalı teslim şart, teslim edilemezse geri döner).
**Projede:** Faz 7.2'deki sistemimiz tam olarak e-ticaret ucunda — `notification-service` çökse bile sipariş 201 dönüyor, bildirim gecikmesi kabul edilebilir bir bedel.
