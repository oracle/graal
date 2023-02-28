/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

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
import com.oracle.truffle.nfi.backend.panama.PanamaSignature.CachedSignatureInfo;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNode.GetArgumentNode;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNode.ConstArgumentNode;
import com.oracle.truffle.nfi.backend.panama.PanamaClosureFactory.CallClosureNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

@ExportLibrary(InteropLibrary.class)
final class PanamaClosure implements TruffleObject {
    @SuppressWarnings("preview") final MemorySegment symbol;

    PanamaClosure(@SuppressWarnings("preview") MemorySegment symbol) {
        this.symbol = symbol;
    }

    @ExportMessage
    boolean isPointer() {
        return true;
    }

    @ExportMessage
    long asPointer() {
        return symbol.address();
    }

    abstract static class CachedClosureInfo {
        final CallTarget closureCallTarget;
        final MethodHandle handle;

        CachedClosureInfo(RootNode rootNode, MethodHandle handle) {
            this.closureCallTarget = rootNode.getCallTarget();
            this.handle = handle;
        }
    }

    static class MonomorphicClosureInfo extends CachedClosureInfo {

        MonomorphicClosureInfo(RootNode rootNode, MethodHandle handle) {
            super(rootNode, handle);
        }

        static MonomorphicClosureInfo create(CachedSignatureInfo signatureInfo, Object executable) {
            CompilerAsserts.neverPartOfCompilation();
            PanamaNFILanguage lang = PanamaNFILanguage.get(null);
            PanamaType retType = signatureInfo.getRetType();
            if (retType.type == NativeSimpleType.STRING) {
                return StringRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else if (retType.type == NativeSimpleType.VOID) {
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            } else {
                return GenericRetClosureRootNode.createInfo(lang, signatureInfo, executable);
            }
        }
    }

    static class PolymorphicClosureInfo extends CachedClosureInfo {

        PolymorphicClosureInfo(RootNode rootNode, MethodHandle handle) {
            super(rootNode, handle);
        }

        static PolymorphicClosureInfo create(CachedSignatureInfo signatureInfo) {
            CompilerAsserts.neverPartOfCompilation();
            PanamaNFILanguage lang = PanamaNFILanguage.get(null);
            PanamaType retType = signatureInfo.getRetType();
            if (retType.type == NativeSimpleType.STRING) {
                return StringRetClosureRootNode.createInfo(lang, signatureInfo);
            } else if (retType.type == NativeSimpleType.VOID) {
                return VoidRetClosureRootNode.createInfo(lang, signatureInfo);
            } else {
                return GenericRetClosureRootNode.createInfo(lang, signatureInfo);
            }
        }
    }

    abstract static class PanamaClosureRootNode extends RootNode {

        // Object closure(Object receiver, Object[] args)
        static final MethodType METHOD_TYPE = MethodType.methodType(Object.class, Object.class, Object[].class);

        @Child InteropLibrary interop;

        PanamaClosureRootNode(PanamaNFILanguage language) {
            super(language);
            this.interop = InteropLibrary.getFactory().createDispatched(3);
        }

        static final MethodHandle handle_CallTarget_call;

        MonomorphicClosureInfo createMonomorphicClosureInfo() {
            CompilerAsserts.neverPartOfCompilation();
            CallTarget upcallTarget = getCallTarget();
            MethodHandle handle = handle_CallTarget_call.bindTo(upcallTarget).asCollector(Object[].class, 2).asType(METHOD_TYPE).asVarargsCollector(Object[].class);
            return new MonomorphicClosureInfo(this, handle);
        }

        PolymorphicClosureInfo createPolymorphicClosureInfo() {
            CompilerAsserts.neverPartOfCompilation();
            CallTarget upcallTarget = getCallTarget();
            MethodHandle handle = handle_CallTarget_call.bindTo(upcallTarget).asCollector(Object[].class, 2).asType(METHOD_TYPE).asVarargsCollector(Object[].class);
            return new PolymorphicClosureInfo(this, handle);
        }

