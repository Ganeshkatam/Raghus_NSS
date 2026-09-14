package edu.college.nss.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class RootController {
    @GetMapping("/api/v1")
    public Map<String, String> apiRoot() {
        return Map.of(
            "service", "nss-backend",
            "version", "v1",
            "status", "ready"
        );
    }
}
