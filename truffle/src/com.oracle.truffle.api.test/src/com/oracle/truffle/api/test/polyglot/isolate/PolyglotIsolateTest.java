/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot.isolate;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage.evalTestLanguage;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.oracle.truffle.api.test.OSUtils;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.TestAPIAccessor;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;
import org.graalvm.nativebridge.Isolate;
import org.graalvm.nativebridge.IsolateThread;
import org.graalvm.nativebridge.ProcessIsolate;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;
import org.graalvm.polyglot.management.ExecutionEvent;
import org.graalvm.polyglot.management.ExecutionListener;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.SubprocessTestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.PolyglotCachingTest;
import com.oracle.truffle.tck.tests.ValueAssert;
import com.oracle.truffle.tck.tests.ValueAssert.Trait;

public class PolyglotIsolateTest {

    @BeforeClass
    public static void loadTestIsolate() {
        // needs to be provided by the test run
        Assume.assumeNotNull(System.getProperty("polyglot.engine.IsolateLibrary"));
    }

    @SuppressWarnings("unused")
    private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
        StringBuilder threadDump = new StringBuilder(System.lineSeparator());
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
            threadDump.append(threadInfo.toString());
            threadDump.append("FULL STACK TRACE\n");
            int i = 0;
            StackTraceElement[] stackTrace = threadInfo.getStackTrace();
            for (; i < stackTrace.length; i++) {
                StackTraceElement ste = stackTrace[i];
                threadDump.append("\tat ").append(ste.toString());
                threadDump.append('\n');
                if (i == 0 && threadInfo.getLockInfo() != null) {
                    Thread.State ts = threadInfo.getThreadState();
                    switch (ts) {
                        case BLOCKED:
                            threadDump.append("\t-  blocked on ").append(threadInfo.getLockInfo());
                            threadDump.append('\n');
                            break;
                        case WAITING:
                            threadDump.append("\t-  waiting on ").append(threadInfo.getLockInfo());
                            threadDump.append('\n');
                            break;
                        case TIMED_WAITING:
                            threadDump.append("\t-  waiting on ").append(threadInfo.getLockInfo());
                            threadDump.append('\n');
                            break;
                        default:
                    }
                }

                for (MonitorInfo mi : threadInfo.getLockedMonitors()) {
                    if (mi.getLockedStackDepth() == i) {
                        threadDump.append("\t-  locked ").append(mi);
                        threadDump.append('\n');
                    }
                }
            }
            threadDump.append("\n");
        }
        return threadDump.toString();
    }

    @Test
    public void testSLMemory() {
        // Truffle isolate specific test
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("engine.IsolateOption.MaxHeapSize",
                        "128m").build()) {
            context.eval("sl", "function main() {\n" +
                            "  i = 0;\n" +
                            "  j = 1;\n" +
                            "  s = \"a\";\n" +
                            "  while (i < 280) {\n" +
                            "    s = s + s;\n" +
                            "    i = i + 1;\n" +
                            "    j = j + j;\n" +
                            "  }\n" +
                            "  return j;\n" +
                            "}\n");
            fail();
        } catch (PolyglotException pe) {
            assertTrue(pe.isResourceExhausted());
        }
    }

    @HostReflection
    public static final class ObjectWithCallback {
        @SuppressWarnings("static-method")
        @HostAccess.Export
        public String callback(int i) {
            return String.valueOf(i);
        }
    }

    @Test
    public void testSLHostCallStackHeadRoom() {
        assumeFalse("StackHeadRoom in not used in process isolation", TruffleTestAssumptions.isExternalIsolate());
        // Truffle isolate specific test
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("engine.HostCallStackHeadRoom",
                        "500KB").build()) {
            ObjectWithCallback hostObject = new ObjectWithCallback();
            context.eval("sl", "function createNewObject() {\n" +
                            "  return new();\n" +
                            "}\n" +
                            "\n" +
                            "function rec(obj, n) {\n" +
                            "  if (n < 1) {\n" +
                            "    ret = new();\n" +
                            "    ret.val = obj.hostObject.callback(5);\n" +
                            "    return ret;\n" +
                            "  } else {\n" +
                            "    return rec(obj, n - 1);\n" +
                            "  }\n" +
                            "}\n");
            Value bindings = context.getBindings("sl");
            Value obj = bindings.getMember("createNewObject").execute();
            obj.putMember("hostObject", hostObject);
            Value v = bindings.getMember("rec").execute(obj, 200);
            assertEquals("5", v.getMember("val").asString());
            int recursionIncrement = 100;
            int initialRecursionDepth = 750;
            for (int recursionDepth = initialRecursionDepth;; recursionDepth += recursionIncrement) {
                try {
                    bindings.getMember("rec").execute(obj, recursionDepth);
                } catch (PolyglotException pe) {
                    assertFalse(pe.isInternalError());
                    assertTrue(pe.getMessage().contains("Not enough stack space to perform a host call from isolated guest language."));
                    break;
                }
            }
        }
    }

    @Test
    public void testValueCleaner() throws IOException, InterruptedException {
        // Truffle isolate specific test
        Assume.assumeTrue(GCUtils.isSupported());
        Runnable runnable = () -> {
            try (Context context = Context.newBuilder().allowHostAccess(HostAccess.newBuilder(HostAccess.ALL).build()).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
                context.getBindings("triste").putMember("delegate", new TestObject());
                // Create a reference that will delegate all calls to "delegate" TestObject.
                Value callbackReference = context.eval("triste", "returnWeaklyReachableGuestObject(callback(create),1)");
                // Get the reference again, now from the guest weak map.
                Value callbackReference2 = context.eval("triste", "returnWeaklyReachableGuestObject(callback(),1)");
                /*
                 * Test that the first reference works. This ensures that the weak reference on the
                 * guest was not cleared before we obtained the second reference.
                 */
                assertFalse(callbackReference.isNull());
                Value v = callbackReference.getMember("callback").execute("Hello world!");
                assertEquals("Hello world!", v.asString());
                /*
                 * Clear the first reference, this should not clear the weak reference on the guest
                 * as we now also have the second reference.
                 */
                WeakReference<Value> callbackWeakReference = new WeakReference<>(callbackReference);
                callbackReference = null;
                GCUtils.assertGc("callbackReference should be freed!", callbackWeakReference);
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                // Get the reference again from the guest weak map.
                Value callbackReference3 = context.eval("triste", "returnWeaklyReachableGuestObject(callback(),1)");
                /*
                 * Test that the second reference works. This ensures that the weak reference on the
                 * guest was not cleared before we obtained the third reference. Test also that the
                 * third reference works.
                 */
                assertFalse(callbackReference2.isNull());
                assertFalse(callbackReference3.isNull());
                v = callbackReference2.getMember("callback").execute("Hello world!");
                assertEquals("Hello world!", v.asString());
                v = callbackReference3.getMember("callback").execute("Hello world!");
                assertEquals("Hello world!", v.asString());
                // Clear the second and the third reference, this should clear the weak reference on
                // the
                // guest.
                WeakReference<Value> callbackWeakReference2 = new WeakReference<>(callbackReference2);
                WeakReference<Value> callbackWeakReference3 = new WeakReference<>(callbackReference3);
                callbackReference2 = null;
                callbackReference3 = null;
                GCUtils.assertGc("callbackReference2 should be freed!", callbackWeakReference2);
                GCUtils.assertGc("callbackReference2 should be freed!", callbackWeakReference3);
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                // Check that the weak reference on the guest was cleared.
                Value callbackReference4 = context.eval("triste", "returnWeaklyReachableGuestObject(callback(),1)");
                assertTrue(callbackReference4.isNull());
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    @HostReflection
    public static class TestObject {
        volatile String callbackArgument;

        public String callback(String msg) {
            callbackArgument = msg;
            return msg;
        }
    }

    @Test
    public void testHostObjectCallback() {
        // Not a truffe isolate specific test, but tests basic triste language functionality and
        // thus complements other tests
        TestObject hostObject = new TestObject();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            ValueAssert.assertValue(binding);
            binding.putMember("hostObject", hostObject);
            Value ho = binding.getMember("hostObject");
            ValueAssert.assertValue(ho, Trait.MEMBERS, Trait.HOST_OBJECT);
            assertTrue(ho.hasMember("callback"));
            assertTrue(ho.canInvokeMember("callback"));
            Value cb = ho.getMember("callback");
            ValueAssert.assertValue(cb, Trait.EXECUTABLE, Trait.HOST_OBJECT);
            String s = "test";
            Value guestString = cb.execute(s);
            ValueAssert.assertValue(guestString, Trait.STRING);
            assertTrue(guestString.isString());
            assertEquals(guestString.asString(), "test");
        }
    }

    @Test
    public void testHostObjectCallbackSpawned() {
        // Not a truffe isolate specific test, but tests basic triste language functionality and
        // this complements other tests
        TestObject hostObject = new TestObject();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            ValueAssert.assertValue(binding);
            binding.putMember("hostObject", hostObject);
            Value ho = binding.getMember("hostObject");
            ValueAssert.assertValue(ho, Trait.MEMBERS, Trait.HOST_OBJECT);
            assertTrue(ho.hasMember("callback"));
            assertTrue(ho.canInvokeMember("callback"));
            Value cb = ho.getMember("callback");
            ValueAssert.assertValue(cb, Trait.EXECUTABLE, Trait.HOST_OBJECT);
            context.eval("triste", "spawnHostObjectCall(callback(5),1)");
            assertEquals("5", hostObject.callbackArgument);
        }
    }

    @Test
    public void testMemoryProtection() throws Exception {
        assumeFalse(TruffleTestAssumptions.isExternalIsolate());
        // Truffle isolate specific test
        Assume.assumeNotNull(getJavaExecutable()); // Has java executable
        SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, () -> {
            Context c1 = Context.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("engine.IsolateMemoryProtection", "true").build();
            Context c2 = Context.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("engine.IsolateMemoryProtection", "true").build();
            Value mem = c1.eval("triste", "alloc(ignored(1024),1)");
            // this will fail and crash the jvm
            c2.eval("triste", "access(ignored(" + mem.asNativePointer() + "),1)");
        }).failOnNonZeroExit(false).onExit((p) -> {
            boolean testSuccess = false;
            for (String line : p.output) {
                if (line.contains("SIGSEGV") || line.contains("Memory Protection not available") || line.contains("Setting the protection of the heap memory failed")) {
                    testSuccess = true;
                    break;
                }
            }
            assertTrue(testSuccess);
        }).run();
    }

    @Test
    public void testActiveEngines() throws Exception {
        // Truffle isolate specific test
        // There may be unclosed engines from other test runs which we need to ignore.
        Set<Engine> enginesToIgnore = findActiveEngines();
        Set<Engine> activeEngines;
        Engine eng1 = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        try {
            Engine eng2 = Engine.newBuilder().build();
            try {
                Engine eng3 = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
                try {
                    activeEngines = findActiveEngines();
                    activeEngines.removeAll(enginesToIgnore);
                    assertEquals(Set.of(eng1, eng2, eng3), activeEngines);
                } finally {
                    eng3.close();
                }
                activeEngines = findActiveEngines();
                activeEngines.removeAll(enginesToIgnore);
                assertEquals(Set.of(eng1, eng2), activeEngines);
            } finally {
                eng2.close();
            }
            activeEngines = findActiveEngines();
            activeEngines.removeAll(enginesToIgnore);
            assertEquals(Set.of(eng1), activeEngines);
        } finally {
            eng1.close();
        }
        activeEngines = findActiveEngines();
        activeEngines.removeAll(enginesToIgnore);
        assertTrue(activeEngines.isEmpty());
    }

    @Test
    public void testEngineClose() {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.
        Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        engine.close(true);
        engine.close(true);
        Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        context.close(true);
        context.close(true);
        engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        context = Context.newBuilder().engine(engine).build();
        engine.close(true);
        context.close(true);
        engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        context = Context.newBuilder().engine(engine).build();
        context.close(true);
        engine.close(true);
    }

    @Test
    public void testContextCloseWithUnbalancedExplicitEnter() {
        // Not a truffe isolate specific test. Until ContextAPITest is refactored to be
        // able to run with truffle isolate, we need this test here.
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            context.enter();
            context.enter();
        }

        try (Context context1 = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            context1.enter();
            assertEquals(context1, Context.getCurrent());
            try (Context context2 = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
                context2.enter();
                assertEquals(context2, Context.getCurrent());
            }
            assertEquals(context1, Context.getCurrent());
            context1.getBindings("triste");
        }

        try (Context context1 = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
            try (Context context2 = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
                context2.enter();
                Value binding = context2.getBindings("triste");
                CancelCallBack callback = new CancelCallBack(context1);
                binding.putMember("hostObject", callback);
                context2.eval("triste", "hostObjectCall(cancel(42),1)");
            }
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }

        try (Context context1 = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Context context2 = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
            context2.enter();
            context1.enter();
            context2.enter();
            assertEquals(context2, Context.getCurrent());
            AbstractPolyglotTest.assertFails(() -> context2.close(), IllegalStateException.class);
            context1.leave();
            context2.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Engine> findActiveEngines() throws ReflectiveOperationException {
        Method m = Engine.class.getDeclaredMethod("findActiveEngines");
        m.setAccessible(true);
        return new HashSet<>((Collection<Engine>) m.invoke(null));
    }

    /**
     * Tests Context.enter and Context.leave.
     */
    @Test
    public void testExplicitEnter() {
        // Not a truffe isolate specific test. Until ContextAPITest is refactored to be
        // able to run with truffle isolate, we need this test here.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            context.enter();
            try {
                Value binding = context.getBindings("triste");
                ValueAssert.assertValue(binding);
                context.enter();
                try {
                    binding.putMember("hostObject", new Object());
                } finally {
                    context.leave();
                }
                Value ho = binding.getMember("hostObject");
                ValueAssert.assertValue(ho, Trait.MEMBERS, Trait.HOST_OBJECT);
            } finally {
                context.leave();
            }
        }

        try (Context context1 = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
                        Context context2 = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            context1.enter();
            AbstractPolyglotTest.assertFails(context2::leave, IllegalStateException.class);
        }
    }

    /**
     * Tests multi threaded access.
     */
    @Test
    public void testIsolateUsedByMultipleThreads() throws Exception {
        // Not a truffe isolate specific test. Until multi-threaded truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            ExecutorService executor = Executors.newCachedThreadPool();
            int workerCount = 10;
            Phaser ready = new Phaser();
            ready.bulkRegister(workerCount);
            try {
                List<Future<?>> results = new ArrayList<>();
                for (int i = 0; i < workerCount; i++) {
                    results.add(executor.submit(new Worker(engine, ready)));
                }
                for (Future<?> result : results) {
                    result.get();
                }
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
            }
        }
    }

    // This case throws because polyglot isolates currently use the native thread identity to map
    // threads in isolates, and that does not work for virtual threads.
    @Test
    public void testIsolateUsedFromHostVirtualThread() throws Throwable {
        AbstractPolyglotTest.runInVirtualThread(() -> {
            Context ctx = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
            try {
                AbstractPolyglotTest.assertFails(() -> ctx.eval("sl", "function main() { return 42; }"),
                                IllegalStateException.class, "Using isolated polyglot contexts together with Java virtual threads is currently not supported.");
            } finally {
                // close() also throws because it calls enterThreadChanged
                AbstractPolyglotTest.assertFails(ctx::close,
                                IllegalStateException.class, "Using isolated polyglot contexts together with Java virtual threads is currently not supported.");
            }
        });
    }

    // This case throws because upcalls to the host from an isolate VirtualThread would pick the
    // wrong host thread (a platform thread and not a virtual thread).
    @Test
    public void testLanguageSpawnVirtualThread() {
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = ctx.getBindings("triste");
            binding.putMember("hostObject", new CallBack(20));
            AbstractPolyglotTest.assertFails(() -> ctx.eval("triste", "spawnHostObjectCallVirtual(callback(0),1)"),
                            PolyglotException.class, "java.lang.IllegalStateException: Using isolated polyglot contexts together with Java virtual threads is currently not supported.");
        }
    }

    @Test
    public void testExecutionListener() {
        // Not a truffe isolate specific test. Until instrumentation truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        List<String> events = new ArrayList<>();
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            String sourceName = "fact.sl";
            String sourceMime = "application/x-sl";
            String sourceLanguage = "sl";
            URI sourceURI = URI.create("file:///tmp/fact.sl");
            String sourceContent = "function fact(n) { if (n == 0) { return 1; } else { return n * fact(n-1); } } function main() { return fact(4); }";
            String functionName = "fact";
            ExecutionListener.newBuilder().sourceFilter((source) -> sourceName.equals(source.getName())).rootNameFilter(functionName::equals).roots(true).statements(true).expressions(
                            true).onEnter((e) -> {
                                SourceSection sourceSection = e.getLocation();
                                if (sourceSection != null && sourceSection.isAvailable()) {
                                    int len = sourceSection.getCharLength();
                                    CharSequence chars = sourceSection.getCharacters();
                                    assertEquals(len, chars.length());
                                    assertEquals(sourceSection, e.getLocation());
                                    Source src = sourceSection.getSource();
                                    assertEquals(sourceName, src.getName());
                                    assertEquals(sourceMime, src.getMimeType());
                                    assertEquals(sourceLanguage, src.getLanguage());
                                    assertEquals(sourceURI, src.getURI());
                                    assertEquals(sourceContent, src.getCharacters().toString());
                                    try (Reader reader = src.getReader()) {
                                        assertEquals(sourceContent, readFully(reader));
                                    } catch (IOException ioe) {
                                        throw new AssertionError(ioe);
                                    }
                                    try (@SuppressWarnings("deprecation")
                                    InputStream in = src.getInputStream()) {
                                        assertEquals(sourceContent, readFully(in));
                                    } catch (IOException ioe) {
                                        throw new AssertionError(ioe);
                                    }
                                }
                                events.add(eventToString("enter", e));
                            }).onReturn((e) -> events.add(eventToString("leave", e))).attach(context.getEngine());
            Source src = Source.newBuilder(sourceLanguage, sourceContent, sourceName).mimeType(sourceMime).uri(sourceURI).buildLiteral();
            Value v = context.eval(src);
            assertEquals(v.asInt(), 24);
        }
        assertTrue(events.contains("enter root in fact"));
        assertTrue(events.contains("leave root in fact"));
        assertTrue(events.contains("enter statement in fact"));
        assertTrue(events.contains("leave statement in fact"));
        assertTrue(events.contains("enter expression in fact"));
        assertTrue(events.contains("leave expression in fact"));
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder res = new StringBuilder();
        try (BufferedReader in = new BufferedReader(reader)) {
            char[] buffer = new char[4];
            int len;
            do {
                len = in.read(buffer, 0, buffer.length);
                if (len > 0) {
                    res.append(buffer, 0, len);
                }
            } while (len > 0);
        }
        return res.toString();
    }

    private static String readFully(InputStream stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BufferedInputStream in = new BufferedInputStream(stream)) {
            byte[] buffer = new byte[4];
            int len;
            do {
                len = in.read(buffer, 0, buffer.length);
                if (len > 0) {
                    out.write(buffer, 0, len);
                }
            } while (len > 0);
        }
        return out.toString();
    }

    private static String eventToString(String eventKind, ExecutionEvent e) {
        String nodeKind;
        if (e.isExpression()) {
            nodeKind = "expression";
        } else if (e.isStatement()) {
            nodeKind = "statement";
        } else if (e.isRoot()) {
            nodeKind = "root";
        } else {
            nodeKind = "???";
        }
        return String.format("%s %s in %s", eventKind, nodeKind, e.getRootName());
    }

    @Test
    public void testCurrentContext() {
        // Truffle isolate specific test
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context1 = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
                        Context context2 = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).build();
                        Context context3 = Context.newBuilder("triste").allowCreateThread(true).allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate",
                                        "true").build()) {

            // Verify explicit enter, leave
            AbstractPolyglotTest.assertFails(Context::getCurrent, IllegalStateException.class);
            context1.enter();
            try {
                context2.enter();
                try {
                    context3.enter();
                    try {
                        assertEquals(context3, Context.getCurrent());
                    } finally {
                        context3.leave();
                    }
                    assertEquals(context2, Context.getCurrent());
                } finally {
                    context2.leave();
                }
                assertEquals(context1, Context.getCurrent());
                AbstractPolyglotTest.assertFails(() -> Context.getCurrent().close(), IllegalStateException.class);
            } finally {
                context1.leave();
            }
            AbstractPolyglotTest.assertFails(Context::getCurrent, IllegalStateException.class);

            // Verify that host code is executed in entered host context
            ContextVerifier contextVerifier = new ContextVerifier();
            Value binding = context3.getBindings("triste");
            binding.putMember("hostObject", contextVerifier);
            context3.eval("triste", "hostObjectCall(callback(42),1)");
            assertEquals(context3, contextVerifier.context);
            AbstractPolyglotTest.assertFails(Context::getCurrent, IllegalStateException.class);

            contextVerifier.context = null;
            Value parsed = context3.parse("triste", "hostObjectCall(callback(42),1)");
            parsed.execute();
            assertEquals(context3, contextVerifier.context);
            AbstractPolyglotTest.assertFails(Context::getCurrent, IllegalStateException.class);

            // Verify that host code executed by polyglot thread is executed in entered host context
            contextVerifier.context = null;
            context3.eval("triste", "spawnHostObjectCall(callback(42),1)");
            assertEquals(context3, contextVerifier.context);
            AbstractPolyglotTest.assertFails(Context::getCurrent, IllegalStateException.class);
        }
    }

    @Test
    public void testValueGetContext() {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context1 = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
                        Context context2 = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            ArgumentRetriever argumentRetriever = new ArgumentRetriever();
            Value binding = context1.getBindings("triste");
            binding.putMember("hostObject", argumentRetriever);
            binding = context2.getBindings("triste");
            binding.putMember("hostObject", argumentRetriever);

            context1.eval("triste", "guestToHostThrow(verify(TruffleException),1)");
            assertNotNull(argumentRetriever.argument);
            assertEquals(context1, argumentRetriever.argument.getContext());
            argumentRetriever.argument.getContext().eval("triste", "hostObjectCall(identity(42),1)");

            context2.eval("triste", "guestToHostThrow(verify(TruffleException),1)");
            assertNotNull(argumentRetriever.argument);
            assertEquals(context2, argumentRetriever.argument.getContext());
            argumentRetriever.argument.getContext().eval("triste", "hostObjectCall(identity(42),1)");
        }
    }

    @HostReflection
    public static final class ArgumentRetriever {

        private Value argument;

        ArgumentRetriever() {
        }

        public void verify(Value object) {
            this.argument = object;
        }

        @SuppressWarnings("static-method")
        public String identity(String arg) {
            return arg;
        }
    }

    @Test
    public void testContextGCed() throws IOException, InterruptedException {
        /*
         * TODO GR-39016: Move to PolyglotGCTest. But we run only PolyglotIsolateTest in a native
         * mode.
         */
        if (ImageInfo.inImageCode()) {
            testContextGCedImpl();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, PolyglotIsolateTest::testContextGCedImpl).run();
        }
    }

    private static void testContextGCedImpl() {
        // Test bound engine
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
        useContext(context);
        context.close();
        Reference<Context> contextRef = new WeakReference<>(context);
        context = null;
        GCUtils.assertGc("Context must be freed.", contextRef);
        assertTrue(isolate.isDisposed());

        // Test explicit engine
        Engine engine = Engine.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        context = Context.newBuilder("triste").engine(engine).allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).build();
        isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(engine);
        useContext(context);
        context.close();
        contextRef = new WeakReference<>(context);
        context = null;
        GCUtils.assertGc("Context must be freed.", contextRef);
        assertFalse(isolate.isDisposed());
        engine.close();
        assertTrue(isolate.isDisposed());

        // Test unclosed context
        context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
        useContext(context);
        contextRef = new WeakReference<>(context);
        WeakReference<Engine> engineRef = new WeakReference<>(context.getEngine());
        context = null;
        GCUtils.assertGc("Context must be freed.", contextRef);
        assertIsolateDisposed(engineRef, isolate);

        // Test explicit engine with unclosed contexts
        Engine engine2 = Engine.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(engine2);
        Context engine2Context1 = Context.newBuilder("triste").engine(engine2).allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).build();
        Context engine2Context2 = Context.newBuilder("triste").engine(engine2).allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).build();
        useContext(engine2Context1);
        useContext(engine2Context2);
        WeakReference<?> engine2Context1Ref = new WeakReference<>(engine2Context1);
        WeakReference<?> engine2Context2Ref = new WeakReference<>(engine2Context2);
        engine2Context1 = null;
        engine2Context2 = null;
        GCUtils.assertGc("Context must be freed.", engine2Context1Ref);
        GCUtils.assertGc("Context must be freed.", engine2Context2Ref);
        Context engine2Context3 = Context.newBuilder("triste").engine(engine2).allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).build();
        useContext(engine2Context3);
        WeakReference<?> engine2Context3Ref = new WeakReference<>(engine2Context3);
        engine2Context3 = null;
        GCUtils.assertGc("Context must be freed.", engine2Context3Ref);
        assertFalse(isolate.isDisposed());
        WeakReference<Engine> engine2Ref = new WeakReference<>(engine2);
        engine2 = null;
        assertIsolateDisposed(engine2Ref, isolate);

        // Test close engine
        context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        engine = context.getEngine();
        isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(engine);
        useContext(context);
        contextRef = new WeakReference<>(context);
        context = null;
        engine.close();
        GCUtils.assertGc("Context must be freed.", contextRef);
        assertTrue(isolate.isDisposed());

        // Test close engine after context gced.
        context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        engine = context.getEngine();
        isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(engine);
        useContext(context);
        contextRef = new WeakReference<>(context);
        context = null;
        GCUtils.assertGc("Context must be freed.", contextRef);
        engine.close();
        assertTrue(isolate.isDisposed());
    }

    private static void assertIsolateDisposed(WeakReference<Engine> engineRef, Isolate<?> isolate) {
        GCUtils.assertGc("Engine must be freed.", engineRef);
        if (OSUtils.isWindows() && ImageInfo.inImageRuntimeCode() && engineRef.get() != null) {
            /*
             * GCUtils.assertGc is not supported on Windows in native-image mode, as heap dump
             * functionality is currently unavailable.
             */
            return;
        }
        long deadLine = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
        while (System.currentTimeMillis() < deadLine) {
            Context.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build().close();
            if (isolate.isDisposed()) {
                break;
            }
        }
        assertTrue("Isolate must be disposed.", isolate.isDisposed());
    }

    private static void useContext(Context context) {
        ContextVerifier contextVerifier = new ContextVerifier();
        Value binding = context.getBindings("triste");
        binding.putMember("hostObject", contextVerifier);
        context.eval("triste", "hostObjectCall(callback(42),1)");
        assertEquals(context, contextVerifier.context);
        contextVerifier.context = null;
        context.eval("triste", "spawnHostObjectCall(callback(42),1)");
        assertEquals(context, contextVerifier.context);
        contextVerifier.context = null;
    }

    @Test
    public void testSourceCache() throws IOException, InterruptedException {
        // Truffe isolate specific test
        assumeTrue(GCUtils.isSupported());
        Runnable runnable = () -> {
            HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
            String sourceName = "test_source.triste";
            TestHandler handler = new TestHandler(sourceName);
            try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).logHandler(handler).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option(
                            "engine.TraceCompilation", "true").option("engine.BackgroundCompilation", "false").option("engine.CompileImmediately", "true").build()) {
                ContextVerifier contextVerifier = new ContextVerifier();
                Value binding = context.getBindings("triste");
                binding.putMember("hostObject", contextVerifier);
                Source source = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", sourceName).buildLiteral();
                context.eval(source);
                assertEquals(1, handler.compilations.getAndSet(0));
                context.eval(source);
                assertEquals(0, handler.compilations.getAndSet(0));
                WeakReference<Source> source1Ref = new WeakReference<>(source);
                source = null;
                GCUtils.assertGc("Source not collected.", source1Ref);
                int compilations = 0;
                for (int i = 0; i < 100 && compilations == 0; i++) {
                    TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                    source = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", sourceName).buildLiteral();
                    context.eval(source);
                    source1Ref = new WeakReference<>(source);
                    source = null;
                    GCUtils.assertGc("Source not collected.", source1Ref);
                    compilations += handler.compilations.getAndSet(0);
                }
                assertEquals(1, compilations);
                Source uncachedSource = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", sourceName).cached(false).buildLiteral();
                context.eval(uncachedSource);
                assertEquals(1, handler.compilations.getAndSet(0));
                context.eval(uncachedSource);
                assertEquals(1, handler.compilations.getAndSet(0));
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    /*
     * Test that CallTargets stay cached as long as their source instance is alive.
     */
    @Test
    public void testParsedASTIsNotCollectedIfSourceIsAlive() {
        // Truffe isolate specific test
        assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

        AtomicInteger parseCalled = new AtomicInteger(0);
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            // needs to stay alive
            Source source = Source.create(PolyglotCachingTest.ParseCounterTestLanguage.ID, "0");

            assertEquals(0, parseCalled.get());
            evalTestLanguage(context, PolyglotCachingTest.ParseCounterTestLanguage.class, source, parseCalled);
            assertEquals(1, parseCalled.get());
            for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                // cache should stay valid and never be collected as long as the source is alive.
                System.gc();
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                System.gc();
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                evalTestLanguage(context, PolyglotCachingTest.ParseCounterTestLanguage.class, Source.create(PolyglotCachingTest.ParseCounterTestLanguage.ID, "0"), parseCalled);
                assertEquals(1, parseCalled.get());
            }
            assertEquals("0", source.getCharacters().toString());
        }
    }

    /*
     * Test that CallTargets can get collected when their source instance is not alive.
     */
    @Test
    public void testParsedASTIsCollectedIfSourceIsNotAlive() {
        // Truffe isolate specific test
        assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));

        AtomicInteger parseCalled = new AtomicInteger(0);
        try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            // must not stay alive
            Source source = Source.create(PolyglotCachingTest.ParseCounterTestLanguage.ID, "0");

            assertEquals(0, parseCalled.get());
            evalTestLanguage(context, PolyglotCachingTest.ParseCounterTestLanguage.class, source, parseCalled);
            assertEquals(1, parseCalled.get());
            source = null;
            for (int i = 0; i < GCUtils.GC_TEST_ITERATIONS; i++) {
                // cache should stay valid and never be collected as long as the source is alive.
                System.gc();
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                System.gc();
                TestAPIAccessor.ISOLATE.triggerIsolateGC(context.getEngine());
                evalTestLanguage(context, PolyglotCachingTest.ParseCounterTestLanguage.class, Source.create(PolyglotCachingTest.ParseCounterTestLanguage.ID, "0"), parseCalled);
            }
            assertTrue(parseCalled.get() > 1);
        }
    }

    @Test
    public void testGetCachedSources() throws IOException, InterruptedException {
        // Truffe isolate specific test
        assumeTrue(GCUtils.isSupported());
        Runnable runnable = () -> {
            HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
            try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
                assertEquals(0, engine.getCachedSources().size());
                try (Context context1 = Context.newBuilder("triste").engine(engine).allowHostAccess(accessPolicy).build();
                                Context context2 = Context.newBuilder("triste").engine(engine).allowHostAccess(accessPolicy).build();
                                Context context3 = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {

                    ContextVerifier contextVerifier = new ContextVerifier();
                    Value binding = context1.getBindings("triste");
                    binding.putMember("hostObject", contextVerifier);
                    binding = context2.getBindings("triste");
                    binding.putMember("hostObject", contextVerifier);
                    binding = context3.getBindings("triste");
                    binding.putMember("hostObject", contextVerifier);

                    Source cachedSource1 = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", "source1.triste").buildLiteral();
                    Source cachedSource2 = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", "source2.triste").buildLiteral();
                    Source cachedSource3 = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", "source3.triste").buildLiteral();
                    Source uncachedSource = Source.newBuilder("triste", "hostObjectCall(callback(42),1)", "source4.triste").cached(false).buildLiteral();

                    assertTrue(engine.getCachedSources().isEmpty());
                    context1.eval(cachedSource1);
                    assertEquals(Collections.singleton(cachedSource1), engine.getCachedSources());
                    context2.eval(cachedSource1);
                    assertEquals(Collections.singleton(cachedSource1), engine.getCachedSources());
                    context2.eval(cachedSource2);
                    assertEquals(new HashSet<>(Arrays.asList(cachedSource1, cachedSource2)), engine.getCachedSources());
                    context3.eval(cachedSource3);
                    assertEquals(new HashSet<>(Arrays.asList(cachedSource1, cachedSource2)), engine.getCachedSources());
                    assertEquals(Collections.singleton(cachedSource3), context3.getEngine().getCachedSources());
                    context1.eval(cachedSource3);
                    assertEquals(new HashSet<>(Arrays.asList(cachedSource1, cachedSource2, cachedSource3)), engine.getCachedSources());
                    context1.eval(uncachedSource);
                    assertEquals(new HashSet<>(Arrays.asList(cachedSource1, cachedSource2, cachedSource3)), engine.getCachedSources());

                    WeakReference<Source> cachedSource1Ref = new WeakReference<>(cachedSource1);
                    cachedSource1 = null;
                    GCUtils.assertGc("Source not collected", cachedSource1Ref);
                    assertEquals(new HashSet<>(Arrays.asList(cachedSource2, cachedSource3)), engine.getCachedSources());
                }
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testMigrateObject() throws InterruptedException {
        // Truffle isolate specific test
        try (Context singleThreadedContext = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowValueSharing(true).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
                        Context multiThreadedContext = Context.newBuilder().allowHostAccess(HostAccess.ALL).allowValueSharing(true).allowExperimentalOptions(true).option("engine.SpawnIsolate",
                                        "true").build()) {
            singleThreadedContext.initialize("triste-single-threaded");
            multiThreadedContext.initialize("triste");
            MigrateObjectCallBack migrateObjectCallBack = new MigrateObjectCallBack();
            singleThreadedContext.getBindings("triste-single-threaded").putMember("delegate", new TestObject());
            singleThreadedContext.getBindings("triste-single-threaded").putMember("hostObject", migrateObjectCallBack);
            Value testInContext1 = singleThreadedContext.eval("triste-single-threaded", "returnWeaklyReachableGuestObject(callback(create),1)");
            assertTrue(testInContext1.hasMember("callback"));
            Value testMigratedToContext2 = multiThreadedContext.asValue(testInContext1);
            assertTrue(testMigratedToContext2.hasMember("callback"));

            // Execute guest code in triste-single-threaded by background thread.
            Thread t = new Thread(() -> singleThreadedContext.eval("triste-single-threaded", "hostObjectCall(execute(42),1)"));
            t.start();
            try {
                migrateObjectCallBack.startSignal.await();
                AbstractPolyglotTest.assertFails(() -> testInContext1.hasMember("callback"), IllegalStateException.class);
                AbstractPolyglotTest.assertFails(() -> testMigratedToContext2.hasMember("callback"), PolyglotException.class, (pe) -> {
                    assertTrue(pe.isHostException());
                    assertTrue(pe.asHostException() instanceof IllegalStateException);
                    assertTrue(pe.getMessage(), pe.getMessage().contains("Multi threaded access requested by thread"));
                });
            } finally {
                migrateObjectCallBack.finishSignal.countDown();
            }
            t.join();
            singleThreadedContext.close();
            AbstractPolyglotTest.assertFails(testInContext1::canExecute, IllegalStateException.class);
            AbstractPolyglotTest.assertFails(testMigratedToContext2::canExecute, PolyglotException.class, (pe) -> {
                assertTrue(pe.isHostException());
                assertTrue(pe.asHostException() instanceof IllegalStateException);
                assertEquals(pe.getMessage(), "The Context is already closed.", pe.getMessage());
            });
        }
    }

    @HostReflection
    public static final class MigrateObjectCallBack {

        final CountDownLatch startSignal = new CountDownLatch(1);
        final CountDownLatch finishSignal = new CountDownLatch(1);

        MigrateObjectCallBack() {
        }

        public String execute(String arg) {
            startSignal.countDown();
            try {
                finishSignal.await();
                return arg;
            } catch (InterruptedException ie) {
                throw new RuntimeException(ie);
            }
        }
    }

    @HostReflection
    public static class ContextVerifier {

        volatile Context context;

        public String callback(String arg) {
            context = Context.getCurrent();
            return arg;
        }
    }

    static final class TestHandler extends Handler {

        private final String sourceName;
        final AtomicInteger compilations;

        TestHandler(String sourceName) {
            this.sourceName = sourceName;
            this.compilations = new AtomicInteger();
        }

        @Override
        public void publish(LogRecord record) {
            if ("engine".equals(record.getLoggerName())) {
                String message = record.getMessage();
                if (message != null && message.contains("opt done") && message.contains(sourceName)) {
                    compilations.incrementAndGet();
                }
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    public void testNativeTread() {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = ctx.getBindings("triste");
            binding.putMember("hostObject", new CallBack(20));
            ctx.eval("triste", "spawnHostObjectCall(callback(0),1)");
        }
    }

    @HostReflection
    public static class CallBack {

        private final int limit;

        CallBack(int limit) {
            this.limit = limit;
        }

        public String callback(String arg) {
            int depth = Integer.parseInt(arg);
            if (depth < limit) {
                Context context = Context.getCurrent();
                return context.eval("triste", "hostObjectCall(callback(" + (depth + 1) + "),1)").asString();
            }
            return arg;
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSourceProperties() throws Exception {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.

        File f = File.createTempFile("foo", ".triste");

        // Try without an existing engine
        assertEquals("triste", Source.findLanguage(f.toURI().toURL()));
        assertEquals("triste", Source.findLanguage(f));
        assertEquals("triste", Source.findLanguage("text/x-TruffleIsolateTestLanguage"));
        assertEquals("text/x-TruffleIsolateTestLanguage", Source.findMimeType(f.toURI().toURL()));
        assertEquals("text/x-TruffleIsolateTestLanguage", Source.findMimeType(f));

        // Try with an existing engine
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            assertEquals("triste", Source.findLanguage(f.toURI().toURL()));
            assertEquals("triste", Source.findLanguage(f));
            assertEquals("triste", Source.findLanguage("text/x-TruffleIsolateTestLanguage"));
            assertEquals("text/x-TruffleIsolateTestLanguage", Source.findMimeType(f.toURI().toURL()));
            assertEquals("text/x-TruffleIsolateTestLanguage", Source.findMimeType(f));
        }
    }

    @Test
    public void testGuestToHostInteropExceptions() {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = ctx.getBindings("triste");
            binding.putMember("hostObject", new CatchGuestException());
            ctx.eval("triste", "guestToHostThrow(arityExceptionCatch(ArityException),1)");
            ctx.eval("triste", "guestToHostThrow(unsupportedTypeCatch(UnsupportedTypeException),1)");
            ctx.eval("triste", "guestToHostThrow(truffleExceptionCatch(TruffleException),1)");
        }
    }

    private static Path getJavaExecutable() {
        String value = System.getProperty("java.home");
        if (value == null) {
            return null;
        }
        Path bin = Paths.get(value).resolve("bin");
        Path java = bin.resolve(isWindows() ? "java.exe" : "java");
        return Files.exists(java) ? java.toAbsolutePath() : null;
    }

    private static boolean isWindows() {
        String name = System.getProperty("os.name");
        return name.startsWith("Windows");
    }

    @HostReflection
    public static class CatchGuestException {

        public void arityExceptionCatch(Function<String, Object> throwObject) {
            AbstractPolyglotTest.assertFails(() -> throwObject.apply(""), IllegalArgumentException.class, (e) -> assertTrue(e.getMessage().contains("Expected 0-10 argument(s) but got 42")));
        }

        public void unsupportedTypeCatch(Function<String, Object> throwObject) {
            AbstractPolyglotTest.assertFails(() -> throwObject.apply("<unsupported>"), IllegalArgumentException.class, (e) -> assertTrue(e.getMessage().contains("<unsupported>")));
        }

        public void truffleExceptionCatch(Function<String, Object> throwObject) {
            AbstractPolyglotTest.assertFails(() -> throwObject.apply(""), PolyglotException.class, (e) -> {
                assertTrue(e.isGuestException());
                assertTrue(e.getMessage().contains("Test Truffle Runtime Error"));
            });
        }
    }

    @Test
    public void testHostToGuestInteropExceptions() {
        // TODO GR-39016: Keep, move to regular truffle tests, or remove.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = ctx.getBindings("triste");
            binding.putMember("hostObject", new ThrowHostException());
            ctx.eval("triste", "hostToGuestCatch(throwToGuest(ArityException),1)");
            ctx.eval("triste", "hostToGuestCatch(throwToGuest(UnsupportedTypeException),1)");
            ctx.eval("triste", "hostToGuestCatch(throwToGuest(CancelExecution),1)");
            ctx.eval("triste", "hostToGuestCatch(throwToGuest(TruffleException),1)");
            ctx.eval("triste", "hostToGuestCatch(throwToGuest(ExitException),1)");
        }
    }

    @HostReflection
    public static class ThrowHostException {

        @SuppressWarnings("serial")
        public void throwToGuest(String arg) throws InteropException {
            switch (arg) {
                case "ArityException":
                    throw ArityException.create(0, 10, 42);
                case "UnsupportedTypeException":
                    throw UnsupportedTypeException.create(new Object[]{arg});
                case "CancelExecution":
                    throw TestAPIAccessor.ENGINE.createCancelExecution(null, "Cancel", false);
                case "ExitException":
                    throw TestAPIAccessor.ENGINE.createExitException(null, "Exit", 0);
                case "TruffleException":
                    throw new AbstractTruffleException("Truffle exception") {
                    };
                default:
                    throw shouldNotReachHere("Unknown exception: " + arg);
            }

        }
    }

    @Test
    public void testPolyglotException() {
        // Not a truffe isolate specific test. Until PolyglotExceptionTest test is refactored to
        // be able to run with truffle isolate, we need this test here.
        try (Context context = Context.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "invalid syntax"), PolyglotException.class,
                            (pe) -> {
                                assertFalse(pe.isInternalError());
                                assertTrue(pe.isSyntaxError());
                                assertEquals("invalid syntax", pe.getSourceLocation().getCharacters().toString());
                            });
        }
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            binding.putMember("hostObject", new CancelCallBack(context));
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "hostObjectCall(cancel(1)," + Integer.MAX_VALUE + ")"), PolyglotException.class,
                            (pe) -> {
                                assertFalse(pe.isInternalError());
                                assertTrue(pe.isCancelled());
                                assertFalse(pe.isResourceExhausted());
                                assertNotNull(pe.getMessage());
                            });
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }

        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            binding.putMember("hostObject", callback);
            new Thread(() -> {
                try {
                    if (callback.running.await(30, TimeUnit.SECONDS)) {
                        try {
                            context.interrupt(Duration.ofSeconds(30));
                            return;
                        } catch (TimeoutException timeoutException) {
                            // pass
                        }
                    }
                } catch (InterruptedException ie) {
                    // pass
                }
                context.close(true);
            }).start();
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "hostObjectCall(noop(1)," + Integer.MAX_VALUE + ")"), PolyglotException.class,
                            (pe) -> {
                                assertFalse(pe.isInternalError());
                                assertFalse(pe.isCancelled());
                                assertTrue(pe.isInterrupted());
                                assertNotNull(pe.getMessage());
                            });
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @Test
    public void testCancelFromSpawnedThread() {
        // Not a truffle isolate specific test. Until cancellation truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            binding.putMember("hostObject", new CancelCallBack(context));
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "spawnHostObjectCall(cancel(1)," + Integer.MAX_VALUE + ")"), PolyglotException.class,
                            (pe) -> {
                                assertFalse(pe.isInternalError());
                                assertTrue(pe.isCancelled());
                                assertFalse(pe.isResourceExhausted());
                                assertNotNull(pe.getMessage());
                            });
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        }
    }

    @HostReflection
    public static class WaitBeforeCancelling {
        private final CountDownLatch signalMainThread = new CountDownLatch(1);

        @SuppressWarnings("unused")
        public void callback(String arg) {
            signalMainThread.countDown();
            synchronized (this) {
                try {
                    while (true) {
                        wait();
                    }
                } catch (InterruptedException ie) {
                }
            }
        }
    }

    @Test
    public void testCancelMainThread() throws ExecutionException, InterruptedException {
        // Not a truffle isolate specific test. Until cancellation truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            WaitBeforeCancelling waitBeforeCancelling = new WaitBeforeCancelling();
            binding.putMember("hostObject", waitBeforeCancelling);
            Future<?> future = executorService.submit(() -> {
                AbstractPolyglotTest.assertFails(() -> context.eval("triste", "hostObjectCall(callback(0),1)"), PolyglotException.class,
                                (pe) -> {
                                    assertFalse(pe.isInternalError());
                                    assertTrue(pe.isCancelled());
                                    assertFalse(pe.isResourceExhausted());
                                    assertNotNull(pe.getMessage());
                                });
            });
            waitBeforeCancelling.signalMainThread.await();
            context.close(true);
            future.get();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancelSpawnedThread() throws ExecutionException, InterruptedException {
        // Not a truffle isolate specific test. Until cancellation truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            WaitBeforeCancelling waitBeforeCancelling = new WaitBeforeCancelling();
            binding.putMember("hostObject", waitBeforeCancelling);
            Future<?> future = executorService.submit(() -> {
                AbstractPolyglotTest.assertFails(() -> {
                    context.eval("triste", "spawnHostObjectCall(callback(0),1)");
                    /*
                     * Local context is cancelled first which makes it possible that the spawned
                     * guest thread (that waits in a host call for a local context cancel) finishes
                     * before the guest is in a cancelling state. Trying to enter again makes sure
                     * the cancel exception is thrown.
                     */
                    context.enter();
                    context.leave();
                }, PolyglotException.class,
                                (pe) -> {
                                    assertFalse(pe.isInternalError());
                                    assertTrue(pe.isCancelled());
                                    assertFalse(pe.isResourceExhausted());
                                    assertNotNull(pe.getMessage());
                                });
            });
            waitBeforeCancelling.signalMainThread.await();
            context.close(true);
            future.get();
        } catch (PolyglotException pe) {
            if (!pe.isCancelled()) {
                throw pe;
            }
        } finally {
            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCancelFromSeparateNonEnteredThread() throws ExecutionException, InterruptedException {
        // Not a truffle isolate specific test. Until cancellation truffle tests are refactored to
        // be able to run with truffle isolate, we need this test here.
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = null;
            HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
            try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).allowCreateThread(true).option("engine.SpawnIsolate", "true").build()) {
                Value binding = context.getBindings("triste");
                CancelCallBack callback = new CancelCallBack(context);
                binding.putMember("hostObject", callback);
                future = executorService.submit(() -> {
                    try {
                        callback.running.await();
                        context.close(true);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    } finally {
                        callback.done.countDown();
                    }
                });
                AbstractPolyglotTest.assertFails(() -> context.eval("triste", "hostObjectCall(awaitDone(10)," + Integer.MAX_VALUE + ")"), PolyglotException.class,
                                (pe) -> {
                                    assertFalse(pe.isInternalError());
                                    assertTrue(pe.isCancelled());
                                    assertFalse(pe.isResourceExhausted());
                                    assertNotNull(pe.getMessage());
                                });
                future.get();
            } catch (PolyglotException pe) {
                if (!pe.isCancelled()) {
                    throw pe;
                }
            }
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(100, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testLogHandler() {
        // Not a truffle isolate specific test. Until LoggingTest is refactored to
        // be able to run with truffle isolate, we need this test here.
        Queue<LogRecord> records = new ConcurrentLinkedQueue<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {
            }
        };
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowCreateThread(true).logHandler(handler).allowExperimentalOptions(true).option("engine.SpawnIsolate",
                        "true").option("log.triste.level", "FINE").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            binding.putMember("hostObject", callback);
            context.eval("triste", "hostObjectCall(noop(42),1)");
            assertEquals(1, records.size());
            LogRecord record = records.poll();
            assertEquals(Level.FINE, record.getLevel());
            assertEquals("triste", record.getLoggerName());
            assertEquals("Request: {0}, method: {1}, arg: {2}, repeat: {3}", record.getMessage());
            assertNull(record.getSourceClassName());
            assertNull(record.getSourceMethodName());
            assertArrayEquals(new Object[]{"hostObjectCall", "noop", "42", 1}, record.getParameters());

            context.eval("triste", "spawnHostObjectCall(noop(42),1)");
            assertEquals(2, records.size());
            record = records.poll();
            assertEquals(Level.FINE, record.getLevel());
            assertEquals("triste", record.getLoggerName());
            assertEquals("Request: {0}, method: {1}, arg: {2}, repeat: {3}", record.getMessage());
            assertNull(record.getSourceClassName());
            assertNull(record.getSourceMethodName());
            assertArrayEquals(new Object[]{"spawnHostObjectCall", "noop", "42", 1}, record.getParameters());
            record = records.poll();
            assertEquals(Level.FINE, record.getLevel());
            assertEquals("triste", record.getLoggerName());
            assertEquals("Executing in thread {0}", record.getMessage());
            assertNull(record.getSourceClassName());
            assertNull(record.getSourceMethodName());
            assertEquals(1, record.getParameters().length);
        }
    }

    @Test
    public void testLogStream() {
        // Not a truffle isolate specific test. Until LoggingTest is refactored to
        // be able to run with truffle isolate, we need this test here.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowCreateThread(true).logHandler(bout).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option(
                        "log.triste.level", "FINE").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            binding.putMember("hostObject", callback);
            context.eval("triste", "hostObjectCall(noop(42),1)");
        }
        assertEquals("[triste] FINE: Request: hostObjectCall, method: noop, arg: 42, repeat: 1", bout.toString().trim());
    }

    @Test
    public void testLogStdErr() {
        // Not a truffle isolate specific test. Until LoggingTest is refactored to
        // be able to run with truffle isolate, we need this test here.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowCreateThread(true).err(bout).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option(
                        "log.triste.level", "FINE").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            binding.putMember("hostObject", callback);
            context.eval("triste", "hostObjectCall(noop(42),1)");
        }
        assertTrue(bout.toString().contains("[triste] FINE: Request: hostObjectCall, method: noop, arg: 42, repeat: 1"));
    }

    @Test
    public void testStdIn() {
        // TODO GR-39016: Move to regular truffle tests.
        String expectedContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        ByteArrayInputStream bin = new ByteArrayInputStream(expectedContent.getBytes());
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowCreateThread(true).in(bin).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            binding.putMember("hostObject", callback);
            context.eval("triste", "read(storeParameters(),1)");
            assertEquals(1, callback.storedParameters.size());
            assertEquals(expectedContent, callback.storedParameters.get(0));
        }
    }

    @Test
    public void testStdOut() {
        // TODO GR-39016: Move to regular truffle tests.
        String expectedContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowCreateThread(true).out(out).err(err).allowExperimentalOptions(true).option("engine.SpawnIsolate",
                        "true").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            callback.value = expectedContent;
            binding.putMember("hostObject", callback);
            context.eval("triste", "write(getValue(stdout),1)");
        }
        assertEquals(expectedContent, out.toString());
        assertEquals("", err.toString());
    }

    @Test
    public void testStdErr() {
        // TODO GR-39016: Move to regular truffle tests.
        String expectedContent = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowCreateThread(true).out(out).err(err).allowExperimentalOptions(true).option("engine.SpawnIsolate",
                        "true").build()) {
            Value binding = context.getBindings("triste");
            CancelCallBack callback = new CancelCallBack(context);
            callback.value = expectedContent;
            binding.putMember("hostObject", callback);
            context.eval("triste", "write(getValue(stderr),1)");
        }
        assertEquals("", out.toString());
        assertEquals(expectedContent, err.toString());
    }

    @Test
    public void testIsolateHeapDump() throws IOException {
        // Truffle isolate specific test
        assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder().allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            context.initialize("sl");
            Path heapDump = TestAPIAccessor.ISOLATE.dumpIsolateHeap(context.getEngine(), null);
            try {
                assertNotNull(heapDump);
                assertTrue(Files.isRegularFile(heapDump));
                assertTrue(Files.size(heapDump) > 0);
            } finally {
                Files.deleteIfExists(heapDump);
            }
        }
    }

    @Test
    public void testSourceSection() {
        // Not a truffle isolate specific test. Until SourceSectionTest is refactored to
        // be able to run with truffle isolate, we need this test here.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Value binding = ctx.getBindings("triste");
            String source = "guestToHostThrow(verify(TruffleException),1)";
            binding.putMember("hostObject", new SourceSectionVerifier(source));
            ctx.eval("triste", source);
        }
    }

    @Test
    public void testSourceSectionSL() {
        // Not a truffle isolate specific test. Until SourceSectionTest is refactored to
        // be able to run with truffle isolate, we need this test here.
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context ctx = Context.newBuilder("sl").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
            Value value = ctx.eval("sl", "function a() { return 42; } function main() { return a; }");
            SourceSectionVerifier.verify("a() { return 42; }", value);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testMessageTransport() throws IOException {
        TestMessageTransport transport = new TestMessageTransport(Collections.emptySet(), Collections.emptySet());
        try (Engine engine = Engine.newBuilder("triste").allowExperimentalOptions(true).serverTransport(transport).option("engine.SpawnIsolate", "true").option("tristetool.Connect",
                        "local").build()) {
            TestMessageEndpoint endpoint = transport.getEndPoint(URI.create("local"));
            String expectedString = "Hello";
            endpoint.echo(expectedString);
            assertEquals(expectedString, endpoint.getActualValue());

            byte[] expectedBinary = new byte[1024];
            for (int i = 0; i < expectedBinary.length; i++) {
                expectedBinary[i] = (byte) (i % Byte.MAX_VALUE);
            }
            ByteBuffer buffer = ByteBuffer.wrap(expectedBinary);
            endpoint.echo(buffer);
            assertArrayEquals(expectedBinary, (byte[]) endpoint.getActualValue());

            buffer = ByteBuffer.allocateDirect(expectedBinary.length << 1);
            buffer.put(expectedBinary, 0, expectedBinary.length);
            buffer.flip();
            endpoint.echo(buffer);
            assertArrayEquals(expectedBinary, (byte[]) endpoint.getActualValue());
        }

        transport = new TestMessageTransport(Collections.singleton(URI.create("forbidden")), Collections.emptySet());
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Engine engine = Engine.newBuilder("triste").allowExperimentalOptions(true).serverTransport(transport).err(err).option("engine.SpawnIsolate", "true").option("tristetool.Connect",
                        "forbidden").build();
        engine.close();
        assertTrue(err.toString().contains("WARNING: VetoException: Forbidden forbidden"));

        transport = new TestMessageTransport(Collections.emptySet(), Collections.singleton(URI.create("failing")));
        err = new ByteArrayOutputStream();
        engine = Engine.newBuilder("triste").allowExperimentalOptions(true).serverTransport(transport).err(err).option("engine.SpawnIsolate", "true").option("tristetool.Connect", "failing").build();
        engine.close();
        assertTrue(err.toString().contains("WARNING: IOException: Failing failing"));
    }

    @Test
    public void testSystemExit() throws Exception {
        Assume.assumeNotNull(getJavaExecutable()); // Has java executable
        SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, () -> {
            try (Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL).useSystemExit(true).option("engine.SpawnIsolate", "true").build()) {
                context.eval("triste", "exit(value(42),1)");
            }
        }).failOnNonZeroExit(false).onExit((p) -> {
            assertEquals(42, p.exitCode);
        }).run();
    }

    @Test
    public void testGR39395() throws Exception {
        Runnable runnable = () -> {
            Context context = Context.newBuilder().option("engine.SpawnIsolate", "true").build();
            Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
            CountDownLatch threadEnteredIsolate = new CountDownLatch(1);
            CountDownLatch contextClosed = new CountDownLatch(1);
            CountDownLatch threadLeftIsolate = new CountDownLatch(1);
            Thread t = new Thread(() -> {
                // Thread cannot use either Context or Engine, otherwise cleaner would not be used.
                IsolateThread isolateThread = isolate.enter();
                try {
                    threadEnteredIsolate.countDown();
                    contextClosed.await();
                } catch (InterruptedException ie) {
                    throw new AssertionError("Interrupted", ie);
                } finally {
                    isolateThread.leave();
                    threadLeftIsolate.countDown();
                }
            });
            t.start();
            try {
                try {
                    try {
                        threadEnteredIsolate.await();
                        // Close isolated context with thread still entered in isolate. The isolate
                        // tear
                        // down is scheduled to active thread's leave call.
                        context.close();
                        // Free engine before isolate tear down
                        WeakReference<Engine> engineRef = new WeakReference<>(context.getEngine());
                        context = null;
                        GCUtils.assertGc("Engine must be freed.", engineRef);
                    } finally {
                        contextClosed.countDown();
                    }
                    threadLeftIsolate.await();
                    // We need to call the engine cleaner to reproduce the problem. The {@link
                    // EngineCleanUpReference} tried to call into an already closed isolate.
                    TestAPIAccessor.ISOLATE.invokeCleaners();
                } finally {
                    t.join();
                }
            } catch (InterruptedException ie) {
                throw new AssertionError(ie);
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    @Test
    public void testGR39764() {
        String exceptionMessage = "expected failure";
        AbstractPolyglotTest.assertFails(() -> {
            Context context = Context.newBuilder("triste").allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("tristetool.Fail", exceptionMessage).build();
            context.close();
        }, PolyglotException.class, (pe) -> {
            assertFalse(pe.isInternalError());
            assertEquals(exceptionMessage, pe.getMessage());
        });
    }

    @Test
    public void testGR47417() throws IOException, InterruptedException {
        Runnable runnable = () -> {
            try (Engine eng = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build()) {
                Context context1 = Context.newBuilder().engine(eng).build();
                context1.getBindings("triste").putMember("delegate", new TestObject());
                context1.getBindings("triste").removeMember("delegate");
                WeakReference<Context> contextRef = new WeakReference<>(context1);
                context1 = null;
                GCUtils.assertGc("Context must be freed", contextRef);
                try (Context context2 = Context.newBuilder().engine(eng).build()) {
                    // We need isolate GC to free "delegate" in context1.
                    TestAPIAccessor.ISOLATE.triggerIsolateGC(eng);
                    context2.getBindings("triste").putMember("delegate", new TestObject());
                }
            }
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    @Test
    public void testGR54300() throws IOException, InterruptedException {
        assumeTrue(GCUtils.isSupported());
        Runnable runnable = () -> {
            /*
             * Initialize truffle compiler, so that the next engine is not reachable from the
             * truffle compilation thread that initializes the compiler.
             */
            Context.newBuilder("triste").option("engine.SpawnIsolate", "true").build().close();
            // build a context and don't close it, then let the bound engine be GCed
            Context context = Context.newBuilder("triste").option("sandbox.MaxCPUTime", "10s").option("engine.SpawnIsolate", "true").build();
            Engine engine = context.getEngine();
            Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(engine);
            Reference<Engine> engineRef = new WeakReference<>(engine);
            context = null;
            engine = null;
            GCUtils.assertGc("Engine must be freed.", engineRef);
            // new context build triggers the previous isolate teardown which hangs
            Context.newBuilder("triste").option("engine.SpawnIsolate", "true").build();
            assertTrue(isolate.isDisposed());
        };
        if (ImageInfo.inImageCode()) {
            runnable.run();
        } else {
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, runnable).run();
        }
    }

    @Test
    public void testHSTruffleObjectCleanupReferenceLeak() {
        final int objectsPerContext = 10;
        final int maxIterations = 128;
        try (Engine engine = Engine.newBuilder().allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").option("engine.MaxIsolateMemory", "32MB").build()) {
            for (int i = 0; i < maxIterations; i++) {
                try (Context context = Context.newBuilder("triste").engine(engine).allowHostAccess(HostAccess.ALL).allowExperimentalOptions(true).build()) {
                    Value scope = context.getBindings("triste");
                    scope.putMember("carrier", new CarrierObject());
                    for (int j = 0; j < objectsPerContext; j++) {
                        Value guestValue = context.eval("triste", "allocateGuestObject(load(524288),1)");
                        scope.putMember("guest_object_" + j, guestValue);
                    }
                }
            }
        }
    }

    @HostReflection
    public static class CarrierObject {
        CarrierObject() {
        }
    }

    @Test
    public void testIsolateProcessDeathInHost() {
        Assume.assumeTrue("Only supported in external (process) isolate mode.", TruffleTestAssumptions.isExternalIsolate());
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        Context context = Context.newBuilder("triste").allowHostAccess(accessPolicy).allowExperimentalOptions(true).option("engine.SpawnIsolate", "true").build();
        try {
            Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
            assertTrue(isolate instanceof ProcessIsolate);
            KillActiveChildProcess hostObject = new KillActiveChildProcess(isolate.getIsolateId(), 100);
            Value binding = context.getBindings("triste");
            binding.putMember("hostObject", hostObject);
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "killActiveChildProcess(callback(),1)"),
                            PolyglotException.class,
                            (pe) -> {
                                assertTrue(pe.isCancelled());
                            });
        } finally {
            AbstractPolyglotTest.assertFails(() -> context.close(),
                            PolyglotException.class,
                            (pe) -> {
                                assertTrue(pe.isCancelled());
                            });
        }
    }

    @HostReflection
    public static final class KillActiveChildProcess {

        private final long externalIsolatePid;
        private final int killAtDepth;

        KillActiveChildProcess(long externalIsolatePid, int killAtDepth) {
            this.externalIsolatePid = externalIsolatePid;
            this.killAtDepth = killAtDepth;
        }

        public void callback(int depth, Value guestCallBack) {
            if (depth == killAtDepth) {
                ProcessHandle isolateProcess = ProcessHandle.of(externalIsolatePid).orElseThrow();
                boolean killed = isolateProcess.destroyForcibly();
                if (!killed) {
                    Context.getCurrent().close(true);
                }
            } else {
                guestCallBack.execute(depth + 1);
            }
        }
    }

    @Test
    public void testIsolateProcessDeathInGuest() {
        Assume.assumeTrue("Only supported in external (process) isolate mode.", TruffleTestAssumptions.isExternalIsolate());
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        Context context = Context.newBuilder("triste").//
                        allowCreateThread(true).//
                        allowHostAccess(accessPolicy).//
                        allowExperimentalOptions(true).//
                        option("engine.SpawnIsolate", "true").//
                        build();
        try {
            Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
            assertTrue(isolate instanceof ProcessIsolate);
            AsynchronousKillActiveChildProcess hostObject = new AsynchronousKillActiveChildProcess(isolate.getIsolateId(), 100);
            Value binding = context.getBindings("triste");
            binding.putMember("hostObject", hostObject);
            AbstractPolyglotTest.assertFails(() -> context.eval("triste", "killActiveChildProcess(callback(100),1)"),
                            PolyglotException.class,
                            (pe) -> {
                                assertTrue(pe.isCancelled());
                            });
        } finally {
            AbstractPolyglotTest.assertFails(() -> context.close(),
                            PolyglotException.class,
                            (pe) -> {
                                assertTrue(pe.isCancelled());
                            });
        }
    }

    /**
     * {@link ProcessHandle} cannot be used to destroy the current process. To terminate the guest
     * isolate process, we must block in a guest call and use a separate thread in the host to kill
     * the child process.
     */
    @HostReflection
    public static final class AsynchronousKillActiveChildProcess {

        private final long externalIsolatePid;
        private final int killAtDepth;
        private boolean guestBlocked;

        AsynchronousKillActiveChildProcess(long externalIsolatePid, int killAtDepth) {
            this.externalIsolatePid = externalIsolatePid;
            this.killAtDepth = killAtDepth;
        }

        public void callback(int depth, Value guestCallBack) {
            if (depth == killAtDepth) {
                Context current = Context.getCurrent();
                new Thread(() -> {
                    waitUntilGuestBlocked();
                    ProcessHandle isolateProcess = ProcessHandle.of(externalIsolatePid).orElseThrow();
                    boolean killed = isolateProcess.destroyForcibly();
                    if (!killed) {
                        current.close(true);
                    }
                }).start();
            }
            guestCallBack.execute(depth + 1);
        }

        public synchronized void notifyBlocked() {
            guestBlocked = true;
            notifyAll();
        }

        private synchronized void waitUntilGuestBlocked() {
            while (!guestBlocked) {
                try {
                    wait();
                } catch (InterruptedException ie) {
                    throw new AssertionError("Interrupted while waiting for guest");
                }
            }
        }
    }

    /**
     * Verifies that when the host (parent) process terminates, the polyglot isolate (child) process
     * is also terminated. By default, when a parent process terminates, its child processes are
     * re-parented to the process with pid 1 (initd, systemd). However, the polyglot isolate is
     * intended to operate only in the context of its host process. Without the host, it becomes
     * useless, so we ensure it is terminated alongside the host process.
     */
    @Test
    public void testHostProcessDeath() throws IOException, InterruptedException, ExecutionException {
        assumeFalse(ImageInfo.inImageRuntimeCode());
        Assume.assumeTrue("Only supported in external (process) isolate mode.", TruffleTestAssumptions.isExternalIsolate());
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            AtomicReference<Future<Integer>> killHostProcessTask = new AtomicReference<>();
            SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, () -> {
                HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
                try (Context context = Context.newBuilder("triste").//
                                allowCreateThread(true).//
                                allowHostAccess(accessPolicy).//
                                allowExperimentalOptions(true).//
                                option("engine.SpawnIsolate", "true").//
                                build()) {
                    Isolate<?> isolate = (Isolate<?>) TestAPIAccessor.ISOLATE.getIsolate(context.getEngine());
                    assertTrue(isolate instanceof ProcessIsolate);
                    AsynchronousKillActiveChildProcess hostObject = new AsynchronousKillActiveChildProcess(isolate.getIsolateId(), -1);
                    Value binding = context.getBindings("triste");
                    binding.putMember("hostObject", hostObject);
                    context.eval("triste", "killActiveChildProcess(callback(10),1)");
                }
            }).failOnNonZeroExit(false).onStart((processHandle) -> {
                killHostProcessTask.set(worker.submit(() -> {
                    ProcessHandle isolateProcess = null;
                    for (int tries = 0; tries < 15; tries++) {
                        List<ProcessHandle> children = processHandle.children().toList();
                        if (children.isEmpty()) {
                            TimeUnit.SECONDS.sleep(2);
                        } else {
                            isolateProcess = children.getFirst();
                            break;
                        }
                    }
                    if (isolateProcess == null) {
                        return -1;
                    }
                    processHandle.destroyForcibly();
                    for (int tries = 0; tries < 15; tries++) {
                        if (isolateProcess.isAlive()) {
                            TimeUnit.SECONDS.sleep(2);
                        } else {
                            return 0;
                        }
                    }
                    return -2;
                }));
            }).run();
            assertEquals(0, (int) killHostProcessTask.get().get());
        }
    }

    private static final String[] EXPECTED_MERGED_JNI_STACK = {
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.throwIOException",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.check",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.start",
                    "com.oracle.truffle.polyglot.isolate.ForeignProcessHandlerGen$NativeToHSEndPoint.start",
                    "org.graalvm.jniutils.JNICalls.callStaticJObject",
                    "com.oracle.truffle.polyglot.isolate.ForeignProcessHandlerGen$NativeToHSStartPoint.start",
                    "<...>",
                    "com.oracle.truffle.api.io.TruffleProcessBuilder.start",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTestLanguage$MainNode.spawnSubProcess",
                    "<...>",
                    "com.oracle.truffle.polyglot.PolyglotContextDispatch.eval",
                    "com.oracle.truffle.polyglot.isolate.GuestPolyglotIsolateServices.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen$HSToNativeEndPoint.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen$HSToNativeStartPoint.parseEval0",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen$HSToNativeStartPoint.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignContextDispatch.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignContextDispatch.eval",
                    "org.graalvm.polyglot.Context.eval",
                    "<...>",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest.testStackTraceMerge",
                    "<...>"
    };

    private static final String[] EXPECTED_MERGED_PROCESS_STACK = {
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.throwIOException",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.check",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest$ThrowExceptionProcessHandler.start",
                    "com.oracle.truffle.polyglot.isolate.ForeignProcessHandlerGen$ProcessToProcessEndPoint.start",
                    "com.oracle.truffle.polyglot.isolate.ForeignProcessHandlerGen.dispatch",
                    "org.graalvm.nativebridge.ProcessIsolate$DispatchSupportImpl.dispatch",
                    "org.graalvm.nativebridge.ProcessIsolateThread.sendAndReceive",
                    "com.oracle.truffle.polyglot.isolate.ForeignProcessHandlerGen$ProcessToProcessStartPoint.start",
                    "<...>",
                    "com.oracle.truffle.api.io.TruffleProcessBuilder.start",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTestLanguage$MainNode.spawnSubProcess",
                    "<...>",
                    "com.oracle.truffle.polyglot.PolyglotContextDispatch.eval",
                    "com.oracle.truffle.polyglot.isolate.GuestPolyglotIsolateServices.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen$ProcessToProcessEndPoint.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen.dispatch",
                    "org.graalvm.nativebridge.ProcessIsolate$DispatchSupportImpl.dispatch",
                    "org.graalvm.nativebridge.ProcessIsolateThreadSupport$ThreadChannel.dispatch",
                    "org.graalvm.nativebridge.ProcessIsolateThreadSupport$ThreadChannel.sendAndReceive",
                    "org.graalvm.nativebridge.ProcessIsolateThread.sendAndReceive",
                    "com.oracle.truffle.polyglot.isolate.ForeignPolyglotIsolateServicesGen$ProcessToProcessStartPoint.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignContextDispatch.parseEval",
                    "com.oracle.truffle.polyglot.isolate.ForeignContextDispatch.eval",
                    "org.graalvm.polyglot.Context.eval",
                    "<...>",
                    "com.oracle.truffle.api.test.polyglot.isolate.PolyglotIsolateTest.testStackTraceMerge",
                    "<...>"
    };

    @Test
    public void testStackTraceMerge() throws IOException {
        HostAccess accessPolicy = HostAccess.newBuilder(HostAccess.ALL).build();
        try (Context context = Context.newBuilder("triste").//
                        allowHostAccess(accessPolicy).//
                        allowExperimentalOptions(true).//
                        option("engine.SpawnIsolate", "true").//
                        allowCreateProcess(true).//
                        allowIO(IOAccess.ALL).//
                        processHandler(new ThrowExceptionProcessHandler()).build()) {
            Source src = Source.newBuilder("triste", "spawnSubProcess(ignored(ls),1)", "test").build();
            AbstractPolyglotTest.assertFails(() -> context.eval(src), PolyglotException.class, (pe) -> {
                assertTrue(pe.isInternalError());
                assertTrue(pe.getMessage().contains(ThrowExceptionProcessHandler.MESSAGE));
                // We need the original exception, PolyglotException hides frames above
                // Context#eval.
                Throwable cause = TestAPIAccessor.ENGINE.getPolyglotExceptionCause(ReflectionUtils.getField(pe, "impl"));
                assertStackTrace(cause.getStackTrace());
            });
        }
    }

    private static void assertStackTrace(StackTraceElement[] stack) {
        String[] expectedStack = TruffleTestAssumptions.isExternalIsolate() ? EXPECTED_MERGED_PROCESS_STACK : EXPECTED_MERGED_JNI_STACK;
        int j = 0;
        out: for (int i = 0; i < stack.length && j < expectedStack.length;) {
            String pattern = expectedStack[j];
            if ("<...>".equals(pattern)) {
                j++;
                if (j == expectedStack.length) {
                    // All patterns are consumed
                    break out;
                }
                pattern = expectedStack[j];
                String current = stackFrameToQualifiedMethodName(stack[i]);
                while (!pattern.equals(current)) {
                    i++;
                    if (i == stack.length) {
                        break out;
                    }
                    current = stackFrameToQualifiedMethodName(stack[i]);
                }
                assertEquals(formatErrorMessage(expectedStack, stack), pattern, current);
            } else {
                String current = stackFrameToQualifiedMethodName(stack[i]);
                assertEquals(formatErrorMessage(expectedStack, stack), pattern, current);
            }
            i++;
            j++;
        }
        assertEquals(formatErrorMessage(expectedStack, stack), expectedStack.length, j);
    }

    private static String formatErrorMessage(String[] expectedStack, StackTraceElement[] stack) {
        return String.format("""
                        Expected stack trace:
                        %s
                        Actual stack trace:
                        %s
                        """, String.join("\n", expectedStack),
                        Arrays.stream(stack).map(StackTraceElement::toString).collect(Collectors.joining("\n")));
    }

    private static String stackFrameToQualifiedMethodName(StackTraceElement element) {
        return element.getClassName() + '.' + element.getMethodName();
    }

    private static final class ThrowExceptionProcessHandler implements ProcessHandler {

        static final String MESSAGE = "Test exception";

        @Override
        public Process start(ProcessCommand command) throws IOException {
            throw check();
        }

        private static IOException check() throws IOException {
            throw throwIOException();
        }

        private static IOException throwIOException() throws IOException {
            throw new IOException(MESSAGE);
        }
    }

    @Test
    public void testJNILocalsFreed() {
        assumeFalse("JNI locals are not used in external isolate.", TruffleTestAssumptions.isExternalIsolate());
        try (Context ctx = Context.newBuilder("triste").//
                        allowHostAccess(HostAccess.ALL).//
                        option("engine.SpawnIsolate", "true").//
                        build()) {
            Value binding = ctx.getBindings("triste");
            HostObjectFactory hostObjectFactory = new HostObjectFactory();
            binding.putMember("hostObject", hostObjectFactory);
            /*
             * Allocates a 1 MB host object per call. After 10_000 iterations, total allocation
             * reaches ~10 GB, enough to trigger an OutOfMemoryError if JNI local references are not
             * released.
             */
            int guestToHostCallCount = 10_000;
            ctx.eval("triste", "loopHostCall(newHostObject(" + hostObjectFactory.newHostObject() + ")," + guestToHostCallCount + ")");
        }
    }

    @Test
    public void testNoMethodScopingWarningWithoutIsolation() throws Exception {
        testScopingWarningImpl(false, HostAccess.ALL, false, false);
    }

    @Test
    public void testNoMethodScopingWarningForNoHostAccess() throws Exception {
        testScopingWarningImpl(true, HostAccess.NONE, false, false);
    }

    @Test
    public void testMethodScopingWarningForUnscopedHostAccess() throws Exception {
        testScopingWarningImpl(true, HostAccess.ALL, false, true);
    }

    @Test
    public void testNoMethodScopingWarningForScopedHostAccess() throws Exception {
        HostAccess scopedHostAccess = HostAccess.newBuilder(HostAccess.ALL).methodScoping(true).build();
        testScopingWarningImpl(true, scopedHostAccess, false, false);
    }

    @Test
    public void testMethodScopingWarningDisabled() throws Exception {
        testScopingWarningImpl(true, HostAccess.ALL, true, false);
    }

    private static void testScopingWarningImpl(boolean spawnIsolate, HostAccess hostAccess, boolean disableWarning, boolean expectWarning) throws Exception {
        assumeFalse(ImageInfo.inImageRuntimeCode());
        SubprocessTestUtils.Builder builder = SubprocessTestUtils.newBuilder(PolyglotIsolateTest.class, () -> {
            Context context = Context.newBuilder("triste").allowHostAccess(hostAccess).spawnIsolate(spawnIsolate).build();
            context.close();
        });
        // Remove engine.SpawnIsolate option passed by gates, the test controls spawn isolate itself
        builder.prefixVmOption(SubprocessTestUtils.markForRemoval(("-Dpolyglot.engine.SpawnIsolate=true")));
        if (disableWarning) {
            builder.prefixVmOption("-Dpolyglot.engine.WarnMethodScoping=false");
        } else {
            builder.prefixVmOption(SubprocessTestUtils.markForRemoval(("-Dpolyglot.engine.WarnMethodScoping=false")));
        }
        builder.onExit((p) -> {
            assertEquals(expectWarning, p.output.stream().anyMatch((l) -> l.contains("An isolated polyglot context uses host access without host method scoping.")));
        });
        builder.run();
    }

    @HostReflection
    public static final class HostObjectFactory {

        @SuppressWarnings("static-method")
        public String newHostObject() {
            char[] content = new char[1 << 20];   // 1MB
            Arrays.fill(content, 'a');
            return new String(content);
        }
    }

    @HostReflection
    public static final class SourceSectionVerifier {

        private final String expectedContent;

        SourceSectionVerifier(String expectedContent) {
            this.expectedContent = expectedContent;
        }

        public void verify(Value object) {
            verify(expectedContent, object);
        }

        static void verify(String expected, Value object) {
            SourceSection sourceSection = object.getSourceLocation();
            assertNotNull(sourceSection);
            assertEquals(expected, sourceSection.getCharacters().toString());
        }
    }

    @HostReflection
    public static class CancelCallBack {

        private final Context context;
        private final CountDownLatch running;
        private final CountDownLatch done;
        final List<String> storedParameters = new ArrayList<>();
        String value;

        CancelCallBack(Context context) {
            this.context = context;
            this.running = new CountDownLatch(1);
            this.done = new CountDownLatch(1);
        }

        public String cancel(String arg) {
            enter();
            context.close(true);
            return arg;
        }

        public String noop(String arg) {
            enter();
            return arg;
        }

        public void storeParameters(String arg) {
            enter();
            storedParameters.add(arg);
        }

        public String getValue() {
            enter();
            return value;
        }

        public void awaitDone(String maxSeconds) {
            enter();
            try {
                if (!done.await(Long.parseLong(maxSeconds), TimeUnit.SECONDS)) {
                    throw new AssertionError("Timeout " + maxSeconds + " seconds");
                }
            } catch (InterruptedException ie) {
            }
        }

        private void enter() {
            running.countDown();
        }
    }

    private static final class Worker implements Runnable {

        private final Engine engine;
        private final Phaser phaser;

        Worker(Engine engine, Phaser phaser) {
            this.engine = engine;
            this.phaser = phaser;
        }

        @Override
        @SuppressWarnings("unused")
        public void run() {
            phaser.awaitAdvance(phaser.arriveAndDeregister());
            for (int i = 0; i < 100; i++) {
                Collection<Instrument> instruments = engine.getInstruments().values();
                for (Instrument ins : instruments) {
                    for (OptionDescriptor desc : ins.getOptions()) {
                    }
                }
                Collection<Language> languages = engine.getLanguages().values();
                for (Language lang : languages) {
                    for (OptionDescriptor desc : lang.getOptions()) {
                    }
                }
            }
        }
    }

    private static final class TestMessageEndpoint implements MessageEndpoint {

        private final MessageEndpoint peer;
        private Object actualValue;

        TestMessageEndpoint(MessageEndpoint peerEndpoint) {
            this.peer = peerEndpoint;
        }

        @Override
        public void sendText(String text) {
            actualValue = text;
        }

        @Override
        public void sendBinary(ByteBuffer data) {
            byte[] arr = new byte[data.limit() - data.position()];
            data.get(arr);
            actualValue = arr;
        }

        @Override
        public void sendPing(ByteBuffer data) {
        }

        @Override
        public void sendPong(ByteBuffer data) {
        }

        @Override
        public void sendClose() {
        }

        void echo(String message) throws IOException {
            peer.sendText(message);
        }

        void echo(ByteBuffer buffer) throws IOException {
            peer.sendBinary(buffer);
        }

        Object getActualValue() {
            Object res = actualValue;
            actualValue = null;
            return res;
        }
    }

    private static final class TestMessageTransport implements MessageTransport {

        private final Set<URI> forbiddenAddresses;
        private final Set<URI> failingAddresses;
        private final Map<URI, TestMessageEndpoint> endpointByURI;

        TestMessageTransport(Set<URI> forbiddenAddresses, Set<URI> failingAddresses) {
            this.forbiddenAddresses = forbiddenAddresses;
            this.failingAddresses = failingAddresses;
            this.endpointByURI = new HashMap<>();
        }

        @Override
        public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, VetoException {
            if (forbiddenAddresses.contains(uri)) {
                throw new VetoException("Forbidden " + uri);
            }
            if (failingAddresses.contains(uri)) {
                throw new IOException("Failing " + uri);
            }
            TestMessageEndpoint endpoint = new TestMessageEndpoint(peerEndpoint);
            endpointByURI.put(uri, endpoint);
            return endpoint;
        }

        TestMessageEndpoint getEndPoint(URI uri) {
            return endpointByURI.get(uri);
        }
    }
}
