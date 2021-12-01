/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.nodes.WasmFrame.popLong;
import static org.graalvm.wasm.nodes.WasmFrame.popDouble;
import static org.graalvm.wasm.nodes.WasmFrame.popFloat;
import static org.graalvm.wasm.nodes.WasmFrame.popInt;
import static org.graalvm.wasm.nodes.WasmFrame.pushLong;
import static org.graalvm.wasm.nodes.WasmFrame.pushDouble;
import static org.graalvm.wasm.nodes.WasmFrame.pushFloat;
import static org.graalvm.wasm.nodes.WasmFrame.pushInt;

import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmInstance;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmType;
import org.graalvm.wasm.WasmVoidResult;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@NodeInfo(language = WasmLanguage.ID, description = "The root node of all WebAssembly functions")
public class WasmRootNode extends RootNode {

    protected final WasmInstance instance;
    private final WasmCodeEntry codeEntry;
    private final SourceSection sourceSection;
    @Child private WasmNode body;

    public WasmRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, WasmInstance instance, WasmCodeEntry codeEntry) {
        super(language, frameDescriptor);
        this.instance = instance;
        this.codeEntry = codeEntry;
        this.body = null;
        this.sourceSection = instance.module().source().createUnavailableSection();
    }

    protected final WasmContext getContext() {
        return WasmContext.get(this);
    }

    public void setBody(WasmNode body) {
        this.body = insert(body);
    }

    @Override
    protected boolean isInstrumentable() {
        return false;
    }

    public void tryInitialize(WasmContext context) {
        // We want to ensure that linking always precedes the running of the WebAssembly code.
        // This linking should be as late as possible, because a WebAssembly context should
        // be able to parse multiple modules before the code gets run.
        context.linker().tryLink(instance);
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        final WasmContext context = getContext();
        tryInitialize(context);
        return executeWithContext(frame, context);
    }

    public Object executeWithContext(VirtualFrame frame, WasmContext context) {
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
        final int numLocals = body.codeEntry().numLocals();
        moveArgumentsToLocals(frame);

        // WebAssembly rules dictate that a function's locals must be initialized to zero before
        // function invocation. For more information, check the specification:
        // https://webassembly.github.io/spec/core/exec/instructions.html#function-calls
        initializeLocals(frame);

        try {
            body.execute(context, frame);
        } catch (StackOverflowError e) {
            errorBranch();
            throw WasmException.create(Failure.CALL_STACK_EXHAUSTED);
        }

        final byte returnTypeId = body.returnTypeId();
        CompilerAsserts.partialEvaluationConstant(returnTypeId);
        switch (returnTypeId) {
            case 0x00:
            case WasmType.VOID_TYPE:
                return WasmVoidResult.getInstance();
            case WasmType.I32_TYPE:
                return popInt(frame, numLocals);
            case WasmType.I64_TYPE:
                return popLong(frame, numLocals);
            case WasmType.F32_TYPE:
                return popFloat(frame, numLocals);
            case WasmType.F64_TYPE:
                return popDouble(frame, numLocals);
            default:
                throw WasmException.format(Failure.UNSPECIFIED_INTERNAL, this, "Unknown return type id: %d", returnTypeId);
        }
    }

    protected final void errorBranch() {
        codeEntry().errorBranch();
    }

    @ExplodeLoop
    private void moveArgumentsToLocals(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int numArgs = body.instance().symbolTable().function(codeEntry().functionIndex()).numArguments();
        assert args.length == numArgs : "Expected number of arguments " + numArgs + ", actual " + args.length;
        for (int i = 0; i != numArgs; ++i) {
            final Object arg = args[i];
            byte type = body.codeEntry().localType(i);
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
            }
        }
    }

    @ExplodeLoop
    private void initializeLocals(VirtualFrame frame) {
        int numArgs = body.instance().symbolTable().function(codeEntry().functionIndex()).numArguments();
        for (int i = numArgs; i != body.codeEntry().numLocals(); ++i) {
            byte type = body.codeEntry().localType(i);
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
            }
        }
    }

    public final WasmCodeEntry codeEntry() {
        return codeEntry;
    }

    @Override
    public final String toString() {
        return getName();
    }

    @Override
    public String getName() {
        if (codeEntry == null) {
            return "function";
        }
        return codeEntry.function().name();
    }

    @Override
    public final String getQualifiedName() {
        if (codeEntry == null) {
            return getName();
        }
        return codeEntry.function().moduleName() + "." + getName();
    }

    @Override
    public final SourceSection getSourceSection() {
        return sourceSection;
    }
}
