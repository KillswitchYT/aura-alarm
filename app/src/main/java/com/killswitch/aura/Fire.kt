package com.killswitch.aura

/**
 * What happens when an alarm goes off, in the one order that protects the ring.
 * The ring comes first. Bookkeeping can fail, the next alarm can fail to arm, none of it may stop the ring or be skipped because of another step.
 * "Already rang" is only remembered after a ring really started, so the backup alarm still gets its chance if the first try failed.
 */
object Fire {
    /** [ring] returns true when a ring (the service or the loud notification) actually started. Returns that same answer. */
    fun run(ring: () -> Boolean, remember: () -> Unit, bookkeeping: () -> Unit, rearm: () -> Unit, onError: (Exception) -> Unit): Boolean {
        val rang = try { ring() } catch (e: Exception) { onError(e); false }
        if (rang) try { remember() } catch (e: Exception) { onError(e) }
        try { bookkeeping() } catch (e: Exception) { onError(e) }
        try { rearm() } catch (e: Exception) { onError(e) }
        return rang
    }
}
