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

package com.oracle.svm.hosted.webimage.wasm;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.hosted.webimage.wasm.ast.id.KnownIds;

import jdk.vm.ci.code.Register;

/**
 * Reserved "registers" for WebAssembly.
 * <p>
 * WebAssembly does not have registers, but many places in SVM assume that they exist (e.g. for
 * accessing the stack). Because of that we use a set of pseudo-registers which are then mapped to
 * global variables or similar during compilation.
 */
public class WebImageWasmReservedRegisters extends ReservedRegisters {

    public static final Register.RegisterCategory PSEUDO = new Register.RegisterCategory("PSEUDO");

    /**
     * "Register" holding the stack pointer. Corresponds to {@link KnownIds#stackPointer}.
     */
    public static final Register FRAME_REGISTER = new Register(0, 0, "stackPointer", PSEUDO);

    @Platforms(Platform.HOSTED_ONLY.class)
    protected WebImageWasmReservedRegisters() {
        super(FRAME_REGISTER, null, null, null);
    }
}
