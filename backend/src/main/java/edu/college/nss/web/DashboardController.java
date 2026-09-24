package edu.college.nss.web;

import edu.college.nss.service.DashboardService;
import edu.college.nss.web.dto.DashboardSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
        @AuthenticationPrincipal UserDetails principal
    ) {
        DashboardSummaryResponse response = dashboardService.getSummary(principal);
        return ResponseEntity.ok(response);
    }
}
