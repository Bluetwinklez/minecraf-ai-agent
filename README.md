# AI Builders (minecraf-ai-agent)

Fabric **26.2 / 26.3** modu: oyuncu gibi davranan **inşaatçı NPC**'ler. Litematica (`.litematic`) veya
yapı bloğu (`.nbt`) şemalarını okuyup yapıyı blok blok kurarlar. Survival'da malzemeyi kendi
envanterinden ve yakındaki sandıklardan alırlar, eksik malzemeyi tam liste olarak söylerler,
yüksek yerler için iskele kurup bitince sökerler.

## Kurulum
- Minecraft 26.2 veya 26.3, Fabric Loader ≥ 0.19.5, Fabric API, Java 25.
- Mod hem sunucuda hem oyuncularda kurulu olmalı (NPC görüntüsü için).
- Derleme: `./gradlew build` → `build/libs/aibuilders-<sürüm>.jar`.

## Şemalar
`.litematic` / `.nbt` dosyalarını şu klasörlerden birine koy:
- `config/aibuilders/schematics/`
- `schematics/` (Litematica'nın varsayılan klasörü; singleplayer'da doğrudan çalışır)

Litematica kurulu olmak zorunda değil; mod dosyayı kendisi okur. Eski sürümde kaydedilmiş şemalar
otomatik güncellenir. Sandık içerikleri, tabela yazıları ve varlıklar (item frame vb.) kopyalanmaz.

## Komutlar (`/aibuilders`, kısaca `/aib`)
| Komut | Ne yapar |
|---|---|
| `/aib spawn <isim> [skinOyuncu]` | Yanında NPC oluşturur, sahibi sensin. Skin oyuncu adından gelir. |
| `/aib schematics` | Şemaları listeler |
| `/aib info <şema>` | Boyut, blok sayısı, atlananlar |
| `/aib materials <şema>` | Şemanın tam malzeme listesi |
| `/aib materials <npc>` | Devam eden inşaatta kalan eksikler |
| `/aib preview <şema> [konum] [rotasyon] [ayna]` | Yerleşimi 10 sn parçacıkla gösterir |
| `/aib build <npc> <şema> [konum] [rotasyon] [ayna]` | İnşaatı başlatır (konum yoksa baktığın blok) |
| `/aib pause / resume / stop / status <npc>` | Kontrol |
| `/aib remove <npc>` | NPC'yi kaldırır, envanteri yere düşer |

NPC'yi sadece sahibi ve op'lar yönetebilir. Sahibi elindeki eşyayla NPC'ye sağ tıklarsa eşya NPC'ye
geçer; boş elle sağ tık durum + eksik malzeme listesini gösterir.

## Sohbet
- Oyuncu `sa` yazarsa en yakın NPC `as` der.
- `Claude ...` ile başlayan mesajlara (örn. `Claude test test`) yapay zekâ herkese görünür cevap verir.
  - Varsayılan: **Ollama** (ücretsiz, kendi VDS'inde). Kurulum:
    ```
    curl -fsSL https://ollama.com/install.sh | sh
    ollama pull qwen2.5:3b
    ```
    MC sunucusu aynı makinedeyse `ollamaUrl` `http://127.0.0.1:11434` kalsın ve **11434 portunu dışarı açma**
    (Ollama'da şifre yok). Farklı makinedeyse portu sadece MC sunucusunun IP'sine aç.
  - İstersen `provider: "claude"` + `claudeApiKey` (veya `ANTHROPIC_API_KEY`) ile Claude API (ücretli).
  - Model ulaşılamazsa NPC durumu / eksik malzeme gibi hazır cevaplar verilir.

## Ayarlar (`config/aibuilders.json`)
`buildMode` (`SURVIVAL`/`CREATIVE`), `blocksPerSecond`, `reach`, `chestSearchRadius`, `clearObstructions`,
`useScaffolding`, `allowTeleportWhenStuck`, `maxNpcsPerPlayer`, `maxSchematicBlocks`, `materialRecheckSeconds`,
`showBossBar`, `chatRadius`, `greetings`, `chatEnabled`, `chatTriggerWord`, `provider`, `ollamaUrl`, `ollamaModel`,
`claudeModel`, `claudeApiKey`, `maxTokens`, `chatTimeoutSeconds`, `chatCooldownSeconds`.

## Bilinen sınırlar
- Bir inşaatı tek NPC yapar. İskele sadece yapının dışına dikey sütun; dışarıdan erişilemeyen iç kısımlar
  (kubbe içi vb.) atlanır ve raporlanır (`allowTeleportWhenStuck` açılırsa ışınlanarak yapar).
- Su/lav survival'da konmaz.
- NPC'ler sadece yüklü chunk'larda bulunur; komutlar yüklü NPC'leri görür.

## Geliştirme
- `./gradlew build` – derleme + unit testler
- `./gradlew runGameTest` – başsız sunucuda oyun testleri (inşa, sandık, eksik malzeme, iskele, rotasyon)
- `./gradlew runClientGameTest` – gerçek istemciyi açar, tek oyunculu dünyada NPC'yi (Notch skin'iyle) spawn edip
  küçük bir kulübe inşa ettirir ve ekran görüntüsü alır (OpenGL gerekir; kendi bilgisayarında çalıştır)
- `./gradlew runClient` / `runServer` – elle deneme

## Claude skill'leri (`.claude/skills/`)
| Skill | Amaç |
|---|---|
| `mc-mod-setup` | Proje kurulumu, loader/sürüm (Fabric 26.2/26.3 bilgileri) |
| `mc-mod-content` | Item, blok, entity, tarif, datagen |
| `mc-mod-mixins` | Mixin, event, access widener |
| `mc-mod-build-test` | Build, çalıştırma, crash analizi |
| `mc-npc-entity` | NPC entity, attribute, renderer, kayıt |
| `mc-npc-ai` | Goal, Brain, pathfinding |
| `mc-npc-interaction` | Diyalog, ticaret, quest, paket |
| `mc-npc-llm` | LLM ile NPC konuşması |
| `mc-npc-builder` | Bu repodaki inşaatçı NPC kodu |
| `caveman-mode` | Kısa cevap modu |
| `adhd-mode` | Adım adım odaklı cevap modu |
