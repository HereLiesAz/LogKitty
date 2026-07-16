package com.hereliesaz.logkitty.ui.delegates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    }
}
