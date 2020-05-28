/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.wasm.WasmTracing.trace;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.wasm.ValueTypes;
import org.graalvm.wasm.WasmCodeEntry;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.WasmVoidResult;

@NodeInfo(language = "wasm", description = "The root node of all WebAssembly functions")
public class WasmRootNode extends RootNode implements WasmNodeInterface {

    private final WasmCodeEntry codeEntry;
    @CompilationFinal private ContextReference<WasmContext> rawContextReference;
    @Child private WasmNode body;

    public WasmRootNode(TruffleLanguage<?> language, WasmCodeEntry codeEntry) {
        super(language);
        this.codeEntry = codeEntry;
        this.body = null;
    }

    protected ContextReference<WasmContext> contextReference() {
        if (rawContextReference == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            rawContextReference = lookupContextReference(WasmLanguage.class);
        }
        return rawContextReference;
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
        context.linker().tryLink();
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        final WasmContext context = contextReference().get();
        tryInitialize(context);
        return executeWithContext(frame, context);
    }

    public Object executeWithContext(VirtualFrame frame, WasmContext context) {

        // WebAssembly structure dictates that a function's arguments are provided to the function
        // as local variables, followed by any additional local variables that the function
        // declares. A VirtualFrame contains a special array for the arguments, so we need to move
        // them to the locals.
        argumentsToLocals(frame);

        // WebAssembly rules dictate that a function's locals must be initialized to zero before
        // function invocation. For more information, check the specification:
        // https://webassembly.github.io/spec/core/exec/instructions.html#function-calls
        initializeLocals(frame);

        trace("%s EXECUTE", this);

        body.execute(context, frame);

        switch (body.returnTypeId()) {
            case 0x00:
            case ValueTypes.VOID_TYPE: {
                return WasmVoidResult.getInstance();
            }
            case ValueTypes.I32_TYPE: {
                long returnValue = pop(frame, 0);
                assert returnValue >>> 32 == 0;
                return (int) returnValue;
            }
            case ValueTypes.I64_TYPE: {
                long returnValue = pop(frame, 0);
                return returnValue;
            }
            case ValueTypes.F32_TYPE: {
                long returnValue = pop(frame, 0);
                assert returnValue >>> 32 == 0;
                return Float.intBitsToFloat((int) returnValue);
            }
            case ValueTypes.F64_TYPE: {
                long returnValue = pop(frame, 0);
                return Double.longBitsToDouble(returnValue);
            }
            default:
                assert false;
                return null;
        }
    }

    @ExplodeLoop
    private void argumentsToLocals(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int numArgs = body.module().symbolTable().function(codeEntry().functionIndex()).numArguments();
        assert args.length == numArgs : "Expected number of arguments " + numArgs + ", actual " + args.length;
        for (int i = 0; i != numArgs; ++i) {
            FrameSlot slot = codeEntry.localSlot(i);
            FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(slot);
            switch (kind) {
                case Int: {
                    int argument = (int) args[i];
                    trace("argument: 0x%08X (%d) [i32]", argument, argument);
                    frame.setInt(slot, argument);
                    break;
                }
                case Long: {
                    long argument = (long) args[i];
                    trace("argument: 0x%016X (%d) [i64]", argument, argument);
                    frame.setLong(slot, argument);
                    break;
                }
                case Float: {
                    float argument = (float) args[i];
                    trace("argument: %f [f32]", argument);
                    frame.setFloat(slot, argument);
                    break;
                }
                case Double: {
                    double argument = (double) args[i];
                    trace("argument: %f [f64]", argument);
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
    public String getQualifiedName() {
        if (codeEntry == null) {
            return getName();
        }
        return codeEntry.function().moduleName() + "." + getName();
    }
}
