package org.twins.horn.service.auth.dto;

import lombok.Data;

@Data
public class TokenIntrospectRsDTOv1 {
    // private String active;
    private String clientId;
    private Long tokenExpiryDate;
    //todo - create bean via swagger
}
