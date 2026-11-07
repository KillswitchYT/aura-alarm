package com.killswitch.aura

/**
 * What happens when an alarm goes off, in the one order that protects the ring.
 * The ring comes first. Bookkeeping can fail, the next alarm can fail to arm, none of it may stop the ring or be skipped because of another step.
 * "Already rang" is only remembered after a ring really started.
 * Re-arming rebuilds every alarm and so cancels this alarm's 2-minute backup, which means a ring that failed cannot lean on the backup.
 * It asks [Fire.run]'s retry hook for its own second attempt a few seconds later instead.
 */
object Fire {
    /** A ring that could not start (service and notification both refused) is tried again this often, this many times. */
    const val RETRY_MS = 20_000L
    const val MAX_RETRIES = 6
    fun shouldRetry(attempt: Int) = attempt in 0 until MAX_RETRIES

    /**
     * [ring] returns true when a ring (the service or the loud notification) actually started. Returns that same answer.
     * [retry] runs only when the ring did not start, before the bookkeeping and the re-arm, and may not stop either of them.
     */
    fun run(
        ring: () -> Boolean, remember: () -> Unit, bookkeeping: () -> Unit, rearm: () -> Unit, onError: (Exception) -> Unit,
        retry: () -> Unit = {},
    ): Boolean {
        val rang = try { ring() } catch (e: Exception) { onError(e); false }
        if (rang) {
            try { remember() } catch (e: Exception) { onError(e) }
        } else {
            try { retry() } catch (e: Exception) { onError(e) }
        }
        try { bookkeeping() } catch (e: Exception) { onError(e) }
        try { rearm() } catch (e: Exception) { onError(e) }
        return rang
    }
}
