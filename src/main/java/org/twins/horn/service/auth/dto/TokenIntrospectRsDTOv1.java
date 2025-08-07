package org.twins.horn.service.auth.dto;

import lombok.Data;

@Data
public class TokenIntrospectRsDTOv1 {
    private String active;
    private String clientId;
    private Long exp;

    //todo - create bean via swagger
}
