package com.eexchange.backend.Controller;

import com.eexchange.backend.Entity.items;
import com.eexchange.backend.Entity.User;
import com.eexchange.backend.Service.ItemService;
import com.eexchange.backend.Repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "https://frontend-eexchange.vercel.app/")
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ItemService itemService;
    private final UserRepository userRepository;

    public AdminController(ItemService itemService, UserRepository userRepository) {
        this.itemService = itemService;
        this.userRepository = userRepository;
    }

    // --- ITEM MANAGEMENT ---

    @GetMapping("/items/pending")
    public List<items> getPendingItems() {
        return itemService.getPendingItems();
    }

    @PutMapping("/items/approve/{id}")
    public ResponseEntity<items> approveItem(@PathVariable String id) {
        items approved = itemService.approveItem(id);
        return ResponseEntity.ok(approved);
    }

    @DeleteMapping("/items/decline/{id}")
    public ResponseEntity<?> declineItem(@PathVariable String id) {
        itemService.declineItem(id);
        return ResponseEntity.ok(Map.of("message", "Item declined and removed"));
    }

    // --- USER MANAGEMENT ---

    @GetMapping("/users")
    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Don't send passwords back
        users.forEach(u -> u.setPassword(null));
        return users;
    }

    @PutMapping("/users/toggle-block/{id}")
    public ResponseEntity<User> toggleBlockUser(@PathVariable String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setBlocked(!user.isBlocked());
        User saved = userRepository.save(user);
        saved.setPassword(null);
        return ResponseEntity.ok(saved);
    }
}
