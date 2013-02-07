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
package com.oracle.graal.asm.sparc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.sparc.*;

/**
 * This class implements an assembler that can encode most SPARC instructions.
 */
public class SPARCAssembler extends AbstractAssembler {

    @SuppressWarnings("unused")
    public SPARCAssembler(TargetDescription target) {
        super(target);
        // TODO Auto-generated constructor stub
        SPARC sparc;
    }

    @Override
    public void align(int modulus) {
        // SPARC: Implement alignment.
    }

    @Override
    public void jmp(Label l) {
        // SPARC: Implement jump.
    }

    @Override
    protected void patchJumpTarget(int branch, int jumpTarget) {
        // SPARC: Implement patching of jump target.
    }

    @Override
    public void bangStack(int disp) {
        // SPARC: Implement stack banging.
    }
}