        static {
            MethodType callType = MethodType.methodType(Object.class, Object[].class);
            try {
                handle_CallTarget_call = MethodHandles.lookup().findVirtual(CallTarget.class, "call", callType);
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    @NodeChild(value = "receiver", type = ClosureArgumentNode.class)
    abstract static class CallClosureNode extends Node {

        protected abstract Object execute(VirtualFrame frame);

        @Children final ClosureArgumentNode[] argNodes;

        CallClosureNode(CachedSignatureInfo signature) {
            PanamaType[] args = signature.getArgTypes();
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
                ErrorContext ctx = PanamaNFILanguage.get(null).errorContext.get();
                int errnoMirror = ctx.getErrno();
                if (ctx.nativeErrnoSet()) {
                    ctx.setErrno(ctx.getNativeErrno());
                }
                Object result = interop.execute(receiver, args);

                ctx.setNativeErrno(ctx.getErrno());
                ctx.setErrno(errnoMirror);

                return result;
            } catch (InteropException ex) {
                throw CompilerDirectives.shouldNotReachHere(ex);
            }
        }
    }

    private static final class StringRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;
        @Child private InteropLibrary interopLibrary;
        @Child ArgumentNode toJavaRet;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            PanamaClosureRootNode rootNode = new StringRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private StringRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            toJavaRet = signature.retType.createArgumentNode();
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                Object ret = callClosure.execute(frame);
                if (interopLibrary.isNull(ret)) {
                    return null;
                }
                return toJavaRet.execute(ret);
            } catch (Throwable t) {
                PanamaNFILanguage.get(this).errorContext.get().setThrowable(t);
                try {
                    return toJavaRet.execute("");
                } catch (UnsupportedTypeException ex) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }

    private static final class VoidRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signatureInfo) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signatureInfo.argTypes.length);
            PanamaClosureRootNode rootNode = new VoidRetClosureRootNode(lang, signatureInfo, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private VoidRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                callClosure.execute(frame);
            } catch (Throwable t) {
                PanamaNFILanguage.get(this).errorContext.get().setThrowable(t);
            }
            return null;
        }
    }

    @NodeChild(type = ClosureArgumentNode.class)
    public static final class GenericRetClosureRootNode extends PanamaClosureRootNode {

        @Child CallClosureNode callClosure;
        @Child ArgumentNode toJavaRet;
        @Child private InteropLibrary interopLibrary;

        static MonomorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signature, Object receiver) {
            ClosureArgumentNode recvNode = new ConstArgumentNode(receiver);
            PanamaClosureRootNode rootNode = new GenericRetClosureRootNode(lang, signature, recvNode);
            return rootNode.createMonomorphicClosureInfo();
        }

        static PolymorphicClosureInfo createInfo(PanamaNFILanguage lang, CachedSignatureInfo signature) {
            ClosureArgumentNode recvNode = new GetArgumentNode(signature.argTypes.length);
            PanamaClosureRootNode rootNode = new GenericRetClosureRootNode(lang, signature, recvNode);
            return rootNode.createPolymorphicClosureInfo();
        }

        private GenericRetClosureRootNode(PanamaNFILanguage lang, CachedSignatureInfo signature, ClosureArgumentNode receiver) {
            super(lang);
            callClosure = CallClosureNodeGen.create(signature, receiver);
            toJavaRet = signature.retType.createArgumentNode();
            interopLibrary = InteropLibrary.getFactory().createDispatched(4);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                Object ret = callClosure.execute(frame);
                if (interopLibrary.isNull(ret)) {
                    return NativePointer.NULL.asPointer();
                }
                return toJavaRet.execute(ret);
            } catch (Throwable t) {
                PanamaNFILanguage.get(this).errorContext.get().setThrowable(t);
                try {
                    return toJavaRet.execute(0);
                } catch (UnsupportedTypeException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }
    }
}
