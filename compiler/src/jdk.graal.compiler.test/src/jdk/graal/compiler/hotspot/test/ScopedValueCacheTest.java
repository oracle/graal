/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.graal.compiler.hotspot.test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HotSpotFieldLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@SuppressWarnings("preview")
public class ScopedValueCacheTest extends HotSpotGraalCompilerTest {

    private static final ScopedValue<Integer> VALUE = ScopedValue.newInstance();

    // These compilation-only entry points reuse the actual Thread invocation plugins.
    public static Object[] readCache() {
        throw new AssertionError("Compilation only");
    }

    public static void writeCache(Object[] cache) {
        throw new AssertionError("Compilation only");
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins plugins) {
        super.registerInvocationPlugins(plugins);
        InvocationPlugins threadPlugins = getReplacements().getGraphBuilderPlugins().getInvocationPlugins();
        ResolvedJavaMethod getter = getResolvedJavaMethod(Thread.class, "scopedValueCache");
        ResolvedJavaMethod setter = getResolvedJavaMethod(Thread.class, "setScopedValueCache");
        InvocationPlugin read = threadPlugins.lookupInvocation(getter, getInitialOptions());
        InvocationPlugin write = threadPlugins.lookupInvocation(setter, getInitialOptions());
        Assert.assertNotNull(read);
        Assert.assertNotNull(write);
        InvocationPlugins.Registration registration = new InvocationPlugins.Registration(plugins, ScopedValueCacheTest.class);
        registration.register(new InvocationPlugin("readCache") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                return read.apply(b, getter, receiver);
            }
        });
        registration.register(new InvocationPlugin("writeCache", Object[].class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode cache) {
                return write.apply(b, setter, receiver, cache);
            }
        });
    }

    public static Object[] readAcrossCall(Runnable action) {
        Object[] before = readCache();
        action.run();
        return new Object[]{before, readCache()};
    }

    public static void writeAcrossCall(Runnable action) {
        Object[] before = readCache();
        action.run();
        writeCache(before);
    }

    private static void noOp() {
    }

    private void checkHandleReload(String snippet) {
        // Keep an opaque call that can unmount a continuation. The production intrinsic is active.
        OptionValues options = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        InstalledCode code = getCode(getResolvedJavaMethod(snippet), options);
        try {
            Assert.assertEquals("A carrier-relative handle must be reloaded after a call", 2,
                            lastCompiledGraph.getNodes().filter(node -> node instanceof ReadNode read &&
                                            read.getLocationIdentity().equals(HotSpotFieldLocationIdentity.JAVA_THREAD_SCOPED_VALUE_CACHE_LOCATION)).count());
        } finally {
            code.invalidate();
        }
    }

    @Test
    public void testReadAcrossCall() {
        checkHandleReload("readAcrossCall");
    }

    @Test
    public void testWriteAcrossCall() {
        checkHandleReload("writeAcrossCall");
    }

    @BytecodeParserNeverInline
    public static void suspend(CountDownLatch entered, AtomicBoolean resume) {
        entered.countDown();
        while (!resume.get()) {
            LockSupport.park();
            if (Thread.currentThread().isInterrupted()) {
                throw new AssertionError("Interrupted while awaiting migration");
            }
        }
    }

    @Test
    public void testReadAcrossCarrierMigration() throws ReflectiveOperationException, InterruptedException {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");
        // The JDK provides this constructor for tests. Schedule the first mount on one carrier
        // and every subsequent mount on the other, instead of hoping for scheduler migration.
        var constructor = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder").getDeclaredConstructor(Executor.class);
        constructor.setAccessible(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean resume = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Object> expectedCache = new AtomicReference<>();
        AtomicReference<Object> previousCarrierCache = new AtomicReference<>();
        AtomicReference<Thread> firstCarrier = new AtomicReference<>();
        AtomicReference<Thread> secondCarrier = new AtomicReference<>();
        AtomicInteger schedules = new AtomicInteger();
        try (ExecutorService first = Executors.newSingleThreadExecutor();
                        ExecutorService second = Executors.newSingleThreadExecutor()) {
            Executor scheduler = task -> {
                int schedule = schedules.getAndIncrement();
                ExecutorService executor = schedule == 0 ? first : second;
                AtomicReference<Thread> carrier = schedule == 0 ? firstCarrier : secondCarrier;
                executor.execute(() -> {
                    carrier.compareAndSet(null, Thread.currentThread());
                    task.run();
                });
            };
            Thread.Builder.OfVirtual builder = (Thread.Builder.OfVirtual) constructor.newInstance(scheduler);
            Thread virtual = builder.unstarted(() -> {
                try {
                    ScopedValue.where(VALUE, 1).run(() -> {
                        VALUE.get();
                        Object[] initialReads = readAcrossCall(ScopedValueCacheTest::noOp);
                        Assert.assertNotNull(initialReads[0]);
                        Assert.assertSame(initialReads[0], initialReads[1]);
                        expectedCache.set(initialReads[0]);

                        Object[] migrationReads = readAcrossCall(() -> suspend(entered, resume));
                        Assert.assertSame(expectedCache.get(), migrationReads[0]);
                        Assert.assertNotSame("Resumption must not read the previous carrier's cache",
                                        previousCarrierCache.get(), migrationReads[1]);
                        Assert.assertSame("Resumption must reload the current carrier's cache handle",
                                        expectedCache.get(), migrationReads[1]);
                    });
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                }
            });
            InstalledCode code = getCode(getResolvedJavaMethod("readAcrossCall"), null, true, true,
                            new OptionValues(getInitialOptions(), HighTier.Options.Inline, false));
            try {
                Assert.assertTrue(code.isValid());
                virtual.start();
                Assert.assertTrue(entered.await(10, TimeUnit.SECONDS));
                first.execute(() -> {
                    try {
                        Assert.assertSame(firstCarrier.get(), Thread.currentThread());
                        ScopedValue.where(VALUE, 2).run(() -> {
                            try {
                                VALUE.get();
                                Object[] carrierReads = readAcrossCall(ScopedValueCacheTest::noOp);
                                Assert.assertSame(carrierReads[0], carrierReads[1]);
                                Assert.assertNotSame("The previous carrier must hold a different cache",
                                                expectedCache.get(), carrierReads[0]);
                                previousCarrierCache.set(carrierReads[0]);
                            } finally {
                                occupied.countDown();
                            }
                            try {
                                // Keep the different cache installed until the virtual thread has
                                // completed its second read on the new carrier.
                                release.await();
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                failure.compareAndSet(null, exception);
                            }
                        });
                    } catch (Throwable throwable) {
                        failure.compareAndSet(null, throwable);
                        occupied.countDown();
                    }
                });
                Assert.assertTrue(occupied.await(10, TimeUnit.SECONDS));
                if (failure.get() != null) {
                    throw new AssertionError("Could not prepare the previous carrier's cache", failure.get());
                }
                resume.set(true);
                LockSupport.unpark(virtual);
                Assert.assertTrue(virtual.join(Duration.ofSeconds(10)));
                Assert.assertTrue("The virtual thread must have remounted", schedules.get() >= 2);
                Assert.assertNotNull("The resumed carrier must have started", secondCarrier.get());
                Assert.assertNotSame("The virtual thread must resume on another carrier", firstCarrier.get(), secondCarrier.get());
                if (failure.get() != null) {
                    throw new AssertionError("Compiled read reused the previous carrier's scoped-value cache handle", failure.get());
                }
            } finally {
                release.countDown();
                resume.set(true);
                LockSupport.unpark(virtual);
                virtual.join();
                code.invalidate();
            }
        }
    }

    private static boolean contains(Object[] array, Object value) {
        for (Object element : array) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("preview")
    public static void testScopedValue() {
        ScopedValue<Integer> scopedValue = ScopedValue.newInstance();
        ScopedValue.where(scopedValue, 42).run(() -> {
            scopedValue.get();
            try {
                Method get = Thread.class.getDeclaredMethod("scopedValueCache");
                get.setAccessible(true);
                Object[] cache = (Object[]) get.invoke(null);
                assertTrue(contains(cache, scopedValue));
            } catch (ReflectiveOperationException e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void testBody() {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");

        compileAndInstallSubstitution(Thread.class, "setScopedValueCache");
        testScopedValue();

        compileAndInstallSubstitution(Thread.class, "scopedValueCache");
        testScopedValue();
    }
}
