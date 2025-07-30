/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#ifndef SHARE_UTILITIES_PERMITFORBIDDENFUNCTIONS_HPP
#define SHARE_UTILITIES_PERMITFORBIDDENFUNCTIONS_HPP

#include "utilities/compilerWarnings.hpp"
#include "utilities/globalDefinitions.hpp"

#ifndef NATIVE_IMAGE
#ifdef _WINDOWS
#include "permitForbiddenFunctions_windows.hpp"
#else
#include "permitForbiddenFunctions_posix.hpp"
#endif
#endif // !NATIVE_IMAGE

// Provide wrappers for some functions otherwise forbidden from use in HotSpot.
//
// There may be special circumstances where an otherwise forbidden function
// really does need to be used.  One example is in the implementation of a
// corresponding os:: function.
//
// Wrapper functions are provided for such forbidden functions.  These
// wrappers are defined in a context where the forbidding warnings are
// suppressed.  They are defined in a special namespace, to highlight uses as
// unusual and requiring increased scrutiny.
//
// Note that there are several seemingly plausible shorter alternatives to
// these written-out wrapper functions.  All that have been tried don't work
// for one reason or another.


namespace svm_container {

namespace permit_forbidden_function {
BEGIN_ALLOW_FORBIDDEN_FUNCTIONS

#ifndef NATIVE_IMAGE
[[noreturn]] inline void exit(int status) { ::exit(status); }
[[noreturn]] inline void _exit(int status) { ::_exit(status); }
#endif // !NATIVE_IMAGE

ATTRIBUTE_PRINTF(3, 0)
inline int vsnprintf(char* str, size_t size, const char* format, va_list ap) {
  return ::vsnprintf(str, size, format, ap);
}

#ifndef NATIVE_IMAGE
inline void* malloc(size_t size) { return ::malloc(size); }
inline void free(void* ptr) { return ::free(ptr); }
inline void* calloc(size_t nmemb, size_t size) { return ::calloc(nmemb, size); }
inline void* realloc(void* ptr, size_t size) { return ::realloc(ptr, size); }

inline char* strdup(const char* s) { return ::strdup(s); }
#endif // !NATIVE_IMAGE

END_ALLOW_FORBIDDEN_FUNCTIONS
} // namespace permit_forbidden_function


} // namespace svm_container

#endif // SHARE_UTILITIES_PERMITFORBIDDENFUNCTIONS_HPP
