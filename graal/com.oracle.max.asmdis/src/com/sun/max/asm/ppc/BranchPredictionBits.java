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
package com.sun.max.asm.ppc;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The branch prediction values for the conditional branches whose encoding includes
 * a hint about whether the branch is likely to be taken or is likely not to be taken.
 */
public final class BranchPredictionBits extends OptionSuffixSymbolicArgument {

    private BranchPredictionBits(int value, String externalMnemonicSuffix) {
        super(value, externalMnemonicSuffix);
    }

    /**
     * No hint is given.
     */
    public static final BranchPredictionBits NONE = new BranchPredictionBits(0, "");

    /**
     * The branch is very likely to be taken.
     */
    public static final BranchPredictionBits PT = new BranchPredictionBits(3, "++");

    /**
     * The branch is very likely <b>not</b> to be taken.
     */
    public static final BranchPredictionBits PN = new BranchPredictionBits(2, "--");

    public static final Symbolizer<BranchPredictionBits> SYMBOLIZER = Symbolizer.Static.initialize(BranchPredictionBits.class);
}
