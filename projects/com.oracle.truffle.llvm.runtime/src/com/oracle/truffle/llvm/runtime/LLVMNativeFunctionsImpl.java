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

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;

import sun.misc.Unsafe;

final class LLVMNativeFunctionsImpl extends LLVMNativeFunctions {

    static final Unsafe UNSAFE = getUnsafe();

    @SuppressWarnings("restriction")
    private static Unsafe getUnsafe() {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
            singleoneInstanceField.setAccessible(true);
            return (Unsafe) singleoneInstanceField.get(null);
        } catch (Exception e) {
            throw new AssertionError();
        }
    }

    private final TruffleObject memmove;
    private final TruffleObject memcpy;
    private final TruffleObject memset;
    private final TruffleObject free;
    private final TruffleObject malloc;
    private final TruffleObject dynamicCast;
    private final TruffleObject sulongCanCatch;
    private final TruffleObject sulongThrow;
    private final TruffleObject getThrownObject;
    private final TruffleObject getExceptionPointer;
    private final TruffleObject getUnwindHeader;
    private final TruffleObject getDestructor;
    private final TruffleObject freeException;
    private final TruffleObject incrementHandlerCount;
    private final TruffleObject decrementHandlerCount;
    private final TruffleObject getHandlerCount;
    private final TruffleObject setHandlerCount;
    private final TruffleObject getExceptionType;

    private final TruffleObject nullPointer;

    LLVMNativeFunctionsImpl(NativeLookup nativeLookup) {
        memmove = nativeLookup == null ? null : nativeLookup.getNativeFunction("@memmove", "(POINTER,POINTER,UINT64):POINTER");
        memcpy = nativeLookup == null ? null : nativeLookup.getNativeFunction("@memcpy", "(POINTER,POINTER,UINT64):POINTER");
        memset = nativeLookup == null ? null : nativeLookup.getNativeFunction("@memset", "(POINTER,SINT32,UINT64):VOID");
        free = nativeLookup == null ? null : nativeLookup.getNativeFunction("@free", "(POINTER):VOID");
        malloc = nativeLookup == null ? null : nativeLookup.getNativeFunction("@malloc", "(UINT64):POINTER");
        dynamicCast = nativeLookup == null ? null : nativeLookup.getNativeFunction("@__dynamic_cast", "(POINTER,POINTER,POINTER,UINT64):POINTER");
        sulongCanCatch = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_canCatch", "(POINTER,POINTER,POINTER):UINT32");
        sulongThrow = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_throw", "(POINTER,POINTER,POINTER,POINTER,POINTER):VOID");
        getThrownObject = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_getThrownObject", "(POINTER):POINTER");
        getExceptionPointer = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_getExceptionPointer", "(POINTER):POINTER");
        getUnwindHeader = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_unwindHeader", "(POINTER):POINTER");
        getDestructor = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_getDestructor", "(POINTER):POINTER");
        freeException = nativeLookup == null ? null : nativeLookup.getNativeFunction("@__cxa_free_exception", "(POINTER):VOID");
        incrementHandlerCount = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_incrementHandlerCount", "(POINTER):VOID");
        decrementHandlerCount = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_decrementHandlerCount", "(POINTER):VOID");
        getHandlerCount = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_getHandlerCount", "(POINTER):SINT32");
        setHandlerCount = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_setHandlerCount", "(POINTER,SINT32):VOID");
        getExceptionType = nativeLookup == null ? null : nativeLookup.getNativeFunction("@sulong_eh_getType", "(POINTER):POINTER");

        nullPointer = nativeLookup == null ? null : nativeLookup.getNativeFunction("@getNullPointer", "():POINTER");
    }

    @Override
    public NullPointerNode createNullPointerNode() {
        return new NullPointerImpl(nullPointer);
    }

    @Override
    public MemCopyNode createMemMoveNode() {
        return new MemCopyNodeImpl(memmove);
    }

    @Override
    public MemCopyNode createMemCopyNode() {
        return new MemCopyNodeImpl(memcpy);
    }

    @Override
    public MemSetNode createMemSetNode() {
        return new MemSetNodeImpl(memset);
    }

    @Override
    public FreeNode createFreeNode() {
        return new FreeNodeImpl(free);
    }

    @Override
    public MallocNode createMallocNode() {
        return new MallocNodeImpl(malloc);
    }

    @Override
    public DynamicCastNode createDynamicCast() {
        return new DynamicCastNodeImpl(dynamicCast);
    }

    @Override
    public SulongCanCatchNode createSulongCanCatch() {
        return new SulongCanCatchNodeImpl(sulongCanCatch);
    }

    @Override
    public SulongThrowNode createSulongThrow() {
        return new SulongThrowNodeImpl(sulongThrow);
    }

    @Override
    public SulongGetThrownObjectNode createGetThrownObject() {
        return new SulongGetThrownObjectNodeImpl(getThrownObject);
    }

    @Override
    public SulongGetExceptionPointerNode createGetExceptionPointer() {
        return new SulongGetExceptionPointerNodeImpl(getExceptionPointer);
    }

    @Override
    public SulongGetUnwindHeaderNode createGetUnwindHeader() {
        return new SulongGetUnwindHeaderNodeImpl(getUnwindHeader);
    }

    @Override
    public SulongFreeExceptionNode createFreeException() {
        return new SulongFreeExceptionNodeImpl(freeException);
    }

    @Override
    public SulongGetDestructorNode createGetDestructor() {
        return new SulongGetDestructorNodeImpl(getDestructor);
    }

    @Override
    public SulongGetExceptionTypeNode createGetExceptionType() {
        return new SulongGetExceptionTypeNodeImpl(getExceptionType);
    }

    @Override
    public SulongDecrementHandlerCountNode createDecrementHandlerCount() {
        return new SulongDecrementHandlerCountNodeImpl(decrementHandlerCount);
    }

    @Override
    public SulongIncrementHandlerCountNode createIncrementHandlerCount() {
        return new SulongIncrementHandlerCountNodeImpl(incrementHandlerCount);
    }

    @Override
    public SulongGetHandlerCountNode createGetHandlerCount() {
        return new SulongGetHandlerCountNodeImpl(getHandlerCount);
    }

    @Override
    public SulongSetHandlerCountNode createSetHandlerCount() {
        return new SulongSetHandlerCountNodeImpl(setHandlerCount);
    }

    private static class SulongGetThrownObjectNodeImpl extends SulongGetThrownObjectNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        SulongGetThrownObjectNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public LLVMAddress getThrownObject(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class SulongGetExceptionPointerNodeImpl extends SulongGetExceptionPointerNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        SulongGetExceptionPointerNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public LLVMAddress getExceptionPointer(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class SulongGetDestructorNodeImpl extends SulongGetDestructorNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        SulongGetDestructorNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public LLVMAddress get(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class SulongGetExceptionTypeNodeImpl extends SulongGetExceptionTypeNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        SulongGetExceptionTypeNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public LLVMAddress get(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class SulongGetUnwindHeaderNodeImpl extends SulongGetUnwindHeaderNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        SulongGetUnwindHeaderNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public LLVMAddress getUnwind(LLVMAddress ptr) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(ptr.getVal())));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class DynamicCastNodeImpl extends DynamicCastNode {

        @Child private Node unbox = Message.UNBOX.createNode();

        DynamicCastNodeImpl(TruffleObject function) {
            super(function, 4);
        }

        @Override
        public LLVMAddress execute(LLVMAddress object, LLVMAddress type1, LLVMAddress type2, long value) {
            try {
                return LLVMAddress.fromLong((long) ForeignAccess.sendUnbox(unbox, (TruffleObject) execute(object.getVal(), type1.getVal(), type2.getVal(), value)));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(e);
            }
        }
    }

    private static class SulongCanCatchNodeImpl extends SulongCanCatchNode {

        SulongCanCatchNodeImpl(TruffleObject function) {
            super(function, 3);
        }

        @Override
        public int canCatch(LLVMAddress adjustedPtr, LLVMAddress excpType, LLVMAddress catchType) {
            return (int) execute(adjustedPtr.getVal(), excpType.getVal(), catchType.getVal());
        }
    }

    private static class SulongThrowNodeImpl extends SulongThrowNode {

        SulongThrowNodeImpl(TruffleObject function) {
            super(function, 5);
        }

        @Override
        public void throvv(LLVMAddress ptr, LLVMAddress type, LLVMAddress destructor, LLVMAddress unexpectedHandler, LLVMAddress terminateHandler) {
            execute(ptr.getVal(), type.getVal(), destructor == null ? 0 : destructor.getVal(), unexpectedHandler == null ? 0 : unexpectedHandler.getVal(),
                            terminateHandler == null ? 0 : terminateHandler.getVal());
        }
    }

    private static class SulongIncrementHandlerCountNodeImpl extends SulongIncrementHandlerCountNode {

        SulongIncrementHandlerCountNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public void inc(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    private static class SulongDecrementHandlerCountNodeImpl extends SulongDecrementHandlerCountNode {

        SulongDecrementHandlerCountNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public void dec(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    private static class SulongGetHandlerCountNodeImpl extends SulongGetHandlerCountNode {

        SulongGetHandlerCountNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public int get(LLVMAddress ptr) {
            return (int) execute(ptr.getVal());
        }
    }

    private static class SulongSetHandlerCountNodeImpl extends SulongSetHandlerCountNode {

        SulongSetHandlerCountNodeImpl(TruffleObject function) {
            super(function, 2);
        }

        @Override
        public void set(LLVMAddress ptr, int value) {
            execute(ptr.getVal(), value);
        }
    }

    private static class SulongFreeExceptionNodeImpl extends SulongFreeExceptionNode {

        SulongFreeExceptionNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public void free(LLVMAddress ptr) {
            execute(ptr.getVal());
        }
    }

    private static class MemCopyNodeImpl extends MemCopyNode {

        MemCopyNodeImpl(TruffleObject function) {
            super(function, 3);
        }

        @Override
        public void execute(LLVMAddress target, LLVMAddress source, long length) {
            UNSAFE.copyMemory(source.getVal(), target.getVal(), length);
        }
    }

    private static class MemSetNodeImpl extends MemSetNode {

        MemSetNodeImpl(TruffleObject function) {
            super(function, 2);
        }

        @Override
        public void execute(LLVMAddress target, int value, long length) {
            UNSAFE.setMemory(target.getVal(), length, (byte) value);
        }
    }

    private static class FreeNodeImpl extends FreeNode {

        FreeNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Override
        public void execute(LLVMAddress addr) {
            UNSAFE.freeMemory(addr.getVal());
        }
    }

    private static class MallocNodeImpl extends MallocNode {

        MallocNodeImpl(TruffleObject function) {
            super(function, 1);
        }

        @Child private Node unbox = Message.UNBOX.createNode();

        @Override
        public LLVMAddress execute(long size) {
            return LLVMAddress.fromLong(UNSAFE.allocateMemory(size));
        }
    }

    private static class NullPointerImpl extends NullPointerNode {

        NullPointerImpl(TruffleObject function) {
            super(function, 0);
        }

        @Child private Node unbox = Message.UNBOX.createNode();

        @Override
        public TruffleObject getNullPointer() {
            return (TruffleObject) execute();
        }
    }
}
