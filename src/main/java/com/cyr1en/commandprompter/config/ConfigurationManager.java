package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.CommandPrompter;
import com.cyr1en.commandprompter.config.annotations.field.ConfigNode;
import com.cyr1en.commandprompter.config.annotations.field.NodeComment;
import com.cyr1en.commandprompter.config.annotations.field.NodeDefault;
import com.cyr1en.commandprompter.config.annotations.field.NodeName;
import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;
import com.cyr1en.kiso.mc.configuration.base.Config;
import com.cyr1en.kiso.mc.configuration.base.ConfigManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class that manages your plugin's configuration(s)
 *
 * <p>
 * This class will turn a configuration record into a YAML configuration file.
 * For this class to work, a record must be:
 * - Annotated with {@link Configuration}.
 * - Fields of the record must be annotated with {@link ConfigNode}
 *
 * <p>
 * Reflection is heavily used in this class to grab the types of each field in a
 * record and initializes the defaults for the config file. It then uses reflection
 * again to query those default values in the config file to instantiate the actual
 * record with its respective data.
 */
public class ConfigurationManager {

    private final ConfigManager configManager;

    public ConfigurationManager(CommandPrompter plugin) {
        this.configManager = new ConfigManager(plugin);
    }

    public <T> T getConfig(Class<T> configClass) {
        if (configClass.getAnnotation(Configuration.class) == null)
            return null;

        Config config = initConfigFile(configClass);

        initializeConfig(configClass, config);

        try {
            Object recordConfig = configClass.getDeclaredConstructors()[0].newInstance();
            @SuppressWarnings("unchecked") T out = (T) recordConfig;
            configClass.getDeclaredField("rawConfig").set(out, config);
            for (Field declaredField : configClass.getDeclaredFields()) {
                if (declaredField.getAnnotation(ConfigNode.class) == null) continue;
                Object value;
                NodeName nameAnnotation = declaredField.getAnnotation(NodeName.class);
                if (declaredField.getType().equals(int.class))
                    value = config.getInt(nameAnnotation.value());
                else if (declaredField.getType().equals(boolean.class))
                    value = config.getBoolean(nameAnnotation.value());
                else if (declaredField.getType().equals(double.class))
                    value = config.getDouble(nameAnnotation.value());
                else if (declaredField.getType().equals(List.class))
                    value = config.getList(nameAnnotation.value());
                else value = config.getString(nameAnnotation.value());
                declaredField.set(out, value);
            }

            return out;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }

    public <T> T reload(Class<T> configClass) {
        return getConfig(configClass);
    }

    private void initializeConfig(Class<?> configClass, Config config) {
        Field[] fields = configClass.getDeclaredFields();
        for (Field field : fields)
            initializeField(field, config);
    }

    private Config initConfigFile(Class<?> configClass) {
        ConfigPath pathAnnotation = configClass.getAnnotation(ConfigPath.class);
        String filePath = pathAnnotation == null ? configClass.getSimpleName() : pathAnnotation.value();

        ConfigHeader headerAnnotation = configClass.getAnnotation(ConfigHeader.class);
        String[] header = headerAnnotation == null ? new String[] { configClass.getSimpleName(), "Configuration"} :
                headerAnnotation.value();
        return configManager.getNewConfig(filePath, header);
    }

    private void initializeField(Field field, Config config) {
        if (field.getAnnotation(ConfigNode.class) == null) return;

        NodeName nameAnnotation = field.getAnnotation(NodeName.class);
        String nodeName = nameAnnotation == null ? field.getName() : nameAnnotation.value();

        NodeDefault defaultAnnotation = field.getAnnotation(NodeDefault.class);
        Object nodeDefault = defaultAnnotation == null ? constructDefaultField(field) : parseDefault(field);

        NodeComment commentAnnotation = field.getAnnotation(NodeComment.class);
        String[] nodeComment = commentAnnotation == null ? new String[]{} : commentAnnotation.value();

        if (config.get(nodeName) != null) return;
        config.set(nodeName, nodeDefault, nodeComment);
        config.saveConfig();
    }

    private Object constructDefaultField(Field f) {
        try {
            if (f.getType().isPrimitive()) {
                if (f.getType().equals(int.class))
                    return 0;
                if (f.getType().equals(boolean.class))
                    return false;
                if (f.getType().equals(double.class))
                    return 0.0;
                if (f.getType().equals(List.class))
                    return new ArrayList<>();
            }
            return f.getType().getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException | InvocationTargetException |
                InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object parseDefault(Field field) {
        NodeDefault defaultAnnotation = field.getAnnotation(NodeDefault.class);
        if (field.getType().equals(int.class))
            return Integer.valueOf(defaultAnnotation.value());
        if (field.getType().equals(boolean.class))
            return Boolean.valueOf(defaultAnnotation.value());
        if (field.getType().equals(double.class))
            return Double.valueOf(defaultAnnotation.value());
        if (field.getType().equals(List.class))
            return Arrays.stream(defaultAnnotation.value().split(",\\s+")).collect(Collectors.toList());
        return defaultAnnotation.value();
    }
}
