package com.bbss.auth.service;

import com.bbss.auth.config.JwtConfig;
import com.bbss.auth.domain.model.RefreshToken;
import com.bbss.auth.domain.model.Role;
import com.bbss.auth.domain.model.User;
import com.bbss.auth.domain.repository.RefreshTokenRepository;
import com.bbss.auth.domain.repository.RoleRepository;
import com.bbss.auth.domain.repository.UserRepository;
import com.bbss.auth.dto.AuthResponse;
import com.bbss.auth.dto.LoginRequest;
import com.bbss.auth.dto.RegisterRequest;
import com.bbss.auth.event.UserRegisteredEvent;
import com.bbss.auth.exception.AccountLockedException;
import com.bbss.auth.exception.InvalidTokenException;
import com.bbss.auth.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String AUTH_SESSION_KEY_PREFIX = "auth:session:";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 15;
    private static final long REDIS_SESSION_TTL_HOURS = 24;
    private static final String KAFKA_TOPIC_USER_REGISTERED = "auth.user.registered";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final AuthenticationManager authenticationManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {} for tenant: {}", request.email(), request.tenantId());

        if (userRepository.existsByEmailAndTenantId(request.email(), request.tenantId())) {
            throw new UserAlreadyExistsException(request.email(), request.tenantId());
        }

        Role adminRole = roleRepository.findByName(Role.ROLE_ADMIN)
            .orElseThrow(() -> new IllegalStateException("Default role ROLE_ADMIN not found"));

        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(request.firstName())
            .lastName(request.lastName())
            .tenantId(request.tenantId())
            .enabled(true)
            .accountNonLocked(true)
            .failedLoginAttempts(0)
            .roles(roles)
            .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        String accessToken = jwtService.generateAccessToken(savedUser, request.tenantId());
        String refreshTokenStr = jwtService.generateRefreshToken(savedUser, request.tenantId());

        saveRefreshToken(refreshTokenStr, savedUser, request.tenantId());

        List<String> userRoles = savedUser.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        UserRegisteredEvent event = UserRegisteredEvent.of(
            savedUser.getId(),
            savedUser.getEmail(),
            savedUser.getFirstName(),
            savedUser.getLastName(),
            request.tenantId(),
            userRoles
        );

        try {
            kafkaTemplate.send(KAFKA_TOPIC_USER_REGISTERED, savedUser.getId().toString(), event);
            log.info("Published UserRegisteredEvent for user: {}", savedUser.getId());
        } catch (Exception e) {
            log.error("Failed to publish UserRegisteredEvent for user: {}", savedUser.getId(), e);
        }

        return buildAuthResponse(accessToken, refreshTokenStr, savedUser, request.tenantId());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {} tenant: {}", request.email(), request.tenantId());

        User user = userRepository.findByEmailAndTenantId(request.email(), request.tenantId())
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isAccountNonLocked()) {
            LocalDateTime lockedUntil = user.getLockedUntil();
            if (lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil)) {
                throw new AccountLockedException(
                    "Account is locked until " + lockedUntil + ". Please try again later.");
            } else {
                user.setAccountNonLocked(true);
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (AuthenticationException e) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(user, request.tenantId());
        String refreshTokenStr = jwtService.generateRefreshToken(user, request.tenantId());

        saveRefreshToken(refreshTokenStr, user, request.tenantId());

        String redisKey = AUTH_SESSION_KEY_PREFIX + refreshTokenStr;
        redisTemplate.opsForValue().set(redisKey, user.getEmail(), REDIS_SESSION_TTL_HOURS, TimeUnit.HOURS);

        log.info("User logged in successfully: {}", user.getEmail());
        return buildAuthResponse(accessToken, refreshTokenStr, user, request.tenantId());
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenStr) {
        log.debug("Processing refresh token request");

        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshTokenStr)
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (storedToken.isRevoked()) {
            log.warn("Attempt to use revoked refresh token for user: {}", storedToken.getUser().getEmail());
            refreshTokenRepository.deleteAllByUser(storedToken.getUser());
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new InvalidTokenException("Refresh token has expired");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String redisKey = AUTH_SESSION_KEY_PREFIX + refreshTokenStr;
        redisTemplate.delete(redisKey);

        User user = storedToken.getUser();
        String tenantId = storedToken.getTenantId();

        String newAccessToken = jwtService.generateAccessToken(user, tenantId);
        String newRefreshTokenStr = jwtService.generateRefreshToken(user, tenantId);

        saveRefreshToken(newRefreshTokenStr, user, tenantId);

        String newRedisKey = AUTH_SESSION_KEY_PREFIX + newRefreshTokenStr;
        redisTemplate.opsForValue().set(newRedisKey, user.getEmail(), REDIS_SESSION_TTL_HOURS, TimeUnit.HOURS);

        log.info("Tokens refreshed successfully for user: {}", user.getEmail());
        return buildAuthResponse(newAccessToken, newRefreshTokenStr, user, tenantId);
    }

    @Transactional
    public void logout(String refreshTokenStr) {
        log.info("Processing logout request");

        refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            log.info("Refresh token revoked for user: {}", token.getUser().getEmail());
        });

        String redisKey = AUTH_SESSION_KEY_PREFIX + refreshTokenStr;
        redisTemplate.delete(redisKey);

        log.debug("Session removed from Redis cache");
    }

    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setAccountNonLocked(false);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_DURATION_MINUTES));
            log.warn("Account locked for user: {} after {} failed attempts", user.getEmail(), attempts);
        }

        userRepository.save(user);
    }

    private void saveRefreshToken(String tokenStr, User user, String tenantId) {
        long refreshExpirationMs = jwtConfig.getRefreshExpirationMs();
        LocalDateTime expiresAt = new Date(System.currentTimeMillis() + refreshExpirationMs)
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();

        RefreshToken refreshToken = RefreshToken.builder()
            .token(tokenStr)
            .user(user)
            .tenantId(tenantId)
            .expiresAt(expiresAt)
            .revoked(false)
            .build();

        refreshTokenRepository.save(refreshToken);
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshTokenStr,
                                            User user, String tenantId) {
        List<String> roles = user.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
            .id(user.getId())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .roles(roles)
            .build();

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshTokenStr)
            .tokenType("Bearer")
            .expiresIn(jwtConfig.getExpirationMs() / 1000)
            .tenantId(tenantId)
            .user(userInfo)
            .build();
    }
}
