/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.examples;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.BytecodeTier;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.serialization.SerializationUtils;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.bytecode.test.examples.BuiltinsTutorialFactory.ParseIntBuiltinNodeGen;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This tutorial demonstrates how to implement guest language builtin functions using a Bytecode DSL
 * interpreter. Builtins typically refer to functions that are shipped as part of the language to
 * access functionality not directly reachable with language syntax.
 * <p>
 * This tutorial explains three different ways to implement builtins. One by specifying a Truffle
 * node (JavaBuiltin), one using direct specification of builder calls (BuilderBuiltin) and another
 * one using guest language source code that is lazily parsed on first use or at native-image build
 * time (SerializedBuiltin). It also demonstrates how these builtins can be used in the polyglot
 * API.
 * <p>
 * We recommend completing the {@link GettingStarted}, {@link ParsingTutorial} and the
 * {@link SerializationTutorial} before reading this tutorial.
 * <p>
 * This tutorial is intended to be read top-to-bottom and contains some runnable unit tests.
 */
public class BuiltinsTutorial {

    /**
     * We start by specifying a sealed base class for all of our different kinds of builtins. Every
     * builtin contains meta-information for the name and the number of arguments. Depending on the
     * language you may specify different or more meta-data here, for example type information for
     * the arguments.
     */
    abstract static sealed class AbstractBuiltin permits JavaBuiltin, BuilderBuiltin, SerializedBuiltin {

        final String name;
        final int args;

        AbstractBuiltin(String name, int args) {
            this.name = name;
            this.args = args;
        }

        /**
         * Every builtin allows the creation of a Truffle {@link CallTarget} that can be used to
         * invoke that builtin.
         */
        abstract CallTarget getOrCreateCallTarget();
    }

    /**
     * Next, we specify a new Bytecode DSL root node. We explicitly enable uncached interpretation
     * to show how uncached Java nodes can be integrated for builtins. We also enable serialization
     * as we will need that for the third category of builtins {@link SerializedBuiltin}.
     */
    @GenerateBytecode(languageClass = LanguageWithBuiltins.class, enableUncachedInterpreter = true, enableSerialization = true)
    public abstract static class BuiltinLanguageRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected BuiltinLanguageRootNode(LanguageWithBuiltins language,
                        FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        /**
         * As the first operation we add the ability to call builtins using the call target returned
         * by {@link AbstractBuiltin#getOrCreateCallTarget()}. In our case we model the builtin as a
         * constant operand, but in case the builtin is not known at parse time, the builtin could
         * also be looked up dynamically.
         */
        @Operation
        @ConstantOperand(type = AbstractBuiltin.class)
        static final class CallBuiltin {

            @Specialization
            public static Object doDefault(AbstractBuiltin b, @Variadic Object[] args) {
                /**
                 * We just forward the variadic arguments of the operation to the call target. The
                 * first time a builtin is called the call target will be created, and for
                 * consecutive calls we will reuse the already created call target.
                 *
                 * Instead of calling the CallTarget directly, like we do here, we could also use a
                 * DirectCallNode instead. Then CallTarget cloning/splitting would also be
                 * supported.
                 */
                return b.getOrCreateCallTarget().call(args);
            }
        }

        /**
         * The implementation of a {@link JavaBuiltin} will execute a plain Truffle node. This
         * operation is used by {@link JavaBuiltin#getOrCreateCallTarget()} to inline the Truffle
         * node into the interpreter and directly execute it, delegating the cached and uncached
         * node creation to the {@link JavaBuiltin} implementation.
         */
        @Operation
        @ConstantOperand(type = JavaBuiltin.class)
        static final class InlineBuiltin {

            @Specialization
            @SuppressWarnings("unused")
            public static Object doDefault(JavaBuiltin builtin,

                            /*
                             * The variadic annotation allows us to pass any number of arguments to
                             * the builtin. The concrete number of arguments is contained in {@link
                             * AbstractBuiltin#args}. We do not statically know them for the
                             * builtin. In order to avoid the Object[] allocation one could create
                             * individual operations for a specific number of arguments.
                             */
                            @Variadic Object[] args,

                            /*
                             * This instructs the DSL to create a new node for any cached usage of
                             * the builtin. For the uncached initializer builtin.getUncached() is
                             * used. An uncached version of a node is useful to avoid repeated
                             * allocations of a cached node if a cached profile is not required.
                             */
                            @Cached(value = "builtin.createNode()", uncached = "builtin.getUncached()", neverDefault = true)//
                            BuiltinNode node) {

                /*
                 * The specialization does not do anything special, it just executes the node. This
                 * can either be the cached or the uncached node here.
                 */
                return node.execute(args);
            }
        }
    }

