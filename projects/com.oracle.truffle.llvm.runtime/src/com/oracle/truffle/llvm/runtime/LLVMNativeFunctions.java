/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

public final class LLVMNativeFunctions {

    private final NFIContextExtension nfiContext;
    private final Map<String, TruffleObject> nativeFunctions;

    public LLVMNativeFunctions(NFIContextExtension nfiContext) {
        this.nfiContext = nfiContext;
        this.nativeFunctions = new HashMap<>();
    }

    private TruffleObject getNativeFunction(LLVMContext context, String name, String signature) {
        return nativeFunctions.computeIfAbsent(name, s -> nfiContext.getNativeFunction(context, name, signature));
    }

    public NullPointerNode createNullPointerNode(LLVMContext context) {
        TruffleObject nullPointerFunction = getNativeFunction(context, "@getNullPointer", "():POINTER");
        return new NullPointerNode(nullPointerFunction);
    }

    public DynamicCastNode createDynamicCast(LLVMContext context) {
        TruffleObject dynamicCastFunction = getNativeFunction(context, "@__dynamic_cast", "(POINTER,POINTER,POINTER,UINT64):POINTER");
        return new DynamicCastNode(dynamicCastFunction);
    }

    public SulongCanCatchNode createSulongCanCatch(LLVMContext context) {
        TruffleObject canCatchFunction = getNativeFunction(context, "@sulong_eh_canCatch", "(POINTER,POINTER,POINTER):UINT32");
        return new SulongCanCatchNode(canCatchFunction);
    }

    public SulongThrowNode createSulongThrow(LLVMContext context) {
        TruffleObject throwFunction = getNativeFunction(context, "@sulong_eh_throw", "(POINTER,POINTER,POINTER,POINTER,POINTER):VOID");
        return new SulongThrowNode(throwFunction);
    }

    public SulongGetThrownObjectNode createGetThrownObject(LLVMContext context) {
        TruffleObject getThrownObjectFunction = getNativeFunction(context, "@sulong_eh_getThrownObject", "(POINTER):POINTER");
        return new SulongGetThrownObjectNode(getThrownObjectFunction);
    }

    public SulongGetExceptionPointerNode createGetExceptionPointer(LLVMContext context) {
        TruffleObject getExceptionPointerFunction = getNativeFunction(context, "@sulong_eh_getExceptionPointer", "(POINTER):POINTER");
        return new SulongGetExceptionPointerNode(getExceptionPointerFunction);
    }

    public SulongGetUnwindHeaderNode createGetUnwindHeader(LLVMContext context) {
        TruffleObject getUnwindHeaderFunction = getNativeFunction(context, "@sulong_eh_unwindHeader", "(POINTER):POINTER");
        return new SulongGetUnwindHeaderNode(getUnwindHeaderFunction);
    }

    public SulongFreeExceptionNode createFreeException(LLVMContext context) {
        TruffleObject freeFunction = getNativeFunction(context, "@__cxa_free_exception", "(POINTER):VOID");
        return new SulongFreeExceptionNode(freeFunction);
    }

    public SulongGetDestructorNode createGetDestructor(LLVMContext context) {
        TruffleObject getDestructorFunction = getNativeFunction(context, "@sulong_eh_getDestructor", "(POINTER):POINTER");
        return new SulongGetDestructorNode(getDestructorFunction);
    }

    public SulongGetExceptionTypeNode createGetExceptionType(LLVMContext context) {
        TruffleObject getExceptionTypeFunction = getNativeFunction(context, "@sulong_eh_getType", "(POINTER):POINTER");
        return new SulongGetExceptionTypeNode(getExceptionTypeFunction);
    }

    public SulongDecrementHandlerCountNode createDecrementHandlerCount(LLVMContext context) {
        TruffleObject decrementHandlerCountFunction = getNativeFunction(context, "@sulong_eh_decrementHandlerCount", "(POINTER):VOID");
        return new SulongDecrementHandlerCountNode(decrementHandlerCountFunction);
    }

    public SulongIncrementHandlerCountNode createIncrementHandlerCount(LLVMContext context) {
        TruffleObject incrementHandlerCountFunction = getNativeFunction(context, "@sulong_eh_incrementHandlerCount", "(POINTER):VOID");
        return new SulongIncrementHandlerCountNode(incrementHandlerCountFunction);
    }

    public SulongGetHandlerCountNode createGetHandlerCount(LLVMContext context) {
        TruffleObject getHandlerCountFunction = getNativeFunction(context, "@sulong_eh_getHandlerCount", "(POINTER):SINT32");
        return new SulongGetHandlerCountNode(getHandlerCountFunction);
    }

    public SulongSetHandlerCountNode createSetHandlerCount(LLVMContext context) {
        TruffleObject setHandlerCountFunction = getNativeFunction(context, "@sulong_eh_setHandlerCount", "(POINTER,SINT32):VOID");
        return new SulongSetHandlerCountNode(setHandlerCountFunction);
    }

