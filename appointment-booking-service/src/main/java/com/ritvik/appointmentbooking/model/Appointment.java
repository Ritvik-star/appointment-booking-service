package com.ritvik.appointmentbooking.model;

import java.time.Instant;
import java.util.UUID;

/*
* UUID id	Uniquely identifies each appointment.
String providerId	Determines whose schedule we must check.
Instant startTime	Represents the appointment start in UTC.
Instant endTime	Represents the appointment end in UTC.
AppointmentStatus status	Determines whether the appointment blocks its time slot.
*
* I have used final for class and field so that once appointment is created its provider and times can not
*  be changed by any accident, and if it got cancelled then it will just use the same and update the status and
* then replace the updated version object in storage, which will help to make state transition.
*
* */

public final class Appointment {

    private final UUID id;
    private final String providerId;
    private final Instant startTime;
    private final Instant endTime;
    private final AppointmentStatus status;

    public Appointment(
            UUID id,
            String providerId,
            Instant startTime,
            Instant endTime,
            AppointmentStatus status) {
        this.id = id;
        this.providerId = providerId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getProviderId() {
        return providerId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public Appointment withStatus(AppointmentStatus newStatus) {
        return new Appointment(
                this.id,
                this.providerId,
                this.startTime,
                this.endTime,
                newStatus
        );
    }
}
