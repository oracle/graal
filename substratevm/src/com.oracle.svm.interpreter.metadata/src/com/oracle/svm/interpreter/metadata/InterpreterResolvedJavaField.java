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
import java.util.function.Function;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserField;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaType;

public final class InterpreterResolvedJavaField implements ResolvedJavaField, CremaFieldAccess {
    public static final InterpreterResolvedJavaField[] EMPTY_ARRAY = new InterpreterResolvedJavaField[0];

    // Computed after analysis.
    private int offset;
    private final int modifiers;
    private final Symbol<Name> name;
    private final Symbol<Type> typeSymbol;

    private InterpreterResolvedJavaType resolvedType;

    private final InterpreterResolvedObjectType declaringClass;

    private JavaConstant constantValue;

    @Platforms(Platform.HOSTED_ONLY.class) private ResolvedJavaField originalField;

    /**
     * Ensures that the field metadata is kept for the interpreter, without forcing it into the
     * image. Reachability still depends on the declaring class and field type reachability.
     * Artificial reachability is useful for fields that are reachable at build-time e.g. inlined or
     * substituted. They are never accessed again at runtime in compiled code, but the interpreter
     * may still access them e.g. $assertionsDisabled.
     */
    @Platforms(Platform.HOSTED_ONLY.class) private boolean artificiallyReachable;

    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedJavaField(ResolvedJavaField originalField,
                    Symbol<Name> name, int modifiers,
                    InterpreterResolvedJavaType resolvedType, InterpreterResolvedObjectType declaringClass,
                    int offset, JavaConstant constant) {
        this.originalField = originalField;
        this.name = MetadataUtil.requireNonNull(name);
        this.typeSymbol = resolvedType.getSymbolicType();
        this.modifiers = modifiers;
        this.resolvedType = MetadataUtil.requireNonNull(resolvedType);
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.offset = offset;
        this.constantValue = constant;
    }

    private InterpreterResolvedJavaField(Symbol<Name> name, int modifiers, InterpreterResolvedJavaType resolvedType, InterpreterResolvedObjectType declaringClass, int offset, JavaConstant constant) {
        this.name = MetadataUtil.requireNonNull(name);
        this.typeSymbol = resolvedType.getSymbolicType();
        this.modifiers = modifiers;
        this.resolvedType = MetadataUtil.requireNonNull(resolvedType);
        this.declaringClass = MetadataUtil.requireNonNull(declaringClass);
        this.offset = offset;
        this.constantValue = constant;
    }

    private InterpreterResolvedJavaField(InterpreterResolvedObjectType declaringClass, ParserField f, int offset) {
        this.name = f.getName();
        this.typeSymbol = f.getType();
        this.modifiers = f.getFlags();
        this.declaringClass = declaringClass;
        this.offset = offset;
        if (TypeSymbols.isPrimitive(f.getType())) {
            // Primitive types are trivially resolved.
            this.resolvedType = InterpreterResolvedPrimitiveType.fromKind(JavaKind.fromPrimitiveOrVoidTypeChar((char) getSymbolicType().byteAt(0)));
        }
    }

    public static InterpreterResolvedJavaField create(InterpreterResolvedObjectType declaringClass, ParserField f, int offset) {
        return new InterpreterResolvedJavaField(declaringClass, f, offset);
    }

