package org.paper.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public static class LessonSlot {
        private final int period;
        private final LocalTime start;
        private final LocalTime end;
        private final LocalTime breakEnd;

        public LessonSlot(int period, LocalTime start, LocalTime end, LocalTime breakEnd) {
            this.period = period;
            this.start = start;
            this.end = end;
            this.breakEnd = breakEnd;
        }

        public int getPeriod() {
            return period;
        }

        public LocalTime getStart() {
            return start;
        }

        public LocalTime getEnd() {
            return end;
        }

        public LocalTime getBreakEnd() {
            return breakEnd;
        }
    }

    private static final List<LessonSlot> MORNING_SLOTS = List.of(
            new LessonSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 45), LocalTime.of(8, 50)),
            new LessonSlot(2, LocalTime.of(8, 50), LocalTime.of(9, 35), LocalTime.of(9, 40)),
            new LessonSlot(3, LocalTime.of(9, 40), LocalTime.of(10, 25), LocalTime.of(10, 30)),
            new LessonSlot(4, LocalTime.of(10, 30), LocalTime.of(11, 15), LocalTime.of(11, 20)),
            new LessonSlot(5, LocalTime.of(11, 20), LocalTime.of(12, 5), LocalTime.of(12, 25)),
            new LessonSlot(6, LocalTime.of(12, 25), LocalTime.of(13, 10), LocalTime.of(13, 10))
    );

    private static final List<LessonSlot> AFTERNOON_SLOTS = List.of(
            new LessonSlot(1, LocalTime.of(13, 15), LocalTime.of(13, 55), LocalTime.of(14, 0)),
            new LessonSlot(2, LocalTime.of(14, 0), LocalTime.of(14, 40), LocalTime.of(14, 45)),
            new LessonSlot(3, LocalTime.of(14, 45), LocalTime.of(15, 25), LocalTime.of(15, 30)),
            new LessonSlot(4, LocalTime.of(15, 30), LocalTime.of(16, 10), LocalTime.of(16, 15)),
            new LessonSlot(5, LocalTime.of(16, 15), LocalTime.of(16, 55), LocalTime.of(17, 0)),
            new LessonSlot(6, LocalTime.of(17, 0), LocalTime.of(17, 40), LocalTime.of(17, 40))
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

    public List<String> getLessonsList(String className, DayOfWeek dayOfWeek) {
        if (dayOfWeek == DayOfWeek.SUNDAY || isPrimaryGrade(className)) {
            return Collections.emptyList();
        }

        JsonNode root = getRoot();
        if (root == null) {
            return Collections.emptyList();
        }

        JsonNode classSchedule = root.get(className);
        if (classSchedule == null) {
            return Collections.emptyList();
        }

        String dayKey = dayOfWeek.name().charAt(0) + dayOfWeek.name().substring(1).toLowerCase();
        JsonNode lessons = classSchedule.path(dayKey);
        if (!lessons.isArray() || lessons.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();
        for (JsonNode lesson : lessons) {
            list.add(lesson.asText());
        }
        return list;
    }

    public String getDaySchedule(String className, DayOfWeek dayOfWeek) {
        if (isPrimaryGrade(className)) {
            return className + " sinfi dars jadvali hali kiritilmagan (Tez kunda).";
        }

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Yakshanba — dam olish kuni.";
        }

        List<String> lessons = getLessonsList(className, dayOfWeek);
        if (lessons.isEmpty()) {
            return UZBEK_DAYS.get(dayOfWeek) + " kuni uchun darslar belgilanmagan.";
        }

        StringBuilder sb = new StringBuilder(className + " sinfi — " + UZBEK_DAYS.get(dayOfWeek) + "\n\n");
        for (int i = 0; i < lessons.size(); i++) {
            sb.append(i + 1).append(". ").append(lessons.get(i)).append("\n");
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

    public String getLiveStatusText(String className, String shift, DayOfWeek dayOfWeek, LocalTime currentTime) {
        if (isPrimaryGrade(className)) {
            return className + " sinfi dars jadvali hali kiritilmagan (Tez kunda).";
        }

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Bugun yakshanba — dam olish kuni.\n\nDushanba kungi darslar uchun: /ertaga yoki /sumka";
        }

        List<String> lessons = getLessonsList(className, dayOfWeek);
        if (lessons.isEmpty()) {
            return className + " sinfi uchun " + getDayName(dayOfWeek) + " kuni darslar belgilanmagan.";
        }

        boolean isAfternoon = "afternoon".equalsIgnoreCase(shift);
        List<LessonSlot> allSlots = isAfternoon ? AFTERNOON_SLOTS : MORNING_SLOTS;

        int lessonCount = Math.min(lessons.size(), allSlots.size());
        LessonSlot firstSlot = allSlots.get(0);
        LessonSlot lastSlot = allSlots.get(lessonCount - 1);

        // Before first lesson
        if (currentTime.isBefore(firstSlot.getStart())) {
            long mins = Duration.between(currentTime, firstSlot.getStart()).toMinutes();
            String waitText = mins > 0 ? mins + " daqiqa qoldi" : "1 daqiqadan kam vaqt qoldi";
            return "Darslar hali boshlanmagan.\n\n"
                    + "Sinf: " + className + " (" + (isAfternoon ? "2-smena" : "1-smena") + ")\n"
                    + "1-soat: " + lessons.get(0) + "\n"
                    + "Boshlanish vaqti: " + firstSlot.getStart() + " (" + waitText + ").";
        }

        // After last lesson of the day ended
        if (!currentTime.isBefore(lastSlot.getEnd())) {
            return "Bugungi darslar yakunlandi.\n\n"
                    + "Sinf: " + className + "\n"
                    + "Bugun jami " + lessonCount + " ta dars o'tildi.\n\n"
                    + "Ertangi jadval: /ertaga\n"
                    + "Ertangi sumka: /sumka";
        }

        // Within school hours
        for (int i = 0; i < lessonCount; i++) {
            LessonSlot slot = allSlots.get(i);
            String currentLesson = lessons.get(i);
            String nextLesson = (i + 1 < lessonCount) ? lessons.get(i + 1) : null;

            // In lesson i
            if (!currentTime.isBefore(slot.getStart()) && currentTime.isBefore(slot.getEnd())) {
                long mins = Duration.between(currentTime, slot.getEnd()).toMinutes();
                String leftText = mins > 0 ? mins + " daqiqa qoldi" : "1 daqiqadan kam vaqt qoldi";

                StringBuilder sb = new StringBuilder();
                sb.append("Hozirgi holat:\n\n");
                sb.append("Sinf: ").append(className).append("\n");
                sb.append("Joriy dars: ").append(slot.getPeriod()).append("-soat — ").append(currentLesson).append("\n");
                sb.append("Tugash vaqti: ").append(slot.getEnd()).append(" (").append(leftText).append(")\n\n");

                if (nextLesson != null) {
                    sb.append("Keyingi dars: ").append(slot.getPeriod() + 1).append("-soat — ").append(nextLesson);
                } else {
                    sb.append("Bu bugungi oxirgi dars.");
                }
                return sb.toString();
            }

            // In break after lesson i
            if (i + 1 < lessonCount && !currentTime.isBefore(slot.getEnd()) && currentTime.isBefore(slot.getBreakEnd())) {
                long mins = Duration.between(currentTime, slot.getBreakEnd()).toMinutes();
                String leftText = mins > 0 ? mins + " daqiqa qoldi" : "1 daqiqadan kam vaqt qoldi";
                boolean isBigBreak = (!isAfternoon && slot.getPeriod() == 5);
                String breakName = isBigBreak ? "Katta tanaffus (20 daqiqa)" : "Tanaffus (5 daqiqa)";

                return "Hozirgi holat:\n\n"
                        + "Sinf: " + className + "\n"
                        + "Hozir: " + breakName + "\n"
                        + "Tugash vaqti: " + slot.getBreakEnd() + " (" + leftText + ")\n\n"
                        + "Keyingi dars: " + (slot.getPeriod() + 1) + "-soat — " + nextLesson;
            }
        }

        return "Bugungi darslar yakunlandi.";
    }

    public String getBackpackChecklist(String className, DayOfWeek dayOfWeek) {
        if (isPrimaryGrade(className)) {
            return className + " sinfi dars jadvali hali kiritilmagan (Tez kunda).";
        }

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return "Yakshanba — dam olish kuni.";
        }

        List<String> lessons = getLessonsList(className, dayOfWeek);
        if (lessons.isEmpty()) {
            return className + " sinfi uchun " + getDayName(dayOfWeek) + " kuni darslar belgilanmagan.";
        }

        Set<String> uniqueSubjects = new LinkedHashSet<>(lessons);
        Map<String, String> specialSupplies = new LinkedHashMap<>();

        for (String subject : uniqueSubjects) {
            String lower = subject.toLowerCase();
            if (lower.contains("jismoniy")) {
                specialSupplies.put("Jismoniy tarbiya", "Sport formasi va poyabzali (krossovka)");
            } else if (lower.contains("chizmachilik")) {
                specialSupplies.put("Chizmachilik", "Chizmachilik daftari, sirkul, chizg'ich va qalam");
            } else if (lower.contains("tasviriy") || lower.contains("rasm")) {
                specialSupplies.put("Tasviriy san'at", "Rasm daftari (albom), qalam yoki bo'yoqlar");
            } else if (lower.contains("texnologiya") || lower.contains("mehnat")) {
                specialSupplies.put("Texnologiya", "Mehnat daftari va amaliy mashg'ulot buyumlari");
            } else if (lower.contains("geometriya")) {
                specialSupplies.put("Geometriya", "Chizg'ich, sirkul va transportir");
            } else if (lower.contains("fizika")) {
                specialSupplies.put("Fizika", "Fizika daftari va chizg'ich");
            } else if (lower.contains("informatika")) {
                specialSupplies.put("Informatika", "Informatika daftari");
            } else if (lower.contains("musiqa")) {
                specialSupplies.put("Musiqa", "Musiqa daftari");
            } else if (lower.contains("geografiya")) {
                specialSupplies.put("Geografiya", "Atlas va kontur xarita");
            } else if (lower.contains("kimyo")) {
                specialSupplies.put("Kimyo", "Kimyo daftari");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Sinf: ").append(className).append("\n");
        sb.append("Sumka ro'yxati — ").append(getDayName(dayOfWeek)).append(":\n\n");

        sb.append("Darslik va daftarlar:\n");
        int i = 1;
        for (String s : lessons) {
            sb.append(i++).append(". ").append(s).append("\n");
        }

        sb.append("\nMaxsus zarur anjomlar:\n");
        if (specialSupplies.isEmpty()) {
            sb.append("• Maxsus qo'shimcha anjom talab etilmaydi\n");
        } else {
            for (Map.Entry<String, String> entry : specialSupplies.entrySet()) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        sb.append("\nHar kungi zarur buyumlar:\n");
        sb.append("• Kundalik daftari\n");
        sb.append("• Qalamdon (ruchka, oddiy qalam, o'chirg'ich, chizg'ich)");

        return sb.toString();
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
