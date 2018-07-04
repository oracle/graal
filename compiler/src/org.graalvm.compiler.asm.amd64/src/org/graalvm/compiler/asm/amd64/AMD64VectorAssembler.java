/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.asm.amd64;

import org.graalvm.compiler.asm.amd64.AVXKind.AVXSize;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * This class extends the AMD64 assembler with functions that emit instructions from the AVX
 * extension.
 */
public class AMD64VectorAssembler extends AMD64MacroAssembler {

    public AMD64VectorAssembler(TargetDescription target) {
        super(target);
        assert ((AMD64) target.arch).getFeatures().contains(CPUFeature.AVX);
    }

    @Override
    public void movflt(Register dst, Register src) {
        VexMoveOp.VMOVAPS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movflt(Register dst, AMD64Address src) {
        VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movflt(AMD64Address dst, Register src) {
        VexMoveOp.VMOVSS.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(Register dst, Register src) {
        VexMoveOp.VMOVAPD.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(Register dst, AMD64Address src) {
        VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
    }

    @Override
    public void movdbl(AMD64Address dst, Register src) {
        VexMoveOp.VMOVSD.emit(this, AVXSize.XMM, dst, src);
    }

}
