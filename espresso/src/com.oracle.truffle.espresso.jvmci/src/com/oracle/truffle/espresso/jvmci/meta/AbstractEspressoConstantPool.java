/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jvmci.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;

public abstract class AbstractEspressoConstantPool implements ConstantPool {
    // @formatter:off
    protected static final byte CONSTANT_Invalid            = 0;
    protected static final byte CONSTANT_Utf8               = 1;
    protected static final byte CONSTANT_Integer            = 3;
    protected static final byte CONSTANT_Float              = 4;
    protected static final byte CONSTANT_Long               = 5;
    protected static final byte CONSTANT_Double             = 6;
    protected static final byte CONSTANT_Class              = 7;
    protected static final byte CONSTANT_String             = 8;
    protected static final byte CONSTANT_Fieldref           = 9;
    protected static final byte CONSTANT_Methodref          = 10;
    protected static final byte CONSTANT_InterfaceMethodref = 11;
    protected static final byte CONSTANT_NameAndType        = 12;
    protected static final byte CONSTANT_MethodHandle       = 15;
    protected static final byte CONSTANT_MethodType         = 16;
    protected static final byte CONSTANT_Dynamic            = 17;
    protected static final byte CONSTANT_InvokeDynamic      = 18;
    protected static final byte CONSTANT_Module             = 19;
    protected static final byte CONSTANT_Package            = 20;
    // @formatter:on

    static final int INVOKEDYNAMIC = 186;

    @Override
    public final void loadReferencedType(int cpi, int opcode) {
        loadReferencedType(cpi, opcode, true);
    }

    @Override
    public final void loadReferencedType(int cpi, int opcode, boolean initialize) {
        if (!loadReferencedType0(cpi, opcode)) {
            return;
        }
        if (initialize) {
            EspressoResolvedJavaType type = (EspressoResolvedJavaType) lookupReferencedType(cpi, opcode);
            type.initialize();
        }
    }

    protected abstract boolean loadReferencedType0(int cpi, int opcode);

    @Override
    public final JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        AbstractEspressoResolvedJavaField field = lookupResolvedField(cpi, (AbstractEspressoResolvedJavaMethod) method, opcode);
        if (field != null) {
            return field;
        }
        String name = lookupName(cpi);
        JavaType type = lookupFieldType(cpi, ((AbstractEspressoResolvedJavaMethod) method).getDeclaringClass());
        JavaType fieldHolder = lookupReferencedType(cpi, opcode);
        return new UnresolvedJavaField(fieldHolder, name, type);
    }

    protected abstract AbstractEspressoResolvedJavaField lookupResolvedField(int cpi, AbstractEspressoResolvedJavaMethod method, int opcode);

    @Override
    public final JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        AbstractEspressoResolvedJavaMethod method = lookupResolvedMethod(cpi, opcode, (AbstractEspressoResolvedJavaMethod) caller);
        if (method != null) {
            return method;
        }
        String name = lookupName(cpi);
        String rawSignature = lookupDescriptor(cpi);
        JavaType methodHolder;
        if (opcode == INVOKEDYNAMIC) {
            methodHolder = getMethodHandleType();
        } else {
            methodHolder = lookupReferencedType(cpi, opcode);
        }
        return new UnresolvedJavaMethod(name, getSignature(rawSignature), methodHolder);
    }

    protected abstract AbstractEspressoSignature getSignature(String rawSignature);

    protected abstract ResolvedJavaType getMethodHandleType();

    protected abstract JavaType lookupFieldType(int cpi, AbstractEspressoResolvedInstanceType accessingType);

    protected abstract String lookupDescriptor(int cpi);

    protected abstract String lookupName(int cpi);

    protected abstract AbstractEspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, AbstractEspressoResolvedJavaMethod caller);

    protected abstract EspressoBootstrapMethodInvocation lookupIndyBootstrapMethodInvocation(int siteIndex);

    @Override
    public final List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        List<BootstrapMethodInvocation> result;
        if (invokeDynamic) {
            int indyEntries = getNumIndyEntries();
            if (indyEntries == 0) {
                return Collections.emptyList();
            }
            result = new ArrayList<>(indyEntries);
            for (int i = 0; i < indyEntries; i++) {
                result.add(lookupIndyBootstrapMethodInvocation(i));
            }
        } else {
            result = new ArrayList<>();
            int length = length();
            for (int i = 0; i < length; i++) {
                byte tagByte = getTagByteAt(i);
                if (tagByte == 17) {
                    // Dynamic
                    result.add(lookupBootstrapMethodInvocation(i, -1));
                }
            }
        }
        return result;
    }

    protected abstract int getNumIndyEntries();

    @Override
    public final Signature lookupSignature(int cpi) {
        String rawSignature = lookupDescriptor(cpi);
        return getSignature(rawSignature);
    }

    @Override
    public final Object lookupConstant(int cpi) {
        return lookupConstant(cpi, true);
    }

    protected abstract byte getTagByteAt(int cpi);

    @SuppressWarnings("unused")
    private String getTagAt(int cpi) {
        // Used in tests
        return switch (getTagByteAt(cpi)) {
            case CONSTANT_Invalid -> "Invalid";
            case CONSTANT_Utf8 -> "Utf8";
            case CONSTANT_Integer -> "Integer";
            case CONSTANT_Float -> "Float";
            case CONSTANT_Long -> "Long";
            case CONSTANT_Double -> "Double";
            case CONSTANT_Class -> "Class";
            case CONSTANT_String -> "String";
            case CONSTANT_Fieldref -> "Fieldref";
            case CONSTANT_Methodref -> "Methodref";
            case CONSTANT_InterfaceMethodref -> "InterfaceMethodref";
            case CONSTANT_NameAndType -> "NameAndType";
            case CONSTANT_MethodHandle -> "MethodHandle";
            case CONSTANT_MethodType -> "MethodType";
            case CONSTANT_Dynamic -> "Dynamic";
            case CONSTANT_InvokeDynamic -> "InvokeDynamic";
            case CONSTANT_Module -> "Module";
            case CONSTANT_Package -> "Package";
            default -> null;
        };
    }
}
