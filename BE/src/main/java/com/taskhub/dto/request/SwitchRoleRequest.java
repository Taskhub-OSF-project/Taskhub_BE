package com.taskhub.dto.request;

import com.taskhub.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SwitchRoleRequest {
    @NotNull
    private Role role;
}
