/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef POLYGLOT_TYPES_H
#define POLYGLOT_TYPES_H

typedef enum {
  polyglot_ok,
  polyglot_invalid_arg,
  polyglot_object_expected,
  polyglot_string_expected,
  polyglot_name_expected,
  polyglot_function_expected,
  polyglot_number_expected,
  polyglot_boolean_expected,
  polyglot_array_expected,
  polyglot_generic_failure,
  polyglot_pending_exception,
  polyglot_cancelled,
  polyglot_status_last
} polyglot_status;

typedef struct {
  char* error_message;
  void* engine_reserved;
  unsigned int engine_error_code;
  polyglot_status error_code;
} polyglot_extended_error_info;

// GR-7868 polyglot_handle becomes void* and all CPointers become CTypeDef
typedef void polyglot_handle;

typedef polyglot_handle polyglot_value;

typedef polyglot_handle polyglot_engine;

typedef polyglot_handle polyglot_context;

typedef polyglot_handle polyglot_callback_info;

typedef polyglot_value* (*polyglot_callback)(void* ithread, polyglot_callback_info* info);

#endif
