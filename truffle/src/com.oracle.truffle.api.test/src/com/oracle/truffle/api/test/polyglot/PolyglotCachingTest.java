/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * Please note that any OOME exceptions when running this test indicate memory leaks in Truffle.
 */
public class PolyglotCachingTest {

    /*
     * Also used for other GC tests.
     */
    public static final int GC_TEST_ITERATIONS = 15;

    @Test
    public void testDisableCaching() throws Exception {
        AtomicInteger parseCalled = new AtomicInteger(0);
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                parseCalled.incrementAndGet();
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(""));
            }
        });
        Context c = Context.create();
        Source cachedSource = Source.newBuilder(ProxyLanguage.ID, "testSourceInstanceIsEqual", "name").cached(true).build();
        Source uncachedSource = Source.newBuilder(ProxyLanguage.ID, "testSourceInstanceIsEqual", "name").cached(false).build();
        assertEquals(0, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(1, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(2, parseCalled.get());
        c.eval(uncachedSource);
        assertEquals(3, parseCalled.get());

        c.eval(cachedSource);
        assertEquals(4, parseCalled.get());
        c.eval(cachedSource);
        assertEquals(4, parseCalled.get());
    }

    /*
     * Tests that the outer source instance is never the same as the one passed in. That allows the
     * outer source instance to be collected while the inner one is still referenced strongly. The
     * garbage collection of the outer source instance will trigger cleanup the cached CallTargets.
     */
    @Test
    public void testLanguageSourceInstanceIsEqualToEmbedder() throws Exception {
        AtomicReference<com.oracle.truffle.api.source.Source> innerSource = new AtomicReference<>(null);
        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                innerSource.set(request.getSource());
                return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(""));
            }
        });
        Context c = Context.create();
        Source source = Source.create(ProxyLanguage.ID, "testSourceInstanceIsEqual");
        c.eval(source);

        assertNotNull(innerSource.get());
        Field f = Source.class.getDeclaredField("impl");
        f.setAccessible(true);

        assertEquals(f.get(source), innerSource.get());
        assertEquals(innerSource.get(), f.get(source));
        assertNotSame(innerSource.get(), f.get(source));
    }

    /*
     * Test that CallTargets stay cached as long as their source instance is alive.
     */
    @Test
    public void testParsedASTIsNotCollectedIfSourceIsAlive() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang();

        Context context = Context.create();
        Source source = Source.create(ProxyLanguage.ID, "0"); // needs to stay alive

        WeakReference<CallTarget> parsedRef = new WeakReference<>(assertParsedEval(context, source));
        for (int i = 0; i < GC_TEST_ITERATIONS; i++) {
            // cache should stay valid and never be collected as long as the source is alive.
            assertCachedEval(context, source);
            System.gc();
        }
        assertNotNull(parsedRef.get());
        context.close();
    }

    /*
     * Test that if the context is strongly referenced and the source reference is freed the GC can
     * collect the source together with the cached CallTargets.
     */
    @Test
    public void testSourceFreeContextStrong() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang();

        Context survivingContext = Context.create();
        assertObjectsCollectible(GC_TEST_ITERATIONS, (iteration) -> {
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget target = assertParsedEval(survivingContext, source);
            assertCachedEval(survivingContext, source);
            return target;
        });
        survivingContext.close();
    }

    /*
     * Test that if the context is freed and the source reference is still strong the GC can collect
     * the cached CallTargets.
     */
    @Test
    public void testSourceStrongContextFree() {
        Assume.assumeFalse("This test is too slow in fastdebug.", System.getProperty("java.vm.version").contains("fastdebug"));
        setupTestLang();

        List<Source> survivingSources = new ArrayList<>();

        assertObjectsCollectible(GC_TEST_ITERATIONS, (iteration) -> {
            Context context = Context.create();
            Source source = Source.create(ProxyLanguage.ID, String.valueOf(iteration));
            CallTarget parsedAST = assertParsedEval(context, source);
            assertCachedEval(context, source);
            survivingSources.add(source);
            context.close();
            return parsedAST;
        });
    }

    private static void assertObjectsCollectible(int iterations, Function<Integer, Object> objectFactory) {
        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        List<WeakReference<Object>> collectibleObjects = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            collectibleObjects.add(new WeakReference<>(objectFactory.apply(i), queue));
            System.gc();
        }
        int refsCleared = 0;
        while (queue.poll() != null) {
            refsCleared++;
        }
        // we need to have any refs cleared for this test to have any value
        Assert.assertTrue(refsCleared > 0);
    }

    long parseCount;
    CallTarget lastParsedTarget;

    private void setupTestLang() {
        byte[] bytes = new byte[16 * 1024 * 1024];
        byte byteValue = (byte) 'a';
        Arrays.fill(bytes, byteValue);
        String testString = new String(bytes); // big string

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected CallTarget parse(ParsingRequest request) throws Exception {
                int index = Integer.parseInt(request.getSource().getCharacters().toString());
                parseCount++;
                lastParsedTarget = Truffle.getRuntime().createCallTarget(new RootNode(languageInstance) {
                    /*
                     * Typical root nodes have a strong reference to source. We need to ensure that
                     * we can still collect the cache if that happens.
                     */
                    @SuppressWarnings("unused") final com.oracle.truffle.api.source.Source source = request.getSource();

                    @SuppressWarnings("unused") final String bigString = testString.substring(index, testString.length());

                    @Override
                    public Object execute(VirtualFrame frame) {
                        return "foobar";
                    }
                });
                return lastParsedTarget;
            }
        });
    }

    private CallTarget assertParsedEval(Context context, Source source) {
        this.parseCount = 0;
        assertEquals("foobar", context.eval(source).asString());
        assertEquals(1, this.parseCount);
        CallTarget parsed = this.lastParsedTarget;
        assertNotNull(parsed);
        this.lastParsedTarget = null;
        return parsed;
    }

    private void assertCachedEval(Context context, Source source) {
        this.parseCount = 0;
        this.lastParsedTarget = null;
        assertEquals("foobar", context.eval(source).asString());
        assertEquals(0, this.parseCount);
        assertNull(lastParsedTarget);
    }

}
