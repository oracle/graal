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

/**
 * \defgroup polyglot LLVM Polyglot API
 * @{
 * @brief Access to the Polyglot API from LLVM.
 *
 * The functions in this module can deal with polyglot values from different
 * languages. Polyglot values don't have a real C-level type. All pointers in
 * LLVM programs can potentially point to polyglot values.
 *
 * Pointers to polyglot values try to emulate the behavior of native pointers
 * where possible. See {@link docs/INTEROP.md} for a description of this
 * behavior.
 *
 * Polyglot values are garbage collected. There is no need to explicitly free
 * values that are returned by functions in this module.
 *
 * The functions in this module can be used to access polyglot values explicitly.
 *
 * @file polyglot.h
 */

#if defined(__cplusplus)
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>

/**
 * Import a value from the global polyglot
 * {@link org::graalvm::polyglot::Context::getPolyglotBindings bindings}.
 *
 * @param name The name of the imported value.
 * @return the imported value
 */
void *polyglot_import(const char *name);

/**
 * Export a value to the global polyglot
 * {@link org::graalvm::polyglot::Context::getPolyglotBindings bindings}.
 *
 * @param name the name of the exported value
 * @param value the exported value
 */
void polyglot_export(const char *name, void *value);

/**
 * Evaluate a source of another language.
 *
 * @param id the language identifier
 * @param code the source code to be evaluated
 * @return the result of the evaluation
 * @see org::graalvm::polyglot::Context::eval
 */
void *polyglot_eval(const char *id, const char *code);

/**
 * Access an argument of the current function.
 *
 * This function can be used to access arguments of the current function by
 * index. This function can be used to access varargs arguments without knowing
 * their exact type.
 */
void *polyglot_get_arg(int i);

/**
 * \defgroup typecheck type checking functions
 * @{
 */

/**
 * Check whether a pointer points to a polyglot value.
 *
 * @see org::graalvm::polyglot::Value
 */
bool polyglot_is_value(const void *value);

/**
 * Check whether a polyglot value is NULL.
 *
 * Note that this is different from a native NULL pointer. A native pointer can
 * point to a concrete polyglot value, but the value it points to can still
 * be NULL.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 *
 * @see org::graalvm::polyglot::Value::isNull
 */
bool polyglot_is_null(const void *value);

/**
 * Check whether a polyglot value is a number.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 *
 * @see org::graalvm::polyglot::Value::isNumber
 */
bool polyglot_is_number(const void *value);

/**
 * Check whether a polyglot value is a boolean.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 *
 * Note that in the Polyglot API, booleans are distinct from numbers.
 *
 * @see org::graalvm::polyglot::Value::isBoolean
 */
bool polyglot_is_boolean(const void *value);

/**
 * Check whether a polyglot value is a string.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 *
 * @see org::graalvm::polyglot::Value::isString
 */
bool polyglot_is_string(const void *value);

/** @} */

/**
 * \defgroup unbox primitive conversion functions
 * @{
 */

/**
 * Check whether a polyglot number can be losslessly converted to a signed
 * 8-bit integer (int8_t).
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_i8(const void *value);

/**
 * Check whether a polyglot number can be losslessly converted to a signed
 * 16-bit integer (int16_t).
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_i16(const void *value);

/**
 * Check whether a polyglot number can be losslessly converted to a signed
 * 32-bit integer (int32_t).
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_i32(const void *value);

/**
 * Check whether a polyglot number can be losslessly converted to a signed
 * 64-bit integer (int64_t).
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_i64(const void *value);

/**
 * Check whether a polyglot number can be losslessly converted to a single
 * precision floating point number.
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_float(const void *value);

/**
 * Check whether a polyglot number can be losslessly converted to a double
 * precision floating point number.
 *
 * Returns false for pointers that do not point to a polyglot number (see
 * {@link polyglot_is_number}).
 */
bool polyglot_fits_in_double(const void *value);

/**
 * Convert a polyglot number to a primitive int8_t value.
 */
int8_t polyglot_as_i8(const void *value);

/**
 * Convert a polyglot number to a primitive int16_t value.
 */
int16_t polyglot_as_i16(const void *value);

/**
 * Convert a polyglot number to a primitive int32_t value.
 */
int32_t polyglot_as_i32(const void *value);

/**
 * Convert a polyglot number to a primitive int64_t value.
 */
int64_t polyglot_as_i64(const void *value);

/**
 * Convert a polyglot number to a primitive float value.
 */
float polyglot_as_float(const void *value);

