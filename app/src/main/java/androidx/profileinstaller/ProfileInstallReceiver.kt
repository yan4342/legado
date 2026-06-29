@file:Suppress("unused")

package androidx.profileinstaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process

/**
 * Shadow of the library's [ProfileInstallReceiver].
 *
 * The library's saveProfile() calls Process.sendSignal() without a try-catch,
 * so if the signal fails setResultCode() is never called and the benchmark
 * library reports "save profile broadcast was not received". This shadow
 * guarantees result codes are always set.
 *
 * The library JAR is filtered at build time to remove its copy of this class,
 * avoiding DEX merge conflicts.
 */
class ProfileInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_INSTALL_PROFILE -> {
                ProfileInstaller.writeProfile(
                    context,
                    { r -> r.run() },
                    object : ProfileInstaller.DiagnosticsCallback {
                        override fun onResultReceived(code: Int, data: Any?) {
                            setResultCode(code)
                        }
                        override fun onDiagnosticReceived(code: Int, data: Any?) {}
                    },
                    true,
                )
            }
            ACTION_SAVE_PROFILE -> {
                try {
                    Process.sendSignal(Process.myPid(), 10) // SIGUSR1
                } catch (_: Throwable) {
                }
                setResultCode(12)
            }
            ACTION_SKIP_FILE -> {
                when (intent.getStringExtra(EXTRA_SKIP_FILE_OPERATION)) {
                    "WRITE_SKIP_FILE" -> ProfileInstaller.writeSkipFile(
                        context,
                        { r -> r.run() },
                        object : ProfileInstaller.DiagnosticsCallback {
                            override fun onResultReceived(code: Int, data: Any?) {
                                setResultCode(code)
                            }
                            override fun onDiagnosticReceived(code: Int, data: Any?) {}
                        },
                    )
                    "DELETE_SKIP_FILE" -> ProfileInstaller.deleteSkipFile(
                        context,
                        { r -> r.run() },
                        object : ProfileInstaller.DiagnosticsCallback {
                            override fun onResultReceived(code: Int, data: Any?) {
                                setResultCode(code)
                            }
                            override fun onDiagnosticReceived(code: Int, data: Any?) {}
                        },
                    )
                    else -> setResultCode(12)
                }
            }
            ACTION_BENCHMARK_OPERATION -> {
                when (intent.getStringExtra(EXTRA_BENCHMARK_OPERATION)) {
                    "DROP_SHADER_CACHE" -> {
                        val cacheDir = if (Build.VERSION.SDK_INT >= 34) {
                            context.createDeviceProtectedStorageContext().cacheDir
                        } else {
                            context.cacheDir
                        }
                        setResultCode(if (cacheDir.deleteRecursively()) 14 else 15)
                    }
                    "SAVE_PROFILE" -> {
                        try {
                            val pid = intent.getIntExtra(EXTRA_PID, Process.myPid())
                            Process.sendSignal(pid, 10)
                        } catch (_: Throwable) {
                        }
                        setResultCode(12)
                    }
                    else -> setResultCode(12)
                }
            }
            else -> setResultCode(0)
        }
    }

    companion object {
        const val ACTION_INSTALL_PROFILE = "androidx.profileinstaller.action.INSTALL_PROFILE"
        const val ACTION_SAVE_PROFILE = "androidx.profileinstaller.action.SAVE_PROFILE"
        const val ACTION_SKIP_FILE = "androidx.profileinstaller.action.SKIP_FILE"
        const val ACTION_BENCHMARK_OPERATION = "androidx.profileinstaller.action.BENCHMARK_OPERATION"
        private const val EXTRA_SKIP_FILE_OPERATION = "EXTRA_SKIP_FILE_OPERATION"
        private const val EXTRA_BENCHMARK_OPERATION = "EXTRA_BENCHMARK_OPERATION"
        private const val EXTRA_PID = "EXTRA_PID"
    }
}
