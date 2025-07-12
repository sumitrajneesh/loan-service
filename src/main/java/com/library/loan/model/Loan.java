package com.library.loan.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Loan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long bookId;
    private Long userId;
    private LocalDateTime loanDate;
    private LocalDateTime returnDate; // Null if not returned
    private LoanStatus status; // ENUM: BORROWED, RETURNED

    public enum LoanStatus {
        BORROWED, RETURNED
    }

    public Loan(Long bookId, Long userId) {
        this.bookId = bookId;
        this.userId = userId;
        this.loanDate = LocalDateTime.now();
        this.status = LoanStatus.BORROWED;
    }
}