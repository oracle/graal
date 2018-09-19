/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.impl.LibFFIClosureFactory.UnboxStringNodeGen;
import java.nio.ByteBuffer;

final class LibFFIClosure implements TruffleObject {

    final ClosureNativePointer nativePointer;

    static LibFFIClosure createSlowPath(LibFFISignature signature, TruffleObject executable) {
        CompilerAsserts.neverPartOfCompilation();
        NFIContext ctx = NFILanguageImpl.getCurrentContextReference().get();
        return create(ctx, signature, executable);
    }

    static LibFFIClosure create(NFIContext context, LibFFISignature signature, TruffleObject executable) {
        CompilerAsserts.neverPartOfCompilation();
        LibFFIClosure ret = new LibFFIClosure(context, signature, executable);
        ret.nativePointer.registerManagedRef(ret);
        return ret;
    }

    static LibFFIClosure newClosureWrapper(ClosureNativePointer nativePointer) {
        LibFFIClosure ret = new LibFFIClosure(nativePointer);
        ret.nativePointer.registerManagedRef(ret);
        return ret;
    }

    private LibFFIClosure(NFIContext context, LibFFISignature signature, TruffleObject executable) {
        Message message = Message.EXECUTE;

        LibFFIType retType = signature.getRetType();
        if (retType instanceof LibFFIType.ObjectType) {
            // shortcut for simple object return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable, message));
            this.nativePointer = context.allocateClosureObjectRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.StringType) {
            // shortcut for simple string return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new StringRetClosureRootNode(signature, executable, message));
            this.nativePointer = context.allocateClosureStringRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.VoidType) {
            // special handling for no return value
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable, message));
            this.nativePointer = context.allocateClosureVoidRet(signature, executeCallTarget);
        } else {
            // generic case: last argument is the return buffer
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new BufferRetClosureRootNode(signature, executable, message));
            this.nativePointer = context.allocateClosureBufferRet(signature, executeCallTarget);
        }
    }

    private LibFFIClosure(ClosureNativePointer nativePointer) {
        this.nativePointer = nativePointer;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return LibFFIClosureMessageResolutionForeign.ACCESS;
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

        private final TruffleObject receiver;
        @Child Node messageNode;

        @Children final ClosureArgumentNode[] argNodes;

        private CallClosureNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            this.receiver = receiver;
            this.messageNode = message.createNode();

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
                return ForeignAccess.send(messageNode, receiver, args);
            } catch (InteropException ex) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(ex);
            }
        }
    }

    private static final class EncodeRetNode extends Node {

        @Child SerializeArgumentNode retNode;
        private final int retObjectCount;

        private EncodeRetNode(LibFFIType retType) {
            retNode = retType.createClosureReturnNode();
            retObjectCount = retType.objectCount;
        }

        RetPatches execute(Object ret, ByteBuffer retBuffer) {
            NativeArgumentBuffer nativeRetBuffer = new NativeArgumentBuffer.Direct(retBuffer, retObjectCount);
            retNode.execute(nativeRetBuffer, ret);
            if (nativeRetBuffer.getPatchCount() > 0) {
                return new RetPatches(nativeRetBuffer.getPatchCount(), nativeRetBuffer.patches, nativeRetBuffer.objects);
            } else {
                return null;
            }
        }
    }

    private static final class BufferRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child EncodeRetNode encodeRet;

        private BufferRetClosureRootNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver, message);
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

        private ObjectRetClosureRootNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver, message);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return callClosure.execute(frame.getArguments());
        }
    }

    abstract static class UnboxStringNode extends Node {

        protected abstract Object execute(Object obj);

        @Specialization
        protected Object nativeString(NativeString str) {
            return str;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "checkIsNull(isNull, str)")
        protected Object unboxNull(TruffleObject str, @Cached("createIsNull()") Node isNull) {
            return null;
        }

        @Specialization
        protected Object javaString(String obj) {
            return obj;
        }

        @Specialization(guards = "checkNeedUnbox(str)")
        protected Object unboxBoxed(TruffleObject str, @Cached("createUnbox()") Node unbox, @Cached("createRecursive()") UnboxStringNode recursive) {
            try {
                Object unboxed = ForeignAccess.sendUnbox(unbox, str);
                return recursive.execute(unboxed);
            } catch (UnsupportedMessageException ex) {
                CompilerDirectives.transferToInterpreter();
                throw UnsupportedTypeException.raise(ex, new Object[]{str});
            }
        }

        protected static Node createIsNull() {
            return Message.IS_NULL.createNode();
        }

        protected static boolean checkIsNull(Node isNullNode, TruffleObject obj) {
            return ForeignAccess.sendIsNull(isNullNode, obj);
        }

        protected static boolean checkNeedUnbox(TruffleObject obj) {
            return !(obj instanceof NativeString);
        }

        protected static Node createUnbox() {
            return Message.UNBOX.createNode();
        }

        protected static UnboxStringNode createRecursive() {
            return UnboxStringNodeGen.create();
        }
    }

    private static final class StringRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child UnboxStringNode unboxString;

        private StringRetClosureRootNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            super(null);
            callClosure = new CallClosureNode(signature, receiver, message);
            unboxString = UnboxStringNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame.getArguments());
            return unboxString.execute(ret);
        }
    }
}
