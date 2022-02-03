/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.spi;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.Assumptions;

public interface CanonicalizerTool extends CoreProviders {

    Assumptions getAssumptions();

    /**
     * Indicates whether this application of the canonicalizer is the last one during compilation.
     * Final canonicalizations can include changes to code shapes that should not be reversed before
     * code generation.
     */
    default boolean finalCanonicalization() {
        return false;
    }

    /**
     * Indicates whether the canonicalization of memory read operations to simpler operations or
     * constants is allowed. Nodes subject to read canonicalization include (among others):
     * {@link ReadNode}, {@link LoadFieldNode} and {@link LoadIndexedNode}.
     */
    boolean canonicalizeReads();

    /**
     * If this method returns false, not all {@link Node#usages() usages of a node} are yet
     * available. So a node must not be canonicalized base on, e.g., information returned from
     * {@link Node#hasNoUsages()}.
     */
    boolean allUsagesAvailable();

    /**
     * Indicates the smallest width for comparing an integer value on the target platform. If this
     * method returns null, then there is no known smallest compare width.
     */
    Integer smallestCompareWidth();

    /**
     * Indicates whether this target platform supports lowering {@code RoundNode}.
     */
    boolean supportsRounding();

    OptionValues getOptions();

}
