package com.ritvik.appointmentbooking.controller;

import com.ritvik.appointmentbooking.dto.AppointmentResponse;
import com.ritvik.appointmentbooking.dto.BookAppointmentRequest;
import com.ritvik.appointmentbooking.model.Appointment;
import com.ritvik.appointmentbooking.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public  AppointmentController(AppointmentService appointmentService){
        this.appointmentService = appointmentService;
    }

    //create new appointment end-point
    @PostMapping
    public ResponseEntity<AppointmentResponse> bookAppointment(@Valid @RequestBody BookAppointmentRequest request){

        Appointment appointment = appointmentService.bookAppointment(
                request.providerId(),
                request.startTime(),
                request.endTime()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AppointmentResponse.from(appointment));
    }


    //cancel existing appointment end-point
    @PostMapping("/{appointmentId}/cancel")
    public ResponseEntity<AppointmentResponse> cancelAppointment(@PathVariable UUID appointmentId){
        Appointment appointment = appointmentService.cancelAppointment(appointmentId);

        return ResponseEntity.ok(AppointmentResponse.from(appointment));
    }

}
