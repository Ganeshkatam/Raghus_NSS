package edu.college.nss.domain;

public enum EventType {
    SERVICE,
    SPECIAL_CAMP,
    COMMUNITY_OUTREACH,
    BLOOD_DONATION,
    TRAINING_WORKSHOP,
    ORIENTATION,
    AWARENESS,
    AWARENESS_RALLY,
    ENVIRONMENTAL_DRIVE,
    CAMPUS_CLEANING,
    CAMPUS_DRIVE,
    NATIONAL_FESTIVAL,
    OTHER;

    public static boolean isValid(String type) {
        if (type == null || type.isBlank()) return false;
        try {
            valueOf(type.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String normalize(String type) {
        if (type == null || type.isBlank()) {
            return SERVICE.name();
        }
        return valueOf(type.trim().toUpperCase()).name();
    }
}
