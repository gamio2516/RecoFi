package jp.knaka.cardmemo

object ReconciliationPolicy {
    fun canLock(progress: Collection<ReconciliationProgress>): Boolean {
        val imported = progress.sumOf { it.imported };val confirmed=progress.sumOf{it.confirmed}
        return imported > 0 && confirmed >= imported
    }
}
