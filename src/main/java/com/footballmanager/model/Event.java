package com.footballmanager.model;

public abstract class Event {
    private int id;
    private int minute;
    private int extraMinute;
    private String note;
    private MatchPlayer primaryPlayer;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public int getExtraMinute() {
        return extraMinute;
    }

    public void setExtraMinute(int extraMinute) {
        this.extraMinute = extraMinute;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public MatchPlayer getPrimaryPlayer() {
        return primaryPlayer;
    }

    public void setPrimaryPlayer(MatchPlayer primaryPlayer) {
        this.primaryPlayer = primaryPlayer;
    }
}
