/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.polyglot.Context;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.SetTraceFuncTest.SetTraceFuncRootNode;
import com.oracle.truffle.api.bytecode.test.examples.InstrumentationTutorial.InstrumentationBytecodeNode.InvertBoolean;
import com.oracle.truffle.api.bytecode.test.examples.InstrumentationTutorial.InstrumentationBytecodeNode.Log;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Truffle instrumentation allows an interpreter to dynamically modify and introspect its behaviour
 * to support use cases like debugging, tracing and logging. The Bytecode DSL supports two forms of
 * instrumentation:
 * <ul>
 * <li>Custom {@link Instrumentation @Instrumentation} operations, which can be inserted at or
 * around arbitrary locations in a program. These are useful for language-specific instrumentation
 * or debugging.
 * <li>{@code Tag} operations, which associate {@link com.oracle.truffle.api.instrumentation.Tag
 * instrumentation tags} to their enclosed operations. Tag operations integrate with
 * {@link TruffleInstrument Truffle instruments} (such as the {@link DebuggerInstrument debugger})
 * to identify different kinds of program locations (e.g., {@link StandardTags.StatementTag
 * statements} or {@link StandardTags.ExpressionTag expressions}). An instrument can register event
 * listeners to track the execution of code with a particular tag; for example, an instrument may
 * collect coverage information by listening for enter and exit events on statements.
 * </ul>
 * These instrumentations are specified at bytecode parse time, but have no execution overhead
 * unless enabled. Arbitrary combinations of them can be enabled at any given time.
 * <p>
 * This tutorial demonstrates how to use instrumentation in a Bytecode DSL interpreter.
 */
