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
package jdk.graal.compiler.lir.alloc.verifier.exceptions;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.alloc.verifier.RAVInstruction;
import jdk.vm.ci.code.Register;

/**
 * Invalid register was used in allocation as defined by the
 * {@link jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig}.
 */
@SuppressWarnings("serial")
public class InvalidRegisterUsedException extends RAVException {
    public final Register register;

    public InvalidRegisterUsedException(Register register, RAVInstruction.Base instruction, BasicBlock<?> block) {
        super(getErrorMessage(register), instruction, block);
        this.register = register;
    }

    static String getErrorMessage(Register register) {
        return "Register " + register + " is not allowed to be used by RegisterAllocatorConfig";
    }
}
