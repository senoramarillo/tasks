package org.tasks.jobs

import android.content.Context
import androidx.hilt.Assisted
import androidx.hilt.work.WorkerInject
import androidx.work.WorkerParameters
import org.tasks.LocalBroadcastManager
import org.tasks.analytics.Firebase
import org.tasks.caldav.CaldavSynchronizer
import org.tasks.data.CaldavDaoBlocking
import org.tasks.data.GoogleTaskListDaoBlocking
import org.tasks.etesync.EteSynchronizer
import org.tasks.gtasks.GoogleTaskSynchronizer
import org.tasks.injection.InjectingWorker
import org.tasks.preferences.Preferences
import org.tasks.sync.SyncAdapters
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyncWork @WorkerInject constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        firebase: Firebase,
        private val caldavSynchronizer: CaldavSynchronizer,
        private val eteSynchronizer: EteSynchronizer,
        private val googleTaskSynchronizer: GoogleTaskSynchronizer,
        private val localBroadcastManager: LocalBroadcastManager,
        private val preferences: Preferences,
        private val caldavDao: CaldavDaoBlocking,
        private val googleTaskListDao: GoogleTaskListDaoBlocking,
        private val syncAdapters: SyncAdapters) : InjectingWorker(context, workerParams, firebase) {
    
    public override fun run(): Result {
        if (!syncAdapters.isSyncEnabled) {
            return Result.success()
        }
        synchronized(LOCK) {
            if (preferences.isSyncOngoing) {
                return Result.retry()
            }
        }
        preferences.isSyncOngoing = true
        localBroadcastManager.broadcastRefresh()
        try {
            sync()
        } catch (e: Exception) {
            firebase.reportException(e)
        } finally {
            preferences.isSyncOngoing = false
            localBroadcastManager.broadcastRefresh()
        }
        return Result.success()
    }

    @Throws(InterruptedException::class)
    private fun sync() {
        val numThreads = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(numThreads)
        for (account in caldavDao.getAccounts()) {
            executor.execute {
                if (account.isCaldavAccount) {
                    caldavSynchronizer.sync(account)
                } else if (account.isEteSyncAccount) {
                    eteSynchronizer.sync(account)
                }
            }
        }
        val accounts = googleTaskListDao.getAccounts()
        for (i in accounts.indices) {
            executor.execute { googleTaskSynchronizer.sync(accounts[i], i) }
        }
        executor.shutdown()
        executor.awaitTermination(15, TimeUnit.MINUTES)
    }

    companion object {
        private val LOCK = Any()
    }
}