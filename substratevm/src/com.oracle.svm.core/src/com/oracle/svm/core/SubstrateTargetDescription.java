/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.EnumSet;

import com.oracle.svm.core.deopt.DeoptimizedFrame;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;

public class SubstrateTargetDescription extends TargetDescription {
    private final int deoptScratchSpace;

    public SubstrateTargetDescription(Architecture arch, boolean isMP, int stackAlignment, int implicitNullCheckLimit, boolean inlineObjects, int deoptScratchSpace) {
        super(arch, isMP, stackAlignment, implicitNullCheckLimit, inlineObjects);
        this.deoptScratchSpace = deoptScratchSpace;
    }

    /**
     * We include all flags that enable AMD64 CPU instructions as we want best possible performance
     * for the code.
     *
     * @return All the flags that enable AMD64 CPU instructions.
     */
    public static EnumSet<AMD64.Flag> allAMD64Flags() {
        return EnumSet.of(AMD64.Flag.UseCountLeadingZerosInstruction, AMD64.Flag.UseCountTrailingZerosInstruction);
    }

    /**
     * Returns the amount of scratch space which must be reserved for return value registers in
     * {@link DeoptimizedFrame}.
     */
    public int getDeoptScratchSpace() {
        return deoptScratchSpace;
    }
}
