/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
#ifndef POLYGLOT_TYPES_H
#define POLYGLOT_TYPES_H

#define POLY_AUTO_LENGTH SIZE_MAX

typedef enum {
  poly_ok,
  poly_string_expected,
  poly_number_expected,
  poly_boolean_expected,
  poly_array_expected,
  poly_generic_failure,
  poly_pending_exception,
} poly_status;

typedef struct {
  char* error_message;
  void* engine_reserved;
  unsigned int engine_error_code;
  poly_status error_code;
} poly_extended_error_info;

typedef void* poly_handle;

typedef poly_handle poly_reference;

typedef poly_handle poly_value;

typedef poly_handle poly_engine;

typedef poly_handle poly_engine_builder;

typedef poly_handle poly_context;

typedef poly_handle poly_context_builder;

typedef poly_handle poly_callback_info;

typedef poly_handle poly_language;

typedef poly_handle poly_exception;

typedef graal_create_isolate_params_t poly_isolate_params;

typedef graal_isolate_t* poly_isolate;

typedef graal_isolatethread_t* poly_thread;

typedef poly_value (*poly_callback)(poly_thread thread, poly_callback_info info);

#endif
