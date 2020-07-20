/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
 * Evaluate a file containing source of another language.
 *
 * The filename argument can be absolute or relative to the current working
 * directory.
 *
 * @param id the language identifier
 * @param filename the file to be evaluated
 * @return the result of the evaluation
 * @see org::graalvm::polyglot::Context::eval
 */
void *polyglot_eval_file(const char *id, const char *filename);

/**
 * Access a Java class via host interop.
 *
 * @param classname the name of the Java class
 * @return the Java class, as polyglot value
 */
void *polyglot_java_type(const char *classname);

/**
 * Access an argument of the current function.
 *
 * This function can be used to access arguments of the current function by
 * index. This function can be used to access varargs arguments without knowing
 * their exact type.
 */
void *polyglot_get_arg(int i);

/**
 * Get the number of arguments passed to the current function.
 *
 * This function can be used to get the number of passed arguments, regular and
 * varargs, without using the va_list API.
 */
int polyglot_get_arg_count();

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

/**
 * Check whether a polyglot value can be instantiated.
 *
 * Returns false for pointers that do not point to a polyglot value (see
 * {@link polyglot_is_value}).
 */
bool polyglot_can_instantiate(const void *object);

/**
 * Instantiate a polyglot value.
 *
 * @param object the polyglot value that should be instantiated
 * @param ... the arguments of the constructor
 * @return the new object, as polyglot value
 */
void *polyglot_new_instance(const void *object, ...);

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
 * Check whether a polyglot value contains a given named member.
 *
 * @param object the polyglot value to test
 * @param name the name of the member to be checked for existance
 * @return true if the member exists, false otherwise
 */
bool polyglot_has_member(const void *value, const char *name);

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
 * Remove a named member from a polyglot object.
 *
 * @param object the polyglot value to modify
 * @param name the name of the member to be removed
 * @return true if the member was successfully removed, false otherwise
 */
bool polyglot_remove_member(void *object, const char *name);

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

/**
 * Remove an array element from a polyglot array.
 *
 * @param array the polyglot array to modify
 * @param idx the index of the removed array element
 * @return true if the array element was successfully removed, false otherwise
 */
bool polyglot_remove_array_element(void *array, int idx);

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
 * Opaque handle representing a polyglot type.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 */
typedef struct __polyglot_typeid *polyglot_typeid;

/**
 * Declare an array type.
 *
 * @param base the element type of the array
 * @param len the array length
 * @return a new typeid referring to an array of base with length len
 */
polyglot_typeid polyglot_array_typeid(polyglot_typeid base, uint64_t len);

/**
 * Converts a polyglot value to a dynamic struct or array pointer.
 *
 * The typeid passed to this function must refer to a struct or array type.
 * Passing a primitive typeid is not valid.
 *
 * @see polyglot_as_MyStruct
 * @see polyglot_as_MyStruct_array
 *
 * @param value a polyglot value
 * @param typeId the type of the polyglot value
 * @return struct or array view of the polyglot value
 */
void *polyglot_as_typed(void *value, polyglot_typeid typeId);

/**
 * Create a polyglot value from a native pointer to a struct or array.
 *
 * The typeid passed to this function must refer to a struct or array type.
 * Passing a primitive typeid is not valid.
 *
 * @see polyglot_from_MyStruct
 * @see polyglot_from_MyStruct_array
 *
 * @param ptr a pointer to a native struct or array
 * @param typeid the type of ptr
 * @return a polyglot value representing ptr
 */
void *polyglot_from_typed(void *ptr, polyglot_typeid typeId);

/**
 * Internal function. Do not use directly.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 * @see POLYGLOT_DECLARE_TYPE
 */
polyglot_typeid __polyglot_as_typeid(void *ptr);

/**
 * Internal macro. Do not use directly.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 * @see POLYGLOT_DECLARE_TYPE
 */
