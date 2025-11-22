package com.ezpay.userservice.serviceImpl;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.ezpay.userservice.constants.UserServiceConstants;
import com.ezpay.userservice.domain.User;
import com.ezpay.userservice.dto.UserEvent;
import com.ezpay.userservice.dto.UserDTO;
import com.ezpay.userservice.dto.records;
import com.ezpay.userservice.mapper.UserMapper;
import com.ezpay.userservice.repository.UserRepository;
import com.ezpay.userservice.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private WebClient webClient;

    @Autowired
    private EmailEventProducer emailEventProducer;



    @Override
    public UserDTO register(UserDTO userDTO) {
        log.info("Inside register()");
        validateUserInput(userDTO);
        Optional<User> existingUser = userRepository.findByMobileNumberAndTemporaryUser(userDTO.getMobileNumber(), false);
        if (existingUser.isPresent() && !existingUser.get().isTemporaryUser()) {
            log.warn("User already exists with phone: {}", userDTO.getMobileNumber());
            throw new RuntimeException(UserServiceConstants.USER_ALREADY_EXISTS);
        }
        if(existingUser.isPresent() && existingUser.get().getUserName()==null){
            userDTO.setUserName(NanoIdUtils.randomNanoId());
        }
        if (userDTO.getPassword() != null) {
            userDTO.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        User user = userMapper.toDomain(userDTO);
        user.setTemporaryUser(false);
        try {
            user = userRepository.save(user);
            sendRegistrationEmail(user.getEmail(), user.getFirstName() + " " + user.getLastName(), "registration-email");
            return userMapper.toDTO(user);
        } catch (Exception ex) {
            log.error("Error occurred while registering user: {}", ex.getMessage(), ex);
            throw new RuntimeException("Registration failed. Please try again.");
        }
    }

    /**
     * method to validate email and phoenumber
     * @param userDTO
     */
    private void validateUserInput(UserDTO userDTO) {
        if (!isValidEmail(userDTO.getEmail()) || !isValidMobileNumber(userDTO.getMobileNumber())) {
            throw new IllegalArgumentException(UserServiceConstants.INVALID_EMAIL_OR_MOBILENUMEBR);
        }
    }

    @Override
    public User login(records.LoginRequest request) {

        Optional<User> user =  userRepository.findByMobileNumber(request.mobile());
        return user.orElse(createTempUser(request));
    }

    private User createTempUser(records.LoginRequest request) {
        int rowsInserted = userRepository.insertIfNotExists(
                request.mobile(),NanoIdUtils.randomNanoId(),
                true // temporaryUser
        );

        if (rowsInserted == 0) {
            // User already exists, fetch it
            return userRepository.findByMobileNumberAndTemporaryUser(request.mobile(), true)
                    .orElseThrow(() -> new IllegalStateException("Temporary user exists but could not be retrieved"));
        }

        // New user inserted, fetch it
        return userRepository.findByMobileNumberAndTemporaryUser(request.mobile(), true)
                .orElseThrow(() -> new IllegalStateException("Temporary user was inserted but could not be retrieved"));
    }


    /**
     * method to update user
     * @param userDTO
     * @return
     */
    public UserDTO updateUser(UserDTO userDTO) {
        log.info("Inside updateUser()");
        Map<String, String> tokens = new HashMap<>();
        try {
            Optional<User> existing = userRepository.findByMobileNumberAndTemporaryUser(userDTO.getMobileNumber(), true);
            if (existing.isEmpty()) {
                throw new RuntimeException("user Not found");
            }
            User user = userMapper.toDomain(userDTO);
            user = userRepository.save(user);
//            sendRegistrationEmail(userDTO.getEmail(),userDTO.getFirstName()+""+userDTO.getLastName(),"User_Update");
            return userMapper.toDTO(user) ;
        } catch(Exception e) {
            log.error("Error while calling updateUser()");
            e.printStackTrace();
        }
        return  userDTO;
    }

    /**
     * method to generate username using email and phone number
     * @param email
     * @param phone
     * @return
     */
    public String generateUsername(String email, String phone) {
        log.info("Inside generateUsername()");

        String emailPart = "";

        if (email != null && !email.isBlank()) {
            String emailPrefix = email.split("@")[0]
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .toLowerCase();
            emailPart = emailPrefix.length() >= 4
                    ? emailPrefix.substring(0, 4)
                    : emailPrefix;
        } else {
            // If email is missing, use first 4 digits of phone instead
            emailPart = phone.length() >= 4
                    ? phone.substring(0, 4)
                    : phone;
        }

        // Last 4 digits of phone number
        String phonePart = phone.length() >= 4
                ? phone.substring(phone.length() - 4)
                : phone;

        return emailPart + UserServiceConstants.EZPAY + phonePart;
    }


    public  boolean isValidMobileNumber(String mobile) {
        if (mobile == null) return false;

        // Remove any leading + sign or spaces
        mobile = mobile.trim().replaceAll("\\s+", "");

        // India: 10 digits, starts with 6-9
        if (mobile.matches("^(\\+91)?[6-9]\\d{9}$")) {
            return true;
        }

        // Canada: 10 digits, optionally +1 prefix
        if (mobile.matches("^(\\+1)?[2-9]\\d{9}$")) {
            return true;
        }

        return false;
    }

    public  boolean isValidEmail(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        return email != null && email.matches(regex);
    }

    public boolean isPasswordValid(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /**
     * method to consume registration email notification
     * @param to
     * @param name
     * @param eventType
     * @throws JsonProcessingException
     */
    public void sendRegistrationEmail(String to, String name,String eventType) throws JsonProcessingException {
        UserEvent userEvent = new UserEvent();
        userEvent.setEmail(to);
        userEvent.setName(name);
        userEvent.setEventType(eventType);
        userEvent.setUserId(null);
        emailEventProducer.sendEmailEvent(userEvent);
    }

    /**
     * this method need mobile number,new password,old Password in payload to reset the password
     * @param resetPasswordObject
     * @return
     */
    @Override
    public UserDTO resetUserPassword(Map<String, String> resetPasswordObject) {
        log.info("Inside resetUserPassword()");
        try{
            if(resetPasswordObject == null || resetPasswordObject.get("oldPassword")==null || resetPasswordObject.get("newPassword")==null){
                throw new IllegalArgumentException("Password Field Must Not Be Empty!");
            }
            Optional<User> existing = userRepository.findByMobileNumberAndTemporaryUser(resetPasswordObject.get("MobileNumber"), false);
            if(existing.isPresent()){
                User user = existing.get();
                isPasswordValid(resetPasswordObject.get("oldPassword"), user.getPassword());
                String newPassword  = resetPasswordObject.get("newPassword");
                user.setPassword(passwordEncoder.encode(newPassword));
                user = userRepository.save(user);
                UserDTO  userDTO =  userMapper.toDTO(user);
                sendRegistrationEmail(userDTO.getEmail(),userDTO.getFirstName()+""+userDTO.getLastName(),"Reset_password");
                return userDTO;
            }else {
                throw new RuntimeException("User Not found");
            }
        }catch (Exception e){
            log.error("Error while calling resetUserPassword()");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * this method needs mobileNumber, last activity any other fields based on operation
     * @param userLastActivty
     * @return
     */
    @Override
    public void saveUserLastActivity(Map<String, String> userLastActivty) {
        log.info("Inside saveUserLastActivity()");
        try{
            String mobileNumber = userLastActivty.get("mobileNumber");
            String pin = userLastActivty.get("pin");
            String lastActivity = userLastActivty.get("lastActivity");
            if (mobileNumber == null || lastActivity == null) {
                throw new IllegalArgumentException("Missing required fields");
            }

            if(pin != null && "PIN SETUP".equalsIgnoreCase(lastActivity) ){
                String encodedPin = passwordEncoder.encode(pin);
                int updated = userRepository.updatePasswordAndActivityByPhoneNumber(encodedPin, lastActivity, mobileNumber);
                if (updated == 0) {
                    throw new RuntimeException("User not found for update");
                }
            } else {
                int updated = userRepository.updateActivityByPhoneNumber(lastActivity, mobileNumber);
                if (updated == 0) {
                    throw new RuntimeException("Last Activity Not Updated");
                }
            }
        }catch (Exception e){
            log.error("Error while calling saveUserLastActivity()");
            e.printStackTrace();
        }
    }
}

