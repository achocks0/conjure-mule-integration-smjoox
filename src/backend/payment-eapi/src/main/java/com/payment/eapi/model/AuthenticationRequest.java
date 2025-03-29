package com.payment.eapi.model;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class representing the authentication request containing 
 * Client ID and Client Secret credentials. Used for deserializing 
 * vendor authentication requests in the Payment API Security Enhancement project.
 * 
 * This class supports the backward compatibility layer by maintaining the existing
 * API contract with vendors while the enhanced security is implemented internally.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The client identifier used for authentication.
     * This field maps to the client_id property in the JSON request.
     */
    @NotBlank(message = "Client ID is required")
    @JsonProperty("client_id")
    private String clientId;
    
    /**
     * The client secret used for authentication.
     * This field maps to the client_secret property in the JSON request.
     */
    @NotBlank(message = "Client Secret is required")
    @JsonProperty("client_secret")
    private String clientSecret;
}