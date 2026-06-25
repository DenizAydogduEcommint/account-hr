package com.ecommint.accounthr.service.importer;

/** Excel import sırasında oluşan hatalar (E2-01). */
public class ExcelImportException extends RuntimeException {

    public ExcelImportException(String message) {
        super(message);
    }

    public ExcelImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
