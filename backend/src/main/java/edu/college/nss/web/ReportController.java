package edu.college.nss.web;

import edu.college.nss.service.ReportService;
import edu.college.nss.web.dto.ReportDTOs.InstitutionalMetricsResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN','FACULTY_COORDINATOR','PROGRAMME_OFFICER')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/metrics")
    public ResponseEntity<InstitutionalMetricsResponse> getMetrics() {
        return ResponseEntity.ok(service.getInstitutionalMetrics());
    }

    @GetMapping("/export/volunteers")
    public ResponseEntity<byte[]> exportVolunteers() {
        byte[] csv = service.exportVolunteersCsv();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=nss_volunteers_roster.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @GetMapping("/export/events")
    public ResponseEntity<byte[]> exportEvents() {
        byte[] csv = service.exportEventsCsv();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=nss_events_roster.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }

    @GetMapping("/export/service-hours")
    public ResponseEntity<byte[]> exportServiceHours() {
        byte[] csv = service.exportServiceHoursCsv();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=nss_service_hours_accreditation.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(csv);
    }
}
