/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1.nativelib;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

/**
 * Defines all data structures that are imported from G1 header files.
 */
@CContext(G1HeaderFiles.class)
public class G1Structs {
    @CStruct(addStructKeyword = true)
    public interface G1HeapOptions extends PointerBase {
        @CField("max_heap_size")
        UnsignedWord maxHeapSize();

        @CField("heap_address_space_size")
        UnsignedWord heapAddressSpaceSize();

        @CField("physical_memory_size")
        UnsignedWord physicalMemorySize();
    }

    @CStruct(addStructKeyword = true)
    public interface G1InitState extends PointerBase {
        @CField("card_table_address")
        Word cardTableAddress();

        @CField("gc_total_collections_address")
        Word gcTotalCollectionsAddress();

        @CField("tlab_top_offset")
        int tlabTopOffset();

        @CField("tlab_end_offset")
        int tlabEndOffset();

        @CField("satb_queue_marking_offset")
        int satbQueueMarkingOffset();

        @CField("satb_queue_buffer_offset")
        int satbQueueBufferOffset();

        @CField("satb_queue_index_offset")
        int satbQueueIndexOffset();

        @CField("card_queue_buffer_offset")
        int cardQueueBufferOffset();

        @CField("card_queue_index_offset")
        int cardQueueIndexOffset();

        @CField("card_table_shift")
        int cardTableShift();

        @CField("log_of_heap_region_grain_bytes")
        int logOfHeapRegionGrainBytes();

        @CField("java_thread_size")
        int javaThreadSize();

        @CField("vm_operation_data_size")
        int vmOperationDataSize();

        @CField("vm_operation_wrapper_data_size")
        int vmOperationWrapperDataSize();

        @CField("dirty_card_value")
        byte dirtyCardValue();

        @CField("young_card_value")
        byte youngCardValue();
    }

    @CStruct(addStructKeyword = true)
    public interface G1RegionBoundaries extends PointerBase {
        G1RegionBoundaries addressOf(long index);

        @CField
        Word bottom();

        @CField
        Word top();
    }

    @CStruct(addStructKeyword = true)
    public interface G1RegionInfo extends PointerBase {
        @CField
        Word bottom();

        @CField
        Word top();

        @CField
        Word end();

        @CField("top_at_mark_start")
        Word topAtMarkStart();

        @CField("parsable_bottom")
        Word parsableBottom();

        @CField("pinned_object_count")
        UnsignedWord pinnedObjectCount();

        @CField("in_collection_set")
        boolean inCollectionSet();

        @CField("remembered_set_state")
        byte rememberedSetState();

        @CField("region_type")
        byte regionType();
    }

    @CStruct(addStructKeyword = true)
    public interface G1InternalState extends PointerBase {
        @CField("total_collections")
        int totalCollections();

        @CField("full_collections")
        int fullCollections();

        @CField("card_table_start")
        Pointer cardTableStart();

        @CField("card_table_size")
        UnsignedWord cardTableSize();

        @CField("block_offset_table_start")
        Pointer blockOffsetTableStart();

        @CField("block_offset_table_size")
        UnsignedWord blockOffsetTableSize();
    }
}
