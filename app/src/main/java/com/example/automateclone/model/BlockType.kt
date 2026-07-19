package com.example.automateclone.model

/**
 * Category of a block. TRIGGER blocks start a flow, ACTION blocks perform work,
 * LOGIC blocks branch/merge execution (e.g. "if", "wait").
 */
enum class BlockCategory { TRIGGER, ACTION, LOGIC }

/**
 * Every block "type" the palette can offer. Each has a fixed category and a
 * set of config fields the editor renders (see Block.config map).
 */
enum class BlockType(
    val category: BlockCategory,
    val displayName: String,
    val configKeys: List<String> = emptyList()
) {
    // --- Triggers ---
    TIME_SCHEDULE(BlockCategory.TRIGGER, "Time Schedule", listOf("hour", "minute", "repeatDays")),
    BATTERY_LEVEL(BlockCategory.TRIGGER, "Battery Level", listOf("threshold", "direction")),
    DEVICE_CHARGING(BlockCategory.TRIGGER, "Charging State", listOf("state")),
    SCREEN_STATE(BlockCategory.TRIGGER, "Screen On/Off", listOf("state")),

    // --- Actions ---
    SEND_SMS(BlockCategory.ACTION, "Send SMS", listOf("phoneNumber", "message")),
    SHOW_NOTIFICATION(BlockCategory.ACTION, "Show Notification", listOf("title", "text")),
    SHOW_TOAST(BlockCategory.ACTION, "Show Toast", listOf("text")),
    VIBRATE(BlockCategory.ACTION, "Vibrate", listOf("durationMs")),
    LAUNCH_APP(BlockCategory.ACTION, "Launch App", listOf("packageName")),
    SET_VOLUME(BlockCategory.ACTION, "Set Volume", listOf("streamType", "level")),

    // --- Logic ---
    WAIT(BlockCategory.LOGIC, "Wait", listOf("durationMs")),
    IF_CONDITION(BlockCategory.LOGIC, "If", listOf("variable", "operator", "value"))
}
