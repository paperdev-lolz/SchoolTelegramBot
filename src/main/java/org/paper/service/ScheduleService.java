package org.paper.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ScheduleService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private static final Pattern CLASS_INPUT_PATTERN = Pattern.compile("^/?(?:class_)?([1-9]|1[0-1])[- ]?([a-dA-Dа-яА-Я])$");

    private static final Map<DayOfWeek, String> UZBEK_DAYS = Map.of(
            DayOfWeek.MONDAY, "Dushanba",
            DayOfWeek.TUESDAY, "Seshanba",
            DayOfWeek.WEDNESDAY, "Chorshanba",
            DayOfWeek.THURSDAY, "Payshanba",
            DayOfWeek.FRIDAY, "Juma",
            DayOfWeek.SATURDAY, "Shanba",
            DayOfWeek.SUNDAY, "Yakshanba"
    );

    private final File schedulesFile;
    private final ObjectMapper objectMapper;
    private JsonNode cachedRoot;

    public ScheduleService(File schedulesFile, ObjectMapper objectMapper) {
        this.schedulesFile = schedulesFile;
        this.objectMapper = objectMapper;
        ensureSchedulesFile();
        loadSchedules();
    }

    private void ensureSchedulesFile() {
        if (schedulesFile.exists() && schedulesFile.length() > 0) {
            return;
        }

        try {
            File parent = schedulesFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (InputStream in = getClass().getResourceAsStream("/schedules.json")) {
                if (in != null) {
                    Files.copy(in, schedulesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Standart schedules.json fayli classpathdan yuklandi.");
                }
            }
        } catch (IOException e) {
            log.warn("schedules.json nusxalashda ogohlantirish: {}", e.getMessage());
        }
    }

    public synchronized void loadSchedules() {
        try {
            if (schedulesFile.exists()) {
                this.cachedRoot = objectMapper.readTree(schedulesFile);
            }
        } catch (IOException e) {
            log.error("Dars jadvalini o'qishda xatolik", e);
        }
    }

    public String getDaySchedule(String className, DayOfWeek dayOfWeek) {
        if (isPrimaryGrade(className)) {
            return className + " sinfi dars jadvali hali kiritilmagan (Tez kunda).";
        }

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Yakshanba — dam olish kuni.";
        }

        String dayKey = dayOfWeek.name().charAt(0) + dayOfWeek.name().substring(1).toLowerCase();
        JsonNode root = getRoot();
        if (root == null) {
            return "Dars jadvalini yuklashda xatolik yuz berdi.";
        }

        JsonNode classSchedule = root.get(className);
        if (classSchedule == null) {
            return className + " sinfi uchun jadval topilmadi.";
        }

        JsonNode lessons = classSchedule.path(dayKey);
        if (!lessons.isArray() || lessons.isEmpty()) {
            return UZBEK_DAYS.get(dayOfWeek) + " kuni uchun darslar belgilanmagan.";
        }

        StringBuilder sb = new StringBuilder(className + " sinfi — " + UZBEK_DAYS.get(dayOfWeek) + "\n\n");
        for (int i = 0; i < lessons.size(); i++) {
            sb.append(i + 1).append(". ").append(lessons.get(i).asText()).append("\n");
        }
        return sb.toString().trim();
    }

    public String getFullWeekSchedule(String className) {
        if (isPrimaryGrade(className)) {
            return className + " sinfi dars jadvali hali kiritilmagan (Tez kunda).";
        }

        JsonNode root = getRoot();
        if (root == null) {
            return "Haftalik jadvalni yuklashda xatolik yuz berdi.";
        }

        JsonNode classSchedule = root.get(className);
        if (classSchedule == null) {
            return className + " sinfi uchun jadval topilmadi.";
        }

        StringBuilder sb = new StringBuilder(className + " — Haftalik dars jadvali:\n\n");
        DayOfWeek[] schoolDays = {
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
        };

        for (DayOfWeek day : schoolDays) {
            String dayKey = day.name().charAt(0) + day.name().substring(1).toLowerCase();
            JsonNode lessons = classSchedule.path(dayKey);
            sb.append(UZBEK_DAYS.get(day)).append(":\n");
            if (lessons.isArray() && !lessons.isEmpty()) {
                for (int i = 0; i < lessons.size(); i++) {
                    sb.append("  ").append(i + 1).append(". ").append(lessons.get(i).asText()).append("\n");
                }
            } else {
                sb.append("  Darslar yo'q\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    public String getBellScheduleText(String shift) {
        if ("afternoon".equalsIgnoreCase(shift)) {
            return "8-Maktab — 2-smena (Tushdan keyingi)\n\n"
                    + "1-soat: 13:15 — 13:55\n"
                    + "2-soat: 14:00 — 14:40\n"
                    + "3-soat: 14:45 — 15:25\n"
                    + "4-soat: 15:30 — 16:10\n"
                    + "5-soat: 16:15 — 16:55\n"
                    + "6-soat: 17:00 — 17:40\n\n"
                    + "Darslar 40 daqiqa (Katta tanaffus yo'q)";
        } else {
            return "8-Maktab — 1-smena (Ertalabki)\n\n"
                    + "1-soat: 08:00 — 08:45\n"
                    + "2-soat: 08:50 — 09:35\n"
                    + "3-soat: 09:40 — 10:25\n"
                    + "4-soat: 10:30 — 11:15\n"
                    + "5-soat: 11:20 — 12:05\n"
                    + "Katta tanaffus (20 daqiqa): 12:05 — 12:25\n"
                    + "6-soat: 12:25 — 13:10";
        }
    }

    public String getDayName(DayOfWeek day) {
        return UZBEK_DAYS.getOrDefault(day, day.name());
    }

    public boolean isPrimaryGrade(String className) {
        if (className == null) return false;
        try {
            int grade = Integer.parseInt(className.split("-")[0]);
            return grade >= 1 && grade <= 4;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasClass(String className) {
        if (className == null) return false;
        JsonNode root = getRoot();
        return root != null && root.has(className);
    }

    public String getCategoryForClass(String className) {
        try {
            int grade = Integer.parseInt(className.split("-")[0]);
            if (grade <= 4) return "1-4";
            if (grade <= 8) return "5-8";
            return "9-11";
        } catch (Exception e) {
            return "5-8";
        }
    }

    public String parseClassName(String input) {
        if (input == null) return null;
        Matcher matcher = CLASS_INPUT_PATTERN.matcher(input.trim());
        if (matcher.matches()) {
            String grade = matcher.group(1);
            String letter = matcher.group(2).toUpperCase();
            letter = switch (letter) {
                case "А" -> "A";
                case "В", "B" -> "B";
                case "Д", "D" -> "D";
                default -> letter;
            };
            return grade + "-" + letter;
        }
        return null;
    }

    private JsonNode getRoot() {
        if (cachedRoot == null) {
            loadSchedules();
        }
        return cachedRoot;
    }
}
