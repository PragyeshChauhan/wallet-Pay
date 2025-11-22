package com.ezpay.wallet.auth_service.repository;


import com.ezpay.wallet.auth_service.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    List<RefreshToken> findByDeviceIdAndUserId(String deviceId, Long userId);

    List<RefreshToken> findByDeviceId(String deviceId);
}