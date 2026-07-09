package com.agentdock.ui

internal class StandardToolWindowLayout(
    private val uiSettingsAccess: () -> UiSettingsAccess? = { ReflectiveUiSettingsAccess.load() }
) {
    fun enable() {
        val access = uiSettingsAccess() ?: return
        if (access.wideScreenSupport) {
            access.wideScreenSupport = false
            access.fireChanged()
        }
    }
}

internal interface UiSettingsAccess {
    var wideScreenSupport: Boolean
    fun fireChanged()
}

private class ReflectiveUiSettingsAccess(
    private val settings: Any,
    private val settingsClass: Class<*>
) : UiSettingsAccess {
    override var wideScreenSupport: Boolean
        get() = settingsClass.getMethod("getWideScreenSupport").invoke(settings) as Boolean
        set(value) {
            settingsClass.getMethod("setWideScreenSupport", Boolean::class.javaPrimitiveType).invoke(settings, value)
        }

    override fun fireChanged() {
        settingsClass.getMethod("fireUISettingsChanged").invoke(settings)
    }

    companion object {
        private const val UI_SETTINGS_CLASS = "com.intellij.ide.ui.UISettings"

        fun load(): UiSettingsAccess? {
            return runCatching {
                val settingsClass = Class.forName(UI_SETTINGS_CLASS)
                val settings = settingsClass.getMethod("getInstance").invoke(null) ?: return null
                ReflectiveUiSettingsAccess(settings, settingsClass)
            }.getOrNull()
        }
    }
}
