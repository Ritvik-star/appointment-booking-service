package com.ritvik.appointmentbooking.repository;

import com.ritvik.appointmentbooking.model.Appointment;
import com.ritvik.appointmentbooking.model.AppointmentStatus;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

/*
* AppointmentRepository interface -	Defines the storage operations.
  InMemoryAppointmentRepository class -	Stores appointment objects in a ConcurrentHashMap.
  save()	- Creates or replaces the stored appointment for its ID.
  findById() -	Retrieves an appointment by its UUID.
  findActiveByProviderId() -	Returns only active appointments belonging to the specified provider.
* */
@Repository
public class InMemoryAppointmentRepository implements AppointmentRepository {

    //this map stores all the appointments including all cancelled appointments.
    private final ConcurrentMap<UUID, Appointment> appointmentsById = new ConcurrentHashMap<>();

    //this map stores only active appointment which are sorted by start time per provider.
    private final ConcurrentMap<String, ConcurrentNavigableMap<Instant, Appointment>>
            activeAppointmentsByProvider = new ConcurrentHashMap<>(0);

    @Override
    public Appointment save(Appointment appointment) {
        Objects.requireNonNull(appointment, "Appointment should not be null");

        appointmentsById .put(appointment.getId(), appointment);

        String providerId = appointment.getProviderId();
        Instant startTime = appointment.getStartTime();

        if(appointment.getStatus() == AppointmentStatus.ACTIVE){
            ConcurrentNavigableMap<Instant, Appointment>
                    schedule =  activeAppointmentsByProvider.computeIfAbsent(
                            providerId,
                            key -> new ConcurrentSkipListMap<>()
            );

            schedule.put(startTime, appointment);
        } else {
            ConcurrentNavigableMap<Instant, Appointment> schedule =
                    activeAppointmentsByProvider.get(providerId);

            if(schedule != null){

                schedule.computeIfPresent(
                        startTime, (key, existing) -> existing.getId().equals(
                                appointment.getId()) ? null : existing
                );

                if(schedule.isEmpty()){
                    activeAppointmentsByProvider.remove(providerId, schedule);
                }
            }
        }

        return appointment;
    }

    @Override
    public Optional<Appointment> findById(UUID id){
        return Optional.ofNullable(appointmentsById.get(id));
    }


//    @Override
//    public List<Appointment> findActiveByProviderId(String providerId){
//        return appointments.values()
//                .stream()
//                .filter(
//                        appointment -> Objects.equals(appointment.getProviderId(), providerId)
//                )
//                .filter(appointment -> appointment.getStatus() == AppointmentStatus.ACTIVE)
//                .toList();
//    }

    //OPTIMIZATION CHANGES -

    @Override
    public Optional<Appointment> findOverlappingActive(
            String providerId,
            Instant startTime,
            Instant endTime
    ){

        ConcurrentNavigableMap<Instant, Appointment> schedule =
                activeAppointmentsByProvider.get(providerId);

        if(schedule == null || schedule.isEmpty()){
            return Optional.empty();
        }

        //Check the nearest appointment starting at or before new start.
        Map.Entry<Instant, Appointment> previous = schedule.floorEntry(startTime);

        if(previous != null && previous.getValue().getEndTime().isAfter(startTime)){

            return Optional.of(previous.getValue());
        }

        // Check the nearest appointment starting at or after new start.

        Map.Entry<Instant, Appointment> next = schedule.ceilingEntry(startTime);

        if(next != null && next.getKey().isBefore(endTime)){
            return Optional.of(next.getValue());
        }

        return Optional.empty();
    }

    /*
    *note - The predecessor-and-successor optimization is valid because our service will maintain the invariant
    * that existing active appointments for a provider never overlap.
    * The service will also serialize booking and cancellation operations for that provider.
    *
    *
    *
    * To understand the optimization -
    * for example -
    *we have 4 appointments already booked
    *09:00 ───── 10:00
    11:00 ───── 12:00
    14:00 ───── 15:00
    17:00 ───── 18:00
    *
    *   A new appointment arrives for 11:30–12:30.
        We don't need to inspect every appointment.
    *
    *   We can use:
        - floorEntry() to find the closest appointment starting at or before 11:30.
        - ceilingEntry() to find the closest appointment starting at or after 11:30.
        The predecessor ends at 12:00, so we immediately identify the conflict.
        *
        *
        *
        * Why this matters - We are maintaining two indexes: one for retrieving appointments by UUID and
        *  another for efficient provider-specific conflict detection. The second index is maintained by activeAppointmentsByProvider,

    * */


}
