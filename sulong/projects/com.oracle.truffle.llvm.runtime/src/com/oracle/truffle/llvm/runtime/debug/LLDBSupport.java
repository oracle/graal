/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMObjectAccess;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLDBSupport {

    private final LLVMLanguage language;
    private final EconomicMap<Type, CallTarget> loadFunctionCache;

    private LLVMDebugValue.Builder cachedDebugValueBuilder;
    private LLVMDebugValue.Builder cachedDebugDeclarationBuilder;

    public LLDBSupport(LLVMLanguage language) {
        this.language = language;
        this.loadFunctionCache = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
    }

    private static final class LoadRootNode extends RootNode {

        @Child LLVMLoadNode loadNode;

        LoadRootNode(LLDBSupport dbSupport, Type loadType) {
            super(dbSupport.language);
            loadNode = dbSupport.getNodeFactory().createLoad(loadType, null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            LLVMPointer offsetPointer = LLVMPointer.cast(frame.getArguments()[0]);
            return loadNode.executeWithTarget(offsetPointer);
        }
    }

    @TruffleBoundary
    public CallTarget getLoadFunction(Type loadType) {
        CallTarget ret = loadFunctionCache.get(loadType);
        if (ret == null) {
            ret = Truffle.getRuntime().createCallTarget(new LoadRootNode(this, loadType));
            loadFunctionCache.put(loadType, ret);
        }
        return ret;
    }

    private NodeFactory getNodeFactory() {
        return language.getNodeFactory();
    }

    public static boolean pointsToObjectAccess(LLVMPointer pointer) {
        if (!LLVMManagedPointer.isInstance(pointer)) {
            return false;
        }

        final LLVMManagedPointer managedPointer = LLVMManagedPointer.cast(pointer);
        final Object target = managedPointer.getObject();
        return target instanceof DynamicObject && ((DynamicObject) target).getShape().getObjectType() instanceof LLVMObjectAccess;
    }

    private static final class BuilderRootNode extends RootNode {

        @Child LLVMDebugValue.Builder builder;

        BuilderRootNode(LLVMDebugValue.Builder builder, LLVMLanguage language) {
            super(language);
            this.builder = builder;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return builder.build(frame.getArguments()[0]);
        }
    }

    private static final class WrappedBuilder implements LLVMDebugValue.Builder {

        private final CallTarget callTarget;

        WrappedBuilder(LLVMDebugValue.Builder inner, LLVMLanguage language) {
            RootNode root = new BuilderRootNode(inner, language);
            this.callTarget = Truffle.getRuntime().createCallTarget(root);
        }

        @Override
        public LLVMDebugValue build(Object irValue) {
            return (LLVMDebugValue) callTarget.call(irValue);
        }
    }

    public LLVMDebugValue.Builder createDebugValueBuilder() {
        if (cachedDebugValueBuilder == null) {
            cachedDebugValueBuilder = new WrappedBuilder(getNodeFactory().createDebugValueBuilder(), language);
        }
        return cachedDebugValueBuilder;
    }

    public LLVMDebugValue.Builder createDebugDeclarationBuilder() {
        if (cachedDebugDeclarationBuilder == null) {
            cachedDebugDeclarationBuilder = new WrappedBuilder(getNodeFactory().createDebugDeclarationBuilder(), language);
        }
        return cachedDebugDeclarationBuilder;
    }

    private static boolean isByteAligned(long bits) {
        return (bits & (Byte.SIZE - 1)) == 0;
    }

    public static String toSizeString(int bitSize) {
        return toSizeString((long) bitSize);
    }

    public static String toSizeString(long bitSize) {
        if (bitSize == 0) {
            return "0 bits";
        } else if (bitSize == 1) {
            return "1 bit";
        } else if (!isByteAligned(bitSize)) {
            return String.format("%d bits", bitSize);
        }

        final long byteSize = bitSize / Byte.SIZE;
        if (byteSize == 1) {
            return "1 byte";
        } else {
            return String.format("%d bytes", byteSize);
        }
    }

    public static boolean isNestedManagedPointer(LLVMPointer base) {
        if (!LLVMManagedPointer.isInstance(base)) {
            return false;
        }

        final LLVMManagedPointer pointer = LLVMManagedPointer.cast(base);
        return LLVMPointer.isInstance(pointer.getObject()) && pointer.getOffset() == 0L;
    }
}
