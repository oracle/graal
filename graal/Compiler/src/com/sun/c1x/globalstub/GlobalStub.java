/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.globalstub;

import static com.sun.cri.ci.CiKind.*;

import com.sun.cri.ci.*;

/**
 * A global stub is a shared routine that performs an operation on behalf of compiled code.
 * Typically the routine is too large to inline, is infrequent, or requires runtime support.
 * Global stubs are called with a callee-save convention; the global stub must save any
 * registers it may destroy and then restore them upon return. This allows the register
 * allocator to ignore calls to global stubs. Parameters to global stubs are
 * passed on the stack in order to preserve registers for the rest of the code.
 *
 * @author Thomas Wuerthinger
 * @author Ben L. Titzer
 */
public class GlobalStub {

    public enum Id {

        fneg(Float, Float),
        dneg(Double, Double),
        f2i(Int, Float),
        f2l(Long, Float),
        d2i(Int, Double),
        d2l(Long, Double);

        public final CiKind resultKind;
        public final CiKind[] arguments;

        private Id(CiKind resultKind, CiKind... args) {
            this.resultKind = resultKind;
            this.arguments = args;
        }
    }

    public final Id id;
    public final CiKind resultKind;
    public final Object stubObject;
    public final int argsSize;
    public final int[] argOffsets;
    public final int resultOffset;

    public GlobalStub(Id id, CiKind resultKind, Object stubObject, int argsSize, int[] argOffsets, int resultOffset) {
        this.id = id;
        this.resultKind = resultKind;
        this.stubObject = stubObject;
        this.argsSize = argsSize;
        this.argOffsets = argOffsets;
        this.resultOffset = resultOffset;
    }

}
