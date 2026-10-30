package com.ritvik.appointmentbooking.dto;

import java.time.Instant;

//This will give our APIs a consistent error structure.
public record ApiErrorResponse(
        Instant timeStamp,
        int status,
        String error,
        String message,
        String path
){
}
