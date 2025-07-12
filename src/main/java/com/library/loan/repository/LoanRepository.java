package com.library.loan.repository;

import com.library.loan.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    Optional<Loan> findByBookIdAndUserIdAndStatus(Long bookId, Long userId, Loan.LoanStatus status);
}
