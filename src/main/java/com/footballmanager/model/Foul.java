package com.footballmanager.model;

public class Foul extends Event {
    private MatchPlayer victim;

    public MatchPlayer getVictim() {
        return victim;
    }

    public void setVictim(MatchPlayer victim) {
        this.victim = victim;
    }
}
