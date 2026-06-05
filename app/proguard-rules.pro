-keep class com.ryzix.vm.qemu.** { *; }
-keep class com.ryzix.vm.vnc.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn java.awt.**
-dontwarn javax.swing.**