#define __POLYGLOT_DECLARE_GENERIC_ARRAY(typedecl, typename)                                                                                         \
    __attribute__((always_inline)) static inline polyglot_typeid polyglot_##typename##_typeid() {                                                    \
        static typedecl __polyglot_typeid_##typename[0];                                                                                             \
        return __polyglot_as_typeid(__polyglot_typeid_##typename);                                                                                   \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline typedecl *polyglot_as_##typename##_array(void *p) {                                                 \
        void *ret = polyglot_as_typed(p, polyglot_array_typeid(polyglot_##typename##_typeid(), 0));                                                  \
        return (typedecl *) ret;                                                                                                                     \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline void *polyglot_from_##typename##_array(typedecl *arr, uint64_t len) {                               \
        return polyglot_from_typed(arr, polyglot_array_typeid(polyglot_##typename##_typeid(), len));                                                 \
    }

__POLYGLOT_DECLARE_GENERIC_ARRAY(bool, boolean)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int8_t, i8)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int16_t, i16)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int32_t, i32)
__POLYGLOT_DECLARE_GENERIC_ARRAY(int64_t, i64)
__POLYGLOT_DECLARE_GENERIC_ARRAY(float, float)
__POLYGLOT_DECLARE_GENERIC_ARRAY(double, double)

/**
 * Internal macro. Do not use directly.
 *
 * @see POLYGLOT_DECLARE_STRUCT
 * @see POLYGLOT_DECLARE_TYPE
 */
#define __POLYGLOT_DECLARE_GENERIC_TYPE(typedecl, typename)                                                                                          \
    __POLYGLOT_DECLARE_GENERIC_ARRAY(typedecl, typename)                                                                                             \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline typedecl *polyglot_as_##typename(void *p) {                                                         \
        void *ret = polyglot_as_typed(p, polyglot_##typename##_typeid());                                                                            \
        return (typedecl *) ret;                                                                                                                     \
    }                                                                                                                                                \
                                                                                                                                                     \
    __attribute__((always_inline)) static inline void *polyglot_from_##typename(typedecl * s) {                                                      \
        return polyglot_from_typed(s, polyglot_##typename##_typeid());                                                                               \
    }

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
 * This macro will generate the following functions:
 *
 * \code
 * polyglot_typeid polyglot_MyStruct_typeid();
 * struct MyStruct *polyglot_as_MyStruct(void *value);
 * struct MyStruct *polyglot_as_MyStruct_array(void *value);
 * void *polyglot_from_MyStruct(struct MyStruct *s);
 * void *polyglot_from_MyStruct_array(struct MyStruct *arr, uint64_t len);
 * \endcode
 */
#define POLYGLOT_DECLARE_STRUCT(type) __POLYGLOT_DECLARE_GENERIC_TYPE(struct type, type)

/**
 * Declare polyglot conversion functions for a user-defined anonymous struct type.
 *
 * Given this type definition:
 * \code
 * typedef struct {
 *   int someMember;
 *   ...
 * } MyType;
 *
 * POLYGLOT_DECLARE_TYPE(MyType)
 * \endcode
 *
 * This macro will generate the following functions:
 *
 * \code
 * polyglot_typeid polyglot_MyType_typeid();
 * MyType *polyglot_as_MyType(void *value);
 * MyType *polyglot_as_MyType_array(void *value);
 * void *polyglot_from_MyType(MyType *s);
 * void *polyglot_from_MyType_array(MyType *arr, uint64_t len);
 * \endcode
 */
#define POLYGLOT_DECLARE_TYPE(type) __POLYGLOT_DECLARE_GENERIC_TYPE(type, type)

#ifdef DOXYGEN // documentation only

/**
 * Get a polyglot typeid for the primitive bool type.
 */
static polyglot_typeid polyglot_boolean_typeid();

/**
 * Get a polyglot typeid for the primitive int8_t type.
 */
static polyglot_typeid polyglot_i8_typeid();

/**
 * Get a polyglot typeid for the primitive int16_t type.
 */
static polyglot_typeid polyglot_i16_typeid();

/**
 * Get a polyglot typeid for the primitive int32_t type.
 */
static polyglot_typeid polyglot_i32_typeid();

/**
 * Get a polyglot typeid for the primitive int64_t type.
 */
static polyglot_typeid polyglot_i64_typeid();

/**
 * Get a polyglot typeid for the primitive float type.
 */
static polyglot_typeid polyglot_float_typeid();

/**
 * Get a polyglot typeid for the primitive double type.
 */
static polyglot_typeid polyglot_double_typeid();

/**
 * Converts a polyglot value to an integer array.
 *
 * For example, this code snippet:
 *
 * \code
 * int32_t *arr = polyglot_as_i32_array(arrayValue);
 * for (int i = 0; i < polyglot_get_array_size(arr); i++) {
 *   sum += arr[i];
 * }
 * \endcode
 *
 * is equivalent to
 *
 * \code
 * for (int i = 0; i < polyglot_get_array_size(arrayValue); i++) {
 *   void *elem = polyglot_get_array_element(arrayValue, i);
 *   sum += polyglot_as_i32(elem);
 * }
 * \endcode
 *
 * The returned pointer is a view of the original value, and does not need to be
 * freed separately.
 *
 * \param value a polyglot array value
 * \return array view of the polyglot value
 */
static int32_t *polyglot_as_i32_array(void *value);

/**
 * Create a polyglot value from a native pointer to a primitive integer array.
 * The resulting polyglot value can be passed to other languages and accessed
 * from there.
 *
 * For example, given this code snippet:
 *
 * \code
 * int32_t *s = calloc(len, sizeof(*s));
 * s[idx] = ...;
 * void *value = polyglot_from_i32_array(s, len);
 * someJSFunction(value);
 * \endcode
 *
 * The following JavaScript code can access the native pointer as if it were a
 * JavaScript array:
 *
 * \code
 * function someJSFunction(value) {
 *   ...
 *   result = value[idx];
 *   ...
 * }
 * \endcode
 *
 * The array access will be bounds checked with the given array length.
 *
 * The returned pointer will be semantically equal to the original pointer. In
 * particular, if one of them is freed, the other will become invalid.
 *
 * \param arr a pointer to a primitive integer array
 * \param len the length of the array
 * \return a polyglot value representing arr
 */
static void *polyglot_from_i32_array(int32_t *arr, uint64_t len);

/**
 * Converts a polyglot value to a bool array.
 *
 * \see polyglot_as_i32_array
 */
static bool *polyglot_as_boolean_array(void *value);

/**
 * Converts a polyglot value to an integer array.
 *
 * \see polyglot_as_i32_array
 */
static int8_t *polyglot_as_i8_array(void *value);

/**
 * Converts a polyglot value to an integer array.
 *
 * \see polyglot_as_i32_array
 */
static int16_t *polyglot_as_i16_array(void *value);

/**
 * Converts a polyglot value to an integer array.
 *
 * \see polyglot_as_i32_array
 */
static int64_t *polyglot_as_i64_array(void *value);

/**
 * Converts a polyglot value to a float array.
 *
 * \see polyglot_as_i32_array
 */
static float *polyglot_as_float_array(void *value);

/**
 * Converts a polyglot value to a double array.
 *
 * \see polyglot_as_i32_array
 */
static double *polyglot_as_double_array(void *value);

/**
 * Create a polyglot value from a native pointer to a primitive bool array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_boolean_array(bool *arr, uint64_t len);

/**
 * Create a polyglot value from a native pointer to a primitive integer array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_i8_array(int8_t *arr, uint64_t len);

/**
 * Create a polyglot value from a native pointer to a primitive integer array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_i16_array(int16_t *arr, uint64_t len);

/**
 * Create a polyglot value from a native pointer to a primitive integer array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_i64_array(int64_t *arr, uint64_t len);

/**
 * Create a polyglot value from a native pointer to a primitive float array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_float_array(float *arr, uint64_t len);

/**
 * Create a polyglot value from a native pointer to a primitive double array.
 *
 * \see polyglot_from_i32_array
 */
static void *polyglot_from_double_array(double *arr, uint64_t len);

struct MyStruct;

/**
 * Get a polyglot type id value for the type MyStruct.
 *
 * This typeid can be used with the functions {@link polyglot_as_typed} and
 * {@link polyglot_from_typed}.
 */
polyglot_typeid polyglot_MyStruct_typeid();

/**
 * Converts a polyglot value to a pointer to MyStruct. Accessing members of the
 * returned value is equivalent to calling {@link polyglot_get_member} or
 * {@link polyglot_put_member} on the original value.
 *
 * For example, this code snippet:
 *
 * \code
 * struct MyStruct *myStruct = polyglot_as_MyStruct(value);
 * int x = myStruct->someMember;
 * myStruct->someMember = 42;
 * \endcode
 *
 * is equivalent to
 *
 * \code
 * int x = polyglot_as_i32(polyglot_get_member(value, "someMember"));
 * polyglot_put_member(value, "someMember", (int) 42);
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
 * void *nested = polyglot_get_member(value, "nestedStruct");
 * polyglot_put_member(nested, "x", (int) 42);
 * \endcode
 *
 * The returned pointer is a view of the original value, and does not need to be
 * freed separately.
 *
 * \param value a polyglot value with members corresponding to struct members
 * \return struct view of the polyglot value
 * \see POLYGLOT_DECLARE_STRUCT
 */
struct MyStruct *polyglot_as_MyStruct(void *value);

/**
 * Converts a polyglot value to an array of MyStruct. Accessing the returned
 * array is equivalent to calling {@link polyglot_get_array_element} or
 * {@link polyglot_set_array_element} on the original value.
 *
 * For example, this code snippet:
 *
 * \code
 * struct MyStruct *arr = polyglot_as_MyStruct_array(arrayValue);
 * for (int i = 0; i < polyglot_get_array_size(arr); i++) {
 *   sum += arr[i].someMember;
 * }
 * \endcode
 *
 * is equivalent to
 *
 * \code
 * for (int i = 0; i < polyglot_get_array_size(arrayValue); i++) {
 *   void *elem = polyglot_get_array_element(arrayValue, i);
 *   sum += polyglot_as_i32(polyglot_get_member(elem, "someMember"));
 * }
 * \endcode
 *
 * The returned pointer is a view of the original value, and does not need to be
 * freed separately.
 *
 * \param value a polyglot array value
 * \return array view of the polyglot value
 * \see POLYGLOT_DECLARE_STRUCT
 */
struct MyStruct *polyglot_as_MyStruct_array(void *value);

/**
 * Create a polyglot value from a native pointer to MyStruct. The resulting
 * polyglot value can be passed to other languages and accessed from there.
 *
 * For example, given this code snippet:
 *
 * \code
 * struct MyStruct *s = malloc(sizeof(*s));
 * s->someMember = ...;
 * void *value = polyglot_from_MyStruct(s);
 * someJSFunction(value);
 * \endcode
 *
 * The following JavaScript code can access the native pointer as if it were a
 * JavaScript object:
 *
 * \code
 * function someJSFunction(value) {
 *   ...
 *   result = value.someMember;
 *   ...
 * }
 * \endcode
 *
 * Primitive or pointer members will be {@link polyglot_get_member readable} and
 * {@link polyglot_put_member writable}. Members that are inline structured
 * types will be only {@link polyglot_get_member readable}.
 *
 * The returned pointer will be semantically equal to the original pointer. In
 * particular, if one of them is freed, the other will become invalid.
 *
 * \param s a pointer to a single MyStruct
 * \return a polyglot value representing s
 * \see POLYGLOT_DECLARE_STRUCT
 */
void *polyglot_from_MyStruct(struct MyStruct *s);

/**
 * Create a polyglot value from a native pointer to an array of MyStruct. The
 * resulting polyglot value can be passed to other languages and accessed from
 * there.
 *
 * For example, given this code snippet:
 *
 * \code
 * struct MyStruct *s = calloc(len, sizeof(*s));
 * s[idx].someMember = ...;
 * void *value = polyglot_from_MyStruct_array(s, len);
 * someJSFunction(value);
 * \endcode
 *
 * The following JavaScript code can access the native pointer as if it were a
 * JavaScript array:
 *
 * \code
 * function someJSFunction(value) {
 *   ...
 *   result = value[idx].someMember;
 *   ...
 * }
 * \endcode
 *
 * The array access will be bounds checked with the given array length.
 *
 * The returned pointer will be semantically equal to the original pointer. In
 * particular, if one of them is freed, the other will become invalid.
 *
 * \param arr a pointer to an array of MyStruct
 * \param len the length of the array
 * \return a polyglot value representing arr
 * \see POLYGLOT_DECLARE_STRUCT
 */
void *polyglot_from_MyStruct_array(struct MyStruct *arr, uint64_t len);
#endif

/** @} */

#if defined(__cplusplus)
}
#endif

/** @} */

#endif
