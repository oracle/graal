/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.impl;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.impl.LibFFIClosureFactory.UnboxStringNodeGen;

@ExportLibrary(InteropLibrary.class)
final class LibFFIClosure implements TruffleObject {

    final ClosureNativePointer nativePointer;

    static LibFFIClosure create(LibFFISignature signature, Object executable, ContextReference<NFIContext> ctxRef) {
        CompilerAsserts.neverPartOfCompilation();
        LibFFIClosure ret = new LibFFIClosure(ctxRef.get(), signature, executable);
        ret.nativePointer.registerManagedRef(ret);
        return ret;
    }

    static LibFFIClosure newClosureWrapper(ClosureNativePointer nativePointer) {
        LibFFIClosure ret = new LibFFIClosure(nativePointer);
        ret.nativePointer.registerManagedRef(ret);
        return ret;
    }

    private LibFFIClosure(NFIContext context, LibFFISignature signature, Object executable) {
        LibFFIType retType = signature.getRetType();
        if (retType instanceof LibFFIType.ObjectType) {
            // shortcut for simple object return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable));
            this.nativePointer = context.allocateClosureObjectRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.NullableType) {
            // shortcut for simple object return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new NullableRetClosureRootNode(signature, executable));
            this.nativePointer = context.allocateClosureObjectRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.StringType) {
            // shortcut for simple string return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new StringRetClosureRootNode(signature, executable));
            this.nativePointer = context.allocateClosureStringRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.VoidType) {
            // special handling for no return value
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable));
            this.nativePointer = context.allocateClosureVoidRet(signature, executeCallTarget);
        } else {
            // generic case: last argument is the return buffer
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new BufferRetClosureRootNode(signature, executable));
            this.nativePointer = context.allocateClosureBufferRet(signature, executeCallTarget);
        }
    }

    private LibFFIClosure(ClosureNativePointer nativePointer) {
        this.nativePointer = nativePointer;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    long asPointer() {
        return nativePointer.getCodePointer();
    }

    @ExportMessage
    LibFFIClosure toNative() {
        return this;
    }

    static final class RetPatches {

        final int count;
        final int[] patches;
        final Object[] objects;

        RetPatches(int count, int[] patches, Object[] objects) {
            this.count = count;
            this.patches = patches;
            this.objects = objects;
        }
    }

    private static final class CallClosureNode extends Node {

        private final Object receiver;
        @Child InteropLibrary interop;

        @Children final ClosureArgumentNode[] argNodes;

        private CallClosureNode(LibFFISignature signature, Object receiver) {
            this.receiver = receiver;
            this.interop = InteropLibrary.getFactory().create(receiver);

            LibFFIType[] args = signature.getArgTypes();
            argNodes = new ClosureArgumentNode[signature.getRealArgCount()];
            int nodeIdx = 0;
            for (LibFFIType arg : args) {
                if (!arg.injectedArgument) {
                    argNodes[nodeIdx++] = arg.createClosureArgumentNode();
                }
            }
        }

        @ExplodeLoop
        Object execute(Object[] argBuffers) {
            Object[] args = new Object[argNodes.length];
            for (int i = 0; i < argNodes.length; i++) {
                args[i] = argNodes[i].execute(argBuffers[i]);
            }

            try {
                return interop.execute(receiver, args);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class EncodeRetNode extends Node {

        private final LibFFIType retType;
        @Child NativeArgumentLibrary nativeArguments;

        private EncodeRetNode(LibFFIType retType) {
            this.retType = retType.overrideClosureRetType();
            this.nativeArguments = NativeArgumentLibrary.getFactory().create(this.retType);
        }

        RetPatches execute(Object ret, ByteBuffer retBuffer) {
            NativeArgumentBuffer nativeRetBuffer = new NativeArgumentBuffer.Direct(retBuffer, retType.objectCount);
            try {
                nativeArguments.serialize(retType, nativeRetBuffer, ret);
                if (nativeRetBuffer.getPatchCount() > 0) {
                    return new RetPatches(nativeRetBuffer.getPatchCount(), nativeRetBuffer.patches, nativeRetBuffer.objects);
                }
            } catch (UnsupportedTypeException ex) {
            }
            return null;
        }
    }

    private static final class BufferRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child EncodeRetNode encodeRet;

        private BufferRetClosureRootNode(LibFFISignature signature, Object receiver) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver);
            encodeRet = new EncodeRetNode(signature.getRetType());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            ByteBuffer retBuffer = (ByteBuffer) frame.getArguments()[frame.getArguments().length - 1];
            Object ret = callClosure.execute(frame.getArguments());
            return encodeRet.execute(ret, retBuffer);
        }
    }

    private static final class ObjectRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;

        private ObjectRetClosureRootNode(LibFFISignature signature, Object receiver) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callClosure.execute(frame.getArguments());
        }
    }

    private static final class NullableRetClosureRootNode extends RootNode {

        @Child private CallClosureNode callClosure;
        @Child private InteropLibrary interopLibrary;

        private NullableRetClosureRootNode(LibFFISignature signature, Object receiver) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver);
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame.getArguments());
            if (interopLibrary.isNull(ret)) {
                return null;
            }
            return ret;
        }
    }

    static final class RetStringBuffer extends NativeArgumentBuffer {

        Object ret;

        RetStringBuffer() {
            super(0);
        }

        @Override
        protected ByteBuffer getPrimBuffer() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putPointer(long ptr, int size) {
            ret = new NativeString(ptr);
        }

        @Override
        public void putObject(TypeTag tag, Object o, int size) {
            assert tag == TypeTag.STRING;
            ret = o;
        }

        @Override
        public byte getInt8() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putInt8(byte b) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public short getInt16() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putInt16(short s) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public int getInt32() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putInt32(int i) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public long getInt64() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putInt64(long l) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public float getFloat() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putFloat(float f) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public double getDouble() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }

        @Override
        public void putDouble(double d) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("should not reach here");
        }
    }

    abstract static class UnboxStringNode extends Node {

        protected abstract Object execute(Object obj) throws UnsupportedTypeException;

        @Specialization(limit = "3")
        protected Object nativeString(Object str,
                        @CachedLibrary("str") SerializeArgumentLibrary serialize) throws UnsupportedTypeException {
            RetStringBuffer retBuffer = new RetStringBuffer();
            CompilerDirectives.ensureVirtualized(retBuffer);
            serialize.putString(str, retBuffer, 0);
            return retBuffer.ret;
        }
    }

    private static final class StringRetClosureRootNode extends RootNode {

        @Child private CallClosureNode callClosure;
        @Child private UnboxStringNode unboxString;

        private StringRetClosureRootNode(LibFFISignature signature, Object receiver) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver);
            unboxString = UnboxStringNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame.getArguments());
            try {
                return unboxString.execute(ret);
            } catch (UnsupportedTypeException ex) {
                return null;
            }
        }
    }
}
