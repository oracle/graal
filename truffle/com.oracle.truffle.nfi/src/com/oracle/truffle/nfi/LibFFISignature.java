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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.nfi.LibFFIType.Direction;
import com.oracle.truffle.nfi.types.NativeSignature;
import com.oracle.truffle.nfi.NativeAllocation.FreeDestructor;
import com.oracle.truffle.nfi.types.NativeArrayTypeMirror;
import com.oracle.truffle.nfi.types.NativeTypeMirror;
import java.util.List;

final class LibFFISignature {

    public static LibFFISignature create(NativeSignature signature) {
        LibFFISignature ret = new LibFFISignature(signature);
        NativeAllocation.registerNativeAllocation(ret, new FreeDestructor(ret.cif));
        return ret;
    }

    private final LibFFIType retType;
    private final LibFFIType[] argTypes;

    private final int primitiveSize;
    private final int objectCount;

    private final long cif;

    private final Direction allowedCallDirection;

    private LibFFISignature(NativeSignature signature) {
        if (signature.getRetType() instanceof NativeArrayTypeMirror) {
            throw new IllegalArgumentException("array type as return value is not supported");
        }

        boolean allowJavaToNativeCall = true;
        boolean allowNativeToJavaCall = true;

        this.retType = LibFFIType.lookupRetType(signature.getRetType());

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
            LibFFIType argType = LibFFIType.lookupArgType(args.get(i));
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
            this.cif = prepareSignatureVarargs(this.retType, signature.getFixedArgCount(), this.argTypes);
        } else {
            this.cif = prepareSignature(this.retType, this.argTypes);
        }

        int primSize = 0;
        int objCount = 0;

        for (LibFFIType type : this.argTypes) {
            int align = type.alignment;
            if (primSize % align != 0) {
                primSize += align - (primSize % align);
            }
            primSize += type.size;
            objCount += type.objectCount;
        }

        this.primitiveSize = primSize;
        this.objectCount = objCount;
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

    public Object execute(long functionPointer, NativeArgumentBuffer.Array argBuffer) {
        CompilerAsserts.partialEvaluationConstant(retType);
        if (retType instanceof LibFFIType.ObjectType) {
            return executeObject(functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
        } else if (retType instanceof LibFFIType.SimpleType) {
            LibFFIType.SimpleType simpleType = (LibFFIType.SimpleType) retType;
            long ret = executePrimitive(functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects);
            return simpleType.fromPrimitive(ret);
        } else {
            NativeArgumentBuffer.Array retBuffer = new NativeArgumentBuffer.Array(retType.size, retType.objectCount);
            executeNative(functionPointer, argBuffer.prim, argBuffer.getPatchCount(), argBuffer.patches, argBuffer.objects, retBuffer.prim);
            return retType.deserialize(retBuffer);
        }
    }

    @TruffleBoundary
    private native void executeNative(long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs, byte[] ret);

    @TruffleBoundary
    private native long executePrimitive(long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs);

    @TruffleBoundary
    private native TruffleObject executeObject(long functionPointer, byte[] primArgs, int patchCount, int[] patchOffsets, Object[] objArgs);

    private static native long prepareSignature(LibFFIType retType, LibFFIType... args);

    private static native long prepareSignatureVarargs(LibFFIType retType, int nFixedArgs, LibFFIType... args);
}
