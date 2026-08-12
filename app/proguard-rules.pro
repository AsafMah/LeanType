# Keep classes that contain native methods
-keep class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# Gemini SDK dependencies
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep Gemini API classes
-keep class com.google.ai.client.generativeai.** { *; }

-keep class helium314.keyboard.latin.utils.ProofreadHelper { *; }
-keep class helium314.keyboard.latin.utils.ProofreadHelper$* { *; }

# Keep java-llama.cpp classes
-keep class de.kherud.llama.** { *; }
-keep class org.nehuatl.llamacpp.** { *; }



# Fix correct service name
-keep class helium314.keyboard.latin.utils.ProofreadService { *; }

# Suppress warnings for missing library dependencies in R8 Full Mode
-dontwarn com.google.api.client.**
-dontwarn java.lang.management.**
-dontwarn org.joda.time.**

# Keep offline voice plugin AIDL interface and parcelable classes
-keep class com.leanbitlab.leantype.voice.** { *; }
-keep interface com.leanbitlab.leantype.voice.** { *; }

-keep interface helium314.keyboard.latin.handwriting.HandwritingRecognizer {
    <methods>;
}
-keep interface helium314.keyboard.latin.handwriting.ModelDownloadListener {
    <methods>;
}

# Keep translation plugin interface to prevent parameter removal/signature optimization
-keep interface helium314.keyboard.latin.translation.ITranslationProvider {
    <methods>;
}

# Keep ML Kit, GMS Tasks, and Firebase components for handwriting plugin dynamic linkage
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.firebase.components.** { *; }

# Keep Kotlin standard library for dynamically loaded plugins
# ponytail: keep kotlin stdlib classes to prevent NoSuchMethodError in plugin loading
-keep class kotlin.** { *; }

