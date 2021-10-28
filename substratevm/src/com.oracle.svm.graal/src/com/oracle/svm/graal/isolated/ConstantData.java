/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.SubstrateMemoryAccessProviderImpl;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@RawStructure
interface ConstantData extends PointerBase {
    @RawField
    <T extends WordBase> T getRawBits();

    @RawField
    void setRawBits(WordBase value);

    @RawField
    char getKind();

    @RawField
    void setKind(char value);

    @RawField
    boolean getCompressed();

    @RawField
    void setCompressed(boolean value);

    /**
     * Whether this structure maps to {@code null}, that is, {@code Constant x = null} (and not
     * {@link JavaConstant#NULL_POINTER}).
     */
    @RawField
    boolean getRepresentsNull();

    @RawField
    void setRepresentsNull(boolean value);
}

final class ConstantDataConverter {
    private static final char IMAGE_HEAP_REF_KIND = '*';
    private static final char OBJECT_HANDLE_KIND = JavaKind.Object.getTypeChar();

    static void fromCompiler(Constant constant, ConstantData data) {
        data.setRepresentsNull(false);
        if (constant instanceof DirectSubstrateObjectConstant) {
            data.setRawBits(ImageHeapObjects.ref(((DirectSubstrateObjectConstant) constant).getObject()));
            data.setKind(IMAGE_HEAP_REF_KIND);
            data.setCompressed(((SubstrateObjectConstant) constant).isCompressed());
        } else if (constant instanceof IsolatedObjectConstant) {
            data.setRawBits(((IsolatedObjectConstant) constant).getHandle());
            data.setKind(OBJECT_HANDLE_KIND);
            data.setCompressed(((SubstrateObjectConstant) constant).isCompressed());
        } else {
            VMError.guarantee(!(constant instanceof SubstrateObjectConstant), "invalid constant");
            fromCompilerOrClient((JavaConstant) constant, data);
        }
    }

    static void fromClient(Constant constant, ConstantData data) {
        data.setRepresentsNull(false);
        if (constant instanceof DirectSubstrateObjectConstant) {
            Object target = ((DirectSubstrateObjectConstant) constant).getObject();
            data.setRawBits(IsolatedCompileClient.get().hand(target));
            data.setKind(OBJECT_HANDLE_KIND);
            data.setCompressed(((DirectSubstrateObjectConstant) constant).isCompressed());
        } else {
            VMError.guarantee(!(constant instanceof SubstrateObjectConstant), "invalid constant");
            fromCompilerOrClient((JavaConstant) constant, data);
        }
    }

    private static void fromCompilerOrClient(JavaConstant constant, ConstantData data) {
        if (constant == null) {
            data.setRepresentsNull(true);
            return;
        }
        WordBase rawBits;
        JavaKind kind = constant.getJavaKind();
        if (kind.isObject() && constant.isNull()) {
            rawBits = IsolatedHandles.nullHandle();
            data.setCompressed(SubstrateObjectConstant.isCompressed(constant));
        } else if (kind.isNumericInteger()) {
            rawBits = WordFactory.signed(constant.asLong());
        } else if (kind == JavaKind.Float) {
            rawBits = WordFactory.unsigned(Float.floatToRawIntBits(constant.asFloat()));
        } else if (kind == JavaKind.Double) {
            rawBits = WordFactory.unsigned(Double.doubleToRawLongBits(constant.asDouble()));
        } else {
            throw VMError.shouldNotReachHere("unsupported constant kind: " + kind);
        }
        data.setRawBits(rawBits);
        data.setKind(kind.getTypeChar());
    }

    static JavaConstant toCompiler(ConstantData data) {
        if (data.getRepresentsNull()) {
            return null;
        }
        char kind = data.getKind();
        if (kind == OBJECT_HANDLE_KIND) {
            ClientHandle<?> handle = data.getRawBits();
            if (handle.equal(IsolatedHandles.nullHandle())) {
                return SubstrateObjectConstant.forObject(null, data.getCompressed());
            }
            return new IsolatedObjectConstant(handle, data.getCompressed());
        }
        return toCompilerOrClient(data);
    }

    static JavaConstant toClient(ConstantData data) {
        if (data.getRepresentsNull()) {
            return null;
        }
        char kindChar = data.getKind();
        if (kindChar == IMAGE_HEAP_REF_KIND) {
            return SubstrateObjectConstant.forObject(ImageHeapObjects.deref(data.getRawBits()), data.getCompressed());
        } else if (kindChar == OBJECT_HANDLE_KIND) {
            Object target = IsolatedCompileClient.get().unhand(data.getRawBits());
            return SubstrateObjectConstant.forObject(target, data.getCompressed());
        }
        return toCompilerOrClient(data);
    }

    private static JavaConstant toCompilerOrClient(ConstantData data) {
        JavaKind kind = JavaKind.fromPrimitiveOrVoidTypeChar(data.getKind());
        return SubstrateMemoryAccessProviderImpl.toConstant(kind, data.getRawBits().rawValue());
    }

    private ConstantDataConverter() {
    }
}