public class InstrumentationTutorial {
    /**
     * First, we define a Bytecode DSL interpreter.
     * <p>
     * The interpreter declares two custom {@link Instrumentation @Instrumentation} operations in
     * the class body, {@link Log} and {@link InvertBoolean}.
     * <p>
     * The interpreter also enables tag instrumentation using
     * {@link GenerateBytecode#enableTagInstrumentation()}. All of the tags {@link ProvidedTags
     * provided} by the language class become available for tag instrumentation (by default, the
     * interpreter performs {@link GenerateBytecode#enableRootTagging() root} and
     * {@link GenerateBytecode#enableRootBodyTagging() root body} tagging automatically when those
     * tags are provided). See {@link #testTags} for a demonstration.
     */
    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableTagInstrumentation = true)
    public abstract static class InstrumentationBytecodeNode extends RootNode implements BytecodeRootNode {
        protected InstrumentationBytecodeNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        /**
         * A custom {@link Instrumentation @Instrumentation} operation is a special {@link Operation
         * operation} that can be dynamically enabled. Since they are disabled by default, custom
         * instrumentations must have transparent stack effects: that is, they must have no dynamic
         * operands and produce no value, or have one dynamic operand and produce a value.
         * <p>
         * Here, we define a custom instrumentation operation of the first kind: a simple log
         * operation that prints a message when enabled. See {@link #testLog} for a demonstration.
         */
        @Instrumentation
        @ConstantOperand(type = String.class, name = "message")
        @ConstantOperand(type = PrintWriter.class, name = "out")
        public static final class Log {
            @Specialization
            @TruffleBoundary
            public static void perform(String message, PrintWriter out) {
                out.println(message);
            }
        }

        /**
         * Here, we define an instrumentation of the second kind: an operation that produces the
         * negation of its boolean operand (say, for fault injection testing). Unlike {@link Log},
         * this instrumentation changes the value it instruments. See {@link #testInvertBoolean} for
         * a demonstration.
         */
        @Instrumentation
        public static final class InvertBoolean {
            @Specialization
            public static boolean perform(boolean value) {
                return !value;
            }
        }

        /**
         * This operation uses on-stack instrumentation to enable {@link Log} instrumentation for
         * the current root node. See {@link #testOnStackInstrumentation} for a demonstration.
         */
        @Operation
        public static final class EnableLogInstrumentation {
            @Specialization
            public static void perform(@Bind InstrumentationBytecodeNode root) {
                /*
                 * Updates the root to enable instrumentation. Importantly, if the instrumentation
                 * is already enabled, update has no effect and gets folded away by PE. In order for
                 * PE to fold the call, config must be a PE constant, which is why we compute it
                 * once and cache it.
                 */
                root.getRootNodes().update(LOG_INSTRUMENTATION_CONFIG);
            }
        }

        /**
         * Tag-based instrumentation typically requires you to specify {@code Tag} operations that
         * wrap other operations. If an operation always corresponds to some tag (e.g., an
         * expression), you can set the {@link Operation#tags} field of the operation. See
         * {@link #testImplicitTags} for a demonstration.
         */
        @Operation(tags = {ExpressionTag.class})
        public static final class Add {
            @Specialization
            public static int perform(int left, int right) {
                return left + right;
            }
        }

    }

    private static final BytecodeConfig LOG_INSTRUMENTATION_CONFIG = InstrumentationBytecodeNodeGen.newConfigBuilder().addInstrumentation(Log.class).build();
    private static final BytecodeConfig INVERT_BOOLEAN_INSTRUMENTATION_CONFIG = InstrumentationBytecodeNodeGen.newConfigBuilder().addInstrumentation(InvertBoolean.class).build();

    /**
     * Let's test the {@link Log} instrumentation.
     */
    @Test
    public void testLog() {
        Context ctx = setupContext();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        // This program is a simple if-then-else with some logging provided by instrumentation.
        InstrumentationBytecodeNode root = InstrumentationBytecodeNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            b.beginRoot();
                // if (arg0) {
                //   log("true");
                //   return 42;
                // } else {
                //   log("false");
                //   return 123;
                // }
                b.beginIfThenElse();
                    b.emitLoadArgument(0);
                    b.beginBlock();
                        b.emitLog("true", pw);
                        b.beginReturn();
                            b.emitLoadConstant(42);
                        b.endReturn();
                    b.endBlock();
                    b.beginBlock();
                        b.emitLog("false", pw);
                        b.beginReturn();
                            b.emitLoadConstant(123);
                        b.endReturn();
                    b.endBlock();
                b.endIfThenElse();
            b.endRoot();
            // @formatter:on
        }).getNode(0);

        /*
         * The initial parse uses BytecodeConfig.DEFAULT, which does not enable any instrumentation.
         * Observe that nothing is logged when the root node is invoked.
         */
        assertEquals(42, root.getCallTarget().call(true));
        assertOutputAndClear(List.of(), out);
        assertEquals(123, root.getCallTarget().call(false));
        assertOutputAndClear(List.of(), out);
        /*- We can also see that Log is disabled by dumping the root node:
         * root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(6) =
         *            0 [000] 001 load.argument  index(0)
         *            1 [004] 008 branch.false   branch_target(0016) branch_profile(0:0.50)
         *            2 [00e] 002 load.constant  constant(Integer 42)
         *            3 [014] 00c return
         *            4 [016] 002 load.constant  constant(Integer 123)
         *            5 [01c] 00c return
         *      ...
         *  ]
         */

        /*
         * To enable Log, we can update the node using a BytecodeConfig that selects it. When a node
         * is updated with a broader configuration than the one it already has, it will be reparsed.
         * In this case, the reparse inserts the newly-enabled Log instructions.
         */
        root.getRootNodes().update(LOG_INSTRUMENTATION_CONFIG);
        assertEquals(42, root.getCallTarget().call(true));
        assertOutputAndClear(List.of("true"), out);
        assertEquals(123, root.getCallTarget().call(false));
        assertOutputAndClear(List.of("false"), out);
        /*- We can see that Log instructions were added:
         * root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(8) =
         *            0 [000] 001 load.argument  index(0)
         *            1 [004] 008 branch.false   branch_target(0020) branch_profile(0:0.50)
         *            2 [00e] 012 c.Log          message(String true) out(PrintWriter java.io.PrintWriter@12cdcf4)
         *            3 [018] 002 load.constant  constant(Integer 42)
         *            4 [01e] 00c return
         *            5 [020] 012 c.Log          message(String false) out(PrintWriter java.io.PrintWriter@12cdcf4)
         *            6 [02a] 002 load.constant  constant(Integer 123)
         *            7 [030] 00c return
         *      ...
         *  ]
         */
        ctx.close();
    }

    /**
     * Let's test the {@link InvertBoolean} instrumentation.
     */
    @Test
    public void testInvertBoolean() {
        Context ctx = setupContext();
        // This program is a simple if-then-else with an instrumentation to invert its condition.
        InstrumentationBytecodeNode root = InstrumentationBytecodeNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            b.beginRoot();
                // if (invertBoolean(arg0)) {
                //   return 42;
                // } else {
                //   return 123;
                // }
                b.beginIfThenElse();
                    b.beginInvertBoolean();
                        b.emitLoadArgument(0);
                    b.endInvertBoolean();
                    b.beginReturn();
                        b.emitLoadConstant(42);
                    b.endReturn();
                    b.beginReturn();
                        b.emitLoadConstant(123);
                    b.endReturn();
                b.endIfThenElse();
            b.endRoot();
            // @formatter:on
        }).getNode(0);

        // Observe that behaviour is as expected for the initial node.
        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(123, root.getCallTarget().call(false));
        /*- root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(6) =
         *            0 [000] 001 load.argument  index(0)
         *            1 [004] 008 branch.false   branch_target(0016) branch_profile(0:0.50)
         *            2 [00e] 002 load.constant  constant(Integer 42)
         *            3 [014] 00c return
         *            4 [016] 002 load.constant  constant(Integer 123)
         *            5 [01c] 00c return
         *      ...
         *  ]
         */

        // Once we enable the InvertBoolean instrumentation, the condition is inverted.
        root.getRootNodes().update(INVERT_BOOLEAN_INSTRUMENTATION_CONFIG);
        assertEquals(123, root.getCallTarget().call(true));
        assertEquals(42, root.getCallTarget().call(false));
        /*- root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(7) =
         *            0 [000] 001 load.argument    index(0)
         *            1 [004] 013 c.InvertBoolean  node(InvertBoolean_Node)
         *            2 [00a] 008 branch.false     branch_target(001c) branch_profile(0:0.50)
         *            3 [014] 002 load.constant    constant(Integer 42)
         *            4 [01a] 00c return
         *            5 [01c] 002 load.constant    constant(Integer 123)
         *            6 [022] 00c return
         *      ...
         *  ]
         */
        ctx.close();
    }

    /**
     * As seen in the examples above, enabling new instrumentations changes the bytecode for a root
     * node. Subsequent invocations of the root node use the instrumented bytecode.
     * <p>
     * Bytecode DSL interpreters also support <i>on-stack</i> instrumentation: if an operation
     * causes a new instrumentation to be added to its root node, the root node will carefully
     * determine the updated bytecode location to resume execution from.
     * <p>
     * Further reading: on-stack instrumentation is an important requirement for language features
     * like Python's {@code settrace}. See {@link SetTraceFuncRootNode} for more details about
     * implementing such a feature.
     */
    @Test
    public void testOnStackInstrumentation() {
        Context ctx = setupContext();
        StringWriter out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        // This program consists of two logs separated by an EnableInstrumentation operation.
        InstrumentationBytecodeNode root = InstrumentationBytecodeNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            b.beginRoot();
                // log("foo");
                // enableInstrumentation(log);
                // log("bar");
                b.emitLog("foo", pw);
                b.emitEnableLogInstrumentation();
                b.emitLog("bar", pw);
            b.endRoot();
            // @formatter:on
        }).getNode(0);

        /*- Before the first invocation, Log is disabled.
         * root.dump =>
         *  UninitializedBytecodeNode(name=null)[
         *      instructions(3) =
         *            0 [000] 014 c.EnableLogInstrumentation
         *            1 [002] 00b load.null
         *            2 [004] 00c return
         *      ...
         *  ]
         */

        /*
         * The first invocation enables Log, but after the first log statement. Only the second log
         * statement executes.
         */
        root.getCallTarget().call();
        assertOutputAndClear(List.of("bar"), out);
        /*- root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(5) =
         *            0 [000] 012 c.Log                       message(String foo) out(PrintWriter java.io.PrintWriter@4f1bfe23)
         *            1 [00a] 014 c.EnableLogInstrumentation
         *            2 [00c] 012 c.Log                       message(String bar) out(PrintWriter java.io.PrintWriter@4f1bfe23)
         *            3 [016] 00b load.null
         *            4 [018] 00c return
         *      ...
         *  ]
         */

        // On subsequent invocations, both log events will execute.
        root.getCallTarget().call();
        assertOutputAndClear(List.of("foo", "bar"), out);
        ctx.close();
    }

    /**
     * Tag instrumentation, the second form of instrumentation supported in the Bytecode DSL, allows
     * you to associate one or more Truffle {@link Tag tags} with a given operation by wrapping it
     * in a {@code Tag} operation.
     * <p>
     * Let's track the values that flow through the program using a simple Expression leave event
     * listener.
     */
    @Test
    public void testTags() {
        Context ctx = setupContext();
        Instrumenter instrumenter = setupInstrumenter(ctx);

        /*
         * This program is a simple if-then-else. Note how each expression is wrapped in a
         * Tag(Expression) operation.
         */
        InstrumentationBytecodeNode root = InstrumentationBytecodeNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            // if (arg0) {
            //   return 42;
            // } else {
            //   return 123;
            // }
            b.beginRoot();
                b.beginIfThenElse();
                    b.beginTag(ExpressionTag.class);
                        b.emitLoadArgument(0);
                    b.endTag(ExpressionTag.class);
                    b.beginReturn();
                        b.beginTag(ExpressionTag.class);
                            b.emitLoadConstant(42);
                        b.endTag(ExpressionTag.class);
                    b.endReturn();
                    b.beginReturn();
                        b.beginTag(ExpressionTag.class);
                            b.emitLoadConstant(123);
                        b.endTag(ExpressionTag.class);
                    b.endReturn();
                b.endIfThenElse();
            b.endRoot();
            // @formatter:on
        }).getNode(0);

        // The root node executes as usual. With the default parse, tags are not materialized.
        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(123, root.getCallTarget().call(false));
        /*- Tag instrumentation inserts special instructions for tag events. There are none yet.
         * root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(6) =
         *            0 [000] 001 load.argument  index(0)
         *            1 [004] 008 branch.false   branch_target(0016) branch_profile(0:0.50)
         *            2 [00e] 002 load.constant  constant(Integer 42)
         *            3 [014] 00c return
         *            4 [016] 002 load.constant  constant(Integer 123)
         *            5 [01c] 00c return
         *      ...
         *  ]
         */

        /*
         * Attach an instrument. Since expression tags have not been materialized, the root node
         * will reparse to materialize them and insert tag instructions.
         */
        Set<Object> expressions = new HashSet<>();
        EventBinding<?> binding = attachListener(instrumenter, expressions, ExpressionTag.class);
        /*- Extra instructions are inserted after reparsing with expression tags:
         * root.dump()
         *  CachedBytecodeNode(name=null)[
         *      instructions(12) =
         *            0 [000] 00f tag.enter      tag(0000 .. 000a EXPRESSION)
         *            1 [006] 001 load.argument  index(0)
         *            2 [00a] 010 tag.leave      tag(0000 .. 000a EXPRESSION)
         *            3 [010] 008 branch.false   branch_target(002e) branch_profile(0:0.50)
         *            4 [01a] 00f tag.enter      tag(001a .. 0026 EXPRESSION)
         *            5 [020] 002 load.constant  constant(Integer 42)
         *            6 [026] 010 tag.leave      tag(001a .. 0026 EXPRESSION)
         *            7 [02c] 00c return
         *            8 [02e] 00f tag.enter      tag(002e .. 003a EXPRESSION)
         *            9 [034] 002 load.constant  constant(Integer 123)
         *           10 [03a] 010 tag.leave      tag(002e .. 003a EXPRESSION)
         *           11 [040] 00c return
         *      ...
         *  ]
         */

        /*
         * Executing the instrumented root node will trigger the listener's onReturn callback, which
         * collects expressions seen.
         */
        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(Set.of(true, 42), expressions);
        expressions.clear();
        assertEquals(123, root.getCallTarget().call(false));
        assertEquals(Set.of(false, 123), expressions);

        /*
         * Just like with instrumentation instructions, once tags have been materialized they cannot
         * be removed. However, if an instrument is disabled (i.e., the binding is disposed of), the
         * tag instructions simplify to no-ops (and will fold away in compiled code).
         */
        binding.dispose();
        expressions.clear();
        assertEquals(42, root.getCallTarget().call(true));
        assertEquals(123, root.getCallTarget().call(false));
        assertTrue(expressions.isEmpty());

        /*-
         * For some use cases, it may be helpful to know the tags associated with a given bytecode
         * range. You can invoke getTagTree() on the BytecodeNode instance to obtain a tree with
         * this information.
         *
         * Alternatively, you can inspect the "tagTree" section of the dump:
         *      tagTree(4) =
         *      ==> [ffffffff .. 0000] ()         |
         *          [0000 .. 000a]   (EXPRESSION) |
         *          [001a .. 0026]   (EXPRESSION) |
         *          [002e .. 003a]   (EXPRESSION) |
         * For example, this dump says that the range 0000 to 000a spans a single expression. If we
         * refer to the bytecode dump above, we see that it represents the argument load expression.
         */

        ctx.close();
    }

    /**
     * In {@link #testTags} we demonstrated how to explicitly wrap operations in {@code Tag}
     * operations. Bytecode DSL interpreters also support two kinds of automatic tagging:
     * <p>
     * First: root and root body tagging. When the tags are provided, a Bytecode DSL interpreter
     * will insert {@link RootTag} and {@link RootBodyTag} tags automatically upon request. (These
     * defaults can be overridden using {@link GenerateBytecode#enableRootTagging()} and
     * {@link GenerateBytecode#enableRootBodyTagging()}.)
     * <p>
     * Second: implicit operation tagging. Operation definitions include a {@link Operation#tags}
     * field that can be used to always associate one or more tags with an operation. The operation
     * will be implicitly wrapped with those tags when they are requested. The
     * {@link InstrumentationBytecodeNode#Add} declares the {@link ExpressionTag} as an implicit
     * tag.
     * <p>
     * Let's demonstrate how all of these tags can be provided by the interpreter without explicitly
     * declaring {@code Tag} operations.
     */
    @Test
    public void testAutomaticTags() {
        Context ctx = setupContext();
        Instrumenter instrumenter = setupInstrumenter(ctx);

        InstrumentationBytecodeNode root = InstrumentationBytecodeNodeGen.create(getLanguage(), BytecodeConfig.DEFAULT, b -> {
            // @formatter:off
            // return 2 + 40;
            b.beginRoot();
                b.beginReturn();
                    b.beginAdd();
                        b.emitLoadConstant(2);
                        b.emitLoadConstant(40);
                    b.endAdd();
                b.endReturn();
            b.endRoot();
            // @formatter:on
        }).getNode(0);

        assertEquals(42, root.getCallTarget().call());
        /*- Again, to start, there are no tag instructions.
         * root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(4) =
         *            0 [000] 002 load.constant  constant(Integer 2)
         *            1 [006] 002 load.constant  constant(Integer 40)
         *            2 [00c] 015 c.Add          node(Add_Node)
         *            3 [012] 00c return
         *      ...
         *  ]
         */

        /*
         * If we attach an instrument that listens for RootTag, RootBodyTag, and ExpressionTag, we
         * should see new tag instructions for all of them in the expected places.
         */
        Set<Object> expressions = new HashSet<>();
        attachListener(instrumenter, expressions, RootTag.class, RootBodyTag.class, ExpressionTag.class);
        /*- Extra instructions are inserted after reparsing with expression tags:
         * root.dump() =>
         *  CachedBytecodeNode(name=null)[
         *      instructions(10) =
         *            0 [000] 00f tag.enter      tag(0000 .. 002c ROOT,ROOT_BODY)
         *            1 [006] 00f tag.enter      tag(0006 .. 001e EXPRESSION)
         *            2 [00c] 002 load.constant  constant(Integer 2)
         *            3 [012] 002 load.constant  constant(Integer 40)
         *            4 [018] 015 c.Add          node(Add_Node)
         *            5 [01e] 010 tag.leave      tag(0006 .. 001e EXPRESSION)
         *            6 [024] 010 tag.leave      tag(0000 .. 002c ROOT,ROOT_BODY)
         *            7 [02a] 00c return
         *            8 [02c] 010 tag.leave      tag(0000 .. 002c ROOT,ROOT_BODY)
         *            9 [032] 00c return
         *      ...
         *  ]
         */
        assertEquals(42, root.getCallTarget().call());
        assertEquals(Set.of(42), expressions);

        ctx.close();
    }

    /**
     * The code below is boilerplate to set up a simple instrument for testing purposes. Refer to
     * the {@link TruffleInstrument javadoc} for more information about implementing instruments.
     */
    @TruffleInstrument.Registration(id = InstrumentationTutorialInstrument.ID, services = Instrumenter.class)
    public static class InstrumentationTutorialInstrument extends TruffleInstrument {

        public static final String ID = "InstrumentationTutorialInstrument";

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getInstrumenter());
        }
    }

    private static Context setupContext() {
        Context context = Context.create(BytecodeDSLTestLanguage.ID);
        context.initialize(BytecodeDSLTestLanguage.ID);
        context.enter();
        return context;
    }

    private static Instrumenter setupInstrumenter(Context ctx) {
        return ctx.getEngine().getInstruments().get(InstrumentationTutorialInstrument.ID).lookup(Instrumenter.class);
    }

    private static EventBinding<?> attachListener(Instrumenter instrumenter, Set<Object> expressions, Class<?>... tags) {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(tags).build();
        return instrumenter.attachExecutionEventFactory(filter, (e) -> new ExecutionEventNode() {
            @Override
            public void onReturnValue(VirtualFrame frame, Object arg) {
                addValue(arg);
            }

            @TruffleBoundary
            private void addValue(Object arg) {
                expressions.add(arg);
            }
        });
    }

    private static BytecodeDSLTestLanguage getLanguage() {
        return BytecodeDSLTestLanguage.REF.get(null);
    }

    private static void assertOutputAndClear(List<String> expected, StringWriter sw) {
        assertEquals(String.join(System.lineSeparator(), expected), sw.toString().trim());
        sw.getBuffer().setLength(0); // clear
    }
}
