/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * This is a source with different versions for various JDKs. When modifying/adding a field in this
 * class accessed from outside this class, be sure to update the field appropriately in all source
 * files named {@code GraalHotSpotVMConfigVersioned.java}.
 *
 * Fields are grouped according to the most recent JBS issue showing why they are versioned.
 *
 * JDK Version: 8+
 */
final class GraalHotSpotVMConfigVersioned extends HotSpotVMConfigAccess {

    GraalHotSpotVMConfigVersioned(HotSpotVMConfigStore store) {
        super(store);
    }

    // JDK-8073583
    final boolean useCRC32CIntrinsics = false;

    // JDK-8075171
    final boolean inlineNotify = false;

    // JDK-8046936
    final int javaThreadReservedStackActivationOffset = 0;
    final int methodFlagsOffset = getFieldOffset("Method::_flags", Integer.class, "u1");
    final long throwDelayedStackOverflowErrorEntry = 0;
    final long enableStackReservedZoneAddress = 0;

    // JDK-8135085
    final int methodIntrinsicIdOffset = getFieldOffset("Method::_intrinsic_id", Integer.class, "u1");

    // JDK-8151956
    final int methodCodeOffset = getFieldOffset("Method::_code", Integer.class, "nmethod*");

    // JDK-8059606
    final int invocationCounterIncrement = 0; // InvocationCounter::count_increment
    final int invocationCounterShift = 0; // InvocationCounter::count_shift

    // JDK-8134994
    final int dirtyCardQueueBufferOffset = getFieldOffset("PtrQueue::_buf", Integer.class, "void**");
    final int dirtyCardQueueIndexOffset = getFieldOffset("PtrQueue::_index", Integer.class, "size_t");
    final int satbMarkQueueBufferOffset = dirtyCardQueueBufferOffset;
    final int satbMarkQueueIndexOffset = dirtyCardQueueIndexOffset;
    final int satbMarkQueueActiveOffset = getFieldOffset("PtrQueue::_active", Integer.class, "bool");

    // JDK-8195142
    final byte dirtyCardValue = getFieldValue("CompilerToVM::Data::dirty_card", Byte.class, "int");
    final byte g1YoungCardValue = getFieldValue("CompilerToVM::Data::g1_young_card", Byte.class, "int");

    // JDK-8033552
    final long heapTopAddress = getFieldValue("CompilerToVM::Data::_heap_top_addr", Long.class, "HeapWord**");

    // JDK-8015774
    final long codeCacheLowBound = getFieldValue("CompilerToVM::Data::CodeCache_low_bound", Long.class, "address");
    final long codeCacheHighBound = getFieldValue("CompilerToVM::Data::CodeCache_high_bound", Long.class, "address");
}
