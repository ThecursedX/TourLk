package com.tourlk.controller;

import com.tourlk.enums.Province;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static lookup data the frontend needs to render dropdowns. Requires a
 * valid JWT like every other non-browse endpoint (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/reference-data")
@Tag(name = "Reference Data", description = "Static lookup lists such as provinces and their districts")
public class ReferenceDataController {

    /** Province name (e.g. "NORTH_WESTERN") to its districts, in declaration order. */
    @GetMapping("/provinces")
    public ResponseEntity<Map<Province, List<String>>> provinces() {
        Map<Province, List<String>> body = new LinkedHashMap<>();
        for (Province province : Province.values()) {
            body.put(province, province.getDistricts());
        }
        return ResponseEntity.ok(body);
    }

}
