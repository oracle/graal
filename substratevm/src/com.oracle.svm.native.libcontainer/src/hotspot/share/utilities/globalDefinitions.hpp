/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_GLOBALDEFINITIONS_HPP
#define SHARE_UTILITIES_GLOBALDEFINITIONS_HPP

#include "utilities/compilerWarnings.hpp"
#include "utilities/debug.hpp"
#include "utilities/macros.hpp"


#include COMPILER_HEADER(utilities/globalDefinitions)

#include <cstddef>
#include <cstdint>
#include <limits>
#include <type_traits>


// Defaults for macros that might be defined per compiler.
#ifndef NOINLINE
#define NOINLINE
#endif
#ifndef ALWAYSINLINE
#define ALWAYSINLINE inline
#endif


// Format 64-bit quantities.
#define INT64_FORMAT             "%"          PRId64
#define INT64_PLUS_FORMAT        "%+"         PRId64
#define INT64_FORMAT_X           "0x%"        PRIx64
#define INT64_FORMAT_X_0         "0x%016"     PRIx64
#define INT64_FORMAT_W(width)    "%"   #width PRId64
#define UINT64_FORMAT            "%"          PRIu64
#define UINT64_FORMAT_X          "0x%"        PRIx64
#define UINT64_FORMAT_X_0        "0x%016"     PRIx64
#define UINT64_FORMAT_W(width)   "%"   #width PRIu64
#define UINT64_FORMAT_0          "%016"       PRIx64

// Format jlong, if necessary
#ifndef JLONG_FORMAT
#define JLONG_FORMAT             INT64_FORMAT
#endif
#ifndef JLONG_FORMAT_W
#define JLONG_FORMAT_W(width)    INT64_FORMAT_W(width)
#endif
#ifndef JULONG_FORMAT
#define JULONG_FORMAT            UINT64_FORMAT
#endif
#ifndef JULONG_FORMAT_X
#define JULONG_FORMAT_X          UINT64_FORMAT_X
#endif


//-------------------------------------------
// Constant for jlong (standardized by C++11)

// Build a 64bit integer constant
#define CONST64(x)  (x ## LL)
#define UCONST64(x) (x ## ULL)

const jlong min_jlong = CONST64(0x8000000000000000);
const jlong max_jlong = CONST64(0x7fffffffffffffff);

// for timer info max values which include all bits, 0xffffffffffffffff
const jlong all_bits_jlong = ~jlong(0);


const size_t K                  = 1024;
const size_t M                  = K*K;
const size_t G                  = M*K;

const jlong NANOSECS_PER_SEC      = CONST64(1000000000);
const jint  NANOSECS_PER_MILLISEC = 1000000;



// Additional Java basic types

typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

// Unsigned byte types for os and stream.hpp

// Unsigned one, two, four and eight byte quantities used for describing
// the .class file format. See JVM book chapter 4.

typedef jubyte  u1;
typedef jushort u2;
typedef juint   u4;
typedef julong  u8;

const jubyte  max_jubyte  = (jubyte)-1;  // 0xFF       largest jubyte
const jushort max_jushort = (jushort)-1; // 0xFFFF     largest jushort
const juint   max_juint   = (juint)-1;   // 0xFFFFFFFF largest juint
const julong  max_julong  = (julong)-1;  // 0xFF....FF largest julong

typedef jbyte  s1;
typedef jshort s2;
typedef jint   s4;
typedef jlong  s8;


// It is necessary to use templates here. Having normal overloaded
// functions does not work because it is necessary to provide both 32-
// and 64-bit overloaded functions, which does not work, and having
// explicitly-typed versions of these routines (i.e., MAX2I, MAX2L)
// will be even more error-prone than macros.
template<class T> constexpr T MAX2(T a, T b)           { return (a > b) ? a : b; }
template<class T> constexpr T MIN2(T a, T b)           { return (a < b) ? a : b; }

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_HPP
