import com.killswitch.aura.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import kotlin.random.Random

fun at(y: Int, mo: Int, d: Int, h: Int, m: Int): Long = Calendar.getInstance().apply { set(y, mo - 1, d, h, m, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
fun dow(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK) - 1
var bad = 0; var total = 0
fun check(name: String, ok: Boolean) { total++; if (!ok) { bad++; println("FAIL $name") } }
fun section(s: String) = println("== $s")

fun main() {
    val sat6 = at(2026, 10, 24, 6, 0); val sat730 = at(2026, 10, 24, 7, 30)

    section("alarm scheduling")
    check("one-off before its time rings today", Alarm(time = "07:00").next(sat6) == at(2026, 10, 24, 7, 0))
    check("one-off after its time rolls to tomorrow", Alarm(time = "07:00").next(sat730) == at(2026, 10, 25, 7, 0))
    check("exactly at ring time is not returned again", Alarm(time = "07:00").next(at(2026, 10, 24, 7, 0)) == at(2026, 10, 25, 7, 0))
    check("Monday-only from Saturday lands Monday 26th", Alarm(time = "07:00", days = setOf(1)).next(sat6) == at(2026, 10, 26, 7, 0))
    check("weekend alarm after 07:30 goes to Sunday", Alarm(time = "07:00", days = setOf(0, 6)).next(sat730) == at(2026, 10, 25, 7, 0))
    val daily = Alarm(time = "07:00", days = (0..6).toSet()); val t1 = daily.next(sat6)
    check("skip-next jumps exactly one ring", daily.copy(skip = t1).next(sat6) == at(2026, 10, 25, 7, 0))
    check("dated alarm in the past is -1", Alarm(time = "07:00", date = "2026-10-01").next(sat6) == -1L)
    check("dated alarm in the future", Alarm(time = "08:15", date = "2026-12-31").next(sat6) == at(2026, 12, 31, 8, 15))
    check("midnight from 23:59", Alarm(time = "00:00").next(at(2026, 10, 24, 23, 59)) == at(2026, 10, 25, 0, 0))
    check("summaries", Alarm(days = setOf(1,2,3,4,5)).daysSummary() == "Weekdays" && Alarm().daysSummary() == "Once" && Alarm(days = setOf(0,6)).daysSummary() == "Weekends")

    section("daylight saving (Egypt falls back at the end of Thursday 29 Oct 2026)")
    val cairoZ = ZoneId.of("Africa/Cairo")
    val before = at(2026, 10, 29, 8, 0)
    val expected = java.time.ZonedDateTime.of(2026, 10, 30, 7, 0, 0, 0, cairoZ).toInstant().toEpochMilli()
    check("daily 07:00 across fall-back lands on 07:00 local", Alarm(time = "07:00", days = (0..6).toSet()).next(before) == expected)
    check("that night is 24h long, not 23h", expected - before == 24 * 3_600_000L)
    check("alarm the day before is a normal 23h", Alarm(time = "07:00", days = (0..6).toSet()).next(at(2026, 10, 27, 8, 0)) - at(2026, 10, 27, 8, 0) == 23 * 3_600_000L)

    section("alarm JSON, including data saved by the web build")
    val old = """{"t":"06:30","l":"Wake up","on":1,"d":[1,2,3],"dt":"","snd":"raw:2","vb":1,"sz":10,"ch":2,"gr":1,"sk":12345}"""
    val a = Alarm.from(JSONObject(old))
    check("old alarm parses", a.time == "06:30" && a.label == "Wake up" && a.on && a.days == setOf(1, 2, 3) && a.sound == "raw:2" && a.snooze == 10 && a.challenge == 2 && a.gradual && a.skip == 12345L)
    val back = Alarm.from(a.toJson())
    check("round trip keeps every field", back == a)
    check("challenge written as ch", a.toJson().getInt("ch") == 2)
    check("garbage day values are dropped", Alarm.from(JSONObject("""{"t":"07:00","d":[1,9,-3,"x"]}""")).days == setOf(1))
    check("missing fields fall back to defaults", Alarm.from(JSONObject("""{"t":"07:00"}""")).let { it.vibrate && it.snooze == 5 && !it.on && it.challenge == 0 })

    section("alarm storage: damaged data vs no alarms")
    val stored = org.json.JSONArray().also { j -> listOf(a, Alarm(time = "22:15")).forEach { x -> j.put(x.toJson()) } }.toString()
    check("saved list parses back", Store.parseAlarms(stored) == listOf(a, Alarm(time = "22:15")))
    check("missing, empty and blank mean no alarms", Store.parseAlarms("") == emptyList<Alarm>() && Store.parseAlarms("[]") == emptyList<Alarm>() && Store.parseAlarms("   ") == emptyList<Alarm>())
    check("garbage is reported as damaged", Store.parseAlarms("not json") == null && Store.parseAlarms("{\"a\":1}") == null && Store.parseAlarms("[1,2]") == null && Store.parseAlarms("[{\"t\":\"07:00\"},5]") == null)
    check("a minimal old-style entry still loads", Store.parseAlarms("""[{"t":"07:00"}]""")!!.size == 1)

    section("settings and bedtime")
    check("quoted setting from web build", Settings.unquote("\"24\"") == "24" && Settings.unquote("24") == "24" && Settings.unquote("") == "")
    check("volume: snooze mode", Settings.volumeAction("snooze", false) == VolAction.SNOOZE && Settings.volumeAction("snooze", true) == VolAction.SNOOZE)
    check("volume: stop mode obeys challenge lock", Settings.volumeAction("stop", false) == VolAction.DISMISS && Settings.volumeAction("stop", true) == VolAction.CONSUME)
    check("volume: none swallows, vol passes", Settings.volumeAction("none", false) == VolAction.CONSUME && Settings.volumeAction("vol", false) == VolAction.PASS)
    val first = at(2026, 10, 25, 7, 0)
    check("bedtime = alarm - 8h - 30min", Bed(true, 8, 30).reminderAt(first, sat6) == at(2026, 10, 24, 22, 30))
    check("bedtime off or past gives -1", Bed(false).reminderAt(first, sat6) == -1L && Bed(true, 8, 30).reminderAt(first, at(2026, 10, 24, 23, 0)) == -1L && Bed(true).reminderAt(-1, sat6) == -1L)
    check("bed JSON round trip", Bed.from(Bed(true, 9, 45).toJson()) == Bed(true, 9, 45))

    section("timers")
    val now = 1_000_000L
    val t = TimerItem.create(300, "Tea", now, id = 42)
    check("new timer runs and ends on time", t.running && t.end == now + 300_000 && t.remaining(now + 100_000) == 200_000L)
    check("remaining never negative", t.remaining(now + 999_999_999) == 0L)
    val paused = t.toggle(now + 100_000)
    check("pause banks the time left", paused.state == "pause" && paused.left == 200_000L && paused.remaining(now + 5_000_000) == 200_000L)
    val resumed = paused.toggle(now + 500_000)
    check("resume ends left-from-now", resumed.running && resumed.end == now + 700_000)
    check("+1:00 while running pushes the end", t.addMinute(now).end == t.end + 60_000 && t.addMinute(now).dur == 360_000L)
    check("+1:00 while paused adds to left", paused.addMinute(now).left == 260_000L)
    check("reset restores full duration", paused.reset().let { it.state == "idle" && it.left == 300_000L })
    check("start from finished restarts the duration", TimerItem(1, "", 5000, "pause", 0, 0).toggle(now).end == now + 5000)
    check("timer JSON round trip", TimerItem.from(t.toJson()) == t)
    check("timer progress", Math.abs(t.progress(now + 150_000) - 0.5f) < 0.001f)
    check("fmt", TimerItem.fmt(65_000) == "01:05" && TimerItem.fmt(3_725_000) == "1:02:05" && TimerItem.fmt(0) == "00:00" && TimerItem.fmt(1) == "00:01")
    check("id in range", TimerItem.newId(0.0) == 1_000_000 && TimerItem.newId(0.999) < 901_000_000)

    section("stopwatch")
    var sw = Stopwatch()
    check("fresh labels", sw.primaryLabel == "Start" && sw.secondaryLabel() == "Lap")
    sw = sw.toggle(1000)
    check("running", sw.running && sw.elapsed(4000) == 3000L && sw.primaryLabel == "Stop")
    sw = sw.lapOrReset(4000).lapOrReset(9000).lapOrReset(10000)
    check("laps are cumulative", sw.laps == listOf(3000L, 8000L, 9000L))
    check("splits", sw.splits() == listOf(3000L, 5000L, 1000L))
    check("best and worst", sw.mark(0) == 0 && sw.mark(1) == 2 && sw.mark(2) == 1)
    sw = sw.toggle(12000)
    check("stopped banks time", !sw.running && sw.acc == 11000L && sw.primaryLabel == "Resume" && sw.secondaryLabel() == "Reset")
    check("elapsed frozen while stopped", sw.elapsed(99999) == 11000L)
    check("resume continues", sw.toggle(20000).elapsed(21000) == 12000L)
    check("copy text", sw.copyText().startsWith("Lap 1  00:03.00  total 00:03.00"))
    check("reset clears", sw.lapOrReset(0) == Stopwatch())
    check("stopwatch JSON round trip", Stopwatch.from(sw.toJson()) == sw)
    check("one lap has no best/worst", Stopwatch(laps = listOf(5L)).mark(0) == 0)
    check("fmt", Stopwatch.fmt(754_320) == "12:34.32" && Stopwatch.fmt(0) == "00:00.00")

    section("wake challenges")
    val m = MathChallenge(Random(7))
    check("sums in range", (0..200).all { val c = MathChallenge(Random(it)); c.a in 7..46 && c.b in 3..42 })
    val wrongA = m.a + m.b + 1
    check("wrong answer re-rolls but keeps progress", !m.answer(wrongA) && m.solved == 0 && !m.done)
    check("null answer is wrong", !m.answer(null))
    repeat(3) { check("correct answer ${it + 1}", m.answer(m.a + m.b)) }
    check("three right finishes", m.done && m.remaining == 0 && m.solved == 3)
    val s = ShakeCounter()
    check("gravity alone is not a shake", !s.feed(0f, 9.8f, 0f, 1000))
    check("hard shake counts", s.feed(20f, 15f, 5f, 1000) && s.count == 1)
    check("debounce 180 ms", !s.feed(20f, 15f, 5f, 1100) && s.feed(20f, 15f, 5f, 1200) && s.count == 2)
    var tm = 2000L; while (!s.done) { s.feed(30f, 0f, 0f, tm); tm += 200 }
    check("25 shakes finishes", s.count == 25 && s.progress == 1f && !s.feed(30f, 0f, 0f, tm + 500) && s.count == 25)

    section("world clock")
    val valid = ZoneId.getAvailableZoneIds()
    val data = java.io.File(System.getProperty("cities", "tools/cities.txt")).readText().trim()
    val all = WorldClock.parseData(data, valid)
    check("city database loads (229 curated + extras)", all.size >= 225)
    check("curated first, extras appended", all.first().name == "Kabul" && all.first().country == "Afghanistan")
    check("no legacy aliases leak in", all.none { it.zone.startsWith("US/") || it.zone.startsWith("Etc/") })
    check("unknown zones are skipped", WorldClock.parseData("X:Nowhere=Mars/Olympus,Cairo=Africa/Cairo", valid).map { it.name }.first() == "Cairo")
    check("search cairo ranks Cairo first", WorldClock.search(all, "cairo").first().name == "Cairo")
    check("search country: egypt finds Cairo", WorldClock.search(all, "egypt").any { it.name == "Cairo" })
    check("nickname uk finds London", WorldClock.search(all, "uk").any { it.name == "London" })
    check("nickname usa finds New York", WorldClock.search(all, "usa").any { it.zone == "America/New_York" })
    check("results capped at 80", WorldClock.search(all, "").size == 80)
    check("no match is empty", WorldClock.search(all, "zzzzqq").isEmpty())
    val cairo = ZoneId.of("Africa/Cairo"); val t0 = at(2026, 10, 24, 12, 0)
    check("same zone is 'Same time'", WorldClock.offsetText(WorldClock.offsetMinutes("Africa/Cairo", t0, cairo)) == "Same time")
    check("Tokyo is +6h from Cairo in October (DST)", WorldClock.offsetMinutes("Asia/Tokyo", t0, cairo) == 360)
    check("Kolkata offset text", WorldClock.offsetText(WorldClock.offsetMinutes("Asia/Kolkata", t0, cairo)) == "+2h 30m")
    check("behind is negative", WorldClock.offsetText(WorldClock.offsetMinutes("America/New_York", t0, cairo)) == "-7h")
    check("GMT labels", WorldClock.gmtLabel("Asia/Kolkata", t0) == "GMT+5:30" && WorldClock.gmtLabel("Europe/London", t0).startsWith("GMT") && WorldClock.gmtLabel("UTC", t0) == "GMT" && WorldClock.gmtLabel("America/New_York", t0) == "GMT-4")
    check("day labels", WorldClock.dayLabel("Pacific/Auckland", at(2026, 10, 24, 20, 0), cairo) == "Tomorrow" && WorldClock.dayLabel("America/Los_Angeles", at(2026, 10, 24, 1, 0), cairo) == "Yesterday" && WorldClock.dayLabel("Africa/Cairo", t0, cairo) == "Today")
    check("converter 09:00 Cairo -> Tokyo 15:00", WorldClock.converted("Asia/Tokyo", 9, 0, LocalDate.of(2026, 10, 24), cairo) == (15 to 0))
    check("converter crosses midnight", WorldClock.converted("Pacific/Auckland", 20, 30, LocalDate.of(2026, 10, 24), cairo) == (6 to 30))
    check("city key round trip", City.parse(City("New York", "America/New_York", "United States").key) == City("New York", "America/New_York", "United States") && City.parse("junk") == null)

    section("backup")
    val al = listOf(a, Alarm(time = "22:15", label = "Night", days = setOf(0, 6), challenge = 1))
    val text = Backup.export(al, listOf("Tokyo|Asia/Tokyo|Japan"), Bed(true, 9, 45), "snooze")
    val d = Backup.parse(text)!!
    check("backup round trip", d.alarms == al && d.cities == listOf("Tokyo|Asia/Tokyo|Japan") && d.bed == Bed(true, 9, 45) && d.vbtn == "snooze")
    check("only 'al' is required", Backup.parse("""{"al":[]}""")!!.let { it.alarms.isEmpty() && it.cities == null && it.bed == null && it.vbtn == null })
    check("web-build backup restores", Backup.parse("""{"al":[$old],"wc":["Cairo|Africa/Cairo|Egypt"],"bed":{"on":1,"goal":7,"wind":15},"vbtn":"stop"}""")!!.let { it.alarms[0].challenge == 2 && it.bed == Bed(true, 7, 15) && it.vbtn == "stop" })
    check("invalid backups rejected", Backup.parse("not json") == null && Backup.parse("""{"wc":[]}""") == null && Backup.parse("""{"al":"x"}""") == null && Backup.parse("") == null)

    section("gaps found in the parity audit")
    val named = Alarm.from(JSONObject("""{"t":"07:00","on":1,"snd":"content://media/x/1","sn":"Zen Garden"}"""))
    check("name of a phone-picked sound survives a save", named.toJson().optString("sn") == "Zen Garden")
    check("sound name is part of the model", named.soundName == "Zen Garden" && Alarm.from(named.toJson()) == named)
    check("no sound name, no sn key", !Alarm().toJson().has("sn"))

    // a timer or an alarm gets the right words when the loud notification has to stand in for the service
    check("fallback text: plain alarm", Scheduler.ringTexts("{}") == ("Alarm" to "Alarm ringing") && Scheduler.ringTexts(null) == ("Alarm" to "Alarm ringing"))
    check("fallback text: labelled alarm", Scheduler.ringTexts("""{"l":"school"}""") == ("school" to "Alarm ringing"))
    check("fallback text: timer", Scheduler.ringTexts("""{"timer":1}""") == ("Timer" to "Timer finished") && Scheduler.ringTexts("""{"l":"tea","timer":1}""") == ("tea" to "Timer finished"))
    check("fallback text: garbage is an alarm", Scheduler.ringTexts("not json") == ("Alarm" to "Alarm ringing"))

    // timers that ran out while the phone was off must not stay stuck on "running"
    val tn = 1_000_000L
    val live = TimerItem.create(300, "live", tn); val dead = TimerItem(2, "dead", 60_000, "run", tn - 60_000, 60_000); val idle = TimerItem(3, "idle", 1000)
    val (after, gone) = Timers.settle(listOf(live, dead, idle), tn)
    check("expired running timer is settled", gone == listOf(dead) && after[1].state == "idle" && after[1].left == 60_000L)
    check("live and idle timers are untouched", after[0] == live && after[2] == idle)
    check("a timer ending right now is left for its alarm", Timers.settle(listOf(TimerItem(4, "", 1000, "run", tn - 2_000, 1000)), tn).second.isEmpty())
    check("nothing to settle returns the same list", Timers.settle(listOf(live, idle), tn).let { it.first == listOf(live, idle) && it.second.isEmpty() })

    // world clock first run
    check("never saved is null, so defaults show", WorldClock.parseSaved("") == null && WorldClock.parseSaved("  ") == null && WorldClock.parseSaved("junk") == null)
    check("removing every city is an empty list, not defaults", WorldClock.parseSaved("[]") == emptyList<String>())
    check("saved list parses", WorldClock.parseSaved("""["Cairo|Africa/Cairo|Egypt",""]""") == listOf("Cairo|Africa/Cairo|Egypt"))
    check("default cities are valid zones", WorldClock.DEFAULTS.size == 3 && WorldClock.DEFAULTS.all { c -> City.parse(c)!!.let { it.zone in ZoneId.getAvailableZoneIds() && it.country.isNotEmpty() } })

    section("slice 2 engine: strict loading, backup extras")
    check("damaged timers are reported", Timers.parse("not json") == null && Timers.parse("[1]") == null && Timers.parse("{\"a\":1}") == null)
    check("no timers is fine", Timers.parse("") == emptyList<TimerItem>() && Timers.parse("[]") == emptyList<TimerItem>())
    check("timers parse back", Timers.parse("[" + TimerItem.create(60, "x", 1000L, 5).toJson() + "]") == listOf(TimerItem.create(60, "x", 1000L, 5)))
    check("damaged stopwatch is reported", Stopwatch.parse("junk") == null && Stopwatch.parse("[1]") == null)
    check("nothing saved is a fresh stopwatch", Stopwatch.parse("") == Stopwatch() && Stopwatch.parse("{}") == Stopwatch())
    val swRun = Stopwatch(acc = 500, running = true, startedAt = 9000, laps = listOf(100L, 400L))
    check("running stopwatch survives storage", Stopwatch.parse(swRun.toJson().toString()) == swRun && Stopwatch.parse(swRun.toJson().toString())!!.elapsed(10000) == 1500L)
    val ex = Backup.export(al, listOf("Tokyo|Asia/Tokyo|Japan"), Bed(), "vol", ro = 2, tfmt = "24", wa = 1)
    Backup.parse(ex)!!.let { check("backup keeps ring style, time format, analog", it.ro == 2 && it.tfmt == "24" && it.wa == 1 && it.alarms == al) }
    check("backup without extras leaves them alone", Backup.parse(text)!!.let { it.ro == null && it.tfmt == null && it.wa == null })
    check("bad extras are ignored or clamped", Backup.parse("""{"al":[],"ro":9,"tfmt":"banana","wa":5}""")!!.let { it.ro == 2 && it.tfmt == null && it.wa == 1 })

    println("\n$total checks, ${total - bad} passed" + if (bad == 0) " - ALL PASSED" else ", $bad FAILED")
    if (bad != 0) System.exit(1)
}
