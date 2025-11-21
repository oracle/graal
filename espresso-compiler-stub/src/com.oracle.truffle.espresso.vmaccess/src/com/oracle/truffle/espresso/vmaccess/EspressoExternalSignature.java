/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.vmaccess;

import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedPrimitiveType;
import com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoSignature;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;

final class EspressoExternalSignature extends AbstractEspressoSignature {
    private final EspressoExternalVMAccess access;

    EspressoExternalSignature(EspressoExternalVMAccess access, String rawSignature) {
        super(rawSignature);
        this.access = access;
    }

    @Override
    protected JavaType lookupType0(String descriptor, AbstractEspressoResolvedInstanceType accessingClass) {
        return access.lookupType(descriptor, accessingClass, false);
    }

    @Override
    protected AbstractEspressoResolvedPrimitiveType lookupPrimitiveType0(JavaKind kind) {
        return access.forPrimitiveKind(kind);
    }
}
