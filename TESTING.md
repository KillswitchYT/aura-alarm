# Aura Alarm: on-device test list

Nothing below has been run on a device by the person who wrote the code. The JVM tests (`tools/run-engine-tests.sh`) cover the pure logic only.
Tick a line only when you saw it work. Write down anything that did not. Phone only, no PC needed.

## 0. Install
- [ ] Install the APK over your current build, do not uninstall. Open it: your existing alarms are all still there.
- [ ] Setup tab shows 7 of 7 protections on. If not, tap Fix on each and come back.

## 1. The ring (the only thing that really matters)
Use Setup, Test alarm now, then the real thing below.
- [ ] Test alarm with the screen on: sound, vibration, ring screen. Dismiss stops everything.
- [ ] REAL alarm 2 minutes from now, phone locked, screen off, face down: screen turns on over the lock screen, sound plays, vibrates. Dismiss it.
- [ ] Same with the phone locked and a secure lock (PIN or fingerprint). The ring screen still shows over the lock.
- [ ] Same with another app open in front (a game or YouTube). The ring screen takes over.
- [ ] Gentle ramp: volume rises over about 30 s. Max + vibrate: loud, vibrates, there is no Snooze button.
- [ ] Snooze: ring, tap Snooze, it rings again after the set minutes.
- [ ] Snooze then reboot the phone before it rings again: it still rings at the snooze time (new in this build).
- [ ] Install a new build while a snooze is waiting: it still rings (new).
- [ ] Math challenge (3 sums): wrong answer gives a new sum, right ones count, only the third one dismisses.
- [ ] Shake challenge: 25 shakes dismisses. Phone still on the lock screen while you shake.
- [ ] Alarm volume: Setup, Alarm volume. Drag it low, tap Play a test: quiet. Drag it high, test again: clearly louder. (The Ringtone and Media sliders must NOT change it.)
- [ ] While a test is ringing, press the phone's volume up/down: the on-screen slider says Alarm, and the sound follows. After you dismiss, the volume you ended on is kept (it is not put back).
- [ ] Sound check in Setup goes off when the alarm volume is under 60%, and its Fix raises it to a loud level by itself.
- [ ] Volume buttons while ringing for each of the 4 settings (Setup). With a challenge on, "Dismiss" must NOT dismiss.
- [ ] Leave one ringing untouched: it stops by itself after 10 minutes.

## 2. Surviving restarts
- [ ] Set an alarm 3 minutes ahead, reboot the phone, do NOT open the app. It still rings.
- [ ] Same but reboot and do not unlock the phone at all (direct boot). It still rings.
- [ ] Change the phone clock time or time zone. The next alarm time in the app updates.
- [ ] Turn off "Alarms & reminders" for the app in system settings, turn it back on: Setup shows it, and Re-arm all alarms fixes it.

## 3. Alarms tab
- [ ] New alarm: time, label, repeat days, snooze, challenge, sound, vibrate, fade in, Save. It appears sorted by time.
- [ ] Pick a phone sound: the sheet shows its real name, not "Phone sound". Reopen the alarm, the name is still there.
- [ ] Pick a date: the alarm becomes "One-off on <date>", repeat days clear. Pick a day after: the date clears.
- [ ] Skip next ring on a repeating alarm: the next ring moves one day on.
- [ ] Toggle, delete, search, "no match" state, empty state.
- [ ] Editor Test button rings the alarm as configured (with its challenge).

## 4. World
- [ ] Fresh install shows New York, London, Tokyo. Add a city (search "uk", "usa", a country, a region). Remove one. Remove all: the empty state shows.
- [ ] Analog / Digital toggle, close the app, reopen: the choice stuck.
- [ ] Converter: pick a time, every city follows. Now resets it to the live time.

## 5. Timers
- [ ] Preset chip starts a timer straight away. h / min / s fields plus a label start one. Empty fields give a red hint.
- [ ] Start, Pause, Resume, +1:00, Reset, delete. Progress bar moves.
- [ ] Two timers at once. Notification shows each with +1 min and Stop buttons, both work.
- [ ] A timer ending with the app closed rings like an alarm and says "Timer finished".
- [ ] A timer running across a reboot: it still rings at the right time.
- [ ] A timer that ends while the phone is OFF: after boot it is idle again and a "Finished at ..." notification appears (new).

## 6. Stopwatch
- [ ] Start, Lap x4, Stop. Best lap is green, worst is red. Copy puts the laps on the clipboard (paste somewhere to check).
- [ ] Start, leave the app for a minute, come back: the time kept counting. Close the app from recents while running: same.
- [ ] Reset clears everything. With no laps the empty state shows.

## 7. Setup
- [ ] Each Fix opens the correct Android screen (exact alarms, notifications, full-screen, battery, Do Not Disturb).
- [ ] Self-test runs line by line and ends with "All protected" or a count.
- [ ] Ring style, volume buttons, time format (12 / 24 / phone) change immediately everywhere, widget included.
- [ ] Bedtime: on, goal, wind-down. "Next reminder at ..." shows. The notification arrives at that time.
- [ ] Night clock: black, dim red, screen stays on, moves every 30 s, tap or Back leaves. Brightness returns to normal after.
- [ ] Backup: Copy, then change an alarm, then Paste + Restore (asks twice). The changed alarm goes back. Garbage text shows "not a valid backup".
- [ ] Share opens the system share sheet.
- [ ] Last fired and Backup ring times look right after an alarm has rung.

## 8. Other entry points
- [ ] Home-screen widget: shows the time and the next alarm, tap opens the app.
- [ ] Voice: "Set an alarm for 7 am", "Set a timer for 5 minutes", "Show my alarms". Each does it once (not twice).
- [ ] Notification permission denied on first launch: Setup shows it off, Fix asks again, then goes to settings after a second refusal.

## 9. Things I could not make easy to test
- The fallback ring (when Android refuses to start the ring service) is almost impossible to trigger on purpose. It is compile-checked and its text is unit-tested, nothing more.
- Battery cost of the clock ring on Home is not measured. It redraws 30 times a second while Home is open and the screen is on, and the glass bar behind it redraws with it. Nothing runs when the app is closed or while an alarm rings. If the phone gets warm with Home open, say so.
