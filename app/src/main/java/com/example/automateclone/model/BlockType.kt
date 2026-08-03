package com.example.automateclone.model

enum class BlockCategory { TRIGGER, ACTION, LOGIC }

enum class BlockType(
    val category: BlockCategory,
    val displayName: String,
    val configKeys: List<String> = emptyList()
) {
    TIME_SCHEDULE(BlockCategory.TRIGGER, "Time Schedule", listOf("hour", "minute", "repeatDays")),
    BATTERY_LEVEL(BlockCategory.TRIGGER, "Battery Level", listOf("threshold", "direction")),
    DEVICE_CHARGING(BlockCategory.TRIGGER, "Charging State", listOf("state")),
    SCREEN_STATE(BlockCategory.TRIGGER, "Screen On/Off", listOf("state")),

    SHOW_NOTIFICATION(BlockCategory.ACTION, "Show Notification", listOf("title", "text", "colorHex")),
    SHOW_TOAST(BlockCategory.ACTION, "Show Toast", listOf("text")),
    VIBRATE(BlockCategory.ACTION, "Vibrate", listOf("durationMs")),
    LAUNCH_APP(BlockCategory.ACTION, "Launch App", listOf("packageName")),
    SET_VOLUME(BlockCategory.ACTION, "Set Volume", listOf("streamType", "level")),
    SIMULATED_TAP(BlockCategory.ACTION, "Simulated Tap", listOf("x", "y")),
    SET_WALLPAPER(BlockCategory.ACTION, "Set Wallpaper Color", listOf("colorHex")),
    COPY_TO_CLIPBOARD(BlockCategory.ACTION, "Copy to Clipboard", listOf("text")),

    WAIT(BlockCategory.LOGIC, "Wait", listOf("durationMs")),
    IF_CONDITION(BlockCategory.LOGIC, "If", listOf("variable", "operator", "value"))
}
