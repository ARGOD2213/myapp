package com.connectone.booking.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.connectone.booking.dto.BookingDtos.BookingResponse;
import com.connectone.booking.dto.BookingDtos.CreateRequest;
import com.connectone.booking.service.BookingService;
import com.connectone.common.config.ConnectOnePrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @GetMapping("/catalogue")
    public List<Map<String, Object>> catalogue(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location) {
        return service.catalogue(type, q, location);
    }

    @GetMapping("/catalogue/{itemId}/availability")
    public List<String> availability(@PathVariable long itemId) {
        return service.availability(itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(
            @AuthenticationPrincipal ConnectOnePrincipal principal,
            @Valid @RequestBody CreateRequest request) {
        return service.create(principal.userId(), request);
    }

    @GetMapping("/me")
    public List<BookingResponse> history(@AuthenticationPrincipal ConnectOnePrincipal principal) {
        return service.history(principal.userId());
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(
            @AuthenticationPrincipal ConnectOnePrincipal principal, @PathVariable long bookingId) {
        return service.cancel(principal.userId(), bookingId);
    }
}
