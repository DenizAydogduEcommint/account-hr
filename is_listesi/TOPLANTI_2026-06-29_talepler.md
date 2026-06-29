# 15:00 Toplantı — İletilecek Talepler (2026-06-29)

> Projeyi ilerletmek için ekipten gereken iki net girdi. İkisi de şu an **bloklu**; gelmeden ilgili özellikler tamamlanamaz.

## 1) Gerçek banka ekstresi gerekiyor (E4 — banka ekstresi işleme)

**Durum:** Selman'ın paylaştığı `re2025kredikartekstreleri` klasöründeki 13 dosya aslında **servis faturası** (OpenAI, ChatGPT, Kapwing, Lucidchart, OpenRouter, Wondershare — 2025 Eylül–Aralık). Bunlar **banka ekstresi değil**, fatura. (İçerikleri açılıp doğrulandı.)

**İhtiyaç:** Üç kartın **kredi kartı işlem dökümü** (ekstre = tarih + işyeri/açıklama + tutar **listesi**, fatura değil):
- Akbank Axess ****3800
- YKB Ticari ****3909
- Ziraat Bankkart ****9164

**Notlar:**
- Banka uygulamasından/internet bankacılığından alınan hesap/kart **hareket dökümü** kastediliyor.
- Format fark etmez ama **Excel veya PDF tercih**; **JPG gelirse OCR (görüntüden metin) ayrı bir iş** olur, daha uzun sürer.
- Her karttan **birer örnek** yeterli — parser bunlara göre yazılacak.
- Konacak yer: `~/account-hr-data/` (yerel; Drive aynasına gerek yok).

## 2) accounting@e-commint.com posta kutusuna dönüştürülmeli (E5-01 — otomatik fatura toplama)

**Durum:** accounting@e-commint.com şu an bir **grup / dağıtım listesi**, gerçek bir **posta kutusu (mailbox) değil**. Bu yüzden sisteme programatik bağlanılamıyor (IMAP / Gmail API çalışmaz) — otomatik mail-fatura toplama yapılamıyor.

**İhtiyaç (biri):**
- accounting@ gerçek bir **mailbox'a** dönüştürülsün, **veya**
- Faturaların düştüğü gerçek bir mailbox + erişim verilsin:
  - **Gmail API** (Workspace service account + domain-wide delegation — Workspace admin onayı gerekir), **veya**
  - **IMAP + uygulama şifresi** (app password).

**Sorumlu:** Fatma / IT (Workspace admin).

---

## Bu arada (girdi beklemeden yapılanlar)
- **E5-03 (fatura belge okuma):** Selman'ın paylaştığı 13 gerçek fatura ile PDF fatura-okuma parser'ı geliştiriliyor (fatura no, tarih, tutar, KDV, sağlayıcı otomatik çıkarımı). Bu, ileride mail/Drive'dan gelen faturaları otomatik işlemeyi besleyecek.
- Gerçek faturalar **repoya konmaz** (gizli şirket verisi); sadece yerelde örnek olarak kullanılır.
