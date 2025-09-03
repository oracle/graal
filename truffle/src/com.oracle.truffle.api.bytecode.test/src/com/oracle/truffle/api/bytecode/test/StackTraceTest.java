/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeParser;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

@RunWith(Parameterized.class)
public class StackTraceTest extends AbstractInstructionTest {

    protected static final BytecodeDSLTestLanguage LANGUAGE = null;

    enum Interpreter {

        CACHED_DEFAULT(StackTraceTestRootNodeCachedDefault.class, true),
        UNCACHED_DEFAULT(StackTraceTestRootNodeUncachedDefault.class, false),
        CACHED_BCI_IN_FRAME(StackTraceTestRootNodeCachedBciInFrame.class, true),
        UNCACHED_BCI_IN_FRAME(StackTraceTestRootNodeUncachedBciInFrame.class, false);

        final Class<? extends StackTraceTestRootNode> clazz;
        final boolean cached;

        Interpreter(Class<? extends StackTraceTestRootNode> clazz, boolean cached) {
            this.clazz = clazz;
            this.cached = cached;
        }
    }

    static final int[] DEPTHS = new int[]{1, 2, 3, 4, 8, 13, 16, 50};
    static final int REPEATS = 4;

    record Run(Interpreter interpreter, int depth) {
        @Override
        public String toString() {
            return interpreter.clazz.getSimpleName() + "(depth=" + depth + ")";
        }
    }

    @Parameters(name = "{0}")
    public static List<Run> createRuns() {
        List<Run> runs = new ArrayList<>(Interpreter.values().length * DEPTHS.length);
        for (Interpreter interpreter : Interpreter.values()) {
            for (int depth : DEPTHS) {
                runs.add(new Run(interpreter, depth));
            }
        }
        return runs;
    }

    @Parameter(0) public Run run;

    Context context;

    @Before
    public void setup() {
        context = Context.create(BytecodeDSLTestLanguage.ID);
        context.initialize(BytecodeDSLTestLanguage.ID);
        context.enter();
    }

    @After
    public void tearDown() {
        context.close();
    }

    @Test
    public void testThrow() {
        int depth = run.depth;
        StackTraceTestRootNode[] nodes = chainCalls(depth, b -> {
            b.beginRoot();
            b.emitDummy();
            b.emitThrowError();
            b.endRoot();
        }, true, false);
        StackTraceTestRootNode outer = nodes[nodes.length - 1];

        for (int repeat = 0; repeat < REPEATS; repeat++) {
            try {
                outer.getCallTarget().call();
                Assert.fail();
            } catch (TestException e) {
                List<TruffleStackTraceElement> elements = TruffleStackTrace.getStackTrace(e);
                assertEquals(nodes.length, elements.size());
                for (int i = 0; i < nodes.length; i++) {
                    assertStackElement(elements.get(i), nodes[i], false);
                }
            }
        }
    }

