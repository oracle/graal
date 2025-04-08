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

package com.oracle.svm.webimage.wasm;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;

/**
 * Symbolic foreign call descriptor that is later linked to a Wasm function.
 * <p>
 * An instance of this descriptor by itself does not directly reference a specific Wasm function.
 * Code generation supports some set of descriptors for which it will insert a call to some Wasm
 * function (e.g. a {@code WasmFunctionTemplate} template function}). Any unknown descriptors will
 * fail the build, so creating a new instance of this class requires making codegen aware of it.
 * <p>
 * This allows Java code to call handwritten Wasm functions (imported Wasm functions can also be
 * called using {@code WasmImportForeignCallDescriptor}).
 */
public class WasmForeignCallDescriptor extends ForeignCallDescriptor {
    public WasmForeignCallDescriptor(String name, Class<?> resultType, Class<?>[] argumentTypes) {
        super(name, resultType, argumentTypes, CallSideEffect.HAS_SIDE_EFFECT, new LocationIdentity[]{LocationIdentity.any()}, false, true);
    }
}
