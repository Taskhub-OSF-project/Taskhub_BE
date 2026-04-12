package com.taskhub.security;

import com.taskhub.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtil {
    public static User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
