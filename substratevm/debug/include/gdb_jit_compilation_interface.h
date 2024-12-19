/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

#ifndef SVM_NATIVE_GDBJITCOMPILATIONINTERFACE_H
#define SVM_NATIVE_GDBJITCOMPILATIONINTERFACE_H

#include <stdint.h>

typedef enum
{
  JIT_NOACTION = 0,
  JIT_REGISTER,
  JIT_UNREGISTER
} jit_actions_t;

struct jit_code_entry
{
  struct jit_code_entry *next_entry;
  struct jit_code_entry *prev_entry;
  const char *symfile_addr;
  uint64_t symfile_size;
};

struct jit_descriptor
{
  uint32_t version;
  /* This type should be jit_actions_t, but we use uint32_t
     to be explicit about the bitwidth.  */
  uint32_t action_flag;
  struct jit_code_entry *relevant_entry;
  struct jit_code_entry *first_entry;
};

#endif
