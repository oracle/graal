/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP
#define SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP

#include "jni.h"

// This file holds compiler-dependent includes,
// globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

#include <alloca.h>
#include <ctype.h>
#include <inttypes.h>
#include <string.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
// In stdlib.h on AIX malloc is defined as a macro causing
// compiler errors when resolving them in different depths as it
// happens in the log tags. This avoids the macro.
#if (defined(__VEC__) || defined(__AIXVEC)) && defined(AIX) \
    && defined(__open_xl_version__) && __open_xl_version__ >= 17
  #undef malloc

namespace svm_container {

  extern void *malloc(size_t) asm("vec_malloc");

} // namespace svm_container

#endif
#include <wchar.h>

#include <math.h>
#include <time.h>
#include <fcntl.h>
#include <dlfcn.h>
#include <pthread.h>

#include <limits.h>
#include <errno.h>

#if defined(LINUX) || defined(_ALLBSD_SOURCE) || defined(_AIX)
#include <signal.h>
#ifndef __OpenBSD__
#include <ucontext.h>
#endif
#ifdef __APPLE__
  #include <AvailabilityMacros.h>
  #include <mach/mach.h>
#endif
#include <sys/time.h>
#endif // LINUX || _ALLBSD_SOURCE

// NULL vs NULL_WORD:
// On Linux NULL is defined as a special type '__null'. Assigning __null to
// integer variable will cause gcc warning. Use NULL_WORD in places where a
// pointer is stored as integer value.  On some platforms, sizeof(intptr_t) >
// sizeof(void*), so here we want something which is integer type, but has the
// same size as a pointer.
#ifdef __GNUC__
  #ifdef _LP64
    #define NULL_WORD  0L
  #else
    // Cast 0 to intptr_t rather than int32_t since they are not the same type
    // on platforms such as Mac OS X.
    #define NULL_WORD  ((intptr_t)0)
  #endif
#else
  #define NULL_WORD  NULL
#endif

// checking for nanness
#if defined(__APPLE__)

namespace svm_container {

inline int g_isnan(double f) { return isnan(f); }

} // namespace svm_container

#elif defined(LINUX) || defined(_ALLBSD_SOURCE) || defined(_AIX)

namespace svm_container {

inline int g_isnan(float  f) { return isnan(f); }
inline int g_isnan(double f) { return isnan(f); }

} // namespace svm_container

#else
#error "missing platform-specific definition here"
#endif

#define CAN_USE_NAN_DEFINE 1


// Checking for finiteness


namespace svm_container {

inline int g_isfinite(jfloat  f)                 { return isfinite(f); }
inline int g_isfinite(jdouble f)                 { return isfinite(f); }


// Formatting.
#ifdef _LP64
# ifdef __APPLE__
# define FORMAT64_MODIFIER "ll"
# else
# define FORMAT64_MODIFIER "l"
# endif
#else // !_LP64
#define FORMAT64_MODIFIER "ll"
#endif // _LP64

// gcc warns about applying offsetof() to non-POD object or calculating
// offset directly when base address is null. The -Wno-invalid-offsetof
// option could be used to suppress this warning, but we instead just
// avoid the use of offsetof().
//
// FIXME: This macro is complex and rather arcane. Perhaps we should
// use offsetof() instead, with the invalid-offsetof warning
// temporarily disabled.
#define offset_of(klass,field)                          \
([]() {                                                 \
  alignas(16) char space[sizeof (klass)];               \
  klass* dummyObj = (klass*)space;                      \
  char* c = (char*)(void*)&dummyObj->field;             \
  return (size_t)(c - space);                           \
}())


#if defined(_LP64) && defined(__APPLE__)
#define JLONG_FORMAT          "%ld"
#define JLONG_FORMAT_W(width) "%" #width "ld"
#endif // _LP64 && __APPLE__

#define THREAD_LOCAL __thread

// Inlining support
#define NOINLINE     __attribute__ ((noinline))
#define ALWAYSINLINE inline __attribute__ ((always_inline))
#define ATTRIBUTE_FLATTEN __attribute__ ((flatten))


} // namespace svm_container

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_GCC_HPP
