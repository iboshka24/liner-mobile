-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.**
-dontwarn com.google.errorprone.annotations.**

-keepclassmembers class dev.lyra.player.model.** {
    *** Companion;
}
-keepclasseswithmembers class dev.lyra.player.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.lyra.player.**$$serializer { *; }
-keepclassmembers class dev.lyra.player.** {
    *** Companion;
}
