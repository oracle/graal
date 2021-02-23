/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.amd64;

import static org.graalvm.compiler.core.common.memory.MemoryOrderMode.VOLATILE;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.gc.WriteBarrier;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.memory.AbstractWriteNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.OrderedMemoryAccess;
import org.graalvm.compiler.nodes.memory.VolatileWriteNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayIndexOfWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64ArrayRegionEqualsWithMaskNode;
import org.graalvm.compiler.replacements.amd64.AMD64TruffleArrayUtilsWithMaskSnippets;

public interface AMD64LoweringProviderMixin extends LoweringProvider {

    @Override
    default Integer smallestCompareWidth() {
        return 8;
    }

    @Override
    default boolean supportsBulkZeroing() {
        return true;
    }

    /**
     * Performs AMD64-specific lowerings. Returns {@code true} if the given Node {@code n} was
     * lowered, {@code false} otherwise.
     */
    default boolean lowerAMD64(Node n, LoweringTool tool) {
        if (n instanceof AMD64ArrayIndexOfWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayIndexOfWithMaskNode) n);
            return true;
        }
        if (n instanceof AMD64ArrayRegionEqualsWithMaskNode) {
            tool.getReplacements().getSnippetTemplateCache(AMD64TruffleArrayUtilsWithMaskSnippets.Templates.class).lower((AMD64ArrayRegionEqualsWithMaskNode) n);
            return true;
        }
        if (n instanceof VolatileWriteNode) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                return false;
            }
            VolatileWriteNode write = (VolatileWriteNode) n;
            if (hasFollowingVolatileBarrier(write)) {
                StructuredGraph graph = write.graph();

                WriteNode add = graph.add(new WriteNode(write.getAddress(), write.getLocationIdentity(), write.value(), write.getBarrierType()));
                add.setLastLocationAccess(write.getLastLocationAccess());
                graph.replaceFixedWithFixed(write, add);
                return true;
            }
        }
        return false;
    }

    default boolean hasFollowingVolatileBarrier(VolatileWriteNode n) {
        FixedWithNextNode cur = n;
        while (cur != null) {
            // Check the memory usages of the current write
            for (Node usage : cur.usages()) {
                if (!(usage instanceof MemoryAccess) || !(usage instanceof FixedWithNextNode)) {
                    // Other kinds of usages won't be visited in the traversal and likely
                    // invalidates elimination of the barrier instruction.
                    return false;
                }
            }
            FixedNode next = cur.next();
            // We can safely ignore GC barriers
            while (next instanceof WriteBarrier) {
                next = ((WriteBarrier) next).next();
            }

            if (next instanceof OrderedMemoryAccess) {
                if (next instanceof AbstractWriteNode || next instanceof AbstractCompareAndSwapNode) {
                    return ((OrderedMemoryAccess) next).getMemoryOrder() == VOLATILE;
                }
                return false;
            }

            // Sequential normal writes are ok as well.
            if (next instanceof WriteNode) {
                cur = (FixedWithNextNode) next;
            } else {
                return false;
            }

        }
        return false;
    }
}
