/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.nfi.impl.LibFFIType.Direction;
import com.oracle.truffle.nfi.impl.NativeAllocation.FreeDestructor;
import com.oracle.truffle.nfi.spi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.spi.types.NativeSignature;
import com.oracle.truffle.nfi.spi.types.NativeTypeMirror;
import java.util.List;

final class LibFFISignature {

    public static LibFFISignature create(NFIContext context, NativeSignature signature) {
        LibFFISignature ret = new LibFFISignature(context, signature);
        NativeAllocation.getGlobalQueue().registerNativeAllocation(ret, new FreeDestructor(ret.cif));
        return ret;
    }

    private final LibFFIType retType;
    @CompilationFinal(dimensions = 1) private final LibFFIType[] argTypes;

    private final int primitiveSize;
    private final int objectCount;

    private final int realArgCount;

    private final long cif;

    private final Direction allowedCallDirection;

    private LibFFISignature(NFIContext context, NativeSignature signature) {
        if (signature.getRetType() instanceof NativeArrayTypeMirror) {
            throw new IllegalArgumentException("array type as return value is not supported");
        }

        boolean allowJavaToNativeCall = true;
        boolean allowNativeToJavaCall = true;

        this.retType = context.lookupRetType(signature.getRetType());

        switch (retType.allowedDataFlowDirection) {
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
        this.argTypes = new LibFFIType[args.size()];
        for (int i = 0; i < argTypes.length; i++) {
            LibFFIType argType = context.lookupArgType(args.get(i));
            if (argType instanceof LibFFIType.VoidType) {
                throw new IllegalArgumentException("void is not a valid argument type");
            }

            switch (argType.allowedDataFlowDirection) {
                case JAVA_TO_NATIVE_ONLY:
                    allowNativeToJavaCall = false;
                    break;
                case NATIVE_TO_JAVA_ONLY:
                    allowJavaToNativeCall = false;
                    break;
            }
            this.argTypes[i] = argType;
        }

        if (allowNativeToJavaCall) {
            if (allowJavaToNativeCall) {
                this.allowedCallDirection = Direction.BOTH;
            } else {
                this.allowedCallDirection = Direction.NATIVE_TO_JAVA_ONLY;
            }
        } else {
            if (allowJavaToNativeCall) {
                this.allowedCallDirection = Direction.JAVA_TO_NATIVE_ONLY;
            } else {
                throw new IllegalArgumentException("invalid signature");
            }
        }

        if (signature.isVarargs()) {
            this.cif = context.prepareSignatureVarargs(this.retType, signature.getFixedArgCount(), this.argTypes);
        } else {
            this.cif = context.prepareSignature(this.retType, this.argTypes);
        }

        int primSize = 0;
        int objCount = 0;
        int argCount = 0;

        for (LibFFIType type : this.argTypes) {
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

        this.primitiveSize = primSize;
        this.objectCount = objCount;
        this.realArgCount = argCount;
    }

    public NativeArgumentBuffer.Array prepareBuffer() {
        return new NativeArgumentBuffer.Array(primitiveSize, objectCount);
    }

    public LibFFIType[] getArgTypes() {
        return argTypes;
    }

    public LibFFIType getRetType() {
        return retType;
    }

    public Direction getAllowedCallDirection() {
        return allowedCallDirection;
    }

    public int getRealArgCount() {
        return realArgCount;
    }

    public Object execute(NFIContext ctx, long functionPointer, NativeArgumentBuffer.Array argBuffer) {
        CompilerAsserts.partialEvaluationConstant(retType);
        if (retType instanceof LibFFIType.ObjectType) {
            Object ret = ctx.executeObject(cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
            if (ret == null) {
                return NativePointer.create(ctx.language, 0);
            } else {
                return ret;
            }
        } else if (retType instanceof LibFFIType.SimpleType) {
            LibFFIType.SimpleType simpleType = (LibFFIType.SimpleType) retType;
            long ret = ctx.executePrimitive(cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
            return simpleType.fromPrimitive(ret);
        } else {
            NativeArgumentBuffer.Array retBuffer = new NativeArgumentBuffer.Array(retType.size, retType.objectCount);
            ctx.executeNative(cif, functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects, retBuffer.prim);
            return retType.deserializeRet(retBuffer, ctx.language);
        }
    }
}
