/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.lir.*;

/**
 * Base class for operations that preserve a set of registers.
 */
public abstract class AMD64RegistersPreservationOp extends AMD64LIRInstruction {

    /**
     * Prunes the set of registers preserved by this operation to exclude those in {@code ignored}
     * and updates {@code debugInfo} with a {@linkplain DebugInfo#getCalleeSaveInfo() description}
     * of where each preserved register is saved.
     */
    public abstract void update(Set<Register> ignored, DebugInfo debugInfo, FrameMap frameMap);
}
