package org.paper.service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.paper.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final File storageFile;
    private final ObjectMapper objectMapper;
    private final Map<Long, UserProfile> cache = new ConcurrentHashMap<>();

    public UserService(File storageFile, ObjectMapper objectMapper) {
        this.storageFile = storageFile;
        this.objectMapper = objectMapper;
        loadAll();
    }

    private synchronized void loadAll() {
        if (!storageFile.exists()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(storageFile);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(entry -> {
                    try {
                        Long userId = Long.parseLong(entry.getKey());
                        JsonNode val = entry.getValue();
                        if (val.isTextual()) {
                            cache.put(userId, new UserProfile(val.asText(), "morning", false, true));
                        } else if (val.isObject()) {
                            String className = val.path("className").asText(null);
                            String shift = val.path("shift").asText("morning");
                            boolean bells = val.path("bellNotifications").asBoolean(false);
                            boolean evening = val.path("eveningReminder").asBoolean(true);
                            if (className != null && !className.isBlank()) {
                                cache.put(userId, new UserProfile(className, shift, bells, evening));
                            }
                        }
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
        } catch (IOException e) {
            log.error("Foydalanuvchilar ma'lumotlarini o'qishda xatolik", e);
        }
    }

    public synchronized void saveProfile(Long userId, UserProfile profile) {
        cache.put(userId, profile);
        persistToFile();
    }

    public UserProfile getProfile(Long userId) {
        return cache.get(userId);
    }

    public Map<Long, UserProfile> getAllProfiles() {
        return new HashMap<>(cache);
    }

    public int getUserCount() {
        return cache.size();
    }

    public synchronized void toggleBellNotifications(Long userId) {
        UserProfile profile = cache.get(userId);
        if (profile != null) {
            profile.setBellNotifications(!profile.isBellNotifications());
            persistToFile();
        }
    }

    public synchronized void disableBellNotifications(Long userId) {
        UserProfile profile = cache.get(userId);
        if (profile != null && profile.isBellNotifications()) {
            profile.setBellNotifications(false);
            persistToFile();
        }
    }

    public synchronized void toggleEveningReminder(Long userId) {
        UserProfile profile = cache.get(userId);
        if (profile != null) {
            profile.setEveningReminder(!profile.isEveningReminder());
            persistToFile();
        }
    }

    public synchronized void toggleShift(Long userId) {
        UserProfile profile = cache.get(userId);
        if (profile != null) {
            String newShift = "morning".equalsIgnoreCase(profile.getShift()) ? "afternoon" : "morning";
            profile.setShift(newShift);
            persistToFile();
        }
    }

    public synchronized void deleteProfile(Long userId) {
        if (cache.remove(userId) != null) {
            persistToFile();
        }
    }

    private synchronized void persistToFile() {
        try {
            File parent = storageFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            ObjectNode root = objectMapper.createObjectNode();
            for (Map.Entry<Long, UserProfile> entry : cache.entrySet()) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("className", entry.getValue().getClassName());
                node.put("shift", entry.getValue().getShift());
                node.put("bellNotifications", entry.getValue().isBellNotifications());
                node.put("eveningReminder", entry.getValue().isEveningReminder());
                root.set(String.valueOf(entry.getKey()), node);
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storageFile, root);
        } catch (IOException e) {
            log.error("Foydalanuvchilar ma'lumotlarini saqlashda xatolik", e);
        }
    }
}
