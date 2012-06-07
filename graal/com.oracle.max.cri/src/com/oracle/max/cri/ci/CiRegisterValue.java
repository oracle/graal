/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.cri.ci;

import com.oracle.max.cri.ri.*;

/**
 * Denotes a register that stores a value of a fixed kind. There is exactly one (canonical) instance of {@code
 * CiRegisterValue} for each ({@link CiRegister}, {@link RiKind}) pair. Use {@link CiRegister#asValue(RiKind)} to
 * retrieve the canonical {@link CiRegisterValue} instance for a given (register,kind) pair.
 */
public final class CiRegisterValue extends CiValue {
    private static final long serialVersionUID = 7999341472196897163L;

    /**
     * The register.
     */
    public final CiRegister reg;

    /**
     * Should only be called from {@link CiRegister#CiRegister} to ensure canonicalization.
     */
    protected CiRegisterValue(RiKind kind, CiRegister register) {
        super(kind);
        this.reg = register;
    }

    @Override
    public int hashCode() {
        return (reg.number << 4) ^ kind.ordinal();
    }

    @Override
    public String toString() {
        return reg.name + kindSuffix();
    }
}
