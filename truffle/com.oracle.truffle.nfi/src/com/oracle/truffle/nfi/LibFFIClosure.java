/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.LibFFIClosureFactory.UnboxNullNodeGen;
import java.nio.ByteBuffer;

class LibFFIClosure {

    final ClosureNativePointer nativePointer;

    static LibFFIClosure create(LibFFISignature signature, TruffleObject executable) {
        LibFFIClosure ret = new LibFFIClosure(signature, executable);
        NativeAllocation.registerNativeAllocation(ret, ret.nativePointer);
        return ret;
    }

    private LibFFIClosure(LibFFISignature signature, TruffleObject executable) {
        Message message = Message.createExecute(signature.getArgTypes().length);

        LibFFIType retType = signature.getRetType();
        if (retType instanceof LibFFIType.StringType || retType instanceof LibFFIType.ObjectType) {
            // shortcut for simple object return values
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable, message));
            this.nativePointer = allocateClosureObjectRet(signature, executeCallTarget);
        } else if (retType instanceof LibFFIType.VoidType) {
            // special handling for no return value
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new ObjectRetClosureRootNode(signature, executable, message));
            this.nativePointer = allocateClosureVoidRet(signature, executeCallTarget);
        } else {
            // generic case: last argument is the return buffer
            CallTarget executeCallTarget = Truffle.getRuntime().createCallTarget(new BufferRetClosureRootNode(signature, executable, message));
            this.nativePointer = allocateClosureBufferRet(signature, executeCallTarget);
        }
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

    private static class CallClosureNode extends Node {

        private final TruffleObject receiver;
        @Child Node messageNode;

        @Children final ClosureArgumentNode[] argNodes;

        private CallClosureNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            this.receiver = receiver;
            this.messageNode = message.createNode();

            LibFFIType[] args = signature.getArgTypes();
            argNodes = new ClosureArgumentNode[args.length];
            for (int i = 0; i < args.length; i++) {
                argNodes[i] = args[i].createClosureArgumentNode();
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
                throw new IllegalStateException(ex);
            }
        }
    }

    private static class EncodeRetNode extends Node {

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

    private static class BufferRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child EncodeRetNode encodeRet;

        private BufferRetClosureRootNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            super(NFILanguage.class, null, null);
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

    static abstract class UnboxNullNode extends Node {

        protected abstract Object execute(Object obj);

        @Specialization
        protected Object unboxTruffleObject(TruffleObject obj, @Cached("createIsNull()") Node isNull) {
            if (ForeignAccess.sendIsNull(isNull, obj)) {
                return null;
            } else {
                return obj;
            }
        }

        @Specialization
        protected Object unboxGeneric(Object obj) {
            return obj;
        }

        protected static Node createIsNull() {
            return Message.IS_NULL.createNode();
        }
    }

    private static class ObjectRetClosureRootNode extends RootNode {

        @Child CallClosureNode callClosure;
        @Child UnboxNullNode unboxNull;

        private ObjectRetClosureRootNode(LibFFISignature signature, TruffleObject receiver, Message message) {
            super(NFILanguage.class, null, null);
            callClosure = new CallClosureNode(signature, receiver, message);
            unboxNull = UnboxNullNodeGen.create();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object ret = callClosure.execute(frame.getArguments());
            return unboxNull.execute(ret);
        }
    }

    private static native ClosureNativePointer allocateClosureObjectRet(LibFFISignature signature, CallTarget callTarget);

    private static native ClosureNativePointer allocateClosureBufferRet(LibFFISignature signature, CallTarget callTarget);

    private static native ClosureNativePointer allocateClosureVoidRet(LibFFISignature signature, CallTarget callTarget);
}