    public static InterpreterResolvedJavaField create(String name, int modifiers, InterpreterResolvedJavaType type, InterpreterResolvedObjectType declaringClass, int offset, JavaConstant constant) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        return new InterpreterResolvedJavaField(nameSymbol, modifiers, type, declaringClass, offset, constant);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedJavaField create(ResolvedJavaField originalField, String name, int modifiers, InterpreterResolvedJavaType type, InterpreterResolvedObjectType declaringClass,
                    int offset, JavaConstant constant) {
        Symbol<Name> nameSymbol = SymbolsSupport.getNames().getOrCreate(name);
        return new InterpreterResolvedJavaField(originalField, nameSymbol, modifiers, type, declaringClass, offset, constant);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaField getOriginalField() {
        return originalField;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setUnmaterializedConstant(JavaConstant constant) {
        assert JavaConstant.NULL_POINTER.equals(constant) || constant instanceof PrimitiveConstant || constant instanceof ReferenceConstant<?>;
        this.offset = InterpreterResolvedJavaField.FIELD_UNMATERIALIZED;
        this.constantValue = constant;
    }

    public static final int FIELD_UNMATERIALIZED = -10;

    public boolean isUnmaterializedConstant() {
        return this.offset == FIELD_UNMATERIALIZED;
    }

    /**
     * A field is undefined when it is unmaterialized, and the value is not preserved for the
     * interpreter. Examples of undefined fields include: {@link jdk.graal.compiler.word.Word}
     * subtypes, {@link DynamicHub}'s vtable.
     */
    public boolean isUndefined() {
        return this.isUnmaterializedConstant() &&
                        this.getUnmaterializedConstant().getJavaKind() == JavaKind.Illegal;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public String getName() {
        return name.toString();
    }

    @Override
    public Symbol<Name> getSymbolicName() {
        return name;
    }

    @Override
    public JavaType getType() {
        if (resolvedType != null) {
            return resolvedType;
        }
        return UnresolvedJavaType.create(getSymbolicType().toString());
    }

    public InterpreterResolvedJavaType getResolvedType() {
        if (resolvedType == null) {
            resolvedType = (InterpreterResolvedJavaType) getDeclaringClass().lookupType(UnresolvedJavaType.create(getSymbolicType().toString()), true);
        }
        return resolvedType;
    }

    public Symbol<Type> getSymbolicType() {
        return typeSymbol;
    }

    @Override
    public InterpreterResolvedObjectType getDeclaringClass() {
        return declaringClass;
    }

    public JavaConstant getUnmaterializedConstant() {
        assert offset == FIELD_UNMATERIALIZED;
        // constantValue can be "Illegal" for some folded constants, for which the value is not
        // stored in the image heap.
        // Also take into account WordBase types, which have an Object kind, but the constantValue
        // is a long.
        assert (this.getType() instanceof InterpreterResolvedJavaType resolved && resolved.isWordType()) ||
                        constantValue == JavaConstant.ILLEGAL || getJavaKind() == constantValue.getJavaKind();
        return constantValue;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean isArtificiallyReachable() {
        return artificiallyReachable;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void markAsArtificiallyReachable() {
        this.artificiallyReachable = true;
    }

    @Override
    public String toString() {
        return "InterpreterResolvedJavaField<holder=" + getDeclaringClass().getName() + " name=" + getName() + " descriptor=" + getType().getName() + " offset=" + this.getOffset() + ">";
    }

    @Override
    public boolean equals(Object other) {
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
    public int hashCode() {
        int result = MetadataUtil.hashCode(name);
        result = 31 * result + MetadataUtil.hashCode(declaringClass);
        result = 31 * result + MetadataUtil.hashCode(typeSymbol);
        return result;
    }

    // region Unimplemented methods

    @Override
    public boolean shouldEnforceInitializerCheck() {
        throw VMError.unimplemented("shouldEnforceInitializerCheck");
    }

    @Override
    public boolean accessChecks(InterpreterResolvedJavaType accessingClass, InterpreterResolvedJavaType holderClass) {
        throw VMError.unimplemented("accessChecks");
    }

    @Override
    public void loadingConstraints(InterpreterResolvedJavaType accessingClass, Function<String, RuntimeException> errorHandler) {
        throw VMError.unimplemented("loadingConstraints");
    }

    @Override
    public boolean isInternal() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean isSynthetic() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Annotation[] getAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw VMError.intentionallyUnimplemented();
    }

    // endregion Unimplemented methods
}
