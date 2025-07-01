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
package com.oracle.truffle.api.bytecode.test.basic_interpreter;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.bytecode.BytecodeConfig;
import com.oracle.truffle.api.bytecode.BytecodeLocation;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.ContinuationResult;
import com.oracle.truffle.api.bytecode.ContinuationRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.LocalAccessor;
import com.oracle.truffle.api.bytecode.LocalRangeAccessor;
import com.oracle.truffle.api.bytecode.MaterializedLocalAccessor;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.test.BytecodeDSLTestLanguage;
import com.oracle.truffle.api.bytecode.test.DebugBytecodeRootNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * This class defines a set of interpreter variants with different configurations. Where possible,
 * prefer to use this class when testing new functionality, because
 * {@link AbstractBasicInterpreterTest} allows us to execute tests on each variant, increasing our
 * test coverage.
 */
@GenerateBytecodeTestVariants({
                @Variant(suffix = "Base", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableTagInstrumentation = true, //
                                enableSpecializationIntrospection = true, //
                                allowUnsafe = false, //
                                variadicStackLimit = "4")),
                @Variant(suffix = "Unsafe", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableTagInstrumentation = true, //
                                enableSpecializationIntrospection = true, //
                                variadicStackLimit = "8")),
                @Variant(suffix = "WithUncached", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableTagInstrumentation = true, //
                                enableUncachedInterpreter = true, //
                                defaultUncachedThreshold = "defaultUncachedThreshold", //
                                enableSpecializationIntrospection = true, //
                                variadicStackLimit = "16")),
                @Variant(suffix = "WithBE", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableTagInstrumentation = true, //
                                enableSpecializationIntrospection = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                variadicStackLimit = "4")),
                @Variant(suffix = "WithOptimizations", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableSpecializationIntrospection = true, //
                                enableTagInstrumentation = true, //
                                defaultLocalValue = "LOCAL_DEFAULT_VALUE", //
                                variadicStackLimit = "8")),
                @Variant(suffix = "WithRootScoping", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableBlockScoping = false, //
                                enableTagInstrumentation = true, //
                                enableSpecializationIntrospection = true, //
                                defaultLocalValue = "LOCAL_DEFAULT_VALUE", //
                                variadicStackLimit = "16")),
                @Variant(suffix = "WithStoreBytecodeIndexInFrame", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableUncachedInterpreter = true, //
                                defaultUncachedThreshold = "defaultUncachedThreshold", //
                                enableSpecializationIntrospection = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                storeBytecodeIndexInFrame = true, //
                                enableTagInstrumentation = true, //
                                variadicStackLimit = "4")),
                // A typical "production" configuration with all of the bells and whistles.
                @Variant(suffix = "ProductionBlockScoping", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableTagInstrumentation = true, //
                                enableUncachedInterpreter = true, //
                                defaultUncachedThreshold = "defaultUncachedThreshold", //
                                enableSpecializationIntrospection = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                variadicStackLimit = "8")),
                @Variant(suffix = "ProductionRootScoping", configuration = @GenerateBytecode(languageClass = BytecodeDSLTestLanguage.class, //
                                additionalAssertions = true, //
                                enableYield = true, //
                                enableMaterializedLocalAccesses = true, //
                                enableSerialization = true, //
                                enableBlockScoping = false, //
                                enableTagInstrumentation = true, //
                                enableUncachedInterpreter = true, //
                                defaultUncachedThreshold = "defaultUncachedThreshold", //
                                enableSpecializationIntrospection = true, //
                                boxingEliminationTypes = {boolean.class, long.class}, //
                                variadicStackLimit = "16"))
})
@ShortCircuitOperation(booleanConverter = BasicInterpreter.ToBoolean.class, name = "ScAnd", operator = Operator.AND_RETURN_VALUE)
@ShortCircuitOperation(booleanConverter = BasicInterpreter.ToBoolean.class, name = "ScOr", operator = Operator.OR_RETURN_VALUE, javadoc = "ScOr returns the first truthy operand value.")
public abstract class BasicInterpreter extends DebugBytecodeRootNode implements BytecodeRootNode {

    static int defaultUncachedThreshold = 16;

    static final Object LOCAL_DEFAULT_VALUE = new LocalDefaultValue();

    static final class LocalDefaultValue {
    }

