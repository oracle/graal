/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.Deque;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.genscavenge.AddressRangeCommittedMemoryProvider;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFixedNode;
import com.oracle.svm.core.graal.nodes.ReadReservedRegisterFloatingNode;
import com.oracle.svm.core.os.CommittedMemoryProvider;

import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.amd64.AMD64CompressAddressLowering;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.AddressLoweringByNodePhase;
import jdk.graal.compiler.replacements.gc.WriteBarrierSnippets;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;

/**
 * This phase is used in the context of the {@link AMD64MemoryMaskingAndFencing} mitigation. It
 * lowers {@link OffsetAddressNode} into {@link AMD64MaskedAddressNode} (instead of normal
 * {@link AMD64AddressNode}), which represent a speculatively safe way of accessing memory.
 */
public class AMD64AddressLoweringAndMaskingByNodePhase extends AddressLoweringByNodePhase {

    private final AddressLoweringByNodePhase.AddressLowering unmaskedLowering;
    private final Register heapBaseRegister;

    public AMD64AddressLoweringAndMaskingByNodePhase(AddressLowering unmaskedLowering) {
        super(unmaskedLowering);
        this.unmaskedLowering = unmaskedLowering;
        this.heapBaseRegister = ReservedRegisters.singleton().getHeapBaseRegister();
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders providers) {
        /*
         * If MemoryMaskingAndFencing is not active, masking should not be enabled, and we can use
         * the normal phase.
         */
        if (!AMD64MemoryMaskingAndFencing.isEnabled()) {
            super.run(graph, providers);
            return;
        }
        lowerAddressWithMaskingAndFencing(graph, getAddressUsagePolicy(providers));
    }

    private static AMD64MemoryMaskingAddressUsagePolicy getAddressUsagePolicy(CoreProviders providers) {
        GraalError.guarantee(providers.getLowerer() instanceof AMD64MemoryMaskingAddressUsagePolicy,
                        "AMD64MemoryMaskingAndFencing requires an AMD64MemoryMaskingAddressUsagePolicy lowering provider, got %s", providers.getLowerer());
        return (AMD64MemoryMaskingAddressUsagePolicy) providers.getLowerer();
    }

    private static long getMask() {
        AddressRangeCommittedMemoryProvider memoryProvider = (AddressRangeCommittedMemoryProvider) ImageSingletons.lookup(CommittedMemoryProvider.class);
        UnsignedWord reservedSpaceSize = memoryProvider.getReservedAddressSpaceSize();

        GraalError.guarantee(CodeUtil.isPowerOf2(reservedSpaceSize.rawValue()), "MemoryMaskingAndFencing only available if the Isolate reserved address space is a power of two.");
        return reservedSpaceSize.rawValue() - 1;
    }

    private void lowerAddressWithMaskingAndFencing(StructuredGraph graph, AMD64MemoryMaskingAddressUsagePolicy addressUsagePolicy) {

        long mask = getMask();

        for (Node node : graph.getNodes()) {

            /*
             * Lowering OffsetAddressNode(s) into AMD64Addresses. Some of them might already have
             * been transformed into AMD64MaskedAddresses while improving the uncompression.
             */
            if (!(node instanceof OffsetAddressNode)) {
                continue;
            }

            OffsetAddressNode address = (OffsetAddressNode) node;
            AddressNode l = unmaskedLowering.lower(address.getBase(), address.getOffset());
            GraalError.guarantee((l instanceof AMD64AddressNode), "Node %s lowered to unexpected node: %s", node, l);

            AMD64AddressNode loweredUnmasked = (AMD64AddressNode) l;

            if (!shouldBeMasked(loweredUnmasked)) {
                // If the node does no need masking then substitute each usage with the lowered one.
                node.replaceAtUsages(loweredUnmasked);
                GraphUtil.killWithUnusedFloatingInputs(node);
                continue;
            }

            // Check if we can already decide if the address is off heap.
            boolean isOffHeap = isOffHeap(loweredUnmasked);

            /*
             * If the node potentially requires masking, we still have to check the users of the
             * node. Some of them may not need a masked node. Thus, we get the iterator for all the
             * users of this node, and then make a decision per case. If the user needs a masked
             * address, then we create the masked version of the current address and substitute the
             * current address with the masked one as the input to its user that we are currently
             * checking.
             *
             * We iterate using a copy since the users list of the node will be updated when calling
             * `replaceAllInputs`. Using a normal iterator will lose track of some nodes.
             */
            for (Node usage : node.usages().snapshot()) {

                if (canUseUnmaskedAddress(addressUsagePolicy, node, usage)) {
                    usage.replaceAllInputs(node, loweredUnmasked);
                    continue;
                }

                GraalError.guarantee(addressUsagePolicy.mightNeedMaskedAddress(node, usage),
                                "Unexpected address usage that is neither allowed to stay unmasked nor known to require masking: %s", usage);
                GraalError.guarantee(usage instanceof FixedNode, "Address usage that requires masking must be fixed: %s", usage);
                FixedNode fixedNode = (FixedNode) usage;
                boolean isOffHeapOrUnknown = isOffHeap || isOffHeapOrUnknownByUsage(usage);

                ValueAnchorNode anchorNode = graph.addWithoutUnique(new ValueAnchorNode());

                AMD64MaskedAddressNode newLowered = graph.addWithoutUnique(
                                new AMD64MaskedAddressNode(loweredUnmasked.getBase(),
                                                loweredUnmasked.getIndex(),
                                                graph.unique(new AMD64CompressAddressLowering.HeapBaseNode(heapBaseRegister)),
                                                mask,
                                                loweredUnmasked.getDisplacement(),
                                                loweredUnmasked.getScale(),
                                                isOffHeapOrUnknown,
                                                anchorNode));

                graph.addBeforeFixed(fixedNode, anchorNode);
                usage.replaceAllInputs(node, newLowered);
            }
        }
    }

