/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
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

#include <graalvm/llvm/polyglot.h>
#include "common.h"

void *polyglot_import(const char *name) {
    should_not_reach();
    return NULL;
}
void polyglot_export(const char *name, void *value) {
    should_not_reach();
}
void *polyglot_eval(const char *id, const char *code) {
    should_not_reach();
    return NULL;
}
void *polyglot_eval_file(const char *id, const char *filename) {
    should_not_reach();
    return NULL;
}
void *polyglot_java_type(const char *classname) {
    should_not_reach();
    return NULL;
}
void *polyglot_get_arg(int i) {
    should_not_reach();
    return NULL;
}
int polyglot_get_arg_count() {
    should_not_reach();
    return 0;
}
int8_t polyglot_as_i8(const void *value) {
    should_not_reach();
    return 0;
}
int16_t polyglot_as_i16(const void *value) {
    should_not_reach();
    return 0;
}
int32_t polyglot_as_i32(const void *value) {
    should_not_reach();
    return 0;
}
int64_t polyglot_as_i64(const void *value) {
    should_not_reach();
    return 0;
}
float polyglot_as_float(const void *value) {
    should_not_reach();
    return 0;
}
double polyglot_as_double(const void *value) {
    should_not_reach();
    return 0;
}
bool polyglot_as_boolean(const void *value) {
    should_not_reach();
    return false;
}
bool polyglot_can_execute(const void *value) {
    should_not_reach();
    return false;
}
void *polyglot_invoke(void *object, const char *name, ...) {
    should_not_reach();
    return NULL;
}
bool polyglot_can_instantiate(const void *object) {
    should_not_reach();
    return false;
}
void *polyglot_new_instance(const void *object, ...) {
    should_not_reach();
    return NULL;
}
bool polyglot_has_members(const void *value) {
    should_not_reach();
    return false;
}
bool polyglot_has_member(const void *value, const char *name) {
    should_not_reach();
    return false;
}
void *polyglot_get_member(const void *object, const char *name) {
    should_not_reach();
    return NULL;
}
void polyglot_put_member(void *object, const char *name, ...) {
    should_not_reach();
}
bool polyglot_remove_member(void *object, const char *name) {
    should_not_reach();
    return false;
}
bool polyglot_has_array_elements(const void *value) {
    should_not_reach();
    return false;
}
uint64_t polyglot_get_array_size(const void *array) {
    should_not_reach();
    return 0;
}
void *polyglot_get_array_element(const void *array, int idx) {
    should_not_reach();
    return NULL;
}
void polyglot_set_array_element(void *array, int idx, ...) {
    should_not_reach();
}
bool polyglot_remove_array_element(void *array, int idx) {
    should_not_reach();
    return false;
}
uint64_t polyglot_get_string_size(const void *value) {
    should_not_reach();
    return 0;
}
uint64_t polyglot_as_string(const void *value, char *buffer, uint64_t bufsize, const char *charset) {
    should_not_reach();
    return 0;
}
void *polyglot_from_string(const char *string, const char *charset) {
    should_not_reach();
    return NULL;
}
void *polyglot_from_string_n(const char *string, uint64_t size, const char *charset) {
    should_not_reach();
    return NULL;
}
// we can provide a defensive implementation for the following functions
bool polyglot_is_value(const void *value) {
    return false;
}
bool polyglot_is_null(const void *value) {
    return false;
}
bool polyglot_is_number(const void *value) {
    return false;
}
bool polyglot_is_boolean(const void *value) {
    return false;
}
bool polyglot_is_string(const void *value) {
    return false;
}
bool polyglot_fits_in_i8(const void *value) {
    return false;
}
bool polyglot_fits_in_i16(const void *value) {
    return false;
}
bool polyglot_fits_in_i32(const void *value) {
    return false;
}
bool polyglot_fits_in_i64(const void *value) {
    return false;
}
bool polyglot_fits_in_float(const void *value) {
    return false;
}
bool polyglot_fits_in_double(const void *value) {
    return false;
}
polyglot_typeid polyglot_array_typeid(polyglot_typeid base, uint64_t len) {
    return NULL;
}
polyglot_typeid __polyglot_as_typeid(void *ptr) {
    return NULL;
}
void *polyglot_as_typed(void *value, polyglot_typeid typeId) {
    return value;
}
void *polyglot_from_typed(void *ptr, polyglot_typeid typeId) {
    return ptr;
}
