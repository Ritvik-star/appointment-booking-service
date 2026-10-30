package com.ritvik.appointmentbooking.exception;

import java.util.UUID;

public class AppointmentAlreadyCancelledException extends RuntimeException{
    public AppointmentAlreadyCancelledException(UUID appointmentId){
        super("Appointment is already cancelled: " + appointmentId);
    }
}
