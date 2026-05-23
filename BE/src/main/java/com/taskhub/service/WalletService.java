package com.taskhub.service;

import com.taskhub.dto.response.WalletReadinessResponse;
import com.taskhub.dto.response.WalletResponse;
import com.taskhub.dto.response.WalletTransactionResponse;
import com.taskhub.exception.TaskHubException;
import com.taskhub.entity.Task;
import com.taskhub.entity.User;
import com.taskhub.entity.WalletTransaction;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.WalletTransactionRepository;
import com.taskhub.security.AuthUtil;
import com.taskhub.util.EscrowCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public WalletResponse getBalance() {
        return new WalletResponse(AuthUtil.getCurrentUser().getWalletBalance());
    }

    /**
     * Check if hirer wallet can cover escrow (budget + 5% platform fee) before creating a task.
     */
    public WalletReadinessResponse assessCreateTaskReadiness(BigDecimal budget) {
        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0)
            throw TaskHubException.badRequest("Budget must be positive");

        User user = AuthUtil.getCurrentUser();
        BigDecimal fee = EscrowCalculator.platformFee(budget);
        BigDecimal required = EscrowCalculator.totalEscrowDeduction(budget);
        BigDecimal balance = user.getWalletBalance();
        boolean sufficient = balance.compareTo(required) >= 0;
        BigDecimal shortfall = sufficient ? BigDecimal.ZERO : required.subtract(balance);

        return WalletReadinessResponse.builder()
                .sufficient(sufficient)
                .budget(budget)
                .platformFee(fee)
                .requiredTotal(required)
                .currentBalance(balance)
                .shortfall(shortfall)
                .action(sufficient ? null : "TOP_UP")
                .resumeFlow(sufficient ? null : "CREATE_TASK")
                .build();
    }

    public void requireSufficientForCreateTask(BigDecimal budget) {
        WalletReadinessResponse readiness = assessCreateTaskReadiness(budget);
        if (!readiness.isSufficient()) {
            throw TaskHubException.insufficientWallet(
                    "So du vi khong du de tao cong viec. Can nap them " + readiness.getShortfall()
                            + " VND (ngan sach + phi 5%) truoc khi tiep tuc.",
                    readiness);
        }
    }

    public List<WalletTransactionResponse> getTransactions() {
        return walletTransactionRepository.findByUserIdOrderByCreatedAtDesc(AuthUtil.getCurrentUser().getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WalletResponse deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        User user = AuthUtil.getCurrentUser();
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);
        recordTransaction(user, WalletTransactionType.top_up, amount, null);
        return new WalletResponse(user.getWalletBalance());
    }

    public void recordTransaction(User user, WalletTransactionType type, BigDecimal amount, Task task) {
        walletTransactionRepository.save(WalletTransaction.builder()
                .user(user)
                .task(task)
                .type(type)
                .amount(amount)
                .balanceAfter(user.getWalletBalance())
                .build());
    }

    private WalletTransactionResponse toResponse(WalletTransaction transaction) {
        return WalletTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .taskId(transaction.getTask() != null ? transaction.getTask().getId() : null)
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
