/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.function.Function;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.CremaResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * JVMCI representation of a {@link ResolvedJavaField} used by Ristretto for compilation. Exists
 * once per {@link InterpreterResolvedJavaField}. Allocated during runtime compilation by a
 * {@link RistrettoConstantPool} if fields are accessed over the constant pool during parsing.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterResolvedJavaField} is gc-ed.
 */
public final class RistrettoField extends SubstrateField {
    private final InterpreterResolvedJavaField interpreterField;

    private RistrettoField(InterpreterResolvedJavaField interpreterField) {
        this.interpreterField = interpreterField;
    }

    private RistrettoField(InterpreterResolvedJavaField interpreterField, SubstrateField aotField) {
        super(aotField.getName(), aotField.getModifiers(), aotField.hashCode(), aotField.getLocation());
        this.interpreterField = interpreterField;
    }

    private static final Function<InterpreterResolvedJavaField, ResolvedJavaField> RISTRETTO_FIELD_FUNCTION = RistrettoField::new;

    public static RistrettoField getOrCreate(InterpreterResolvedJavaField interpreterField) {
        return (RistrettoField) interpreterField.getRistrettoField(RISTRETTO_FIELD_FUNCTION);
    }

    public static RistrettoField getOrCreate(InterpreterResolvedJavaField interpreterField, SubstrateField aotField) {
        GraalError.guarantee(!RistrettoUtils.isRuntimeLoaded(aotField.getDeclaringClass()), "Must be in the image already");
        return (RistrettoField) interpreterField.getRistrettoField(interpreterResolvedJavaField -> new RistrettoField(interpreterResolvedJavaField, aotField));
    }

    public InterpreterResolvedJavaField getInterpreterField() {
        return interpreterField;
    }

    @Override
    public int getLocation() {
        // can re-use the offset from the interpreter field, it's derived from the dynamic hub
        return interpreterField.getOffset();
    }

    @Override
    public boolean isAccessed() {
        // In Crema, any field can potentially be accessed by dynamically loaded bytecode.
        return true;
    }

    @Override
    public boolean isReachable() {
        // In Crema, any field can potentially be accessed by dynamically loaded bytecode.
        return true;
    }

    @Override
    public boolean isWritten() {
        // In Crema, any field can potentially be accessed by dynamically loaded bytecode.
        return true;
    }

    @Override
    public int hashCode() {
        return interpreterField.hashCode();
    }

    @Override
    public JavaType getType() {
        JavaType fieldType = interpreterField.getType();
        if (fieldType instanceof InterpreterResolvedJavaType iType) {
            return RistrettoType.getOrCreate(iType);
        }
        if (fieldType instanceof SubstrateType sType) {
            return RistrettoUtils.toRType(sType);
        }
        return fieldType;
    }

    @Override
    public JavaKind getStorageKind() {
        JavaType fieldType = getType();
        if (fieldType instanceof UnresolvedJavaType) {
            /*
             * Without a shared type there is no shared-world storage kind to query yet. Fall back
             * to the descriptor-derived Java kind, preserving the word-storage override used by
             * Crema for symbolic runtime-loaded fields.
             */
            return interpreterField.isWordStorage() ? SubstrateTarget.getWordKind() : interpreterField.getJavaKind();
        } else {
            GraalError.guarantee(fieldType instanceof RistrettoType, "Must have a ristretto field or an unresolved one but found %s", fieldType);
            return ((RistrettoType) fieldType).getStorageKind();
        }
    }

    @Override
    public String getName() {
        return interpreterField.getName();
    }

    @Override
    public int getModifiers() {
        return interpreterField.getModifiers();
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return RistrettoType.getOrCreate(interpreterField.getDeclaringClass());
    }

    @Override
    public boolean isSynthetic() {
        // not implemented by svm field,
        return interpreterField.isSynthetic();
    }

    @Override
    public String toString() {
        return "RistrettoField{super=" + super.toString() + ", interpreterField=" + interpreterField + "}";
    }

    @Override
    public int getInstalledLayerNum() {
        return interpreterField.getInstalledLayerNum();
    }

    @Override
    public Object getStaticFieldBaseForRuntimeLoadedClass() {
        if (interpreterField.isStatic()) {
            InterpreterResolvedJavaType iType = interpreterField.getDeclaringClass();
            if (iType instanceof CremaResolvedObjectType declaringClass) {
                return declaringClass.getStaticStorage(interpreterField.getJavaKind().isPrimitive(), interpreterField.getInstalledLayerNum());
            }
            return null;
        }
        throw GraalError.shouldNotReachHere("Only static fields should end up here");
    }

}
