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

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;

import java.lang.invoke.MethodHandle;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoConstantPool extends AbstractEspressoConstantPool {
    @SuppressWarnings("unused")
    // Used by the VM
    private final EspressoResolvedInstanceType holder;

    EspressoConstantPool(EspressoResolvedInstanceType holder) {
        this.holder = holder;
    }

    @Override
    public native int length();

    @Override
    public native JavaType lookupReferencedType(int cpi, int opcode);

    @Override
    public native EspressoBootstrapMethodInvocation lookupBootstrapMethodInvocation(int cpi, int opcode);

    @Override
    public JavaType lookupType(int cpi, @SuppressWarnings("unused") int opcode) {
        return lookupType(cpi);
    }

    native JavaType lookupType(int cpi);

    @Override
    public native String lookupUtf8(int cpi);

    @Override
    public native Object lookupConstant(int cpi, boolean resolve);

    @Override
    public native JavaConstant lookupAppendix(int cpi, int opcode);

    @Override
    protected native boolean loadReferencedType0(int cpi, int opcode);

    @Override
    protected EspressoResolvedJavaField lookupResolvedField(int cpi, AbstractEspressoResolvedJavaMethod method, int opcode) {
        return lookupResolvedField(cpi, (EspressoResolvedJavaMethod) method, opcode);
    }

    private native EspressoResolvedJavaField lookupResolvedField(int cpi, EspressoResolvedJavaMethod method, int opcode);

    @Override
    protected native String lookupDescriptor(int cpi);

    @Override
    protected native String lookupName(int cpi);

    @Override
    protected EspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, AbstractEspressoResolvedJavaMethod caller) {
        return lookupResolvedMethod(cpi, opcode, (EspressoResolvedJavaMethod) caller);
    }

    private native EspressoResolvedJavaMethod lookupResolvedMethod(int cpi, int opcode, EspressoResolvedJavaMethod caller);

    @Override
    protected native EspressoBootstrapMethodInvocation lookupIndyBootstrapMethodInvocation(int siteIndex);

    @Override
    protected native int getNumIndyEntries();

    @Override
    protected native byte getTagByteAt(int cpi);

    @Override
    protected ResolvedJavaType getMethodHandleType() {
        return runtime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(MethodHandle.class);
    }

    @Override
    protected JavaType lookupFieldType(int cpi, AbstractEspressoResolvedInstanceType accessingType) {
        String typeDescriptor = lookupDescriptor(cpi);
        return runtime().lookupType(typeDescriptor, (EspressoResolvedInstanceType) accessingType, false);
    }

    @Override
    protected EspressoSignature getSignature(String rawSignature) {
        return new EspressoSignature(rawSignature);
    }
}
