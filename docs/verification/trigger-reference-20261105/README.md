# Weapon trigger spelling reference — 2026-11-05

Acquisition and extra-trigger evolution descriptions explicitly refer to the Roman spelling used by one-stroke input mode. The fixed dictionary spelling remains the trigger basis across Roman alternatives and kana input. Trigger groups and weapon balance are unchanged.

The end-to-end unit regression feeds CombatSession with jikann / zikann / じかん and checks Blade Trap deployment, then chizu / tizu / ちず and checks Tesla Mine deployment. These are shared matcher/domain tests, not evidence of a particular physical keyboard's notifications.

No physical device was connected at verification start, so VM_Pixel_7a is used for the Android screen check. Debug/AndroidTest APK builds passed. Unit tests: 168 passed, including the six alternative-input completion cases. Android LevelUpInstrumentedTest: 4 passed. Actual emulator captures are levelup.png and evolution.png; acquisition conditions fit the phone cards without clipping. The captured evolution screen covers ordinary Repeater branches; the extra-trigger wording uses the same shared starting-condition formatter.
