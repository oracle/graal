/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.debug.DebugEnvironment;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.phases.OnStackReplacementPhase;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test on-stack-replacement with locks.
 */
public class GraalOSRLockTest extends GraalOSRTestBase {

    private static boolean TestInSeparateThread = false;

    // testing only
    public static boolean isMonitorLockHeld(Object o) {
        return isMonitorLockHeldByThread(o, null);
    }

    public static boolean isMonitorLockHeldByThread(Object o, Thread t) {
        int oihc = System.identityHashCode(o);
        ThreadMXBean tmxbean = ManagementFactory.getThreadMXBean();
        ThreadInfo[] tinfos = tmxbean.dumpAllThreads(true, false);

        for (ThreadInfo ti : tinfos) {
            if (!(t != null && t.getId() != ti.getThreadId())) {
                for (MonitorInfo mi : ti.getLockedMonitors()) {
                    if (mi.getIdentityHashCode() == oihc) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected static void run(Runnable r) {
        if (TestInSeparateThread) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    beforeOSRLockTest();
                    r.run();
                    afterOSRLockTest();
                }
            });
            t.start();
            try {
                t.join();
            } catch (Throwable t1) {
                throw new GraalError(t1);
            }
        } else {
            beforeOSRLockTest();
            r.run();
            afterOSRLockTest();
        }
    }

    private static boolean wasLocked() {
        return isMonitorLockHeld(lock) || isMonitorLockHeld(lock1);
    }

    protected static Map<OptionValue<?>, Object> osrLockNoDeopt() {
        HashMap<OptionValue<?>, Object> overrides = new HashMap<>();
        overrides.put(OnStackReplacementPhase.Options.DeoptAfterOSR, false);
        overrides.put(OnStackReplacementPhase.Options.SupportOSRWithLocks, true);
        return overrides;
    }

    protected static Map<OptionValue<?>, Object> osrLockDeopt() {
        HashMap<OptionValue<?>, Object> overrides = new HashMap<>();
        overrides.put(OnStackReplacementPhase.Options.SupportOSRWithLocks, true);
        return overrides;
    }

    public static int SideEffectI;