    /**
     * Next, we declare a simple helper method that takes a bytecode parser with the generated
     * builder and returns the parsed root node. This will come in handy to implement
     * {@link AbstractBuiltin#getOrCreateCallTarget()} later.
     */
    private static BuiltinLanguageRootNode parse(LanguageWithBuiltins language, BytecodeParser<BuiltinLanguageRootNodeGen.Builder> parser) {
        BytecodeRootNodes<BuiltinLanguageRootNode> nodes = BuiltinLanguageRootNodeGen.create(language, BytecodeConfig.DEFAULT, parser);
        /**
         * In our language we only ever parse one root node at a time. So we can just return the
         * root node directly.
         */
        return nodes.getNodes().get(0);
    }

    /**
     * Next, we create a base class for all Java based builtins.
     */
    abstract static class BuiltinNode extends Node {

        /**
         * Truffle automatically interprets execute methods with varargs as a variable number of
         * arguments. However any specific subclass must have fixed number of dynamic arguments.
         * This is quite convenient, as we are using the {@link Variadic} feature to collect the
         * arguments from the bytecode stack and then we can directly forward the created object
         * array to this execute method.
         */
        abstract Object execute(Object... args);
    }

    /**
     * Now let's declare a sample builtin node. This builtin just tries to parse an int from a
     * string. If the operand is already an int there is nothing to do. If it is a string we call to
     * {@link Integer#parseInt(String)} to parse the number.
     *
     * Since we are extending {@link BuiltinNode} we do not need to declare an execute method here,
     * as the execute method of the base class will automatically be used and implemented by Truffle
     * DSL. To understand the behavior it can be a good idea to look at the generated implementation
     * here: {@link ParseIntBuiltinNodeGen#execute(Object...)}. As you can see the DSL automatically
     * maps the varargs arguments of the execute method as a dynamic operand to the node.
     *
     * We are also specifying {@link GenerateUncached} such that a
     * {@link ParseIntBuiltinNodeGen#getUncached()} method is generated. We will need that later in
     * the {@link JavaBuiltin} instantiation (see {@link #createJavaBuiltin()}).
     */
    @GenerateUncached
    @GenerateCached
    @GenerateInline(false) // make inlining warning go away
    abstract static class ParseIntBuiltinNode extends BuiltinNode {

        @Specialization
        int doInt(int a) {
            // Already an int, so nothing to do.
            return a;
        }

