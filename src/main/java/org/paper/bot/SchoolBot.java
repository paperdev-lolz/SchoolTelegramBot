package org.paper.bot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.paper.model.UserProfile;
import org.paper.service.EveningReminderService;
import org.paper.service.ScheduleService;
import org.paper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.AnswerInlineQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.inlinequery.InlineQuery;
import org.telegram.telegrambots.meta.api.objects.inlinequery.inputmessagecontent.InputTextMessageContent;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResult;
import org.telegram.telegrambots.meta.api.objects.inlinequery.result.InlineQueryResultArticle;
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
    private final Set<Long> adminChatIds;
    private EveningReminderService eveningReminderService;

    public SchoolBot(String botToken,
                     String botUsername,
                     UserService userService,
                     ScheduleService scheduleService,
                     Set<Long> adminChatIds) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.userService = userService;
        this.scheduleService = scheduleService;
        this.adminChatIds = adminChatIds != null ? adminChatIds : Collections.emptySet();
    }

    public void setEveningReminderService(EveningReminderService eveningReminderService) {
        this.eveningReminderService = eveningReminderService;
    }

    public void registerCommands() {
        List<BotCommand> commands = List.of(
                new BotCommand("/jadval", "Dars jadvalini ko'rish"),
                new BotCommand("/hozir", "Hozirgi dars holati"),
                new BotCommand("/sumka", "Ertangi sumka anjomlari"),
                new BotCommand("/bugun", "Bugungi darslar"),
                new BotCommand("/ertaga", "Ertangi darslar"),
                new BotCommand("/hafta", "Haftalik dars jadvali"),
                new BotCommand("/eslatma", "Kechki eslatmani ko'rish"),
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
            if (update.hasInlineQuery()) {
                handleInlineQuery(update.getInlineQuery());
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Update qayta ishlashda xatolik", e);
        }
    }

    private void handleInlineQuery(InlineQuery inlineQuery) {
        String query = inlineQuery.getQuery() != null ? inlineQuery.getQuery().trim() : "";
        Long userId = inlineQuery.getFrom().getId();
        UserProfile profile = userService.getProfile(userId);

        List<InlineQueryResult> results = new ArrayList<>();
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        DayOfWeek todayDow = today.getDayOfWeek();
        LocalDate tomorrow = today.plusDays(1);
        DayOfWeek tomorrowDow = (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) ? DayOfWeek.MONDAY : tomorrow.getDayOfWeek();

        String parsedClass = scheduleService.parseClassName(query);

        if (parsedClass != null && scheduleService.hasClass(parsedClass)) {
            // User typed specific class: e.g. "@bot 8-a", "@bot 7b"
            addClassInlineResults(results, parsedClass, today, todayDow, tomorrowDow);
        } else if (query.equalsIgnoreCase("vaqt") || query.equalsIgnoreCase("vaqtlar") || query.equalsIgnoreCase("qongiroq")) {
            addBellTimingResults(results);
        } else if (profile != null) {
            // User has a registered class and query is empty: show their class schedule
            addClassInlineResults(results, profile.getClassName(), today, todayDow, tomorrowDow);
            addBellTimingResults(results);
        } else {
            // User not registered: show hints and sample class shortcuts
            addHelpInlineResults(results, today, todayDow, tomorrowDow);
        }

        AnswerInlineQuery answer = new AnswerInlineQuery();
        answer.setInlineQueryId(inlineQuery.getId());
        answer.setResults(results);
        answer.setCacheTime(15);
        answer.setIsPersonal(true);

        try {
            execute(answer);
        } catch (TelegramApiException e) {
            log.warn("Inline query javobini yuborishda xatolik: {}", e.getMessage());
        }
    }

    private void addClassInlineResults(List<InlineQueryResult> results,
                                       String className,
                                       LocalDate today,
                                       DayOfWeek todayDow,
                                       DayOfWeek tomorrowDow) {
        // 1. Bugungi darslar
        String todaySchedule;
        if (todayDow == DayOfWeek.SUNDAY) {
            todaySchedule = className + " sinfi — Bugun yakshanba (dam olish kuni).\nDushanba kungi dars jadvali:\n\n"
                    + scheduleService.getDaySchedule(className, DayOfWeek.MONDAY);
        } else {
            todaySchedule = className + " sinfi — Bugungi dars jadvali (" + scheduleService.getDayName(todayDow) + "):\n\n"
                    + scheduleService.getDaySchedule(className, todayDow);
        }

        List<String> todayLessons = scheduleService.getLessonsList(className, todayDow == DayOfWeek.SUNDAY ? DayOfWeek.MONDAY : todayDow);
        String todayDesc = todayLessons.isEmpty() ? "Darslar belgilanmagan" : String.join(", ", todayLessons.subList(0, Math.min(3, todayLessons.size()))) + "...";

        results.add(createArticle(
                className + "_today",
                className + " — Bugungi darslar",
                todayDesc,
                todaySchedule
        ));

        // 2. Ertangi darslar
        String tomorrowSchedule = className + " sinfi — Ertangi dars jadvali (" + scheduleService.getDayName(tomorrowDow) + "):\n\n"
                + scheduleService.getDaySchedule(className, tomorrowDow);
        List<String> tomorrowLessons = scheduleService.getLessonsList(className, tomorrowDow);
        String tomorrowDesc = tomorrowLessons.isEmpty() ? "Darslar belgilanmagan" : String.join(", ", tomorrowLessons.subList(0, Math.min(3, tomorrowLessons.size()))) + "...";

        results.add(createArticle(
                className + "_tomorrow",
                className + " — Ertangi darslar",
                tomorrowDesc,
                tomorrowSchedule
        ));

        // 3. Haftalik jadval
        String weekSchedule = scheduleService.getFullWeekSchedule(className);
        results.add(createArticle(
                className + "_week",
                className + " — Haftalik to'liq jadval",
                "Dushanbadan Shanbagacha haftalik dars jadvali",
                weekSchedule
        ));

        // 4. Sumka anjomlari
        String sumkaText = scheduleService.getBackpackChecklist(className, tomorrowDow);
        results.add(createArticle(
                className + "_sumka",
                className + " — Sumka anjomlari (" + scheduleService.getDayName(tomorrowDow) + ")",
                "Darsliklar va maxsus zarur buyumlar ro'yxati",
                sumkaText
        ));

        // 5. Jonli dars holati
        String liveText = scheduleService.getLiveStatusText(
                className,
                "morning",
                todayDow,
                LocalTime.now(SCHOOL_ZONE)
        );
        results.add(createArticle(
                className + "_live",
                className + " — Hozirgi dars holati",
                "Ayni vaqtdagi dars va qolgan vaqt hisobi",
                liveText
        ));
    }

    private void addBellTimingResults(List<InlineQueryResult> results) {
        results.add(createArticle(
                "bell_morning",
                "1-smena qo'ng'iroqlar jadvali (Ertalabki)",
                "08:00 dan 13:10 gacha (45 daqiqa dars, 20 daqiqa katta tanaffus)",
                scheduleService.getBellScheduleText("morning")
        ));

        results.add(createArticle(
                "bell_afternoon",
                "2-smena qo'ng'iroqlar jadvali (Tushdan keyingi)",
                "13:15 dan 17:40 gacha (40 daqiqa dars, katta tanaffussiz)",
                scheduleService.getBellScheduleText("afternoon")
        ));
    }

    private void addHelpInlineResults(List<InlineQueryResult> results,
                                      LocalDate today,
                                      DayOfWeek todayDow,
                                      DayOfWeek tomorrowDow) {
        results.add(createArticle(
                "hint_how_to",
                "Sinf nomini kiriting (masalan: 8-A)",
                "Format: @sakkizmaktabni_bot <sinf>",
                "8-Maktab boti: dars jadvalini ulashish uchun sinf nomini kiriting.\nMasalan: @sakkizmaktabni_bot 8-A"
        ));

        String[] sampleClasses = {"5-A", "7-A", "8-A", "9-A", "11-A"};
        for (String cls : sampleClasses) {
            String sched = scheduleService.getDaySchedule(cls, todayDow == DayOfWeek.SUNDAY ? DayOfWeek.MONDAY : todayDow);
            results.add(createArticle(
                    "sample_" + cls,
                    cls + " sinfi dars jadvali",
                    "Ulashish uchun bosing",
                    cls + " sinfi — Dars jadvali:\n\n" + sched
            ));
        }

        addBellTimingResults(results);
    }

    private InlineQueryResultArticle createArticle(String id, String title, String description, String text) {
        InlineQueryResultArticle article = new InlineQueryResultArticle();
        article.setId(id);
        article.setTitle(title);
        article.setDescription(description);

        InputTextMessageContent content = new InputTextMessageContent();
        content.setMessageText(text);
        article.setInputMessageContent(content);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText("8-Maktab Botini ochish");
        btn.setUrl("https://t.me/" + botUsername.replace("@", ""));
        markup.setKeyboard(List.of(List.of(btn)));
        article.setReplyMarkup(markup);

        return article;
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
            case "/hozir", "/status", "/live" -> handleLiveStatusCommand(chatId, null);
            case "/sumka", "/backpack" -> handleBackpackCommand(chatId, null, "tomorrow");
            case "/eslatma", "/testeslatma" -> handlePreviewReminderCommand(chatId);
            case "/sinf", "/profil" -> handleClassInfoCommand(chatId);
            case "/vaqtlar", "/qongiroq" -> handleBellScheduleCommand(chatId);
            case "/sozlamalar", "/settings" -> sendSettingsMessage(chatId, null);
            case "/myid", "/id" -> handleMyIdCommand(chatId);
            case "/elon", "/broadcast" -> handleAdminBroadcast(chatId, text);
            case "/statistika", "/stats" -> handleAdminStats(chatId);
            case "/yordam", "/help" -> handleHelpCommand(chatId);
            default -> handleDirectTextInput(chatId, text);
        }
    }

    private void handleStartCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        String response = "Assalomu alaykum. 8-maktab axborot botiga xush kelibsiz.\n\n"
                + "Bot orqali dars jadvali, jonli dars holati, sumka yordamchisi va kechki eslatmalarni bilib olishingiz mumkin.\n\n"
                + (profile != null
                ? "Siz ulangan sinf: " + profile.getClassName() + " (" + profile.getShiftDisplayName() + ")\n\n"
                + "Asosiy buyruqlar:\n"
                + "/hozir - Hozirgi dars holati\n"
                + "/sumka - Ertangi sumka anjomlari\n"
                + "/eslatma - Ertangi kun kechki eslatmasi\n"
                + "/jadval - Dars jadvali\n"
                + "/bugun - Bugungi darslar\n"
                + "/ertaga - Ertangi darslar\n"
                + "/vaqtlar - Qo'ng'iroqlar jadvali\n"
                + "/sozlamalar - Sozlamalar\n\n"
                + "💡 Istalgan chatda dars jadvalini ulashish uchun: @" + botUsername.replace("@", "") + " " + profile.getClassName()
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

    private void handleLiveStatusCommand(Long chatId, Integer editMessageId) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Sinf tanlanmagan. Iltimos, avval /register orqali sinfingizni tanlang.");
            return;
        }

        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        LocalTime now = LocalTime.now(SCHOOL_ZONE);
        String liveText = scheduleService.getLiveStatusText(
                profile.getClassName(),
                profile.getShift(),
                today.getDayOfWeek(),
                now
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                createButton("Yangilash", "live_refresh"),
                createButton("Bugungi darslar", "sch_day:today")
        ));
        rows.add(List.of(
                createButton("Sumka", "sch_sumka:tomorrow"),
                createButton("Dars vaqtlari", "vaqtlar_shift:" + profile.getShift())
        ));
        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, liveText, markup);
        } else {
            sendMessage(chatId, liveText, markup);
        }
    }

    private void handleBackpackCommand(Long chatId, Integer editMessageId, String dayOption) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Sinf tanlanmagan. Iltimos, avval /register orqali sinfingizni tanlang.");
            return;
        }

        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        DayOfWeek targetDow;

        if ("today".equalsIgnoreCase(dayOption)) {
            targetDow = today.getDayOfWeek();
        } else {
            LocalDate tomorrow = today.plusDays(1);
            if (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) {
                tomorrow = tomorrow.plusDays(1);
            } else if (today.getDayOfWeek() == DayOfWeek.SUNDAY) {
                tomorrow = today.plusDays(1);
            }
            targetDow = tomorrow.getDayOfWeek();
        }

        String backpackText = scheduleService.getBackpackChecklist(profile.getClassName(), targetDow);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                createButton("Ertaga uchun", "sumka_day:tomorrow"),
                createButton("Bugun uchun", "sumka_day:today")
        ));
        rows.add(List.of(
                createButton("Hozirgi dars", "sch_live"),
                createButton("Dars jadvali", "sch_day:today")
        ));
        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, backpackText, markup);
        } else {
            sendMessage(chatId, backpackText, markup);
        }
    }

    private void handlePreviewReminderCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        if (profile == null) {
            sendMessage(chatId, "Sinf tanlanmagan. Iltimos, avval /register orqali sinfingizni tanlang.");
            return;
        }

        if (eveningReminderService != null) {
            boolean sent = eveningReminderService.sendPreviewReminder(chatId);
            if (!sent) {
                sendMessage(chatId, "Ertangi kun uchun dars jadvali topilmadi yoki kiritilmagan.");
            }
        } else {
            LocalDate today = LocalDate.now(SCHOOL_ZONE);
            LocalDate tomorrow = today.plusDays(1);
            DayOfWeek targetDow = (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) ? DayOfWeek.MONDAY : tomorrow.getDayOfWeek();
            String text = scheduleService.getEveningReminderText(profile.getClassName(), profile.getShift(), targetDow);
            sendMessage(chatId, text != null ? text : "Ertangi kun uchun darslar belgilanmagan.");
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
                + "Qo'ng'iroq bildirishnomalari: " + (profile.isBellNotifications() ? "Yoqilgan" : "O'chirilgan") + "\n"
                + "Kechki eslatma (19:00 - 20:00): " + (profile.isEveningReminder() ? "Yoqilgan" : "O'chirilgan") + "\n\n"
                + "O'zgartirish uchun: /sozlamalar";
        sendMessage(chatId, text);
    }

    private void handleBellScheduleCommand(Long chatId) {
        UserProfile profile = userService.getProfile(chatId);
        String shift = profile != null ? profile.getShift() : "morning";
        sendBellScheduleMessage(chatId, null, shift);
    }

    private void handleMyIdCommand(Long chatId) {
        String msg = "Sizning Telegram ID raqamingiz: " + chatId + "\n\n"
                + "(Maktab ma'muri sifatida belgilash uchun ushbu ID ni bot sozlamalaridagi ADMIN_CHAT_ID o'zgaruvchisiga kiriting).";
        sendMessage(chatId, msg);
    }

    private boolean isAdmin(Long chatId) {
        return adminChatIds.contains(chatId);
    }

    private void handleAdminBroadcast(Long chatId, String text) {
        if (!isAdmin(chatId)) {
            sendMessage(chatId, "Ushbu buyruq faqat maktab ma'muri uchun mo'ljallangan.");
            return;
        }

        String[] parts = text.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            String usage = "E'lon yuborish tartibi:\n\n"
                    + "1. Butun maktabga:\n"
                    + "/elon <xabar matni>\n"
                    + "Masalan: /elon Ertaga darslar soat 08:00 da boshlanadi.\n\n"
                    + "2. Aniq bir sinfga:\n"
                    + "/elon <xabar matni> <sinf>\n"
                    + "Masalan: /elon Ertaga 3-soat dars bo'lmaydi 8-A\n"
                    + "(yoki: /elon 8-A Ertaga 3-soat dars bo'lmaydi)";
            sendMessage(chatId, usage);
            return;
        }

        String content = parts[1].trim();
        String targetClass = null;
        String announcement = content;

        // 1. Check if the last word is a valid class name (e.g. "/elon Ertaga 3-soat Kimyo bo'lmaydi 8-A")
        int lastSpaceIndex = content.lastIndexOf(' ');
        if (lastSpaceIndex != -1) {
            String lastWord = content.substring(lastSpaceIndex + 1).trim();
            String parsed = scheduleService.parseClassName(lastWord);
            if (parsed != null && scheduleService.hasClass(parsed)) {
                targetClass = parsed;
                announcement = content.substring(0, lastSpaceIndex).trim();
            }
        }

        // 2. If not found at the end, check if the first word is a class name (e.g. "/elon 8-A Ertaga 3-soat...")
        if (targetClass == null) {
            int firstSpaceIndex = content.indexOf(' ');
            if (firstSpaceIndex != -1) {
                String firstWord = content.substring(0, firstSpaceIndex).trim();
                String parsed = scheduleService.parseClassName(firstWord);
                if (parsed != null && scheduleService.hasClass(parsed)) {
                    targetClass = parsed;
                    announcement = content.substring(firstSpaceIndex + 1).trim();
                }
            }
        }

        if (announcement.isBlank()) {
            sendMessage(chatId, "E'lon matni bo'sh bo'lishi mumkin emas.");
            return;
        }

        // Filter target recipients based on class or whole school
        Map<Long, UserProfile> allProfiles = userService.getAllProfiles();
        List<Long> targetRecipients = new ArrayList<>();

        for (Map.Entry<Long, UserProfile> entry : allProfiles.entrySet()) {
            UserProfile p = entry.getValue();
            if (targetClass == null) {
                targetRecipients.add(entry.getKey());
            } else if (targetClass.equalsIgnoreCase(p.getClassName())) {
                targetRecipients.add(entry.getKey());
            }
        }

        int total = targetRecipients.size();
        if (total == 0) {
            if (targetClass != null) {
                sendMessage(chatId, targetClass + " sinfida hali hech qaysi o'quvchi ro'yxatdan o'tmagan.");
            } else {
                sendMessage(chatId, "Ro'yxatdan o'tgan foydalanuvchilar topilmadi.");
            }
            return;
        }

        String broadcastText = (targetClass != null)
                ? targetClass + " sinfi uchun e'lon!\n\n" + announcement + "\n\n— Maktab ma'muriyati"
                : "E'lon!\n\n" + announcement + "\n\n— Maktab ma'muriyati";

        String scope = (targetClass != null) ? targetClass + " sinfining " : "barcha ";
        sendMessage(chatId, "E'lon " + scope + total + " ta foydalanuvchisiga yuborilmoqda...");

        int sent = 0;
        int failed = 0;

        for (Long targetChatId : targetRecipients) {
            try {
                SendMessage sm = new SendMessage();
                sm.setChatId(targetChatId.toString());
                sm.setText(broadcastText);
                execute(sm);
                sent++;
            } catch (TelegramApiException e) {
                failed++;
                handleTelegramError(targetChatId, e);
            }
        }

        String report = "E'lon tarqatish yakunlandi.\n\n"
                + (targetClass != null ? "Sinf: " + targetClass + "\n" : "Qamrov: Butun maktab\n")
                + "Yetkazildi: " + sent + " ta\n"
                + "Yetib bormadi (bot bloklangan): " + failed + " ta\n"
                + "Jami: " + total + " ta";
        sendMessage(chatId, report);
    }

    private void handleAdminStats(Long chatId) {
        if (!isAdmin(chatId)) {
            sendMessage(chatId, "Ushbu buyruq faqat maktab ma'muri uchun mo'ljallangan.");
            return;
        }

        Map<Long, UserProfile> allProfiles = userService.getAllProfiles();
        int total = allProfiles.size();

        int morningCount = 0;
        int afternoonCount = 0;
        int bellEnabledCount = 0;
        int eveningReminderCount = 0;
        Map<String, Integer> classCounts = new TreeMap<>();

        for (UserProfile p : allProfiles.values()) {
            if ("afternoon".equalsIgnoreCase(p.getShift())) {
                afternoonCount++;
            } else {
                morningCount++;
            }
            if (p.isBellNotifications()) {
                bellEnabledCount++;
            }
            if (p.isEveningReminder()) {
                eveningReminderCount++;
            }
            String c = p.getClassName() != null ? p.getClassName() : "Noma'lum";
            classCounts.put(c, classCounts.getOrDefault(c, 0) + 1);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("8-Maktab Boti Statistikasi:\n\n");
        sb.append("Jami foydalanuvchilar: ").append(total).append(" ta\n");

        if (total > 0) {
            int morningPct = (int) Math.round((morningCount * 100.0) / total);
            int afternoonPct = (int) Math.round((afternoonCount * 100.0) / total);
            int bellPct = (int) Math.round((bellEnabledCount * 100.0) / total);
            int eveningPct = (int) Math.round((eveningReminderCount * 100.0) / total);

            sb.append("• 1-smena: ").append(morningCount).append(" ta (").append(morningPct).append("%)\n");
            sb.append("• 2-smena: ").append(afternoonCount).append(" ta (").append(afternoonPct).append("%)\n");
            sb.append("• Qo'ng'iroq bildirishnomalari: ").append(bellEnabledCount).append(" ta (").append(bellPct).append("%)\n");
            sb.append("• Kechki eslatmalar yoqilgan: ").append(eveningReminderCount).append(" ta (").append(eveningPct).append("%)\n\n");

            sb.append("Sinflar bo'yicha taqsimot:\n");
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(classCounts.entrySet());
            sorted.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

            for (Map.Entry<String, Integer> entry : sorted) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" ta\n");
            }
        }

        sendMessage(chatId, sb.toString());
    }

    private void handleHelpCommand(Long chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Buyruqlar ro'yxati:\n\n")
                .append("/jadval - Dars jadvalini ko'rish\n")
                .append("/hozir - Hozirgi dars holati va vaqt hisobi\n")
                .append("/sumka - Ertangi darslar va sumka anjomlari\n")
                .append("/eslatma - Ertangi kun kechki eslatmasini olish\n")
                .append("/bugun - Bugungi darslar\n")
                .append("/ertaga - Ertangi darslar\n")
                .append("/hafta - Haftalik to'liq jadval\n")
                .append("/sinf - Joriy sinf va smena ma'lumoti\n")
                .append("/register - Sinf va smenani tanlash\n")
                .append("/vaqtlar - Qo'ng'iroqlar va dars vaqtlari\n")
                .append("/sozlamalar - Profil va bildirishnomalarni sozlash\n")
                .append("/myid - Telegram ID raqamingiz\n")
                .append("/yordam - Ushbu yordam xabari\n\n")
                .append("💡 Inline rejim:\n")
                .append("Istalgan chatda yoki guruhda @").append(botUsername.replace("@", "")).append(" 8-A deb yozib, dars jadvalini to'g'ridan-to'g'ri ulashishingiz mumkin!");

        if (isAdmin(chatId)) {
            sb.append("\n\nMa'mur buyruqlari:\n")
                    .append("/elon <matn> [sinf] - Maktab yoki aniq bir sinfga e'lon yuborish\n")
                    .append("/statistika - Bot statistikasi");
        }

        sendMessage(chatId, sb.toString());
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

            // By default, evening reminder is enabled (true)
            UserProfile profile = new UserProfile(className, shift, bells, true);
            userService.saveProfile(chatId, profile);

            String breakInfo = "afternoon".equalsIgnoreCase(shift)
                    ? "Darslar: 40 daqiqa (Katta tanaffus yo'q)."
                    : "Katta tanaffus: 5-soatdan keyin 20 daqiqa.";

            String text = "Ro'yxatdan o'tish yakunlandi.\n\n"
                    + "Sinf: " + className + "\n"
                    + "Smena: " + profile.getShiftDisplayName() + "\n"
                    + "Qo'ng'iroq bildirishnomalari: " + (bells ? "Yoqilgan" : "O'chirilgan") + "\n"
                    + "Kechki eslatma (19:00 - 20:00): Yoqilgan\n\n"
                    + breakInfo;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            rows.add(List.of(
                    createButton("Bugungi jadval", "sch_day:today"),
                    createButton("Hozirgi holat", "sch_live")
            ));
            rows.add(List.of(
                    createButton("Sumka", "sch_sumka:tomorrow"),
                    createButton("Dars vaqtlari", "vaqtlar_shift:" + shift)
            ));
            rows.add(List.of(createButton("Sozlamalar", "open_settings")));
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
        } else if (data.equals("live_refresh") || data.equals("sch_live")) {
            handleLiveStatusCommand(chatId, messageId);
        } else if (data.startsWith("sumka_day:")) {
            String dayOption = data.substring("sumka_day:".length());
            handleBackpackCommand(chatId, messageId, dayOption);
        } else if (data.startsWith("sch_sumka:")) {
            String dayOption = data.substring("sch_sumka:".length());
            handleBackpackCommand(chatId, messageId, dayOption);
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
        } else if (data.equals("toggle_evening")) {
            userService.toggleEveningReminder(chatId);
            sendSettingsMessage(chatId, messageId);
        }
    }

    private void sendCategorySelection(Long chatId, Integer editMessageId) {
        String text = "Sinf toifasini tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("5-8 sinflar", "reg_group:5-8")));
        rows.add(List.of(createButton("9-11 sinflar", "reg_group:9-11")));
        rows.add(List.of(createButton("1-4 boshlang'ich (Tez kunda)", "reg_group:1-4")));

        markup.setKeyboard(rows);

        if (editMessageId != null) {
            editMessage(chatId, editMessageId, text, markup);
        } else {
            sendMessage(chatId, text, markup);
        }
    }

    private void sendGradeSelection(Long chatId, Integer editMessageId, String group) {
        if ("1-4".equals(group)) {
            String text = "Boshlang'ich (1-4) sinflar dars jadvali hali maktab tomonidan taqdim etilmagan.\n\n"
                    + "Tez kunda kiritiladi. Hozircha 5-11 sinflar mavjud.";
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(createButton("Orqaga", "reg_group:back"))));
            editMessage(chatId, editMessageId, text, markup);
            return;
        }

        String text = group + " sinflar bo'yicha sinfingizni tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if ("5-8".equals(group)) {
            rows.add(List.of(
                    createButton("5-A", "reg_class:5-A"),
                    createButton("5-B", "reg_class:5-B"),
                    createButton("5-D", "reg_class:5-D")
            ));
            rows.add(List.of(
                    createButton("6-A", "reg_class:6-A"),
                    createButton("6-B", "reg_class:6-B")
            ));
            rows.add(List.of(
                    createButton("7-A", "reg_class:7-A"),
                    createButton("7-B", "reg_class:7-B"),
                    createButton("7-D", "reg_class:7-D")
            ));
            rows.add(List.of(
                    createButton("8-A", "reg_class:8-A"),
                    createButton("8-B", "reg_class:8-B"),
                    createButton("8-D", "reg_class:8-D")
            ));
        } else if ("9-11".equals(group)) {
            rows.add(List.of(
                    createButton("9-A", "reg_class:9-A"),
                    createButton("9-B", "reg_class:9-B"),
                    createButton("9-D", "reg_class:9-D")
            ));
            rows.add(List.of(
                    createButton("10-A", "reg_class:10-A"),
                    createButton("10-B", "reg_class:10-B"),
                    createButton("10-D", "reg_class:10-D")
            ));
            rows.add(List.of(
                    createButton("11-A", "reg_class:11-A"),
                    createButton("11-B", "reg_class:11-B"),
                    createButton("11-D", "reg_class:11-D")
            ));
        }

        rows.add(List.of(createButton("Orqaga", "reg_group:back")));
        markup.setKeyboard(rows);

        editMessage(chatId, editMessageId, text, markup);
    }

    private void sendShiftSelection(Long chatId, Integer editMessageId, String className) {
        String text = "Tanlangan sinf: " + className + "\n\nQaysi smenada o'qiysiz?";

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

        // Navigation 1: Today, Tomorrow, Week
        rows.add(List.of(
                createButton("Bugun", "sch_day:today"),
                createButton("Ertaga", "sch_day:tomorrow"),
                createButton("Haftalik", "sch_day:week")
        ));

        // Navigation 2: Days of week
        rows.add(List.of(
                createButton("Dush", "sch_day:MONDAY"),
                createButton("Sesh", "sch_day:TUESDAY"),
                createButton("Chor", "sch_day:WEDNESDAY"),
                createButton("Pay", "sch_day:THURSDAY"),
                createButton("Juma", "sch_day:FRIDAY"),
                createButton("Shan", "sch_day:SATURDAY")
        ));

        // Navigation 3: Live & Backpack
        rows.add(List.of(
                createButton("Hozirgi dars", "sch_live"),
                createButton("Sumka", "sch_sumka:tomorrow")
        ));

        // Navigation 4: Bells & Settings
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
                + "Qo'ng'iroq bildirishnomalari: " + (profile.isBellNotifications() ? "Yoqilgan" : "O'chirilgan") + "\n"
                + "Kechki eslatma (19:00 - 20:00): " + (profile.isEveningReminder() ? "Yoqilgan" : "O'chirilgan");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(createButton("Sinfni o'zgartirish", "reg_group:back")));
        rows.add(List.of(createButton("Smenani almashtirish", "toggle_shift")));
        rows.add(List.of(createButton(
                profile.isBellNotifications() ? "Qo'ng'iroq: O'chirish" : "Qo'ng'iroq: Yoqish",
                "toggle_bell"
        )));
        rows.add(List.of(createButton(
                profile.isEveningReminder() ? "Kechki eslatma: O'chirish" : "Kechki eslatma: Yoqish",
                "toggle_evening"
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
