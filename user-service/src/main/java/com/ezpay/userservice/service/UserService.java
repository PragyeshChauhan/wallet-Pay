package com.ezpay.userservice.service;

import com.ezpay.userservice.domain.User;
import com.ezpay.userservice.dto.UserDTO;
import com.ezpay.userservice.dto.records;

import java.util.Map;

public interface UserService {
    UserDTO register(UserDTO userDTO);
    UserDTO resetUserPassword(Map<String, String> resetPasswordObject);
    UserDTO updateUser(UserDTO userDTO);
    User login (records.LoginRequest request);
    void saveUserLastActivity(Map<String, String> resetPasswordObject);
}
