package edu.college.nss.web;

import edu.college.nss.service.PublicService;
import edu.college.nss.web.dto.PublicDTOs.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class PublicApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicService publicService;

    @Test
    public void getOverview_anonymousAccess_shouldReturnPublicDataWithoutPiiOrInternalIds() throws Exception {
        PublicEventResponse event = new PublicEventResponse(
            "Mega Blood Donation Drive",
            "College-wide donation camp in collaboration with Red Cross",
            "HEALTH",
            "COLLEGE_WIDE",
            "Campus Auditorium",
            Instant.parse("2026-11-20T04:00:00Z"),
            Instant.parse("2026-11-20T11:00:00Z"),
            "OPEN",
            "UNIT-01",
            "NSS Unit 01"
        );

        PublicAnnouncementResponse notice = new PublicAnnouncementResponse(
            "Upcoming Blood Drive Schedule",
            "Registration is open for all student volunteers.",
            "HIGH",
            Instant.parse("2026-11-01T09:00:00Z"),
            "UNIT-01",
            "NSS Unit 01"
        );

        PublicOverviewResponse overview = new PublicOverviewResponse(
            2L,
            15L,
            4L,
            3L,
            List.of(event),
            List.of(notice)
        );

        when(publicService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/public/overview")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeDrivesCount", is(2)))
            .andExpect(jsonPath("$.completedDrivesCount", is(15)))
            .andExpect(jsonPath("$.verifiedUnitsCount", is(4)))
            .andExpect(jsonPath("$.publishedNoticesCount", is(3)))
            .andExpect(jsonPath("$.upcomingDrives", hasSize(1)))
            .andExpect(jsonPath("$.upcomingDrives[0].title", is("Mega Blood Donation Drive")))
            .andExpect(jsonPath("$.upcomingDrives[0].organizingUnitNumber", is("UNIT-01")))
            // Assert that internal IDs and PII are NOT present
            .andExpect(jsonPath("$.upcomingDrives[0].eventId").doesNotExist())
            .andExpect(jsonPath("$.upcomingDrives[0].unitId").doesNotExist())
            .andExpect(jsonPath("$.upcomingDrives[0].capacity").doesNotExist())
            .andExpect(jsonPath("$.recentAnnouncements", hasSize(1)))
            .andExpect(jsonPath("$.recentAnnouncements[0].title", is("Upcoming Blood Drive Schedule")))
            .andExpect(jsonPath("$.recentAnnouncements[0].announcementId").doesNotExist())
            .andExpect(jsonPath("$.recentAnnouncements[0].userId").doesNotExist());
    }

    @Test
    public void getEvents_anonymousAccess_shouldReturnPublicEventsPage() throws Exception {
        PublicEventResponse event = new PublicEventResponse(
            "Green Campus Tree Plantation",
            "Massive tree plantation campaign across Dakamarri village",
            "ENVIRONMENT",
            "UNIT",
            "Dakamarri",
            Instant.parse("2026-11-25T03:30:00Z"),
            Instant.parse("2026-11-25T08:30:00Z"),
            "PUBLISHED",
            "UNIT-02",
            "NSS Unit 02"
        );

        when(publicService.getEvents(anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(event)));

        mockMvc.perform(get("/api/v1/public/events")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content", hasSize(1)))
            .andExpect(jsonPath("$.content[0].title", is("Green Campus Tree Plantation")))
            .andExpect(jsonPath("$.content[0].status", is("PUBLISHED")))
            .andExpect(jsonPath("$.content[0].eventId").doesNotExist());
    }

    @Test
    public void getUnits_anonymousAccess_shouldReturnPublicUnitsWithoutInternalAdminFields() throws Exception {
        PublicUnitResponse unit = new PublicUnitResponse(
            "UNIT-01",
            "NSS Unit 01 (CSE & IT)",
            "Dr. K. Srinivas"
        );

        when(publicService.getUnits()).thenReturn(List.of(unit));

        mockMvc.perform(get("/api/v1/public/units")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].unitNumber", is("UNIT-01")))
            .andExpect(jsonPath("$[0].unitName", is("NSS Unit 01 (CSE & IT)")))
            .andExpect(jsonPath("$[0].officerName", is("Dr. K. Srinivas")))
            .andExpect(jsonPath("$[0].unitId").doesNotExist())
            .andExpect(jsonPath("$[0].capacity").doesNotExist());
    }

    @Test
    public void getAnnouncements_anonymousAccess_shouldReturnPublishedNotices() throws Exception {
        PublicAnnouncementResponse notice = new PublicAnnouncementResponse(
            "Blood Donation Notice",
            "Donors can check their eligibility at the NSS office.",
            "NORMAL",
            Instant.parse("2026-11-02T10:00:00Z"),
            "UNIT-01",
            "NSS Unit 01"
        );

        when(publicService.getAnnouncements()).thenReturn(List.of(notice));

        mockMvc.perform(get("/api/v1/public/announcements")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].title", is("Blood Donation Notice")))
            .andExpect(jsonPath("$[0].announcementId").doesNotExist());
    }

    @Test
    public void postToPublicEndpoint_anonymousAccess_shouldBeRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/public/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Fake Event\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void getProtectedOperationalEvents_anonymousAccess_shouldBeRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/events")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    public void getProtectedOperationalVolunteers_anonymousAccess_shouldBeRejectedWithUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/volunteers")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
}

