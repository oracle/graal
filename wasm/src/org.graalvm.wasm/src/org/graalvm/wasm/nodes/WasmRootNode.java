/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.nodes;

import static org.graalvm.wasm.nodes.WasmFrame.popDouble;
import static org.graalvm.wasm.nodes.WasmFrame.popFloat;
import static org.graalvm.wasm.nodes.WasmFrame.popInt;
import static org.graalvm.wasm.nodes.WasmFrame.popLong;
import static org.graalvm.wasm.nodes.WasmFrame.popReference;
import static org.graalvm.wasm.nodes.WasmFrame.popVector128;
import static org.graalvm.wasm.nodes.WasmFrame.pushDouble;
import static org.graalvm.wasm.nodes.WasmFrame.pushFloat;
import static org.graalvm.wasm.nodes.WasmFrame.pushInt;
import static org.graalvm.wasm.nodes.WasmFrame.pushLong;
import static org.graalvm.wasm.nodes.WasmFrame.pushReference;
import static org.graalvm.wasm.nodes.WasmFrame.pushVector128;

import org.graalvm.wasm.WasmArguments;
import org.graalvm.wasm.WasmConstant;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmModule;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;

@NodeInfo(language = WasmLanguage.ID, description = "The root node of all WebAssembly functions")
public class WasmRootNode extends RootNode {
    private SourceSection sourceSection;
    @Child private WasmInstrumentableFunctionNode functionNode;
    private final BranchProfile nonLinkedProfile = BranchProfile.create();
    /** Bound module instance (single-context mode only). */
    @CompilationFinal private WasmInstance boundInstance;

