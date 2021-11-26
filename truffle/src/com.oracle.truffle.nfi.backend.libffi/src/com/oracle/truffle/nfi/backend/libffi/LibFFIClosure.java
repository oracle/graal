/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.libffi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
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
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNode.ConstArgumentNode;
import com.oracle.truffle.nfi.backend.libffi.ClosureArgumentNode.GetArgumentNode;
import com.oracle.truffle.nfi.backend.libffi.LibFFIClosureFactory.BufferRetClosureRootNodeGen;
import com.oracle.truffle.nfi.backend.libffi.LibFFIClosureFactory.CallClosureNodeGen;
import com.oracle.truffle.nfi.backend.libffi.LibFFIClosureFactory.UnboxStringNodeGen;
import com.oracle.truffle.nfi.backend.libffi.LibFFISignature.CachedSignatureInfo;
import com.oracle.truffle.nfi.backend.libffi.LibFFIType.CachedTypeInfo;
import com.oracle.truffle.nfi.backend.libffi.NativeArgumentBuffer.TypeTag;

@ExportLibrary(InteropLibrary.class)
final class LibFFIClosure implements TruffleObject {

    final ClosureNativePointer nativePointer;

    static LibFFIClosure newClosureWrapper(ClosureNativePointer nativePointer) {
        LibFFIClosure ret = new LibFFIClosure(nativePointer);
        ret.nativePointer.registerManagedRef(ret);
        return ret;
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

    abstract static class CachedClosureInfo {

        final CallTarget closureCallTarget;

        CachedClosureInfo(RootNode rootNode) {
            this.closureCallTarget = rootNode.getCallTarget();
        }
    }

    abstract static class MonomorphicClosureInfo extends CachedClosureInfo {

        private MonomorphicClosureInfo(RootNode rootNode) {
            super(rootNode);
        }

        abstract ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature);

        static MonomorphicClosureInfo create(CachedSignatureInfo signatureInfo, Object executable) {
            CompilerAsserts.neverPartOfCompilation();
            LibFFILanguage lang = LibFFILanguage.get(null);
            CachedTypeInfo retType = signatureInfo.getRetType();
            if (retType instanceof LibFFIType.ObjectType) {
                // special handling simple object return values
                return ObjectRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else if (retType instanceof LibFFIType.NullableType) {
                // special handling for simple object return values
                // additionally converts interop-null to Java-null
                return NullableRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else if (retType instanceof LibFFIType.StringType) {
                // special handling for simple string return values
                return StringRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else if (retType instanceof LibFFIType.VoidType) {
                // special handling for no return value
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else {
                // generic case: last argument is the return buffer
                return BufferRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            }
        }
    }

    abstract static class PolymorphicClosureInfo extends CachedClosureInfo {

        private PolymorphicClosureInfo(RootNode rootNode) {
            super(rootNode);
        }

        abstract ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver);

        static PolymorphicClosureInfo create(CachedSignatureInfo signatureInfo) {
            CompilerAsserts.neverPartOfCompilation();
            LibFFILanguage lang = LibFFILanguage.get(null);
            CachedTypeInfo retType = signatureInfo.getRetType();
            if (retType instanceof LibFFIType.ObjectType) {
                // special handling simple object return values
                return ObjectRetClosureRootNode.createInfo(lang, signatureInfo);
            } else if (retType instanceof LibFFIType.NullableType) {
                // special handling for simple object return values
                // additionally converts interop-null to Java-null
                return NullableRetClosureRootNode.createInfo(lang, signatureInfo);
            } else if (retType instanceof LibFFIType.StringType) {
                // special handling for simple string return values
                return StringRetClosureRootNode.createInfo(lang, signatureInfo);
            } else if (retType instanceof LibFFIType.VoidType) {
                // special handling for no return value
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo);
            } else {
                // generic case: last argument is the return buffer
                return BufferRetClosureRootNode.createInfo(lang, signatureInfo);
            }
        }
    }

    @NodeChild(value = "receiver", type = ClosureArgumentNode.class)
    abstract static class CallClosureNode extends Node {

        protected abstract Object execute(VirtualFrame frame);

        @Children final ClosureArgumentNode[] argNodes;

        CallClosureNode(CachedSignatureInfo signature) {
            CachedTypeInfo[] args = signature.getArgTypes();
            argNodes = new ClosureArgumentNode[args.length];
            for (int i = 0; i < args.length; i++) {
                ClosureArgumentNode rawArg = new GetArgumentNode(i);
                argNodes[i] = args[i].createClosureArgumentNode(rawArg);
            }
        }

        @Specialization(limit = "3")
        @ExplodeLoop
        Object doCall(VirtualFrame frame, Object receiver,
                        @CachedLibrary("receiver") InteropLibrary interop) {
            Object[] args = new Object[argNodes.length];
            for (int i = 0; i < argNodes.length; i++) {
                args[i] = argNodes[i].execute(frame);
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

        private final CachedTypeInfo retType;
        @Child SerializeArgumentNode serialize;

        private EncodeRetNode(CachedTypeInfo retType) {
            this.retType = retType;
            this.serialize = retType.createSerializeArgumentNode();
        }

        RetPatches execute(Object ret, NativeArgumentBuffer.Pointer retBuffer) {
            NativeArgumentBuffer nativeRetBuffer = new NativeArgumentBuffer.Direct(retBuffer, retType.objectCount);
            try {
                serialize.serialize(ret, nativeRetBuffer);
                if (nativeRetBuffer.getPatchCount() > 0) {
                    if (nativeRetBuffer.getPatchCount() == 1 && TypeTag.getTag(nativeRetBuffer.patches[0]) == TypeTag.KEEPALIVE) {
                        // special case for closure ret: we need to increment the refcount
                        Object keepalive = nativeRetBuffer.objects[0];
                        if (keepalive instanceof LibFFIClosure) {
                            ((LibFFIClosure) keepalive).nativePointer.addRef();
                            return null;
                        }
                    }
                    return new RetPatches(nativeRetBuffer.getPatchCount(), nativeRetBuffer.patches, nativeRetBuffer.objects);
                }
            } catch (UnsupportedTypeException ex) {
            }
            return null;
        }
    }

    @NodeChild(value = "retBuffer", type = ClosureArgumentNode.class)
    abstract static class BufferRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child EncodeRetNode encodeRet;

        // args of CallTarget: arg_0, arg_1, ..., arg_n, ret_buffer
        // return: RetPatches or null
        static MonomorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            ClosureArgumentNode retBuffer = new GetArgumentNode(signatureInfo.argTypes.length);
            BufferRetClosureRootNode rootNode = BufferRetClosureRootNodeGen.create(lang, signatureInfo, recvNode, retBuffer);
            return new MonomorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature) {
                    return ctx.allocateClosureBufferRet(signature, closureCallTarget, null);
                }
            };
        }

        // args of CallTarget: arg_0, arg_1, ..., arg_n, receiver, ret_buffer
        // return: RetPatches or null
        static PolymorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            ClosureArgumentNode retBuffer = new GetArgumentNode(signatureInfo.argTypes.length + 1);
            BufferRetClosureRootNode rootNode = BufferRetClosureRootNodeGen.create(lang, signatureInfo, recvNode, retBuffer);
            return new PolymorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver) {
                    return ctx.allocateClosureBufferRet(signature, closureCallTarget, receiver);
                }
            };
        }

        BufferRetClosureRootNode(LibFFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            encodeRet = new EncodeRetNode(signature.getRetType());
        }

        @Specialization
        public Object doBufferRet(VirtualFrame frame, NativeArgumentBuffer.Pointer retBuffer) {
            Object ret = callClosure.execute(frame);
            return encodeRet.execute(ret, retBuffer);
        }
    }

    private static final class VoidRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;

        // args of CallTarget: arg_0, arg_1, ..., arg_n
        // return: null
        static MonomorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            VoidRetClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return new MonomorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature) {
                    return ctx.allocateClosureVoidRet(signature, closureCallTarget, null);
                }
            };
        }

        // args of CallTarget: arg_0, arg_1, ..., arg_n, receiver
        // return: null
        static PolymorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            VoidRetClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return new PolymorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver) {
                    return ctx.allocateClosureVoidRet(signature, closureCallTarget, receiver);
                }
            };
        }

        private VoidRetClosureRootNode(LibFFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            callClosure.execute(frame);
            return null;
        }
    }

    private static final class ObjectRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;

        // args of CallTarget: arg_0, arg_1, ..., arg_n
        // return: a Java object
        static MonomorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            ObjectRetClosureRootNode rootNode = new ObjectRetClosureRootNode(lang, signatureInfo, recvNode);
            return new MonomorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature) {
                    return ctx.allocateClosureObjectRet(signature, closureCallTarget, null);
                }
            };
        }

        // args of CallTarget: arg_0, arg_1, ..., arg_n, receiver
        // return: a Java object (or null if nullable==true)
        static PolymorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            ObjectRetClosureRootNode rootNode = new ObjectRetClosureRootNode(lang, signatureInfo, recvNode);
            return new PolymorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver) {
                    return ctx.allocateClosureObjectRet(signature, closureCallTarget, receiver);
                }
            };
        }

        private ObjectRetClosureRootNode(LibFFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callClosure.execute(frame);
        }
    }

    private static final class NullableRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child private InteropLibrary interopLibrary;

        // args of CallTarget: arg_0, arg_1, ..., arg_n
        // return: a Java object or null
        static MonomorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            NullableRetClosureRootNode rootNode = new NullableRetClosureRootNode(lang, signatureInfo, recvNode);
            return new MonomorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature) {
                    return ctx.allocateClosureObjectRet(signature, closureCallTarget, null);
                }
            };
        }

        // args of CallTarget: arg_0, arg_1, ..., arg_n, receiver
        // return: a Java object (or null if nullable==true)
        static PolymorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            NullableRetClosureRootNode rootNode = new NullableRetClosureRootNode(lang, signatureInfo, recvNode);
            return new PolymorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver) {
                    return ctx.allocateClosureObjectRet(signature, closureCallTarget, receiver);
                }
            };
        }

        private NullableRetClosureRootNode(LibFFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
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
        public int position() {
            return 0;
        }

        @Override
        public void position(int newPosition) {
            assert newPosition == 0;
        }

        @Override
        public void putPointer(long ptr, int size) {
            assert ret == null;
            ret = new NativeString(ptr);
        }

        @Override
        public void putObject(TypeTag tag, Object o, int size) {
            assert ret == null;
            switch (tag) {
                case STRING:
                    ret = o;
                    break;
                case KEEPALIVE:
                    // nothing to do
                    // putPointer will be called with the real value later
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere(tag.name());
            }
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

        @Child SerializeArgumentNode serialize;

        UnboxStringNode(CachedTypeInfo strType) {
            this.serialize = strType.createSerializeArgumentNode();
            assert strType instanceof LibFFIType.StringType;
        }

        protected abstract Object execute(Object obj) throws UnsupportedTypeException;

        @Specialization
        protected Object nativeString(Object str) throws UnsupportedTypeException {
            RetStringBuffer retBuffer = new RetStringBuffer();
            CompilerDirectives.ensureVirtualized(retBuffer);
            serialize.serialize(str, retBuffer);
            return retBuffer.ret;
        }
    }

    private static final class StringRetClosureRootNode extends RootNode {

        @Child private CallClosureNode callClosure;
        @Child private UnboxStringNode unboxString;

        // args of CallTarget: arg_0, arg_1, ..., arg_n
        // return: a Java String
        static MonomorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            StringRetClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return new MonomorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature) {
                    return ctx.allocateClosureStringRet(signature, closureCallTarget, null);
                }
            };
        }

        // args of CallTarget: arg_0, arg_1, ..., arg_n, receiver
        // return: a Java String
        static PolymorphicClosureInfo createInfo(LibFFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            StringRetClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return new PolymorphicClosureInfo(rootNode) {

                @Override
                ClosureNativePointer allocateClosure(LibFFIContext ctx, LibFFISignature signature, Object receiver) {
                    return ctx.allocateClosureStringRet(signature, closureCallTarget, receiver);
                }
            };
        }

        private StringRetClosureRootNode(LibFFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            unboxString = UnboxStringNodeGen.create(signature.getRetType());
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame);
            try {
                return unboxString.execute(ret);
            } catch (UnsupportedTypeException ex) {
                return null;
            }
        }
    }
}
