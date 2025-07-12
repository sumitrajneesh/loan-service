package com.library.loan.controller;

import com.library.loan.model.Loan;
import com.library.loan.repository.LoanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean; // Keep this import
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoanController.class)
public class LoanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LoanRepository loanRepository;

    // CHANGE THIS: Mock WebClient directly, not WebClient.Builder
    @MockBean
    private WebClient webClient; // Now we mock the WebClient instance itself

    // Remove these fields, they are no longer needed as instance variables for mocking
    // private WebClient webClient; // REMOVE THIS
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    private WebClient.RequestBodySpec requestBodySpec;

    @Autowired
    private ObjectMapper objectMapper;

    private Loan loan1;
    private Loan loan2;

    @BeforeEach
    void setUp() {
        loan1 = new Loan(1L, 101L, 201L, LocalDateTime.now(), null, Loan.LoanStatus.BORROWED);
        loan2 = new Loan(2L, 102L, 202L, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3), Loan.LoanStatus.RETURNED);

        // Initialize mock chain components
        // webClient is now a @MockBean, so it's already a mock. No need to mock(WebClient.class)
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);

        // Mock GET requests
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object.class))).thenReturn(requestHeadersSpec); // For GET with 1 param (bookId or userId)
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // Mock PUT requests
        when(webClient.put()).thenReturn(requestBodyUriSpec); // Now returns RequestBodyUriSpec
        when(requestBodyUriSpec.uri(anyString(), any(Object.class))).thenReturn(requestBodySpec); // Chain to RequestBodySpec
        when(requestBodySpec.body(any(Mono.class), any(Class.class))).thenReturn(requestHeadersSpec); // Mock the body method
        when(requestBodySpec.retrieve()).thenReturn(responseSpec); // For when no body is specified but it's a PUT
    }

    // ... (rest of your test methods remain the same)
    // No changes needed in test methods themselves, as the mocking chain is now correct.
}