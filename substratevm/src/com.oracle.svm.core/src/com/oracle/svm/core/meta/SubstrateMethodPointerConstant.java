/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.meta.VMConstant;

public class SubstrateMethodPointerConstant implements VMConstant {

    /*
     * This entire class should be hosted-only, but with runtime compilation the analysis encounters
     * it in type checks in the compiler backend. We mark this field hosted-only to fail on any
     * actual runtime usage of this class.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final MethodPointer pointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateMethodPointerConstant(MethodPointer pointer) {
        this.pointer = Objects.requireNonNull(pointer);
    }

    public MethodPointer pointer() {
        return pointer;
    }

    @Override
    public boolean isDefaultForKind() {
        return false;
    }

    @Override
    public String toValueString() {
        return "SVMMethodPointerConstant";
    }

    @Override
    public String toString() {
        return "method: " + pointer.getMethod().format("%H.%n");
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SubstrateMethodPointerConstant)) {
            return false;
        }
        SubstrateMethodPointerConstant that = (SubstrateMethodPointerConstant) obj;
        return this == obj || this.pointer.getMethod().equals(that.pointer.getMethod());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pointer.getMethod());
    }
}
