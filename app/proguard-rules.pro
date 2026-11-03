# Rules for the optimized build. Compose and the backdrop library bring their own consumer rules.
# Resources are looked up by name for the built-in alarm sounds, and resource shrinking is off, so nothing else needs keeping.
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
