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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.nfi.impl.FunctionExecuteNode.SignatureExecuteNode;
import com.oracle.truffle.nfi.impl.LibFFIType.CachedTypeInfo;
import com.oracle.truffle.nfi.impl.LibFFIType.Direction;
import static com.oracle.truffle.nfi.impl.LibFFIType.Direction.JAVA_TO_NATIVE_ONLY;
import static com.oracle.truffle.nfi.impl.LibFFIType.Direction.NATIVE_TO_JAVA_ONLY;
import com.oracle.truffle.nfi.impl.NativeAllocation.FreeDestructor;
import com.oracle.truffle.nfi.spi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeSignature;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror;
import java.util.List;

/**
 * Runtime object representing native signatures. Instances of this class can not be cached in
 * shared AST nodes, since they contain references to native datastructures of libffi.
 *
 * All information that is context and process independent is collected in a separate object,
 * {@link CachedSignatureInfo}. Two {@link LibFFISignature} objects that have the same {@link
 * CachedSignatureInfo} are guaranteed to behave the same semantically.
 */
final class LibFFISignature {

    public static LibFFISignature create(NFIContext context, NativeSignature signature) {
        LibFFISignature ret = new LibFFISignature(context, signature);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new FreeDestructor(ret.cif));
        return ret;
    }

    private final long cif; // native pointer
    final CachedSignatureInfo signatureInfo;

    private LibFFISignature(NFIContext context, NativeSignature signature) {
        if (signature.getRetType() instanceof NativeArrayTypeMirror) {
            throw new IllegalArgumentException("array type as return value is not supported");
        }

        boolean allowJavaToNativeCall = true;
        boolean allowNativeToJavaCall = true;

        LibFFIType retType = context.lookupRetType(signature.getRetType());

        switch (retType.typeInfo.allowedDataFlowDirection) {
            /*
             * If the call goes from Java to native, the return value flows from native-to-Java, and
             * vice-versa.
             */
            case JAVA_TO_NATIVE_ONLY:
                allowJavaToNativeCall = false;
                break;
            case NATIVE_TO_JAVA_ONLY:
                allowNativeToJavaCall = false;
                break;
        }

        List<NativeTypeMirror> args = signature.getArgTypes();
        LibFFIType[] argTypes = new LibFFIType[args.size()];
        CachedTypeInfo[] argTypesInfo = new CachedTypeInfo[args.size()];
        for (int i = 0; i < argTypes.length; i++) {
            LibFFIType argType = context.lookupArgType(args.get(i));
            CachedTypeInfo argTypeInfo = argType.typeInfo;
            if (argType.typeInfo instanceof LibFFIType.VoidType) {
                throw new IllegalArgumentException("void is not a valid argument type");
            }

            switch (argType.typeInfo.allowedDataFlowDirection) {
                case JAVA_TO_NATIVE_ONLY:
                    allowNativeToJavaCall = false;
                    break;
                case NATIVE_TO_JAVA_ONLY:
                    allowJavaToNativeCall = false;
                    break;
            }
            argTypes[i] = argType;
            argTypesInfo[i] = argTypeInfo;
        }

        Direction allowedCallDirection;
        if (allowNativeToJavaCall) {
            if (allowJavaToNativeCall) {
                allowedCallDirection = Direction.BOTH;
            } else {
                allowedCallDirection = Direction.NATIVE_TO_JAVA_ONLY;
            }
        } else {
            if (allowJavaToNativeCall) {
                allowedCallDirection = Direction.JAVA_TO_NATIVE_ONLY;
            } else {
                throw new IllegalArgumentException("invalid signature");
            }
        }

        if (signature.isVarargs()) {
            this.cif = context.prepareSignatureVarargs(retType, signature.getFixedArgCount(), argTypes);
        } else {
            this.cif = context.prepareSignature(retType, argTypes);
        }

        int primSize = 0;
        int objCount = 0;
        int argCount = 0;

        for (CachedTypeInfo type : argTypesInfo) {
            int align = type.alignment;
            if (primSize % align != 0) {
                primSize += align - (primSize % align);
            }
            primSize += type.size;
            objCount += type.objectCount;
            if (!type.injectedArgument) {
                argCount++;
            }
        }

        this.signatureInfo = new CachedSignatureInfo(context.language, retType.typeInfo, argTypesInfo, primSize, objCount, argCount, allowedCallDirection);
    }

    /**
     * This class contains all information about native signatures that can be shared across
     * contexts. Instances of this class can safely be cached in shared AST. This contains a shared
     * {@link CallTarget} that can be used to call functions of that signature.
     */
    static final class CachedSignatureInfo {

        final CachedTypeInfo retType;
        @CompilationFinal(dimensions = 1) final CachedTypeInfo[] argTypes;

        final int primitiveSize;
        final int objectCount;

        final int realArgCount;

        final Direction allowedCallDirection;

        final CallTarget callTarget;

        public CachedSignatureInfo(NFILanguageImpl language, CachedTypeInfo retType, CachedTypeInfo[] argTypes, int primitiveSize, int objectCount, int realArgCount, Direction allowedCallDirection) {
            this.retType = retType;
            this.argTypes = argTypes;
            this.primitiveSize = primitiveSize;
            this.objectCount = objectCount;
            this.realArgCount = realArgCount;
            this.allowedCallDirection = allowedCallDirection;

            this.callTarget = Truffle.getRuntime().createCallTarget(new SignatureExecuteNode(language, this));
        }

        public NativeArgumentBuffer.Array prepareBuffer() {
            return new NativeArgumentBuffer.Array(primitiveSize, objectCount);
        }

        public CachedTypeInfo[] getArgTypes() {
            return argTypes;
        }

        public CachedTypeInfo getRetType() {
            return retType;
        }

        public Direction getAllowedCallDirection() {
            return allowedCallDirection;
        }

        public int getRealArgCount() {
            return realArgCount;
        }

        public Object execute(LibFFISignature signature, NFIContext ctx, long functionPointer, NativeArgumentBuffer.Array argBuffer) {
            assert signature.signatureInfo == this;
            CompilerAsserts.partialEvaluationConstant(retType);
            if (retType instanceof LibFFIType.ObjectType) {
                Object ret = ctx.executeObject(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
                if (ret == null) {
                    return NativePointer.create(ctx.language, 0);
                } else {
                    return ret;
                }
            } else if (retType instanceof LibFFIType.SimpleType) {
                LibFFIType.SimpleType simpleType = (LibFFIType.SimpleType) retType;
                long ret = ctx.executePrimitive(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
                return simpleType.fromPrimitive(ret);
            } else {
                NativeArgumentBuffer.Array retBuffer = new NativeArgumentBuffer.Array(retType.size, retType.objectCount);
                ctx.executeNative(signature.cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects, retBuffer.prim);
                return retType.deserializeRet(retBuffer, ctx.language);
            }
        }
    }
}
