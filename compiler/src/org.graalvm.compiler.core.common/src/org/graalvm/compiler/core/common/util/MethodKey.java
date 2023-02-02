/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Key type for a map when {@link ResolvedJavaMethod} cannot be used due to the
 * {@link ResolvedJavaMethod} keys potentially becoming invalid while the map is still in use. For
 * example, a JVMCI implementation can scope the validity of a {@link ResolvedJavaMethod} to a
 * single compilation such that VM resources held by the {@link ResolvedJavaMethod} object can be
 * released once compilation ends.
 */
public final class MethodKey {

    private final String declaringClass;
    private final String name;
    private final String descriptor;
    private final int hashCode;

    /**
     * Creates a key representing {@code method}.
     */
    public MethodKey(ResolvedJavaMethod method) {
        this.declaringClass = method.getDeclaringClass().getName();
        this.name = method.getName();
        this.descriptor = method.getSignature().toMethodDescriptor();
        this.hashCode = method.hashCode();
    }

    public String getName() {
        return name;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getDescriptor() {
        return descriptor;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MethodKey) {
            MethodKey that = (MethodKey) obj;
            return this.hashCode == that.hashCode &&
                            this.name.equals(that.name) &&
                            this.declaringClass.equals(that.declaringClass) &&
                            this.descriptor.equals(that.descriptor);
        }
        return false;
    }

    @Override
    public String toString() {
        return declaringClass + "." + name + descriptor;
    }
}
