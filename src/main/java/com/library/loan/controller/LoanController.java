package com.library.loan.controller;

import com.library.loan.model.Loan;
import com.library.loan.repository.LoanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/loans")
@CrossOrigin(origins = "*") // Allow all origins for simplicity in demo, restrict in production
public class LoanController {

    @Autowired
    private LoanRepository loanRepository;

    private final WebClient webClient;

    @Value("${book.service.url}")
    private String bookServiceUrl;

    @Value("${user.service.url}")
    private String userServiceUrl;

    public LoanController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @GetMapping
    public List<Loan> getAllLoans() {
        return loanRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Loan> getLoanById(@PathVariable Long id) {
        Optional<Loan> loan = loanRepository.findById(id);
        return loan.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> handleLoanAction(@RequestBody Map<String, Object> payload) {
        String type = (String) payload.get("type");

        if ("borrow".equals(type)) {
            Long bookId = ((Number) payload.get("bookId")).longValue();
            Long userId = ((Number) payload.get("userId")).longValue();
            return borrowBook(bookId, userId);
        } else if ("return".equals(type)) {
            Long loanId = ((Number) payload.get("loanId")).longValue();
            return returnBook(loanId);
        } else {
            return new ResponseEntity<>("Invalid loan action type", HttpStatus.BAD_REQUEST);
        }
    }

    private ResponseEntity<?> borrowBook(Long bookId, Long userId) {
        // 1. Validate Book existence and availability
        Boolean bookAvailable = webClient.get()
                .uri(bookServiceUrl + "/{id}", bookId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(book -> (Integer) book.get("availableQuantity") > 0)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(false)) // Book not found
                .block(); // Blocking for simplicity, consider reactive flow

        if (bookAvailable == null || !bookAvailable) {
            return new ResponseEntity<>("Book not found or not available.", HttpStatus.BAD_REQUEST);
        }

        // 2. Validate User existence
        Boolean userExists = webClient.get()
                .uri(userServiceUrl + "/{id}", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(user -> true)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(false)) // User not found
                .block(); // Blocking for simplicity

        if (userExists == null || !userExists) {
            return new ResponseEntity<>("User not found.", HttpStatus.BAD_REQUEST);
        }

        // 3. Check if user already borrowed this specific book and hasn't returned it
        Optional<Loan> existingLoan = loanRepository.findByBookIdAndUserIdAndStatus(bookId, userId, Loan.LoanStatus.BORROWED);
        if (existingLoan.isPresent()) {
            return new ResponseEntity<>("User already has this book borrowed.", HttpStatus.CONFLICT);
        }

        // 4. Create Loan record
        Loan newLoan = new Loan(bookId, userId);
        Loan savedLoan = loanRepository.save(newLoan);

        // 5. Update Book availability (decrement)
        webClient.put()
                .uri(bookServiceUrl + "/{id}/available?change=-1", bookId)
                .retrieve()
                .toBodilessEntity()
                .block(); // Blocking for simplicity

        return new ResponseEntity<>(savedLoan, HttpStatus.CREATED);
    }

    private ResponseEntity<?> returnBook(Long loanId) {
        Optional<Loan> loanOptional = loanRepository.findById(loanId);
        if (loanOptional.isEmpty()) {
            return new ResponseEntity<>("Loan record not found.", HttpStatus.NOT_FOUND);
        }

        Loan loan = loanOptional.get();
        if (loan.getStatus() == Loan.LoanStatus.RETURNED) {
            return new ResponseEntity<>("Book already returned.", HttpStatus.BAD_REQUEST);
        }

        // 1. Update Loan record
        loan.setReturnDate(LocalDateTime.now());
        loan.setStatus(Loan.LoanStatus.RETURNED);
        Loan updatedLoan = loanRepository.save(loan);

        // 2. Update Book availability (increment)
        webClient.put()
                .uri(bookServiceUrl + "/{id}/available?change=1", loan.getBookId())
                .retrieve()
                .toBodilessEntity()
                .block(); // Blocking for simplicity

        return ResponseEntity.ok(updatedLoan);
    }

    // Basic health check endpoint
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("OK");
    }
}
