package com.ritvik.appointmentbooking.service;

import com.ritvik.appointmentbooking.model.Appointment;
import com.ritvik.appointmentbooking.model.AppointmentStatus;
import com.ritvik.appointmentbooking.exception.AppointmentAlreadyCancelledException;
import com.ritvik.appointmentbooking.exception.AppointmentConflictException;
import com.ritvik.appointmentbooking.exception.AppointmentNotFoundException;
import com.ritvik.appointmentbooking.exception.InvalidAppointmentException;
import com.ritvik.appointmentbooking.repository.InMemoryAppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentServiceTest {
    private AppointmentService appointmentService;

    private static final String PROVIDER_A = "provider-101";
    private static final String PROVIDER_B = "provider-102";

    private static final Instant START = Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant END = Instant.parse("2026-10-30T11:00:00Z");

    @BeforeEach
    void setUp(){
        InMemoryAppointmentRepository repository = new InMemoryAppointmentRepository();

        appointmentService = new AppointmentService(repository);
    }

    //1. for successful booking
    @Test
    void shouldBookValidAppointment(){
        Appointment appointment = appointmentService.bookAppointment(PROVIDER_A, START, END);

        assertNotNull(appointment.getId());
        assertEquals(PROVIDER_A, appointment.getProviderId());
        assertEquals(START, appointment.getStartTime());
        assertEquals(END, appointment.getEndTime());
        assertEquals(
                AppointmentStatus.ACTIVE,
                appointment.getStatus()
        );
    }

    //2. same-provider overlapping booking must be rejected
    @Test
    void shouldRejectOverlappingAppointment() {
        appointmentService.bookAppointment(
                PROVIDER_A, START, END
        );

        Instant newStart = Instant.parse("2026-10-30T10:30:00Z");
        Instant newEnd = Instant.parse("2026-10-30T11:30:00Z");

        assertThrows(AppointmentConflictException.class,
                ()-> appointmentService.bookAppointment(PROVIDER_A, newStart, newEnd)
        );
    }

    //3. Appointment completely inside an existing interval
    @Test
    void shouldRejectContainedAppointment() {

        appointmentService.bookAppointment(PROVIDER_A, START, END);

        Instant newStart = Instant.parse("2026-10-30T10:15:00Z");
        Instant newEnd = Instant.parse("2026-10-30T10:45:00Z");

        assertThrows(
                AppointmentConflictException.class,
                () -> appointmentService.bookAppointment(
                        PROVIDER_A, newStart, newEnd
                )
        );

    }


    // 4. Adjacent appointments should be allowed
    @Test
    void shouldAllowAdjacentAppointments() {

        appointmentService.bookAppointment(
                PROVIDER_A, START, END
        );

        Appointment nextAppointment =
                appointmentService.bookAppointment(
                        PROVIDER_A,
                        END,
                        Instant.parse("2026-10-30T12:00:00Z")
                );

        assertNotNull(nextAppointment.getId());
        assertEquals(AppointmentStatus.ACTIVE,
                nextAppointment.getStatus());
    }


    // 5. Different providers should not conflict
    @Test
    void shouldAllowSameTimeForDifferentProviders() {

        appointmentService.bookAppointment(
                PROVIDER_A, START, END
        );

        Appointment secondAppointment =
                appointmentService.bookAppointment(
                        PROVIDER_B, START, END
                );

        assertEquals(PROVIDER_B, secondAppointment.getProviderId());
    }


    // 6. Cancellation should release the time slot
    @Test
    void shouldAllowRebookingAfterCancellation() {

        Appointment original =
                appointmentService.bookAppointment(
                        PROVIDER_A, START, END
                );

        Appointment cancelled =
                appointmentService.cancelAppointment(
                        original.getId()
                );

        assertEquals(
                AppointmentStatus.CANCELLED,
                cancelled.getStatus()
        );

        Appointment replacement =
                appointmentService.bookAppointment(
                        PROVIDER_A, START, END
                );

        assertNotEquals(original.getId(), replacement.getId());
        assertEquals(
                AppointmentStatus.ACTIVE,
                replacement.getStatus()
        );
    }

    // 7. Cancelling a missing appointment
    @Test
    void shouldRejectCancellationForMissingAppointment() {

        UUID unknownId = UUID.randomUUID();

        assertThrows(
                AppointmentNotFoundException.class,
                () -> appointmentService.cancelAppointment(unknownId)
        );
    }


    // 8. Cancelling an already-cancelled appointment
    @Test
    void shouldRejectRepeatedCancellation() {

        Appointment appointment =
                appointmentService.bookAppointment(
                        PROVIDER_A, START, END
                );

        appointmentService.cancelAppointment(appointment.getId());

        assertThrows(
                AppointmentAlreadyCancelledException.class,
                () -> appointmentService.cancelAppointment(
                        appointment.getId()
                )
        );
    }


    // 9. Invalid requests must be rejected
    @Test
    void shouldRejectInvalidBookingRequests() {

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        null, START, END
                )
        );

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        "   ", START, END
                )
        );

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        PROVIDER_A, null, END
                )
        );

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        PROVIDER_A, START, null
                )
        );

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        PROVIDER_A, START, START
                )
        );

        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.bookAppointment(
                        PROVIDER_A, END, START
                )
        );

        // Verify that rejected requests did not block valid bookings.
        assertNotNull(
                appointmentService.bookAppointment(
                        PROVIDER_A, START, END
                )
        );
    }

    // 10. Appointment ID is mandatory for cancellation
    @Test
    void shouldRejectNullAppointmentId() {
        assertThrows(
                InvalidAppointmentException.class,
                () -> appointmentService.cancelAppointment(null)
        );
    }


    // 11. Concurrent overlapping booking requests
    @Test
    void shouldAllowOnlyOneConcurrentBookingForSameProvider() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Boolean> firstBooking =
                    executor.submit(() ->
                            attemptBooking(ready, start)
                    );

            Future<Boolean> secondBooking =
                    executor.submit(() ->
                            attemptBooking(ready, start)
                    );

            // Wait until both threads are ready.
            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "Both threads should be ready"
            );

            // Release both threads at approximately the same time.
            start.countDown();

            boolean firstResult =
                    firstBooking.get(5, TimeUnit.SECONDS);

            boolean secondResult =
                    secondBooking.get(5, TimeUnit.SECONDS);

            int successfulBookings =
                    (firstResult ? 1 : 0) +
                            (secondResult ? 1 : 0);

            assertEquals(
                    1,
                    successfulBookings,
                    "Exactly one booking should succeed"
            );

            // A third overlapping attempt must also be rejected.
            assertThrows(
                    AppointmentConflictException.class,
                    () -> appointmentService.bookAppointment(
                            PROVIDER_A, START, END
                    )
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private boolean attemptBooking(
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {

        ready.countDown();

        assertTrue(
                start.await(5, TimeUnit.SECONDS),
                "Concurrent booking start timed out"
        );

        try {
            appointmentService.bookAppointment(
                    PROVIDER_A, START, END
            );

            return true;

        } catch (AppointmentConflictException exception) {

            return false;
        }
    }

}
