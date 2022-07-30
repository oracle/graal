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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;

/**
 * Key type for a map from {@link HotSpotResolvedJavaMethod} objects to some value. A
 * {@link HotSpotResolvedJavaMethod} object cannot be used in a map that outlives a single
 * compilation as it is not guaranteed to be valid after the compilation.
 */
public final class HotSpotMethodKey {
    private final String declaringClass;
    private final String name;
    private final String descriptor;

    /**
     * Cached value of {@code HotSpotResolvedJavaMethodImpl.hashCode()} which is a function of the
     * underlying {@code Method*}.
     */
    private final int hashCode;

    public HotSpotMethodKey(HotSpotResolvedJavaMethod method) {
        this.declaringClass = method.getDeclaringClass().getName();
        this.name = method.getName();
        this.descriptor = method.getSignature().toMethodDescriptor();
        this.hashCode = method.hashCode();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HotSpotMethodKey) {
            HotSpotMethodKey that = (HotSpotMethodKey) obj;
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