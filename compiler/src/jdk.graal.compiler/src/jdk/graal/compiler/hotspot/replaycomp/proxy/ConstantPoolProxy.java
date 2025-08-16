/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

import java.util.List;

//JaCoCo Exclude

public final class ConstantPoolProxy extends CompilationProxyBase implements ConstantPool {
    ConstantPoolProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(ConstantPool.class, name, params);
    }

    private static final SymbolicMethod lengthMethod = method("length");
    private static final InvokableMethod lengthInvokable = (receiver, args) -> ((ConstantPool) receiver).length();

    @Override
    public int length() {
        return (int) handle(lengthMethod, lengthInvokable);
    }

    private static final SymbolicMethod loadReferencedTypeMethod = method("loadReferencedType", int.class, int.class);
    private static final InvokableMethod loadReferencedTypeInvokable = (receiver, args) -> {
        ((ConstantPool) receiver).loadReferencedType((int) args[0], (int) args[1]);
        return null;
    };

    @Override
    public void loadReferencedType(int rawIndex, int opcode) {
        handle(loadReferencedTypeMethod, loadReferencedTypeInvokable, rawIndex, opcode);
    }

    private static final SymbolicMethod lookupReferencedTypeMethod = method("lookupReferencedType", int.class, int.class);
    private static final InvokableMethod lookupReferencedTypeInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupReferencedType((int) args[0], (int) args[1]);

    @Override
    public JavaType lookupReferencedType(int rawIndex, int opcode) {
        return (JavaType) handle(lookupReferencedTypeMethod, lookupReferencedTypeInvokable, rawIndex, opcode);
    }

    private static final SymbolicMethod lookupFieldMethod = method("lookupField", int.class, ResolvedJavaMethod.class, int.class);
    private static final InvokableMethod lookupFieldInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupField((int) args[0], (ResolvedJavaMethod) args[1], (int) args[2]);

    @Override
    public JavaField lookupField(int rawIndex, ResolvedJavaMethod method, int opcode) {
        return (JavaField) handle(lookupFieldMethod, lookupFieldInvokable, rawIndex, method, opcode);
    }

    public static final SymbolicMethod lookupMethodMethod = method("lookupMethod", int.class, int.class, ResolvedJavaMethod.class);
    public static final InvokableMethod lookupMethodInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupMethod((int) args[0], (int) args[1], (ResolvedJavaMethod) args[2]);

    @Override
    public JavaMethod lookupMethod(int cpi, int opcode, ResolvedJavaMethod caller) {
        return (JavaMethod) handle(lookupMethodMethod, lookupMethodInvokable, cpi, opcode, caller);
    }

    private static final SymbolicMethod lookupBootstrapMethodInvocationMethod = method("lookupBootstrapMethodInvocation", int.class, int.class);
    private static final InvokableMethod lookupBootstrapMethodInvocationInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupBootstrapMethodInvocation((int) args[0], (int) args[1]);

    @Override
    public BootstrapMethodInvocation lookupBootstrapMethodInvocation(int index, int opcode) {
        return (BootstrapMethodInvocation) handle(lookupBootstrapMethodInvocationMethod, lookupBootstrapMethodInvocationInvokable, index, opcode);
    }

    private static final SymbolicMethod lookupBootstrapMethodInvocationsMethod = method("lookupBootstrapMethodInvocations", boolean.class);
    private static final InvokableMethod lookupBootstrapMethodInvocationsInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupBootstrapMethodInvocations((boolean) args[0]);

    @Override
    @SuppressWarnings("unchecked")
    public List<BootstrapMethodInvocation> lookupBootstrapMethodInvocations(boolean invokeDynamic) {
        return (List<BootstrapMethodInvocation>) handle(lookupBootstrapMethodInvocationsMethod, lookupBootstrapMethodInvocationsInvokable, invokeDynamic);
    }

    private static final SymbolicMethod lookupTypeMethod = method("lookupType", int.class, int.class);
    private static final InvokableMethod lookupTypeInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupType((int) args[0], (int) args[1]);

    @Override
    public JavaType lookupType(int cpi, int opcode) {
        return (JavaType) handle(lookupTypeMethod, lookupTypeInvokable, cpi, opcode);
    }

    private static final SymbolicMethod lookupUtf8Method = method("lookupUtf8", int.class);
    private static final InvokableMethod lookupUtf8Invokable = (receiver, args) -> ((ConstantPool) receiver).lookupUtf8((int) args[0]);

    @Override
    public String lookupUtf8(int cpi) {
        return (String) handle(lookupUtf8Method, lookupUtf8Invokable, cpi);
    }

    private static final SymbolicMethod lookupSignatureMethod = method("lookupSignature", int.class);
    private static final InvokableMethod lookupSignatureInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupSignature((int) args[0]);

    @Override
    public Signature lookupSignature(int cpi) {
        return (Signature) handle(lookupSignatureMethod, lookupSignatureInvokable, cpi);
    }

    private static final SymbolicMethod lookupConstantMethod = method("lookupConstant", int.class);
    private static final InvokableMethod lookupConstantInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupConstant((int) args[0]);

    @Override
    public Object lookupConstant(int cpi) {
        return handle(lookupConstantMethod, lookupConstantInvokable, cpi);
    }

    private static final SymbolicMethod lookupConstantBooleanMethod = method("lookupConstant", int.class, boolean.class);
    private static final InvokableMethod lookupConstantBooleanInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupConstant((int) args[0], (boolean) args[1]);

    @Override
    public Object lookupConstant(int cpi, boolean resolve) {
        return handle(lookupConstantBooleanMethod, lookupConstantBooleanInvokable, cpi, resolve);
    }

    private static final SymbolicMethod lookupAppendixMethod = method("lookupAppendix", int.class, int.class);
    private static final InvokableMethod lookupAppendixInvokable = (receiver, args) -> ((ConstantPool) receiver).lookupAppendix((int) args[0], (int) args[1]);

    @Override
    public JavaConstant lookupAppendix(int rawIndex, int opcode) {
        return (JavaConstant) handle(lookupAppendixMethod, lookupAppendixInvokable, rawIndex, opcode);
    }
}
