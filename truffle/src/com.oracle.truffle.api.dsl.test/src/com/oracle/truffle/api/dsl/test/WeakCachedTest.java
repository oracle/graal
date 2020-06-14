/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertNotNull;

import java.lang.ref.WeakReference;
import java.util.concurrent.Semaphore;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.WeakCachedTestFactory.ConsistentGuardAndSpecializationNodeGen;
import com.oracle.truffle.api.dsl.test.WeakCachedTestFactory.TestNullWeakCacheNodeGen;
import com.oracle.truffle.api.dsl.test.WeakCachedTestFactory.WeakInlineCacheNodeGen;
import com.oracle.truffle.api.dsl.test.WeakCachedTestFactory.WeakSharedCacheNodeGen;
import com.oracle.truffle.api.dsl.test.WeakCachedTestFactory.WeakSimpleNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.GCUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@SuppressWarnings("unused")
public class WeakCachedTest extends AbstractPolyglotTest {

    @Test
    public void testWeakSimpleNode() {
        WeakSimpleNode node = WeakSimpleNodeGen.create();
        Object o = new String("");
        WeakReference<Object> ref = new WeakReference<>(o);
        node.execute(o);
        o = null;
        GCUtils.assertGc("Reference is not collected", ref);
    }

    @GenerateUncached
    abstract static class WeakSimpleNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(String arg,
                        @Cached(value = "arg", weak = true) String cachedStorage) {
            return arg;
        }

    }

    @Test
    public void testWeakInlineCache() {
        WeakInlineCacheNode node = WeakInlineCacheNodeGen.create();
        Object o0 = new String("");
        Object o1 = new String("");
        Object o2 = new String("");
        WeakReference<Object> ref0 = new WeakReference<>(o0);
        WeakReference<Object> ref1 = new WeakReference<>(o1);
        WeakReference<Object> ref2 = new WeakReference<>(o2);
        node.execute(o0);
        node.execute(o1);
        o0 = null;
        GCUtils.assertGc("Reference is not collected", ref0);

        node.execute(o1);
        node.execute(o2);
        o1 = null;
        o2 = null;
        GCUtils.assertGc("Reference is not collected", ref1);
        GCUtils.assertGc("Reference is not collected", ref2);

        assertFails(() -> node.execute(new String("")), UnsupportedSpecializationException.class);
    }

    @GenerateUncached
    abstract static class WeakInlineCacheNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "cachedArg == arg", limit = "3")
        Object s0(String arg,
                        @Cached(value = "arg", weak = true) String cachedArg) {
            assertNotNull(cachedArg);
            return arg;
        }

    }

    @Test
    public void testWeakSharedNode() {
        WeakSharedCacheNode node = WeakSharedCacheNodeGen.create();
        Object o0 = new String("");
        WeakReference<Object> ref1 = new WeakReference<>(o0);
        node.execute(o0, false);
        o0 = null;
        GCUtils.assertGc("Reference is not collected", ref1);
        node.execute("", false);
    }

    @GenerateUncached
    abstract static class WeakSharedCacheNode extends Node {

        abstract Object execute(Object arg0, boolean expectNull);

        @Specialization(guards = "arg.length() > 0")
        Object s0(String arg, boolean expectNull,
                        @Shared("sharedArg") @Cached(value = "arg", weak = true) String cachedStorage) {
            assertNotNull(cachedStorage);
            return arg;
        }

        @Specialization
        Object s1(String arg, boolean expectNull,
                        @Shared("sharedArg") @Cached(value = "arg", weak = true) String cachedStorage) {
            assertNotNull(cachedStorage);
            return arg;
        }

    }

    /*
     * Test that while executing a specialization, between guard and specialization the weak
     * reference cannot get collected.
     */
    @Test
    public void testConsistentGuardAndSpecialization() throws InterruptedException {
        ConsistentGuardAndSpecializationNode node = ConsistentGuardAndSpecializationNodeGen.create();
        Object o0 = new String("");
        WeakReference<Object> ref1 = new WeakReference<>(o0);
        node.execute(o0);
        Thread t = new Thread(new Runnable() {
            public void run() {
                node.locksEnabled = true;
                node.execute(new String(""));
            }
        });
        t.start();
        node.waitForGuard.acquire();
        o0 = null;
        try {
            GCUtils.assertNotGc("Reference is not collected", ref1);
        } finally {
            node.waitForSpecialization.release();
            t.join();
        }
        GCUtils.assertGc("Reference is not collected", ref1);

    }

    @Test
    public void testNullWeakReference() {
        assertFails(() -> TestNullWeakCacheNodeGen.create().execute(null), UnsupportedSpecializationException.class);
        assertFails(() -> TestNullWeakCacheNodeGen.getUncached().execute(null), UnsupportedSpecializationException.class);
    }

    @GenerateUncached
    abstract static class TestNullWeakCacheNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(Object arg,
                        @Cached(value = "arg", weak = true) Object cachedStorage) {
            assertNotNull(cachedStorage);
            return arg;
        }

    }

    abstract static class ConsistentGuardAndSpecializationNode extends Node {

        final Semaphore waitForSpecialization = new Semaphore(0);
        final Semaphore waitForGuard = new Semaphore(0);

        abstract Object execute(Object arg0);

        volatile boolean locksEnabled;

        boolean acquireLock(Object a) {
            if (locksEnabled) {
                waitForGuard.release();
                try {
                    waitForSpecialization.acquire();
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
            return true;
        }

        @Specialization(guards = {"arg.equals(cachedStorage)", "acquireLock(arg)"}, limit = "3")
        Object s0(String arg,
                        @Cached(value = "arg", weak = true) String cachedStorage) {
            assertNotNull(cachedStorage);
            return arg;
        }

    }

    abstract static class ErrorWeakPrimitiveNode extends Node {

        abstract Object execute(Object arg0);

        @Specialization(guards = "arg == cachedStorage", limit = "3")
        Object s0(int arg,
                        @com.oracle.truffle.api.test.ExpectError("Cached parameters with primitive types cannot be weak. Set weak to false to resolve this.") //
                        @Cached(value = "arg", weak = true) int cachedStorage) {
            return arg;
        }
    }

    @GenerateUncached
    abstract static class ErrorWeakCachedReachablFromFallback extends Node {

        abstract Object execute(Object arg0);

        @Specialization
        Object s0(String arg,
                        @Cached(value = "arg", weak = true) String cachedStorage) {
            return arg;
        }

        @com.oracle.truffle.api.test.ExpectError("Some guards for the following specializations could not be negated for the @Fallback specialization: [s0].%")
        @Fallback
        Object fallback(Object arg) {
            return arg;
        }
    }

}
