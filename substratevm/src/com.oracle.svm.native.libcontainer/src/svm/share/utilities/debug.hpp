/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_DEBUG_HPP
#define SHARE_UTILITIES_DEBUG_HPP

#include "utilities/compilerWarnings.hpp"

#ifdef ASSERT
// error reporting helper functions

namespace svm_container {

[[noreturn]]
void report_vm_error(const char* file, int line, const char* error_msg);

[[noreturn]]
ATTRIBUTE_PRINTF(4, 5)
void report_vm_error(const char* file, int line, const char* error_msg,
                     const char* detail_fmt, ...);

} // namespace svm_container

#endif

#ifdef ASSERT
#define __FILENAME_ONLY__ __FILE__
#else
// NOTE (chaeubl): Avoid that __FILE__ embeds the full path into the binary.
#define __FILENAME_ONLY__ "unknown file"
#endif

// assertions
#ifndef ASSERT
#define vmassert(p, ...)
#else
#define vmassert(p, ...)                                                       \
do {                                                                           \
  if (!(p)) {                                            \
    report_vm_error(__FILENAME_ONLY__, __LINE__, "assert(" #p ") failed", __VA_ARGS__); \
  }                                                                            \
} while (0)
#endif

// For backward compatibility.
#define assert(p, ...) vmassert(p, __VA_ARGS__)

#ifndef PRINT_WARNINGS
#define warning(format, ...)
#else

namespace svm_container {

void warning(const char* format, ...);

} // namespace svm_container

#endif

#define STATIC_ASSERT(Cond) static_assert((Cond), #Cond)

#endif // SHARE_UTILITIES_DEBUG_HPP