/**
 * Convert a polyglot number to a primitive double value.
 */
double polyglot_as_double(const void *value);

/**
 * Convert a polyglot boolean to a primitive bool value.
 */
bool polyglot_as_boolean(const void *value);

/** @} */

/**
 * \defgroup execute function execution
 * @{
 *
 * Run executable polyglot values.
 *
 * Pointers to executable polyglot values can be cast to a function pointer type.
 * These function pointers can be called like a regular function to execute the
 * polyglot value.
 *
 * Example:
 *
 *     int (*fn)(int, double) = polyglot_import("fn");
 *     int ret = fn(5, 3.7);
 */

/**
 * Check whether a polyglot value can be executed.
 *
 * To execute a polyglot value, cast it to a function pointer type and call it.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 */
bool polyglot_can_execute(const void *value);

/**
 * Invoke an object oriented method on a polyglot value.
 *
 * @param object the object containing the method
 * @param name the name of the method to be invoked
 * @param ... the arguments of the method
 * @return the return value of the method
 */
void *polyglot_invoke(void *object, const char *name, ...);

/** @} */

/**
 * \defgroup access structured value access
 * @{
 *
 * Access to polyglot arrays and objects.
 *
 * Polyglot values can have members or array elements, or both.
 */

/**
 * Check whether a polyglot value is an object with named members.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 */
bool polyglot_has_members(const void *value);

/**
 * Read a named member from a polyglot object.
 *
 * The result is also a polyglot value. Use the {@link unbox primitive conversion
 * functions} if the member contains a primitive value.
 *
 * @param object the polyglot value to read from
 * @param name the name of the member to be read
 * @return a polyglot value
 */
void *polyglot_get_member(const void *object, const char *name);

/**
 * Put a named member into a polyglot object.
 *
 * This varargs function has to be called with exactly 3 arguments. The type
 * of the third argument is arbitrary. The function accepts polyglot values,
 * primitives or pointers.
 *
 * @param object the polyglot object to write to
 * @param name the name of the member to be put
 * @param ... the written value
 */
void polyglot_put_member(void *object, const char *name, ...);

/**
 * Check whether a polyglot value has array elements.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 */
bool polyglot_has_array_elements(const void *value);

/**
 * Get the size of the polyglot array.
 */
uint64_t polyglot_get_array_size(const void *array);

/**
 * Read an array element from a polyglot array.
 *
 * The result is also a polyglot value. Use the {@link unbox primitive conversion
 * functions} if the member contains a primitive value.
 *
 * @param array the polyglot array to read from
 * @param idx the index of the array element
 * @return a polyglot value
 */
void *polyglot_get_array_element(const void *array, int idx);

/**
 * Write an array element to a polyglot array.
 *
 * This varargs function has to be called with exactly 3 arguments. The type
 * of the third argument is arbitrary. The function accepts polyglot values,
 * primitives or pointers.
 *
 * @param array the polyglot array to write to
 * @param idx the index of the array element
 * @param ... the written value
 */
void polyglot_set_array_element(void *array, int idx, ...);

/** @} */

/**
 * \defgroup string string functions
 * @{
 *
 * Access polyglot string values.
 *
 * Polyglot string values (see {@link polyglot_is_string}) are unicode strings.
 *
 * Polyglot string values can be cast to `char *`, but this will only work
 * reliably for strings that contain only LATIN-1 characters and no embedded
 * zero characters. The reverse is not true, exported `char *` values will not
 * be seen by other languages as polyglot strings.
 *
 * The functions in this module can be used to explicitly convert polyglot
 * strings to and from C strings.
 *
 * The string functions that take a `charset` argument can work with arbitrary
 * character set encodings. They accept the same charset names as the Java
 * {@link java::nio::Charset::forName} function. The length arguments or return
 * values are always in bytes, regardless of the character set, even if it uses
 * multiple bytes per character.
 */

/**
 * Get the size of a polyglot string value.
 *
 * @return the size of the string, in unicode characters
 */
uint64_t polyglot_get_string_size(const void *value);

/**
 * Convert a polyglot value to a C string.
 *
 * The C string will be written to a caller-provided buffer. This function
 * produces a zero-terminated string.
 *
 * At most `bufsize` bytes will be written to the buffer. *Attention:* If the
 * string including the zero-terminator does not fit in the buffer, the result
 * string in `buffer` may not be zero-terminated. Check the return value to
 * be safe.
 *
 * @param value the polyglot value to be converted
 * @param buffer the result buffer
 * @param bufsize the size of the result buffer, in bytes
 * @param charset the character set for conversion
 * @return the number of bytes written to the buffer, *excluding* the
 *         zero-terminator
 */
