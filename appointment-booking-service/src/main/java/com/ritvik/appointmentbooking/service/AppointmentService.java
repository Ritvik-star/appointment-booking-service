package com.ritvik.appointmentbooking.service;

import com.ritvik.appointmentbooking.model.Appointment;
import com.ritvik.appointmentbooking.model.AppointmentStatus;
import com.ritvik.appointmentbooking.exception.AppointmentAlreadyCancelledException;
import com.ritvik.appointmentbooking.exception.AppointmentConflictException;
import com.ritvik.appointmentbooking.exception.AppointmentNotFoundException;
import com.ritvik.appointmentbooking.exception.InvalidAppointmentException;
import com.ritvik.appointmentbooking.repository.AppointmentRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AppointmentService {
    private static final int LOCK_STRIPES = 256;

    private final AppointmentRepository appointmentRepository;
    private final ReentrantLock[] providerLocks = new ReentrantLock[LOCK_STRIPES];

    public AppointmentService(AppointmentRepository appointmentRepository){

        this.appointmentRepository = appointmentRepository;

        //initialize a fixed number of reusable locks.
        for(int i=0; i< LOCK_STRIPES; i++){
            providerLocks[i] = new ReentrantLock();
        }

    }

    /*
    *
    * Books an appointment after validating the request
      and checking for active appointment conflicts.
    *
     */

    public Appointment bookAppointment(
            String providerId,
            Instant startTime,
            Instant endTime
    ) {
        validateBookingRequest(providerId, startTime, endTime);

        ReentrantLock lock = getProviderLock(providerId);

        lock.lock();

        try{
            //check whether an active appointment overlaps.

            boolean hasConflict = appointmentRepository
                    .findOverlappingActive(
                            providerId,
                            startTime,
                            endTime
                    ).isPresent();

            if(hasConflict){
                throw new AppointmentConflictException("Provider already has an overlapping active appointment.");
            }

            //creates the appointment only after validation.

            Appointment appointment = new Appointment(
                    UUID.randomUUID(),
                    providerId,
                    startTime,
                    endTime,
                    AppointmentStatus.ACTIVE
            );

            return appointmentRepository.save(appointment);
        } finally {
            lock.unlock();
        }
    }


//  Cancels an existing active appointment.

    public Appointment cancelAppointment(UUID appointmentId){
        if (appointmentId == null){
            throw new InvalidAppointmentException("Appointment id is required.");
        }

        //Initial lookup to determine which provider must be locked.
        Appointment existingAppointment = appointmentRepository
                .findById(appointmentId)
                .orElseThrow(
                        () -> new AppointmentNotFoundException(appointmentId)
                );

        String providerId = existingAppointment.getProviderId();

        ReentrantLock lock = getProviderLock(providerId);

        lock.lock();

        try{
            //re-read inside the lock to avoid acting on stale state

            Appointment currentAppointment = appointmentRepository
                    .findById(appointmentId)
                    .orElseThrow(
                            () -> new AppointmentNotFoundException(appointmentId)
                    );

            if(currentAppointment.getStatus() == AppointmentStatus.CANCELLED){
                throw new AppointmentAlreadyCancelledException(appointmentId);
            }

            //create new immutable appointment with cancelled status.
            Appointment cancelledAppointment = currentAppointment.withStatus(AppointmentStatus.CANCELLED);

            return appointmentRepository.save(cancelledAppointment);
        } finally {
            lock.unlock();
        }
    }


    // now validates all mandatory booking inputs.
    private void validateBookingRequest(String providerId, Instant startTime, Instant endTime){
        if(providerId == null || providerId.isBlank()){
            throw new InvalidAppointmentException("Provider Id is required");
        }

        if(startTime == null || endTime == null){
            throw new InvalidAppointmentException("Start and end time are required.");
        }

        if(!startTime.isBefore((endTime))){
            throw new InvalidAppointmentException(
                    "Appointment end time must be after start time."
            );
        }
    }


//  maps a provider ID to one of the fixed lock stripes.
private ReentrantLock getProviderLock(String providerId){

        int index = Math.floorMod(
                providerId.hashCode(),
                LOCK_STRIPES
        );

        return providerLocks[index];
}

}



/*
* 1.   why we have used 256 ReentrantLocks ?
*
*   We don't want to create a separate lock object for every provider and retain those objects indefinitely.

Instead, we use 256 reusable locks.

Provider IDs are mapped to locks using their hash codes.

For example:
Provider	Lock
Provider A	Lock 12
Provider B	Lock 43
Provider C	Lock 12
Provider D	Lock 91

Provider A and C happen to share a lock. This may occasionally serialize unrelated requests, but it doesn't compromise correctness.
*
*
*
* 2. Why do we lock the entire booking operation?
        - sequence is:
        *
  Acquire provider lock
       |
       v
Check overlapping appointments
       |
       v
Conflict found?
   /       \
 YES        NO
  |          |
Reject    Create appointment
             |
             v
          Save booking
             |
             v
        Release lock

*
*
*   Without this lock, two requests could both pass the conflict check before either saves its appointment.
*
*
*
* */
