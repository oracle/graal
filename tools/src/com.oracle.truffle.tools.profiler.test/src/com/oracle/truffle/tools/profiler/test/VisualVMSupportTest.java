package com.oracle.truffle.tools.profiler.test;

import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.tools.profiler.CPUSampler;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

public class VisualVMSupportTest extends AbstractProfilerTest {

    Source defaultSourceForSampling = makeSource("ROOT(" +
                    "DEFINE(foo,ROOT(SLEEP(1)))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," +
                    "CALL(baz),CALL(bar)" +
                    ")");

    @Test
    @SuppressWarnings("unchecked")
    public void test() {
        List<WeakReference<CPUSampler>> samplers = (List<WeakReference<CPUSampler>>) ReflectionUtils.getStaticField(CPUSampler.class, "allInstances");
        CPUSampler sampler = samplers.iterator().next().get();
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (true) {
                    execute(defaultSourceForSampling);
                }
            }
        };
        Thread execThread = new Thread(runnable);
        execThread.start();
        try {
            // NOTE: Execution is still running in a separate thread.
            for (int i = 0; i < 30; i++) {
                Map<Thread, StackTraceElement[]> sample = (Map<Thread, StackTraceElement[]>) ReflectionUtils.invoke(sampler, "takeSample");
                if (sample != null) {
                    StackTraceElement[] stackTraceElements = sample.get(execThread);
                    reverse(stackTraceElements);
                    for (StackTraceElement element : stackTraceElements) {
                        Assert.assertEquals("InstrumentTestLang", element.getClassName());
                    }
                    if (stackTraceElements.length >= 1) {
                        Assert.assertEquals("", stackTraceElements[0].getMethodName());
                    }
                    if (stackTraceElements.length >= 2) {
                        String methodName = stackTraceElements[1].getMethodName();
                        Assert.assertTrue("baz".equals(methodName) || "bar".equals(methodName));
                    }
                    if (stackTraceElements.length >= 3) {
                        String methodName = stackTraceElements[2].getMethodName();
                        Assert.assertTrue("bar".equals(methodName) || "foo".equals(methodName));
                    }
                    if (stackTraceElements.length == 4) {
                        Assert.assertEquals("foo", stackTraceElements[3].getMethodName());
                    }
                    if (stackTraceElements.length > 4) {
                        Assert.fail("too many stack frames.");
                    }
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        } finally {
            execThread.interrupt();
        }
    }

    private void reverse(StackTraceElement[] stackTraceElements) {
        for (int i = 0; i < stackTraceElements.length / 2; i++) {
            StackTraceElement temp = stackTraceElements[i];
            stackTraceElements[i] = stackTraceElements[stackTraceElements.length - i - 1];
            stackTraceElements[stackTraceElements.length - i - 1] = temp;
        }
    }

    // TODO why is this needed?
    @Before
    public void setupSampler() {
        CPUSampler sampler = CPUSampler.find(context.getEngine());
        synchronized (sampler) {
            sampler.setGatherSelfHitTimes(true);
            sampler.setDelaySamplingUntilNonInternalLangInit(false);
            sampler.setVisualVM(true);
        }
    }
}
