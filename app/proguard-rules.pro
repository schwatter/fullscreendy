# Eclipse Paho MQTT
-keep class org.eclipse.paho.** { *; }
-dontwarn org.eclipse.paho.**

# CameraX
-keep class androidx.camera.** { *; }

# App-Komponenten (per Manifest/Reflection referenziert)
-keep class de.kewl.fullscreendy.AdminReceiver { *; }
-keep class de.kewl.fullscreendy.BootReceiver { *; }
-keep class de.kewl.fullscreendy.FullScreendyApp { *; }
-keep class de.kewl.fullscreendy.MainActivity { *; }
-keep class de.kewl.fullscreendy.service.KioskService { *; }
