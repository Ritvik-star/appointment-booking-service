package com.ritvik.appointmentbooking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

//This represents the incoming booking request.
public record BookAppointmentRequest(

        @NotBlank(message = "Provider ID is required")
        String providerId,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime

) {
}


/*
* Java record?
- Immutable request object.
- Automatically generates constructor and accessor methods.
- Less boilerplate than a traditional DTO class.
- Suitable for Java 21 because introduced in Java 16
@NotBlank and @NotNull provide request-level validation.
*  The service will still validate the business rules independently.
*
* */