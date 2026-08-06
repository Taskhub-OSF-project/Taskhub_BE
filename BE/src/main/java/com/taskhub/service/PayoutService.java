package com.taskhub.service;

import com.taskhub.dto.PageRequestDto;
import com.taskhub.dto.PageResponse;
import com.taskhub.dto.request.CreatePayoutRequestDto;
import com.taskhub.dto.request.ResolvePayoutRequestDto;
import com.taskhub.dto.response.PayoutRequestResponse;
import com.taskhub.entity.PayoutRequest;
import com.taskhub.entity.User;
import com.taskhub.enums.NotificationType;
import com.taskhub.enums.PayoutStatus;
import com.taskhub.enums.WalletTransactionType;
import com.taskhub.exception.TaskHubException;
import com.taskhub.repository.PayoutRequestRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {
    private final PayoutRequestRepository payoutRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Transactional
    public PayoutRequestResponse createPayoutRequest(CreatePayoutRequestDto dto) {
        if (dto.getAmount() == null || dto.getAmount().compareTo(new BigDecimal("50000")) < 0) {
            throw TaskHubException.badRequest("Số tiền rút tối thiểu phải từ 50.000₫");
        }

        Long userId = AuthUtil.getCurrentUser().getId();
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> TaskHubException.notFound("User not found"));

        if (user.getWalletBalance().compareTo(dto.getAmount()) < 0) {
            throw TaskHubException.badRequest("Số dư khả dụng trong ví không đủ để thao tác rút tiền");
        }

        // 1. Tạm Khấu Trừ Số Dư Khả Dụng & Ghi Ledger (Withdrawal)
        user.setWalletBalance(user.getWalletBalance().subtract(dto.getAmount()));
        userRepository.save(user);
        walletService.recordTransaction(user, WalletTransactionType.withdrawal, dto.getAmount().negate(), null);

        // 2. Tạo Đơn Rút Tiền PENDING
        PayoutRequest request = PayoutRequest.builder()
                .user(user)
                .amount(dto.getAmount())
                .bankCode(dto.getBankCode().trim())
                .accountNumber(dto.getAccountNumber().trim().replaceAll("\\s+", ""))
                .accountName(dto.getAccountName().trim().toUpperCase())
                .status(PayoutStatus.PENDING)
                .build();

        payoutRepository.save(request);
        log.info("User {} created Payout Request #{}, amount={}", userId, request.getId(), dto.getAmount());

        String formattedAmount = formatVnd(dto.getAmount());
        // 3. Gửi thông báo cho User và Admin
        notificationService.notify(userId, NotificationType.PAYOUT_REQUEST_CREATED,
                "Yêu cầu rút tiền đang xử lý",
                "Bạn vừa tạo yêu cầu rút " + formattedAmount + " về ngân hàng " + dto.getBankCode() + " - STK: " + dto.getAccountNumber() + ". Hệ thống đang chuyển thông tin tới Admin xử lý.",
                user.getRole().name().equalsIgnoreCase("HIRER") ? "/hirer/wallet" : "/student/wallet",
                request.getId());

        notificationService.notifyAdmins(NotificationType.PAYOUT_REQUEST_CREATED,
                "Yêu cầu rút tiền mới #PR-" + request.getId(),
                "Thành viên " + user.getFullName() + " vừa gửi yêu cầu rút " + formattedAmount + " về STK " + dto.getAccountNumber() + " (" + dto.getBankCode() + ").",
                "/admin/payouts",
                request.getId());

        return PayoutRequestResponse.from(request);
    }

    @Transactional
    public PayoutRequestResponse resolvePayoutRequest(Long id, ResolvePayoutRequestDto dto) {
        PayoutRequest request = payoutRepository.findById(id)
                .orElseThrow(() -> TaskHubException.notFound("Không tìm thấy đơn yêu cầu rút tiền PR-" + id));

        if (request.getStatus() != PayoutStatus.PENDING) {
            throw TaskHubException.badRequest("Đơn rút tiền này đã được xử lý (trạng thái hiện tại: " + request.getStatus() + ")");
        }

        User admin = AuthUtil.getCurrentUser();
        User user = request.getUser();
        String formattedAmount = formatVnd(request.getAmount());
        String link = user.getRole().name().equalsIgnoreCase("HIRER") ? "/hirer/wallet" : "/student/wallet";

        if (Boolean.TRUE.equals(dto.getApproved())) {
            request.setStatus(PayoutStatus.COMPLETED);
            request.setAdminNote(dto.getNote() != null ? dto.getNote().trim() : "Admin đã phê duyệt và chuyển khoản");
            request.setProcessedBy(admin.getId());
            request.setProcessedAt(LocalDateTime.now());
            payoutRepository.save(request);

            log.info("Admin {} APPROVED Payout Request #PR-{}", admin.getId(), id);
            notificationService.notify(user.getId(), NotificationType.PAYOUT_APPROVED,
                    "Rút tiền thành công!",
                    "Yêu cầu rút " + formattedAmount + " (Mã PR-" + id + ") đã được Admin thao tác chuyển khoản tới ngân hàng " + request.getBankCode() + " của bạn thành công!",
                    link, request.getId());
        } else {
            // Trường hợp Từ Chối -> Tự động Hoàn Trả Tiền (+Balance) vào ví của User
            request.setStatus(PayoutStatus.REJECTED);
            String rejectReason = (dto.getNote() != null && !dto.getNote().isBlank())
                    ? dto.getNote().trim()
                    : "Thông tin tài khoản ngân hàng sai lệch hoặc không xác thực.";
            request.setAdminNote(rejectReason);
            request.setProcessedBy(admin.getId());
            request.setProcessedAt(LocalDateTime.now());
            payoutRepository.save(request);

            // Hoàn số dư cho user
            User targetUser = userRepository.findByIdForUpdate(user.getId())
                    .orElseThrow(() -> TaskHubException.notFound("User owner not found"));
            targetUser.setWalletBalance(targetUser.getWalletBalance().add(request.getAmount()));
            userRepository.save(targetUser);
            walletService.recordTransaction(targetUser, WalletTransactionType.refund, request.getAmount(), null);

            log.info("Admin {} REJECTED Payout Request #PR-{}, refunded {} to user {}", admin.getId(), id, request.getAmount(), user.getId());
            notificationService.notify(user.getId(), NotificationType.PAYOUT_REJECTED,
                    "Yêu cầu rút tiền bị từ chối - Đã hoàn tiền",
                    "Yêu cầu rút " + formattedAmount + " (Mã PR-" + id + ") bị từ chối. Lý do: " + rejectReason + ". Toàn bộ số tiền đã được tự động hoàn lại vào số dư ví của bạn.",
                    link, request.getId());
        }

        return PayoutRequestResponse.from(request);
    }

    public PageResponse<PayoutRequestResponse> getMyPayoutRequests(PageRequestDto req) {
        Long userId = AuthUtil.getCurrentUser().getId();
        Page<PayoutRequest> page = payoutRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(req.getPage(), Math.min(req.getSize(), 100))
        );
        List<PayoutRequestResponse> list = page.getContent().stream()
                .map(PayoutRequestResponse::from).toList();
        return PageResponse.of(list, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public PageResponse<PayoutRequestResponse> getAllPayoutRequests(String statusStr, PageRequestDto req) {
        PageRequest pageable = PageRequest.of(req.getPage(), Math.min(req.getSize(), 100));
        Page<PayoutRequest> page;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr)) {
            try {
                PayoutStatus status = PayoutStatus.valueOf(statusStr.toUpperCase().trim());
                page = payoutRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
            } catch (IllegalArgumentException e) {
                page = payoutRepository.findAllByOrderByCreatedAtDesc(pageable);
            }
        } else {
            page = payoutRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        List<PayoutRequestResponse> list = page.getContent().stream()
                .map(PayoutRequestResponse::from).toList();
        return PageResponse.of(list, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    private String formatVnd(BigDecimal val) {
        if (val == null) return "0₫";
        NumberFormat fmt = NumberFormat.getInstance(new Locale("vi", "VN"));
        return fmt.format(val) + "₫";
    }
}
