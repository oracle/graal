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
 * An optional (suffix) argument to a branch assembler instruction specifying
 * if the instruction in the delay slot of the branch will be executed. For example:
 * <pre>bne,a <i>label</i></pre>
 */
public final class AnnulBit extends OptionSuffixSymbolicArgument {

    private AnnulBit(int value, String externalMnemonicSuffix) {
        super(value, externalMnemonicSuffix);
    }

    /**
     * The annul bit is not set.
     */
    public static final AnnulBit NO_A = new AnnulBit(0, "");

    /**
     * The annul bit is set.
     */
    public static final AnnulBit A = new AnnulBit(1, ",a");

    public static final Symbolizer<AnnulBit> SYMBOLIZER = Symbolizer.Static.initialize(AnnulBit.class);

}
