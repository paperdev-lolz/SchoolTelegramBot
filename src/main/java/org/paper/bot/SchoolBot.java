package org.paper.bot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.paper.model.UserProfile;
import org.paper.service.ScheduleService;
import org.paper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class SchoolBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(SchoolBot.class);
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Tashkent");

    private final String botToken;
    private final String botUsername;
    private final UserService userService;
    private final ScheduleService scheduleService;

    public SchoolBot(String botToken, String botUsername, UserService userService, ScheduleService scheduleService) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.userService = userService;
        this.scheduleService = scheduleService;
    }

    public void registerCommands() {
        List<BotCommand> commands = List.of(
                new BotCommand("/jadval", "Dars jadvalini ko'rish"),
                new BotCommand("/bugun", "Bugungi darslar"),
                new BotCommand("/ertaga", "Ertangi darslar"),
                new BotCommand("/hafta", "Haftalik dars jadvali"),
                new BotCommand("/vaqtlar", "Qo'ng'iroqlar va dars vaqtlari"),
                new BotCommand("/sinf", "Mening sinfim va smenam"),
                new BotCommand("/register", "Sinfni tanlash yoki almashtirish"),
                new BotCommand("/sozlamalar", "Profil va bildirishnomalarni sozlash"),
                new BotCommand("/yordam", "Yordam va ma'lumot")
        );

        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            log.info("Telegram menyu buyruqlari muvaffaqiyatli ro'yxatdan o'tkazildi.");
        } catch (TelegramApiException e) {
            log.warn("Telegram buyruqlarini ro'yxatdan o'tkazishda ogohlantirish: {}", e.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Update qayta ishlashda xatolik", e);
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText().trim();
        Long chatId = message.getChatId();

        // Strip @botusername in group chats
        if (text.contains("@")) {
            text = text.replaceAll("@" + PatternQuote(botUsername.replace("@", "")), "");
        }

        String command = text.toLowerCase().split("\\s+")[0];

        switch (command) {
            case "/start" -> handleStartCommand(chatId);
            case "/register", "/registration" -> sendCategorySelection(chatId, null);
            case "/jadval", "/dars", "/darslar" -> handleScheduleCommand(chatId, "today");
            case "/bugun" -> handleScheduleCommand(chatId, "today");
            case "/ertaga" -> handleScheduleCommand(chatId, "tomorrow");
            case "/hafta", "/haftalik" -> handleScheduleCommand(chatId, "week");
            case "/sinf", "/profil" -> handleClassInfoCommand(chatId);
            case "/vaqtlar", "/qongiroq" -> handleBellScheduleCommand(chatId);
            case "/sozlamalar", "/settings" -> sendSettingsMessage(chatId, null);
            case "/yordam", "/help" -> handleHelpCommand(chatId);
            default -> handleDirectTextInput(chatId, text);
        }
    }

    private void handleStartCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        String response = "Assalomu alaykum. 8-maktab axborot botiga xush kelibsiz.\n\n"
                + "Bot orqali dars jadvali va qo'ng'iroqlar vaqtini bilib olishingiz mumkin.\n\n"
                + (profile != null
                ? "Siz ulangan sinf: " + profile.getClassName() + " (" + profile.getShiftDisplayName() + ")\n\n"
                + "Buyruqlar:\n"
                + "/jadval - Dars jadvali\n"
                + "/bugun - Bugungi darslar\n"
                + "/ertaga - Ertangi darslar\n"
                + "/vaqtlar - Qo'ng'iroqlar jadvali\n"
                + "/sozlamalar - Sozlamalar"
                : "Boshlash uchun sinfingizni tanlang: /register");
        sendMessage(chatId, response);
    }

    private void handleScheduleCommand(Long chatId, String option) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Sinf tanlanmagan. Iltimos, avval /register orqali sinfingizni tanlang.");
        } else {
            sendScheduleMessage(chatId, null, profile.getClassName(), option);
        }
    }

    private void handleClassInfoCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Siz hali sinfingizni tanlamagansiz.\nTanlash uchun: /register");
            return;
        }

        String text = "Sizning ma'lumotlaringiz:\n\n"
                + "Sinf: " + profile.getClassName() + "\n"
                + "Smena: " + profile.getShiftDisplayName() + "\n"
                + "Qo'ng'iroq bildirishnomalari: " + (profile.isBellNotifications() ? "Yoqilgan" : "O'chirilgan") + "\n\n"
                + "O'zgartirish uchun: /sozlamalar";
        sendMessage(chatId, text);
    }

    private void handleBellScheduleCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        String shift = profile != null ? profile.getShift() : "morning";
        sendBellScheduleMessage(chatId, null, shift);
    }

    private void handleHelpCommand(Long chatId) {
        sendMessage(chatId,
                "Buyruqlar ro'yxati:\n\n"
                        + "/jadval - Dars jadvalini ko'rish\n"
                        + "/bugun - Bugungi darslar\n"
                        + "/ertaga - Ertangi darslar\n"
                        + "/hafta - Haftalik to'liq jadval\n"
                        + "/sinf - Joriy sinf va smena ma'lumoti\n"
                        + "/register - Sinf va smenani tanlash\n"
                        + "/vaqtlar - Qo'ng'iroqlar va dars vaqtlari\n"
                        + "/sozlamalar - Profil va bildirishnomalarni sozlash\n"
                        + "/yordam - Ushbu yordam xabari");
    }

    private void handleDirectTextInput(Long chatId, String text) {
        String parsedClass = scheduleService.parseClassName(text);
        if (parsedClass != null) {
            if (scheduleService.isPrimaryGrade(parsedClass)) {
                sendMessage(chatId, parsedClass + " — Boshlang'ich (1-4) sinflar dars jadvali hali kiritilmagan (Tez kunda).\n\n"
                        + "Hozircha 5-11 sinflar mavjud: /register");
                return;
            }
            if (!scheduleService.hasClass(parsedClass)) {
                sendMessage(chatId, parsedClass + " sinfi maktab jadvalida topilmadi.\n\nMavjud sinflarni ko'rish uchun /register buyrug'ini yuboring.");
                return;
            }
            sendShiftSelection(chatId, null, parsedClass);
        }
    }

    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();

        try {
            execute(new AnswerCallbackQuery(callbackQuery.getId()));
        } catch (TelegramApiException ignored) {
        }

        if (data.startsWith("reg_group:")) {
            String group = data.substring("reg_group:".length());
            if (group.equals("back")) {
                sendCategorySelection(chatId, messageId);
            } else {
                sendGradeSelection(chatId, messageId, group);
            }
        } else if (data.startsWith("reg_class:")) {
            String className = data.substring("reg_class:".length());
            sendShiftSelection(chatId, messageId, className);
        } else if (data.startsWith("reg_shift:")) {
            String[] parts = data.substring("reg_shift:".length()).split(":");
            String className = parts[0];
            String shift = parts[1];
            sendBellNotificationSelection(chatId, messageId, className, shift);
        } else if (data.startsWith("reg_bell:")) {
            String[] parts = data.substring("reg_bell:".length()).split(":");
            String className = parts[0];
            String shift = parts[1];
            boolean bells = Boolean.parseBoolean(parts[2]);

            UserProfile profile = new UserProfile(className, shift, bells);
            userService.saveProfile(chatId, profile);

            String breakInfo = "afternoon".equalsIgnoreCase(shift)
                    ? "Darslar: 40 daqiqa (Katta tanaffus yo'q)."
                    : "Katta tanaffus: 5-soatdan keyin 20 daqiqa.";

            String text = "Ro'yxatdan o'tish yakunlandi.\n\n"
                    + "Sinf: " + className + "\n"
                    + "Smena: " + profile.getShiftDisplayName() + "\n"
                    + "Bildirishnomalar: " + (bells ? "Yoqilgan" : "O'chirilgan") + "\n\n"
                    + breakInfo;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(createButton("Bugungi jadval", "sch_day:today")));
            rows.add(List.of(
                    createButton("Dars vaqtlari", "vaqtlar_shift:" + shift),
                    createButton("Sozlamalar", "open_settings")
            ));
            markup.setKeyboard(rows);

            editMessage(chatId, messageId, text, markup);
        } else if (data.startsWith("sch_day:")) {
            String dayOption = data.substring("sch_day:".length());
            UserProfile profile = userService.getProfile(chatId);
            if (profile == null) {
                editMessage(chatId, messageId, "Iltimos, avval /register orqali sinfingizni tanlang.", null);
                return;
            }
            sendScheduleMessage(chatId, messageId, profile.getClassName(), dayOption);
        } else if (data.startsWith("vaqtlar_shift:")) {
            String shift = data.substring("vaqtlar_shift:".length());
            sendBellScheduleMessage(chatId, messageId, shift);
        } else if (data.equals("open_settings")) {
            sendSettingsMessage(chatId, messageId);
        } else if (data.equals("toggle_shift")) {
            userService.toggleShift(chatId);
            sendSettingsMessage(chatId, messageId);
        } else if (data.equals("toggle_bell")) {
            userService.toggleBellNotifications(chatId);
            sendSettingsMessage(chatId, messageId);
        }
    }

    private void sendCategorySelection(Long chatId, Integer editMessageId) {
        String text = "Sinf toifasini tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("Boshlang'ich (1-4 sinflar) [Tez kunda]", "reg_group:1-4")));
        rows.add(List.of(createButton("O'rta maktab (5-8 sinflar)", "reg_group:5-8")));
        rows.add(List.of(createButton("Yuqori sinflar (9-11 sinflar)", "reg_group:9-11")));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, text, markup);
        } else {
            sendMessage(chatId, text, markup);
        }
    }

    private void sendPrimaryComingSoon(Long chatId, Integer editMessageId) {
        String text = "Boshlang'ich sinflar (1-4 sinflar)\n\n"
                + "Ushbu sinflar uchun dars jadvali hali kiritilmagan (Tez kunda).\n\n"
                + "Iltimos, boshqa toifani tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("O'rta maktab (5-8 sinflar)", "reg_group:5-8")));
        rows.add(List.of(createButton("Yuqori sinflar (9-11 sinflar)", "reg_group:9-11")));
        rows.add(List.of(createButton("Orqaga", "reg_group:back")));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, text, markup);
        } else {
            sendMessage(chatId, text, markup);
        }
    }

    private void sendGradeSelection(Long chatId, Integer editMessageId, String group) {
        if ("1-4".equals(group)) {
            sendPrimaryComingSoon(chatId, editMessageId);
            return;
        }

        int startGrade;
        int endGrade;

        switch (group) {
            case "5-8" -> {
                startGrade = 5;
                endGrade = 8;
            }
            case "9-11" -> {
                startGrade = 9;
                endGrade = 11;
            }
            default -> {
                sendCategorySelection(chatId, editMessageId);
                return;
            }
        }

        String text = group + " sinflar bo'yicha sinfingizni tanlang:";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String[] letters = {"A", "B", "D"};

        for (int g = startGrade; g <= endGrade; g++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (String letter : letters) {
                String className = g + "-" + letter;
                if (scheduleService.hasClass(className)) {
                    row.add(createButton(className, "reg_class:" + className));
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }

        rows.add(List.of(createButton("Orqaga", "reg_group:back")));
        markup.setKeyboard(rows);

        editMessage(chatId, editMessageId, text, markup);
    }

    private void sendShiftSelection(Long chatId, Integer editMessageId, String className) {
        String text = "Sinf: " + className + "\n\nO'qish smenasini tanlang:";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("1-smena (Ertalabki)", "reg_shift:" + className + ":morning")));
        rows.add(List.of(createButton("2-smena (Tushdan keyingi)", "reg_shift:" + className + ":afternoon")));

        String category = scheduleService.getCategoryForClass(className);
        rows.add(List.of(createButton("Orqaga", "reg_group:" + category)));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, text, markup);
        } else {
            sendMessage(chatId, text, markup);
        }
    }

    private void sendBellNotificationSelection(Long chatId, Integer editMessageId, String className, String shift) {
        String shiftDisplayName = "afternoon".equalsIgnoreCase(shift) ? "2-smena" : "1-smena";
        String text = "Sinf: " + className + " (" + shiftDisplayName + ")\n\n"
                + "Qo'ng'iroqlar va tanaffuslar haqida bildirishnoma olib turasizmi?";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                createButton("Ha", "reg_bell:" + className + ":" + shift + ":true"),
                createButton("Yo'q", "reg_bell:" + className + ":" + shift + ":false")
        ));
        rows.add(List.of(createButton("Orqaga", "reg_class:" + className)));

        markup.setKeyboard(rows);

        editMessage(chatId, editMessageId, text, markup);
    }

    private void sendScheduleMessage(Long chatId, Integer editMessageId, String className, String option) {
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        DayOfWeek dow = today.getDayOfWeek();

        String scheduleText;

        if ("week".equalsIgnoreCase(option)) {
            scheduleText = scheduleService.getFullWeekSchedule(className);
        } else if ("tomorrow".equalsIgnoreCase(option)) {
            LocalDate tomorrow = today.plusDays(1);
            DayOfWeek tomorrowDow = tomorrow.getDayOfWeek();
            if (tomorrowDow == DayOfWeek.SUNDAY) {
                tomorrow = tomorrow.plusDays(1);
                tomorrowDow = DayOfWeek.MONDAY;
                scheduleText = "Ertaga yakshanba (dam olish kuni).\nDushanba kungi dars jadvali:\n\n"
                        + scheduleService.getDaySchedule(className, tomorrowDow);
            } else {
                scheduleText = "Ertangi (" + scheduleService.getDayName(tomorrowDow) + ") dars jadvali:\n\n"
                        + scheduleService.getDaySchedule(className, tomorrowDow);
            }
        } else if ("today".equalsIgnoreCase(option)) {
            if (dow == DayOfWeek.SUNDAY) {
                scheduleText = "Bugun yakshanba (dam olish kuni).\nDushanba kungi dars jadvali:\n\n"
                        + scheduleService.getDaySchedule(className, DayOfWeek.MONDAY);
            } else {
                scheduleText = "Bugungi (" + scheduleService.getDayName(dow) + ") dars jadvali:\n\n"
                        + scheduleService.getDaySchedule(className, dow);
            }
        } else {
            // Check if option is a specific day of the week
            try {
                DayOfWeek selectedDay = DayOfWeek.valueOf(option.toUpperCase());
                scheduleText = scheduleService.getDayName(selectedDay) + " kungi dars jadvali:\n\n"
                        + scheduleService.getDaySchedule(className, selectedDay);
            } catch (IllegalArgumentException e) {
                scheduleText = scheduleService.getDaySchedule(className, dow);
            }
        }

        UserProfile profile = userService.getProfile(chatId);
        String currentShift = profile != null ? profile.getShift() : "morning";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Quick navigation: Today, Tomorrow, Week
        rows.add(List.of(
                createButton("Bugun", "sch_day:today"),
                createButton("Ertaga", "sch_day:tomorrow"),
                createButton("Haftalik", "sch_day:week")
        ));

        // Day by day selector: Dush, Sesh, Chor, Pay, Juma, Shan
        rows.add(List.of(
                createButton("Dush", "sch_day:MONDAY"),
                createButton("Sesh", "sch_day:TUESDAY"),
                createButton("Chor", "sch_day:WEDNESDAY"),
                createButton("Pay", "sch_day:THURSDAY"),
                createButton("Juma", "sch_day:FRIDAY"),
                createButton("Shan", "sch_day:SATURDAY")
        ));

        // Bell timings and settings
        rows.add(List.of(
                createButton("Dars vaqtlari", "vaqtlar_shift:" + currentShift),
                createButton("Sozlamalar", "open_settings")
        ));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, scheduleText, markup);
        } else {
            sendMessage(chatId, scheduleText, markup);
        }
    }

    private void sendBellScheduleMessage(Long chatId, Integer editMessageId, String shift) {
        String otherShift = "morning".equalsIgnoreCase(shift) ? "afternoon" : "morning";
        String scheduleText = scheduleService.getBellScheduleText(shift);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton(
                "morning".equalsIgnoreCase(shift) ? "2-smenaga o'tish" : "1-smenaga o'tish",
                "vaqtlar_shift:" + otherShift
        )));
        rows.add(List.of(
                createButton("Jadvalga qaytish", "sch_day:today"),
                createButton("Sozlamalar", "open_settings")
        ));
        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, scheduleText, markup);
        } else {
            sendMessage(chatId, scheduleText, markup);
        }
    }

    private void sendSettingsMessage(Long chatId, Integer editMessageId) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Siz hali ro'yxatdan o'tmagansiz. /register orqali boshlang.");
            return;
        }

        String text = "Sozlamalar:\n\n"
                + "Sinf: " + profile.getClassName() + "\n"
                + "Smena: " + profile.getShiftDisplayName() + "\n"
                + "Bildirishnomalar: " + (profile.isBellNotifications() ? "Yoqilgan" : "O'chirilgan");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("Sinfni o'zgartirish", "reg_group:back")));
        rows.add(List.of(createButton("Smenani almashtirish", "toggle_shift")));
        rows.add(List.of(createButton(
                profile.isBellNotifications() ? "Bildirishnomani o'chirish" : "Bildirishnomani yoqish",
                "toggle_bell"
        )));
        rows.add(List.of(createButton("Dars jadvali", "sch_day:today")));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, text, markup);
        } else {
            sendMessage(chatId, text, markup);
        }
    }

    public void sendDirectMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private void sendMessage(Long chatId, String text) {
        sendMessage(chatId, text, null);
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage sm = new SendMessage();
        sm.setChatId(chatId.toString());
        sm.setText(text);
        if (markup != null) {
            sm.setReplyMarkup(markup);
        }
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            handleTelegramError(chatId, e);
        }
    }

    private void editMessage(Long chatId, Integer messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText em = new EditMessageText();
        em.setChatId(chatId.toString());
        em.setMessageId(messageId);
        em.setText(text);
        if (markup != null) {
            em.setReplyMarkup(markup);
        }
        try {
            execute(em);
        } catch (TelegramApiException e) {
            if (!e.getMessage().contains("message is not modified")) {
                handleTelegramError(chatId, e);
            }
        }
    }

    private void handleTelegramError(Long chatId, TelegramApiException e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("bot was blocked by the user") || msg.contains("user is deactivated") || msg.contains("chat not found"))) {
            log.info("Foydalanuvchi botni bloklagan yoki chat topilmadi: chatId={}", chatId);
            userService.disableBellNotifications(chatId);
        } else {
            log.error("Xabar yuborishda xatolik: chatId={}", chatId, e);
        }
    }

    private String PatternQuote(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
}