    @Test
    public void testThrowBehindInterop() {
        int depth = run.depth;
        StackTraceTestRootNode[] nodes = chainCalls(depth, b -> {
            b.beginRoot();
            b.beginThrowErrorBehindInterop();
            b.emitLoadConstant(new ThrowErrorExecutable());
            b.endThrowErrorBehindInterop();
            b.endRoot();
        }, true, false);
        StackTraceTestRootNode outer = nodes[nodes.length - 1];

        for (int repeat = 0; repeat < REPEATS; repeat++) {
            try {
                outer.getCallTarget().call();
                Assert.fail();
            } catch (TestException e) {
                List<TruffleStackTraceElement> elements = TruffleStackTrace.getStackTrace(e);
                assertEquals(nodes.length, elements.size());
                for (int i = 0; i < nodes.length; i++) {
                    assertStackElement(elements.get(i), nodes[i], false);
                }
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCapture() {
        int depth = run.depth;
        StackTraceTestRootNode[] nodes = chainCalls(depth, b -> {
            b.beginRoot();
            b.emitDummy();
            b.beginReturn();
            b.emitCaptureStack();
            b.endReturn();
            b.endRoot();
        }, true, false);
        StackTraceTestRootNode outer = nodes[nodes.length - 1];

        for (int repeat = 0; repeat < REPEATS; repeat++) {
            List<TruffleStackTraceElement> elements = (List<TruffleStackTraceElement>) outer.getCallTarget().call();
            assertEquals(nodes.length, elements.size());
            for (int i = 0; i < nodes.length; i++) {
                assertStackElement(elements.get(i), nodes[i], false);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCaptureWithSources() {
        int depth = run.depth;
        Source s = Source.newBuilder(BytecodeDSLTestLanguage.ID, "root0", "root0.txt").build();
        StackTraceTestRootNode[] nodes = chainCalls(depth, b -> {
            b.beginSource(s);
            b.beginSourceSection(0, "root0".length());
            b.beginRoot();
            b.emitDummy();
            b.beginReturn();
            b.emitCaptureStack();
            b.endReturn();
            b.endRoot();
            b.endSourceSection();
            b.endSource();
        }, true, true);
        StackTraceTestRootNode outer = nodes[nodes.length - 1];

        for (int repeat = 0; repeat < REPEATS; repeat++) {
            List<TruffleStackTraceElement> elements = (List<TruffleStackTraceElement>) outer.getCallTarget().call();
            assertEquals(nodes.length, elements.size());
            for (int i = 0; i < nodes.length; i++) {
                assertStackElement(elements.get(i), nodes[i], true);
            }
        }
    }

    private void assertStackElement(TruffleStackTraceElement element, StackTraceTestRootNode target, boolean checkSources) {
        assertSame(target.getCallTarget(), element.getTarget());
        assertNotNull(element.getLocation());
        BytecodeNode bytecode = target.getBytecodeNode();
        if (run.interpreter.cached) {
            assertSame(bytecode, BytecodeNode.get(element.getLocation()));
            assertSame(bytecode, BytecodeNode.get(element));
        } else {
            assertSame(bytecode, element.getLocation());
        }
        assertEquals(bytecode.getInstructionsAsList().get(1).getBytecodeIndex(), element.getBytecodeIndex());

        Object interopObject = element.getGuestObject();
        InteropLibrary lib = InteropLibrary.getFactory().create(interopObject);
        try {
            assertTrue(lib.hasExecutableName(interopObject));
            assertEquals(target.getName(), lib.getExecutableName(interopObject));
            assertFalse(lib.hasDeclaringMetaObject(interopObject));
            if (checkSources) {
                assertTrue(lib.hasSourceLocation(interopObject));
                assertEquals(target.getName(), lib.getSourceLocation(interopObject).getCharacters());
            }
        } catch (UnsupportedMessageException ex) {
            fail("Interop object could not receive message: " + ex);
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNoLocation() {
        int depth = run.depth;
        StackTraceTestRootNode[] nodes = chainCalls(depth, b -> {
            b.beginRoot();
            b.emitDummy();
            b.emitThrowErrorNoLocation();
            b.endRoot();
        }, false, false);
        StackTraceTestRootNode outer = nodes[nodes.length - 1];
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            try {
                outer.getCallTarget().call();
                Assert.fail();
            } catch (TestException e) {
                List<TruffleStackTraceElement> elements = TruffleStackTrace.getStackTrace(e);
                assertEquals(nodes.length, elements.size());
                for (int i = 0; i < nodes.length; i++) {
                    assertStackElementNoLocation(elements.get(i), nodes[i]);
                }
            }
        }
    }

    private static void assertStackElementNoLocation(TruffleStackTraceElement element, StackTraceTestRootNode target) {
        assertSame(target.getCallTarget(), element.getTarget());
        assertNull(element.getLocation());
        assertEquals(-1, element.getBytecodeIndex());
        assertNull(BytecodeNode.get(element));
    }

    private StackTraceTestRootNode[] chainCalls(int depth, BytecodeParser<StackTraceTestRootNodeBuilder> innerParser, boolean includeLocation, boolean includeSources) {
        StackTraceTestRootNode[] nodes = new StackTraceTestRootNode[depth];
        nodes[0] = parse(innerParser);
        nodes[0].setName("root0");
        for (int i = 1; i < depth; i++) {
            int index = i;
            String name = "root" + i;
            Source s = includeSources ? Source.newBuilder(BytecodeDSLTestLanguage.ID, name, name + ".txt").build() : null;
            nodes[i] = parse(b -> {
                if (includeSources) {
                    b.beginSource(s);
                    b.beginSourceSection(0, name.length());
                }
                b.beginRoot();
                b.emitDummy();
                b.beginReturn();
                CallTarget target = nodes[index - 1].getCallTarget();
                if (includeLocation) {
                    b.emitCall(target);
                } else {
                    b.emitCallNoLocation(target);
                }
                b.endReturn();
                b.endRoot().depth = index;
                if (includeSources) {
                    b.endSourceSection();
                    b.endSource();
                }
            });
            nodes[i].setName(name);
        }
        return nodes;
    }

    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "CachedDefault", configuration = //
                    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, storeBytecodeIndexInFrame = false, enableUncachedInterpreter = false, boxingEliminationTypes = {int.class})),
                    @Variant(suffix = "UncachedDefault", configuration = //
                    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, storeBytecodeIndexInFrame = false, enableUncachedInterpreter = true, boxingEliminationTypes = {int.class})),
                    @Variant(suffix = "CachedBciInFrame", configuration = //
                    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, storeBytecodeIndexInFrame = true, enableUncachedInterpreter = false, boxingEliminationTypes = {int.class})),
                    @Variant(suffix = "UncachedBciInFrame", configuration = //
                    @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, storeBytecodeIndexInFrame = true, enableUncachedInterpreter = true, boxingEliminationTypes = {int.class}))
    })
    public abstract static class StackTraceTestRootNode extends DebugBytecodeRootNode implements BytecodeRootNode {

