package com.enterpriseai.hub.domain;

/**
 * Authorities recognised by the platform. The {@code ROLE_} prefix is kept in the
 * persisted value so it can be mapped straight onto a Spring Security authority.
 */
public enum RoleName {

    ROLE_ADMIN,
    ROLE_USER;

    public String shortName() {
        return name().substring("ROLE_".length());
    }
}
