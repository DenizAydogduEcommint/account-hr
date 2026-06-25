package com.ecommint.accounthr.service.importer;

/** Fatura dosyası klasör tarama/kopyalama/eşleme sırasında oluşan hatalar (E2-03). */
public class InvoiceFileImportException extends RuntimeException {

    public InvoiceFileImportException(String message) {
        super(message);
    }

    public InvoiceFileImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
