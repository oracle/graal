/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.GraalError;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * A minimal implementation of {@link ResolvedJavaType} for use by libgraal.
 *
 * Libgraal snippets have their own hierarchy of these types because they represent a distinct type
 * system that's overlapping with the platform type system. These types are also transient in the
 * graph and should disappear from the graph once the snippet is inlined and optimized.
 *
 * {@link jdk.vm.ci.hotspot.HotSpotResolvedJavaType HotSpotResolvedJavaType} can't be used here
 * because the Graal classes may not be available in the host VM and even if they are, loading them
 * causes unnecessary class loading. The Substrate type system could be used, but it is
 * implementation overkill for the purposes of libgraal. It would also introduce an unwanted
 * dependency from the Graal compiler to SVM.
 */
public final class SnippetResolvedJavaType implements ResolvedJavaType {
    private final Class<?> javaClass;
    private SnippetResolvedJavaMethod[] methods;
    private SnippetResolvedJavaType arrayOfType;

    public SnippetResolvedJavaType(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    public void setArrayOfType(SnippetResolvedJavaType arrayOfType) {
        assert this.arrayOfType == null || this.arrayOfType.equals(arrayOfType);
        this.arrayOfType = arrayOfType;
    }

    @LibGraalSupport.HostedOnly
    synchronized SnippetResolvedJavaMethod add(SnippetResolvedJavaMethod method) {
        if (methods == null) {
            methods = new SnippetResolvedJavaMethod[]{method};
            return method;
        }
        // This in inefficient but is only used while building
        // libgraal for a small number of methods.
        int index = Arrays.asList(methods).indexOf(method);
        if (index == -1) {
            SnippetResolvedJavaMethod[] newMethods = Arrays.copyOf(methods, methods.length + 1);
            newMethods[methods.length] = method;
            methods = newMethods;
            return method;
        } else {
            return methods[index];
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SnippetResolvedJavaType that = (SnippetResolvedJavaType) o;
        return Objects.equals(javaClass, that.javaClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(javaClass);
    }

    @Override
    public boolean hasFinalizer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Assumptions.AssumptionResult<Boolean> hasFinalizableSubclass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getModifiers() {
        return javaClass.getModifiers();
    }

    @Override
    public boolean isInterface() {
        return javaClass.isInterface();
    }

    @Override
    public boolean isInstanceClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimitive() {
        return javaClass.isPrimitive();
    }

    @Override
    public boolean isEnum() {
        return javaClass.isEnum();
    }

    @Override
    public boolean isInitialized() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void initialize() {
    }

    @Override
    public void link() {
    }

    @Override
    public boolean isLinked() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof SnippetResolvedJavaType) {
            return javaClass.isAssignableFrom(((SnippetResolvedJavaType) other).javaClass);
        }
        return false;
    }

    @Override
    public ResolvedJavaType lookupType(UnresolvedJavaType unresolvedJavaType, boolean resolve) {
        // UnresolvedJavaTypes can't be resolved relative to SnippetResolvedJavatypes
        throw new NoClassDefFoundError();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResolvedJavaType getHostClass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInstance(JavaConstant obj) {
        if (obj instanceof SnippetObjectConstant) {
            return javaClass.isAssignableFrom(((SnippetObjectConstant) obj).object.getClass());
        }
        return false;
    }

    @Override
    public ResolvedJavaType getSuperclass() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaType[] getInterfaces() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaType getSingleImplementor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaType findLeastCommonAncestor(ResolvedJavaType otherType) {
        if (getClass() != otherType.getClass()) {
            throw new InternalError("mixing type systems: " + this + " " + otherType);
        }
        if (this.equals(otherType)) {
            return this;
        }
        throw new UnsupportedOperationException("LCA: " + this + " " + otherType);
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaType> findLeafConcreteSubtype() {
        if (isLeaf()) {
            return new Assumptions.AssumptionResult<>(this);
        }
        return null;
    }

    @Override
    public boolean isJavaLangObject() {
        return false;
    }

    @Override
    public String getName() {
        return MetaUtil.toInternalName(javaClass.getName());
    }

    @Override
    public ResolvedJavaType getComponentType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaType getArrayClass() {
        assert arrayOfType != null;
        return arrayOfType;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public ResolvedJavaType resolve(ResolvedJavaType accessingClass) {
        return this;
    }

    @Override
    public ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Assumptions.AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod method) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaField[] getInstanceFields(boolean includeSuperclasses) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaField[] getStaticFields() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaField findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSourceFileName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isLocal() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isMember() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredConstructors() {
        return getDeclaredConstructors(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return getDeclaredMethods(true);
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods(boolean forceLink) {
        GraalError.guarantee(!forceLink, "only use getDeclaredMethods without forcing to link, because linking can throw LinkageError");
        if (methods == null) {
            return new ResolvedJavaMethod[0];
        }
        return methods.clone();
    }

    @Override
    public List<ResolvedJavaMethod> getAllMethods(boolean forceLink) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedJavaMethod getClassInitializer() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCloneableWithAllocation() {
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
    public boolean isArray() {
        return javaClass.isArray();
    }

    @Override
    public String toString() {
        return "SnippetResolvedJavaType{" +
                        "javaClass=" + javaClass +
                        '}';
    }
}
