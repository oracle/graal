/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.aarch64;

import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.phases.common.AddressLoweringPhase.AddressLowering;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;

public class AArch64AddressLowering extends AddressLowering {

    private final CodeCacheProvider codeCache;

    public AArch64AddressLowering(CodeCacheProvider codeCache) {
        this.codeCache = codeCache;
    }

    @Override
    public AddressNode lower(ValueNode address) {
        return lower(address, null);
    }

    @Override
    public AddressNode lower(ValueNode base, ValueNode offset) {
        asImmediate(base);
        throw JVMCIError.unimplemented();
    }

    private JavaConstant asImmediate(ValueNode value) {
        JavaConstant c = value.asJavaConstant();
        if (c != null && c.getJavaKind().isNumericInteger() && !codeCache.needsDataPatch(c)) {
            return c;
        } else {
            return null;
        }
    }
}
