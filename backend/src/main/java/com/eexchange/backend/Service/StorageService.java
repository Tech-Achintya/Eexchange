package com.eexchange.backend.Service;

import com.eexchange.backend.Service.Storage.ImageType;

public interface StorageService {

    /**
     * Stores the bytes under posts/{userId}/{uuid}.{ext} and returns the object key.
     */
    String store(Long userId, byte[] content, ImageType imageType);

    /**
     * Deletes the object key. Callers that must not fail use their own try/catch.
     */
    void delete(String key);

    /**
     * Builds the public URL from STORAGE_PUBLIC_BASE_URL + key.
     */
    String publicUrl(String key);
}