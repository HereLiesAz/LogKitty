package com.hereliesaz.logkitty.privilege

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.hereliesaz.logkitty.MainActivity

/**
 * Restarts LogKitty's main process. A freshly granted `READ_LOGS` only takes effect in a new process
 * (the `log` group is attached at fork), so after a Shizuku grant the old process must die.
 *
 * Runs in its own `:restart` process: it kills the main process, relaunches [MainActivity], and
 * exits — the standard "process phoenix" pattern, with no alarm or background-start exemption needed.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(EXTRA_MAIN_PID, -1)
        if (mainPid > 0) Process.killProcess(mainPid)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        private const val EXTRA_MAIN_PID = "main_pid"

        /** Call from the main process. */
        fun restart(context: Context) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_MAIN_PID, Process.myPid())
            )
        }
    }
}
