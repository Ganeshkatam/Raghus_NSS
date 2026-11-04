package edu.college.nss.web;

import edu.college.nss.service.PublicService;
import edu.college.nss.web.dto.PublicDTOs.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final PublicService publicService;

    public PublicController(PublicService publicService) {
        this.publicService = publicService;
    }

    @GetMapping("/overview")
    public ResponseEntity<PublicOverviewResponse> getOverview() {
        return ResponseEntity.ok(publicService.getOverview());
    }

    @GetMapping("/events")
    public ResponseEntity<Page<PublicEventResponse>> getEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(publicService.getEvents(page, size));
    }

    @GetMapping("/units")
    public ResponseEntity<List<PublicUnitResponse>> getUnits() {
        return ResponseEntity.ok(publicService.getUnits());
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<PublicAnnouncementResponse>> getAnnouncements() {
        return ResponseEntity.ok(publicService.getAnnouncements());
    }
}