    private record AddressUsage(Node addressValue, Node usage) {
    }

    private static boolean canUseUnmaskedAddress(AMD64MemoryMaskingAddressUsagePolicy addressUsagePolicy, Node addressValue, Node usage) {
        Deque<AddressUsage> worklist = new ArrayDeque<>();
        EconomicSet<Node> processedNodes = EconomicSet.create();

        worklist.add(new AddressUsage(addressValue, usage));

        while (!worklist.isEmpty()) {
            CompilationAlarm.checkProgress(addressValue.graph());

            AddressUsage current = worklist.removeLast();
            if (!addressUsagePolicy.canUseUnmaskedAddress(current.addressValue(), current.usage())) {
                return false;
            }

            if (!addressUsagePolicy.isUnmaskedAddressPassThrough(current.usage())) {
                continue;
            }

            if (!processedNodes.add(current.usage())) {
                continue;
            }

            for (Node passThroughUsage : current.usage().usages()) {
                worklist.add(new AddressUsage(current.usage(), passThroughUsage));
            }
        }
        return true;
    }

    private boolean isOffHeap(AMD64AddressNode address) {
        return floatingReservedRegisterIsNotHeapBase(address.getBase()) || fixedReservedRegisterIsNotHeapBase(address.getBase());
    }

    private static boolean isOffHeapOrUnknownByUsage(Node node) {

        if (node instanceof CInterfaceReadNode) {
            return true;
        }

        if (!(node instanceof FixedAccessNode fixedAccessNode)) {
            return true;
        }

        /**
         * Unsafe accesses are marked ANY_LOCATION for heap accesses and OFF_HEAP_LOCATION for off
         * heap accesses. When in doubt, a conditional node is used and the choice happens at
         * runtime.
         * {@link jdk.graal.compiler.replacements.StandardGraphBuilderPlugins.UnsafeAccessPlugin#createUnsafeAccess}
         */
        if (fixedAccessNode.getLocationIdentity().equals(NamedLocationIdentity.OFF_HEAP_LOCATION) ||
                        fixedAccessNode.getLocationIdentity().equals(WriteBarrierSnippets.GC_CARD_LOCATION)) {
            return true;
        }

        return false;
    }

    private boolean shouldBeMasked(AMD64AddressNode lowered) {
        /*
         * We don't mask addresses that use reserved registers (except for the heap base register
         * which is used directly in the masking pattern) because an attacker won't have control
         * over them. Note that we do need to protect accesses that use a reserved register as a
         * base and a non-null index, since the index might be attacker controlled at runtime.
         */

        if (lowered.getBase() instanceof ReadReservedRegisterFloatingNode floatingNode) {
            if (floatingNode.getRegister().equals(heapBaseRegister)) {
                return true;
            }
            return lowered.getIndex() != null;
        }

        if (lowered.getBase() instanceof ReadReservedRegisterFixedNode fixedNode) {
            if (fixedNode.getRegister().equals(heapBaseRegister)) {
                return true;
            }
            return lowered.getIndex() != null;
        }

        return true;
    }

    private boolean floatingReservedRegisterIsNotHeapBase(ValueNode value) {
        return (value instanceof ReadReservedRegisterFloatingNode floatingNode && !floatingNode.getRegister().equals(heapBaseRegister));
    }

    private boolean fixedReservedRegisterIsNotHeapBase(ValueNode value) {
        return (value instanceof ReadReservedRegisterFixedNode fixedNode && !fixedNode.getRegister().equals(heapBaseRegister));
    }
}
