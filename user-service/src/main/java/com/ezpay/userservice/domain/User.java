package com.ezpay.userservice.domain;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "firstName cannot be empty")
    private String firstName;

    @NotNull(message = "LastName cannot be empty")
    private String lastName;

    @NotNull(message = "email cannot be empty")
    @Column(unique = true)
    private String email;

    @Column(unique = true)
    private String mobileNumber;

    private boolean isVerified;

    private String userName;

    private String password;

    private boolean temporaryUser;

    private String lastActivity;

    private String gender;

    private String dateOfBirth;

    private String personaInquiryId;

    private String verificationStatus = "PENDING";

    private List<String> roles = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public @NotNull(message = "firstName cannot be empty") String getFirstName() {
        return firstName;
    }

    public void setFirstName(@NotNull(message = "firstName cannot be empty") String firstName) {
        this.firstName = firstName;
    }

    public @NotNull(message = "LastName cannot be empty") String getLastName() {
        return lastName;
    }

    public void setLastName(@NotNull(message = "LastName cannot be empty") String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isTemporaryUser() {
        return temporaryUser;
    }

    public void setTemporaryUser(boolean temporaryUser) {
        this.temporaryUser = temporaryUser;
    }

    public String getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(String lastActivity) {
        this.lastActivity = lastActivity;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getPersonaInquiryId() {
        return personaInquiryId;
    }

    public void setPersonaInquiryId(String personaInquiryId) {
        this.personaInquiryId = personaInquiryId;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
