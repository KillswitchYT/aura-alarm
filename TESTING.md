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
- [ ] Alarm volume: Setup, Alarm volume is a free 10 to 100% slider. Drag it to about 20%, tap Play a test: quiet. Drag to 100%, test again: clearly louder. Drag it a second and third time: the thumb still follows your finger. The phone's own Ringtone, Media and Alarm sliders must NOT change how loud it is.
- [ ] While a test is ringing, press the phone's volume down: the on-screen slider says Alarm and the sound gets quieter. After you dismiss, the next ring is back at your slider level.
- [ ] Sound check in Setup goes off when the alarm volume slider is under 60%, and its Fix sets it to 80% by itself.
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
- [ ] Tapping an alarm's toggle only switches it on or off and does NOT open the editor. Tapping the time or details opens the editor.
- [ ] Delete, search (with 5 or more alarms), the All / On / Off chips (with 3 or more), "no match" state, empty state.
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
- [ ] Backup: Export to file, pick Downloads, save. Open the file in a file manager: readable text with your alarms in it.
- [ ] Change an alarm, then Import from file and pick it: it says how many alarms it has, Restore asks twice, the changed alarm goes back. Pick a random file (a photo, a text file): it says it is not a backup.
- [ ] A backup file made by the old web build still imports.
- [ ] Last fired and Backup ring times look right after an alarm has rung.

- [ ] Tab switching: tap each tab, then drag the bar across tabs. Note if any tap freezes. Setup, at the bottom, shows the slowest frame in ms (about 17 is smooth). Tell me the number and which tab, and whether it happens on every tap or only the first time you open each tab.

## 8. Other entry points
- [ ] Home-screen widget: shows the time and the next alarm, tap opens the app.
- [ ] Voice: "Set an alarm for 7 am", "Set a timer for 5 minutes", "Show my alarms". Each does it once (not twice).
- [ ] Notification permission denied on first launch: Setup shows it off, Fix asks again, then goes to settings after a second refusal.

## 9. Things I could not make easy to test
- The fallback ring (when Android refuses to start the ring service) is almost impossible to trigger on purpose. It is compile-checked and its text is unit-tested, nothing more.
- Battery cost of the clock ring on Home is not measured. It redraws 30 times a second while Home is open and the screen is on, and the glass bar behind it redraws with it. Nothing runs when the app is closed or while an alarm rings. If the phone gets warm with Home open, say so.

## 10. Look and glass (after the no-boxes pass)
- [ ] Every button, chip, close icon, toggle, slider and the dismiss slide is the same glass family (rim highlight, slight blur). Nothing looks like a flat grey pill next to the glass ones.
- [ ] Alarms, World, Timers, Stopwatch and Setup have no boxes around rows. Rows are separated by faint lines.
- [ ] Setup: with 7 of 7 protections on, the seven rows fold into one "All 7 protections are on" row; tap it to open them, "Hide" folds them again. Turn one protection off in system settings: they unfold by themselves.
- [ ] Setup alarm volume slider (the library one) is the accent colour (orange in dark, blue in light), not purple, and moves smoothly; the percent next to it follows.
- [ ] Open the alarm editor and the city picker: chips, day letters and the time button inside the sheet are glass too.
- [ ] Scroll the Setup tab and switch tabs: does anything stutter more than before? The ms meter at the bottom of Setup says how bad.

## 11. Speed
- [ ] Setup, bottom: after you have tapped through the tabs, it lists each tab as "first visit X ms, after that Y ms". Send me that whole block. Under 50 is smooth, over 100 you feel it.
- [ ] Wait about 5 seconds after opening the app before tapping tabs (it is pre-loading them). Does the first visit feel better than before?
- [ ] "Aura Alarm (fast)": a second, optimized copy (its own app, its own alarms, safe to delete). Install it from the Releases page (app-fast.apk), open the tabs, and compare the numbers and the feel with the normal app. If the fast one is smooth and the normal one is not, the lag is mostly the debug build.
- [ ] Tabs stay alive now: scroll Setup down, switch away and back: your scroll position is kept. Type 5 0 0 on the Timers numpad, switch away and back: it is still there. (This is on purpose.)
- [ ] The fade-in of each list still plays every time you arrive on a tab.
- [ ] A hidden tab never reacts to touches: tap in empty space on any tab and check nothing opens or toggles that belongs to another tab.
- [ ] With the Stopwatch running, switch to another tab and back: the time kept counting and the digits are right at once.

## 12. Light mode
- [ ] Setup, Appearance, Theme dropdown: tap it, three choices open (Match phone, Dark, Light). Picking one repaints the whole app straight away, no restart.
- [ ] Light: every word is readable on every tab (Alarms, World, Timers, Stopwatch, Setup), including the small grey captions, the "On" and "Fix" words in Setup and the red "Quiet" warning.
- [ ] Light: the clock ring, toggles, the New alarm and Start buttons are iPhone-style blue (white text on the buttons) and stand out clearly from the light background. Dark mode is still orange.
- [ ] Light: the blue words (the countdown, Fix, the next alarm time) are a slightly deeper blue and easy to read. The glass blue buttons are a lighter blue than the toggle fill, that is the glass library doing its thing, but the white text on them must still read clearly.
- [ ] Light: the glass pieces (tab bar, New alarm, chips, toggles, volume slider) look like light glass, not dark patches. The alarm editor and city picker sheets are light too.
- [ ] Light: the phone's clock and battery icons at the top are dark (readable), and the bottom navigation icons too.
- [ ] Match phone: switch your phone between dark and light in system settings while the app is open: the app follows.
- [ ] The ring screen (Setup, Test alarm now) is ALWAYS dark, even in light mode. After you dismiss it the app is light again.
- [ ] Night clock is black and dim red in both themes.
- [ ] Your choice survives closing the app, and an exported backup restores it.
- [ ] Not done on purpose: the home-screen widget keeps its own colours.

## 13. Parity audit fixes (build after 3.1.26)
- [ ] SKIP ON A ONE-DAY ALARM. Make an alarm with ONLY one weekday ticked (say Monday), Save. Open it, tap "Skip the next ring", Save. The row says "skipping next" and the Next alarm card shows the Monday AFTER (about 7 to 13 days away), not "None set". Before this fix it showed "None set" and the alarm was dead.
- [ ] Open that alarm again and untick skip, Save: Next alarm goes back to the nearer Monday.
- [ ] DAMAGED BACKUP. On a PC or in a text app, make a file named `bad.json` containing exactly: {"al":[{"t":"7am","l":"broken","on":1},{"t":"08:15","l":"fine","on":1}]}  Setup, Import from file, pick it, restore. PASS: the app does NOT crash. "fine" is on at 8:15. "broken" is there at 7:00 and switched OFF, so nothing rings at a time you never chose. Before this fix the app crashed every time you opened it.
- [ ] Phone language set to Arabic (or any language with its own digits): create an alarm and a one-off dated alarm, export a backup file and open it in a text app: times and dates are plain 0-9 digits like 07:30 and 2026-12-05.
- [ ] Not field-testable: the new ring retry (the ring could not start at all, so it tries again every 20 s up to 6 times). It only runs when both the foreground service and the loud notification fail, which I cannot make happen on purpose. It is covered by engine tests of the order of steps only. Scheduler.retryRing itself has never run.
