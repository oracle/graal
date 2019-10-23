/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;

import java.lang.reflect.InvocationTargetException;

@NodeInfo(language = "wasm", description = "The root node of all WebAssembly functions")
public class WasmRootNode extends RootNode implements WasmNodeInterface {
    private static final TruffleLogger logger = TruffleLogger.getLogger("wasm");

    @CompilationFinal private WasmCodeEntry codeEntry;
    @CompilationFinal private ContextReference<WasmContext> rawContextReference;
    @Child private WasmNode body;

    public WasmRootNode(TruffleLanguage<?> language, WasmCodeEntry codeEntry) {
        super(language);
        this.codeEntry = codeEntry;
        this.body = null;
    }

    protected ContextReference<WasmContext> contextReference() {
        if (rawContextReference == null) {
            rawContextReference = lookupContextReference(WasmLanguage.class);
        }
        return rawContextReference;
    }

    public void setBody(WasmNode body) {
        this.body = insert(body);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        // We want to ensure that linking always precedes the running of the WebAssembly code.
        // This linking should be as late as possible, because a WebAssembly context should
        // be able to parse multiple modules before the code gets run.
        final WasmContext context = contextReference().get();
        context.linker().tryLink();

        /*
         * WebAssembly structure dictates that a function's arguments are provided to the function
         * as local variables, followed by any additional local variables that the function declares.
         * A VirtualFrame contains a special array for the arguments, so we need to move them to the locals.
         */
        argumentsToLocals(frame);

        /*
         * WebAssembly rules dictate that a function's locals must be initialized to zero before function invocation.
         * For more information, check the specification:
         * https://webassembly.github.io/spec/core/exec/instructions.html#function-calls
         */
        initializeLocals(frame);

        logger.finest(() -> this + " EXECUTE");

        body.execute(context, frame);

        long returnValue = pop(frame, 0);
        if (getCallTarget().toString().contains("wasm-function:6") && returnValue == 0x44c) {
            System.err.println("culprit");
        }
        switch (body.returnTypeId()) {
            case 0x00:
            case ValueTypes.VOID_TYPE:
                return WasmVoidResult.getInstance();
            case ValueTypes.I32_TYPE:
                assert returnValue >>> 32 == 0;
                return (int) returnValue;
            case ValueTypes.I64_TYPE:
                return returnValue;
            case ValueTypes.F32_TYPE:
                assert returnValue >>> 32 == 0;
                return Float.intBitsToFloat((int) returnValue);
            case ValueTypes.F64_TYPE:
                return Double.longBitsToDouble(returnValue);
            default:
                assert false;
                return null;
        }
    }

    @ExplodeLoop
    private void argumentsToLocals(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int numArgs = body.module().symbolTable().function(codeEntry().functionIndex()).numArguments();
        assert args.length == numArgs;
        for (int i = 0; i != numArgs; ++i) {
            FrameSlot slot = codeEntry.localSlot(i);
            FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(slot);
            switch (kind) {
                case Int: {
                    int argument = (int) args[i];
                    logger.finest(() -> String.format("argument: 0x%08X (%d) [i32]", argument, argument));
                    frame.setInt(slot, argument);
                    break;
                }
                case Long: {
                    long argument = (long) args[i];
                    logger.finest(() -> String.format("argument: 0x%016X (%d) [i64]", argument, argument));
                    frame.setLong(slot, argument);
                    break;
                }
                case Float: {
                    float argument = (float) args[i];
                    logger.finest(() -> String.format("argument: %f [f32]", argument));
                    frame.setFloat(slot, argument);
                    break;
                }
                case Double: {
                    double argument = (double) args[i];
                    logger.finest(() -> String.format("argument: %f [f64]", argument));
                    frame.setDouble(slot, argument);
                    break;
                }
            }
        }
    }

    @ExplodeLoop
    private void initializeLocals(VirtualFrame frame) {
        int numArgs = body.module().symbolTable().function(codeEntry().functionIndex()).numArguments();
        for (int i = numArgs; i != body.codeEntry().numLocals(); ++i) {
            byte type = body.codeEntry().localType(i);
            switch (type) {
                case ValueTypes.I32_TYPE:
                    body.setInt(frame, i, 0);
                    break;
                case ValueTypes.I64_TYPE:
                    body.setLong(frame, i, 0);
                    break;
                case ValueTypes.F32_TYPE:
                    body.setFloat(frame, i, 0);
                    break;
                case ValueTypes.F64_TYPE:
                    body.setDouble(frame, i, 0);
                    break;
            }
        }
    }

    @Override
    public WasmCodeEntry codeEntry() {
        return codeEntry;
    }

    @Override
    public String toString() {
        return "wasm-function:" + codeEntry.functionIndex();
    }
}
