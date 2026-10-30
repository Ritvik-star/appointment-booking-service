package com.ritvik.appointmentbooking.repository;

import com.ritvik.appointmentbooking.model.Appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

//AppointmentRepository interface -	Defines the storage operations.
public interface AppointmentRepository {
    Appointment save(Appointment appointment);

    Optional<Appointment> findById(UUID Id);

//    List<Appointment> findActiveByProviderId(String providerId);

    /*  OPTIMIZATION -

    * replacing findActiveByProviderId() with findOverlappingActive().
    * because service needs to know whether a conflict exists,
    * not retrieve an entire list of appointments unnecessarily.
    *
    * */

    Optional<Appointment> findOverlappingActive(
            String providerId,
            Instant startTime,
            Instant endTime
    );

    /*
    *With old implementation my repository uses ConcurrentHashMap to store appointments.
    but if we have 100,000 appointments, checking every appointment to find a conflict is inefficient.
    *
    * Instead, will maintain a separate sorted appointment index for each provider.
    and will use Java's ConcurrentSkipListMap, which keeps appointments sorted by their start time.
    *
    *
    *  --- Further changes done in InMemoryAppointmentRepository class ----
    *
    * This gives us:
        - Fast appointment lookup using UUID.
        - Provider-specific appointment lookup.
        - Sorted active appointments.
        - Cancelled appointments retained for cancellation history but removed from the active schedule index.
    * */


}
