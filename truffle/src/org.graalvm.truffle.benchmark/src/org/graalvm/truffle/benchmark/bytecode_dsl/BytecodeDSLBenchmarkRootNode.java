/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.truffle.benchmark.bytecode_dsl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.bytecode.BytecodeIndexMode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.ConstantOperand;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

@GenerateBytecodeTestVariants({
                @Variant(suffix = "NoOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableThreadedSwitch = false, enableTagInstrumentation = true, enableYield = true)),
                @Variant(suffix = "Uncached", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableThreadedSwitch = true, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true)),
                @Variant(suffix = "AllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableThreadedSwitch = false, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true, boxingEliminationTypes = {
                                                int.class, boolean.class})),
                @Variant(suffix = "ThreadedAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableThreadedSwitch = true, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true, boxingEliminationTypes = {
                                                int.class, boolean.class})),
                @Variant(suffix = "TailCallAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableTailCallHandlers = true, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true, boxingEliminationTypes = {
                                                int.class, boolean.class})),
                @Variant(suffix = "TailCallExplicitBciAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableTailCallHandlers = true, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.NONE, boxingEliminationTypes = {
                                                int.class, boolean.class})),
                @Variant(suffix = "TailCallStoredBciAllOpts", configuration = @GenerateBytecode(languageClass = BenchmarkLanguage.class, //
                                enableTailCallHandlers = true, enableTagInstrumentation = true, enableYield = true, enableUncachedInterpreter = true, bytecodeIndexMode = BytecodeIndexMode.FRAME, boxingEliminationTypes = {
                                                int.class, boolean.class}))
})
// NONE variants intentionally benchmark operations that cannot store a bytecode index.
@SuppressWarnings("truffle-interpreted-performance")
public abstract class BytecodeDSLBenchmarkRootNode extends RootNode implements BytecodeRootNode {

    protected BytecodeDSLBenchmarkRootNode(BenchmarkLanguage language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    @Operation
    static final class Add {
        @Specialization
        static int doInts(int left, int right) {
            return left + right;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    static final class AddConst {
        @Specialization
        static int doInt(int c, int operand) {
            return c + operand;
        }
    }

    @Operation
    static final class Mult {
        @Specialization
        static int doInt(int left, int right) {
            return left * right;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    static final class MultConst {
        @Specialization
        static int doInt(int c, int operand) {
            return c * operand;
        }
    }

    @Operation
    @ConstantOperand(type = int.class, specifyAtEnd = true)
    static final class DivConst {
        @Specialization
        static int doInt(int operand, int c) {
            return operand / c;
        }
    }

    @Operation
    @ConstantOperand(type = int.class)
    static final class EqConst {
        @Specialization
        static boolean doInt(int c, int operand) {
            return c == operand;
        }
    }

    @Operation
    static final class Mod {
        @Specialization
        static int doInts(int left, int right) {
            return left % right;
        }
    }

    @Operation
    static final class Less {
        @Specialization
        static boolean doInts(int left, int right) {
            return left < right;
        }
    }

    @Operation
    static final class ArrayLength {
        @Specialization
        static int doArray(int[] array) {
            return array.length;
        }
    }

    @Operation
    static final class ArrayIndex {
        @Specialization
        static int doArray(int[] array, int i) {
            return array[i];
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractDirectCall {
        @Specialization(guards = "true", excludeForUncached = true)
        static int doInt(int delta, int value,
                        @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(value - delta);
        }

        @Specialization(replaces = "doInt")
        static int doUncached(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), value - delta);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractIndirectCall {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), value - delta);
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractCallTarget {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Bind Node location) {
            return (int) root.getCallTarget().call(location, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractCallTargetImplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Bind Node location) {
            return (int) root.getCallTarget().call(location, -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractCallTargetExplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Bind Node location) {
            return (int) root.getCallTarget().call(location, bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractDirectCallImplicitBci {
        @Specialization(guards = "true", excludeForUncached = true)
        static int doInt(int delta, int value,
                        @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(-1, new Object[]{value - delta});
        }

        @Specialization(replaces = "doInt")
        static int doUncached(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractDirectCallExplicitBci {
        @Specialization(guards = "true", excludeForUncached = true)
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(bytecodeIndex, new Object[]{value - delta});
        }

        @Specialization(replaces = "doInt")
        static int doUncached(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractIndirectCallImplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractIndirectCallExplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root.getCallTarget(), bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractDirectCallWithRootNodeImplicitBci {
        @Specialization(guards = "true", excludeForUncached = true)
        static int doInt(int delta, int value,
                        @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(BytecodeDSLBenchmarkRootNode.class, -1, new Object[]{value - delta});
        }

        @Specialization(replaces = "doInt")
        static int doUncached(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root, -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractDirectCallWithRootNodeExplicitBci {
        @Specialization(guards = "true", excludeForUncached = true)
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Cached("create($rootNode.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(BytecodeDSLBenchmarkRootNode.class, bytecodeIndex, new Object[]{value - delta});
        }

        @Specialization(replaces = "doInt")
        static int doUncached(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root, bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractIndirectCallWithRootNodeImplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root, -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractIndirectCallWithRootNodeExplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Cached IndirectCallNode callNode) {
            return (int) callNode.call(root, bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractRootNodeCallImplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Bind Node location) {
            return (int) root.call(location, -1, new Object[]{value - delta});
        }
    }

    @Operation(storeBytecodeIndex = true)
    @ConstantOperand(type = int.class)
    static final class RecurseSubtractRootNodeCallExplicitBci {
        @Specialization
        static int doInt(int delta, int value,
                        @Bind("$bytecodeIndex") int bytecodeIndex,
                        @Bind BytecodeDSLBenchmarkRootNode root,
                        @Bind Node location) {
            return (int) root.call(location, bytecodeIndex, new Object[]{value - delta});
        }
    }

    @Operation
    static final class Unreachable {
        @Specialization
        static void assertUnreachable() {
            CompilerDirectives.shouldNotReachHere();
        }
    }

}
