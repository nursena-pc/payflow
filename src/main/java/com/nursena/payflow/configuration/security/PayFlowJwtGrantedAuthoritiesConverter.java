package com.nursena.payflow.configuration.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class PayFlowJwtGrantedAuthoritiesConverter
    implements Converter<
    Jwt,
    Collection<GrantedAuthority>
    > {

    private static final String ROLE_CLAIM = "role";
    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public Collection<GrantedAuthority> convert(
        Jwt jwt
    ) {
        Objects.requireNonNull(
            jwt,
            "jwt must not be null"
        );

        Object role = jwt
            .getClaims()
            .get(ROLE_CLAIM);

        if (!ADMIN_ROLE.equals(role)) {
            return List.of();
        }

        return List.of(
            new SimpleGrantedAuthority(
                OperationsAuthorities.OPERATIONS
            )
        );
    }
}
