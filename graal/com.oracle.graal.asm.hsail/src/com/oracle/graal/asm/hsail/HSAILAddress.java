/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.asm.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;

/**
 * Represents an address in target machine memory, specified via some combination of a base register
 * and a displacement.
 */
public final class HSAILAddress extends AbstractAddress {

    private final Register base;
    private final long displacement;

    /**
     * Creates an {@link HSAILAddress} with given base register and no displacement.
     * 
     * 
     * @param base the base register
     */
    public HSAILAddress(Register base) {
        this(base, 0);
    }

    /**
     * Creates an {@link HSAILAddress} with given base register and a displacement. This is the most
     * general constructor.
     * 
     * @param base the base register
     * @param displacement the displacement
     */
    public HSAILAddress(Register base, long displacement) {
        this.base = base;
        this.displacement = displacement;
    }

    /**
     * @return Base register that defines the start of the address computation. If not present, is
     *         denoted by {@link Value#ILLEGAL}.
     */
    public Register getBase() {
        return base;
    }

    /**
     * @return Optional additive displacement.
     */
    public long getDisplacement() {
        return displacement;
    }

}
