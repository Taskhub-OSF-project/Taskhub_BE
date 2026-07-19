package com.taskhub.service;

import com.taskhub.dto.request.EmailOtpVerifyRequest;
import com.taskhub.dto.request.LoginRequest;
import com.taskhub.dto.request.RegisterRequest;
import com.taskhub.dto.response.AuthResponse;
import com.taskhub.entity.EmailOtpChallenge;
import com.taskhub.entity.User;
import com.taskhub.enums.EmailOtpPurpose;
import com.taskhub.enums.Role;
import com.taskhub.repository.EmailOtpChallengeRepository;
import com.taskhub.repository.OtpTokenRepository;
import com.taskhub.repository.RefreshTokenRepository;
import com.taskhub.repository.UserRepository;
import com.taskhub.repository.VerificationTokenRepository;
import com.taskhub.security.JwtService;
import com.taskhub.service.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpAuthServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OtpTokenRepository otpTokenRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private EmailOtpChallengeRepository emailOtpChallengeRepository;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;
    @Mock private MailService mailService;
    @Mock private SmsService smsService;

    private AuthService authService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                refreshTokenRepository,
                otpTokenRepository,
                verificationTokenRepository,
                emailOtpChallengeRepository,
                passwordEncoder,
                jwtService,
                auditService,
                mailService,
                smsService);
        ReflectionTestUtils.setField(authService, "requireEmailVerification", true);
        ReflectionTestUtils.setField(authService, "requireLoginEmailOtp", true);
        when(mailService.isDeliveryEnabled()).thenReturn(true);
        when(emailOtpChallengeRepository.save(any(EmailOtpChallenge.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) user.setId(7L);
            return user;
        });
    }

    @Test
    void registrationOtp_verifiesEmailWithoutIssuingSession() {
        AuthResponse pending = authService.register(RegisterRequest.builder()
                .email("Student@Test.com")
                .password("strong-password")
                .fullName("Student Test")
                .role(Role.STUDENT)
                .build());

        assertTrue(pending.isEmailOtpRequired());
        assertTrue(pending.isVerificationRequired());
        assertEquals(EmailOtpPurpose.REGISTRATION.name(), pending.getOtpPurpose());
        assertNull(pending.getToken());

        ArgumentCaptor<EmailOtpChallenge> challengeCaptor = ArgumentCaptor.forClass(EmailOtpChallenge.class);
        verify(emailOtpChallengeRepository).save(challengeCaptor.capture());
        EmailOtpChallenge challenge = challengeCaptor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendRegistrationOtp(eq("student@test.com"), codeCaptor.capture());
        String code = codeCaptor.getValue();
        assertTrue(code.matches("\\d{6}"));
        assertTrue(passwordEncoder.matches(code, challenge.getCodeHash()));

        when(emailOtpChallengeRepository.findByChallengeId(challenge.getChallengeId()))
                .thenReturn(Optional.of(challenge));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .email("student@test.com")
                .password(passwordEncoder.encode("strong-password"))
                .fullName("Student Test")
                .role(Role.STUDENT)
                .emailVerified(false)
                .build()));

        AuthResponse verified = authService.verifyEmailOtp(EmailOtpVerifyRequest.builder()
                .challengeId(challenge.getChallengeId().toString())
                .code(code)
                .build());

        assertTrue(verified.isEmailVerified());
        assertNull(verified.getToken());
        assertNotNull(challenge.getUsedAt());
    }

    @Test
    void loginOtp_issuesSessionOnlyAfterCorrectCode() {
        User user = User.builder()
                .id(9L)
                .email("verified@test.com")
                .password(passwordEncoder.encode("strong-password"))
                .fullName("Verified User")
                .role(Role.HIRER)
                .emailVerified(true)
                .build();
        when(userRepository.findByEmailIgnoreCase("verified@test.com")).thenReturn(Optional.of(user));

        AuthResponse pending = authService.login(LoginRequest.builder()
                .email("verified@test.com")
                .password("strong-password")
                .build());

        assertTrue(pending.isEmailOtpRequired());
        assertFalse(pending.isVerificationRequired());
        assertEquals(EmailOtpPurpose.LOGIN.name(), pending.getOtpPurpose());
        assertNull(pending.getToken());
        verifyNoInteractions(refreshTokenRepository);

        ArgumentCaptor<EmailOtpChallenge> challengeCaptor = ArgumentCaptor.forClass(EmailOtpChallenge.class);
        verify(emailOtpChallengeRepository).save(challengeCaptor.capture());
        EmailOtpChallenge challenge = challengeCaptor.getValue();
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendLoginOtp(eq("verified@test.com"), codeCaptor.capture());

        when(emailOtpChallengeRepository.findByChallengeId(challenge.getChallengeId()))
                .thenReturn(Optional.of(challenge));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(9L, "verified@test.com", "HIRER"))
                .thenReturn("access-token");
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(604_800_000L);

        AuthResponse authenticated = authService.verifyEmailOtp(EmailOtpVerifyRequest.builder()
                .challengeId(challenge.getChallengeId().toString())
                .code(codeCaptor.getValue())
                .build());

        assertEquals("access-token", authenticated.getToken());
        assertNotNull(authenticated.getRefreshToken());
        verify(refreshTokenRepository).save(any());
    }
}
