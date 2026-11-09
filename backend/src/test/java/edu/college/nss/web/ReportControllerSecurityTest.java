package edu.college.nss.web;

import edu.college.nss.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ReportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    @DisplayName("I-07: Volunteers export requires REPORTS_EXPORT authority - Denied for REPORTS_VIEW only")
    @WithMockUser(username = "po@raghunss.edu", authorities = {"REPORTS_VIEW"})
    void exportVolunteers_reportsViewOnly_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/volunteers"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I-07: Volunteers export allowed for user with REPORTS_EXPORT")
    @WithMockUser(username = "admin@raghunss.edu", authorities = {"REPORTS_VIEW", "REPORTS_EXPORT"})
    void exportVolunteers_reportsExport_allowed() throws Exception {
        when(reportService.exportVolunteersCsv()).thenReturn("col1,col2\nval1,val2".getBytes());
        mockMvc.perform(get("/api/v1/reports/export/volunteers"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("I-07: Events export requires REPORTS_EXPORT authority - Denied for REPORTS_VIEW only")
    @WithMockUser(username = "po@raghunss.edu", authorities = {"REPORTS_VIEW"})
    void exportEvents_reportsViewOnly_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/events"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I-07: Events export allowed for user with REPORTS_EXPORT")
    @WithMockUser(username = "admin@raghunss.edu", authorities = {"REPORTS_VIEW", "REPORTS_EXPORT"})
    void exportEvents_reportsExport_allowed() throws Exception {
        when(reportService.exportEventsCsv()).thenReturn("col1,col2\nval1,val2".getBytes());
        mockMvc.perform(get("/api/v1/reports/export/events"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("I-07: Service hours export requires REPORTS_EXPORT authority - Denied for REPORTS_VIEW only")
    @WithMockUser(username = "po@raghunss.edu", authorities = {"REPORTS_VIEW"})
    void exportServiceHours_reportsViewOnly_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/service-hours"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("I-07: Service hours export allowed for user with REPORTS_EXPORT")
    @WithMockUser(username = "admin@raghunss.edu", authorities = {"REPORTS_VIEW", "REPORTS_EXPORT"})
    void exportServiceHours_reportsExport_allowed() throws Exception {
        when(reportService.exportServiceHoursCsv()).thenReturn("col1,col2\nval1,val2".getBytes());
        mockMvc.perform(get("/api/v1/reports/export/service-hours"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("I-07: Export endpoints require authentication - Denied for unauthenticated callers")
    void exportEndpoints_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/volunteers"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reports/export/events"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reports/export/service-hours"))
            .andExpect(status().isUnauthorized());
    }
}

