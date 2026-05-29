package com.cax.cax_backend.word.controller;

import com.cax.cax_backend.common.dto.ApiResponse;
import com.cax.cax_backend.word.model.WordOfTheDay;
import com.cax.cax_backend.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/api/words") @RequiredArgsConstructor
public class WordController {
    private final WordRepository repo;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<WordOfTheDay>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(repo.findByActiveTrue().orElse(null)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<WordOfTheDay>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(repo.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WordOfTheDay>> create(@RequestBody WordOfTheDay body) {
        return ResponseEntity.ok(ApiResponse.created("Word created", repo.save(body)));
    }
}
