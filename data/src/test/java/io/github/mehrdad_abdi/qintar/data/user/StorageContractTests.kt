package io.github.mehrdad_abdi.qintar.data.user

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mehrdad_abdi.qintar.domain.testing.FakeBookmarkStore
import io.github.mehrdad_abdi.qintar.domain.testing.FakeKhatmStore
import io.github.mehrdad_abdi.qintar.domain.testing.FakeReadLogStore
import io.github.mehrdad_abdi.qintar.domain.testing.FakeTransactionRunner
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/** The reference: the fakes that every domain test runs on. */
class FakeStorageContractTest : StorageContract() {
    override val stores: Stores = run {
        val log = FakeReadLogStore()
        val khatms = FakeKhatmStore()
        val bookmarks = FakeBookmarkStore()
        Stores(log, khatms, bookmarks, FakeTransactionRunner(log, khatms, bookmarks))
    }
}

/** The same contract against Room on a real SQLite database. */
@RunWith(AndroidJUnit4::class)
class RoomStorageContractTest : StorageContract() {
    private lateinit var db: QintarDatabase
    override lateinit var stores: Stores

    @Before
    fun open() {
        db = QintarDatabase.inMemory(ApplicationProvider.getApplicationContext())
        stores = roomStores(db)
    }

    @After
    fun close() {
        db.close()
    }
}

fun roomStores(db: QintarDatabase) = Stores(
    log = RoomReadLogStore(db.readLogDao()),
    khatms = RoomKhatmStore(db.khatmDao()),
    bookmarks = RoomBookmarkStore(db.bookmarkDao()),
    tx = RoomTransactionRunner(db),
)
