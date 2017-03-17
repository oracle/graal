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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.LanguageInfo;
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
import com.oracle.truffle.api.vm.PolyglotRuntime;

public class EngineTest {
    private final PolyglotRuntime testRuntime = PolyglotRuntime.newBuilder().build();
    private final Set<PolyglotEngine> toDispose = new HashSet<>();

    protected PolyglotEngine.Builder createBuilder() {
        return PolyglotEngine.newBuilder();
    }

    private PolyglotEngine.Builder createBuilderInternal() {
        PolyglotEngine.Builder builder = createBuilder();
        builder.runtime(testRuntime);
        return builder;
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
        Builder builder = createBuilderInternal();
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
                    RootNode root = new RootNode(null) {
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

        public CachingLanguage() {
        }

        @Override
        protected CachingLanguageChannel createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            return (CachingLanguageChannel) env.getConfig().get("channel");
        }

        @Override
        protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            final boolean boxed = request.getSource().getCode().equals("boxed");
            final CachingLanguageChannel channel = getContextReference().get();
            RootNode root = new RootNode(this) {
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

    @Test
    public void languageInstancesAreNotShared() {
        ForkingLanguage.constructorInvocationCount = 0;
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel1 = new ForkingLanguageChannel(builder::build);
        PolyglotEngine vm1 = register(builder.config(ForkingLanguage.MIME_TYPE, "channel", channel1).build());
        register(vm1);
        vm1.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();

        assertEquals(1, ForkingLanguage.constructorInvocationCount);

        ForkingLanguageChannel channel2 = new ForkingLanguageChannel(builder::build);
        PolyglotEngine vm2 = register(createBuilder().config(ForkingLanguage.MIME_TYPE, "channel", channel2).build());
        register(vm2);
        vm2.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();

        assertEquals(2, ForkingLanguage.constructorInvocationCount);
        assertNotSame(channel1.language, channel2.language);
    }

    @Test
    public void basicForkTest() throws Exception {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        builder.config(ForkingLanguage.MIME_TYPE, "channel", channel);
        PolyglotEngine vm = register(builder.build());

        PolyglotEngine uninitializedFork = builder.build();

        // language is not yet initialized -> no fork necessary
        assertEquals(0, channel.forks.size());

        assertEquals(channel.globalObject, vm.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject().as(String.class));
        assertEquals(1, channel.language.createContextCount);
        assertEquals(0, channel.language.forkContextCount);
        assertEquals(0, channel.language.disposeContextCount);

        // unsure that the uninitialized fork creates its own context
        uninitializedFork.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();
        assertEquals(2, channel.language.createContextCount);
        assertEquals(0, channel.language.forkContextCount);
        assertEquals(0, channel.language.disposeContextCount);

        List<PolyglotEngine> forks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            forks.add(channel.fork());
            assertEquals(channel.forks.size(), forks.size());

            assertEquals(2, channel.language.createContextCount);
            assertEquals(i + 1, channel.language.forkContextCount);
            assertEquals(0, channel.language.disposeContextCount);
            assertEquals(channel.forks.get(i).globalObject, forks.get(i).getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject().as(String.class));
        }

        for (ForkingLanguageChannel forkChannel : channel.forks) {
            // the language instance is shared across all languages.
            assertSame(channel.language, forkChannel.language);
        }

        int forksLeft = forks.size();
        for (PolyglotEngine fork : forks) {
            // test we can still safely access the global object
            fork.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();

            fork.dispose();
            forksLeft--;
            assertEquals(channel.forks.size(), forksLeft);

            // test we can still safely access the global object of the origin vm
            vm.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();
            assertEquals(2, channel.language.createContextCount);
            assertEquals(5, channel.language.forkContextCount);
            assertEquals(forks.indexOf(fork) + 1, channel.language.disposeContextCount);
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void forkUnsupportedFailsGracefully() throws Exception {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        builder.config(ForkingLanguage.MIME_TYPE, "channel", channel);
        PolyglotEngine vm = register(builder.build());

        // fork supported when not initialized
        try {
            assertNotNull(channel.fork());
        } catch (Exception e) {
            fail();
        }

        vm.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();

        channel.language.forkSupported = false;
        channel.fork();
    }

    @Test
    public void forkedSymbolsNotSharedButCopied() throws Exception {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        builder.config(ForkingLanguage.MIME_TYPE, "channel", channel);
        PolyglotEngine vm = register(builder.build());
        channel.symbols.put("sym1", "symvalue1");
        vm.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject(); // initialize language

        assertEquals("symvalue1", vm.findGlobalSymbol("sym1").as(String.class));

        PolyglotEngine fork = channel.fork();

        assertEquals("symvalue1", vm.findGlobalSymbol("sym1").as(String.class));
        assertEquals("symvalue1", fork.findGlobalSymbol("sym1").as(String.class));

        final ForkingLanguageChannel forkChannel = channel.forks.get(0);
        forkChannel.symbols.put("sym2", "symvalue2");

        assertNull(vm.findGlobalSymbol("sym2"));
        assertEquals("symvalue2", fork.findGlobalSymbol("sym2").as(String.class));

        channel.symbols.put("sym2", "symvalue3");

        assertEquals("symvalue3", vm.findGlobalSymbol("sym2").as(String.class));
        assertEquals("symvalue2", fork.findGlobalSymbol("sym2").as(String.class));

        assertEquals("symvalue1", vm.findGlobalSymbol("sym1").as(String.class));
        assertEquals("symvalue1", fork.findGlobalSymbol("sym1").as(String.class));

        PolyglotEngine forkfork = forkChannel.fork();
        assertEquals("symvalue1", forkfork.findGlobalSymbol("sym1").as(String.class));
        assertEquals("symvalue2", forkfork.findGlobalSymbol("sym2").as(String.class));
    }

    @Test
    public void forkInLanguageTest() {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        PolyglotEngine vm = builder.config(ForkingLanguage.MIME_TYPE, "channel", channel).build();

        vm.eval(Source.newBuilder("").name("").mimeType(ForkingLanguage.MIME_TYPE).build()).get();

        assertEquals(1, channel.languageForks.size());
        assertFalse(channel.languageForks.get(0).disposed);

        vm.dispose();
        // make sure language forks are disposed with the engine that created it.
        assertTrue(channel.languageForks.get(0).disposed);

    }

    @Test
    public void testLanguageInfo() {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        PolyglotEngine vm = builder.config(ForkingLanguage.MIME_TYPE, "channel", channel).build();
        vm.eval(Source.newBuilder("").name("").mimeType(ForkingLanguage.MIME_TYPE).build()).get();

        assertNotNull(channel.info);
        assertEquals(1, channel.info.getMimeTypes().size());
        assertTrue(channel.info.getMimeTypes().contains(ForkingLanguage.MIME_TYPE));
        assertEquals("forkinglanguage", channel.info.getName());
        assertEquals("version", channel.info.getVersion());
    }

    @Test
    public void testLanguageAccess() {
        final Builder builder = createBuilderInternal();
        ForkingLanguageChannel channel = new ForkingLanguageChannel(builder::build);
        PolyglotEngine vm = builder.config(ForkingLanguage.MIME_TYPE, "channel", channel).build();
        vm.eval(Source.newBuilder("").name("").mimeType(ForkingLanguage.MIME_TYPE).build()).get();

        RootNode root = new RootNode(channel.language) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        try {
            // no access using a TruffleLanguage hack
            root.getLanguage(TruffleLanguage.class);
            fail();
        } catch (ClassCastException e) {
        }

        Class<?> oClass = Object.class;

        @SuppressWarnings({"rawtypes", "unchecked"})
        Class<? extends TruffleLanguage> lang = (Class<? extends TruffleLanguage>) oClass;

        try {
            // no access using a TruffleLanguage class cast
            root.getLanguage(lang);
            fail();
        } catch (ClassCastException e) {
        }

        oClass = SecretInterfaceType.class;
        @SuppressWarnings({"rawtypes", "unchecked"})
        Class<? extends TruffleLanguage> secretInterface = (Class<? extends TruffleLanguage>) oClass;
        try {
            // no access using secret interface
            root.getLanguage(secretInterface);
            fail();
        } catch (ClassCastException e) {
        }

        // this should work as expected
        assertNotNull(root.getLanguage(ForkingLanguage.class));
    }

    interface SecretInterfaceType {

    }

    private static class ForkingLanguageChannel implements TruffleObject {

        ForkingLanguage language;

        private static int globalIndex = 0;

        final String globalObject = "global" + globalIndex++;
        final ForkingLanguageChannel parent;
        final Map<String, Object> symbols = new HashMap<>();
        final List<ForkingLanguageChannel> forks = new ArrayList<>();
        final List<ForkingLanguageChannel> languageForks = new ArrayList<>();
        final List<PolyglotEngine> dispose = new ArrayList<>();

        LanguageInfo info;

        boolean disposed;
        ForkingLanguageChannel toFork;
        Callable<PolyglotEngine> toCreate;

        ForkingLanguageChannel(Callable<PolyglotEngine> toCreate) {
            this((ForkingLanguageChannel) null);
            this.toCreate = toCreate;
        }

        ForkingLanguageChannel(ForkingLanguageChannel parent) {
            this.parent = parent;
            if (parent != null) {
                this.symbols.putAll(parent.symbols);
            }
            this.symbols.put("thisContext", this);
        }

        PolyglotEngine fork() {
            ForkingLanguageChannel channel = this;
            while (channel.parent != null) {
                channel = channel.parent;
            }
            channel.toFork = this;
            PolyglotEngine fork;
            try {
                fork = channel.toCreate.call();
            } catch (Exception ex) {
                throw raise(RuntimeException.class, ex);
            }
            fork.getLanguages().get(ForkingLanguage.MIME_TYPE).getGlobalObject();
            assertNull("The toFork channel was used", channel.toFork);
            dispose.add(fork);
            return fork;
        }

        @SuppressWarnings("all")
        private static <E extends Exception> E raise(Class<E> aClass, Exception ex) throws E {
            throw (E) ex;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }

    }

    @TruffleLanguage.Registration(mimeType = ForkingLanguage.MIME_TYPE, version = "version", name = "forkinglanguage")
    public static final class ForkingLanguage extends TruffleLanguage<ForkingLanguageChannel> implements SecretInterfaceType {

        static final String MIME_TYPE = "application/x-test-forking";

        static int constructorInvocationCount;

        int createContextCount = 0;
        int disposeContextCount = 0;
        int forkContextCount = 0;

        boolean forkSupported = true;

        public ForkingLanguage() {
            constructorInvocationCount++;
        }

        @Override
        protected ForkingLanguageChannel createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
            ForkingLanguageChannel channel = (ForkingLanguageChannel) env.getConfig().get("channel");
            if (channel.toFork != null) {
                ForkingLanguageChannel forking = channel.toFork;
                channel.toFork = null;
                return forkContext(forking);
            }
            createContextCount++;
            channel.language = this;
            channel.info = new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    return null;
                }
            }.getLanguageInfo();

            return channel;
        }

        protected ForkingLanguageChannel forkContext(ForkingLanguageChannel context) {
            forkContextCount++;
            if (!forkSupported) {
                throw new UnsupportedOperationException();
            }
            ForkingLanguageChannel channel = new ForkingLanguageChannel(context);
            channel.language = this;
            context.forks.add(channel);
            return channel;
        }

        @Override
        protected void disposeContext(ForkingLanguageChannel context) {
            disposeContextCount++;
            context.disposed = true;
            if (context.parent != null) {
                context.parent.forks.remove(context);
            }
            for (PolyglotEngine eng : context.dispose) {
                try {
                    eng.dispose();
                } catch (IllegalStateException ex) {
                    // ignore
                }
            }
        }

        @Override
        protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {
                boolean initialized;

                @Override
                public Object execute(VirtualFrame frame) {
                    if (!initialized) {
                        initialized = true;
                        int prevForkContextCount = forkContextCount;
                        final ForkingLanguageChannel myContext = getContextReference().get();
                        PolyglotEngine eng = myContext.fork();
                        assertEquals(prevForkContextCount + 1, forkContextCount);
                        ForkingLanguageChannel forkedContext = eng.findGlobalSymbol("thisContext").as(ForkingLanguageChannel.class);
                        getContextReference().get().languageForks.add(forkedContext);
                        assertEquals(getContextReference().get(), forkedContext.parent);
                    }

                    int prevForkContextCount = forkContextCount;
                    final ForkingLanguageChannel myContext = getContextReference().get();
                    PolyglotEngine fork = myContext.fork();
                    assertEquals(prevForkContextCount + 1, forkContextCount);
                    int prevDisposeCount = disposeContextCount;
                    fork.dispose();
                    assertEquals(prevDisposeCount + 1, disposeContextCount);

                    return null;
                }
            });
        }

        @Override
        protected Object findExportedSymbol(ForkingLanguageChannel context, String globalName, boolean onlyExplicit) {
            return context.symbols.get(globalName);
        }

        @Override
        protected Object getLanguageGlobal(ForkingLanguageChannel context) {
            return context.globalObject;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }
    }

}
