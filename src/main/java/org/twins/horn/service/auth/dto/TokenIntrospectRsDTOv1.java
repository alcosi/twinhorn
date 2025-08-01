package org.twins.horn.service.auth.dto;

import lombok.Data;

@Data
public class TokenIntrospectRsDTOv1 {
    public String active;
    public String clientId;
    public Long exp;

    //todo - create bean via swagger
}
