/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.AbstractBytecodeTruffleException;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.LocalSetter;
import com.oracle.truffle.api.bytecode.LocalSetterRange;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This class defines a set of interpreter variants with different configurations. Where possible,
 * prefer to use this class when testing new functionality, because
 * {@link AbstractBasicInterpreterTest} allows us to execute tests on each variant, increasing our
 * test coverage.
 */
@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true, allowUnsafe = false)),
                @Variant(suffix = "Unsafe", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true)),
                @Variant(suffix = "WithUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true, enableUncachedInterpreter = true)),
                @Variant(suffix = "WithBE", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true, boxingEliminationTypes = {
                                boolean.class, long.class}, decisionsFile = "basic_interpreter_quickening_only.json")),
                @Variant(suffix = "WithOptimizations", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true, //
                                decisionsFile = "basic_interpreter_decisions.json")),
                // A typical "production" configuration with all of the bells and whistles.
                @Variant(suffix = "Production", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, enableYield = true, enableSerialization = true, enableTagInstrumentation = true, enableUncachedInterpreter = true, //
                                boxingEliminationTypes = {long.class}, decisionsFile = "basic_interpreter_decisions.json"))
})
@ShortCircuitOperation(booleanConverter = BasicInterpreter.ToBoolean.class, name = "ScAnd", operator = Operator.AND_RETURN_VALUE)
@ShortCircuitOperation(booleanConverter = BasicInterpreter.ToBoolean.class, name = "ScOr", operator = Operator.OR_RETURN_VALUE)
public abstract class BasicInterpreter extends DebugBytecodeRootNode implements BytecodeRootNode {

