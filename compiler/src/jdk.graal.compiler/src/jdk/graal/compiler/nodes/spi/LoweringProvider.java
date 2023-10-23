/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaKind;

/**
 * Provides a capability for replacing a higher node with one or more lower level nodes.
 */
public interface LoweringProvider {

    void lower(Node n, LoweringTool tool);

    /**
     * Reconstructs the array index from an address node that was created as a lowering of an
     * indexed access to an array.
     *
     * @param elementKind the {@link JavaKind} of the array elements
     * @param address an {@link AddressNode} pointing to an element in an array
     * @return a node that gives the index of the element
     */
    ValueNode reconstructArrayIndex(JavaKind elementKind, AddressNode address);

    /**
     * Indicates the smallest width for comparing an integer value on the target platform.
     */
    Integer smallestCompareWidth();

    /**
     * Indicates whether this target platform supports bulk zeroing of arbitrary size.
     */
    boolean supportsBulkZeroing();

    /**
     * Indicates whether this target platform supports optimized filling of memory regions with
     * {@code long} values.
     */
    boolean supportsOptimizedFilling(OptionValues options);

    /**
     * Indicates whether this target platform supports lowering {@link RoundNode}.
     */
    boolean supportsRounding();

    /**
     * Indicates whether this target platform supports the usage of implicit (trapping) null checks.
     */
    boolean supportsImplicitNullChecks();

    /**
     * Indicates whether all writes are ordered on this target platform.
     */
    boolean writesStronglyOrdered();

    /**
     * Returns the target being lowered.
     */
    TargetDescription getTarget();

    /**
     * Returns the barrier set use for code generation.
     */
    BarrierSet getBarrierSet();

    /**
     * Indicates whether the target platform complies with the JVM specification semantics for
     * {@code idiv} and {@code ldiv} when the dividend is {@link Integer#MIN_VALUE} or
     * {@link Long#MIN_VALUE} respectively and the divisor is {@code -1}. The specified result for
     * this case is the dividend.
     */
    boolean divisionOverflowIsJVMSCompliant();

    /**
     * Indicates whether this target platform supports uses {@link CastValue} for narrows.
     */
    boolean narrowsUseCastValue();

    /**
     * Indicates whether this target platform can fold an {@code extendKind} into a given
     * {@link ExtendableMemoryAccess}.
     */
    boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind);
}
