/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.sdk.staging.layeredimage.MultiLayeredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

public class InterpreterResolvedJavaField implements ResolvedJavaField, CremaFieldAccess {
    public static final InterpreterResolvedJavaField[] EMPTY_ARRAY = new InterpreterResolvedJavaField[0];

    // Special offset values
    private static final int FIELD_UNMATERIALIZED = -10;
    private static final int OFFSET_UNINITIALIZED = -11;

    private final int modifiers;
    private final Symbol<Name> name;
    private final Symbol<Type> typeSymbol;

    // Computed after analysis.
    private int offset;
    protected byte layerNum;

    private final InterpreterResolvedObjectType declaringClass;
    protected InterpreterResolvedJavaType resolvedType;

    private final boolean isWordStorage;

    private JavaConstant constantValue;

    @Platforms(Platform.HOSTED_ONLY.class) private AnalysisField originalField;

    /**
     * Ensures that the field metadata is kept for the interpreter, without forcing it into the
     * image. Reachability still depends on the declaring class and field type reachability.
     * Artificial reachability is useful for fields that are reachable at build-time e.g. inlined or
     * substituted. They are never accessed again at runtime in compiled code, but the interpreter
     * may still access them e.g. $assertionsDisabled.
     */
    @Platforms(Platform.HOSTED_ONLY.class) private boolean artificiallyReachable;

    protected InterpreterResolvedJavaField(
                    Symbol<Name> name, Symbol<Type> typeSymbol, int modifiers,
                    InterpreterResolvedJavaType resolvedType, InterpreterResolvedObjectType declaringClass,
                    int offset,
                    JavaConstant constant,
                    boolean isWordStorage) {
        this.name = MetadataUtil.requireNonNull(name);
        this.typeSymbol = MetadataUtil.requireNonNull(typeSymbol);
        this.modifiers = modifiers;
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.offset = offset;
        this.constantValue = constant;
        this.isWordStorage = isWordStorage;
        this.resolvedType = resolvedType;
        if (resolvedType == null && TypeSymbols.isPrimitive(typeSymbol)) {
            // Primitive types are trivially resolved.
            this.resolvedType = InterpreterResolvedPrimitiveType.fromKind(CremaTypeAccess.symbolToJvmciKind(typeSymbol));
        }
        this.layerNum = NumUtil.safeToByte(Modifier.isStatic(modifiers) /*- Prevents 'this-escape' warning. */
                        ? MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED
                        : MultiLayeredImageSingleton.NONSTATIC_FIELD_LAYER_NUMBER);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaField createAtBuildTime(AnalysisField originalField, InterpreterResolvedObjectType declaringClass) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(originalField.getName());
        Symbol<Type> typeSymbol = CremaTypeAccess.jvmciNameToType(originalField.getType().getName());
        InterpreterResolvedJavaField field = new InterpreterResolvedJavaField(
                        nameSymbol, typeSymbol, originalField.getModifiers(),
                        /*- resolvedType */ null,
                        declaringClass,
                        OFFSET_UNINITIALIZED,
                        /*- constantValue */ null,
                        originalField.getType().isWordType());
        field.setOriginalField(originalField);
        return field;
    }

