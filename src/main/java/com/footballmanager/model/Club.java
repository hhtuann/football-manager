package com.footballmanager.model;

import java.util.List;
import java.util.ArrayList;

public class Club {
    private int id;
    private String fullName;
    private String shortName;
    private int foundingYear;
    private String coachName;
    private Manager manager;
    private Stadium homeStadium;
    private List<ClubPlayer> clubPlayers = new ArrayList<>();

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public int getFoundingYear() {
        return foundingYear;
    }

    public void setFoundingYear(int foundingYear) {
        this.foundingYear = foundingYear;
    }

    public String getCoachName() {
        return coachName;
    }

    public void setCoachName(String coachName) {
        this.coachName = coachName;
    }

    public Manager getManager() {
        return manager;
    }

    public void setManager(Manager manager) {
        this.manager = manager;
    }

    public Stadium getHomeStadium() {
        return homeStadium;
    }

    public void setHomeStadium(Stadium homeStadium) {
        this.homeStadium = homeStadium;
    }

    public List<ClubPlayer> getClubPlayers() {
        return clubPlayers;
    }

    public void setClubPlayers(List<ClubPlayer> clubPlayers) {
        this.clubPlayers = clubPlayers;
    }
}
