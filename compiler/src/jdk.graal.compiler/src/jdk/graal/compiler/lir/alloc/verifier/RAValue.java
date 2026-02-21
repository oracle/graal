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

import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * Wrapper around Value to change how indexing
 * in data structures like Map or Set is done.
 * <p>
 * Register is indexed by its name without the kind.
 * Variable is indexed by its number index without the kind.
 * Stack slots and constants remain as is, because the kind
 * does not mess with indexing.
 * </p>
 */
public class RAValue {
    /**
     * Create a new RAValue instance from Value.
     *
     * @param value Value we are wrapping
     * @return Instance of RAValue
     */
    public static RAValue create(Value value) {
        if (LIRValueUtil.isVariable(value)) {
            return new RAVariable(LIRValueUtil.asVariable(value));
        }

        return new RAValue(value);
    }

    protected Value value;

    protected RAValue(Value value) {
        this.value = value;
    }

    public Value getValue() {
        return this.value;
    }

    public boolean isIllegal() {
        return Value.ILLEGAL.equals(value);
    }

    public RAVariable asVariable() {
        return (RAVariable) this;
    }

    public boolean isVariable() {
        return false;
    }

    @Override
    public int hashCode() {
        if (this.value instanceof RegisterValue registerValue) {
            return registerValue.getRegister().hashCode();
        }

        return this.value.hashCode();
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
        if (other instanceof RAValue otherValueWrap) {
            if (this.value instanceof RegisterValue thisReg && otherValueWrap.value instanceof RegisterValue otherReg) {
                return thisReg.getRegister().equals(otherReg.getRegister());
            }

            return this.value.equals(otherValueWrap.value);
        }

        return false;
    }

    @Override
    public String toString() {
        if (value instanceof RegisterValue regValue) {
            return regValue.getRegister().toString();
        }

        return value.toString();
    }
}
