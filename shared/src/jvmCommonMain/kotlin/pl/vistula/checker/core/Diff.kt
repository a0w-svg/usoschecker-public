package pl.vistula.checker.core

object Diff {
    /** The initial download is a baseline. A sliding timetable window is compared only in overlap. */
    @JvmStatic
    @JvmOverloads
    fun between(
        previous: Snapshot?,
        current: Snapshot,
        now: Long,
        today: String,
        language: String? = "pl"
    ): List<Change> {
        val changes = ArrayList<Change>()
        if (previous == null) return changes
        for (r in current.records.values) {
            val old = previous.records[r.id]
            if (r.kind == "plan" && (r.date < today || !previous.covers(r.date))) continue
            if (old == null || !r.same(old)) {
                changes.add(
                    Change(
                        if (old == null) null else previous.forLanguage(language).records.getOrDefault(old.id, old),
                        current.forLanguage(language).records.getOrDefault(r.id, r),
                        now
                    )
                )
            }
        }
        for (old in previous.records.values) {
            if (current.records.containsKey(old.id)) continue
            if (old.kind == "plan" && (!current.covers(old.date) || old.date < today)) continue
            changes.add(
                Change(
                    previous.forLanguage(language).records.getOrDefault(old.id, old),
                    null,
                    now
                )
            )
        }
        return changes
    }
}

