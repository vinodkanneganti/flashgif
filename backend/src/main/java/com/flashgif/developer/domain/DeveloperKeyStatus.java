package com.flashgif.developer.domain;

public enum DeveloperKeyStatus {
    ACTIVE, REVOKED;

    public String dbValue() { return name().toLowerCase(); }
}
