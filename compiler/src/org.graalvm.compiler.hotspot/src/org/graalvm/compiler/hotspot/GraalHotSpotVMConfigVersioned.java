/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
    boolean useCRC32CIntrinsics = false;

    // JDK-8075171
    boolean inlineNotify = false;

    // JDK-8046936
    int javaThreadReservedStackActivationOffset = 0;
    int methodFlagsOffset = getFieldOffset("Method::_flags", Integer.class, "u1");
    long throwDelayedStackOverflowErrorEntry = 0;
    long enableStackReservedZoneAddress = 0;

    // JDK-8135085
    int methodIntrinsicIdOffset = getFieldOffset("Method::_intrinsic_id", Integer.class, "u1");

    // JDK-8151956
    int methodCodeOffset = getFieldOffset("Method::_code", Integer.class, "nmethod*");

    // JDK-8059606
    int invocationCounterIncrement = 0; // InvocationCounter::count_increment
    int invocationCounterShift = 0; // InvocationCounter::count_shift

    // JDK-8134994
    int dirtyCardQueueBufferOffset = getFieldOffset("PtrQueue::_buf", Integer.class, "void**");
    int dirtyCardQueueIndexOffset = getFieldOffset("PtrQueue::_index", Integer.class, "size_t");
    int satbMarkQueueBufferOffset = dirtyCardQueueBufferOffset;
    int satbMarkQueueIndexOffset = dirtyCardQueueIndexOffset;
    int satbMarkQueueActiveOffset = getFieldOffset("PtrQueue::_active", Integer.class, "bool");

    // JDK-8195142
    byte dirtyCardValue = getFieldValue("CompilerToVM::Data::dirty_card", Byte.class, "int");
    byte g1YoungCardValue = getFieldValue("CompilerToVM::Data::g1_young_card", Byte.class, "int");

    // JDK-8201318
    int javaThreadDirtyCardQueueOffset = getFieldOffset("JavaThread::_dirty_card_queue", Integer.class, "DirtyCardQueue");
    int javaThreadSatbMarkQueueOffset = getFieldOffset("JavaThread::_satb_mark_queue", Integer.class);
    int g1CardQueueIndexOffset = javaThreadDirtyCardQueueOffset + dirtyCardQueueIndexOffset;
    int g1CardQueueBufferOffset = javaThreadDirtyCardQueueOffset + dirtyCardQueueBufferOffset;
    int g1SATBQueueMarkingOffset = javaThreadSatbMarkQueueOffset + satbMarkQueueActiveOffset;
    int g1SATBQueueIndexOffset = javaThreadSatbMarkQueueOffset + satbMarkQueueIndexOffset;
    int g1SATBQueueBufferOffset = javaThreadSatbMarkQueueOffset + satbMarkQueueBufferOffset;

    // JDK-8033552
    long heapTopAddress = getFieldValue("CompilerToVM::Data::_heap_top_addr", Long.class, "HeapWord**");

    // JDK-8015774
    long codeCacheLowBound = getFieldValue("CompilerToVM::Data::CodeCache_low_bound", Long.class, "address");
    long codeCacheHighBound = getFieldValue("CompilerToVM::Data::CodeCache_high_bound", Long.class, "address");
}
