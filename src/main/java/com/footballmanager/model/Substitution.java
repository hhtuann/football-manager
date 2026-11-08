package com.footballmanager.model;

public class Substitution extends Event {
    private MatchPlayer playerIn;

    public MatchPlayer getPlayerIn() {
        return playerIn;
    }

    public void setPlayerIn(MatchPlayer playerIn) {
        this.playerIn = playerIn;
    }
}
