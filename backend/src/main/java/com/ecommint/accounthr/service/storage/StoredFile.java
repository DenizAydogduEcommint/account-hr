package com.ecommint.accounthr.service.storage;

/**
 * Bir dosya başarıyla depolandıktan sonra dönen metadata.
 *
 * @param relativePath storage köküne göreli yol (ör. {@code 2026-02/google_workspace_subat.pdf})
 * @param fileName     üretilen dosya adı (ör. {@code google_workspace_subat.pdf})
 * @param sha256       içeriğin SHA-256 hex digesti (64 karakter)
 * @param sizeBytes    yazılan içeriğin byte cinsinden boyutu
 */
public record StoredFile(String relativePath, String fileName, String sha256, long sizeBytes) {
}
