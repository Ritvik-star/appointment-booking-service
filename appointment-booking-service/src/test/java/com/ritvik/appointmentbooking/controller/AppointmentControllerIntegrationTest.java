
package com.ritvik.appointmentbooking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String START = "2026-10-31T10:00:00Z";
    private static final String END = "2026-10-31T11:00:00Z";

    private String bookingBody(String providerId, String startTime, String endTime) throws Exception {

        return objectMapper.writeValueAsString(
                Map.of(
                        "providerId", providerId,
                        "startTime", startTime,
                        "endTime", endTime
                )
        );
    }

    private String uniqueProvider() {
        return "provider-" + UUID.randomUUID();
    }

    // 1. Successful HTTP booking
    @Test
    void shouldCreateAppointmentThroughApi() throws Exception {

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                uniqueProvider(), START, END
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // 2. Same-provider overlapping booking
    @Test
    void shouldReturnConflictForOverlappingBooking() throws Exception {

        String providerId = uniqueProvider();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(providerId, START, END)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                providerId,
                                "2026-10-31T10:30:00Z",
                                "2026-10-31T11:30:00Z"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // 3. Adjacent appointments
    @Test
    void shouldAllowAdjacentAppointmentsThroughApi() throws Exception {

        String providerId = uniqueProvider();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(providerId, START, END)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                providerId,
                                END,
                                "2026-10-31T12:00:00Z"
                        )))
                .andExpect(status().isCreated());
    }

    // 4. Different providers, same time
    @Test
    void shouldAllowDifferentProvidersAtSameTime() throws Exception {

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                uniqueProvider(), START, END
                        )))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                uniqueProvider(), START, END
                        )))
                .andExpect(status().isCreated());
    }

    // 5. Cancellation and subsequent rebooking
    @Test
    void shouldAllowRebookingAfterCancellation() throws Exception {

        String providerId = uniqueProvider();

        MvcResult bookingResult = mockMvc.perform(
                        post("/api/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bookingBody(
                                        providerId, START, END
                                )))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(
                bookingResult.getResponse().getContentAsString()
        );

        String appointmentId = response.get("id").asText();

        mockMvc.perform(
                        post("/api/appointments/{id}/cancel",
                                appointmentId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(providerId, START, END)))
                .andExpect(status().isCreated());
    }

    // 6. Invalid duration
    @Test
    void shouldRejectInvalidDurationThroughApi() throws Exception {

        String providerId = uniqueProvider();

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(
                                providerId, START, START
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // 7. Missing provider identifier
    @Test
    void shouldRejectMissingProviderId() throws Exception {

        String request = objectMapper.writeValueAsString(
                Map.of(
                        "startTime", START,
                        "endTime", END
                )
        );

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    // 8. Invalid timestamp format
    @Test
    void shouldRejectInvalidTimestampFormat() throws Exception {

        String request = """
                {
                    "providerId": "provider-201",
                    "startTime": "invalid-timestamp",
                    "endTime": "2026-10-31T11:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    // 9. Missing appointment cancellation
    @Test
    void shouldReturnNotFoundForMissingAppointment() throws Exception {

        mockMvc.perform(
                        post("/api/appointments/{id}/cancel",
                                UUID.randomUUID())
                )
                .andExpect(status().isNotFound());
    }

    // 10. Already cancelled appointment
    @Test
    void shouldRejectRepeatedCancellationThroughApi() throws Exception {

        String providerId = uniqueProvider();

        MvcResult result = mockMvc.perform(
                        post("/api/appointments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(bookingBody(
                                        providerId, START, END
                                )))
                .andExpect(status().isCreated())
                .andReturn();

        String appointmentId = objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).get("id").asText();

        mockMvc.perform(
                        post("/api/appointments/{id}/cancel",
                                appointmentId)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/appointments/{id}/cancel",
                                appointmentId)
                )
                .andExpect(status().isConflict());
    }
}
