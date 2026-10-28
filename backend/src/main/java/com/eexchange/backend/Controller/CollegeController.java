package com.eexchange.backend.Controller;

import com.eexchange.backend.DTO.ApiResponse;
import com.eexchange.backend.DTO.CollegeResponse;
import com.eexchange.backend.Service.CollegeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeCatalogService collegeCatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CollegeResponse>>> getColleges() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Colleges retrieved",
                collegeCatalogService.getActiveColleges()));
    }
}