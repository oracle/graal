/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A minimal implementation of {@link ResolvedJavaMethod} for use by libgraal.
 *
 * @see SnippetResolvedJavaType
 */
public final class SnippetResolvedJavaMethod implements ResolvedJavaMethod {
    private final String name;
    private final int modifiers;
    private final SnippetResolvedJavaType type;
    private final SnippetSignature signature;

    public SnippetResolvedJavaMethod(SnippetResolvedJavaType type, ResolvedJavaMethod method) {
        this.type = type;
        this.name = method.getName();
        this.modifiers = method.getModifiers();
        this.signature = new SnippetSignature(method.getSignature().toMethodDescriptor());
        assert format("%H.%n(%P)").equals(method.format("%H.%n(%P)"));
        assert format("%H.%n(%p)").equals(method.format("%H.%n(%p)"));
        assert format("%h.%n").equals(method.format("%h.%n"));
    }

    @Override
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return type;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public int getMaxLocals() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxStackSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSynthetic() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isVarArgs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isBridge() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDefault() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isClassInitializer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isConstructor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canBeStaticallyBound() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return new StackTraceElement(getDeclaringClass().getName(), name, null, -1);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reprofile() {

    }

    @Override
    public ConstantPool getConstantPool() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canBeInlined() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldBeInlined() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Constant getEncoding() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Annotation[] getAnnotations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SnippetResolvedJavaMethod that = (SnippetResolvedJavaMethod) o;
        return modifiers == that.modifiers &&
                        name.equals(that.name) &&
                        type.equals(that.type) &&
                        signature.equals(that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, modifiers, type, signature);
    }

    @Override
    public String toString() {
        return "SnippetResolvedJavaMethod{" +
                        "name='" + name + '\'' +
                        ", type=" + type +
                        '}';
    }
}
