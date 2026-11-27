package org.paper;

import java.io.File;

import org.paper.bot.SchoolBot;
import org.paper.service.BellNotificationService;
import org.paper.service.ScheduleService;
import org.paper.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final File REGISTRATIONS_FILE = new File("data/registrations.json");
    private static final File SCHEDULES_FILE = new File("data/schedules.json");

    public static void main(String[] args) {
        // Load configuration from OS environment variables first, then fallback to .env if present
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        if (botToken == null || botToken.isBlank()) {
            botToken = dotenv.get("TELEGRAM_BOT_TOKEN");
        }

        String botUsername = System.getenv("TELEGRAM_BOT_USERNAME");
        if (botUsername == null || botUsername.isBlank()) {
            botUsername = dotenv.get("TELEGRAM_BOT_USERNAME", "@sakkizmaktabni_bot");
        }

        if (botToken == null || botToken.isBlank()) {
            log.error("TELEGRAM_BOT_TOKEN topilmadi! .env faylini yoki tizim muhit o'zgaruvchilarini tekshiring.");
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        UserService userService = new UserService(REGISTRATIONS_FILE, objectMapper);
        ScheduleService scheduleService = new ScheduleService(SCHEDULES_FILE, objectMapper);

        SchoolBot bot = new SchoolBot(botToken, botUsername, userService, scheduleService);

        BellNotificationService bellNotificationService =
                new BellNotificationService(userService, bot::sendDirectMessage);

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);

            // Register Telegram slash command menu
            bot.registerCommands();

            // Start bell scheduler
            bellNotificationService.start();

            log.info("8-Maktab boti muvaffaqiyatli ishga tushdi. Ro'yxatdan o'tgan foydalanuvchilar: {}", userService.getUserCount());

            // Graceful shutdown on JVM termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Bot to'xtatilmoqda...");
                bellNotificationService.stop();
            }));

            // Start lightweight HTTP health-check server if PORT is set (Render, Railway, Heroku)
            startHealthCheckServer();

        } catch (TelegramApiException e) {
            log.error("Botni ro'yxatdan o'tkazishda xatolik yuz berdi", e);
        }
    }

    private static void startHealthCheckServer() {
        String portStr = System.getenv("PORT");
        if (portStr == null || portStr.isBlank()) {
            return;
        }

        try {
            int port = Integer.parseInt(portStr.trim());
            com.sun.net.httpserver.HttpServer server =
                    com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                String response = "8-Maktab Telegram Bot is running.";
                byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });
            server.setExecutor(null);
            server.start();
            log.info("Cloud health-check server port {} da ishga tushdi.", port);
        } catch (Exception e) {
            log.warn("Health-check serverini ishga tushirishda ogohlantirish: {}", e.getMessage());
        }
    }
}
