package com.fieldforcepro.dto.auth;



import com.fieldforcepro.model.UserRole;



public record UserDto(

        Long id,

        String email,

        String name,

        UserRole role,

        boolean isActive

) {

}

