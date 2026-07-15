package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service xử lý ví và lịch sử giao dịch.
 * Thuộc module Wallet, được gọi từ WalletController và các service khác.
 */
@Service
@RequiredArgsConstructor
public class WalletService {
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Value("${app.wallet.cash-simulation-enabled:false}")
    private boolean cashSimulationEnabled;

    /**
     * Lấy số dư ví của user hiện tại.
     */
    public WalletResponse getBalance() {
        Long userId = AuthUtil.getCurrentUser().getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        return new WalletResponse(user.getWalletBalance());
    }

    /**
     * Kiểm tra ví có đủ budget + 5% fee trước khi tạo task.
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

    /**
     * Bắt lỗi sớm nếu ví không đủ để tạo task.
     */
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

    public PageResponse<WalletTransactionResponse> getTransactionsPaged(PageRequestDto pageReq) {
        Long userId = AuthUtil.getCurrentUser().getId();
        var page = walletTransactionRepository.findByUserId(userId,
                org.springframework.data.domain.PageRequest.of(pageReq.getPage(), Math.min(pageReq.getSize(), 100),
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
        return PageResponse.<WalletTransactionResponse>builder()
                .content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize())
                .totalElements(page.getTotalElements()).totalPages(page.getTotalPages())
                .first(page.isFirst()).last(page.isLast())
                .hasNext(page.hasNext()).hasPrevious(page.hasPrevious())
                .build();
    }

    /**
     * Nạp tiền vào ví người dùng hiện tại.
     */
    @Transactional
    public WalletResponse deposit(BigDecimal amount) {
        if (!cashSimulationEnabled)
            throw TaskHubException.forbidden("Direct wallet deposits are disabled; use a payment provider");
        if (amount == null)
            throw TaskHubException.badRequest("Amount is required");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw TaskHubException.badRequest("Amount must be positive");
        User user = userRepository.findByIdForUpdate(AuthUtil.getCurrentUser().getId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);
        recordTransaction(user, WalletTransactionType.top_up, amount, null);
        return new WalletResponse(user.getWalletBalance());
    }

    /**
     * Rút tiền từ ví người dùng hiện tại.
     */
    @Transactional
    public WalletResponse withdraw(BigDecimal amount) {
        if (!cashSimulationEnabled)
            throw TaskHubException.forbidden("Direct wallet withdrawals are disabled; use a payment provider");
        if (amount == null)
            throw TaskHubException.badRequest("Amount is required");
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw TaskHubException.badRequest("Amount must be positive");
        User user = userRepository.findByIdForUpdate(AuthUtil.getCurrentUser().getId())
                .orElseThrow(() -> TaskHubException.notFound("User not found"));
        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw TaskHubException.badRequest("Số dư ví không đủ để rút");
        }
        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        userRepository.save(user);
        recordTransaction(user, WalletTransactionType.withdrawal, amount.negate(), null);
        return new WalletResponse(user.getWalletBalance());
    }

    /**
     * Ghi một dòng ledger cho giao dịch ví.
     */
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
        // Mapping entity -> DTO để trả cho client.
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
