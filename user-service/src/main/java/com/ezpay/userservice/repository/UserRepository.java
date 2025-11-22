package com.ezpay.userservice.repository;

import com.ezpay.userservice.domain.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobileNumberAndTemporaryUser(String phone,boolean temporaryUser);
    Optional<User> findByMobileNumber(String phone);


    @Modifying
    @Transactional
    @Query("""
    UPDATE User u
    SET u.password = :password, u.lastActivity = :activity
    WHERE u.mobileNumber = :mobileNumber
""")
    int updatePasswordAndActivityByPhoneNumber(
            @Param("password") String password,
            @Param("activity") String activity,
            @Param("mobileNumber") String mobileNumber
    );

    @Modifying
    @Transactional
    @Query("""
    UPDATE User u
    SET u.lastActivity = :activity
    WHERE u.mobileNumber = :mobileNumber
""")
    int updateActivityByPhoneNumber(
            @Param("activity") String activity,
            @Param("mobileNumber") String mobileNumber
    );

    @Modifying
    @Transactional
    @Query(value = """
    INSERT INTO users (mobile_number, user_name, temporary_user, is_verified)
    VALUES (:phoneNumber, :userName, :temporaryUser, false)
    ON CONFLICT (mobile_number) DO NOTHING
    """, nativeQuery = true)
    int insertIfNotExists(@Param("phoneNumber") String phoneNumber,
                          @Param("userName") String userName,
                          @Param("temporaryUser") boolean temporaryUser);

    User findByPersonaInquiryId(String inquiryId);
    Optional<User> findByUserName(String userName);
}