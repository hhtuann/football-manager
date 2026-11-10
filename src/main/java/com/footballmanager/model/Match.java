package com.footballmanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

public class Match {
    private int id;
    private LocalDateTime kickoffTime;
    private String status;
    private List<MatchClub> matchClubs = new ArrayList<>();
    private List<Event> events = new ArrayList<>();
    private boolean lineupLocked = true;
    private Integer roundNumber;
    private Integer matchNumber;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public LocalDateTime getKickoffTime() {
        return kickoffTime;
    }

    public void setKickoffTime(LocalDateTime kickoffTime) {
        this.kickoffTime = kickoffTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<MatchClub> getMatchClubs() {
        return matchClubs;
    }

    public void setMatchClubs(List<MatchClub> matchClubs) {
        this.matchClubs = matchClubs;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(List<Event> events) {
        this.events = events;
    }

    public boolean isLineupLocked() {
        return kickoffTime == null || lineupLocked;
    }

    public void setLineupLocked(boolean lineupLocked) {
        this.lineupLocked = lineupLocked;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public Integer getMatchNumber() {
        return matchNumber;
    }

    public void setMatchNumber(Integer matchNumber) {
        this.matchNumber = matchNumber;
    }

    public String getDisplayCode() {
        return roundNumber == null || matchNumber == null ? "Chưa xác định" : String.format("R%02d - T%02d",
            roundNumber, matchNumber);
    }
}
