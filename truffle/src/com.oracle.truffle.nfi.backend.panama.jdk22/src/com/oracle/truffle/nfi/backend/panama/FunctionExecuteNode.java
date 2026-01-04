/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.nfi.backend.panama.PanamaSignature.CachedSignatureInfo;

@GenerateUncached
@ImportStatic(PanamaNFILanguage.class)
@GenerateAOT
@GenerateInline(false)
abstract class FunctionExecuteNode extends Node {

    public abstract Object execute(long receiver, PanamaSignature signature, Object[] args) throws ArityException, UnsupportedTypeException;

    @Specialization(guards = "signature.signatureInfo == cachedInfo", limit = "3")
    @GenerateAOT.Exclude
    protected Object cachedSignature(long receiver, PanamaSignature signature, Object[] args,
                    @Cached("signature.signatureInfo") @SuppressWarnings("unused") CachedSignatureInfo cachedInfo,
                    @Cached("createCachedSignatureCall(cachedInfo)") DirectCallNode execute) {
        try {
            return execute.call(receiver, args, signature);
        } finally {
            /*
             * Ensure that the GC can not free the objects as long as they might still be in use by
             * the native code (maybe indirectly via an embedded native pointer).
             */
            Reference.reachabilityFence(args);
        }
    }

    protected static DirectCallNode createCachedSignatureCall(CachedSignatureInfo signature) {
        DirectCallNode callNode = DirectCallNode.create(signature.callTarget);
        callNode.forceInlining();
        return callNode;
    }

    @Specialization(replaces = "cachedSignature")
    static Object genericExecute(long receiver, PanamaSignature signature, Object[] args,
                    @Cached IndirectCallNode execute) {
        try {
            return execute.call(signature.signatureInfo.callTarget, receiver, args, signature);
        } finally {
            /*
             * Ensure that the GC can not free the objects as long as they might still be in use by
             * the native code (maybe indirectly via an embedded native pointer).
             */
            Reference.reachabilityFence(args);
        }
    }

    abstract static class SignatureExecuteNode extends RootNode {

        final CachedSignatureInfo signatureInfo;
        @Children ArgumentNode[] argNodes;
        @Children PostCallArgumentNode[] postCallArgNodes;
        final boolean needsArena;

        SignatureExecuteNode(PanamaNFILanguage language, CachedSignatureInfo signatureInfo) {
            super(language);
            this.signatureInfo = signatureInfo;

            PanamaType[] argTypes = signatureInfo.getArgTypes();
            this.argNodes = new ArgumentNode[argTypes.length];
            boolean postCall = false;
            boolean arenaNeeded = false;
            for (int i = 0; i < argTypes.length; i++) {
                argNodes[i] = argTypes[i].createArgumentNode();
                postCall |= argTypes[i].needsPostCallProcessing();
                arenaNeeded |= argTypes[i].needsArena();
            }
            this.needsArena = arenaNeeded;
            if (postCall) {
                this.postCallArgNodes = new PostCallArgumentNode[argNodes.length];
                for (int i = 0; i < argNodes.length; i++) {
                    postCallArgNodes[i] = argTypes[i].createPostCallArgumentNode();
                }
            }
        }

        @Override
        public abstract Object execute(VirtualFrame frame);

        MemorySegment getAddress(VirtualFrame frame) {
            return MemorySegment.ofAddress((long) frame.getArguments()[0]);
        }

        Object[] getArgs(VirtualFrame frame) {
            return (Object[]) frame.getArguments()[1];
        }

        PanamaSignature getSig(VirtualFrame frame) {
            return (PanamaSignature) frame.getArguments()[2];
        }

        @Specialization
        @ExplodeLoop
        public Object doGeneric(VirtualFrame frame) {
            Object[] args = getArgs(frame);
            MemorySegment address = getAddress(frame);
            PanamaSignature signature = getSig(frame);

            if (args.length != argNodes.length) {
                throw silenceException(RuntimeException.class, ArityException.create(argNodes.length, argNodes.length, args.length));
            }

            Object[] convertedArgs = postCallArgNodes == null ? args : new Object[args.length];
            Arena arena = needsArena ? Arena.ofConfined() : null;
            try {
                try {
                    PanamaType[] types = signatureInfo.getArgTypes();
                    assert argNodes.length == types.length;

                    for (int i = 0; i < argNodes.length; i++) {
                        convertedArgs[i] = argNodes[i].execute(arena, args[i]);
                    }
                } catch (UnsupportedTypeException ex) {
                    throw silenceException(RuntimeException.class, ex);
                }
                try {
                    return signatureInfo.execute(signature, convertedArgs, address, this);
                } finally {
                    if (postCallArgNodes != null) {
                        for (int i = 0; i < postCallArgNodes.length; i++) {
                            if (postCallArgNodes[i] != null) {
                                postCallArgNodes[i].execute(args[i], convertedArgs[i]);
                            }
                        }
                    }
                }

            } finally {
                if (needsArena) {
                    arena.close();
                }
            }
        }

        @SuppressWarnings({"unchecked", "unused"})
        static <E extends Exception> RuntimeException silenceException(Class<E> type, Exception ex) throws E {
            throw (E) ex;
        }
    }
}
