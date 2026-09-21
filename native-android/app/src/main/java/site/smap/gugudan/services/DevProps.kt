package site.smap.gugudan.services

import site.smap.gugudan.BuildConfig

// DEBUG 전용 QA 훅 — adb shell setprop debug.gugu.* 로 주입 (릴리스에서는 항상 null)
object DevProps {
    fun get(key: String): String? {
        if (!BuildConfig.DEBUG) return null
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            (cls.getMethod("get", String::class.java).invoke(null, key) as? String)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    fun flag(key: String): Boolean = get(key) == "1"
}
