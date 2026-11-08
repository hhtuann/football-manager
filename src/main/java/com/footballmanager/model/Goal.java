package com.footballmanager.model;

public class Goal extends Event {
    private String type;
    private MatchPlayer assister;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public MatchPlayer getAssister() {
        return assister;
    }

    public void setAssister(MatchPlayer assister) {
        this.assister = assister;
    }
}
