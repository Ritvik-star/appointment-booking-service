package com.ritvik.appointmentbooking.dto;

import com.ritvik.appointmentbooking.model.Appointment;
import com.ritvik.appointmentbooking.model.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

//This represents the response returned to the client.

public record AppointmentResponse(
        UUID id,
        String providerId,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status
) {

    public static AppointmentResponse from(Appointment appointment){

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getProviderId(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus()
        );
    }
}
