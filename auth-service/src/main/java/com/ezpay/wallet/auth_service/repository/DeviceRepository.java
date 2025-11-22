package com.ezpay.wallet.auth_service.repository;

import com.ezpay.wallet.auth_service.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByDeviceIdAndUserId(String deviceId, Long userId);
    Optional<Device> findByDeviceId(String deviceId);
}