    protected static class HeapFunctionNode extends Node {

        private final TruffleObject function;
        @Child private Node nativeExecute;

        protected HeapFunctionNode(TruffleObject function, int argCount) {
            this.function = function;
            this.nativeExecute = Message.createExecute(argCount).createNode();
        }

        protected Object execute(Object... args) {
            try {
                return ForeignAccess.sendExecute(nativeExecute, function, args);
            } catch (InteropException e) {
                throw new AssertionError(e);
            }
        }
    }

    public static class SulongIncrementHandlerCountNode extends HeapFunctionNode {
        SulongIncrementHandlerCountNode(TruffleObject function) {
            super(function, 1);
        }

        public void inc(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    public static class SulongDecrementHandlerCountNode extends HeapFunctionNode {
        SulongDecrementHandlerCountNode(TruffleObject function) {
            super(function, 1);
        }

        public void dec(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    public static class SulongGetHandlerCountNode extends HeapFunctionNode {

        SulongGetHandlerCountNode(TruffleObject function) {
            super(function, 1);
        }

        public int get(LLVMAddress ptr) {
            return (int) execute(ptr.getVal());
        }
    }

    public static class SulongSetHandlerCountNode extends HeapFunctionNode {
        SulongSetHandlerCountNode(TruffleObject function) {
            super(function, 2);
        }

        public void set(LLVMAddress ptr, int value) {
            execute(ptr.getVal(), value);
        }
    }

    public static class SulongFreeExceptionNode extends HeapFunctionNode {
        SulongFreeExceptionNode(TruffleObject function) {
            super(function, 1);
        }

        public void free(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    public static class SulongGetDestructorNode extends HeapFunctionNode {
        @Child private Node asPointer = Message.AS_POINTER.createNode();

        SulongGetDestructorNode(TruffleObject function) {
            super(function, 1);
        }

        public LLVMAddress get(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SulongGetExceptionTypeNode extends HeapFunctionNode {
        @Child private Node asPointer = Message.AS_POINTER.createNode();

        SulongGetExceptionTypeNode(TruffleObject function) {
            super(function, 1);
        }

        public LLVMAddress get(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SulongGetUnwindHeaderNode extends HeapFunctionNode {

        @Child private Node asPointer = Message.AS_POINTER.createNode();

        SulongGetUnwindHeaderNode(TruffleObject function) {
            super(function, 1);
        }

        public LLVMAddress getUnwind(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SulongGetThrownObjectNode extends HeapFunctionNode {
        @Child private Node asPointer = Message.AS_POINTER.createNode();

        SulongGetThrownObjectNode(TruffleObject function) {
            super(function, 1);
        }

        public LLVMAddress getThrownObject(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SulongGetExceptionPointerNode extends HeapFunctionNode {
        @Child private Node asPointer = Message.AS_POINTER.createNode();

        SulongGetExceptionPointerNode(TruffleObject function) {
            super(function, 1);
        }

        public LLVMAddress getExceptionPointer(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class SulongCanCatchNode extends HeapFunctionNode {
        SulongCanCatchNode(TruffleObject function) {
            super(function, 3);
        }

        public int canCatch(LLVMAddress adjustedPtr, LLVMAddress excpType, LLVMAddress catchType) {
            return (int) execute(adjustedPtr.getVal(), excpType.getVal(), catchType.getVal());
        }
    }

    public static class SulongThrowNode extends HeapFunctionNode {
        SulongThrowNode(TruffleObject function) {
            super(function, 5);
        }

        public void throvv(LLVMAddress ptr, LLVMAddress type, LLVMAddress destructor, LLVMAddress unexpectedHandler, LLVMAddress terminateHandler) {
            execute(ptr.getVal(), type.getVal(), destructor == null ? 0 : destructor.getVal(), unexpectedHandler == null ? 0 : unexpectedHandler.getVal(),
                            terminateHandler == null ? 0 : terminateHandler.getVal());
        }
    }

    public static class DynamicCastNode extends HeapFunctionNode {

        @Child private Node asPointer = Message.AS_POINTER.createNode();

        DynamicCastNode(TruffleObject function) {
            super(function, 4);
        }

        public LLVMAddress execute(LLVMAddress object, LLVMAddress type1, LLVMAddress type2, long value) {
            try {
                return LLVMAddress.fromLong(ForeignAccess.sendAsPointer(asPointer, (TruffleObject) execute(object.getVal(), type1.getVal(), type2.getVal(), value)));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    public static class NullPointerNode extends HeapFunctionNode {

        NullPointerNode(TruffleObject function) {
            super(function, 0);
        }

        public TruffleObject getNullPointer() {
            return (TruffleObject) execute();
        }
    }

}
