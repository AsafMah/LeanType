private val modules = listOf(
    SettingsModule(
        SettingsWithoutKey.SCREEN_NAV_TWO_THUMB_TYPING,
        SettingsDestination.TwoThumbTyping,
        provider = ::createTwoThumbTypingSettings,
    ),
)

object SettingsWithoutKey {
    const val SCREEN_NAV_TWO_THUMB_TYPING = "screen_nav_two_thumb_typing"
}
