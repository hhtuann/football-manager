package com.footballmanager.model;

public class Rule {
    private int id;
    private int requiredMainPlayers;
    private int maxBenchPlayers;
    private int maxSubstitutions;
    private int yellowCardsToSuspend;
    private int ageLimit;
    private int maxForeignPlayers;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRequiredMainPlayers() {
        return requiredMainPlayers;
    }

    public void setRequiredMainPlayers(int requiredMainPlayers) {
        this.requiredMainPlayers = requiredMainPlayers;
    }

    public int getMaxBenchPlayers() {
        return maxBenchPlayers;
    }

    public void setMaxBenchPlayers(int maxBenchPlayers) {
        this.maxBenchPlayers = maxBenchPlayers;
    }

    public int getMaxSubstitutions() {
        return maxSubstitutions;
    }

    public void setMaxSubstitutions(int maxSubstitutions) {
        this.maxSubstitutions = maxSubstitutions;
    }

    public int getYellowCardsToSuspend() {
        return yellowCardsToSuspend;
    }

    public void setYellowCardsToSuspend(int yellowCardsToSuspend) {
        this.yellowCardsToSuspend = yellowCardsToSuspend;
    }

    public int getAgeLimit() {
        return ageLimit;
    }

    public void setAgeLimit(int ageLimit) {
        this.ageLimit = ageLimit;
    }

    public int getMaxForeignPlayers() {
        return maxForeignPlayers;
    }

    public void setMaxForeignPlayers(int maxForeignPlayers) {
        this.maxForeignPlayers = maxForeignPlayers;
    }
}
