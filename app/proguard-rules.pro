# Модели состояния сериализуются kotlinx.serialization — генерируемые
# сериализаторы и их Companion должны пережить сжатие.
-keepclassmembers class com.tihon.productivnost.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.tihon.productivnost.core.**$$serializer { *; }
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.tihon.productivnost.core.** { *; }
-dontwarn kotlinx.serialization.**

# Сервис, ресивер и активити поднимаются системой по имени из манифеста.
-keep class com.tihon.productivnost.service.ProductivityService { *; }
-keep class com.tihon.productivnost.service.BootReceiver { *; }
-keep class com.tihon.productivnost.ProductivnostApp { *; }
