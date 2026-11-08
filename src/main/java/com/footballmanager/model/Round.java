package com.footballmanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class Round {
    private static final Pattern CANONICAL_NAME = Pattern.compile("Vòng [1-9][0-9]{0,8}");
    private int id;
    private String name;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private List<Match> matches = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getRoundNumber() {
        return numberFromName(name);
    }

    public static Integer numberFromName(String name) {
        return name != null && CANONICAL_NAME.matcher(name).matches()
                ? Integer.valueOf(name.substring(5)) : null;
    }

    public String getDisplayCode() {
        Integer number = getRoundNumber();
        return number == null ? "Chưa xác định" : String.format("R%02d", number);
    }

    public String getDisplayName() {
        return name;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Match> getMatches() {
        return matches;
    }

    public void setMatches(List<Match> matches) {
        this.matches = matches;
    }
}
