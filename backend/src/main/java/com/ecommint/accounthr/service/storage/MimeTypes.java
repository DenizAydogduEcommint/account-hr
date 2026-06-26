package com.ecommint.accounthr.service.storage;

import java.util.Locale;
import java.util.Set;

/**
 * Sunucu tarafı MIME-tipi türetme + önizleme allowlist'i (E3 deep-review #1 — Stored-XSS).
 *
 * <h2>Neden</h2>
 * Multipart yüklemede istemcinin gönderdiği {@code Content-Type} parçası GÜVENİLMEZ:
 * saldırgan {@code invoice.pdf} adıyla ama {@code Content-Type: text/html} +
 * {@code <script>} gövdesiyle dosya yükleyip {@code GET /files/{id}/preview} (inline)
 * üzerinden aynı-origin HTML çalıştırabilir (stored-XSS). {@code X-Content-Type-Options:
 * nosniff} bile AÇIKÇA {@code text/html} verilen yanıtı engellemez.
 *
 * <p>Çözüm: {@code mimeType} ASLA istemciden alınmaz. Uzantı doğrulandıktan SONRA
 * ({@code validateFile}) tip SUNUCU TARAFINDA uzantıdan türetilir
 * ({@link #fromExtension}). Ek savunma olarak önizleme yolu da depolanan mime'ı bir
 * ALLOWLIST'ten geçirir ({@link #isPreviewSafe}); listede yoksa {@code
 * application/octet-stream} ile servis edilir (asla inline aktif içerik render etmez).
 */
public final class MimeTypes {

    /** Genel "ne olduğu belirsiz" varsayılanı — tarayıcı render etmez, indirir. */
    public static final String OCTET_STREAM = "application/octet-stream";

    /**
     * Önizleme için inline servis edilmesi GÜVENLİ MIME tipleri. Aktif içerik
     * (text/html, image/svg+xml, application/javascript vb.) KASTEN dışarıdadır.
     */
    private static final Set<String> PREVIEW_ALLOWLIST = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/xml",
            "text/xml");

    private MimeTypes() {
    }

    /**
     * Dosya adının uzantısından SUNUCU TARAFINDA MIME tipi türetir (istemci
     * {@code Content-Type}'ı yok sayılır). İzinli uzantılar:
     * {@code pdf, xml, jpg, jpeg, png}; tanınmayan/uzantısız → {@link #OCTET_STREAM}.
     */
    public static String fromExtension(String filename) {
        String ext = extensionOf(filename);
        if (ext == null) {
            return OCTET_STREAM;
        }
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "xml" -> "application/xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> OCTET_STREAM;
        };
    }

    /**
     * Depolanan mime önizlemede inline render edilmeye GÜVENLİ mi? (allowlist üyeliği,
     * büyük/küçük harf duyarsız). {@code null}/boş → güvenli değil.
     */
    public static boolean isPreviewSafe(String mime) {
        if (mime == null || mime.isBlank()) {
            return false;
        }
        return PREVIEW_ALLOWLIST.contains(mime.trim().toLowerCase(Locale.ROOT));
    }

    /** Dosya adından küçük-harf uzantı (noktasız); yoksa null. */
    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
