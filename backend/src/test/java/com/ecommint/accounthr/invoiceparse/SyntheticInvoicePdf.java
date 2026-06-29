package com.ecommint.accounthr.invoiceparse;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Test yardımcısı: PDFBox ile, GERÇEK Stripe tarzı faturaların metin düzenini taklit eden
 * SENTETİK bir PDF üretir. Gerçek (gizli) müşteri PDF'leri repoya ASLA konmaz — testler
 * yalnızca burada in-memory üretilen sahte içerikle çalışır.
 *
 * <p>Her satır ayrı bir metin satırı olarak yazılır; böylece {@link org.apache.pdfbox.text.PDFTextStripper}
 * çıktısı gerçek faturalardaki satır-bazlı düzene benzer.
 */
final class SyntheticInvoicePdf {

    private SyntheticInvoicePdf() {
    }

    /** Verilen metin satırlarından tek sayfalık bir PDF'in byte'larını üretir. */
    static byte[] of(List<String> lines) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 11);
                cs.setLeading(16f);
                cs.newLineAtOffset(50, 750);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Sentetik PDF üretilemedi", e);
        }
    }
}
