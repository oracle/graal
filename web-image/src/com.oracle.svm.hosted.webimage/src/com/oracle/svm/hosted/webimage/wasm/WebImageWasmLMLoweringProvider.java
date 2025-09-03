/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.hosted.webimage.WebImageLoweringProvider;

import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractDeoptimizeNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.extended.ClassIsArrayNode;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.LoadArrayComponentHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubNode;
import jdk.graal.compiler.nodes.extended.LoadHubOrNullNode;
import jdk.graal.compiler.nodes.extended.ObjectIsArrayNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.UnboxNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.java.AccessFieldNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.ClassIsAssignableFromNode;
import jdk.graal.compiler.nodes.java.InstanceOfDynamicNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.ExtendableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.IndexAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.replacements.nodes.AssertionNode;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;

public class WebImageWasmLMLoweringProvider extends WebImageLoweringProvider {
    public WebImageWasmLMLoweringProvider(MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        super(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider, target);
    }

    /**
     * A list of node classes which should be lowered using the parent lowering providers.
     */
    private static final Set<Class<?>> DEFAULT_LOWERABLE_NODES = new HashSet<>(Arrays.asList(
                    ArrayLengthNode.class,
                    ClassIsArrayNode.class,
                    GetClassNode.class,
                    IndexAddressNode.class,
                    InstanceOfNode.class,
                    InstanceOfDynamicNode.class,
                    LoadIndexedNode.class,
                    ObjectIsArrayNode.class,
                    StoreIndexedNode.class,
                    ClassIsAssignableFromNode.class,
                    UnsafeMemoryLoadNode.class,
                    UnsafeMemoryStoreNode.class,
                    UnsafeCompareAndSwapNode.class,
                    AtomicReadAndWriteNode.class,
                    AtomicReadAndAddNode.class,
                    UnboxNode.class,
                    BoxNode.class,
                    RawLoadNode.class,
                    RawStoreNode.class,
                    LoadArrayComponentHubNode.class,
                    IdentityHashCodeNode.class,
                    FixedAccessNode.class,
                    AbstractDeoptimizeNode.class,
                    LoadHubNode.class,
                    LoadHubOrNullNode.class,
                    UnsafeCompareAndExchangeNode.class,
                    CommitAllocationNode.class,
                    AssertionNode.class,
                    DeadEndNode.class,
                    LoadExceptionObjectNode.class,
                    AccessFieldNode.class));

    @SuppressWarnings("unchecked")
    @Override
    public void lower(Node n, LoweringTool tool) {
        @SuppressWarnings("rawtypes")
        NodeLoweringProvider lowering = getLowerings().get(n.getClass());
        if (lowering != null) {
            lowering.lower(n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public boolean shouldLower(Node n) {
        for (Class<?> c : DEFAULT_LOWERABLE_NODES) {
            if (c.isInstance(n)) {
                return true;
            }
        }
        return false;
    }

    /**
     * In WASM, all comparisons are between 32-bit or 64-bit integers.
     */
    @Override
    public Integer smallestCompareWidth() {
        return 32;
    }

    /**
     * Supported with the {@code memory.fill} instruction.
     */
    @Override
    public boolean supportsBulkZeroingOfEden() {
        return true;
    }

    /**
     * WASM is single-threaded for now, but in the threading proposal, memory accesses are
     * sequentially consistent.
     */
    @Override
    public boolean writesStronglyOrdered() {
        return true;
    }

    /**
     * WASM is not compliant. Dividing the min value by -1 traps.
     * <p>
     * Ref:
     * https://webassembly.github.io/spec/core/exec/numerics.html#xref-exec-numerics-op-idiv-s-mathrm-idiv-s-n-i-1-i-2
     */
    @Override
    public boolean divisionOverflowIsJVMSCompliant() {
        return false;
    }

    /**
     * Likely does not apply to WASM, but return false to be safe.
     */
    @Override
    public boolean narrowsUseCastValue() {
        return false;
    }

    /**
     * WASM has load instructions with included zero/sign extensions.
     */
    @Override
    public boolean supportsFoldingExtendIntoAccess(ExtendableMemoryAccess access, MemoryExtendKind extendKind) {
        if (!access.isCompatibleWithExtend(extendKind)) {
            return false;
        }

        return access instanceof ReadNode;
    }
}
