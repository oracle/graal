/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.PrefetchAllocateNode;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.extended.NullCheckNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.replacements.nodes.ZeroMemoryNode;
import jdk.graal.compiler.word.WordCastNode;

/**
 * Policy used by {@link AMD64AddressLoweringAndMaskingByNodePhase} to classify usages of an
 * address value. The methods receive both the value that carries the address and its usage so
 * implementations can make input-specific decisions for nodes that consume multiple addresses.
 */
public interface AMD64MemoryMaskingAddressUsagePolicy {

    /**
     * Returns {@code true} if {@code usage} consumes {@code addressValue} as a value that does not
     * need memory-source protection. {@code addressValue} may be the original address node or a
     * pass-through node that acts as a proxy for an address.
     */
    default boolean canUseUnmaskedAddress(@SuppressWarnings("unused") Node addressValue, Node usage) {
        if (usage instanceof OffsetAddressNode) {
            return true;
        }
        if (usage instanceof ValueProxy) {
            return true;
        }
        if (usage instanceof CallTargetNode) {
            return true;
        }
        if (usage instanceof WordCastNode) {
            return true;
        }
        if (usage instanceof WriteNode) {
            return true;
        }
        if (usage instanceof PrefetchAllocateNode) {
            return true;
        }
        if (usage instanceof ZeroMemoryNode) {
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code usage} passes an address through, which requires analyzing its
     * usages before the address can stay unmasked.
     */
    default boolean isUnmaskedAddressPassThrough(Node usage) {
        return usage instanceof ValueProxy || usage instanceof WordCastNode;
    }

    /**
     * Returns {@code true} if {@code usage} may need a masked address. For every valid address
     * usage, {@code !canUseUnmaskedAddress(addressValue, usage)} implies
     * {@code mightNeedMaskedAddress(addressValue, usage)}.
     */
    default boolean mightNeedMaskedAddress(@SuppressWarnings("unused") Node addressValue, Node usage) {
        if (usage instanceof FixedAccessNode) {
            return true;
        }
        if (usage instanceof NullCheckNode) {
            return true;
        }
        if (usage instanceof ForeignCall) {
            return true;
        }
        return false;
    }
}
