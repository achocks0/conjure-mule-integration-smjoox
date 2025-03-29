package com.payment.eapi.model;

import java.io.Serializable; // JDK 11
import java.util.Date; // JDK 11

import lombok.Data; // 1.18.20
import lombok.Builder; // 1.18.20
import lombok.NoArgsConstructor; // 1.18.20
import lombok.AllArgsConstructor; // 1.18.20
import com.fasterxml.jackson.annotation.JsonProperty; // 2.13.0

/**
 * Model class representing the authentication response containing JWT token and expiration information.
 * Used for serializing responses to vendor authentication requests in the Payment API Security Enhancement project.
 * This class supports the backward compatibility requirement while implementing the enhanced token-based security.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse implements Serializable {

    @JsonProperty("token")
    private String token;
    
    @JsonProperty("expires_at")
    private Date expiresAt;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    /**
     * Static factory method to create an AuthenticationResponse from a Token object.
     *
     * @param token The Token object containing token string and expiration time
     * @return Authentication response containing token string, expiration time, and token type
     */
    public static AuthenticationResponse fromToken(Token token) {
        return AuthenticationResponse.builder()
                .token(token.getTokenString())
                .expiresAt(Date.from(token.getExpiration()))
                .tokenType("Bearer")
                .build();
    }
}