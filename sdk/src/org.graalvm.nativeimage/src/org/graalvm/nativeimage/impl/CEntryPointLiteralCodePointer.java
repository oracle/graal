/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.ComparableWord;

/*
 * This is a holder object that just stores the raw name and signature of the method we
 * reference. During native image generation, this object gets replaced with the actual pointer to
 * the method, and the necessary relocation information is generated.
 */
public class CEntryPointLiteralCodePointer implements CFunctionPointer {

    public final Class<?> definingClass;
    public final String methodName;
    public final Class<?>[] parameterTypes;

    public CEntryPointLiteralCodePointer(Class<?> definingClass, String methodName, Class<?>... parameterTypes) {
        this.definingClass = definingClass;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
    }

    @Override
    public boolean isNull() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @Override
    public boolean isNonNull() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }

    @Override
    public long rawValue() {
        throw new IllegalStateException("Cannot invoke method during native image generation");
    }
}