        protected StackTraceTestRootNode(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        public String name;
        public int depth;

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "StackTest[name=" + name + ", depth=" + depth + "]";
        }

        // just used to increment the instruction index
        @Operation
        static final class Dummy {
            @Specialization
            static void doDefault() {
            }
        }

        @Operation
        @ConstantOperand(type = CallTarget.class)
        static final class Call {
            @Specialization
            static Object doDefault(CallTarget target, @Bind Node node) {
                return target.call(node);
            }
        }

        @Operation
        @ConstantOperand(type = CallTarget.class)
        static final class CallNoLocation {
            @Specialization
            static Object doDefault(CallTarget target) {
                return target.call((Node) null);
            }
        }

        @Operation
        static final class ThrowErrorBehindInterop {

            @Specialization(limit = "1")
            static Object doDefault(Object executable, @CachedLibrary("executable") InteropLibrary executables) {
                try {
                    return executables.execute(executable);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
        }

        @Operation
        static final class ThrowError {

            @Specialization
            static Object doDefault(@Bind Node node) {
                throw new TestException(node);
            }
        }

        @Operation
        static final class ThrowErrorNoLocation {

            @Specialization
            static Object doDefault() {
                throw new TestException(null);
            }
        }

        @Operation
        static final class CaptureStack {

            @Specialization
            static Object doDefault(@Bind Node node) {
                TestException ex = new TestException(node);
                return TruffleStackTrace.getStackTrace(ex);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class ThrowErrorExecutable implements TruffleObject {

        @ExportMessage
        @SuppressWarnings("unused")
        final Object execute(Object[] args, @CachedLibrary("this") InteropLibrary library) {
            throw new TestException(library);
        }

        @ExportMessage
        final boolean isExecutable() {
            return true;
        }

    }

    @SuppressWarnings("serial")
    static class TestException extends AbstractTruffleException {

        TestException(Node location) {
            super(resolveLocation(location));
        }

        private static Node resolveLocation(Node location) {
            if (location == null) {
                return null;
            }
            if (location.isAdoptable()) {
                return location;
            } else {
                return EncapsulatingNodeReference.getCurrent().get();
            }
        }
    }

    private StackTraceTestRootNode parse(BytecodeParser<StackTraceTestRootNodeBuilder> parser) {
        BytecodeRootNodes<StackTraceTestRootNode> nodes = StackTraceTestRootNodeBuilder.invokeCreate((Class<? extends StackTraceTestRootNode>) run.interpreter.clazz,
                        LANGUAGE, BytecodeConfig.WITH_SOURCE, (BytecodeParser<? extends StackTraceTestRootNodeBuilder>) parser);
        StackTraceTestRootNode root = nodes.getNodes().get(nodes.getNodes().size() - 1);
        return root;
    }

}