    public WasmRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, WasmInstrumentableFunctionNode functionNode) {
        super(language, frameDescriptor);
        this.functionNode = functionNode;
    }

    protected final WasmContext getContext() {
        return WasmContext.get(this);
    }

    /**
     * Overridden by {@link org.graalvm.wasm.predefined.WasmBuiltinRootNode}.
     */
    protected WasmModule module() {
        return functionNode.module();
    }

    @SuppressWarnings("static-method")
    public final void tryInitialize(WasmContext context, WasmInstance instance) {
        // We want to ensure that linking always precedes the running of the WebAssembly code.
        // This linking should be as late as possible, because a WebAssembly context should
        // be able to parse multiple modules before the code gets run.
        if (!instance.isLinkCompleted()) {
            nonLinkedProfile.enter();
            context.linker().tryLink(instance);
        }
    }

    protected final WasmInstance instance(VirtualFrame frame) {
        WasmInstance instance = boundInstance;
        if (instance == null) {
            instance = WasmArguments.getModuleInstance(frame.getArguments());
        } else {
            CompilerAsserts.partialEvaluationConstant(instance);
            assert instance == WasmArguments.getModuleInstance(frame.getArguments());
        }
        assert instance == WasmContext.get(this).lookupModuleInstance(module());
        return instance;
    }

    public final void setBoundModuleInstance(WasmInstance boundInstance) {
        CompilerAsserts.neverPartOfCompilation();
        assert this.boundInstance == null;
        this.boundInstance = boundInstance;
    }

    protected final WasmMemory memory(VirtualFrame frame) {
        return memory(frame, 0);
    }

    protected final WasmMemory memory(VirtualFrame frame, int index) {
        return module().memory(instance(frame), index);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        assert WasmArguments.isValid(frame.getArguments());
        final WasmContext context = getContext();
        final WasmInstance instance = instance(frame);
        tryInitialize(context, instance);
        return executeWithContext(frame, context, instance);
    }

    public Object executeWithContext(VirtualFrame frame, WasmContext context, WasmInstance instance) {
        // WebAssembly structure dictates that a function's arguments are provided to the function
        // as local variables, followed by any additional local variables that the function
        // declares. A VirtualFrame contains a special array for the arguments, so we need to move
        // the arguments to the array that holds the locals.
        //
        // The operand stack is also represented in the same long array.
        //
        // This combined array is kept inside a frame slot.
        // The reason for this is that the operand stack cannot be passed
        // as an argument to the loop-node's execute method,
        // and must be restored at the beginning of the loop body.
        final int localCount = functionNode.localCount();
        moveArgumentsToLocals(frame);

        // WebAssembly rules dictate that a function's locals must be initialized to zero before
        // function invocation. For more information, check the specification:
        // https://webassembly.github.io/spec/core/exec/instructions.html#function-calls
        initializeLocals(frame);

        final int resultCount = functionNode.resultCount();
        CompilerAsserts.partialEvaluationConstant(resultCount);
        if (resultCount > 1) {
            WasmLanguage.get(this).multiValueStack().resize(resultCount);
        }

        try {
            functionNode.execute(frame, context, instance);
        } catch (StackOverflowError e) {
            functionNode.enterErrorBranch();
            throw WasmException.create(Failure.CALL_STACK_EXHAUSTED);
        }
        if (resultCount == 0) {
            return WasmConstant.VOID;
        } else if (resultCount == 1) {
            final byte resultType = functionNode.resultType(0);
            CompilerAsserts.partialEvaluationConstant(resultType);
            switch (resultType) {
                case WasmType.VOID_TYPE:
                    return WasmConstant.VOID;
                case WasmType.I32_TYPE:
                    return popInt(frame, localCount);
                case WasmType.I64_TYPE:
                    return popLong(frame, localCount);
                case WasmType.F32_TYPE:
                    return popFloat(frame, localCount);
                case WasmType.F64_TYPE:
                    return popDouble(frame, localCount);
                case WasmType.V128_TYPE:
                    return popVector128(frame, localCount);
                case WasmType.FUNCREF_TYPE:
                case WasmType.EXTERNREF_TYPE:
                    return popReference(frame, localCount);
                default:
                    throw WasmException.format(Failure.UNSPECIFIED_INTERNAL, this, "Unknown result type: %d", resultType);
            }
        } else {
            moveResultValuesToMultiValueStack(frame, resultCount, localCount);
            return WasmConstant.MULTI_VALUE;
        }
    }

    @ExplodeLoop
    private void moveResultValuesToMultiValueStack(VirtualFrame frame, int resultCount, int localCount) {
        CompilerAsserts.partialEvaluationConstant(resultCount);
        final var multiValueStack = WasmLanguage.get(this).multiValueStack();
        final long[] primitiveMultiValueStack = multiValueStack.primitiveStack();
        final Object[] objectMultiValueStack = multiValueStack.objectStack();
        for (int i = 0; i < resultCount; i++) {
            final int resultType = functionNode.resultType(i);
            CompilerAsserts.partialEvaluationConstant(resultType);
            switch (resultType) {
                case WasmType.I32_TYPE:
                    primitiveMultiValueStack[i] = popInt(frame, localCount + i);
                    break;
                case WasmType.I64_TYPE:
                    primitiveMultiValueStack[i] = popLong(frame, localCount + i);
                    break;
                case WasmType.F32_TYPE:
                    primitiveMultiValueStack[i] = Float.floatToRawIntBits(popFloat(frame, localCount + i));
                    break;
                case WasmType.F64_TYPE:
                    primitiveMultiValueStack[i] = Double.doubleToRawLongBits(popDouble(frame, localCount + i));
                    break;
                case WasmType.V128_TYPE:
                    objectMultiValueStack[i] = popVector128(frame, localCount + i);
                    break;
                case WasmType.FUNCREF_TYPE:
                case WasmType.EXTERNREF_TYPE:
                    objectMultiValueStack[i] = popReference(frame, localCount + i);
                    break;
                default:
                    throw WasmException.format(Failure.UNSPECIFIED_INTERNAL, this, "Unknown result type: %d", resultType);
            }
        }
    }

    @ExplodeLoop
    private void moveArgumentsToLocals(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int paramCount = functionNode.paramCount();
        assert WasmArguments.getArgumentCount(args) == paramCount : "Expected number of params " + paramCount + ", actual " + WasmArguments.getArgumentCount(args);
        for (int i = 0; i != paramCount; ++i) {
            final Object arg = WasmArguments.getArgument(args, i);
            byte type = functionNode.localType(i);
            switch (type) {
                case WasmType.I32_TYPE:
                    pushInt(frame, i, (int) arg);
                    break;
                case WasmType.I64_TYPE:
                    pushLong(frame, i, (long) arg);
                    break;
                case WasmType.F32_TYPE:
                    pushFloat(frame, i, (float) arg);
                    break;
                case WasmType.F64_TYPE:
                    pushDouble(frame, i, (double) arg);
                    break;
                case WasmType.V128_TYPE:
                    pushVector128(frame, i, (Vector128) arg);
                    break;
                case WasmType.FUNCREF_TYPE:
                case WasmType.EXTERNREF_TYPE:
                    pushReference(frame, i, arg);
                    break;
            }
        }
    }

    @ExplodeLoop
    private void initializeLocals(VirtualFrame frame) {
        int paramCount = functionNode.paramCount();
        for (int i = paramCount; i != functionNode.localCount(); ++i) {
            byte type = functionNode.localType(i);
            switch (type) {
                case WasmType.I32_TYPE:
                    pushInt(frame, i, 0);
                    break;
                case WasmType.I64_TYPE:
                    pushLong(frame, i, 0L);
                    break;
                case WasmType.F32_TYPE:
                    pushFloat(frame, i, 0F);
                    break;
                case WasmType.F64_TYPE:
                    pushDouble(frame, i, 0D);
                    break;
                case WasmType.V128_TYPE:
                    pushVector128(frame, i, Vector128.ZERO);
                    break;
                case WasmType.FUNCREF_TYPE:
                case WasmType.EXTERNREF_TYPE:
                    pushReference(frame, i, WasmConstant.NULL);
                    break;
            }
        }
    }

    @Override
    public final String toString() {
        return getName();
    }

    @Override
    public String getName() {
        if (functionNode == null) {
            return "function";
        }
        return functionNode.name();
    }

    @Override
    public final String getQualifiedName() {
        if (functionNode == null) {
            return getName();
        }
        return functionNode.qualifiedName();
    }

    @Override
    @TruffleBoundary
    protected boolean isInstrumentable() {
        return functionNode != null && functionNode.isInstrumentable();
    }

    @Override
    @TruffleBoundary
    public final SourceSection getSourceSection() {
        if (functionNode == null) {
            return null;
        }
        if (sourceSection == null) {
            sourceSection = functionNode.getSourceSection();
            if (sourceSection == null) {
                sourceSection = module().source().createUnavailableSection();
            }
        }
        return sourceSection;
    }
}
