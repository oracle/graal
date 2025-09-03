/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;

import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.espresso.classfile.ConstantPool;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols;
import com.oracle.svm.espresso.classfile.descriptors.ParserSymbols.ParserTypes;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class InterpreterResolvedPrimitiveType extends InterpreterResolvedJavaType {
    private final JavaKind kind;

    private InterpreterResolvedPrimitiveType(Symbol<Type> type, JavaKind kind) {
        super(type, kind.toJavaClass());
        assert kind.isPrimitive();
        this.kind = kind;
    }

    static {
        ParserSymbols.ensureInitialized();
    }

    static final InterpreterResolvedPrimitiveType BOOLEAN = new InterpreterResolvedPrimitiveType(ParserTypes._boolean, JavaKind.Boolean);
    static final InterpreterResolvedPrimitiveType BYTE = new InterpreterResolvedPrimitiveType(ParserTypes._byte, JavaKind.Byte);
    static final InterpreterResolvedPrimitiveType SHORT = new InterpreterResolvedPrimitiveType(ParserTypes._short, JavaKind.Short);
    static final InterpreterResolvedPrimitiveType CHAR = new InterpreterResolvedPrimitiveType(ParserTypes._char, JavaKind.Char);
    static final InterpreterResolvedPrimitiveType INT = new InterpreterResolvedPrimitiveType(ParserTypes._int, JavaKind.Int);
    static final InterpreterResolvedPrimitiveType FLOAT = new InterpreterResolvedPrimitiveType(ParserTypes._float, JavaKind.Float);
    static final InterpreterResolvedPrimitiveType LONG = new InterpreterResolvedPrimitiveType(ParserTypes._long, JavaKind.Long);
    static final InterpreterResolvedPrimitiveType DOUBLE = new InterpreterResolvedPrimitiveType(ParserTypes._double, JavaKind.Double);
    static final InterpreterResolvedPrimitiveType VOID = new InterpreterResolvedPrimitiveType(ParserTypes._void, JavaKind.Void);

    public static InterpreterResolvedPrimitiveType fromKind(JavaKind kind) {
        // @formatter:off
        switch (kind) {
            case Boolean : return BOOLEAN;
            case Byte    : return BYTE;
            case Short   : return SHORT;
            case Char    : return CHAR;
            case Int     : return INT;
            case Float   : return FLOAT;
            case Long    : return LONG;
            case Double  : return DOUBLE;
            case Void    : return VOID;
            case Object  : // fall-through
            case Illegal : // fall-through
            default:
                throw new IllegalArgumentException(kind.toString());
        }
        // @formatter:on
    }

    @Override
    public int getModifiers() {
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public ResolvedJavaType getComponentType() {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return kind;
    }

    @Override
    public String getSourceFileName() {
        return null;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        return null;
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        return new ResolvedJavaType[0];
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        return this.equals(other);
    }

    @Override
    public InterpreterResolvedJavaMethod[] getDeclaredMethods(boolean link) {
        return NO_METHODS;
    }

    @Override
    public String getJavaName() {
        return getJavaKind().getJavaName();
    }

    @Override
    public InterpreterResolvedJavaType findLeastCommonAncestor(InterpreterResolvedJavaType other) {
        if (this == other) {
            return this;
        }
        return null;
    }

    @Override
    public InterpreterResolvedJavaType getSuperClass() {
        return null;
    }

    @Override
    public InterpreterResolvedJavaType getHostType() {
        return null;
    }

    @Override
    public Symbol<Name> getSymbolicRuntimePackage() {
        return SymbolsSupport.getNames().getOrCreate(ByteSequence.EMPTY);
    }

    @Override
    public InterpreterResolvedJavaField lookupField(Symbol<Name> name, Symbol<Type> type) {
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupMethod(Symbol<Name> name, Symbol<Signature> signature) {
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupInstanceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupVTableEntry(int vtableIndex) {
        return null;
    }

    @Override
    public ConstantPool getConstantPool() {
        return null;
    }

    @Override
    public InterpreterResolvedJavaType resolveClassConstantInPool(int cpi) {
        return null;
    }
}
