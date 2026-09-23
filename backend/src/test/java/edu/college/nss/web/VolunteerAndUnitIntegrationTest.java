package edu.college.nss.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.college.nss.service.NssUnitService;
import edu.college.nss.service.VolunteerService;
import edu.college.nss.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class VolunteerAndUnitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VolunteerService volunteerService;

    @MockitoBean
    private NssUnitService unitService;

    @Test
    @WithMockUser(username = "admin@raghunss.edu", roles = {"ADMIN"})
    public void createUnit_asAdmin_shouldReturnCreated() throws Exception {
        UnitResponse response = new UnitResponse(1L, "NSS Unit 1", "UNIT-01", null, null, null, 0, Instant.now());
        when(unitService.createUnit(any(UnitRequest.class))).thenReturn(response);

        UnitRequest request = new UnitRequest("NSS Unit 1", "UNIT-01", null);

        mockMvc.perform(post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unitName").value("NSS Unit 1"))
            .andExpect(jsonPath("$.unitNumber").value("UNIT-01"));
    }

    @Test
    @WithMockUser(username = "volunteer@raghunss.edu", roles = {"VOLUNTEER"})
    public void createUnit_asVolunteer_shouldReturnForbidden() throws Exception {
        UnitRequest request = new UnitRequest("Unauthorized Unit", "UNIT-99", null);

        mockMvc.perform(post("/api/v1/units")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "admin@raghunss.edu", roles = {"ADMIN"})
    public void createVolunteer_asAdmin_shouldReturnCreated() throws Exception {
        VolunteerResponse response = new VolunteerResponse(
            1L, UUID.randomUUID(), "Jane Doe", "jane@raghunss.edu", "9123456780",
            "2026CS001", "Computer Science", 2, LocalDate.now(), "ACTIVE",
            null, null, Instant.now()
        );
        when(volunteerService.createVolunteer(any(VolunteerRequest.class))).thenReturn(response);

        VolunteerRequest request = new VolunteerRequest(
            "Jane Doe", "jane@raghunss.edu", "9123456780",
            "2026CS001", "Computer Science", 2, "Pass@123"
        );

        mockMvc.perform(post("/api/v1/volunteers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.collegeId").value("2026CS001"))
            .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    @WithMockUser(username = "admin@raghunss.edu", roles = {"ADMIN"})
    public void addMemberToUnit_asAdmin_shouldReturnCreated() throws Exception {
        MembershipResponse response = new MembershipResponse(
            1L, 1L, "Jane Doe", "2026CS001", "Computer Science",
            1L, "NSS Unit 1", "UNIT-01", Instant.now(), null, true
        );
        when(unitService.addMemberToUnit(eq(1L), eq(1L))).thenReturn(response);

        MembershipRequest request = new MembershipRequest(1L);

        mockMvc.perform(post("/api/v1/units/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unitName").value("NSS Unit 1"))
            .andExpect(jsonPath("$.volunteerName").value("Jane Doe"))
            .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @WithMockUser(username = "jane@raghunss.edu", roles = {"VOLUNTEER"})
    public void getUnitMembers_asVolunteer_shouldReturnOk() throws Exception {
        MembershipResponse response = new MembershipResponse(
            1L, 1L, "Jane Doe", "2026CS001", "Computer Science",
            1L, "NSS Unit 1", "UNIT-01", Instant.now(), null, true
        );
        when(unitService.getUnitMembers(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/units/1/members"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].volunteerName").value("Jane Doe"));
    }
}