uint64_t polyglot_as_string(const void *value, char *buffer, uint64_t bufsize, const char *charset);

/**
 * Convert a zero-terminated C string to a polyglot string.
 *
 * The C string is expected to be terminated with a zero character. If the
 * string has embedded zero characters, the conversion will stop at the first.
 *
 * @param string a zero-terminated C string
 * @param charset the character set of the C string
 * @return a polyglot string
 */
void *polyglot_from_string(const char *string, const char *charset);

/**
 * Convert a C string with explicit size to a polyglot string.
 *
 * This function reads exactly `len` bytes from `string`. Zero characters are
 * not handled specially, they are included in the returned string.
 *
 * @param string a C string
 * @param size the size of the C string, in bytes
 * @param charset the character set of the C string
 * @return a polyglot string
 */
void *polyglot_from_string_n(const char *string, uint64_t size, const char *charset);

/** @} */

/**
 * \defgroup custom user type access
 * @{
 *
 * Convert polyglot values to user defined C types.
 */

/**
 * Internal function. Do not use directly.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 */
void *__polyglot_as_typed(void *ptr, void *typeid);

/**
 * Internal function. Do not use directly.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 */
void *__polyglot_as_typed_array(void *ptr, void *typeid);

/**
 * Declare polyglot conversion functions for a user-defined struct type.
 *
 * Given this struct definition:
 * \code
 * struct MyStruct {
 *   int someMember;
 *   ...
 * };
 *
 * POLYGLOT_DECLARE_STRUCT(MyStruct)
 * \endcode
 *
 * This macro will generate two conversion functions:
 *
 * \code
 * struct MyStruct *polyglot_as_MyStruct(void *value);
 * \endcode
 *
 * Converts a polyglot value to a pointer to MyStruct. Accessing members of the
 * returned value is equivalent to calling {@link polyglot_get_member} or
 * {@link polyglot_put_member} on the original value.
 *
 * \code
 * struct MyStruct *polyglot_as_MyStruct_array(void *value);
 * \endcode
 *
 * Converts a polyglot value to an array of MyStruct. Accessing the returned
 * array is equivalent to calling {@link polyglot_get_array_element} or
 * {@link polyglot_set_array_element} on the original value.
 *
 * For example, this code snippet:
 *
 * \code
 * struct MyStruct *myStruct = polyglot_as_MyStruct(value);
 * int x = myStruct->someMember;
 * myStruct->someMember = 42;
 *
 * struct MyStruct *arr = polyglot_as_MyStruct_array(arrayValue);
 * for (int i = 0; i < polyglot_get_array_size(arr); i++) {
 *   sum += arr[i].someMember;
 * }
 * \endcode
 *
 * is equivalent to
 *
 * \code
 * int x = polyglot_as_i32(polyglot_get_member(value, "someMember"));
 * polyglot_put_member(value, "someMember", (int) 42);
 *
 * for (int i = 0; i < polyglot_get_array_size(arrayValue); i++) {
 *   void *elem = polyglot_get_array_element(arrayValue, i);
 *   sum += polyglot_as_i32(polyglot_get_member(elem, "someMember"));
 * }
 * \endcode
 *
 * This will also work for structs or arrays nested inside the top level struct.
 * In this case, accesses will produce multiple nested access calls.
 *
 * For example:
 *
 * \code
 * myStruct->nestedStruct.x = 42;
 * \endcode
 *
 * is equivalent to
 *
 * \code
 * polyglot_put_member(polyglot_get_member(value, "nestedStruct"), (int) 42);
 * \endcode
 */
#define POLYGLOT_DECLARE_STRUCT(type)                                                                                                                \
  static struct type __polyglot_typeid_##type[0];                                                                                                    \
                                                                                                                                                     \
  __attribute__((always_inline)) static inline struct type *polyglot_as_##type(void *p) {                                                            \
    void *ret = __polyglot_as_typed(p, __polyglot_typeid_##type);                                                                                    \
    return (struct type *)ret;                                                                                                                       \
  }                                                                                                                                                  \
                                                                                                                                                     \
  __attribute__((always_inline)) static inline struct type *polyglot_as_##type##_array(void *p) {                                                    \
    void *ret = __polyglot_as_typed_array(p, __polyglot_typeid_##type);                                                                              \
    return (struct type *)ret;                                                                                                                       \
  }

/** @} */

#if defined(__cplusplus)
}
#endif

/** @} */

#endif
