/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

/*
 READ ME:

 All interop functions that are commented-out are not implemented
 or not tested in Sulong.
*/

void *truffle_import(const char *name);
/* This import function caches the result (i.e. the imported object) for a given name,
i.e., a subsequent import with the same name does not do a lookup but returns the cached value.
*/
void *truffle_import_cached(const char *name);
// void truffle_export(const char *name, void *value);

// Predicates:
bool truffle_is_executable(void *object);
bool truffle_is_null(void *object);
bool truffle_has_size(void *object);
bool truffle_is_boxed(void *object);

// Execute:
void *truffle_execute(void *object, ...);
int truffle_execute_i(void *object, ...);
long truffle_execute_l(void *object, ...);
char truffle_execute_c(void *object, ...);
float truffle_execute_f(void *object, ...);
double truffle_execute_d(void *object, ...);
bool truffle_execute_b(void *object, ...);

// Invoke:
void *truffle_invoke(void *object, const char *name, ...);
int truffle_invoke_i(void *object, const char *name, ...);
long truffle_invoke_l(void *object, const char *name, ...);
char truffle_invoke_c(void *object, const char *name, ...);
float truffle_invoke_f(void *object, const char *name, ...);
double truffle_invoke_d(void *object, const char *name, ...);
bool truffle_invoke_b(void *object, const char *name, ...);

// GetSize
int truffle_get_size(void *object);

// Unbox
int truffle_unbox_i(void *object);
long truffle_unbox_l(void *object);
char truffle_unbox_c(void *object);
float truffle_unbox_f(void *object);
double truffle_unbox_d(void *object);
bool truffle_unbox_b(void *object);

// Read
void *truffle_read(void *object, const char *name);
int truffle_read_i(void *object, const char *name);
long truffle_read_l(void *object, const char *name);
char truffle_read_c(void *object, const char *name);
float truffle_read_f(void *object, const char *name);
double truffle_read_d(void *object, const char *name);
bool truffle_read_b(void *object, const char *name);

void *truffle_read_idx(void *object, int idx);
int truffle_read_idx_i(void *object, int idx);
long truffle_read_idx_l(void *object, int idx);
char truffle_read_idx_c(void *object, int idx);
float truffle_read_idx_f(void *object, int idx);
double truffle_read_idx_d(void *object, int idx);
bool truffle_read_idx_b(void *object, int idx);

// Write
void truffle_write(void *object, const char *name, void *value);
void truffle_write_i(void *object, const char *name, int value);
void truffle_write_l(void *object, const char *name, long value);
void truffle_write_c(void *object, const char *name, char value);
void truffle_write_f(void *object, const char *name, float value);
void truffle_write_d(void *object, const char *name, double value);
void truffle_write_b(void *object, const char *name, bool value);

void truffle_write_idx(void *object, int idx, void *value);
void truffle_write_idx_i(void *object, int idx, int value);
void truffle_write_idx_l(void *object, int idx, long value);
void truffle_write_idx_c(void *object, int idx, char value);
void truffle_write_idx_f(void *object, int idx, float value);
void truffle_write_idx_d(void *object, int idx, double value);
void truffle_write_idx_b(void *object, int idx, bool value);

#if defined(__cplusplus)
}
#endif

#endif
