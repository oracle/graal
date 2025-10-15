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

#ifndef SVM_SHENANDOAH_GC_STRUCTS_H
#define SVM_SHENANDOAH_GC_STRUCTS_H

#include <sys/types.h>

#ifdef __cplusplus
namespace svm_gc {
#endif

struct ShenandoahHeapOptions {
  size_t max_heap_size;
  size_t heap_address_space_size;
  size_t physical_memory_size;
};

struct ShenandoahInitState {
  void* card_table_address;
  int tlab_top_offset;
  int tlab_end_offset;
  int card_table_shift;
  int log_of_heap_region_grain_bytes;
  int java_thread_size;
  int vm_operation_data_size;
  int vm_operation_wrapper_data_size;
  char dirty_card_value;
};

struct ShenandoahRegionBoundaries {
  u_char *bottom;
  u_char *top;
};

struct ShenandoahRegionInfo {
  u_char *bottom;
  u_char *top;
  u_char *end;
  char region_type;
};

struct ShenandoahInternalState {
  unsigned int total_collections;
  unsigned int full_collections;

  void* card_table_start;
  size_t card_table_size;
};

#ifdef __cplusplus
} // namespace svm_gc
#endif

#endif // SVM_SHENANDOAH_GC_STRUCTS_H
