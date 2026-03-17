/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;

/**
 * Wrap around {@link RegisterValue} to only index
 * by the name of the {@link Register} it holds.
 */
public class RARegister extends RAValue {
    protected RegisterValue registerValue;

    protected RARegister(RegisterValue registerValue) {
        super(registerValue);

        this.registerValue = registerValue;
    }

    public RegisterValue getRegisterValue() {
        return registerValue;
    }

    public Register getRegister() {
        return registerValue.getRegister();
    }

    @Override
    public RARegister asRegister() {
        return this;
    }

    @Override
    public boolean isRegister() {
        return true;
    }

    @Override
    public int hashCode() {
        return this.registerValue.getRegister().hashCode();
    }

    /**
     * Equal RegisterValue on it's Register, not Register and kind,
     * otherwise same as Value.
     *
     * @param other The reference object with which to compare.
     * @return Are said values equal?
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof RARegister otherReg) {
            return this.registerValue.getRegister().equals(otherReg.registerValue.getRegister());
        }

        return false;
    }

    @Override
    public String toString() {
        return this.registerValue.getRegister().toString();
    }
}
