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
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.NativeAllocator;
import com.oracle.truffle.llvm.runtime.NativeResolver;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.Type;

abstract class Container {
    private static final int CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC = 2;

    protected final Type type;

    Container(Type type) {
        CompilerAsserts.neverPartOfCompilation();
        this.type = type;
    }

    Type getType() {
        return type;
    }

    abstract void destroy();

    abstract LLVMAddress getNativeLocation(LLVMGlobalVariable global);

    abstract void putI1(LLVMGlobalVariable global, boolean value);

    abstract void putI8(LLVMGlobalVariable global, byte value);

    abstract void putI16(LLVMGlobalVariable global, short value);

    abstract void putI32(LLVMGlobalVariable global, int value);

    abstract void putI64(LLVMGlobalVariable global, long value);

    abstract void putFloat(LLVMGlobalVariable global, float value);

    abstract void putDouble(LLVMGlobalVariable global, double value);

    abstract void putAddress(LLVMGlobalVariable global, LLVMAddress value);

    abstract void putTruffleObject(LLVMGlobalVariable global, TruffleObject value);

    abstract void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value);

    abstract void putFunction(LLVMGlobalVariable global, LLVMFunction value);

    abstract void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value);

    abstract void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value);

    abstract Object get(LLVMGlobalVariable global);

    abstract boolean getI1(LLVMGlobalVariable global);

    abstract byte getI8(LLVMGlobalVariable global);

    abstract short getI16(LLVMGlobalVariable global);

    abstract int getI32(LLVMGlobalVariable global);

    abstract long getI64(LLVMGlobalVariable global);

    abstract float getFloat(LLVMGlobalVariable global);

    abstract double getDouble(LLVMGlobalVariable global);

    static final class UninitializedContainer extends Container {

        private final NativeResolver resolver;

        UninitializedContainer(Type type, NativeResolver resolver) {
            super(type);
            this.resolver = resolver;
        }

        @Override
        LLVMAddress getNativeLocation(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getNativeLocation(global);
        }

        @Override
        void putI1(LLVMGlobalVariable global, boolean value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putI1(global, value);
        }

        @Override
        void putI8(LLVMGlobalVariable global, byte value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putI8(global, value);
        }

        @Override
        void putI16(LLVMGlobalVariable global, short value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putI16(global, value);
        }

        @Override
        void putI32(LLVMGlobalVariable global, int value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putI32(global, value);
        }

        @Override
        void putI64(LLVMGlobalVariable global, long value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putI64(global, value);
        }

        @Override
        void putFloat(LLVMGlobalVariable global, float value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putFloat(global, value);
        }

        @Override
        void putDouble(LLVMGlobalVariable global, double value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putDouble(global, value);
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putAddress(global, value);
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot store managed object to native memory");
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot store managed object to native memory");
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putFunction(global, value);
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putBoxedPrimitive(global, value);
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().get(global);
        }

        @Override
        boolean getI1(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getI1(global);
        }

        @Override
        byte getI8(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getI8(global);
        }

        @Override
        short getI16(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getI16(global);
        }

        @Override
        int getI32(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getI32(global);
        }

        @Override
        long getI64(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getI64(global);
        }

        @Override
        float getFloat(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getFloat(global);
        }

        @Override
        double getDouble(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            return global.getContainer().getDouble(global);
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            global.setContainer(new NativeContainer(type, resolver.resolve()));
            global.getContainer().putGlobal(global, value);
        }

        @Override
        void destroy() {
            // nothing to do
        }

    }

    static final class NativeContainer extends Container {
        private final LLVMAddress address;

        NativeContainer(Type type, LLVMAddress address) {
            super(type);
            this.address = address;
        }

        @Override
        void destroy() {
            LLVMMemory.free(address);
        }

        @Override
        LLVMAddress getNativeLocation(LLVMGlobalVariable global) {
            return address;
        }

        @Override
        void putI1(LLVMGlobalVariable global, boolean value) {
            LLVMMemory.putI1(address, value);
        }

        @Override
        void putI8(LLVMGlobalVariable global, byte value) {
            LLVMMemory.putI8(address, value);
        }

        @Override
        void putI16(LLVMGlobalVariable global, short value) {
            LLVMMemory.putI16(address, value);
        }

        @Override
        void putI32(LLVMGlobalVariable global, int value) {
            LLVMMemory.putI32(address, value);
        }

        @Override
        void putI64(LLVMGlobalVariable global, long value) {
            LLVMMemory.putI64(address, value);
        }

        @Override
        void putFloat(LLVMGlobalVariable global, float value) {
            LLVMMemory.putFloat(address, value);
        }

        @Override
        void putDouble(LLVMGlobalVariable global, double value) {
            LLVMMemory.putDouble(address, value);
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot store managed object to native memory");
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot store managed object to native memory");
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            LLVMMemory.putAddress(address, value.getFunctionIndex());
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            // do not store the boxed primitive to native memory.
            // the boxed primitive was never converted to an address; when reading it would be
            // interpreted as an address
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Cannot store managed object to native memory");
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return LLVMMemory.getAddress(address);
        }

        @Override
        boolean getI1(LLVMGlobalVariable global) {
            return LLVMMemory.getI1(address);
        }

        @Override
        byte getI8(LLVMGlobalVariable global) {
            return LLVMMemory.getI8(address);
        }

        @Override
        short getI16(LLVMGlobalVariable global) {
            return LLVMMemory.getI16(address);
        }

        @Override
        int getI32(LLVMGlobalVariable global) {
            return LLVMMemory.getI32(address);
        }

        @Override
        long getI64(LLVMGlobalVariable global) {
            return LLVMMemory.getI64(address);
        }

        @Override
        float getFloat(LLVMGlobalVariable global) {
            return LLVMMemory.getFloat(address);
        }

        @Override
        double getDouble(LLVMGlobalVariable global) {
            return LLVMMemory.getDouble(address);
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            LLVMMemory.putAddress(address, value.getNativeLocation());
        }
    }

    abstract static class AbstractManagedContainer extends Container {
        protected final NativeAllocator allocator;

        AbstractManagedContainer(Type type, NativeAllocator address) {
            super(type);
            this.allocator = address;
        }

        @Override
        LLVMAddress getNativeLocation(LLVMGlobalVariable global) {
            return transferToNativeWithCopy(global);
        }

        LLVMAddress transferToNativeNoCopy(LLVMGlobalVariable global) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            LLVMAddress address = allocator.allocate();
            NativeContainer nativeContainer = new NativeContainer(type, address);
            global.setContainer(nativeContainer);
            return address;
        }

        LLVMAddress transferToNativeWithCopy(LLVMGlobalVariable global) {
            LLVMAddress address = transferToNativeNoCopy(global);
            copyToNative(address);
            return address;
        }

        abstract void copyToNative(LLVMAddress a);

        @Override
        void putI1(LLVMGlobalVariable global, boolean value) {
            transferToNativeNoCopy(global);
            global.getContainer().putI1(global, value);
        }

        @Override
        void putI8(LLVMGlobalVariable global, byte value) {
            transferToNativeNoCopy(global);
            global.getContainer().putI8(global, value);
        }

        @Override
        void putI16(LLVMGlobalVariable global, short value) {
            transferToNativeNoCopy(global);
            global.getContainer().putI16(global, value);

        }

        @Override
        void putI32(LLVMGlobalVariable global, int value) {
            transferToNativeNoCopy(global);
            global.getContainer().putI32(global, value);
        }

        @Override
        void putI64(LLVMGlobalVariable global, long value) {
            transferToNativeNoCopy(global);
            global.getContainer().putI64(global, value);
        }

        @Override
        void putFloat(LLVMGlobalVariable global, float value) {
            transferToNativeNoCopy(global);
            global.getContainer().putFloat(global, value);
        }

        @Override
        void putDouble(LLVMGlobalVariable global, double value) {
            transferToNativeNoCopy(global);
            global.getContainer().putDouble(global, value);
        }

        @Override
        boolean getI1(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getI1(global);
        }

        @Override
        byte getI8(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getI8(global);
        }

        @Override
        short getI16(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getI16(global);
        }

        @Override
        int getI32(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getI32(global);
        }

        @Override
        long getI64(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getI64(global);
        }

        @Override
        float getFloat(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getFloat(global);
        }

        @Override
        double getDouble(LLVMGlobalVariable global) {
            transferToNativeWithCopy(global);
            return global.getContainer().getDouble(global);
        }

    }

    static final class UninitializedManagedContainer extends AbstractManagedContainer {

        UninitializedManagedContainer(Type type, NativeAllocator address) {
            super(type, address);
        }

        @Override
        void destroy() {
            // nothing to do
        }

        @Override
        void copyToNative(LLVMAddress a) {
            // nothing to do
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedLLVMAddressContainer newContainer = new CachedLLVMAddressContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, 0);
            global.setContainer(newContainer);
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return LLVMAddress.nullPointer();
        }
    }

    static final class CachedLLVMAddressContainer extends AbstractManagedContainer {

        private final long address;
        private int changes;

        CachedLLVMAddressContainer(Type type, NativeAllocator allocator, LLVMAddress address, int changes) {
            this(type, allocator, address.getVal(), changes);
        }

        CachedLLVMAddressContainer(Type type, NativeAllocator allocator, long ptr, int changes) {
            super(type, allocator);
            this.address = ptr;
            this.changes = changes;
        }

        @Override
        void destroy() {
            // nothing to do
        }

        @Override
        void copyToNative(LLVMAddress a) {
            CompilerAsserts.neverPartOfCompilation();
            assert type instanceof PointerType;
            LLVMMemory.putAddress(a, address);
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            assert value != null;
            if (value.getVal() != address) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericLLVMAddressContainer newContainer = new GenericLLVMAddressContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedLLVMAddressContainer newContainer = new CachedLLVMAddressContainer(type, allocator, value.getVal(), changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return LLVMAddress.fromLong(address);
        }

    }

    static final class CachedManagedContainer extends AbstractManagedContainer {

        private final Object managedValue;
        private int changes;

        CachedManagedContainer(Type type, NativeAllocator allocator, Object managedValue, int changes) {
            super(type, allocator);
            this.managedValue = managedValue;
            this.changes = changes;
        }

        @Override
        void destroy() {
            // nothing to do
        }

        @Override
        void copyToNative(LLVMAddress address) {
            CompilerAsserts.neverPartOfCompilation();
            assert type instanceof PointerType;
            if (managedValue instanceof LLVMFunction) {
                LLVMMemory.putAddress(address, ((LLVMFunction) managedValue).getFunctionIndex());
            } else if (managedValue instanceof LLVMAddress) {
                LLVMMemory.putAddress(address, (LLVMAddress) managedValue);
            } else if (managedValue instanceof LLVMGlobalVariable) {
                LLVMMemory.putAddress(address, ((LLVMGlobalVariable) managedValue).getNativeLocation());
            } else if (managedValue instanceof TruffleObject || managedValue instanceof LLVMTruffleObject) {
                throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + managedValue);
            } else if (managedValue == null) {
                // nothing to do
            } else {
                throw new AssertionError("Unknown type: " + managedValue.getClass());
            }
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            assert !(managedValue instanceof LLVMAddress) && !value.equals(managedValue) : "there is a separate cached container class for LLVMAddress objects";
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                GenericLLVMAddressContainer newContainer = new GenericLLVMAddressContainer(type, allocator, value);
                global.setContainer(newContainer);
            } else {
                CachedLLVMAddressContainer newContainer = new CachedLLVMAddressContainer(type, allocator, value, changes);
                global.setContainer(newContainer);
            }
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            if (managedValue != value) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            if (value != managedValue) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            if (value != managedValue) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            if (value != managedValue) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            if (value != managedValue) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (++changes >= CACHING_TRIES_BEFORE_SWITCHING_TO_GENERIC) {
                    GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
                    global.setContainer(newContainer);
                } else {
                    CachedManagedContainer newContainer = new CachedManagedContainer(type, allocator, value, changes);
                    global.setContainer(newContainer);
                }
            }
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return managedValue;
        }

    }

    static final class GenericLLVMAddressContainer extends AbstractManagedContainer {

        private long address;

        GenericLLVMAddressContainer(Type type, NativeAllocator allocator, LLVMAddress address) {
            super(type, allocator);
            this.address = address.getVal();
        }

        @Override
        void destroy() {
            // nothing to do
        }

        @Override
        void copyToNative(LLVMAddress a) {
            CompilerAsserts.neverPartOfCompilation();
            LLVMMemory.putAddress(a, address);
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            this.address = value.getVal();
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
            global.setContainer(newContainer);
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
            global.setContainer(newContainer);
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
            global.setContainer(newContainer);
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
            global.setContainer(newContainer);
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            GenericManagedContainer newContainer = new GenericManagedContainer(type, allocator, value);
            global.setContainer(newContainer);
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return LLVMAddress.fromLong(address);
        }

    }

    /**
     * This class is the most generic implementation and must not do any specializations. Otherwise,
     * we would risk an endless deopt loop.
     */
    static final class GenericManagedContainer extends AbstractManagedContainer {

        private Object managedValue;

        GenericManagedContainer(Type type, NativeAllocator allocator, Object managedValue) {
            super(type, allocator);
            this.managedValue = managedValue;
        }

        @Override
        void destroy() {
            // nothing to do
        }

        @Override
        void copyToNative(LLVMAddress address) {
            CompilerAsserts.neverPartOfCompilation();
            assert type instanceof PointerType;
            if (managedValue instanceof LLVMFunction) {
                LLVMMemory.putAddress(address, ((LLVMFunction) managedValue).getFunctionIndex());
            } else if (managedValue instanceof LLVMAddress) {
                LLVMMemory.putAddress(address, (LLVMAddress) managedValue);
            } else if (managedValue instanceof LLVMGlobalVariable) {
                LLVMMemory.putAddress(address, ((LLVMGlobalVariable) managedValue).getNativeLocation());
            } else if (managedValue instanceof TruffleObject || managedValue instanceof LLVMTruffleObject) {
                throw new IllegalStateException("Cannot resolve address of a foreign TruffleObject: " + managedValue);
            } else if (managedValue == null) {
                // nothing to do
            } else {
                throw new AssertionError("Unknown type: " + managedValue.getClass());
            }
        }

        @Override
        void putAddress(LLVMGlobalVariable global, LLVMAddress value) {
            this.managedValue = value;
        }

        @Override
        void putTruffleObject(LLVMGlobalVariable global, TruffleObject value) {
            this.managedValue = value;
        }

        @Override
        void putLLVMTruffleObject(LLVMGlobalVariable global, LLVMTruffleObject value) {
            this.managedValue = value;
        }

        @Override
        void putFunction(LLVMGlobalVariable global, LLVMFunction value) {
            this.managedValue = value;
        }

        @Override
        void putBoxedPrimitive(LLVMGlobalVariable global, LLVMBoxedPrimitive value) {
            this.managedValue = value;
        }

        @Override
        Object get(LLVMGlobalVariable global) {
            return managedValue;
        }

        @Override
        void putGlobal(LLVMGlobalVariable global, LLVMGlobalVariable value) {
            this.managedValue = value;
        }
    }

}
