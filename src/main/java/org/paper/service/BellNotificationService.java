package org.paper.service;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.paper.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BellNotificationService {
    private static final Logger log = LoggerFactory.getLogger(BellNotificationService.class);
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Tashkent");

    private static final Map<String, String> MORNING_BELLS = Map.ofEntries(
            Map.entry("08:00", "1-soat boshlandi."),
            Map.entry("08:45", "1-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("08:50", "2-soat boshlandi."),
            Map.entry("09:35", "2-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("09:40", "3-soat boshlandi."),
            Map.entry("10:25", "3-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("10:30", "4-soat boshlandi."),
            Map.entry("11:15", "4-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("11:20", "5-soat boshlandi."),
            Map.entry("12:05", "5-soat tugadi. Katta tanaffus (20 daqiqa, 12:25 gacha)."),
            Map.entry("12:25", "Katta tanaffus tugadi. 6-soat boshlandi."),
            Map.entry("13:10", "6-soat tugadi. 1-smena darslari yakunlandi.")
    );

    private static final Map<String, String> AFTERNOON_BELLS = Map.ofEntries(
            Map.entry("13:15", "1-soat boshlandi."),
            Map.entry("13:55", "1-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("14:00", "2-soat boshlandi."),
            Map.entry("14:40", "2-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("14:45", "3-soat boshlandi."),
            Map.entry("15:25", "3-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("15:30", "4-soat boshlandi."),
            Map.entry("16:10", "4-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("16:15", "5-soat boshlandi."),
            Map.entry("16:55", "5-soat tugadi. Tanaffus (5 daqiqa)."),
            Map.entry("17:00", "6-soat boshlandi."),
            Map.entry("17:40", "6-soat tugadi. 2-smena darslari yakunlandi.")
    );

    private final UserService userService;
    private final BiConsumer<Long, String> messageSender;
    private final ScheduledExecutorService scheduler;
    private String lastTriggerKey = "";

    public BellNotificationService(UserService userService, BiConsumer<Long, String> messageSender) {
        this.userService = userService;
        this.messageSender = messageSender;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "bell-notification-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkAndDispatchBells, 10, 30, TimeUnit.SECONDS);
        log.info("Qo'ng'iroq bildirishnomalari xizmati ishga tushirildi.");
    }

    private void checkAndDispatchBells() {
        try {
            ZonedDateTime now = ZonedDateTime.now(SCHOOL_ZONE);
            DayOfWeek dow = now.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) {
                return;
            }

            String timeKey = String.format("%02d:%02d", now.getHour(), now.getMinute());
            String triggerKey = dow.name() + "_" + timeKey;
            if (triggerKey.equals(lastTriggerKey)) {
                return;
            }

            String morningMsg = MORNING_BELLS.get(timeKey);
            String afternoonMsg = AFTERNOON_BELLS.get(timeKey);

            if (morningMsg == null && afternoonMsg == null) {
                return;
            }

            lastTriggerKey = triggerKey;
            Map<Long, UserProfile> profiles = userService.getAllProfiles();

            for (Map.Entry<Long, UserProfile> entry : profiles.entrySet()) {
                Long chatId = entry.getKey();
                UserProfile profile = entry.getValue();

                if (!profile.isBellNotifications()) {
                    continue;
                }

                String text = null;
                if ("morning".equalsIgnoreCase(profile.getShift()) && morningMsg != null) {
                    text = morningMsg;
                } else if ("afternoon".equalsIgnoreCase(profile.getShift()) && afternoonMsg != null) {
                    text = afternoonMsg;
                }

                if (text != null) {
                    messageSender.accept(chatId, text);
                }
            }
        } catch (Exception e) {
            log.error("Bildirishnoma yuborishda xatolik yuz berdi", e);
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}

