package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.UpdateItemRequest;
import com.eexchange.backend.Entity.ItemStatus;
import com.eexchange.backend.Entity.items;
import com.eexchange.backend.Repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public items addItem(items item, String userEmail) {
        item.setStatus(ItemStatus.PENDING);
        item.setUserEmail(userEmail);           //set the logged-in user's email
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        return itemRepository.save(item);
    }

    public List<items> getAll() {
        return itemRepository.findByStatus(ItemStatus.AVAILABLE);
    }

    public List<items> getMyItems(String userEmail) {
        return itemRepository.findByUserEmail(userEmail);
    }

    public items editItem(String itemId, String userEmail, String role, UpdateItemRequest request) {
        items existing = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        boolean isAdmin = "ROLE_ADMIN".equals(role);
        if (!existing.getUserEmail().equals(userEmail) && !isAdmin) {
            throw new RuntimeException("You are not authorized to edit this item");
        }

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setCategory(request.getCategory());
        return itemRepository.save(existing);
    }

    public List<items> getPendingItems() {
        return itemRepository.findByStatus(ItemStatus.PENDING);
    }

    public items approveItem(String itemId) {
        items item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        item.setStatus(ItemStatus.AVAILABLE);
        item.setUpdatedAt(LocalDateTime.now());
        return itemRepository.save(item);
    }

    public void declineItem(String itemId) {
        itemRepository.deleteById(itemId);
    }

    public void deleteMyItem(String itemId, String userEmail, String role) {
        items item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));
        
        boolean isAdmin = "ROLE_ADMIN".equals(role);
        if (!item.getUserEmail().equals(userEmail) && !isAdmin) {
            throw new RuntimeException("You are not authorized to delete this item");
        }
        
        itemRepository.deleteById(itemId);
    }
}
