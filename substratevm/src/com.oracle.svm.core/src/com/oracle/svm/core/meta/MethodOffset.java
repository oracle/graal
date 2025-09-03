/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.Objects;

import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * The offset of the compiled code of a method from the {@linkplain KnownIntrinsics#codeBase() code
 * base}.
 */
public final class MethodOffset implements MethodRef {
    private final ResolvedJavaMethod method;

    public MethodOffset(ResolvedJavaMethod method) {
        this.method = Objects.requireNonNull(method);
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
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
