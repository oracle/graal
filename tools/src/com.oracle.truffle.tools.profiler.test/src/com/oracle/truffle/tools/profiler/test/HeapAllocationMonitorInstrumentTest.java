package com.oracle.truffle.tools.profiler.test;

import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.profiler.HeapAllocationMonitor;
import com.oracle.truffle.tools.profiler.MetaObjInfo;

public class HeapAllocationMonitorInstrumentTest extends AbstractProfilerTest {
    private HeapAllocationMonitor monitor;

    @Before
    public void setupTracer() {
        monitor = HeapAllocationMonitor.find(context.getEngine());
        Assert.assertNotNull(monitor);
    }

    @Test
    public void testNoAllocations() throws InterruptedException {
        Assert.assertFalse(monitor.isCollecting());
        Assert.assertFalse(monitor.hasData());

        monitor.setCollecting(true);

        Assert.assertTrue(monitor.isCollecting());
        Assert.assertFalse(monitor.hasData());

        for (int i = 0; i < 10; i++) {
            eval(defaultSource);
            Assert.assertEquals("Data in snapshot without allocations!", 0, monitor.snapshot().length);
        }
    }

    final Source oneAllocationSource = makeSource("ROOT(" + "DEFINE(foo,ROOT(STATEMENT))," + "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," + "ALLOCATION,CALL(baz),CALL(bar)" + ")");

    @Test
    public void testAllocations() throws InterruptedException {
        Assert.assertFalse(monitor.isCollecting());
        Assert.assertFalse(monitor.hasData());

        monitor.setCollecting(true);

        Assert.assertTrue(monitor.isCollecting());
        Assert.assertFalse(monitor.hasData());

        for (int i = 0; i < 10; i++) {
            eval(oneAllocationSource);
            final MetaObjInfo[] snapshot = monitor.snapshot();
            Assert.assertEquals("Incorrect snapshot size", 1, snapshot.length);
            final MetaObjInfo metaObjInfo = snapshot[0];
            Assert.assertEquals(i + 1, metaObjInfo.getAllocatedInstancesCount());
            Assert.assertEquals(InstrumentationTestLanguage.NAME, metaObjInfo.getLanguage());
            Assert.assertEquals("Integer", metaObjInfo.getName());

        }
    }

    @Test
    public void testActivatedDuringExec() throws InterruptedException {
        Assert.assertFalse(monitor.isCollecting());
        Assert.assertFalse(monitor.hasData());

        final Thread thread = new Thread(() -> {
            while (true) {
                eval(oneAllocationSource);
                if (Thread.interrupted()) {
                    break;
                }
            }
        });
        thread.start();

        try {
            Thread.sleep(50);
            monitor.setCollecting(true);
            Thread.sleep(50);
            Assert.assertTrue(monitor.isCollecting());
            Assert.assertTrue(monitor.hasData());
            for (int i = 0; i < 10; i++) {
                final MetaObjInfo[] snapshot = monitor.snapshot();
                Assert.assertEquals("Incorrect snapshot size", 1, snapshot.length);
                final MetaObjInfo metaObjInfo = snapshot[0];
                Assert.assertTrue(1 < metaObjInfo.getAllocatedInstancesCount());
                Assert.assertEquals(InstrumentationTestLanguage.NAME, metaObjInfo.getLanguage());
                Assert.assertEquals("Integer", metaObjInfo.getName());
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Assert.fail("Interruption.");
        } finally {
            thread.interrupt();
        }
        thread.join();
    }
}
