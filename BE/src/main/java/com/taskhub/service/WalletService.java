package com.taskhub.service;

import com.taskhub.dto.response.WalletResponse;
import com.taskhub.entity.User;
import com.taskhub.repository.UserRepository;
import com.taskhub.security.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class WalletService {
    private final UserRepository userRepository;

    public WalletResponse getBalance() {
        return new WalletResponse(AuthUtil.getCurrentUser().getWalletBalance());
    }

    @Transactional
    public WalletResponse deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        User user = AuthUtil.getCurrentUser();
        user.setWalletBalance(user.getWalletBalance().add(amount));
        userRepository.save(user);
        return new WalletResponse(user.getWalletBalance());
    }
}
