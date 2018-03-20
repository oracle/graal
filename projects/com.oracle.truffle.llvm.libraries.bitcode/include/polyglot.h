/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

#ifndef POLYGLOT_H
#define POLYGLOT_H

#if defined(__cplusplus)
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>

void *polyglot_import(const char *name);
void polyglot_export(const char *name, void *value);

void *polyglot_eval(const char *id, const char *code);

// varargs handling
void *polyglot_get_arg(int i);

// type checks
bool polyglot_is_value(const void *value);
bool polyglot_is_null(const void *value);
bool polyglot_is_number(const void *value);
bool polyglot_is_boolean(const void *value);
bool polyglot_is_string(const void *object);

// unboxing
bool polyglot_fits_in_i8(const void *value);
bool polyglot_fits_in_i16(const void *value);
bool polyglot_fits_in_i32(const void *value);
bool polyglot_fits_in_i64(const void *value);
bool polyglot_fits_in_float(const void *value);
bool polyglot_fits_in_double(const void *value);

int8_t polyglot_as_i8(const void *value);
int16_t polyglot_as_i16(const void *value);
int32_t polyglot_as_i32(const void *value);
int64_t polyglot_as_i64(const void *value);
float polyglot_as_float(const void *value);
double polyglot_as_double(const void *value);
bool polyglot_as_boolean(const void *value);

// executing
bool polyglot_can_execute(const void *value);
// to send the execute message, cast the value to a function pointer and call it

// array access
bool polyglot_has_array_elements(const void *value);

uint64_t polyglot_get_array_size(const void *value);
void *polyglot_get_array_element(const void *value, int idx);
void polyglot_set_array_element(void *value, int idx, ...);

// member access
bool polyglot_has_members(const void *value);

void *polyglot_get_member(const void *value, const char *name);
void polyglot_put_member(void *value, const char *name, ...);

// strings
uint64_t polyglot_get_string_size(void *object);

uint64_t polyglot_as_string(void *object, char *buffer, uint64_t buflen, const char *charset);

void *polyglot_from_string(const char *string, const char *charset);
void *polyglot_from_string_n(const char *string, uint64_t len, const char *charset);

#if defined(__cplusplus)
}
#endif

#endif
