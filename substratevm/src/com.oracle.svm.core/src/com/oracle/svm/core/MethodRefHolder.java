/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.guest.staging.meta.MethodOffset;
import com.oracle.svm.guest.staging.meta.MethodPointer;
import com.oracle.svm.guest.staging.meta.MethodRef;
import com.oracle.svm.core.meta.MethodRefUtils;
import com.oracle.svm.core.meta.MethodRefSupport;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Immutable image-heap carrier for method entry points. When relative code pointers are enabled,
 * this stores a {@link MethodOffset}. Otherwise, it stores a {@link MethodPointer}. Runtime access
 * reconstructs a callable absolute pointer from the stored representation.
 */
public final class MethodRefHolder {
    private final MethodRef methodRef;

    @Platforms(Platform.HOSTED_ONLY.class)
    public MethodRefHolder(MethodRef methodRef) {
        this.methodRef = Objects.requireNonNull(methodRef);
        assert (SubstrateOptions.useRelativeCodePointers() ? methodRef instanceof MethodOffset : methodRef instanceof MethodPointer) : //
                        "MethodRefHolder's input must be of type " + (SubstrateOptions.useRelativeCodePointers() ? MethodOffset.class.getSimpleName() : MethodPointer.class.getSimpleName());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaMethod getMethod() {
        return MethodRefSupport.getMethod(methodRef);
    }

    public <T extends CFunctionPointer> T getFunctionPointer() {
        return MethodRefUtils.asCodePointer(methodRef);
    }
}
