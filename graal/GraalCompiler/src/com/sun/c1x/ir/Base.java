/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.debug.*;
import com.sun.cri.ci.*;

/**
 * The {@code Base} instruction represents the end of the entry block of the procedure that has
 * both the standard entry and the OSR entry as successors.
 *
 * @author Ben L. Titzer
 */
public final class Base extends BlockEnd {

    /**
     * Constructs a new Base instruction.
     * @param standardEntry the standard entrypoint block
     * @param osrEntry the OSR entrypoint block
     */
    public Base(BlockBegin standardEntry, BlockBegin osrEntry) {
        super(CiKind.Illegal, null, false);
        assert osrEntry == null || osrEntry.isOsrEntry();
        assert standardEntry.isStandardEntry();
        if (osrEntry != null) {
            successors.add(osrEntry);
        }
        successors.add(standardEntry);
    }

    /**
     * Gets the standard entrypoint block.
     * @return the standard entrypoint block
     */
    public BlockBegin standardEntry() {
        return defaultSuccessor();
    }

    /**
     * Gets the OSR entrypoint block, if it exists.
     * @return the OSR entrypoint bock, if it exists; {@code null} otherwise
     */
    public BlockBegin osrEntry() {
        return successors.size() < 2 ? null : successors.get(0);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitBase(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("std entry B").print(standardEntry().blockID);
        if (successors().size() > 1) {
            out.print(" osr entry B").print(osrEntry().blockID);
        }
    }
}
