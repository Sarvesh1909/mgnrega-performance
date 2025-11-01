package com.mgnrega.backend;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/districts")
@CrossOrigin(origins = "*")
public class DistrictController {
    @GetMapping
    public List<Map<String, Object>> getDistricts() {
        return List.of(
            Map.of("id", 1, "name", "Lucknow"),
            Map.of("id", 2, "name", "Kanpur"),
            Map.of("id", 3, "name", "Varanasi"),
            Map.of("id", 101, "name", "Mumbai"),
            Map.of("id", 102, "name", "Pune"),
            Map.of("id", 103, "name", "Nagpur"),
            Map.of("id", 104, "name", "Nashik"),
            Map.of("id", 105, "name", "Aurangabad"),
            Map.of("id", 106, "name", "Thane"),
            Map.of("id", 107, "name", "Solapur"),
            Map.of("id", 108, "name", "Amravati"),
            Map.of("id", 109, "name", "Kolhapur")
        );
    }
}
