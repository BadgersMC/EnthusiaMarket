package net.badgersmc.em.infrastructure.persistence

import java.sql.Connection

internal inline fun <T> Connection.inMarketTransaction(block: () -> T): T {
    autoCommit = false
    try {
        val result = block()
        commit()
        return result
    } catch (failure: Throwable) {
        try { rollback() } catch (rollbackFailure: Exception) { failure.addSuppressed(rollbackFailure) }
        throw failure
    }
}
