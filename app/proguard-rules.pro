# R8 rules for NightTorch.
#
# Deliberately short. Compose, DataStore and kotlinx.coroutines all ship their own consumer
# rules inside their AARs, so the library side needs nothing from us. What follows covers
# only the places where *this* app is reached by name rather than by a call R8 can see.

# The accessibility service is instantiated by the system from the name in the manifest, so
# nothing in the app references it and R8 would otherwise be free to remove or rename it.
# Losing this class silently disables the app's entire reason for existing.
-keep class com.bordware.nighttorch.service.FlashlightAccessibilityService { *; }

# Same reasoning for the Application and the launcher Activity.
-keep class com.bordware.nighttorch.NightTorchApp { *; }
-keep class com.bordware.nighttorch.ui.MainActivity { *; }

# Keep line numbers in stack traces, and hide the original source file name. Without the
# first, a crash report from a user is unreadable; without the second, the file names leak
# for no benefit.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