    public static InterpreterResolvedJavaField createForInterpreter(String name, int modifiers,
                    JavaType type, InterpreterResolvedObjectType declaringClass,
                    int offset,
                    JavaConstant constant,
                    boolean isWordStorage,
                    int layerNum) {
        MetadataUtil.requireNonNull(type);
        MetadataUtil.requireNonNull(declaringClass);
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        InterpreterResolvedJavaType resolvedType = type instanceof InterpreterResolvedJavaType ? (InterpreterResolvedJavaType) type : null;
        Symbol<Type> symbolicType = resolvedType == null ? CremaTypeAccess.jvmciNameToType(type.getName()) : resolvedType.getSymbolicType();
        InterpreterResolvedJavaField result = new InterpreterResolvedJavaField(nameSymbol, symbolicType, modifiers, resolvedType, declaringClass, offset, constant, isWordStorage);
        if (result.isStatic()) {
            result.layerNum = NumUtil.safeToByte(layerNum);
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final AnalysisField getOriginalField() {
        return originalField;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setOriginalField(AnalysisField originalField) {
        this.originalField = originalField;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setUnmaterializedConstant(JavaConstant constant) {
        assert JavaConstant.NULL_POINTER.equals(constant) || constant instanceof PrimitiveConstant || constant instanceof ReferenceConstant<?>;
        this.offset = InterpreterResolvedJavaField.FIELD_UNMATERIALIZED;
        this.constantValue = constant;
    }

    public final boolean isUnmaterializedConstant() {
        return this.offset == FIELD_UNMATERIALIZED;
    }

    /**
     * A field is undefined when it is unmaterialized, and the value is not preserved for the
     * interpreter. Examples of undefined fields include: {@link jdk.graal.compiler.word.Word}
     * subtypes, {@link DynamicHub}'s vtable.
     */
    public final boolean isUndefined() {
        return this.isUnmaterializedConstant() &&
                        this.getUnmaterializedConstant().getJavaKind() == JavaKind.Illegal;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setOffset(int offset) {
        VMError.guarantee(this.offset == OFFSET_UNINITIALIZED || this.offset == offset, "InterpreterField offset should not be set twice.");
        this.offset = offset;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setInstalledLayerNum(int layerNum) {
        assert isStatic();
        VMError.guarantee(this.layerNum == MultiLayeredImageSingleton.LAYER_NUM_UNINSTALLED || this.layerNum == layerNum);
        this.layerNum = NumUtil.safeToByte(layerNum);

    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void setResolvedType(InterpreterResolvedJavaType resolvedType) {
        VMError.guarantee(this.resolvedType == null || this.resolvedType.equals(resolvedType),
                        "InterpreterField resolvedType should not be set twice.");
        this.resolvedType = resolvedType;
    }

    @Override
    public final int getModifiers() {
        return modifiers;
    }

    @Override
    public final int getOffset() {
        return offset;
    }

    public final int getInstalledLayerNum() {
        return layerNum;
    }

    @Override
    public final String getName() {
        return name.toString();
    }

    @Override
    public final Symbol<Name> getSymbolicName() {
        return name;
    }

    @Override
    public JavaType getType() {
        /*
         * For fields created at build-time, the type is set if it is available. We explicitly do
         * not want to trigger field type resolution at build-time.
         *
         * If the resolvedType is null, the type was not included in the image. If we were to
         * eagerly create a ResolvedJavaType for it, we would force it back in.
         */
        if (resolvedType == null) {
            // Not included. return the unresolved type.
            return UnresolvedJavaType.create(typeSymbol.toString());
        }
        return resolvedType;
    }

    public InterpreterResolvedJavaType getResolvedType() {
        return resolvedType;
    }

    @Override
    public final JavaKind getJavaKind() {
        return CremaTypeAccess.symbolToJvmciKind(getSymbolicType());
    }

    public final boolean isWordStorage() {
        return isWordStorage;
    }

    public final Symbol<Type> getSymbolicType() {
        return typeSymbol;
    }

    @Override
    public final InterpreterResolvedObjectType getDeclaringClass() {
        return declaringClass;
    }

    public final JavaConstant getUnmaterializedConstant() {
        assert offset == FIELD_UNMATERIALIZED;
        // constantValue can be "Illegal" for some folded constants, for which the value is not
        // stored in the image heap.
        // Also take into account WordBase types, which have an Object kind, but the constantValue
        // is a long.
        assert (isWordStorage()) || constantValue == JavaConstant.ILLEGAL || getJavaKind() == constantValue.getJavaKind();
        return constantValue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final boolean isArtificiallyReachable() {
        return artificiallyReachable;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public final void markAsArtificiallyReachable() {
        this.artificiallyReachable = true;
    }

    @Override
    public final String toString() {
        return "InterpreterResolvedJavaField<holder=" + getDeclaringClass().getName() + " name=" + name + " descriptor=" + typeSymbol + " offset=" + offset + ">";
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterpreterResolvedJavaField)) {
            return false;
        }
        InterpreterResolvedJavaField that = (InterpreterResolvedJavaField) other;
        return name.equals(that.name) && declaringClass.equals(that.declaringClass) && typeSymbol.equals(that.typeSymbol);
    }

    @Override
    public final int hashCode() {
        int result = MetadataUtil.hashCode(name);
        result = 31 * result + MetadataUtil.hashCode(declaringClass);
        result = 31 * result + MetadataUtil.hashCode(typeSymbol);
        return result;
    }

    // region Unimplemented methods

    @Override
    public final boolean shouldEnforceInitializerCheck() {
        throw VMError.unimplemented("shouldEnforceInitializerCheck");
    }

    @Override
    public final boolean accessChecks(InterpreterResolvedJavaType accessingClass, InterpreterResolvedJavaType holderClass) {
        throw VMError.unimplemented("accessChecks");
    }

    @Override
    public final void loadingConstraints(InterpreterResolvedJavaType accessingClass, Function<String, RuntimeException> errorHandler) {
        throw VMError.unimplemented("loadingConstraints");
    }

    @Override
    public final boolean isInternal() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final boolean isSynthetic() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Annotation[] getAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public final Annotation[] getDeclaredAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods
}