    protected BasicInterpreter(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    protected String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // Expose the protected cloneUninitialized method for testing.
    public BasicInterpreter doCloneUninitialized() {
        return (BasicInterpreter) cloneUninitialized();
    }

    protected static class TestException extends AbstractBytecodeTruffleException {
        private static final long serialVersionUID = -9143719084054578413L;

        public final long value;

        TestException(String string, Node node, int bci, long value) {
            super(string, node, bci);
            this.value = value;
        }
    }

    @Override
    public Object interceptControlFlowException(ControlFlowException ex, VirtualFrame frame, BytecodeNode bytecodeNode, int bci) throws Throwable {
        if (ex instanceof EarlyReturnException ret) {
            return ret.result;
        }
        throw ex;
    }

    @SuppressWarnings({"serial"})
    protected static class EarlyReturnException extends ControlFlowException {
        private static final long serialVersionUID = 3637685681756424058L;

        public final Object result;

        EarlyReturnException(Object result) {
            this.result = result;
        }
    }

    @Operation
    static final class EarlyReturn {
        @Specialization
        public static void perform(Object result) {
            throw new EarlyReturnException(result);
        }
    }

    @Operation
    static final class AddOperation {
        @Specialization
        public static long addLongs(long lhs, long rhs) {
            return lhs + rhs;
        }

        @Specialization
        @TruffleBoundary
        public static String addStrings(String lhs, String rhs) {
            return lhs + rhs;
        }
    }

    @Operation
    static final class LessThanOperation {
        @Specialization
        public static boolean lessThan(long lhs, long rhs) {
            return lhs < rhs;
        }
    }

    @Operation
    static final class VeryComplexOperation {
        @Specialization
        public static long bla(long a1, @Variadic Object[] a2) {
            return a1 + a2.length;
        }
    }

    @Operation
    static final class ThrowOperation {
        @Specialization
        public static Object perform(long value,
                        // TODO passing the actual bci breaks compiler tests because of how we
                        // instantiate a location node from the bci
                        @SuppressWarnings("unused") @Bind("$location") BytecodeLocation bci,
                        @Bind("$root") Node node) {
            throw new TestException("fail", node, -1, value);
        }
    }

    @Operation
    static final class ReadExceptionOperation {
        @Specialization
        public static long perform(TestException ex) {
            return ex.value;
        }
    }

    @Operation
    static final class AlwaysBoxOperation {
        @Specialization
        public static Object perform(Object value) {
            return value;
        }
    }

    @Operation
    static final class AppenderOperation {
        @SuppressWarnings("unchecked")
        @Specialization
        @TruffleBoundary
        public static void perform(List<?> list, Object value) {
            ((List<Object>) list).add(value);
        }
    }

    @Operation
    static final class TeeLocal {
        @Specialization
        public static long doInt(VirtualFrame frame, long value, LocalSetter setter) {
            setter.setLong(frame, value);
            return value;
        }

        @Specialization
        public static Object doGeneric(VirtualFrame frame, Object value, LocalSetter setter) {
            setter.setObject(frame, value);
            return value;
        }
    }

    @Operation
    static final class TeeLocalRange {
        @Specialization
        @ExplodeLoop
        public static Object doLong(VirtualFrame frame, long[] value, LocalSetterRange setter) {
            for (int i = 0; i < value.length; i++) {
                setter.setLong(frame, i, value[i]);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        public static Object doGeneric(VirtualFrame frame, Object[] value, LocalSetterRange setter) {
            for (int i = 0; i < value.length; i++) {
                setter.setObject(frame, i, value[i]);
            }
            return value;
        }
    }

    @SuppressWarnings("unused")
    @Operation
    public static final class Invoke {
        @Specialization(guards = {"callTargetMatches(root.getCallTarget(), callNode.getCallTarget())"}, limit = "1")
        public static Object doRootNode(BasicInterpreter root, @Variadic Object[] args, @Cached("create(root.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doRootNode"})
        public static Object doRootNodeUncached(BasicInterpreter root, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            return callNode.call(root.getCallTarget(), args);
        }

        @Specialization(guards = {"callTargetMatches(root.getCallTarget(), callNode.getCallTarget())"}, limit = "1")
        public static Object doClosure(TestClosure root, @Variadic Object[] args, @Cached("create(root.getCallTarget())") DirectCallNode callNode) {
            assert args.length == 0 : "not implemented";
            return callNode.call(root.getFrame());
        }

        @Specialization(replaces = {"doClosure"})
        public static Object doClosureUncached(TestClosure root, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            assert args.length == 0 : "not implemented";
            return callNode.call(root.getCallTarget(), root.getFrame());
        }

        @Specialization(guards = {"callTargetMatches(callTarget, callNode.getCallTarget())"}, limit = "1")
        public static Object doCallTarget(CallTarget callTarget, @Variadic Object[] args, @Cached("create(callTarget)") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doCallTarget"})
        public static Object doCallTargetUncached(CallTarget callTarget, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            return callNode.call(callTarget, args);
        }

        protected static boolean callTargetMatches(CallTarget left, CallTarget right) {
            return left == right;
        }
    }

    @Operation
    public static final class CreateClosure {
        @Specialization
        public static TestClosure materialize(VirtualFrame frame, BasicInterpreter root) {
            return new TestClosure(frame.materialize(), root);
        }
    }

    @Operation
    public static final class VoidOperation {
        @Specialization
        public static void doNothing() {
        }
    }

    @Operation
    public static final class ToBoolean {
        @Specialization
        public static boolean doLong(long l) {
            return l != 0;
        }

        @Specialization
        public static boolean doBoolean(boolean b) {
            return b;
        }

        @Specialization
        public static boolean doString(String s) {
            return s != null;
        }
    }

    @Operation
    public static final class NonNull {
        @Specialization
        public static boolean doObject(Object o) {
            return o != null;
        }
    }

    @Operation
    public static final class GetSourcePosition {
        @Specialization
        public static Object doOperation(VirtualFrame frame,
                        @Bind("$node") Node node,
                        @Bind("$bytecode") BytecodeNode bytecode) {
            return bytecode.getSourceLocation(frame, node);
        }
    }

    @Operation
    public static final class CopyLocalsToFrame {
        @Specialization
        public static Frame doSomeLocals(VirtualFrame frame, long length, @Bind("$root") BasicInterpreter rootNode) {
            Frame newFrame = Truffle.getRuntime().createMaterializedFrame(frame.getArguments(), frame.getFrameDescriptor());
            rootNode.copyLocals(frame, newFrame, (int) length);
            return newFrame;
        }

        @Specialization(guards = {"length == null"})
        public static Frame doAllLocals(VirtualFrame frame, @SuppressWarnings("unused") Object length, @Bind("$root") BasicInterpreter rootNode) {
            Frame newFrame = Truffle.getRuntime().createMaterializedFrame(frame.getArguments(), frame.getFrameDescriptor());
            rootNode.copyLocals(frame, newFrame);
            return newFrame;
        }
    }

    @Operation
    public static final class CollectBytecodeLocations {
        @Specialization
        public static List<BytecodeLocation> perform() {
            List<BytecodeLocation> bytecodeIndices = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(f -> {
                bytecodeIndices.add(BytecodeLocation.get(f));
                return null;
            });
            return bytecodeIndices;
        }
    }

    @Operation
    public static final class AppendInstructionIndex {
        @SuppressWarnings("unchecked")
        @Specialization
        @TruffleBoundary
        public static void doAppend(List<?> indices, @Bind("$location") BytecodeLocation location) {
            ((List<Integer>) indices).add(location.findInstructionIndex());
        }
    }

    @Operation
    public static final class ContinueNode {
        public static final int LIMIT = 3;

        @SuppressWarnings("unused")
        @Specialization(guards = {"result.getContinuationRootNode() == rootNode"}, limit = "LIMIT")
        public static Object invokeDirect(ContinuationResult result, Object value,
                        @Cached("result.getContinuationRootNode()") RootNode rootNode,
                        @Cached("create(rootNode.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(result.getFrame(), value);
        }

        @Specialization(replaces = "invokeDirect")
        public static Object invokeIndirect(ContinuationResult result, Object value,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(result.getContinuationCallTarget(), result.getFrame(), value);
        }
    }

}

class TestClosure {
    private final MaterializedFrame frame;
    private final RootCallTarget root;

    TestClosure(MaterializedFrame frame, BasicInterpreter root) {
        this.frame = frame;
        this.root = root.getCallTarget();
    }

    public RootCallTarget getCallTarget() {
        return root;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public Object call() {
        return root.call(frame);
    }
}
