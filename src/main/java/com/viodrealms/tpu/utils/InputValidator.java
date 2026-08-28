package com.viodrealms.tpu.utils;

public class InputValidator {
    public static final int MAX_NAME_LENGTH = 32;

    public static String sanitizeName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceAll("\\s+", " ");
    }

    public static boolean isValidWaypointName(String rawName) {
        return isValidWaypointName(rawName, MAX_NAME_LENGTH);
    }

    public static boolean isValidWaypointName(String rawName, int maxLength) {
        String name = sanitizeName(rawName);
        if (name.isEmpty() || name.length() > maxLength) {
            return false;
        }
        return name.matches("[A-Za-z0-9_\u0600-\u06FF .-]+") && !name.matches("[_.-]{1,}");
    }
}
