package org.paper.service;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleServiceTest {

    private ScheduleService scheduleService;

    @BeforeEach
    void setUp() {
        File file = new File("src/main/resources/schedules.json");
        scheduleService = new ScheduleService(file, new ObjectMapper());
    }

    @Test
    void testGetLessonsList() {
        List<String> mondayLessons = scheduleService.getLessonsList("7-A", DayOfWeek.MONDAY);
        assertNotNull(mondayLessons);
        assertFalse(mondayLessons.isEmpty());
        assertTrue(mondayLessons.size() >= 5);
    }

    @Test
    void testLiveStatusMorningBeforeStart() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.MONDAY, LocalTime.of(7, 30));
        assertTrue(status.contains("hali boshlanmagan"));
        assertTrue(status.contains("1-soat"));
    }

    @Test
    void testLiveStatusMorningDuringFirstLesson() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.MONDAY, LocalTime.of(8, 20));
        assertTrue(status.contains("1-soat"));
        assertTrue(status.contains("Tugash vaqti: 08:45"));
    }

    @Test
    void testLiveStatusMorningDuringShortBreak() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.MONDAY, LocalTime.of(8, 48));
        assertTrue(status.contains("Tanaffus (5 daqiqa)"));
        assertTrue(status.contains("Keyingi dars: 2-soat"));
    }

    @Test
    void testLiveStatusMorningDuringBigBreak() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.MONDAY, LocalTime.of(12, 10));
        assertTrue(status.contains("Katta tanaffus (20 daqiqa)"));
        assertTrue(status.contains("12:25"));
    }

    @Test
    void testLiveStatusMorningAfterSchool() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.MONDAY, LocalTime.of(13, 30));
        assertTrue(status.contains("yakunlandi"));
    }

    @Test
    void testLiveStatusAfternoonBeforeStart() {
        String status = scheduleService.getLiveStatusText("7-A", "afternoon", DayOfWeek.MONDAY, LocalTime.of(12, 50));
        assertTrue(status.contains("hali boshlanmagan"));
        assertTrue(status.contains("13:15"));
    }

    @Test
    void testLiveStatusAfternoonDuringLesson() {
        String status = scheduleService.getLiveStatusText("7-A", "afternoon", DayOfWeek.MONDAY, LocalTime.of(13, 30));
        assertTrue(status.contains("1-soat"));
        assertTrue(status.contains("13:55"));
    }

    @Test
    void testLiveStatusAfternoonAfterSchool() {
        String status = scheduleService.getLiveStatusText("7-A", "afternoon", DayOfWeek.MONDAY, LocalTime.of(18, 0));
        assertTrue(status.contains("yakunlandi"));
    }

    @Test
    void testLiveStatusSunday() {
        String status = scheduleService.getLiveStatusText("7-A", "morning", DayOfWeek.SUNDAY, LocalTime.of(10, 0));
        assertTrue(status.contains("Yakshanba") || status.contains("yakshanba"));
    }

    @Test
    void testBackpackChecklist() {
        String checklist = scheduleService.getBackpackChecklist("7-A", DayOfWeek.MONDAY);
        assertNotNull(checklist);
        assertTrue(checklist.contains("Sumka ro'yxati"));
        assertTrue(checklist.contains("Darslik va daftarlar"));
        assertTrue(checklist.contains("Kundalik"));
        assertTrue(checklist.contains("Qalamdon"));
    }

    @Test
    void testEveningReminderText() {
        String reminder = scheduleService.getEveningReminderText("7-A", "morning", DayOfWeek.TUESDAY);
        assertNotNull(reminder);
        assertTrue(reminder.contains("Kechki eslatma"));
        assertTrue(reminder.contains("Seshanba"));
        assertTrue(reminder.contains("08:00"));

        // Sunday target should return null as no school on Sunday
        assertNull(scheduleService.getEveningReminderText("7-A", "morning", DayOfWeek.SUNDAY));
    }
}
