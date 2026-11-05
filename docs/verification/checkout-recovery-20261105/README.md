# Checkout/deployment recovery — 2026-11-05

At diagnosis, GitHub main and the canonical application sources were both a6f4e4e. The recovery changes add build configuration, provenance and guidance; game code is unchanged. The apparent rollback came from the retired checkout at 0e95a93: Android Studio built/deployed that copy using the same application ID, replacing the current device app. Before changing the app, its installed base APK was pulled and compared against the retired checkout's IDE intermediate APK. The complete APK bytes and SHA-256 match. Raw APKs and IDE logs are kept in ignored build/rollback-audit-20261105, not committed.

Missing from the retired checkout:

- 9d0aeb3: secondary activation fixes, Chopper flight slots, Hunter corrections, typed-only reward selection and caption overlap priorities.
- f1d161c: Shield Drone, Shooter/missiles, Warder/barrier and Splitter, plus reference-traced visuals.
- b20c4b2: continuous faster Drone tier progression without the unimplemented boss gate.
- fb51270: start / coming soon... / keyboard test / quit and viable Japanese trigger groups.
- 97488cf and a6f4e4e: weapon activation description/reference wording.

Weapon evolution and area-size changes predate the retired checkout and were not lost from that build. No canonical application source was reset or restored during this recovery.

The retired checkout's original settings file was backed up before adding an unconditional settings-stage GradleException explaining the canonical location. Its sources and Git/Jujutsu history remain intact. Running its normal :app:assembleDebug command now exits 1 with that exact guidance, before application tasks can execute. A PROJECT_MOVED.txt notice identifies it as a reference copy. The routed development workflow and project AGENTS now require verifying IDE/build/deployment provenance when relocating a checkout.

The IDE now opens the canonical project. Its initial sync failed with the combined missing-directory/unwritable-directory error. The directory existed in both JDKs, but a direct Java probe reported File.canWrite() = false under Studio's JBR 25 and true under the installed Temurin 25, for both mapped and UNC paths. No ACL or security policy was changed. The project daemon criteria now select ADOPTIUM 25, and the existing mise JDK was added to the user's local Gradle discovery paths (original user properties backed up in ignored build/rollback-audit-20261105). updateDaemonJvm regenerated the vendor-specific download metadata; no JDK installation was required.

A build launched with Studio's JBR client then selected a Temurin daemon and succeeded offline. Android Studio's actual Gradle sync also succeeded (11-second Gradle result / 13-second sync log), and the app run configuration appeared. CLI-only success is not used as proof of IDE sync. The complete APK SHA-256 remains unchanged from the preserved canonical build. The physical device disconnected after the old installed APK was captured; its latest-app reinstall/hash verification remains pending USB reconnection. No emulator deployment is counted as physical recovery.
