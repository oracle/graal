/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.cri.ci;

/**
 * Denotes a register that stores a value of a fixed kind. There is exactly one (canonical) instance of {@code
 * CiRegisterValue} for each ({@link CiRegister}, {@link CiKind}) pair. Use {@link CiRegister#asValue(CiKind)} to
 * retrieve the canonical {@link CiRegisterValue} instance for a given (register,kind) pair.
 */
public final class CiRegisterValue extends CiValue {

    /**
     * The register.
     */
    public final CiRegister reg;

    /**
     * Should only be called from {@link CiRegister#CiRegister} to ensure canonicalization.
     */
    CiRegisterValue(CiKind kind, CiRegister register) {
        super(kind);
        this.reg = register;
    }

    @Override
    public int hashCode() {
        return kind.ordinal() ^ reg.number;
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public boolean equalsIgnoringKind(CiValue other) {
        if (other instanceof CiRegisterValue) {
            return ((CiRegisterValue) other).reg == reg;
        }
        return false;
    }

    @Override
    public String name() {
        return reg.name;
    }

    @Override
    public CiRegister asRegister() {
        return reg;
    }
}
