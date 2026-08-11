/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import static com.oracle.svm.shared.util.VMError.shouldNotReachHere;

import java.util.Objects;

import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The offset of the compiled code of a method from the {@linkplain KnownIntrinsics#codeBase() code
 * base}.
 *
 * Do not use this concrete class in image runtime code. Use one of its superinterfaces instead.
 */
public final class MethodOffset implements MethodRef {
    public static final boolean DEFAULT_PERMIT_REWRITE_TO_PLT = true;

    @Platforms(HOSTED_ONLY.class) //
    private final ResolvedJavaMethod method;

    @Platforms(HOSTED_ONLY.class)
    public MethodOffset(ResolvedJavaMethod method) {
        this(method, true);
    }

    /** @see MethodPointer#MethodPointer(ResolvedJavaMethod, boolean) */
    @Platforms(HOSTED_ONLY.class)
    public MethodOffset(ResolvedJavaMethod method, boolean permitsRewriteToPLT) {
        VMError.guarantee(permitsRewriteToPLT == DEFAULT_PERMIT_REWRITE_TO_PLT, "Not implemented: all calls to methods in PLT/GOT are currently redirected");
        this.method = Objects.requireNonNull(method);
    }

    @Override
    @Platforms(HOSTED_ONLY.class)
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Platforms(HOSTED_ONLY.class)
    @SuppressWarnings("static-method")
    public boolean permitsRewriteToPLT() {
        return DEFAULT_PERMIT_REWRITE_TO_PLT;
    }

    @Override
    public long rawValue() {
        throw shouldNotReachHere("must not be called in hosted mode");
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public boolean equals(Object obj) {
        throw VMError.shouldNotReachHere("equals() not supported on words");
    }

    @Override
    public int hashCode() {
        throw VMError.shouldNotReachHere("hashCode() not supported on words");
    }
}
