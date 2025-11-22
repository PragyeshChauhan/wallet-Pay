package com.ezpay.userservice.mapper;

import com.ezpay.userservice.domain.User;
import com.ezpay.userservice.dto.UserDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDTO toDTO(User user){
        if(user == null){
            return null;
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO,"password");
        return userDTO;
    }

    public User toDomain(UserDTO userDTO){
        if(userDTO == null){
            return null;
        }
        User user = new User();
        BeanUtils.copyProperties(userDTO,user);
        return user;
    }
}
