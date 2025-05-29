/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import static jdk.vm.ci.amd64.AMD64.r13;
import static jdk.vm.ci.amd64.AMD64.r14;
import static jdk.vm.ci.amd64.AMD64.r15;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;

public final class AMD64ReservedRegisters extends ReservedRegisters {

    public static final Register THREAD_REGISTER = r15;
    public static final Register HEAP_BASE_REGISTER = r14;
    public static final Register CODE_BASE_REGISTER_CANDIDATE = r13;

    @Platforms(Platform.HOSTED_ONLY.class)
    AMD64ReservedRegisters() {
        super(AMD64.rsp, THREAD_REGISTER, HEAP_BASE_REGISTER, CODE_BASE_REGISTER_CANDIDATE);
    }

    @Override
    public boolean isReservedRegister(Register reg) {
        return super.isReservedRegister(reg) || reg.equals(AMD64.rip);
    }
}