    private static void lockOnObject(Object o, String msg) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (o) {
                    SideEffectI = 1;
                }
            }
        });
        t.start();
        try {
            t.join(1000);
        } catch (InterruptedException e) {
            Assert.fail("Object " + msg + " was locked");
        }
    }

    private static void beforeOSRLockTest() {
        // try lock both objects
        lockOnObject(lock, "lock");
        lockOnObject(lock1, "lock1");
        Assert.assertFalse(wasLocked());
    }

    private static void afterOSRLockTest() {
        // try lock both objects
        lockOnObject(lock, "lock");
        lockOnObject(lock1, "lock1");
        Assert.assertFalse(wasLocked());
        // force a safepoint and hope the inflated locks are deflated
        System.gc();
    }

    @BeforeClass
    public static void init() {
        DebugEnvironment.ensureInitialized();
    }

    // @Test
    @SuppressWarnings("try")
    public void testLockOSROuterImmediateDeoptAfter() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockDeopt())) {
                testOSR("testOuterLockImmediateDeoptAfter");
            }
        });
    }

    static class A {

    }

    static class B {
        @SuppressWarnings("unused")
        B(A a) {

        }
    }

    HashMap<String, HashSet<A>> listeners = new HashMap<>();

    public synchronized ReturnValue synchronizedSnippet() {
        /*
         * Test method for which liveness would conclude the original object is no longer alive
         * although it is.
         */
        Collection<HashSet<A>> allListeners = listeners.values();
        for (HashSet<A> group : allListeners) {
            GraalDirectives.blackhole(group);
        }
        return ReturnValue.SUCCESS;
    }

    @Test
    @SuppressWarnings("try")
    public void testSynchronizedSnippet() {
        GraalOSRLockTest instance = new GraalOSRLockTest();
        // enough entries to trigger OSR
        for (int i = 0; i < 100000; i++) {
            instance.listeners.put("hello" + i, null);
        }
        testOSR("synchronizedSnippet", instance);
        Assert.assertFalse(isMonitorLockHeld(instance));
    }

    @Test
    @SuppressWarnings("try")
    public void testOSRTrivialLoop() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockDeopt())) {
                testOSR("testReduceOSRTrivialLoop");
            } catch (Throwable t) {
                Assert.assertEquals("OSR compilation without OSR entry loop.", t.getMessage());
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerImmediateDeoptAfter() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockDeopt())) {
                testOSR("testOuterInnerLockImmediateDeoptAfter");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterCompileRestOfMethod() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt());
                            OverrideScope o2 = OptionValue.override(HighTier.Options.Inline, false)) {
                testOSR("testOuterLockCompileRestOfMethod");
            }

        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerCompileRestOfMethod() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt())) {
                testOSR("testOuterInnerLockCompileRestOfMethod");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthCompileRestOfMethod() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt());
                            OverrideScope o2 = OptionValue.override(HighTier.Options.Inline, false)) {
                testOSR("testOuterInnerLockDepth1CompileRestOfMethod");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthDeopt() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt());
                            OverrideScope o2 = OptionValue.override(HighTier.Options.Inline, false)) {
                testOSR("testOuterInnerLockDepth1DeoptAfter");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthRecursiveCompileRestOfMethod0() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt())) {
                testOSR("testOuterInnerLockDepth1RecursiveCompileRestOfMethod1");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerLockDepthRecursiveCompileRestOfMethod1() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt())) {
                testOSR("testOuterInnerLockDepth1RecursiveCompileRestOfMethod2");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterCompileRestOfMethodSubsequentLock() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt())) {
                testOSR("testOuterLockCompileRestOfMethodSubsequentLock");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSROuterInnerSameLockCompileRestOfMethod() {
        run(() -> {
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt())) {
                testOSR("testOuterInnerSameLockCompileRestOfMethod");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSRRecursive() {
        run(() -> {
            // call it
            testRecursiveLockingLeaf();
            ResolvedJavaMethod leaf = getResolvedJavaMethod("testRecursiveLockingLeaf");
            // profile it
            leaf.reprofile();
            testRecursiveLockingLeaf();
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt());
                            OverrideScope o2 = OptionValue.override(HighTier.Options.Inline, false)) {
                compile(leaf, -1);
                testOSR("testRecursiveLockingRoot");
            }
        });
    }

    @Test
    @SuppressWarnings("try")
    public void testLockOSRRecursiveLeafOSR() {
        run(() -> {
            testRecursiveRootNoOSR();
            ResolvedJavaMethod root = getResolvedJavaMethod("testRecursiveRootNoOSR");
            try (OverrideScope o = OptionValue.override(osrLockNoDeopt());
                            OverrideScope o2 = OptionValue.override(HighTier.Options.Inline, false)) {
                compile(root, -1);
                testOSR("testRecursiveLeafOSR");
                // force a safepoint and hope the inflated locks are deflated
                System.gc();
                // call the root to call into the leaf and enter the osr-ed code
                testRecursiveRootNoOSR();
            }
        });
    }

    protected static int limit = 10000;
    protected static Object lock = new Object();
    protected static Object lock1 = new Object();
    private static final boolean LOG = false;

    static {
        // force identity hash code for easy displaced mark identification
        int h1 = System.identityHashCode(lock);
        int h2 = System.identityHashCode(lock1);
        if (LOG) {
            TTY.println("Forcing a system identity hashcode on lock object " + h1);
            TTY.println("Forcing a system identity hashcode on lock1 object " + h2);
        }
    }

    public static ReturnValue testReduceOSRTrivialLoop() {
        for (int i = 0; i < limit * limit; i++) {
            GraalDirectives.blackhole(i);
            if (GraalDirectives.inCompiledCode()) {
                return ReturnValue.SUCCESS;
            }
        }
        return ReturnValue.FAILURE;
    }

    public static ReturnValue testOuterLockImmediateDeoptAfter() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        System.out.println("DoneBeforeDeopting");
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (GraalDirectives.inCompiledCode()) {
                throw new Error("Must not be part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockImmediateDeoptAfter() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (lock1) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeDeopting");
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            GraalDirectives.deoptimize();
            return ret;
        }
    }

    public static ReturnValue testOuterLockCompileRestOfMethod() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 1001 == 0) {
                    ret = ReturnValue.SUCCESS;
                    System.out.println("Doing a gc");
                    System.gc();
                }
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockCompileRestOfMethod() {
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (lock1) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                            System.gc();
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1CompileRestOfMethod() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock1) {
                for (int i = 1; i < limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 1001 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                            System.gc();
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            } else {
                // lock 1 must be free
                if (isMonitorLockHeld(lock1)) {
                    throw new Error("Lock 1 must have been released already");
                }

                // lock 2 must still be locked and cannot be acquired by another thread
                if (!isMonitorLockHeldByThread(lock, Thread.currentThread())) {
                    throw new Error("Lock must not have been released already");
                }
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1DeoptAfter() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock1) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                        }
                    }
                }
                GraalDirectives.controlFlowAnchor();
                GraalDirectives.deoptimize();
                if (GraalDirectives.inCompiledCode()) {
                    throw new Error("Must not part of compiled code");
                }
            }
        }
        return ret;
    }

    public static ReturnValue testOuterInnerLockDepth1RecursiveCompileRestOfMethod1() {
        // testing the order of the lock releasing
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (lock) {
            synchronized (lock) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testOuterInnerLockDepth1RecursiveCompileRestOfMethod2() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < 10 * limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveLockingRoot() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < limit; i++) {
                    GraalDirectives.blackhole(i);
                    testRecursiveLockingLeaf();
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveLockingLeaf() {
        // testing the order of the lock releasing
        final Object l = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (l) {
            synchronized (l) {
                for (int i = 1; i < limit; i++) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                    }
                }
            }
            return ret;
        }
    }

    public static ReturnValue testRecursiveRootNoOSR() {
        // testing the order of the lock releasing
        final Object l = lock;
        synchronized (l) {
            ReturnValue ret = ReturnValue.FAILURE;
            for (int i = 0; i < 5; i++) {
                if (GraalDirectives.inCompiledCode()) {
                    ret = testRecursiveLeafOSR();
                }
                GraalDirectives.controlFlowAnchor();
                if (ret == ReturnValue.FAILURE) {
                    return ret;
                }
            }
            GraalDirectives.controlFlowAnchor();
            return ret;
        }
    }

    public static ReturnValue testRecursiveLeafOSR() {
        ReturnValue ret = ReturnValue.FAILURE;
        // lock is already locked by the caller
        synchronized (lock) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        System.out.println("DoneBeforeNotDeopting");
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            return ret;
        }
    }

    // test cases for optimizations
    public static ReturnValue testOuterLockCompileRestOfMethodSubsequentLock() {
        final Object monitor = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (monitor) {
            for (int i = 1; i < 10 * limit; i++) {
                GraalDirectives.blackhole(i);
                if (i % 33 == 0) {
                    ret = ReturnValue.SUCCESS;
                    if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                        System.out.println("DoneBeforeNotDeopting");
                    }
                }
            }
        }
        synchronized (monitor) {
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
        }
        return ret;

    }

    public static ReturnValue testOuterInnerSameLockCompileRestOfMethod() {
        final Object monitor = lock;
        ReturnValue ret = ReturnValue.FAILURE;
        synchronized (monitor) {
            for (int i = 1; i < 10 * limit; i++) {
                synchronized (monitor) {
                    GraalDirectives.blackhole(i);
                    if (i % 33 == 0) {
                        ret = ReturnValue.SUCCESS;
                        if (GraalDirectives.inCompiledCode() && i + 33 > (10 * limit)) {
                            System.out.println("DoneBeforeNotDeopting");
                        }
                    }
                }
            }
            GraalDirectives.controlFlowAnchor();
            if (!GraalDirectives.inCompiledCode()) {
                throw new Error("Must part of compiled code");
            }
            return ret;
        }
    }

}
