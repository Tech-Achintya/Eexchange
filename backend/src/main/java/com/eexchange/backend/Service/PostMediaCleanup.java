package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Post;
import com.eexchange.backend.Entity.PostImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

// Shared by the admin services so S3 deletion never breaks a committed action.
@Component
@RequiredArgsConstructor
@Slf4j
public class PostMediaCleanup {

    private final StorageService storageService;

    public List<String> keysOf(Post post) {
        return post.getImages().stream()
                .map(PostImage::getUrl)
                .toList();
    }

    public void deleteAfterCommit(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(keys);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteQuietly(keys);
                    }
                });
    }

    private void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete storage object {}; continuing", key, ex);
            }
        }
    }
}