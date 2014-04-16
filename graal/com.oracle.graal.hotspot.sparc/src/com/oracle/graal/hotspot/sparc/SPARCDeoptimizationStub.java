/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.sparc.SPARC.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;

final class SPARCDeoptimizationStub extends DeoptimizationStub {

    private RegisterConfig registerConfig;

    public SPARCDeoptimizationStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(providers, target, linkage);
        registerConfig = new SPARCHotSpotRegisterConfig(target, new Register[]{o0, o1, o2, o3, o4, o5, o7, l0, l1, l2, l3, l4, l5, l6, l7, i0, i1, i2, i3, i4, i5, f0, f1, f2, f3, f4, f5, f6, f7});
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }
}
