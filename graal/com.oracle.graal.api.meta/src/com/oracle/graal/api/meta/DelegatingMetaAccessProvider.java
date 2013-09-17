/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

import java.lang.reflect.*;

/**
 * A {@link MetaAccessProvider} that delegates to another {@link MetaAccessProvider}.
 */
public class DelegatingMetaAccessProvider implements MetaAccessProvider {

    private final MetaAccessProvider delegate;

    public DelegatingMetaAccessProvider(MetaAccessProvider delegate) {
        this.delegate = delegate;
    }

    protected MetaAccessProvider delegate() {
        return delegate;
    }

    public ResolvedJavaType lookupJavaType(Class<?> clazz) {
        return delegate.lookupJavaType(clazz);
    }

    public ResolvedJavaMethod lookupJavaMethod(Method reflectionMethod) {
        return delegate.lookupJavaMethod(reflectionMethod);
    }

    public ResolvedJavaMethod lookupJavaConstructor(Constructor reflectionConstructor) {
        return delegate.lookupJavaConstructor(reflectionConstructor);
    }

    public ResolvedJavaField lookupJavaField(Field reflectionField) {
        return delegate.lookupJavaField(reflectionField);
    }

    public ResolvedJavaType lookupJavaType(Constant constant) {
        return delegate.lookupJavaType(constant);
    }

    public Signature parseMethodDescriptor(String methodDescriptor) {
        return delegate.parseMethodDescriptor(methodDescriptor);
    }

    public boolean constantEquals(Constant x, Constant y) {
        return delegate.constantEquals(x, y);
    }

    public Integer lookupArrayLength(Constant array) {
        return delegate.lookupArrayLength(array);
    }

    public Constant readUnsafeConstant(Kind kind, Object base, long displacement, boolean compressible) {
        return delegate.readUnsafeConstant(kind, base, displacement, compressible);
    }

    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
        return delegate.isReexecutable(descriptor);
    }

    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
        return delegate.getKilledLocations(descriptor);
    }

    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
        return delegate.canDeoptimize(descriptor);
    }
}
