-keep class com.pckeyboard.ime.** { *; }
-keepclassmembers class * extends android.inputmethodservice.InputMethodService { *; }

# Lucene's Hunspell: loaded via straight API calls, no reflection on our
# side, but keep the hunspell package intact and silence warnings about
# optional / newer-JDK codepaths lucene-core references but never touches
# on the spell-checking path.
-keep class org.apache.lucene.analysis.hunspell.** { *; }
-dontwarn org.apache.lucene.**
-dontwarn java.lang.invoke.**
-dontwarn jdk.**
-dontwarn sun.misc.**
