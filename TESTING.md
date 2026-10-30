# Device test plan (native 3.0)
## Screens, build 1
S1. LAUNCH. Dark navy scene with soft indigo and peach glow, clock ring ticking, Aura Alarm title. PASS if it is not a blank screen.
S2. GLASS. Scroll the list under the floating tab bar. PASS if the bar blurs and bends what passes behind it (Android 13+).
S3. ADD. New alarm, set a time from the time button, a label, days, Save. PASS if it appears sorted and the countdown updates.
S4. EDIT. Open an alarm: every field you saved is shown. Delete removes it. Toggle on the list turns it off and on.
S5. CHALLENGE. Set Math or Shake, Test. PASS if the ring screen shows the challenge and no Dismiss button.
S6. SETUP TAB. Seven checks, Fix opens the right Android screen, the banner on Home counts them.
S7. KEYBOARD. Typing a label keeps the editor above the keyboard.

## Engine

Run the JVM checks first: `tools/run-engine-tests.sh` (84 checks, includes Egypt's daylight-saving change).
Then on the phone, once the UI exists:
1. ONE-OFF. Alarm 2 min ahead, lock phone. PASS: wakes, rings, Dismiss stops it, alarm switches itself off.
2. REPEAT + SKIP. Daily alarm, skip the next ring. PASS: countdown jumps a day, the ring after that still happens.
3. SNOOZE. PASS: rings again after the snooze time. Test alarm snoozes for 10 s and stays a test.
4. MATH CHALLENGE. PASS: no Dismiss in the notification shade, three correct sums needed, a wrong answer re-rolls.
5. SHAKE CHALLENGE. PASS: 25 hard shakes needed, resting on a table does nothing.
6. TIMERS. Start two, pause one, +1:00 from the notification. PASS: both ring on time, counts survive closing the app.
7. TIMER RING. PASS: label shows the timer name, no Snooze button.
8. RING STYLE. Gentle ramp: volume rises over 30 s. Max + vibrate: full volume, always vibrates, no Snooze.
9. VOLUME BUTTONS while ringing: Change volume / Snooze / Dismiss / Do nothing. With a challenge set, Dismiss is blocked.
10. BEDTIME. Turn on, 8 h goal, 30 min wind-down, alarm at 07:00. PASS: reminder at 22:30.
11. WORLD CLOCK. Add Tokyo, search "uk", "usa". Offsets and Today/Tomorrow right. Converter moves all rows together.
12. BACKUP. Copy backup, wipe alarms, Restore. PASS: alarms, cities, bedtime and volume-button setting return.
13. WIDGET. Add it. PASS: shows time and next alarm, tapping opens the app, 12/24h setting applies.
14. VOICE. "Set an alarm for 6:30" and "set a timer for 10 minutes". PASS: both appear.
15. REBOOT. PASS: alarms and running timers still fire without opening the app.
16. EXACT ALARMS REVOKED (Android 12-13). PASS: alarm still rings, setup check 1 shows it.
17. UPDATE FROM THE WEB BUILD. Install over a same-signature 2.0. PASS: old alarms, cities, bedtime all present.
