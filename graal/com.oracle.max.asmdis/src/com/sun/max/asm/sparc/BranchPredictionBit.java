/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.sparc;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * An optional (suffix) argument to a branch with prediction assembler instruction
 * specifying if the prediction bit is to be set. For example:
 * <pre>bgu,pt <i>i_or_x_cc</i>, <i>label</i></pre>
 */
public final class BranchPredictionBit extends OptionSuffixSymbolicArgument {

    private BranchPredictionBit(int value, String externalMnemonicSuffix) {
        super(value, externalMnemonicSuffix);
    }

    /**
     * The prediction bit is not set, indicating that the branch is not likely to be taken.
     */
    public static final BranchPredictionBit PN = new BranchPredictionBit(0, ",pn");

    /**
     * The prediction bit is set, indicating that the branch is likely to be taken.
     */
    public static final BranchPredictionBit PT = new BranchPredictionBit(1, ",pt");

    public static final Symbolizer<BranchPredictionBit> SYMBOLIZER = Symbolizer.Static.initialize(BranchPredictionBit.class);
}
