package org.paper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {
    private String className;
    private String shift;
    private boolean bellNotifications;

    public UserProfile() {
        this.shift = "morning";
        this.bellNotifications = false;
    }

    public UserProfile(String className, String shift, boolean bellNotifications) {
        this.className = className;
        this.shift = "afternoon".equalsIgnoreCase(shift) ? "afternoon" : "morning";
        this.bellNotifications = bellNotifications;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getShift() {
        return shift != null ? shift : "morning";
    }

    public void setShift(String shift) {
        this.shift = "afternoon".equalsIgnoreCase(shift) ? "afternoon" : "morning";
    }

    public boolean isBellNotifications() {
        return bellNotifications;
    }

    public void setBellNotifications(boolean bellNotifications) {
        this.bellNotifications = bellNotifications;
    }

    public String getShiftDisplayName() {
        return "afternoon".equalsIgnoreCase(shift) ? "2-smena (Tushdan keyingi)" : "1-smena (Ertalabki)";
    }
}

