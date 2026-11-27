package org.paper.service;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EveningReminderServiceTest {

    private EveningReminderService eveningReminderService;

    @BeforeEach
    void setUp() {
        File schedFile = new File("src/main/resources/schedules.json");
        File regFile = new File("target/test-registrations.json");
        ObjectMapper mapper = new ObjectMapper();
        UserService userService = new UserService(regFile, mapper);
        ScheduleService scheduleService = new ScheduleService(schedFile, mapper);
        eveningReminderService = new EveningReminderService(userService, scheduleService, (id, text) -> {});
    }

    @Test
    void testScheduledTimeAlwaysValidSlot() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        for (int i = 0; i < 30; i++) {
            LocalDate date = start.plusDays(i);
            LocalTime time = eveningReminderService.getScheduledTimeForDate(date);
            assertTrue(EveningReminderService.SLOTS.contains(time),
                    "Vaqt SLOTS ro'yxatida bo'lishi kerak: " + time);
        }
    }

    @Test
    void testScheduledTimeRandomizedDistribution() {
        // Over 60 days, all 3 slots (19:00, 19:30, 20:00) must appear
        Set<LocalTime> seenSlots = new HashSet<>();
        LocalDate start = LocalDate.of(2026, 9, 1);
        for (int i = 0; i < 60; i++) {
            seenSlots.add(eveningReminderService.getScheduledTimeForDate(start.plusDays(i)));
        }

        assertTrue(seenSlots.contains(LocalTime.of(19, 0)));
        assertTrue(seenSlots.contains(LocalTime.of(19, 30)));
        assertTrue(seenSlots.contains(LocalTime.of(20, 0)));
    }

    @Test
    void testScheduledTimeDeterministicPerDay() {
        LocalDate date = LocalDate.of(2026, 11, 27);
        LocalTime firstCall = eveningReminderService.getScheduledTimeForDate(date);
        LocalTime secondCall = eveningReminderService.getScheduledTimeForDate(date);
        assertEquals(firstCall, secondCall, "Bir xil kun uchun vaqt o'zgarmasligi kerak");
    }
}
