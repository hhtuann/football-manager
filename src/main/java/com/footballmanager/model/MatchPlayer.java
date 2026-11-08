package com.footballmanager.model;

import java.time.LocalDateTime;

public class MatchPlayer {
    private int id;
    private LocalDateTime registeredAt;
    private String role;
    private ClubPlayer clubPlayer;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public ClubPlayer getClubPlayer() {
        return clubPlayer;
    }

    public void setClubPlayer(ClubPlayer clubPlayer) {
        this.clubPlayer = clubPlayer;
    }
}