        @Specialization
        @TruffleBoundary
        int doString(String a) {
            try {
                return Integer.parseInt(a);
            } catch (NumberFormatException e) {
                // a real language would translate this error to an AbstractTruffleException here.
                // we are lazy, so skip this step for now.
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    /**
     * It's time to implement our first builtin variant: one that is backed by a Truffle node. There
     * are many different ways to represent such a builtin, but we decide to take the uncached node
     * directly as parameter and we use {@link Supplier} for a factory method to create the cached
     * node. Alternatively one could use the {@link GenerateNodeFactory} feature of the Truffle DSL.
     */
    static final class JavaBuiltin extends AbstractBuiltin {

        private final BuiltinNode uncached;
        private final Supplier<BuiltinNode> createCached;
        @CompilationFinal private CallTarget cachedTarget;

        JavaBuiltin(String name, int args, BuiltinNode uncachedNode, Supplier<BuiltinNode> createCached) {
            super(name, args);
            this.uncached = uncachedNode;
            this.createCached = createCached;
        }

        /**
         * Now we have all the parts together to create a Bytecode DSL builtin root node. In order
         * to minimize memory footprint we lazily create the call target on first access.
         *
         * This root node is a simple bytecode node that executes the {@link JavaBuiltin} using the
         * {@link BuiltinLanguageRootNode#InlineBuiltin} operation. It will automatically transition
         * the interpreter (and hence, the builtin node) from uncached to cached.
         *
         * One advantage of using the Bytecode DSL to implement the builtin root node is that we
         * automatically get the method {@link Prolog} and {@link EpilogReturn} executed. In
         * addition a bytecode root node supports bytecode and tag instrumentation and also offers
         * support for continuations. If none of that is needed, some languages may instead opt to
         * create a dedicated {@link RootNode} subclass for builtins.
         *
         * Another advantage of using bytecode to inline builtins is that they can also be used
         * directly in the code without call semantics (i.e., instead of a builtin being a call
         * target, as it is here, the parser could use {@link BuiltinLanguageRootNode#InlineBuiltin}
         * to directly inline a builtin node into a guest bytecode method).
         */
        @Override
        CallTarget getOrCreateCallTarget() {
            if (cachedTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedTarget = parse(LanguageWithBuiltins.get(), (b) -> {
                    b.beginRoot();
                    b.beginInlineBuiltin(this);
                    for (int i = 0; i < args; i++) {
                        b.emitLoadArgument(i);
                    }
                    b.endInlineBuiltin();
                    b.endRoot();
                }).getCallTarget();
            }
            return cachedTarget;
        }

        /**
         * Since we enable {@link GenerateBytecode#enableUncachedInterpreter()}, the builtin root
         * node will first use the uncached version of the node. It can transition to cached and
         * uses the cached version supplied by {@link #createNode} below.
         */
        BuiltinNode getUncached() {
            return uncached;
        }

        /**
         * When the Bytecode DSL interpreter transitions from uncached to cached we call this
         * supplier to create the the cached node. This, by default, happens after 16 calls or loop
         * iterations (controlled by {@link BytecodeNode#setUncachedThreshold}).
         */
        BuiltinNode createNode() {
            return createCached.get();
        }
    }

    /**
     * Now let's put everything together and create our first {@link JavaBuiltin} with
     * {@link ParseIntBuiltinNode}.
     */
    static JavaBuiltin createJavaBuiltin() {
        return new JavaBuiltin("parseInt", 1, ParseIntBuiltinNodeGen.getUncached(), ParseIntBuiltinNodeGen::create);
    }

    private Context context;

    /**
     * In order to use <code>LanguageWithBuiltins.get()</code> in a unit-test we need to enter a
     * polyglot context. In a real language you would always be entered as the parse request was
     * triggered through a {@link TruffleLanguage#parse parse request}.
     */
    @Before
    public void enterContext() {
        context = Context.create("language-with-builtins");
        context.enter();
    }

    @After
    public void closeContext() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /**
     * Let's verify that calling a builtin works as expected. Note that this test recreates the
     * ParseInt builtin for every call, which is not ideal. We will fix this later with
     * {@link LanguageWithBuiltins}.
     */
    @Test
    public void testCallJavaBuiltin() {
        BuiltinLanguageRootNode root = parse(LanguageWithBuiltins.get(), b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCallBuiltin(createJavaBuiltin());
            b.emitLoadArgument(0);
            b.endCallBuiltin();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(42, root.getCallTarget().call("42"));
    }

    /**
     * We can also inline the builtin directly into the bytecodes like in the test below. This is
     * only allowed if the language semantics allow for builtins not being on the stack trace. For
     * calls to be visible in the stack trace they need to go through a {@link CallTarget}.
     */
    @Test
    public void testInlineJavaBuiltin() {
        BuiltinLanguageRootNode root = parse(LanguageWithBuiltins.get(), b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginInlineBuiltin(createJavaBuiltin());
            b.emitLoadArgument(0);
            b.endInlineBuiltin();
            b.endReturn();
            b.endRoot();
        });

        root.getBytecodeNode().setUncachedThreshold(2);
        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(BytecodeTier.UNCACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call("42"));
        // transitions to cached once the threshold is exceeded
        assertEquals(BytecodeTier.CACHED, root.getBytecodeNode().getTier());
        assertEquals(42, root.getCallTarget().call(42));
        assertEquals(42, root.getCallTarget().call("42"));
    }

    /**
     * Another way of specifying builtins is to use a builder lambda directly. This way you can
     * minimize binary footprint by expressing a builtin with regular bytecodes. As languages
     * mature, they often grow in size and binary footprint becomes more pronounced. Using builder
     * builtins it is possible to reduce the number of methods reachable for partial evaluation, as
     * we are reusing existing bytecodes to implement them. This may reduce the binary footprint of
     * the interpreter.
     */
    static final class BuilderBuiltin extends AbstractBuiltin {

        private final BytecodeParser<BuiltinLanguageRootNodeGen.Builder> parser;
        @CompilationFinal private CallTarget cachedTarget;

        BuilderBuiltin(String name, int args, BytecodeParser<BuiltinLanguageRootNodeGen.Builder> parser) {
            super(name, args);
            this.parser = parser;
        }

        /**
         * Builder builtins are simple, they can directly use the passed parser to create the call
         * target.
         */
        @Override
        CallTarget getOrCreateCallTarget() {
            if (cachedTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedTarget = parse(LanguageWithBuiltins.get(), parser).getCallTarget();
            }
            return cachedTarget;
        }

    }

    /**
     * This is a simple example of a builtin that just returns 42.
     */
    static BuilderBuiltin createBuilderBuiltin() {
        return new BuilderBuiltin("builderBuiltin", 0, (b) -> {
            b.beginRoot();
            b.beginReturn();
            b.emitLoadConstant(42);
            b.endReturn();
            b.endRoot();
        });
    }

    /**
     * Like {@link #testCallJavaBuiltin}, test that we can call a BuilderBuiltin.
     */
    @Test
    public void testCallBuilderBuiltin() {
        BuiltinLanguageRootNode root = parse(LanguageWithBuiltins.get(), b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCallBuiltin(createBuilderBuiltin());
            b.endCallBuiltin();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call());
    }

    /**
     * The third and last kind of builtin in this tutorial is the {@link SerializedBuiltin}. It can
     * be useful to use the serialized form of the Bytecode DSL builder calls and load them from a
     * file if they are needed. This may further decrease binary footprint of the interpreter.
     */
    static final class SerializedBuiltin extends AbstractBuiltin {

        private final Supplier<byte[]> getBytes;
        @CompilationFinal CallTarget cachedTarget;

        SerializedBuiltin(String name, int args, Supplier<byte[]> getBytes) {
            super(name, args);
            this.getBytes = getBytes;
        }

        @Override
        CallTarget getOrCreateCallTarget() {
            if (cachedTarget == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedTarget = deserialize(getBytes.get()).getCallTarget();
            }
            return cachedTarget;
        }

    }

    /**
     * Using an inner class allows the BYTECODES to be lazily initialized on HotSpot and initialized
     * at build-time in native-images.
     */
    static class LazyBytecodes {

        private static final byte[] BYTECODES = loadCode();

        private static byte[] loadCode() {
            /**
             * In a real language we would invoke the parser for source code text here.
             * Alternatively the code could be loaded from file and be preparsed at build time.
             */
            return serialize((b) -> {
                b.beginRoot();
                b.beginReturn();
                b.emitLoadConstant(42);
                b.endReturn();
                b.endRoot();
            });
        }

        static byte[] get() {
            return BYTECODES;
        }
    }

    /**
     * Here are the two helpers to serialize and deserialize nodes from bytes. Please refer to the
     * {@link SerializationTutorial} for details on serialization.
     */
    private static BuiltinLanguageRootNode deserialize(byte[] deserialized) {
        try {
            BytecodeRootNodes<BuiltinLanguageRootNode> nodes = BuiltinLanguageRootNodeGen.deserialize(LanguageWithBuiltins.get(), BytecodeConfig.DEFAULT,
                            () -> SerializationUtils.createDataInput(ByteBuffer.wrap(deserialized)),
                            (context, input) -> {
                                return input.readInt();
                            });
            return nodes.getNodes().get(nodes.getNodes().size() - 1);
        } catch (IOException e) {
            // no IO exceptions expected
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static byte[] serialize(BytecodeParser<BuiltinLanguageRootNodeGen.Builder> parser) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        // Invoke serialize.
        try {
            BuiltinLanguageRootNodeGen.serialize(new DataOutputStream(output), (context, buffer, value) -> buffer.writeInt((int) value), parser);
        } catch (IOException e) {
            // no IO exceptions expected
            throw CompilerDirectives.shouldNotReachHere(e);
        }

        // The results will be written to the output buffer.
        return output.toByteArray();
    }

    /**
     * This is how a serialized builtin would be created. In a real language it might be a good idea
     * to load and serialize groups of builtins at once.
     */
    static SerializedBuiltin createSerializedBuiltin() {
        return new SerializedBuiltin("serializedSample", 0, LazyBytecodes::get);
    }

    /**
     * Like {@link #testCallJavaBuiltin}, test that we can call a SerializedBuiltin.
     */
    @Test
    public void testCallSerializedBuiltin() {
        BuiltinLanguageRootNode root = parse(LanguageWithBuiltins.get(), b -> {
            b.beginRoot();
            b.beginReturn();
            b.beginCallBuiltin(createSerializedBuiltin());
            b.endCallBuiltin();
            b.endReturn();
            b.endRoot();
        });

        assertEquals(42, root.getCallTarget().call());
    }

    /**
     * We have demonstrated three ways to define builtins, and how to call/inline them in your
     * Bytecode DSL interpreter. Lastly, we will demonstrate how these builtins can be used as
     * interop values in the polyglot API.
     * <p>
     * We first define an interop object that wraps a builtin. It supports the {@code execute} and
     * {@code isExecutable} messages. When executed, it simply calls the call target with the
     * provided arguments.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class BuiltinExecutable implements TruffleObject {

        final AbstractBuiltin builtin;

        BuiltinExecutable(AbstractBuiltin builtin) {
            this.builtin = builtin;
        }

        @ExportMessage
        Object execute(Object[] args) {
            return builtin.getOrCreateCallTarget().call(args);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

    }

    /**
     * We declare a TruffleLanguage for the root node.
     * <p>
     * For simplicity, builtins are registered by name, and parsing returns the builtin whose name
     * matches the source content of the request.
     */
    @Registration(id = "language-with-builtins", name = "Language with Builtins Demo Language")
    static class LanguageWithBuiltins extends TruffleLanguage<Env> {

        private final Map<String, AbstractBuiltin> builtins = createBuiltins();

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        /**
         * We collect all of our builtins in a hash map for fast access. A real language would
         * probably have a group for each builtin namespace.
         */
        private static Map<String, AbstractBuiltin> createBuiltins() {
            Map<String, AbstractBuiltin> builtins = new HashMap<>();
            registerBuiltin(builtins, createJavaBuiltin());
            registerBuiltin(builtins, createBuilderBuiltin());
            registerBuiltin(builtins, createSerializedBuiltin());
            return builtins;
        }

        private static void registerBuiltin(Map<String, AbstractBuiltin> map, AbstractBuiltin b) {
            map.put(b.name, b);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            /**
             * Instead of parsing the source, like in a real language, for demonstration purposes we
             * only use the source characters and lookup the builtin by name.
             */
            AbstractBuiltin b = builtins.get(request.getSource().getCharacters());
            if (b == null) {
                throw CompilerDirectives.shouldNotReachHere("Invalid builtin.");
            }
            /**
             * We wrap the builtin in a BuiltinExecutable to make the builtin function executable
             * with parameters.
             */
            return RootNode.createConstantNode(new BuiltinExecutable(b)).getCallTarget();
        }

        private static final LanguageReference<LanguageWithBuiltins> REFERENCE = LanguageReference.create(LanguageWithBuiltins.class);

        /**
         * This allows languages to lookup the language as thread local.
         */
        static LanguageWithBuiltins get() {
            return REFERENCE.get(null);
        }
    }

    /**
     * Finally, put everything together. We obtain the builtins as interop objects using
     * {@code eval} and then execute them using the polyglot API.
     */
    @Test
    public void testLanguageWithBuiltins() {
        assertEquals(42, context.eval("language-with-builtins", "parseInt").execute(42).asInt());
        assertEquals(42, context.eval("language-with-builtins", "parseInt").execute("42").asInt());

        assertEquals(42, context.eval("language-with-builtins", "builderBuiltin").execute().asInt());
        assertEquals(42, context.eval("language-with-builtins", "serializedSample").execute().asInt());
    }

}
