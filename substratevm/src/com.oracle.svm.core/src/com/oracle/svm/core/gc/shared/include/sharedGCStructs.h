/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SVM_SHARED_GC_STRUCTS_HPP
#define SVM_SHARED_GC_STRUCTS_HPP

#include <sys/types.h>

#ifdef __cplusplus
namespace svm_gc {
#endif

// forward declarations
typedef struct CodeInfo CodeInfo;

// data structures for frames that are currently on the stack
struct StackFrame {
  u_char *stack_pointer;
  u_char *encoded_reference_map;
  size_t reference_map_index;
};

struct StackFrames {
  size_t count;
  struct StackFrame frames[0]; // variable-sized array
};

struct StackFramesPerThread {
  size_t count;
  struct StackFrames *threads[0]; // variable-sized array
};

// data structures for JIT-compiled code that is currently on the stack
struct CodeInfos {
  size_t count;
  struct CodeInfo *code_infos[0]; // variable-sized array
};

struct CodeInfosPerThread {
  size_t count;
  struct CodeInfos *threads[0]; // variable-sized array
};

#ifdef __cplusplus
} // namespace svm_gc
#endif

#endif // SVM_SHARED_GC_STRUCTS_HPP
