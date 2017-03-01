/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L1;
import static com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.L1_ALT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.api.interop.java.MethodMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.vm.ImplicitExplicitExportTest.Ctx;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.api.vm.PolyglotEngine.Value;
import java.util.Collections;
import static org.junit.Assert.assertTrue;

public class EngineTest {
    private Set<PolyglotEngine> toDispose = new HashSet<>();

    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder();
    }

    private PolyglotEngine register(PolyglotEngine engine) {
        toDispose.add(engine);
        return engine;
    }

    @After
    public void dispose() {
        for (PolyglotEngine engine : toDispose) {
            engine.dispose();
        }
    }

    @Test
    public void npeWhenCastingAs() throws Exception {
        PolyglotEngine tvm = createBuilder().build();
        register(tvm);

        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        PolyglotEngine.Language language2 = tvm.getLanguages().get("application/x-test-import-export-2");
        language2.eval(Source.newBuilder("explicit.value=42").name("define 42").mimeType("content/unknown").build());

        PolyglotEngine.Value value = language1.eval(Source.newBuilder("return=value").name("42.value").mimeType("content/unknown").build());
        String res = value.as(String.class);
        assertNotNull(res);
    }

    @Test
    public void testPassingThroughInteropException() throws Exception {
        PolyglotEngine tvm = createBuilder().build();
        register(tvm);

        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        try {
            PolyglotEngine.Value value = language1.eval(Source.newBuilder("throwInteropException").name("interopTest").mimeType("content/unknown").build());
            value.as(Object.class);
        } catch (Exception e) {
            while (e instanceof RuntimeException) {
                e = (Exception) e.getCause();
            }
            assertEquals("Expecting UnsupportedTypeException", UnsupportedTypeException.class, e.getClass());
            return;
        }
        fail("Expected UnsupportedTypeException, got none");
    }

    @Test
    public void checkCachingOfNodes() {
        PolyglotEngine vm1 = createBuilder().build();
        register(vm1);
        PolyglotEngine vm2 = createBuilder().executor(Executors.newSingleThreadExecutor()).build();
        register(vm2);

        PolyglotEngine.Language language1 = vm1.getLanguages().get("application/x-test-hash");
        PolyglotEngine.Language language2 = vm2.getLanguages().get("application/x-test-hash");
        PolyglotEngine.Language alt1 = vm1.getLanguages().get("application/x-test-hash-alt");
        PolyglotEngine.Language alt2 = vm2.getLanguages().get("application/x-test-hash-alt");
        final Source sharedSource = Source.newBuilder("anything").name("something").mimeType("content/unknown").build();

        Object hashIn1Round1 = language1.eval(sharedSource).get();
        Object hashIn2Round1 = language2.eval(sharedSource).get();
        Object hashIn1Round2 = language1.eval(sharedSource).get();
        Object hashIn2Round2 = language2.eval(sharedSource).get();

        Object altIn1Round1 = alt1.eval(sharedSource).get();
        Object altIn2Round1 = alt2.eval(sharedSource).get();
        Object altIn1Round2 = alt1.eval(sharedSource).get();
        Object altIn2Round2 = alt2.eval(sharedSource).get();

        assertEquals("Two executions in 1st engine share the nodes", hashIn1Round1, hashIn1Round2);
        assertEquals("Two executions in 2nd engine share the nodes", hashIn2Round1, hashIn2Round2);

        assertEquals("Two alternative executions in 1st engine share the nodes", altIn1Round1, altIn1Round2);
        assertEquals("Two alternative executions in 2nd engine share the nodes", altIn2Round1, altIn2Round2);

        assertNotEquals("Two executions in different languages don't share the nodes", hashIn1Round1, altIn1Round1);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn1Round1, altIn2Round1);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn2Round2, altIn1Round2);
        assertNotEquals("Two executions in different languages don't share the nodes", hashIn2Round2, altIn2Round2);

        assertNotEquals("Two executions in different engines don't share the nodes", hashIn1Round1, hashIn2Round1);
        assertNotEquals("Two executions in different engines don't share the nodes", hashIn2Round2, hashIn1Round2);
    }

    protected Thread forbiddenThread() {
        return null;
    }

    private interface AccessArray {
        AccessArray dupl();

        List<? extends Number> get(int index);
    }

    @Test
    public void wrappedAsArray() throws Exception {
        Object[][] matrix = {{1, 2, 3}};

        PolyglotEngine tvm = createBuilder().globalSymbol("arr", new ArrayTruffleObject(matrix, forbiddenThread())).build();
        register(tvm);

        PolyglotEngine.Language language1 = tvm.getLanguages().get("application/x-test-import-export-1");
        AccessArray access = language1.eval(Source.newBuilder("return=arr").name("get the array").mimeType("content/unknown").build()).as(AccessArray.class);
        assertNotNull("Array converted to list", access);
        access = access.dupl();
        List<? extends Number> list = access.get(0);
        assertEquals("Size 3", 3, list.size());
        assertEquals(1, list.get(0));
        assertEquals(2, list.get(1));
        assertEquals(3, list.get(2));
        Integer[] arr = list.toArray(new Integer[0]);
        assertEquals("Three items in array", 3, arr.length);
        assertEquals(1, arr[0].intValue());
        assertEquals(2, arr[1].intValue());
        assertEquals(3, arr[2].intValue());
    }

    @Test
    public void engineConfigBasicAccess() {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-1", "cmd-line-args", new String[]{"1", "2"});
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language1 = vm.getLanguages().get("application/x-test-import-export-1");

        assertNotNull("Lang1 found", language1);

        Ctx ctx1 = language1.getGlobalObject().as(Ctx.class);
        String[] args = (String[]) ctx1.env.getConfig().get("cmd-line-args");
        assertNotNull("Founds args", args);

        assertEquals("1", args[0]);
        assertEquals("2", args[1]);

        assertNull("Can't see settings for other language", ctx1.env.getConfig().get("hello"));

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        assertNotNull("Lang2 found", language2);

        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
        assertNull("Cannot find args", ctx2.env.getConfig().get("cmd-line-args"));
    }

    @Test
    public void engineConfigShouldBeReadOnly() {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-1", "cmd-line-args", new String[]{"1", "2"});
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language1 = vm.getLanguages().get("application/x-test-import-export-1");
        Ctx ctx1 = language1.getGlobalObject().as(Ctx.class);

        // make sure configuration is read-only
        try {
            ctx1.env.getConfig().put("hi", "there!");
            fail("The map should be readonly");
        } catch (UnsupportedOperationException ex) {
            // OK
        }
    }

    @Test
    public void secondValueWins() {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-2", "hello", "truffle");
        builder.config("application/x-test-import-export-2", "hello", "world");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void secondValueWins2() {
        Builder builder = createBuilder();
        builder.config("application/x-test-import-export-2", "hello", "world");
        builder.config("application/x-test-import-export-2", "hello", "truffle");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language2 = vm.getLanguages().get("application/x-test-import-export-2");
        Ctx ctx2 = language2.getGlobalObject().as(Ctx.class);
        assertEquals("truffle", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void altValueWins() {
        Builder builder = createBuilder();
        builder.config(L1, "hello", "truffle");
        builder.config(L1_ALT, "hello", "world");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void altValueWins2() {
        Builder builder = createBuilder();
        builder.config(L1_ALT, "hello", "truffle");
        builder.config(L1, "hello", "world");
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertEquals("world", ctx2.env.getConfig().get("hello"));
    }

    @Test
    public void configIsNeverNull() {
        Builder builder = createBuilder();
        PolyglotEngine vm = builder.build();
        register(vm);

        PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
        Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
        assertNull(ctx2.env.getConfig().get("hello"));
    }

    static class YourLang {
        public static final String MIME_TYPE = L1;
    }

    @Test
    public void exampleOfConfiguration() {
        // @formatter:off
        String[] args = {"--kernel", "Kernel.som", "--instrument", "dyn-metrics"};
        Builder builder = PolyglotEngine.newBuilder();
        builder.config(YourLang.MIME_TYPE, "CMD_ARGS", args);
        PolyglotEngine vm = builder.build();
        // @formatter:on

        try {
            PolyglotEngine.Language language1 = vm.getLanguages().get(L1);
            Ctx ctx2 = language1.getGlobalObject().as(Ctx.class);
            String[] read = (String[]) ctx2.env.getConfig().get("CMD_ARGS");

            assertSame("The same array as specified is returned", args, read);
        } finally {
            vm.dispose();
        }
    }

    @Test
    public void testCaching() {
        CachingLanguageChannel channel = new CachingLanguageChannel();
        PolyglotEngine vm = register(createBuilder().config(CachingLanguage.MIME_TYPE, "channel", channel).build());

        final Source source1 = Source.newBuilder("unboxed").name("something").mimeType(CachingLanguage.MIME_TYPE).build();
        final Source source2 = Source.newBuilder("unboxed").name("something").mimeType(CachingLanguage.MIME_TYPE).build();

        int cachedTargetsSize = -1;
        int interopTargetsSize = -1;

        // from now on we should not create any new targets
        for (int i = 0; i < 10; i++) {
            Value value1 = vm.eval(source1);
            Value value2 = vm.eval(source2);

            value1 = value1.execute().execute().execute().execute();
            value2 = value2.execute().execute().execute().execute();

            value1.get();
            value2.get();

            assertNotNull(value1.as(CachingTruffleObject.class));
            assertNotNull(value2.as(CachingTruffleObject.class));

            if (i == 0) {
                cachedTargetsSize = channel.parseTargets.size();
                interopTargetsSize = channel.interopTargets.size();
                // its fair to assume some call targets need to get created
                assertNotEquals(0, cachedTargetsSize);
                assertNotEquals(0, interopTargetsSize);
            } else {
                // we need to have stable call targets after the first run.
                assertEquals(cachedTargetsSize, channel.parseTargets.size());
                assertEquals(interopTargetsSize, channel.interopTargets.size());
            }
        }
    }

    @FunctionalInterface
    interface TestInterface {
        void foobar();
    }

    interface ArrayLike {
        @MethodMessage(message = "WRITE")
        void set(int index, Object value);

        @MethodMessage(message = "READ")
        Object get(int index);

        @MethodMessage(message = "GET_SIZE")
        int size();

        @MethodMessage(message = "HAS_SIZE")
        boolean isArray();
    }

    @Test
    public void testCachingFailing() {
        CachingLanguageChannel channel = new CachingLanguageChannel();
        PolyglotEngine vm = register(createBuilder().config(CachingLanguage.MIME_TYPE, "channel", channel).build());

        final Source source1 = Source.newBuilder("boxed").name("something").mimeType(CachingLanguage.MIME_TYPE).build();
        final Source source2 = Source.newBuilder("boxed").name("something").mimeType(CachingLanguage.MIME_TYPE).build();

        int cachedTargetsSize = -1;
        int interopTargetsSize = -1;

        for (int i = 0; i < 10; i++) {
            Value value1 = vm.eval(source1);
            Value value2 = vm.eval(source2);

            TestInterface testInterface1 = value1.as(TestInterface.class);
            testInterface1.foobar();
            value1.as(Byte.class);
            value1.as(Short.class);
            value1.as(Integer.class);
            value1.as(Long.class);
            value1.as(Float.class);
            value1.as(Double.class);
            Map<?, ?> m1 = value1.as(Map.class);
            assertTrue(m1.isEmpty());
            List<?> l1 = value1.as(List.class);
            assertEquals(0, l1.size());
            ArrayLike a1 = value1.as(ArrayLike.class);
            assertEquals(0, a1.size());
            assertTrue(a1.isArray());

            TestInterface testInterface2 = value2.as(TestInterface.class);
            testInterface2.foobar();
            value2.as(Byte.class);
            value2.as(Short.class);
            value2.as(Integer.class);
            value2.as(Long.class);
            value2.as(Float.class);
            value2.as(Double.class);
            value2.as(Map.class);
            Map<?, ?> m2 = value2.as(Map.class);
            assertTrue(m2.isEmpty());
            List<?> l2 = value2.as(List.class);
            assertEquals(0, l2.size());
            ArrayLike a2 = value1.as(ArrayLike.class);
            assertEquals(0, a2.size());
            assertTrue(a2.isArray());

            if (i == 0) {
                // warmup
                cachedTargetsSize = channel.parseTargets.size();
                interopTargetsSize = channel.interopTargets.size();
                assertNotEquals(0, cachedTargetsSize);
                assertNotEquals(0, interopTargetsSize);
                channel.frozen = true;
            } else {
                // we need to have stable call targets after the first run.
                assertEquals(cachedTargetsSize, channel.parseTargets.size());
                assertEquals(interopTargetsSize, channel.interopTargets.size());
            }
        }
    }

    private static class CachingLanguageChannel {

        final List<CallTarget> parseTargets = new ArrayList<>();
        final List<CallTarget> interopTargets = new ArrayList<>();

        boolean frozen;
    }

    private static class CachingTruffleObject implements TruffleObject {

        private final CachingLanguageChannel channel;
        private boolean boxed;

        CachingTruffleObject(CachingLanguageChannel channel, boolean boxed) {
            this.channel = channel;
            this.boxed = boxed;
        }

        public ForeignAccess getForeignAccess() {
            return ForeignAccess.create(new ForeignAccess.Factory() {

                @Override
                public boolean canHandle(TruffleObject obj) {
                    return true;
                }

                public CallTarget accessMessage(final Message tree) {
                    RootNode root = new RootNode(TruffleLanguage.class, null, null) {
                        @Override
                        public Object execute(VirtualFrame frame) {
                            if (tree == Message.IS_BOXED) {
                                return boxed;
                            } else if (tree == Message.IS_EXECUTABLE) {
                                return true;
                            } else if (tree == Message.IS_NULL) {
                                return false;
                            } else if (tree == Message.HAS_SIZE) {
                                return true;
                            } else if (tree == Message.GET_SIZE) {
                                return 0;
                            } else if (tree == Message.KEYS) {
                                return JavaInterop.asTruffleObject(Collections.emptyList());
                            } else if (tree == Message.UNBOX) {
                                return 42;
                            }
                            return new CachingTruffleObject(channel, boxed);
                        }
                    };
                    CallTarget target = Truffle.getRuntime().createCallTarget(root);
                    channel.interopTargets.add(target);
                    if (channel.frozen) {
                        throw new IllegalStateException("No new calltargets for " + tree);
                    }
                    return target;
                }
            });
        }

    }

    @TruffleLanguage.Registration(mimeType = CachingLanguage.MIME_TYPE, version = "", name = "")
    public static final class CachingLanguage extends TruffleLanguage<CachingLanguageChannel> {

        static final String MIME_TYPE = "application/x-test-caching";

        public static final CachingLanguage INSTANCE = new CachingLanguage();

        public CachingLanguage() {
        }

        @Override
        protected CachingLanguageChannel createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            return (CachingLanguageChannel) env.getConfig().get("channel");
        }

        @Override
        protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            final boolean boxed = request.getSource().getCode().equals("boxed");
            final CachingLanguageChannel channel = findContext(createFindContextNode());
            RootNode root = new RootNode(TruffleLanguage.class, null, null) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return new CachingTruffleObject(channel, boxed);
                }
            };
            CallTarget target = Truffle.getRuntime().createCallTarget(root);
            channel.parseTargets.add(target);
            if (channel.frozen) {
                throw new IllegalStateException("No new calltargets");
            }
            return target;
        }

        @Override
        protected Object findExportedSymbol(CachingLanguageChannel context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(CachingLanguageChannel context) {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

}
