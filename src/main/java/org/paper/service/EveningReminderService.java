package org.paper.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.paper.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EveningReminderService {
    private static final Logger log = LoggerFactory.getLogger(EveningReminderService.class);
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Tashkent");

    public static final List<LocalTime> SLOTS = List.of(
            LocalTime.of(19, 0),
            LocalTime.of(19, 30),
            LocalTime.of(20, 0)
    );

    private final UserService userService;
    private final ScheduleService scheduleService;
    private final BiConsumer<Long, String> messageSender;
    private final ScheduledExecutorService scheduler;

    private LocalDate lastDispatchedDate = null;

    public EveningReminderService(UserService userService,
                                  ScheduleService scheduleService,
                                  BiConsumer<Long, String> messageSender) {
        this.userService = userService;
        this.scheduleService = scheduleService;
        this.messageSender = messageSender;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "evening-reminder-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAndDispatch, 15, 30, TimeUnit.SECONDS);
        log.info("Kechki eslatmalar xizmati ishga tushirildi (Tasodifiy: 19:00, 19:30 yoki 20:00 da).");
    }

    /**
     * Deterministically chooses one of 19:00, 19:30, or 20:00 based on the date seed.
     * This ensures the time is randomized each day, but remains stable across bot restarts within the same day.
     */
    public LocalTime getScheduledTimeForDate(LocalDate date) {
        Random rand = new Random(date.toEpochDay() ^ 0x5DEECE66DL);
        return SLOTS.get(rand.nextInt(SLOTS.size()));
    }

    private void checkAndDispatch() {
        try {
            ZonedDateTime now = ZonedDateTime.now(SCHOOL_ZONE);
            LocalDate today = now.toLocalDate();

            // Only dispatch once per calendar day
            if (today.equals(lastDispatchedDate)) {
                return;
            }

            // If today is Saturday, tomorrow is Sunday (rest day), so no school tomorrow
            if (today.getDayOfWeek() == DayOfWeek.SATURDAY) {
                return;
            }

            LocalTime targetTime = getScheduledTimeForDate(today);
            LocalTime currentTime = now.toLocalTime();

            // Match if current time is at target time (or within a 2-minute catch-up window)
            if (!currentTime.isBefore(targetTime) && currentTime.isBefore(targetTime.plusMinutes(2))) {
                lastDispatchedDate = today;
                log.info("Bugungi kechki eslatma tarqatilmoqda. Belgilangan vaqt: {}", targetTime);
                dispatchReminders(today);
            }
        } catch (Exception e) {
            log.error("Kechki eslatma xizmatida xatolik yuz berdi", e);
        }
    }

    private void dispatchReminders(LocalDate today) {
        LocalDate tomorrow = today.plusDays(1);
        DayOfWeek tomorrowDow = tomorrow.getDayOfWeek();

        Map<Long, UserProfile> profiles = userService.getAllProfiles();

        for (Map.Entry<Long, UserProfile> entry : profiles.entrySet()) {
            Long chatId = entry.getKey();
            UserProfile profile = entry.getValue();

            if (!profile.isEveningReminder()) {
                continue;
            }

            String reminderText = scheduleService.getEveningReminderText(
                    profile.getClassName(),
                    profile.getShift(),
                    tomorrowDow
            );

            if (reminderText != null && !reminderText.isBlank()) {
                messageSender.accept(chatId, reminderText);
            }
        }
    }

    public boolean sendPreviewReminder(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            return false;
        }

        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        DayOfWeek targetDow = (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) ? DayOfWeek.MONDAY : tomorrow.getDayOfWeek();

        String reminderText = scheduleService.getEveningReminderText(
                profile.getClassName(),
                profile.getShift(),
                targetDow
        );

        if (reminderText != null) {
            messageSender.accept(chatId, "[Test Kechki Eslatma]\n\n" + reminderText);
            return true;
        }
        return false;
    }

    public void stop() {
        scheduler.shutdown();
    }
}
