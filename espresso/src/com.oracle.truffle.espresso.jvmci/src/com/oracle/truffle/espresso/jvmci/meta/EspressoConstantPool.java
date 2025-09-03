/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;

public final class EspressoConstantPool implements ConstantPool {
    public static final int INVOKEDYNAMIC = 186;

    @SuppressWarnings("unused")
    // Used by the VM
    private final EspressoResolvedInstanceType holder;

    public EspressoConstantPool(EspressoResolvedInstanceType holder) {
        this.holder = holder;
    }

    @Override
    public native int length();

    @Override
    public void loadReferencedType(int cpi, int opcode) {
        loadReferencedType(cpi, opcode, true);
    }

    @Override
    public void loadReferencedType(int cpi, int opcode, boolean initialize) {
        if (!loadReferencedType0(cpi, opcode)) {
            return;
        }
        if (initialize) {
            EspressoResolvedJavaType type = (EspressoResolvedJavaType) lookupReferencedType(cpi, opcode);
            type.initialize();
        }
    }

    private native boolean loadReferencedType0(int cpi, int opcode);

    @Override
    public native JavaType lookupReferencedType(int cpi, int opcode);

    @Override
    public JavaField lookupField(int cpi, ResolvedJavaMethod method, int opcode) {
        EspressoResolvedJavaField field;
        try {
            field = lookupResolvedField(cpi, (EspressoResolvedJavaMethod) method, opcode);
        } catch (Throwable t) {
            // ignore errors that can happen during type resolution
            field = null;
        }
        if (field != null) {
            return field;
        }
        String name = lookupName(cpi);
        String typeDescriptor = lookupDescriptor(cpi);
        JavaType type = runtime().lookupType(typeDescriptor, ((EspressoResolvedJavaMethod) method).getDeclaringClass(), false);
        JavaType fieldHolder = lookupReferencedType(cpi, opcode);
        return new UnresolvedJavaField(fieldHolder, name, type);
    }

    private native EspressoResolvedJavaField lookupResolvedField(int cpi, EspressoResolvedJavaMethod method, int opcode);

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        EspressoResolvedJavaMethod method;
        try {
            method = lookupResolvedMethod(cpi, opcode, (EspressoResolvedJavaMethod) caller);
        } catch (Throwable t) {
            // ignore errors that can happen during type resolution
            method = null;
        }
        if (method != null) {
            return method;
        }
        String name = lookupName(cpi);
        String rawSignature = lookupDescriptor(cpi);
        JavaType methodHolder;
        if (opcode == INVOKEDYNAMIC) {
            methodHolder = runtime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(MethodHandle.class);
        } else {
            methodHolder = lookupReferencedType(cpi, opcode);
        }
        return new UnresolvedJavaMethod(name, new EspressoSignature(rawSignature), methodHolder);
    }

    private native String lookupDescriptor(int cpi);

    private native String lookupName(int cpi);

    private native EspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, EspressoResolvedJavaMethod caller);

    @Override
    public native EspressoBootstrapMethodInvocation lookupBootstrapMethodInvocation(int cpi, int opcode);

    private native EspressoBootstrapMethodInvocation lookupIndyBootstrapMethodInvocation(int siteIndex);

    @Override
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
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

    private native int getNumIndyEntries();

    @Override
    public native JavaType lookupType(int cpi, int opcode);

    @Override
    public native String lookupUtf8(int cpi);

    @Override
    public Signature lookupSignature(int cpi) {
        String rawSignature = lookupDescriptor(cpi);
        return new EspressoSignature(rawSignature);
    }

    @Override
    public Object lookupConstant(int cpi) {
        return lookupConstant(cpi, true);
    }

    @Override
    public native Object lookupConstant(int cpi, boolean resolve);

    @Override
    public native JavaConstant lookupAppendix(int cpi, int opcode);

    private native byte getTagByteAt(int cpi);

    @SuppressWarnings("unused")
    private String getTagAt(int cpi) {
        // Used in tests
        switch (getTagByteAt(cpi)) {
            case 1:
                return "Utf8";
            case 3:
                return "Integer";
            case 4:
                return "Float";
            case 5:
                return "Long";
            case 6:
                return "Double";
            case 7:
                return "Class";
            case 8:
                return "String";
            case 9:
                return "Fieldref";
            case 10:
                return "Methodref";
            case 11:
                return "InterfaceMethodref";
            case 12:
                return "NameAndType";
            case 15:
                return "MethodHandle";
            case 16:
                return "MethodType";
            case 17:
                return "Dynamic";
            case 18:
                return "InvokeDynamic";
            case 19:
                return "Module";
            case 20:
                return "Package";
        }
        return null;
    }
}
