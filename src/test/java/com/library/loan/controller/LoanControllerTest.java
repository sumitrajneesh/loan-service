package com.library.loan.controller;

import com.library.loan.model.Loan;
import com.library.loan.repository.LoanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean // Mock WebClient.Builder as it's a @Bean
    private WebClient.Builder webClientBuilder;

    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;

    @Autowired
    private ObjectMapper objectMapper;

    private Loan loan1;
    private Loan loan2;

    @BeforeEach
    void setUp() {
        loan1 = new Loan(1L, 101L, 201L, LocalDateTime.now(), null, Loan.LoanStatus.BORROWED);
        loan2 = new Loan(2L, 102L, 202L, LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3), Loan.LoanStatus.RETURNED);

        // Mock WebClient behavior
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(webClient.put()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object.class), any(Object.class))).thenReturn(requestHeadersSpec); // For put with params
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void testGetAllLoans() throws Exception {
        when(loanRepository.findAll()).thenReturn(Arrays.asList(loan1, loan2));

        mockMvc.perform(get("/api/loans"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].bookId").value(101L))
                .andExpect(jsonPath("$[1].userId").value(202L));
    }

    @Test
    void testGetLoanByIdFound() throws Exception {
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan1));

        mockMvc.perform(get("/api/loans/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.bookId").value(101L));
    }

    @Test
    void testGetLoanByIdNotFound() throws Exception {
        when(loanRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/loans/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testBorrowBookSuccess() throws Exception {
        // Mock Book Service response for availability
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Collections.singletonMap("availableQuantity", 1))); // Book is available
        // Mock User Service response for existence
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Collections.singletonMap("id", 201L))); // User exists
        // Mock Book Service response for update (decrement)
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        when(loanRepository.findByBookIdAndUserIdAndStatus(anyLong(), anyLong(), any(Loan.LoanStatus.class)))
                .thenReturn(Optional.empty()); // No existing loan
        when(loanRepository.save(any(Loan.class))).thenReturn(loan1);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "borrow");
        payload.put("bookId", 101L);
        payload.put("userId", 201L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(101L))
                .andExpect(jsonPath("$.userId").value(201L))
                .andExpect(jsonPath("$.status").value("BORROWED"));

        verify(webClient, times(2)).get(); // One for book, one for user
        verify(webClient, times(1)).put(); // One for updating book quantity
        verify(loanRepository, times(1)).save(any(Loan.class));
    }

    @Test
    void testBorrowBookNotAvailable() throws Exception {
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Collections.singletonMap("availableQuantity", 0))); // Book not available

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "borrow");
        payload.put("bookId", 101L);
        payload.put("userId", 201L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Book not found or not available."));

        verify(webClient, times(1)).get(); // Only book check
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void testBorrowBookUserNotFound() throws Exception {
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Collections.singletonMap("availableQuantity", 1))) // Book is available
                .thenReturn(Mono.error(new WebClientResponseException(HttpStatus.NOT_FOUND.value(), "Not Found", null, null, null))); // User not found

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "borrow");
        payload.put("bookId", 101L);
        payload.put("userId", 201L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User not found."));

        verify(webClient, times(2)).get(); // Book and User check
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void testBorrowBookAlreadyBorrowed() throws Exception {
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(Collections.singletonMap("availableQuantity", 1))) // Book is available
                .thenReturn(Mono.just(Collections.singletonMap("id", 201L))); // User exists

        when(loanRepository.findByBookIdAndUserIdAndStatus(anyLong(), anyLong(), any(Loan.LoanStatus.class)))
                .thenReturn(Optional.of(loan1)); // Existing loan found

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "borrow");
        payload.put("bookId", 101L);
        payload.put("userId", 201L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(content().string("User already has this book borrowed."));

        verify(webClient, times(2)).get(); // Book and User check
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void testReturnBookSuccess() throws Exception {
        when(loanRepository.findById(1L)).thenReturn(Optional.of(loan1));
        when(loanRepository.save(any(Loan.class))).thenReturn(loan1); // Mock saving the updated loan
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty()); // Mock book service update

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "return");
        payload.put("loanId", 1L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        verify(loanRepository, times(1)).save(any(Loan.class));
        verify(webClient, times(1)).put();
    }

    @Test
    void testReturnBookNotFound() throws Exception {
        when(loanRepository.findById(99L)).thenReturn(Optional.empty());

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "return");
        payload.put("loanId", 99L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Loan record not found."));

        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void testReturnBookAlreadyReturned() throws Exception {
        when(loanRepository.findById(2L)).thenReturn(Optional.of(loan2)); // loan2 is already RETURNED

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "return");
        payload.put("loanId", 2L);

        mockMvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Book already returned."));

        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/loans/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }
}
