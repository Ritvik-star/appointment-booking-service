package com.ritvik.appointmentbooking.exception;

public class InvalidAppointmentException extends RuntimeException {
    public InvalidAppointmentException(String message){
        super(message);
    }
}
