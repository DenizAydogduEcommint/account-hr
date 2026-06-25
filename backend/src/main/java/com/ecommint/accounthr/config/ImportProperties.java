package com.ecommint.accounthr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Veri-aktarım (import) yapılandırması (E2-03). {@code app.import.*}.
 *
 * <p>{@code invoiceFilesSourceDir}: {@code faturalar/} klasör taramasının varsayılan
 * kaynak dizini (Drive aynası — READ-ONLY). {@code INVOICE_FILES_SOURCE_DIR} env'inden
 * gelir. Geliştirici makinesinde varsayılanı {@code expenses/faturalar}'dır; staging/prod'da
 * env ile verilir. Sabit (compile edilmiş) bir geliştirici yolu KULLANILMAZ.
 *
 * <p>İstekteki {@code sourceDir} parametresi bu köke göre doğrulanabilir; üretimde
 * keyfi dizinlerin taranmasını engellemek için kullanılır.
 */
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    /** {@code faturalar/} klasör taramasının varsayılan + izinli kök kaynak dizini. */
    private String invoiceFilesSourceDir;

    public String getInvoiceFilesSourceDir() {
        return invoiceFilesSourceDir;
    }

    public void setInvoiceFilesSourceDir(String invoiceFilesSourceDir) {
        this.invoiceFilesSourceDir = invoiceFilesSourceDir;
    }
}
