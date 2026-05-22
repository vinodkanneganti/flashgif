package com.flashgif.users.domain;

public enum UserStatus {
    ACTIVE, DISABLED;

    public String dbValue() { return name().toLowerCase(); }
}
