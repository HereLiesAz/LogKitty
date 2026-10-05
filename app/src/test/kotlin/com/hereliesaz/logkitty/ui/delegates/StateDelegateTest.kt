package com.hereliesaz.logkitty.ui.delegates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.CoroutineScope

class StateDelegateTest {

    @Test
    fun `test circular buffer limits log size`() = runBlocking {
        val bufferSizeFlow = MutableStateFlow(3)
        val delegate = StateDelegate(
            scope = this,
            dispatcher = Dispatchers.Unconfined,
            bufferSizeFlow = bufferSizeFlow
        )
        
        // Disable target filters so all lines are accepted
        delegate.setTargetApps(emptySet(), emptySet())

        // Append 5 logs
        delegate.appendSystemLog("Line 1")
        delegate.appendSystemLog("Line 2")
        delegate.appendSystemLog("Line 3")
        delegate.appendSystemLog("Line 4")
        delegate.appendSystemLog("Line 5")

        // Wait to allow the batch processor to emit
        delay(200)

        val logs = delegate.systemLog.value
        assertEquals(3, logs.size)
        // Ensure only the last 3 logs are kept
        assertEquals("Line 3", logs[0].text)
        assertEquals("Line 4", logs[1].text)
        assertEquals("Line 5", logs[2].text)
        // StateDelegate's batch loop never ends; cancel it so runBlocking can return.
        coroutineContext.cancelChildren()
    }
}

class CappingTest {
    private fun l(id: Long, target: Boolean) = IndexedLogLine(id, "line $id", null, target)

    @Test
    fun `non-target lines are evicted before target lines`() {
        val current = listOf(l(1, true), l(2, false), l(3, true), l(4, false))
        val out = StateDelegate.Capping.capped(current, listOf(l(5, false)), 4)
        assertEquals(listOf(1L, 3L, 4L, 5L), out.map { it.id })
    }

    @Test
    fun `falls back to oldest when only target lines remain`() {
        val current = listOf(l(1, true), l(2, true), l(3, true))
        val out = StateDelegate.Capping.capped(current, listOf(l(4, true)), 3)
        assertEquals(listOf(2L, 3L, 4L), out.map { it.id })
    }
}
