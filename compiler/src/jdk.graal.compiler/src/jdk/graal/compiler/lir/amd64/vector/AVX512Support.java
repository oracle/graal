/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.amd64.vector;

import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public interface AVX512Support {
    /**
     * Denotes the optional opmask register that allows additional masking for write operations. The
     * {@link Value#getValueKind} should correspond to the operand registers in such a way that all
     * lanes may be masked (e.g. 8 bits for 512 bit QWORDs in a ZMM register). May be
     * {@link Value#ILLEGAL} if no register should be used.
     *
     * @return a register that contains the opmask operand or {@link Value#ILLEGAL}
     */
    AllocatableValue getOpmask();

    /**
     * Safely determines the register to use for the opmask operand of the instruction. If no
     * register is specified ({@link Value#ILLEGAL}), no opmask register ({@link Register#None}) is
     * returned.
     *
     * @return the register for the opmask operand
     */
    default Register getOpmaskRegister() {
        Value mask = getOpmask();
        return Value.ILLEGAL.equals(mask) ? Register.None : asRegister(mask);
    }
}
