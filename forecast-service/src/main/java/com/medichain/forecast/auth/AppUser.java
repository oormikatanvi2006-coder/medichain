package com.medichain.forecast.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** A person who can sign in. Passwords are stored as salted PBKDF2 hashes, never as text. */
@Entity
@Table(name = "app_user")
public class AppUser {
    public static final String STATE_ADMIN = "STATE_ADMIN";
    public static final String DISTRICT_PHARMACIST = "DISTRICT_PHARMACIST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String passwordHash;
    private String salt;
    private String displayName;
    private String designation;
    @Column(name = "role_code")
    private String role;
    /** Null for state-level users, who may open every district. */
    private String district;
    private LocalDateTime lastLoginAt;

    public AppUser() {
    }

    public AppUser(String username, String passwordHash, String salt, String displayName, String designation,
                   String role, String district) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.displayName = displayName;
        this.designation = designation;
        this.role = role;
        this.district = district;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
