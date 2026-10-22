package com.example.judgex.models;

import java.io.Serializable;
import java.util.Objects;

public class TeamMember implements Serializable {
    private String id;
    private String name;
    private String email;
    private String rollNumber;
    private String department;
    private String yearOfStudy;
    private String phone;
    private String roleInTeam; // e.g. Leader, Developer, Designer, Researcher
    private boolean isLeader;
    private boolean confirmed;

    public TeamMember() {
        this.confirmed = true;
    }

    public TeamMember(String id, String name, String email, String rollNumber, 
                      String department, String yearOfStudy, String phone, boolean isLeader) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.rollNumber = rollNumber;
        this.department = department;
        this.yearOfStudy = yearOfStudy;
        this.phone = phone != null ? phone : "";
        this.roleInTeam = isLeader ? "Team Leader" : "Member";
        this.isLeader = isLeader;
        this.confirmed = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email != null ? email.trim().toLowerCase(java.util.Locale.US) : "";
    }

    public void setEmail(String email) {
        this.email = email != null ? email.trim().toLowerCase(java.util.Locale.US) : "";
    }

    public String getRollNumber() {
        return rollNumber != null ? rollNumber.trim().toUpperCase(java.util.Locale.US) : "";
    }

    public void setRollNumber(String rollNumber) {
        this.rollNumber = rollNumber != null ? rollNumber.trim().toUpperCase(java.util.Locale.US) : "";
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getYearOfStudy() {
        return yearOfStudy;
    }

    public void setYearOfStudy(String yearOfStudy) {
        this.yearOfStudy = yearOfStudy;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRoleInTeam() {
        return roleInTeam != null ? roleInTeam : (isLeader ? "Team Leader" : "Member");
    }

    public void setRoleInTeam(String roleInTeam) {
        this.roleInTeam = roleInTeam;
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void setLeader(boolean leader) {
        isLeader = leader;
        if (leader) {
            this.roleInTeam = "Team Leader";
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamMember that = (TeamMember) o;
        if (id != null && that.id != null && id.equals(that.id)) return true;
        if (getEmail() != null && !getEmail().isEmpty() && getEmail().equalsIgnoreCase(that.getEmail())) return true;
        if (getRollNumber() != null && !getRollNumber().isEmpty() && getRollNumber().equalsIgnoreCase(that.getRollNumber())) return true;
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, getEmail(), getRollNumber());
    }
}
