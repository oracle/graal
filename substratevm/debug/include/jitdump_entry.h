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

#ifndef SVM_NATIVE_JITDUMPRECORDS_H
#define SVM_NATIVE_JITDUMPRECORDS_H

#ifdef __linux__

// This header specifies the jitdump entries described in the Jitdump specification (see https://github.com/torvalds/linux/blob/46a51f4f5edade43ba66b3c151f0e25ec8b69cb6/tools/perf/Documentation/jitdump-specification.txt)
// The implementation of the Jitdump provider generating these entries is located in com.oracle.svm.core.posix.debug.jitdump.JitdumpProvider.

#include <stdint.h>

typedef enum
{
  JIT_CODE_LOAD = 0,
  JIT_CODE_MOVE = 1,
  JIT_CODE_DEBUG_INFO = 2,
  JIT_CODE_CLOSE = 3,
  JIT_CODE_UNWINDING_INFO = 4
} record_type;

struct file_header
{
  uint32_t magic;
  uint32_t version;
  uint32_t total_size;
  uint32_t elf_mach;
  uint32_t pad1;
  uint32_t pid;
  uint64_t timestamp;
  uint64_t flags;
};

struct record_header
{
  uint32_t id;
  uint32_t total_size;
  uint64_t timestamp;
};

struct code_load_record
{
  struct record_header header;
  uint32_t pid;
  uint32_t tid;
  uint64_t vma;
  uint64_t code_addr;
  uint64_t code_size;
  uint64_t code_index;
};

struct debug_entry
{
  uint64_t code_addr;
  uint32_t line;
  uint32_t discrim;
};

struct debug_info_record
{
  struct record_header header;
  uint64_t code_addr;
  uint64_t nr_entry;
};

#endif /* __linux__ */

#endif
