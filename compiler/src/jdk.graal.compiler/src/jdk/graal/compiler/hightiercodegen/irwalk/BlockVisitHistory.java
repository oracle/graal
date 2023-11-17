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
package jdk.graal.compiler.hightiercodegen.irwalk;

import java.util.HashSet;
import java.util.Set;

import jdk.graal.compiler.nodes.cfg.HIRBlock;

public class BlockVisitHistory {
    private final Set<HIRBlock> visitedBlocks;

    public BlockVisitHistory() {
        visitedBlocks = new HashSet<>();
    }

    public boolean blockVisited(HIRBlock b) {
        return visitedBlocks.contains(b);
    }

    public void visitBlock(HIRBlock b) {
        assert !visitedBlocks.contains(b) : "Block already visited" + b.toString();
        visitedBlocks.add(b);
    }
}
