package org.paper.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfile {
    private String className;
    private String shift;
    private boolean bellNotifications;
    private boolean eveningReminder;

    public UserProfile() {
        this.shift = "morning";
        this.bellNotifications = false;
        this.eveningReminder = true;
    }

    public UserProfile(String className, String shift, boolean bellNotifications) {
        this(className, shift, bellNotifications, true);
    }

    public UserProfile(String className, String shift, boolean bellNotifications, boolean eveningReminder) {
        this.className = className;
        this.shift = "afternoon".equalsIgnoreCase(shift) ? "afternoon" : "morning";
        this.bellNotifications = bellNotifications;
        this.eveningReminder = eveningReminder;
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

    public boolean isEveningReminder() {
        return eveningReminder;
    }

    public void setEveningReminder(boolean eveningReminder) {
        this.eveningReminder = eveningReminder;
    }

    public String getShiftDisplayName() {
        return "afternoon".equalsIgnoreCase(shift) ? "2-smena (Tushdan keyingi)" : "1-smena (Ertalabki)";
    }
}
