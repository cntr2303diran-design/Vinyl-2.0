package com.vinyl.launcher.data.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SerialWorkerTest {
    @Test
    fun jobsRunOneByOneInTheOrderTheyWerePosted() {
        val worker = SerialWorker()
        val seen = Collections.synchronizedList(ArrayList<Int>())
        repeat(300) { i -> worker.post { seen.add(i) } }
        worker.drain()
        assertEquals((0 until 300).toList(), seen.toList())
    }

    @Test
    fun jobsRunOffTheCallingThread() {
        val worker = SerialWorker()
        val where = arrayOfNulls<Thread>(1)
        worker.post { where[0] = Thread.currentThread() }
        worker.drain()
        assertNotSame(Thread.currentThread(), where[0])
    }

    @Test
    fun postingNeverBlocksEvenWhileAJobIsRunning() {
        val worker = SerialWorker()
        val release = CountDownLatch(1)
        val second = AtomicBoolean(false)
        worker.post { release.await(10, TimeUnit.SECONDS) }
        worker.post { second.set(true) }
        // post() sudah kembali padahal pekerjaan pertama masih menahan antrean; yang kedua belum jalan
        assertFalse(second.get())
        release.countDown()
        worker.drain()
        assertTrue(second.get())
    }

    @Test
    fun theDefaultWorkerRunsOnItsOwnLowestPriorityDaemonThread() {
        // Pekerjaan Keystore tidak mendesak: tidak boleh berebut CPU dengan thread utama saat beranda pertama digambar
        val worker = SerialWorker()
        val where = arrayOfNulls<Thread>(1)
        worker.post { where[0] = Thread.currentThread() }
        worker.drain()
        val thread = where[0]!!
        assertEquals("vinyl-secure", thread.name)
        assertEquals(Thread.MIN_PRIORITY, thread.priority)
        assertTrue(thread.isDaemon)
    }

    @Test
    fun eachDefaultWorkerHasItsOwnThread() {
        val a = SerialWorker()
        val b = SerialWorker()
        val threads = arrayOfNulls<Thread>(2)
        a.post { threads[0] = Thread.currentThread() }
        b.post { threads[1] = Thread.currentThread() }
        a.drain()
        b.drain()
        // Satu antrean yang macet (mis. Keystore tidak menjawab) tidak menahan antrean lain
        assertNotSame(threads[0], threads[1])
    }

    @Test
    fun aFailingJobDoesNotStopTheQueue() {
        val worker = SerialWorker()
        val after = AtomicBoolean(false)
        worker.post { throw IllegalStateException("pekerjaan gagal") }
        worker.post { after.set(true) }
        worker.drain()
        assertTrue(after.get())
    }
}
