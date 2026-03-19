package com.bbss.auth.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {

    private String eventId;
    private String eventType;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String tenantId;
    private List<String> roles;
    private LocalDateTime registeredAt;

    public static UserRegisteredEvent of(UUID userId, String email, String firstName,
                                          String lastName, String tenantId, List<String> roles) {
        return UserRegisteredEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("USER_REGISTERED")
            .userId(userId)
            .email(email)
            .firstName(firstName)
            .lastName(lastName)
            .tenantId(tenantId)
            .roles(roles)
            .registeredAt(LocalDateTime.now())
            .build();
    }
}
