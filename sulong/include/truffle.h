/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef TRUFFLE_H
#define TRUFFLE_H

#if defined(__cplusplus)
extern "C" {
#endif

#include <stdbool.h>
#include <stdlib.h>

void truffle_load_library(const char *string);

// Managed operations
void *truffle_virtual_malloc(size_t size);
void *truffle_managed_malloc(long size);
void *truffle_managed_memcpy(void *destination, const void *source, size_t count);

// Managed objects <===> native handles
void *truffle_handle_for_managed(void *managedObject);
void *truffle_release_handle(void *nativeHandle);
void *truffle_managed_from_handle(void *nativeHandle);
bool truffle_is_handle_to_managed(void *nativeHandle);
void *truffle_assign_managed(void *dst, void *managed);
void *truffle_deref_handle_for_managed(void *managed);
bool truffle_cannot_be_handle(void *nativeHandle);

/*
 * All function below here are deprecated and will be removed in a future release.
 * Use the equivalent functions from <polyglot.h> instead.
 */

void *truffle_import(const char *name);        // renamed to polyglot_import
void *truffle_import_cached(const char *name); // no replacement, use polyglot_import

void *truffle_address_to_function(void *address); // deprecated, does nothing

void *truffle_get_arg(int i); // renamed to polyglot_get_arg

// Predicates:
bool truffle_is_executable(const void *object);     // renamed to polyglot_can_execute
bool truffle_is_null(const void *object);           // renamed to polyglot_is_null
bool truffle_has_size(const void *object);          // renamed to polyglot_has_array_elements
bool truffle_is_boxed(const void *object);          // no replacement
bool truffle_is_truffle_object(const void *object); // renamed to polyglot_is_value

// Execute: deprecated, use typecast to function pointer instead
void *truffle_execute(void *object, ...);
int truffle_execute_i(void *object, ...);
long truffle_execute_l(void *object, ...);
char truffle_execute_c(void *object, ...);
float truffle_execute_f(void *object, ...);
double truffle_execute_d(void *object, ...);
bool truffle_execute_b(void *object, ...);

// Invoke:
void *truffle_invoke(void *object, const char *name, ...);    // renamed to polyglot_invoke
int truffle_invoke_i(void *object, const char *name, ...);    // deprecated, use polyglot_as_i32(polyglot_invoke(...))
long truffle_invoke_l(void *object, const char *name, ...);   // deprecated, use polyglot_as_i64(polyglot_invoke(...))
char truffle_invoke_c(void *object, const char *name, ...);   // deprecated, use polyglot_as_i8(polyglot_invoke(...))
float truffle_invoke_f(void *object, const char *name, ...);  // deprecated, use polyglot_as_float(polyglot_invoke(...))
double truffle_invoke_d(void *object, const char *name, ...); // deprecated, use polyglot_as_double(polyglot_invoke(...))
bool truffle_invoke_b(void *object, const char *name, ...);   // deprecated, use polyglot_as_boolean(polyglot_invoke(...))

// GetSize
int truffle_get_size(const void *object); // renamed to polyglot_get_array_size

// Unbox
int truffle_unbox_i(void *object);    // renamed to polyglot_as_i32
long truffle_unbox_l(void *object);   // renamed to polyglot_as_i64
char truffle_unbox_c(void *object);   // renamed to polyglot_as_i8
float truffle_unbox_f(void *object);  // renamed to polyglot_as_float
double truffle_unbox_d(void *object); // renamed to polyglot_as_double
bool truffle_unbox_b(void *object);   // renamed to polyglot_as_boolean

// Read
void *truffle_read(void *object, const char *name);    // renamed to polyglot_get_member
int truffle_read_i(void *object, const char *name);    // deprecated, use polyglot_as_i32(polyglot_get_member(...))
long truffle_read_l(void *object, const char *name);   // deprecated, use polyglot_as_i64(polyglot_get_member(...))
char truffle_read_c(void *object, const char *name);   // deprecated, use polyglot_as_i8(polyglot_get_member(...))
float truffle_read_f(void *object, const char *name);  // deprecated, use polyglot_as_float(polyglot_get_member(...))
double truffle_read_d(void *object, const char *name); // deprecated, use polyglot_as_double(polyglot_get_member(...))
bool truffle_read_b(void *object, const char *name);   // deprecated, use polyglot_as_boolean(polyglot_get_member(...))

void *truffle_read_idx(void *object, int idx);    // renamed to polyglot_get_array_element
int truffle_read_idx_i(void *object, int idx);    // deprecated, use polyglot_as_i32(polyglot_get_array_element(...))
long truffle_read_idx_l(void *object, int idx);   // deprecated, use polyglot_as_i64(polyglot_get_array_element(...))
char truffle_read_idx_c(void *object, int idx);   // deprecated, use polyglot_as_i8(polyglot_get_array_element(...))
float truffle_read_idx_f(void *object, int idx);  // deprecated, use polyglot_as_float(polyglot_get_array_element(...))
double truffle_read_idx_d(void *object, int idx); // deprecated, use polyglot_as_double(polyglot_get_array_element(...))
bool truffle_read_idx_b(void *object, int idx);   // deprecated, use polyglot_as_boolean(polyglot_get_array_element(...))

// Write
void truffle_write(void *object, const char *name, void *value);    // renamed to polyglot_put_member
void truffle_write_i(void *object, const char *name, int value);    // deprecated, use polyglot_put_member
void truffle_write_l(void *object, const char *name, long value);   // deprecated, use polyglot_put_member
void truffle_write_c(void *object, const char *name, char value);   // deprecated, use polyglot_put_member
void truffle_write_f(void *object, const char *name, float value);  // deprecated, use polyglot_put_member
void truffle_write_d(void *object, const char *name, double value); // deprecated, use polyglot_put_member
void truffle_write_b(void *object, const char *name, bool value);   // deprecated, use polyglot_put_member

void truffle_write_idx(void *object, int idx, void *value);    // renamed to polyglot_set_array_element
void truffle_write_idx_i(void *object, int idx, int value);    // deprecated, use polyglot_set_array_element
void truffle_write_idx_l(void *object, int idx, long value);   // deprecated, use polyglot_set_array_element
void truffle_write_idx_c(void *object, int idx, char value);   // deprecated, use polyglot_set_array_element
void truffle_write_idx_f(void *object, int idx, float value);  // deprecated, use polyglot_set_array_element
void truffle_write_idx_d(void *object, int idx, double value); // deprecated, use polyglot_set_array_element
void truffle_write_idx_b(void *object, int idx, bool value);   // deprecated, use polyglot_set_array_element

// Strings
void *truffle_read_string(const char *string);              // deprecated, use polyglot_from_string instead
void *truffle_read_n_string(const char *string, int n);     // deprecated, use polyglot_from_string_n instead
void *truffle_read_bytes(const char *bytes);                // deprecated, no replacement
void *truffle_read_n_bytes(const char *bytes, int n);       // deprecated, no replacement
const char *truffle_string_to_cstr(const char *string);     // deprecated, use polyglot_as_string instead
void truffle_free_cstr(const char *truffle_allocated_cstr); // deprecated, no replacement

void *truffle_sulong_function_to_native_pointer(void *sulongFunctionPointer, const void *signature); // deprecated, no replacement

void *truffle_polyglot_eval(const char *mimeType, const char *code); // deprecated, use polyglot_eval instead

#if defined(__cplusplus)
}
#endif

#endif
