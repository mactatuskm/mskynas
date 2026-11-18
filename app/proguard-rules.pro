# Modelos de datos: se leen y guardan como JSON (kotlinx.serialization). Se conservan completos
# para que R8 no les cambie nombres ni quite campos.
-keep class com.mskynas.tv.data.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Conscrypt: avisos de clases opcionales que no se usan en Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
