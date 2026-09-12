package com.eexchange.backend.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/dummy")
@RestController
public class dummyController {
    @GetMapping("/api/cron/run-task")
    public ResponseEntity<?> runTask(@RequestHeader(value = "X-CRON-KEY", required = false) String key) {
        if (!"mySecret123".equals(key)) {
            return ResponseEntity.status(403).body("Forbidden");
        }
        return ResponseEntity.ok("Done");
    }
}
