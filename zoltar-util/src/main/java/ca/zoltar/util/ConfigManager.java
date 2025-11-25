package ca.zoltar.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String APP_DIR_NAME = ".zoltar-java";
    
    private static ConfigManager instance;
    private final Path configPath;
    private Map<String, Object> config;
    private final ObjectMapper mapper;

    private ConfigManager() {
        String userHome = System.getProperty("user.home");
        Path appDir = Paths.get(userHome, APP_DIR_NAME);
        this.configPath = appDir.resolve(CONFIG_FILE_NAME);
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.config = new HashMap<>();
        
        ensureAppDirExists(appDir);
        loadConfig();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void ensureAppDirExists(Path appDir) {
        if (!Files.exists(appDir)) {
            try {
                Files.createDirectories(appDir);
                logger.info("Created application directory: {}", appDir);
            } catch (IOException e) {
                logger.error("Failed to create application directory", e);
            }
        }
    }

    private void loadConfig() {
        if (Files.exists(configPath)) {
            try {
                config = mapper.readValue(configPath.toFile(), new TypeReference<Map<String, Object>>() {});
                logger.info("Loaded configuration from {}", configPath);
            } catch (IOException e) {
                logger.error("Failed to load configuration", e);
            }
        } else {
            logger.info("No configuration file found at {}, using defaults.", configPath);
            saveConfig(); // Save defaults
        }
    }

    public void saveConfig() {
        try {
            mapper.writeValue(configPath.toFile(), config);
            logger.info("Saved configuration to {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to save configuration", e);
        }
    }

    public Object get(String key) {
        return config.get(key);
    }

    public void set(String key, Object value) {
        config.put(key, value);
    }
    
    public Path getAppDir() {
        return configPath.getParent();
    }
}