    protected BasicInterpreter(BytecodeDSLTestLanguage language, FrameDescriptor frameDescriptor) {
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
    @TruffleBoundary
    public String toString() {
        return String.format("%s(%s)", this.getClass().getSimpleName(), getName());
    }

    // Expose the protected cloneUninitialized method for testing.
    public BasicInterpreter doCloneUninitialized() {
        return (BasicInterpreter) cloneUninitialized();
    }

    protected static class TestException extends AbstractTruffleException {
        private static final long serialVersionUID = -9143719084054578413L;

        public final long value;

        TestException(String string, Node node, long value) {
            super(string, node);
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
    public static class EarlyReturnException extends ControlFlowException {
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

    @Operation(javadoc = "Adds the two operand values, which must either be longs or Strings.")
    static final class Add {
        @Specialization
        public static long addLongs(long lhs, long rhs) {
            return lhs + rhs;
        }

        @Specialization
        @TruffleBoundary
        public static String addStrings(String lhs, String rhs) {
            return lhs + rhs;
        }

        @Fallback
        @TruffleBoundary
        public static String addObjects(Object lhs, Object rhs) {
            return lhs.toString() + rhs.toString();
        }
    }

    @Operation(javadoc = "Exercises interop on the operand.")
    static final class ToString {
        @Specialization(limit = "2")
        public static Object doForeignObject(Object value,
                        @CachedLibrary("value") InteropLibrary interop) {
            try {
                return interop.asString(value);
            } catch (UnsupportedMessageException e) {
                return null;
            }
        }

    }

    @Operation
    @ConstantOperand(type = BasicInterpreter.class)
    static final class Call {

        @Specialization
        static Object call(BasicInterpreter interpreter,
                        @Variadic Object[] arguments,
                        @Bind Node location) {
            return interpreter.getCallTarget().call(location, arguments);
        }

    }

    @Operation
    @ConstantOperand(type = long.class)
    static final class AddConstantOperation {
        @Specialization
        public static long addLongs(long constantLhs, long rhs) {
            return constantLhs + rhs;
        }

        @Specialization
        public static String addStrings(long constantLhs, String rhs) {
            return constantLhs + rhs;
        }
    }

    @Operation
    @ConstantOperand(type = long.class, specifyAtEnd = true)
    static final class AddConstantOperationAtEnd {
        @Specialization
        public static long addLongs(long lhs, long constantRhs) {
            return lhs + constantRhs;
        }

        @Specialization
        public static String addStrings(String lhs, long constantRhs) {
            return lhs + constantRhs;
        }
    }

    @Operation
    static final class VariadicOperation {
        @Specialization
        public static long doLong(long a1, @Variadic Object[] a2) {
            return a1 + a2.length;
        }

        @Fallback
        public static long doOther(@SuppressWarnings("unused") Object a1, @Variadic Object[] a2) {
            return a2.length;
        }
    }

    @Operation
    static final class ThrowOperation {
        @Specialization
        public static Object perform(long value,
                        @Bind Node node) {
            throw new TestException("fail", node, value);
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
    @ConstantOperand(type = LocalAccessor.class)
    static final class TeeLocal {
        @Specialization
        public static long doLong(VirtualFrame frame,
                        LocalAccessor setter,
                        long value,
                        @Bind BytecodeNode bytecode) {
            setter.setLong(bytecode, frame, value);
            return value;
        }

        @Specialization(replaces = "doLong")
        public static Object doGeneric(VirtualFrame frame,
                        LocalAccessor setter,
                        Object value,
                        @Bind BytecodeNode bytecode) {
            if (value instanceof Long l) {
                setter.setLong(bytecode, frame, l);
            } else if (value instanceof Integer i) {
                setter.setInt(bytecode, frame, i);
            } else if (value instanceof Byte b) {
                setter.setByte(bytecode, frame, b);
            } else if (value instanceof Boolean b) {
                setter.setBoolean(bytecode, frame, b);
            } else if (value instanceof Float f) {
                setter.setFloat(bytecode, frame, f);
            } else if (value instanceof Double d) {
                setter.setDouble(bytecode, frame, d);
            } else {
                setter.setObject(bytecode, frame, value);
            }
            return value;
        }
    }

    @Operation
    @ConstantOperand(type = LocalRangeAccessor.class)
    static final class TeeLocalRange {
        @Specialization
        @ExplodeLoop
        public static Object doLong(VirtualFrame frame,
                        LocalRangeAccessor setter,
                        long[] value,
                        @Bind BytecodeNode bytecode) {
            if (value.length != setter.getLength()) {
                throw new IllegalArgumentException("TeeLocalRange length mismatch");
            }
            for (int i = 0; i < setter.getLength(); i++) {
                setter.setLong(bytecode, frame, i, value[i]);
            }
            return value;
        }

        @Specialization
        @ExplodeLoop
        public static Object doGeneric(VirtualFrame frame,
                        LocalRangeAccessor setter,
                        Object[] value,
                        @Bind BytecodeNode bytecode) {
            if (value.length != setter.getLength()) {
                throw new IllegalArgumentException("TeeLocalRange length mismatch");
            }
            for (int i = 0; i < setter.getLength(); i++) {
                if (value[i] instanceof Long l) {
                    setter.setLong(bytecode, frame, i, l);
                } else if (value[i] instanceof Integer n) {
                    setter.setInt(bytecode, frame, i, n);
                } else if (value[i] instanceof Byte b) {
                    setter.setByte(bytecode, frame, i, b);
                } else if (value[i] instanceof Boolean b) {
                    setter.setBoolean(bytecode, frame, i, b);
                } else if (value[i] instanceof Float f) {
                    setter.setFloat(bytecode, frame, i, f);
                } else if (value[i] instanceof Double d) {
                    setter.setDouble(bytecode, frame, i, d);
                } else {
                    setter.setObject(bytecode, frame, i, value[i]);
                }
            }
            return value;
        }
    }

    @Operation
    @ConstantOperand(type = MaterializedLocalAccessor.class)
    static final class TeeMaterializedLocal {
        @Specialization
        public static long doLong(MaterializedLocalAccessor accessor,
                        MaterializedFrame materializedFrame,
                        long value,
                        @Bind BytecodeNode bytecode) {
            accessor.setLong(bytecode, materializedFrame, value);
            return value;
        }

        @Specialization(replaces = "doLong")
        public static Object doGeneric(MaterializedLocalAccessor accessor,
                        MaterializedFrame materializedFrame,
                        Object value,
                        @Bind BytecodeNode bytecode) {
            if (value instanceof Long l) {
                accessor.setLong(bytecode, materializedFrame, l);
            } else if (value instanceof Integer i) {
                accessor.setInt(bytecode, materializedFrame, i);
            } else if (value instanceof Byte b) {
                accessor.setByte(bytecode, materializedFrame, b);
            } else if (value instanceof Boolean b) {
                accessor.setBoolean(bytecode, materializedFrame, b);
            } else if (value instanceof Float f) {
                accessor.setFloat(bytecode, materializedFrame, f);
            } else if (value instanceof Double d) {
                accessor.setDouble(bytecode, materializedFrame, d);
            } else {
                accessor.setObject(bytecode, materializedFrame, value);
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
        public static Object doClosure(TestClosure root, @Variadic Object[] args, @Cached("args.length") int length, @Cached("create(root.getCallTarget())") DirectCallNode callNode) {
            CompilerAsserts.partialEvaluationConstant(length);
            if (length == 0) {
                return callNode.call(root.getFrame());
            } else {
                Object[] allArgs = new Object[length + 1];
                allArgs[0] = root.getFrame();
                System.arraycopy(args, 0, allArgs, 1, length);
                return callNode.call(allArgs);
            }
        }

        @Specialization(replaces = {"doClosure"})
        public static Object doClosureUncached(TestClosure root, @Variadic Object[] args, @Shared @Cached IndirectCallNode callNode) {
            Object[] allArgs = new Object[args.length + 1];
            allArgs[0] = root.getFrame();
            System.arraycopy(args, 0, allArgs, 1, args.length);
            return callNode.call(root.getCallTarget(), allArgs);
        }

        protected static boolean callTargetMatches(CallTarget left, CallTarget right) {
            return left == right;
        }
    }

    @Operation
    public static final class InvokeRecursive {
        @Specialization(guards = "true", excludeForUncached = true)
        public static Object doRootNode(@Variadic Object[] args, @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(args);
        }

        @Specialization(replaces = {"doRootNode"})
        public static Object doRootNodeUncached(@Variadic Object[] args, @Bind BasicInterpreter root, @Shared @Cached IndirectCallNode callNode) {
            return callNode.call(root.getCallTarget(), args);
        }
    }

    @Operation
    public static final class MaterializeFrame {
        @Specialization
        public static MaterializedFrame materialize(VirtualFrame frame) {
            return frame.materialize();
        }
    }

    @Operation
    public static final class CreateClosure {
        @Specialization
        public static TestClosure materialize(VirtualFrame frame, BasicInterpreter root) {
            return new TestClosure(frame.materialize(), root);
        }
    }

    @Operation(javadoc = "Does nothing.")
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
    public static final class GetSourcePosition {
        @Specialization
        public static SourceSection doOperation(VirtualFrame frame,
                        @Bind Node node,
                        @Bind BytecodeNode bytecode) {
            return bytecode.getSourceLocation(frame, node);
        }
    }

    @Operation
    public static final class EnsureAndGetSourcePosition {
        @Specialization
        public static SourceSection doOperation(VirtualFrame frame, boolean ensure,
                        @Bind Node node,
                        @Bind BytecodeNode bytecode) {
            // Put this branch in the operation itself so that the bytecode branch profile doesn't
            // mark this path unreached during compilation.
            if (ensure) {
                return bytecode.ensureSourceInformation().getSourceLocation(frame, node);
            } else {
                return bytecode.getSourceLocation(frame, node);
            }
        }
    }

    @Operation
    public static final class GetSourcePositions {
        @Specialization
        public static SourceSection[] doOperation(VirtualFrame frame,
                        @Bind Node node,
                        @Bind BytecodeNode bytecode) {
            return bytecode.getSourceLocations(frame, node);
        }
    }

    @Operation
    @ConstantOperand(type = long.class) // (actually int, but serialization works with longs)
    public static final class CopyLocalsToFrame {
        @Specialization(guards = {"length != 0"})
        public static Frame doSomeLocals(VirtualFrame frame, long length,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind("$bytecodeIndex") int bci) {
            Frame newFrame = Truffle.getRuntime().createMaterializedFrame(frame.getArguments(), frame.getFrameDescriptor());
            bytecodeNode.copyLocalValues(bci, frame, newFrame, 0, (int) length);
            return newFrame;
        }

        @Fallback
        public static Frame doAllLocals(VirtualFrame frame, @SuppressWarnings("unused") long length,
                        @Bind BytecodeNode bytecodeNode,
                        @Bind("$bytecodeIndex") int bci) {
            Frame newFrame = Truffle.getRuntime().createMaterializedFrame(frame.getArguments(), frame.getFrameDescriptor());
            bytecodeNode.copyLocalValues(bci, frame, newFrame);
            return newFrame;
        }
    }

    @Operation
    public static final class GetBytecodeLocation {
        // Note: this is just to test the API. You can bind the BytecodeLocation directly.
        @Specialization
        public static BytecodeLocation perform(
                        VirtualFrame frame,
                        @Bind Node node,
                        @Bind BytecodeNode bytecode) {
            return bytecode.getBytecodeLocation(frame, node);
        }
    }

    @Operation
    public static final class CollectBytecodeLocations {
        @Specialization
        public static List<BytecodeLocation> perform(
                        @Bind BytecodeNode bytecode,
                        @Bind BasicInterpreter currentRootNode) {
            List<BytecodeLocation> bytecodeLocations = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(f -> {
                if (f.getCallTarget() instanceof RootCallTarget rct) {
                    RootNode frameRootNode = rct.getRootNode();
                    if (frameRootNode instanceof ContinuationRootNode cont) {
                        frameRootNode = (RootNode) cont.getSourceRootNode();
                    }
                    if (currentRootNode == frameRootNode) {
                        // We already have the bytecode node, no need to search.
                        bytecodeLocations.add(bytecode.getBytecodeLocation(f));
                    } else {
                        bytecodeLocations.add(BytecodeLocation.get(f));
                    }
                } else {
                    bytecodeLocations.add(null);
                }
                return null;
            });
            return bytecodeLocations;
        }
    }

    @Operation
    public static final class CollectSourceLocations {
        @Specialization
        public static List<SourceSection> perform(
                        @Bind BytecodeLocation location,
                        @Bind BasicInterpreter currentRootNode) {
            List<SourceSection> sourceLocations = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(f -> {
                if (f.getCallTarget() instanceof RootCallTarget rct && rct.getRootNode() instanceof BasicInterpreter frameRootNode) {
                    if (currentRootNode == frameRootNode) {
                        // The top-most stack trace element doesn't have a call node.
                        sourceLocations.add(location.getSourceLocation());
                    } else {
                        sourceLocations.add(frameRootNode.getBytecodeNode().getSourceLocation(f));
                    }
                } else {
                    sourceLocations.add(null);
                }
                return null;
            });
            return sourceLocations;
        }
    }

    @Operation
    public static final class CollectAllSourceLocations {
        @Specialization
        public static List<SourceSection[]> perform(
                        @Bind BytecodeLocation location,
                        @Bind BasicInterpreter currentRootNode) {
            List<SourceSection[]> allSourceLocations = new ArrayList<>();
            Truffle.getRuntime().iterateFrames(f -> {
                if (f.getCallTarget() instanceof RootCallTarget rct && rct.getRootNode() instanceof BasicInterpreter frameRootNode) {
                    if (currentRootNode == frameRootNode) {
                        // The top-most stack trace element doesn't have a call node.
                        allSourceLocations.add(location.getSourceLocations());
                    } else {
                        allSourceLocations.add(frameRootNode.getBytecodeNode().getSourceLocations(f));
                    }
                } else {
                    allSourceLocations.add(null);
                }
                return null;
            });
            return allSourceLocations;
        }
    }

    @Operation
    public static final class Continue {
        public static final int LIMIT = 3;

        @SuppressWarnings("unused")
        @Specialization(guards = {"result.getContinuationRootNode() == rootNode"}, limit = "LIMIT")
        public static Object invokeDirect(ContinuationResult result, Object value,
                        @Cached("result.getContinuationRootNode()") ContinuationRootNode rootNode,
                        @Cached("create(rootNode.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(result.getFrame(), value);
        }

        @Specialization(replaces = "invokeDirect")
        public static Object invokeIndirect(ContinuationResult result, Object value,
                        @Cached IndirectCallNode callNode) {
            return callNode.call(result.getContinuationCallTarget(), result.getFrame(), value);
        }
    }

    @Operation
    public static final class CurrentLocation {
        @Specialization
        public static BytecodeLocation perform(@Bind BytecodeLocation location) {
            return location;
        }
    }

    @Instrumentation
    public static final class PrintHere {
        @Specialization
        public static void perform() {
            System.out.println("here!");
        }
    }

    @Instrumentation(javadoc = "Increments the instrumented value by 1.")
    public static final class IncrementValue {
        @Specialization
        public static long doIncrement(long value) {
            return value + 1;
        }
    }

    @Instrumentation
    public static final class DoubleValue {
        @Specialization
        public static long doDouble(long value) {
            return value << 1;
        }
    }

    @Operation
    public static final class EnableIncrementValueInstrumentation {
        @Specialization
        public static void doEnable(
                        @Bind BasicInterpreter root,
                        @Cached(value = "getConfig(root)", allowUncached = true, neverDefault = true) BytecodeConfig config) {
            root.getRootNodes().update(config);
        }

        @TruffleBoundary
        protected static BytecodeConfig getConfig(BasicInterpreter root) {
            BytecodeConfig.Builder configBuilder = BasicInterpreterBuilder.invokeNewConfigBuilder(root.getClass());
            configBuilder.addInstrumentation(IncrementValue.class);
            return configBuilder.build();
        }
    }

    @Operation
    static final class Mod {
        @Specialization
        static long doInts(long left, long right) {
            return left % right;
        }
    }

    @Operation
    static final class Less {
        @Specialization
        static boolean doInts(long left, long right) {
            return left < right;
        }
    }

    @Operation
    public static final class EnableDoubleValueInstrumentation {
        @Specialization
        public static void doEnable(
                        @Bind BasicInterpreter root,
                        @Cached(value = "getConfig(root)", allowUncached = true, neverDefault = true) BytecodeConfig config) {
            root.getRootNodes().update(config);
        }

        @TruffleBoundary
        protected static BytecodeConfig getConfig(BasicInterpreter root) {
            BytecodeConfig.Builder configBuilder = BasicInterpreterBuilder.invokeNewConfigBuilder(root.getClass());
            configBuilder.addInstrumentation(DoubleValue.class);
            return configBuilder.build();
        }

    }

    record Bindings(
                    BytecodeNode bytecode,
                    RootNode root,
                    BytecodeLocation location,
                    Instruction instruction,
                    Node node,
                    int bytecodeIndex) {
    }

    @Operation
    static final class ExplicitBindingsTest {
        @Specialization
        @SuppressWarnings("truffle")
        public static Bindings doDefault(
                        @Bind("$bytecodeNode") BytecodeNode bytecode,
                        @Bind("$rootNode") BasicInterpreter root1,
                        @Bind("$rootNode") BytecodeRootNode root2,
                        @Bind("$rootNode") RootNode root3,
                        @Bind("$bytecodeNode.getBytecodeLocation($bytecodeIndex)") BytecodeLocation location,
                        @Bind("$bytecodeNode.getInstruction($bytecodeIndex)") Instruction instruction,
                        @Bind("this") Node node1,
                        @Bind("$node") Node node2,
                        @Bind("$bytecodeIndex") int bytecodeIndex) {
            if (root1 != root2 || root2 != root3) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            if (node1 != node2) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            return new Bindings(bytecode, root1, location, instruction, node1, bytecodeIndex);
        }
    }

    @Operation
    static final class ImplicitBindingsTest {
        @Specialization
        public static Bindings doDefault(
                        @Bind BytecodeNode bytecode,
                        @Bind BasicInterpreter root1,
                        @Bind BytecodeRootNode root2,
                        @Bind RootNode root3,
                        @Bind BytecodeLocation location,
                        @Bind Instruction instruction,
                        @Bind Node node,
                        @Bind("$bytecodeIndex") int bytecodeIndex) {

            if (root1 != root2 || root2 != root3) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            return new Bindings(bytecode, root1, location, instruction, node, bytecodeIndex);
        }
    }

    @Operation
    static final class Variadic0Operation {
        @Specialization
        public static Object[] doDefault(@Variadic Object[] args) {
            return args;
        }
    }

    @Operation
    static final class Variadic1Operation {
        @Specialization
        @SuppressWarnings("unused")
        public static Object[] doDefault(long arg0, @Variadic Object[] args) {
            return args;
        }
    }

    @Operation
    static final class VariadicOffsetOperation {
        @Specialization
        @SuppressWarnings("unused")
        public static Object[] doDefault(@Variadic(startOffset = 4) Object[] args) {
            assertTrue(args.length >= 3);
            for (int i = 0; i < 4; i++) {
                assertNull(args[i]);
            }
            return args;
        }
    }

    @Operation
    @Variadic
    static final class DynamicVariadic {

        @Specialization
        @SuppressWarnings("unused")
        public static Object[] doDefault(@Variadic Object[] args) {
            return args;
        }
    }

    @Operation
    @Variadic
    static final class DynamicVariadicNull {

        @Specialization
        @SuppressWarnings("unused")
        public static Object[] doDefault() {
            return null;
        }
    }

    @Operation
    @Variadic
    static final class DynamicVariadicNums {

        @Specialization
        @SuppressWarnings("unused")
        public static Object[] doDefault(long a) {
            Object[] res = new Long[(int) a];
            for (long i = 0; i < a; i++) {
                res[(int) i] = i;
            }
            return res;
        }
    }

    @Operation
    static final class VariadicAddInt {
        @Specialization
        @SuppressWarnings("unused")
        public static long doDefault(long a, @Variadic Object[] args) {
            long result = 0;
            for (Object arg : args) {
                if (arg instanceof Long i) {
                    result += i * a;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Expected 'arg' to be long, found: " + arg.getClass().getSimpleName());
                }
            }
            return result;
        }
    }

    @Operation
    static final class VariadicAddLArr {
        @Specialization
        @SuppressWarnings("unused")
        public static long doDefault(long[] o, @Variadic Object[] args) {
            long result = 0;
            for (Object arg : args) {
                if (arg instanceof Long i) {
                    result += i;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Expected 'arg' to be long, found: " + arg.getClass().getSimpleName());
                }
            }
            return result;
        }
    }

    @Operation
    static final class VariadicAddIntLArr {
        @Specialization
        @SuppressWarnings("unused")
        public static long doDefault(long a, long[] o, @Variadic Object[] args) {
            long result = 0;
            for (Object arg : args) {
                if (arg instanceof Long i) {
                    result += i * a;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Expected 'arg' to be long, found: " + arg.getClass().getSimpleName());
                }
            }
            return result;
        }
    }

    @Operation
    static final class VariadicAddIntIntLArrLArr {
        @Specialization
        @SuppressWarnings("unused")
        public static long doDefault(long a, long b, long[] o, long[] p, @Variadic Object[] args) {
            long result = 0;
            for (Object arg : args) {
                if (arg instanceof Long i) {
                    result += i * a * b;
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new AssertionError("Expected 'arg' to be long, found: " + arg.getClass().getSimpleName());
                }
            }
            return result;
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
