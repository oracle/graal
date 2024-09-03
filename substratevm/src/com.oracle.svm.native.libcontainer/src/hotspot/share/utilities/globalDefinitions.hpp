/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef NATIVE_IMAGE
// Get constants like JVM_T_CHAR and JVM_SIGNATURE_INT, before pulling in <jvm.h>.
#include "classfile_constants.h"
#endif // !NATIVE_IMAGE

#include COMPILER_HEADER(utilities/globalDefinitions)

#include <cstddef>
#include <cstdint>
#include <limits>
#include <type_traits>

#ifndef NATIVE_IMAGE

namespace svm_container {

class oopDesc;

} // namespace svm_container

#endif // !NATIVE_IMAGE

// Defaults for macros that might be defined per compiler.
#ifndef NOINLINE
#define NOINLINE
#endif
#ifndef ALWAYSINLINE
#define ALWAYSINLINE inline
#endif

#ifndef NATIVE_IMAGE
#ifndef ATTRIBUTE_ALIGNED
#define ATTRIBUTE_ALIGNED(x) alignas(x)
#endif

#ifndef ATTRIBUTE_FLATTEN
#define ATTRIBUTE_FLATTEN
#endif

// These are #defines to selectively turn on/off the Print(Opto)Assembly
// capabilities. Choices should be led by a tradeoff between
// code size and improved supportability.
// if PRINT_ASSEMBLY then PRINT_ABSTRACT_ASSEMBLY must be true as well
// to have a fallback in case hsdis is not available.
#if defined(PRODUCT)
  #define SUPPORT_ABSTRACT_ASSEMBLY
  #define SUPPORT_ASSEMBLY
  #undef  SUPPORT_OPTO_ASSEMBLY      // Can't activate. In PRODUCT, many dump methods are missing.
  #undef  SUPPORT_DATA_STRUCTS       // Of limited use. In PRODUCT, many print methods are empty.
#else
  #define SUPPORT_ABSTRACT_ASSEMBLY
  #define SUPPORT_ASSEMBLY
  #define SUPPORT_OPTO_ASSEMBLY
  #define SUPPORT_DATA_STRUCTS
#endif
#if defined(SUPPORT_ASSEMBLY) && !defined(SUPPORT_ABSTRACT_ASSEMBLY)
  #define SUPPORT_ABSTRACT_ASSEMBLY
#endif

// This file holds all globally used constants & types, class (forward)
// declarations and a few frequently used utility functions.

// Declare the named class to be noncopyable.  This macro must be followed by
// a semi-colon.  The macro provides deleted declarations for the class's copy
// constructor and assignment operator.  Because these operations are deleted,
// they cannot be defined and potential callers will fail to compile.
#define NONCOPYABLE(C) C(C const&) = delete; C& operator=(C const&) = delete /* next token must be ; */


//----------------------------------------------------------------------------------------------------
// Printf-style formatters for fixed- and variable-width types as pointers and
// integers.  These are derived from the definitions in inttypes.h.  If the platform
// doesn't provide appropriate definitions, they should be provided in
// the compiler-specific definitions file (e.g., globalDefinitions_gcc.hpp)

// Guide to the suffixes used in the format specifiers for integers:
//        - print the decimal value:                   745565
//  _X    - print as hexadecimal, without leading 0s: 0x12345
//  _X_0  - print as hexadecimal, with leading 0s: 0x00012345
//  _W(w) - prints w sized string with the given value right
//          adjusted. Use -w to print left adjusted.
//
// Note that the PTR format specifiers print using 0x with leading zeros,
// just like the _X_0 version for integers.

// Format 8-bit quantities.
#define INT8_FORMAT_X_0          "0x%02"      PRIx8
#define UINT8_FORMAT_X_0         "0x%02"      PRIx8

// Format 16-bit quantities.
#define INT16_FORMAT_X_0         "0x%04"      PRIx16
#define UINT16_FORMAT_X_0        "0x%04"      PRIx16

// Format 32-bit quantities.
#define INT32_FORMAT             "%"          PRId32
#define INT32_FORMAT_X           "0x%"        PRIx32
#define INT32_FORMAT_X_0         "0x%08"      PRIx32
#define INT32_FORMAT_W(width)    "%"   #width PRId32
#define UINT32_FORMAT            "%"          PRIu32
#define UINT32_FORMAT_X          "0x%"        PRIx32
#define UINT32_FORMAT_X_0        "0x%08"      PRIx32
#define UINT32_FORMAT_W(width)   "%"   #width PRIu32
#endif // !NATIVE_IMAGE

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

// Format integers which change size between 32- and 64-bit.
#define SSIZE_FORMAT             "%"          PRIdPTR
#define SSIZE_PLUS_FORMAT        "%+"         PRIdPTR
#define SSIZE_FORMAT_W(width)    "%"   #width PRIdPTR
#define SIZE_FORMAT              "%"          PRIuPTR
#define SIZE_FORMAT_X            "0x%"        PRIxPTR
#ifdef _LP64
#define SIZE_FORMAT_X_0          "0x%016"     PRIxPTR
#else
#define SIZE_FORMAT_X_0          "0x%08"      PRIxPTR
#endif
#define SIZE_FORMAT_W(width)     "%"   #width PRIuPTR

#define INTX_FORMAT              "%"          PRIdPTR
#define INTX_FORMAT_X            "0x%"        PRIxPTR
#define INTX_FORMAT_W(width)     "%"   #width PRIdPTR
#define UINTX_FORMAT             "%"          PRIuPTR
#define UINTX_FORMAT_X           "0x%"        PRIxPTR
#ifdef _LP64
#define UINTX_FORMAT_X_0         "0x%016"     PRIxPTR
#else
#define UINTX_FORMAT_X_0         "0x%08"      PRIxPTR
#endif
#define UINTX_FORMAT_W(width)    "%"   #width PRIuPTR

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

#ifndef NATIVE_IMAGE
// Format pointers which change size between 32- and 64-bit.
#ifdef  _LP64
#define INTPTR_FORMAT            "0x%016"     PRIxPTR
#define PTR_FORMAT               "0x%016"     PRIxPTR
#else   // !_LP64
#define INTPTR_FORMAT            "0x%08"      PRIxPTR
#define PTR_FORMAT               "0x%08"      PRIxPTR
#endif  // _LP64

// Convert pointer to intptr_t, for use in printing pointers.

namespace svm_container {

inline intptr_t p2i(const volatile void* p) {
  return (intptr_t) p;
}

#define BOOL_TO_STR(_b_) ((_b_) ? "true" : "false")

//----------------------------------------------------------------------------------------------------
// Forbid the use of various C library functions.
// Some of these have os:: replacements that should normally be used instead.
// Others are considered security concerns, with preferred alternatives.

FORBID_C_FUNCTION(void exit(int), "use os::exit");
FORBID_C_FUNCTION(void _exit(int), "use os::exit");
FORBID_C_FUNCTION(char* strerror(int), "use os::strerror");
FORBID_C_FUNCTION(char* strtok(char*, const char*), "use strtok_r");
FORBID_C_FUNCTION(int sprintf(char*, const char*, ...), "use os::snprintf");
FORBID_C_FUNCTION(int vsprintf(char*, const char*, va_list), "use os::vsnprintf");
FORBID_C_FUNCTION(int vsnprintf(char*, size_t, const char*, va_list), "use os::vsnprintf");

// All of the following functions return raw C-heap pointers (sometimes as an option, e.g. realpath or getwd)
// or, in case of free(), take raw C-heap pointers. Don't use them unless you are really sure you must.
FORBID_C_FUNCTION(void* malloc(size_t size), "use os::malloc");
FORBID_C_FUNCTION(void* calloc(size_t nmemb, size_t size), "use os::malloc and zero out manually");
FORBID_C_FUNCTION(void free(void *ptr), "use os::free");
FORBID_C_FUNCTION(void* realloc(void *ptr, size_t size), "use os::realloc");
FORBID_C_FUNCTION(char* strdup(const char *s), "use os::strdup");
FORBID_C_FUNCTION(char* strndup(const char *s, size_t n), "don't use");
FORBID_C_FUNCTION(int posix_memalign(void **memptr, size_t alignment, size_t size), "don't use");
FORBID_C_FUNCTION(void* aligned_alloc(size_t alignment, size_t size), "don't use");
FORBID_C_FUNCTION(char* realpath(const char* path, char* resolved_path), "use os::Posix::realpath");
FORBID_C_FUNCTION(char* get_current_dir_name(void), "use os::get_current_directory()");
FORBID_C_FUNCTION(char* getwd(char *buf), "use os::get_current_directory()");
FORBID_C_FUNCTION(wchar_t* wcsdup(const wchar_t *s), "don't use");
FORBID_C_FUNCTION(void* reallocf(void *ptr, size_t size), "don't use");

//----------------------------------------------------------------------------------------------------
// Constants

const int LogBytesPerShort   = 1;
const int LogBytesPerInt     = 2;
#ifdef _LP64
const int LogBytesPerWord    = 3;
#else
const int LogBytesPerWord    = 2;
#endif
const int LogBytesPerLong    = 3;

const int BytesPerShort      = 1 << LogBytesPerShort;
const int BytesPerInt        = 1 << LogBytesPerInt;
const int BytesPerWord       = 1 << LogBytesPerWord;
const int BytesPerLong       = 1 << LogBytesPerLong;

const int LogBitsPerByte     = 3;
const int LogBitsPerShort    = LogBitsPerByte + LogBytesPerShort;
const int LogBitsPerInt      = LogBitsPerByte + LogBytesPerInt;
const int LogBitsPerWord     = LogBitsPerByte + LogBytesPerWord;
const int LogBitsPerLong     = LogBitsPerByte + LogBytesPerLong;

const int BitsPerByte        = 1 << LogBitsPerByte;
const int BitsPerShort       = 1 << LogBitsPerShort;
const int BitsPerInt         = 1 << LogBitsPerInt;
const int BitsPerWord        = 1 << LogBitsPerWord;
const int BitsPerLong        = 1 << LogBitsPerLong;

const int WordAlignmentMask  = (1 << LogBytesPerWord) - 1;
const int LongAlignmentMask  = (1 << LogBytesPerLong) - 1;

const int oopSize            = sizeof(char*); // Full-width oop
extern int heapOopSize;                       // Oop within a java object
const int wordSize           = sizeof(char*);
const int longSize           = sizeof(jlong);
const int jintSize           = sizeof(jint);
const int size_tSize         = sizeof(size_t);

const int BytesPerOop        = BytesPerWord;  // Full-width oop

extern int LogBytesPerHeapOop;                // Oop within a java object
extern int LogBitsPerHeapOop;
extern int BytesPerHeapOop;
extern int BitsPerHeapOop;

const int BitsPerJavaInteger = 32;
const int BitsPerJavaLong    = 64;
const int BitsPerSize_t      = size_tSize * BitsPerByte;

// Size of a char[] needed to represent a jint as a string in decimal.
const int jintAsStringSize = 12;

// An opaque type, so that HeapWord* can be a generic pointer into the heap.
// We require that object sizes be measured in units of heap words (e.g.
// pointer-sized values), so that given HeapWord* hw,
//   hw += oop(hw)->foo();
// works, where foo is a method (like size or scavenge) that returns the
// object size.
class HeapWordImpl;             // Opaque, never defined.
typedef HeapWordImpl* HeapWord;

// Analogous opaque struct for metadata allocated from metaspaces.
class MetaWordImpl;             // Opaque, never defined.
typedef MetaWordImpl* MetaWord;

// HeapWordSize must be 2^LogHeapWordSize.
const int HeapWordSize        = sizeof(HeapWord);
#ifdef _LP64
const int LogHeapWordSize     = 3;
#else
const int LogHeapWordSize     = 2;
#endif
const int HeapWordsPerLong    = BytesPerLong / HeapWordSize;
const int LogHeapWordsPerLong = LogBytesPerLong - LogHeapWordSize;

// The minimum number of native machine words necessary to contain "byte_size"
// bytes.
inline size_t heap_word_size(size_t byte_size) {
  return (byte_size + (HeapWordSize-1)) >> LogHeapWordSize;
}

inline jfloat jfloat_cast(jint x);
inline jdouble jdouble_cast(jlong x);

} // namespace svm_container

#endif // !NATIVE_IMAGE

//-------------------------------------------
// Constant for jlong (standardized by C++11)

// Build a 64bit integer constant
#define CONST64(x)  (x ## LL)
#define UCONST64(x) (x ## ULL)


namespace svm_container {

const jlong min_jlong = CONST64(0x8000000000000000);
const jlong max_jlong = CONST64(0x7fffffffffffffff);

#ifndef NATIVE_IMAGE
//-------------------------------------------
// Constant for jdouble
const jlong min_jlongDouble = CONST64(0x0000000000000001);
const jdouble min_jdouble = jdouble_cast(min_jlongDouble);
const jlong max_jlongDouble = CONST64(0x7fefffffffffffff);
const jdouble max_jdouble = jdouble_cast(max_jlongDouble);
#endif // !NATIVE_IMAGE

const size_t K                  = 1024;
const size_t M                  = K*K;
const size_t G                  = M*K;
#ifndef NATIVE_IMAGE
const size_t HWperKB            = K / sizeof(HeapWord);

// Constants for converting from a base unit to milli-base units.  For
// example from seconds to milliseconds and microseconds

const int MILLIUNITS    = 1000;         // milli units per base unit
const int MICROUNITS    = 1000000;      // micro units per base unit
const int NANOUNITS     = 1000000000;   // nano units per base unit
const int NANOUNITS_PER_MILLIUNIT = NANOUNITS / MILLIUNITS;
#endif // !NATIVE_IMAGE

const jlong NANOSECS_PER_SEC      = CONST64(1000000000);
const jint  NANOSECS_PER_MILLISEC = 1000000;

#ifndef NATIVE_IMAGE
// Unit conversion functions
// The caller is responsible for considering overflow.

inline int64_t nanos_to_millis(int64_t nanos) {
  return nanos / NANOUNITS_PER_MILLIUNIT;
}
inline int64_t millis_to_nanos(int64_t millis) {
  return millis * NANOUNITS_PER_MILLIUNIT;
}

// Proper units routines try to maintain at least three significant digits.
// In worst case, it would print five significant digits with lower prefix.
// G is close to MAX_SIZE on 32-bit platforms, so its product can easily overflow,
// and therefore we need to be careful.

inline const char* proper_unit_for_byte_size(size_t s) {
#ifdef _LP64
  if (s >= 100*G) {
    return "G";
  }
#endif
  if (s >= 100*M) {
    return "M";
  } else if (s >= 100*K) {
    return "K";
  } else {
    return "B";
  }
}

template <class T>
inline T byte_size_in_proper_unit(T s) {
#ifdef _LP64
  if (s >= 100*G) {
    return (T)(s/G);
  }
#endif
  if (s >= 100*M) {
    return (T)(s/M);
  } else if (s >= 100*K) {
    return (T)(s/K);
  } else {
    return s;
  }
}

#define PROPERFMT             SIZE_FORMAT "%s"
#define PROPERFMTARGS(s)      byte_size_in_proper_unit(s), proper_unit_for_byte_size(s)

#define RANGEFMT              "[" PTR_FORMAT " - " PTR_FORMAT "), (" SIZE_FORMAT " bytes)"
#define RANGEFMTARGS(p1, size) p2i(p1), p2i(p1 + size), size

inline const char* exact_unit_for_byte_size(size_t s) {
#ifdef _LP64
  if (s >= G && (s % G) == 0) {
    return "G";
  }
#endif
  if (s >= M && (s % M) == 0) {
    return "M";
  }
  if (s >= K && (s % K) == 0) {
    return "K";
  }
  return "B";
}

inline size_t byte_size_in_exact_unit(size_t s) {
#ifdef _LP64
  if (s >= G && (s % G) == 0) {
    return s / G;
  }
#endif
  if (s >= M && (s % M) == 0) {
    return s / M;
  }
  if (s >= K && (s % K) == 0) {
    return s / K;
  }
  return s;
}

#define EXACTFMT            SIZE_FORMAT "%s"
#define EXACTFMTARGS(s)     byte_size_in_exact_unit(s), exact_unit_for_byte_size(s)

// Memory size transition formatting.

#define HEAP_CHANGE_FORMAT "%s: " SIZE_FORMAT "K(" SIZE_FORMAT "K)->" SIZE_FORMAT "K(" SIZE_FORMAT "K)"

#define HEAP_CHANGE_FORMAT_ARGS(_name_, _prev_used_, _prev_capacity_, _used_, _capacity_) \
  (_name_), (_prev_used_) / K, (_prev_capacity_) / K, (_used_) / K, (_capacity_) / K

//----------------------------------------------------------------------------------------------------
// VM type definitions

// intx and uintx are the 'extended' int and 'extended' unsigned int types;
// they are 32bit wide on a 32-bit platform, and 64bit wide on a 64bit platform.

typedef intptr_t  intx;
typedef uintptr_t uintx;

const intx  min_intx  = (intx)1 << (sizeof(intx)*BitsPerByte-1);
const intx  max_intx  = (uintx)min_intx - 1;
const uintx max_uintx = (uintx)-1;

// Table of values:
//      sizeof intx         4               8
// min_intx             0x80000000      0x8000000000000000
// max_intx             0x7FFFFFFF      0x7FFFFFFFFFFFFFFF
// max_uintx            0xFFFFFFFF      0xFFFFFFFFFFFFFFFF

typedef unsigned int uint;   NEEDS_CLEANUP

//----------------------------------------------------------------------------------------------------
// Java type definitions

// All kinds of 'plain' byte addresses
typedef   signed char s_char;
typedef unsigned char u_char;
typedef u_char*       address;
typedef const u_char* const_address;

// Pointer subtraction.
// The idea here is to avoid ptrdiff_t, which is signed and so doesn't have
// the range we might need to find differences from one end of the heap
// to the other.
// A typical use might be:
//     if (pointer_delta(end(), top()) >= size) {
//       // enough room for an object of size
//       ...
// and then additions like
//       ... top() + size ...
// are safe because we know that top() is at least size below end().
inline size_t pointer_delta(const volatile void* left,
                            const volatile void* right,
                            size_t element_size) {
  assert(left >= right, "avoid underflow - left: " PTR_FORMAT " right: " PTR_FORMAT, p2i(left), p2i(right));
  return (((uintptr_t) left) - ((uintptr_t) right)) / element_size;
}

// A version specialized for HeapWord*'s.
inline size_t pointer_delta(const HeapWord* left, const HeapWord* right) {
  return pointer_delta(left, right, sizeof(HeapWord));
}
// A version specialized for MetaWord*'s.
inline size_t pointer_delta(const MetaWord* left, const MetaWord* right) {
  return pointer_delta(left, right, sizeof(MetaWord));
}

// pointer_delta_as_int is called to do pointer subtraction for nearby pointers that
// returns a non-negative int, usually used as a size of a code buffer range.
// This scales to sizeof(T).
template <typename T>
inline int pointer_delta_as_int(const volatile T* left, const volatile T* right) {
  size_t delta = pointer_delta(left, right, sizeof(T));
  assert(delta <= size_t(INT_MAX), "pointer delta out of range: %zu", delta);
  return static_cast<int>(delta);
}

//
// ANSI C++ does not allow casting from one pointer type to a function pointer
// directly without at best a warning. This macro accomplishes it silently
// In every case that is present at this point the value be cast is a pointer
// to a C linkage function. In some case the type used for the cast reflects
// that linkage and a picky compiler would not complain. In other cases because
// there is no convenient place to place a typedef with extern C linkage (i.e
// a platform dependent header file) it doesn't. At this point no compiler seems
// picky enough to catch these instances (which are few). It is possible that
// using templates could fix these for all cases. This use of templates is likely
// so far from the middle of the road that it is likely to be problematic in
// many C++ compilers.
//
#define CAST_TO_FN_PTR(func_type, value) (reinterpret_cast<func_type>(value))
#define CAST_FROM_FN_PTR(new_type, func_ptr) ((new_type)((uintptr_t)(func_ptr)))

// Need the correct linkage to call qsort without warnings
extern "C" {
  typedef int (*_sort_Fn)(const void *, const void *);
}
#endif // !NATIVE_IMAGE

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


} // namespace svm_container

#ifndef NATIVE_IMAGE

namespace svm_container {

const jbyte min_jbyte = -(1 << 7);       // smallest jbyte
const jbyte max_jbyte = (1 << 7) - 1;    // largest jbyte
const jshort min_jshort = -(1 << 15);    // smallest jshort
const jshort max_jshort = (1 << 15) - 1; // largest jshort

const jint min_jint = (jint)1 << (sizeof(jint)*BitsPerByte-1); // 0x80000000 == smallest jint
const jint max_jint = (juint)min_jint - 1;                     // 0x7FFFFFFF == largest jint

const jint min_jintFloat = (jint)(0x00000001);
const jfloat min_jfloat = jfloat_cast(min_jintFloat);
const jint max_jintFloat = (jint)(0x7f7fffff);
const jfloat max_jfloat = jfloat_cast(max_jintFloat);

//----------------------------------------------------------------------------------------------------
// JVM spec restrictions

const int max_method_code_size = 64*K - 1;  // JVM spec, 2nd ed. section 4.8.1 (p.134)

//----------------------------------------------------------------------------------------------------
// old CDS options
extern bool RequireSharedSpaces;
extern "C" {
// Make sure UseSharedSpaces is accessible to the serviceability agent.
extern JNIEXPORT jboolean UseSharedSpaces;
}

//----------------------------------------------------------------------------------------------------
// Object alignment, in units of HeapWords.
//
// Minimum is max(BytesPerLong, BytesPerDouble, BytesPerOop) / HeapWordSize, so jlong, jdouble and
// reference fields can be naturally aligned.

extern int MinObjAlignment;
extern int MinObjAlignmentInBytes;
extern int MinObjAlignmentInBytesMask;

extern int LogMinObjAlignment;
extern int LogMinObjAlignmentInBytes;

// Maximal size of heap where unscaled compression can be used. Also upper bound
// for heap placement: 4GB.
const  uint64_t UnscaledOopHeapMax = (uint64_t(max_juint) + 1);
// Maximal size of heap where compressed oops can be used. Also upper bound for heap
// placement for zero based compression algorithm: UnscaledOopHeapMax << LogMinObjAlignmentInBytes.
extern uint64_t OopEncodingHeapMax;

// Machine dependent stuff


} // namespace svm_container

#include CPU_HEADER(globalDefinitions)

// The maximum size of the code cache.  Can be overridden by targets.
#ifndef CODE_CACHE_SIZE_LIMIT
#define CODE_CACHE_SIZE_LIMIT (2*G)
#endif

// Allow targets to reduce the default size of the code cache.
#define CODE_CACHE_DEFAULT_LIMIT CODE_CACHE_SIZE_LIMIT

// To assure the IRIW property on processors that are not multiple copy
// atomic, sync instructions must be issued between volatile reads to
// assure their ordering, instead of after volatile stores.
// (See "A Tutorial Introduction to the ARM and POWER Relaxed Memory Models"
// by Luc Maranget, Susmit Sarkar and Peter Sewell, INRIA/Cambridge)
#ifdef CPU_MULTI_COPY_ATOMIC
// Not needed.

namespace svm_container {

const bool support_IRIW_for_not_multiple_copy_atomic_cpu = false;

} // namespace svm_container

#else
// From all non-multi-copy-atomic architectures, only PPC64 supports IRIW at the moment.
// Final decision is subject to JEP 188: Java Memory Model Update.

namespace svm_container {

const bool support_IRIW_for_not_multiple_copy_atomic_cpu = PPC64_ONLY(true) NOT_PPC64(false);

} // namespace svm_container

#endif

// The expected size in bytes of a cache line.
#ifndef DEFAULT_CACHE_LINE_SIZE
#error "Platform should define DEFAULT_CACHE_LINE_SIZE"
#endif

// The default padding size for data structures to avoid false sharing.
#ifndef DEFAULT_PADDING_SIZE
#error "Platform should define DEFAULT_PADDING_SIZE"
#endif


//----------------------------------------------------------------------------------------------------
// Miscellaneous

// 6302670 Eliminate Hotspot __fabsf dependency
// All fabs() callers should call this function instead, which will implicitly
// convert the operand to double, avoiding a dependency on __fabsf which
// doesn't exist in early versions of Solaris 8.

namespace svm_container {

inline double fabsd(double value) {
  return fabs(value);
}

// Returns numerator/denominator as percentage value from 0 to 100. If denominator
// is zero, return 0.0.
template<typename T>
inline double percent_of(T numerator, T denominator) {
  return denominator != 0 ? (double)numerator / (double)denominator * 100.0 : 0.0;
}

//----------------------------------------------------------------------------------------------------
// Special casts
// Cast floats into same-size integers and vice-versa w/o changing bit-pattern
typedef union {
  jfloat f;
  jint i;
} FloatIntConv;

typedef union {
  jdouble d;
  jlong l;
  julong ul;
} DoubleLongConv;

inline jint    jint_cast    (jfloat  x)  { return ((FloatIntConv*)&x)->i; }
inline jfloat  jfloat_cast  (jint    x)  { return ((FloatIntConv*)&x)->f; }

inline jlong   jlong_cast   (jdouble x)  { return ((DoubleLongConv*)&x)->l;  }
inline julong  julong_cast  (jdouble x)  { return ((DoubleLongConv*)&x)->ul; }
inline jdouble jdouble_cast (jlong   x)  { return ((DoubleLongConv*)&x)->d;  }

inline jint low (jlong value)                    { return jint(value); }
inline jint high(jlong value)                    { return jint(value >> 32); }

// the fancy casts are a hopefully portable way
// to do unsigned 32 to 64 bit type conversion
inline void set_low (jlong* value, jint low )    { *value &= (jlong)0xffffffff << 32;
                                                   *value |= (jlong)(julong)(juint)low; }

inline void set_high(jlong* value, jint high)    { *value &= (jlong)(julong)(juint)0xffffffff;
                                                   *value |= (jlong)high       << 32; }

inline jlong jlong_from(jint h, jint l) {
  jlong result = 0; // initialization to avoid warning
  set_high(&result, h);
  set_low(&result,  l);
  return result;
}

union jlong_accessor {
  jint  words[2];
  jlong long_value;
};

void basic_types_init(); // cannot define here; uses assert


// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicType : u1 {
// The values T_BOOLEAN..T_LONG (4..11) are derived from the JVMS.
  T_BOOLEAN     = JVM_T_BOOLEAN,
  T_CHAR        = JVM_T_CHAR,
  T_FLOAT       = JVM_T_FLOAT,
  T_DOUBLE      = JVM_T_DOUBLE,
  T_BYTE        = JVM_T_BYTE,
  T_SHORT       = JVM_T_SHORT,
  T_INT         = JVM_T_INT,
  T_LONG        = JVM_T_LONG,
  // The remaining values are not part of any standard.
  // T_OBJECT and T_VOID denote two more semantic choices
  // for method return values.
  // T_OBJECT and T_ARRAY describe signature syntax.
  // T_ADDRESS, T_METADATA, T_NARROWOOP, T_NARROWKLASS describe
  // internal references within the JVM as if they were Java
  // types in their own right.
  T_OBJECT      = 12,
  T_ARRAY       = 13,
  T_VOID        = 14,
  T_ADDRESS     = 15,
  T_NARROWOOP   = 16,
  T_METADATA    = 17,
  T_NARROWKLASS = 18,
  T_CONFLICT    = 19, // for stack value type with conflicting contents
  T_ILLEGAL     = 99
};

#define SIGNATURE_TYPES_DO(F, N)                \
    F(JVM_SIGNATURE_BOOLEAN, T_BOOLEAN, N)      \
    F(JVM_SIGNATURE_CHAR,    T_CHAR,    N)      \
    F(JVM_SIGNATURE_FLOAT,   T_FLOAT,   N)      \
    F(JVM_SIGNATURE_DOUBLE,  T_DOUBLE,  N)      \
    F(JVM_SIGNATURE_BYTE,    T_BYTE,    N)      \
    F(JVM_SIGNATURE_SHORT,   T_SHORT,   N)      \
    F(JVM_SIGNATURE_INT,     T_INT,     N)      \
    F(JVM_SIGNATURE_LONG,    T_LONG,    N)      \
    F(JVM_SIGNATURE_CLASS,   T_OBJECT,  N)      \
    F(JVM_SIGNATURE_ARRAY,   T_ARRAY,   N)      \
    F(JVM_SIGNATURE_VOID,    T_VOID,    N)      \
    /*end*/

inline bool is_java_type(BasicType t) {
  return T_BOOLEAN <= t && t <= T_VOID;
}

inline bool is_java_primitive(BasicType t) {
  return T_BOOLEAN <= t && t <= T_LONG;
}

inline bool is_subword_type(BasicType t) {
  // these guys are processed exactly like T_INT in calling sequences:
  return (t == T_BOOLEAN || t == T_CHAR || t == T_BYTE || t == T_SHORT);
}

inline bool is_signed_subword_type(BasicType t) {
  return (t == T_BYTE || t == T_SHORT);
}

inline bool is_unsigned_subword_type(BasicType t) {
  return (t == T_BOOLEAN || t == T_CHAR);
}

inline bool is_double_word_type(BasicType t) {
  return (t == T_DOUBLE || t == T_LONG);
}

inline bool is_reference_type(BasicType t, bool include_narrow_oop = false) {
  return (t == T_OBJECT || t == T_ARRAY || (include_narrow_oop && t == T_NARROWOOP));
}

inline bool is_integral_type(BasicType t) {
  return is_subword_type(t) || t == T_INT || t == T_LONG;
}

inline bool is_non_subword_integral_type(BasicType t) {
  return t == T_INT || t == T_LONG;
}

inline bool is_floating_point_type(BasicType t) {
  return (t == T_FLOAT || t == T_DOUBLE);
}

extern char type2char_tab[T_CONFLICT+1];     // Map a BasicType to a jchar
inline char type2char(BasicType t) { return (uint)t < T_CONFLICT+1 ? type2char_tab[t] : 0; }
extern int type2size[T_CONFLICT+1];         // Map BasicType to result stack elements
extern const char* type2name_tab[T_CONFLICT+1];     // Map a BasicType to a char*
extern BasicType name2type(const char* name);

const char* type2name(BasicType t);

inline jlong max_signed_integer(BasicType bt) {
  if (bt == T_INT) {
    return max_jint;
  }
  assert(bt == T_LONG, "unsupported");
  return max_jlong;
}

inline jlong min_signed_integer(BasicType bt) {
  if (bt == T_INT) {
    return min_jint;
  }
  assert(bt == T_LONG, "unsupported");
  return min_jlong;
}

// Auxiliary math routines
// least common multiple
extern size_t lcm(size_t a, size_t b);


// NOTE: replicated in SA in vm/agent/sun/jvm/hotspot/runtime/BasicType.java
enum BasicTypeSize {
  T_BOOLEAN_size     = 1,
  T_CHAR_size        = 1,
  T_FLOAT_size       = 1,
  T_DOUBLE_size      = 2,
  T_BYTE_size        = 1,
  T_SHORT_size       = 1,
  T_INT_size         = 1,
  T_LONG_size        = 2,
  T_OBJECT_size      = 1,
  T_ARRAY_size       = 1,
  T_NARROWOOP_size   = 1,
  T_NARROWKLASS_size = 1,
  T_VOID_size        = 0
};

// this works on valid parameter types but not T_VOID, T_CONFLICT, etc.
inline int parameter_type_word_count(BasicType t) {
  if (is_double_word_type(t))  return 2;
  assert(is_java_primitive(t) || is_reference_type(t), "no goofy types here please");
  assert(type2size[t] == 1, "must be");
  return 1;
}

// maps a BasicType to its instance field storage type:
// all sub-word integral types are widened to T_INT
extern BasicType type2field[T_CONFLICT+1];
extern BasicType type2wfield[T_CONFLICT+1];


// size in bytes
enum ArrayElementSize {
  T_BOOLEAN_aelem_bytes     = 1,
  T_CHAR_aelem_bytes        = 2,
  T_FLOAT_aelem_bytes       = 4,
  T_DOUBLE_aelem_bytes      = 8,
  T_BYTE_aelem_bytes        = 1,
  T_SHORT_aelem_bytes       = 2,
  T_INT_aelem_bytes         = 4,
  T_LONG_aelem_bytes        = 8,
#ifdef _LP64
  T_OBJECT_aelem_bytes      = 8,
  T_ARRAY_aelem_bytes       = 8,
#else
  T_OBJECT_aelem_bytes      = 4,
  T_ARRAY_aelem_bytes       = 4,
#endif
  T_NARROWOOP_aelem_bytes   = 4,
  T_NARROWKLASS_aelem_bytes = 4,
  T_VOID_aelem_bytes        = 0
};

extern int _type2aelembytes[T_CONFLICT+1]; // maps a BasicType to nof bytes used by its array element
#ifdef ASSERT
extern int type2aelembytes(BasicType t, bool allow_address = false); // asserts
#else
inline int type2aelembytes(BasicType t, bool allow_address = false) { return _type2aelembytes[t]; }
#endif

inline bool same_type_or_subword_size(BasicType t1, BasicType t2) {
  return (t1 == t2) || (is_subword_type(t1) && type2aelembytes(t1) == type2aelembytes(t2));
}

// JavaValue serves as a container for arbitrary Java values.

class JavaValue {

 public:
  typedef union JavaCallValue {
    jfloat   f;
    jdouble  d;
    jint     i;
    jlong    l;
    jobject  h;
    oopDesc* o;
  } JavaCallValue;

 private:
  BasicType _type;
  JavaCallValue _value;

 public:
  JavaValue(BasicType t = T_ILLEGAL) { _type = t; }

  JavaValue(jfloat value) {
    _type    = T_FLOAT;
    _value.f = value;
  }

  JavaValue(jdouble value) {
    _type    = T_DOUBLE;
    _value.d = value;
  }

 jfloat get_jfloat() const { return _value.f; }
 jdouble get_jdouble() const { return _value.d; }
 jint get_jint() const { return _value.i; }
 jlong get_jlong() const { return _value.l; }
 jobject get_jobject() const { return _value.h; }
 oopDesc* get_oop() const { return _value.o; }
 JavaCallValue* get_value_addr() { return &_value; }
 BasicType get_type() const { return _type; }

 void set_jfloat(jfloat f) { _value.f = f;}
 void set_jdouble(jdouble d) { _value.d = d;}
 void set_jint(jint i) { _value.i = i;}
 void set_jlong(jlong l) { _value.l = l;}
 void set_jobject(jobject h) { _value.h = h;}
 void set_oop(oopDesc* o) { _value.o = o;}
 void set_type(BasicType t) { _type = t; }

 jboolean get_jboolean() const { return (jboolean) (_value.i);}
 jbyte get_jbyte() const { return (jbyte) (_value.i);}
 jchar get_jchar() const { return (jchar) (_value.i);}
 jshort get_jshort() const { return (jshort) (_value.i);}

};


// TosState describes the top-of-stack state before and after the execution of
// a bytecode or method. The top-of-stack value may be cached in one or more CPU
// registers. The TosState corresponds to the 'machine representation' of this cached
// value. There's 4 states corresponding to the JAVA types int, long, float & double
// as well as a 5th state in case the top-of-stack value is actually on the top
// of stack (in memory) and thus not cached. The atos state corresponds to the itos
// state when it comes to machine representation but is used separately for (oop)
// type specific operations (e.g. verification code).

enum TosState {         // describes the tos cache contents
  btos = 0,             // byte, bool tos cached
  ztos = 1,             // byte, bool tos cached
  ctos = 2,             // char tos cached
  stos = 3,             // short tos cached
  itos = 4,             // int tos cached
  ltos = 5,             // long tos cached
  ftos = 6,             // float tos cached
  dtos = 7,             // double tos cached
  atos = 8,             // object cached
  vtos = 9,             // tos not cached
  number_of_states,
  ilgl                  // illegal state: should not occur
};


inline TosState as_TosState(BasicType type) {
  switch (type) {
    case T_BYTE   : return btos;
    case T_BOOLEAN: return ztos;
    case T_CHAR   : return ctos;
    case T_SHORT  : return stos;
    case T_INT    : return itos;
    case T_LONG   : return ltos;
    case T_FLOAT  : return ftos;
    case T_DOUBLE : return dtos;
    case T_VOID   : return vtos;
    case T_ARRAY  : // fall through
    case T_OBJECT : return atos;
    default       : return ilgl;
  }
}

inline BasicType as_BasicType(TosState state) {
  switch (state) {
    case btos : return T_BYTE;
    case ztos : return T_BOOLEAN;
    case ctos : return T_CHAR;
    case stos : return T_SHORT;
    case itos : return T_INT;
    case ltos : return T_LONG;
    case ftos : return T_FLOAT;
    case dtos : return T_DOUBLE;
    case atos : return T_OBJECT;
    case vtos : return T_VOID;
    default   : return T_ILLEGAL;
  }
}


// Helper function to convert BasicType info into TosState
// Note: Cannot define here as it uses global constant at the time being.
TosState as_TosState(BasicType type);


// JavaThreadState keeps track of which part of the code a thread is executing in. This
// information is needed by the safepoint code.
//
// There are 4 essential states:
//
//  _thread_new         : Just started, but not executed init. code yet (most likely still in OS init code)
//  _thread_in_native   : In native code. This is a safepoint region, since all oops will be in jobject handles
//  _thread_in_vm       : Executing in the vm
//  _thread_in_Java     : Executing either interpreted or compiled Java code (or could be in a stub)
//
// Each state has an associated xxxx_trans state, which is an intermediate state used when a thread is in
// a transition from one state to another. These extra states makes it possible for the safepoint code to
// handle certain thread_states without having to suspend the thread - making the safepoint code faster.
//
// Given a state, the xxxx_trans state can always be found by adding 1.
//
enum JavaThreadState {
  _thread_uninitialized     =  0, // should never happen (missing initialization)
  _thread_new               =  2, // just starting up, i.e., in process of being initialized
  _thread_new_trans         =  3, // corresponding transition state (not used, included for completeness)
  _thread_in_native         =  4, // running in native code
  _thread_in_native_trans   =  5, // corresponding transition state
  _thread_in_vm             =  6, // running in VM
  _thread_in_vm_trans       =  7, // corresponding transition state
  _thread_in_Java           =  8, // running in Java or in stub code
  _thread_in_Java_trans     =  9, // corresponding transition state (not used, included for completeness)
  _thread_blocked           = 10, // blocked in vm
  _thread_blocked_trans     = 11, // corresponding transition state
  _thread_max_state         = 12  // maximum thread state+1 - used for statistics allocation
};

enum LockingMode {
  // Use only heavy monitors for locking
  LM_MONITOR     = 0,
  // Legacy stack-locking, with monitors as 2nd tier
  LM_LEGACY      = 1,
  // New lightweight locking, with monitors as 2nd tier
  LM_LIGHTWEIGHT = 2
};

//----------------------------------------------------------------------------------------------------
// Special constants for debugging

const jint     badInt             = -3;                     // generic "bad int" value
const intptr_t badAddressVal      = -2;                     // generic "bad address" value
const intptr_t badOopVal          = -1;                     // generic "bad oop" value
const intptr_t badHeapOopVal      = (intptr_t) CONST64(0x2BAD4B0BBAADBABE); // value used to zap heap after GC
const int      badStackSegVal     = 0xCA;                   // value used to zap stack segments
const int      badHandleValue     = 0xBC;                   // value used to zap vm handle area
const int      badResourceValue   = 0xAB;                   // value used to zap resource area
const int      freeBlockPad       = 0xBA;                   // value used to pad freed blocks.
const int      uninitBlockPad     = 0xF1;                   // value used to zap newly malloc'd blocks.
const juint    uninitMetaWordVal  = 0xf7f7f7f7;             // value used to zap newly allocated metachunk
const jubyte   heapPaddingByteVal = 0xBD;                   // value used to zap object padding in the heap
const juint    badHeapWordVal     = 0xBAADBABE;             // value used to zap heap after GC
const int      badCodeHeapNewVal  = 0xCC;                   // value used to zap Code heap at allocation
const int      badCodeHeapFreeVal = 0xDD;                   // value used to zap Code heap at deallocation
const intptr_t badDispHeaderDeopt = 0xDE0BD000;             // value to fill unused displaced header during deoptimization
const intptr_t badDispHeaderOSR   = 0xDEAD05A0;             // value to fill unused displaced header during OSR

// (These must be implemented as #defines because C++ compilers are
// not obligated to inline non-integral constants!)
#define       badAddress        ((address)svm_container::badAddressVal)
#define       badHeapWord       (svm_container::badHeapWordVal)

// Default TaskQueue size is 16K (32-bit) or 128K (64-bit)
const uint TASKQUEUE_SIZE = (NOT_LP64(1<<14) LP64_ONLY(1<<17));

//----------------------------------------------------------------------------------------------------
// Utility functions for bitfield manipulations

const intptr_t AllBits    = ~0; // all bits set in a word
const intptr_t NoBits     =  0; // no bits set in a word
const jlong    NoLongBits =  0; // no bits set in a long
const intptr_t OneBit     =  1; // only right_most bit set in a word

// get a word with the n.th or the right-most or left-most n bits set
// (note: #define used only so that they can be used in enum constant definitions)
#define nth_bit(n)        (((n) >= BitsPerWord) ? 0 : (OneBit << (n)))
#define right_n_bits(n)   (nth_bit(n) - 1)

// bit-operations using a mask m
inline void   set_bits    (intptr_t& x, intptr_t m) { x |= m; }
inline void clear_bits    (intptr_t& x, intptr_t m) { x &= ~m; }
inline intptr_t mask_bits      (intptr_t  x, intptr_t m) { return x & m; }
inline jlong    mask_long_bits (jlong     x, jlong    m) { return x & m; }
inline bool mask_bits_are_true (intptr_t flags, intptr_t mask) { return (flags & mask) == mask; }

// bit-operations using the n.th bit
inline void    set_nth_bit(intptr_t& x, int n) { set_bits  (x, nth_bit(n)); }
inline void  clear_nth_bit(intptr_t& x, int n) { clear_bits(x, nth_bit(n)); }
inline bool is_set_nth_bit(intptr_t  x, int n) { return mask_bits (x, nth_bit(n)) != NoBits; }

// returns the bitfield of x starting at start_bit_no with length field_length (no sign-extension!)
inline intptr_t bitfield(intptr_t x, int start_bit_no, int field_length) {
  return mask_bits(x >> start_bit_no, right_n_bits(field_length));
}


//----------------------------------------------------------------------------------------------------
// Utility functions for integers

// Avoid use of global min/max macros which may cause unwanted double
// evaluation of arguments.
#ifdef max
#undef max
#endif

#ifdef min
#undef min
#endif

} // namespace svm_container

#endif // !NATIVE_IMAGE

// It is necessary to use templates here. Having normal overloaded
// functions does not work because it is necessary to provide both 32-
// and 64-bit overloaded functions, which does not work, and having
// explicitly-typed versions of these routines (i.e., MAX2I, MAX2L)
// will be even more error-prone than macros.

namespace svm_container {

template<class T> constexpr T MAX2(T a, T b)           { return (a > b) ? a : b; }
template<class T> constexpr T MIN2(T a, T b)           { return (a < b) ? a : b; }
#ifndef NATIVE_IMAGE
template<class T> constexpr T MAX3(T a, T b, T c)      { return MAX2(MAX2(a, b), c); }
template<class T> constexpr T MIN3(T a, T b, T c)      { return MIN2(MIN2(a, b), c); }
template<class T> constexpr T MAX4(T a, T b, T c, T d) { return MAX2(MAX3(a, b, c), d); }
template<class T> constexpr T MIN4(T a, T b, T c, T d) { return MIN2(MIN3(a, b, c), d); }

#define ABS(x) asserted_abs(x, __FILE__, __LINE__)

template<class T> inline T asserted_abs(T x, const char* file, int line) {
  bool valid_arg = !(std::is_integral<T>::value && x == std::numeric_limits<T>::min());
#ifdef ASSERT
  if (!valid_arg) {
    report_vm_error(file, line, "ABS: argument should not allow overflow");
  }
#endif
  // Prevent exposure to UB by checking valid_arg here as well.
  return (x < 0 && valid_arg) ? -x : x;
}

// Return the given value clamped to the range [min ... max]
template<typename T>
inline T clamp(T value, T min, T max) {
  assert(min <= max, "must be");
  return MIN2(MAX2(value, min), max);
}

inline bool is_odd (intx x) { return x & 1;      }
inline bool is_even(intx x) { return !is_odd(x); }

// abs methods which cannot overflow and so are well-defined across
// the entire domain of integer types.
static inline unsigned int uabs(unsigned int n) {
  union {
    unsigned int result;
    int value;
  };
  result = n;
  if (value < 0) result = 0-result;
  return result;
}
static inline julong uabs(julong n) {
  union {
    julong result;
    jlong value;
  };
  result = n;
  if (value < 0) result = 0-result;
  return result;
}
static inline julong uabs(jlong n) { return uabs((julong)n); }
static inline unsigned int uabs(int n) { return uabs((unsigned int)n); }

// "to" should be greater than "from."
inline size_t byte_size(void* from, void* to) {
  return pointer_delta(to, from, sizeof(char));
}

// Pack and extract shorts to/from ints:

inline u2 extract_low_short_from_int(u4 x) {
  return u2(x & 0xffff);
}

inline u2 extract_high_short_from_int(u4 x) {
  return u2((x >> 16) & 0xffff);
}

inline int build_int_from_shorts( u2 low, u2 high ) {
  return ((int)((unsigned int)high << 16) | (unsigned int)low);
}

// swap a & b
template<class T> static void swap(T& a, T& b) {
  T tmp = a;
  a = b;
  b = tmp;
}

// array_size_impl is a function that takes a reference to T[N] and
// returns a reference to char[N].  It is not ODR-used, so not defined.
template<typename T, size_t N> char (&array_size_impl(T (&)[N]))[N];

#define ARRAY_SIZE(array) sizeof(array_size_impl(array))

//----------------------------------------------------------------------------------------------------
// Sum and product which can never overflow: they wrap, just like the
// Java operations.  Note that we don't intend these to be used for
// general-purpose arithmetic: their purpose is to emulate Java
// operations.

// The goal of this code to avoid undefined or implementation-defined
// behavior.  The use of an lvalue to reference cast is explicitly
// permitted by Lvalues and rvalues [basic.lval].  [Section 3.10 Para
// 15 in C++03]
#define JAVA_INTEGER_OP(OP, NAME, TYPE, UNSIGNED_TYPE)  \
inline TYPE NAME (TYPE in1, TYPE in2) {                 \
  UNSIGNED_TYPE ures = static_cast<UNSIGNED_TYPE>(in1); \
  ures OP ## = static_cast<UNSIGNED_TYPE>(in2);         \
  return reinterpret_cast<TYPE&>(ures);                 \
}

JAVA_INTEGER_OP(+, java_add, jint, juint)
JAVA_INTEGER_OP(-, java_subtract, jint, juint)
JAVA_INTEGER_OP(*, java_multiply, jint, juint)
JAVA_INTEGER_OP(+, java_add, jlong, julong)
JAVA_INTEGER_OP(-, java_subtract, jlong, julong)
JAVA_INTEGER_OP(*, java_multiply, jlong, julong)

inline jint  java_negate(jint  v) { return java_subtract((jint) 0, v); }
inline jlong java_negate(jlong v) { return java_subtract((jlong)0, v); }

#undef JAVA_INTEGER_OP

// Provide integer shift operations with Java semantics.  No overflow
// issues - left shifts simply discard shifted out bits.  No undefined
// behavior for large or negative shift quantities; instead the actual
// shift distance is the argument modulo the lhs value's size in bits.
// No undefined or implementation defined behavior for shifting negative
// values; left shift discards bits, right shift sign extends.  We use
// the same safe conversion technique as above for java_add and friends.
#define JAVA_INTEGER_SHIFT_OP(OP, NAME, TYPE, XTYPE)    \
inline TYPE NAME (TYPE lhs, jint rhs) {                 \
  const uint rhs_mask = (sizeof(TYPE) * 8) - 1;         \
  STATIC_ASSERT(rhs_mask == 31 || rhs_mask == 63);      \
  XTYPE xres = static_cast<XTYPE>(lhs);                 \
  xres OP ## = (rhs & rhs_mask);                        \
  return reinterpret_cast<TYPE&>(xres);                 \
}

JAVA_INTEGER_SHIFT_OP(<<, java_shift_left, jint, juint)
JAVA_INTEGER_SHIFT_OP(<<, java_shift_left, jlong, julong)

// For signed shift right, assume C++ implementation >> sign extends.
//
// C++14 5.8/3: In the description of "E1 >> E2" it says "If E1 has a signed type
// and a negative value, the resulting value is implementation-defined."
//
// However, C++20 7.6.7/3 further defines integral arithmetic, as part of
// requiring two's-complement behavior.
// https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2018/p0907r3.html
// https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2018/p1236r1.html
// The corresponding C++20 text is "Right-shift on signed integral types is an
// arithmetic right shift, which performs sign-extension."
//
// As discussed in the two's complement proposal, all known modern C++ compilers
// already behave that way. And it is unlikely any would go off and do something
// different now, with C++20 tightening things up.
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right, jint, jint)
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right, jlong, jlong)
// For >>> use C++ unsigned >>.
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right_unsigned, jint, juint)
JAVA_INTEGER_SHIFT_OP(>>, java_shift_right_unsigned, jlong, julong)

#undef JAVA_INTEGER_SHIFT_OP

//----------------------------------------------------------------------------------------------------
// The goal of this code is to provide saturating operations for int/uint.
// Checks overflow conditions and saturates the result to min_jint/max_jint.
#define SATURATED_INTEGER_OP(OP, NAME, TYPE1, TYPE2) \
inline int NAME (TYPE1 in1, TYPE2 in2) {             \
  jlong res = static_cast<jlong>(in1);               \
  res OP ## = static_cast<jlong>(in2);               \
  if (res > max_jint) {                              \
    res = max_jint;                                  \
  } else if (res < min_jint) {                       \
    res = min_jint;                                  \
  }                                                  \
  return static_cast<int>(res);                      \
}

SATURATED_INTEGER_OP(+, saturated_add, int, int)
SATURATED_INTEGER_OP(+, saturated_add, int, uint)
SATURATED_INTEGER_OP(+, saturated_add, uint, int)
SATURATED_INTEGER_OP(+, saturated_add, uint, uint)

#undef SATURATED_INTEGER_OP

// Taken from rom section 8-2 of Henry S. Warren, Jr., Hacker's Delight (2nd ed.) (Addison Wesley, 2013), 173-174.
inline uint64_t multiply_high_unsigned(const uint64_t x, const uint64_t y) {
  const uint64_t x1 = x >> 32u;
  const uint64_t x2 = x & 0xFFFFFFFF;
  const uint64_t y1 = y >> 32u;
  const uint64_t y2 = y & 0xFFFFFFFF;
  const uint64_t z2 = x2 * y2;
  const uint64_t t = x1 * y2 + (z2 >> 32u);
  uint64_t z1 = t & 0xFFFFFFFF;
  const uint64_t z0 = t >> 32u;
  z1 += x2 * y1;

  return x1 * y1 + z0 + (z1 >> 32u);
}

// Taken from java.lang.Math::multiplyHigh which uses the technique from section 8-2 of Henry S. Warren, Jr.,
// Hacker's Delight (2nd ed.) (Addison Wesley, 2013), 173-174 but adapted for signed longs.
inline int64_t multiply_high_signed(const int64_t x, const int64_t y) {
  const jlong x1 = java_shift_right((jlong)x, 32);
  const jlong x2 = x & 0xFFFFFFFF;
  const jlong y1 = java_shift_right((jlong)y, 32);
  const jlong y2 = y & 0xFFFFFFFF;

  const uint64_t z2 = (uint64_t)x2 * y2;
  const int64_t t = x1 * y2 + (z2 >> 32u); // Unsigned shift
  int64_t z1 = t & 0xFFFFFFFF;
  const int64_t z0 = java_shift_right((jlong)t, 32);
  z1 += x2 * y1;

  return x1 * y1 + z0 + java_shift_right((jlong)z1, 32);
}

// Dereference vptr
// All C++ compilers that we know of have the vtbl pointer in the first
// word.  If there are exceptions, this function needs to be made compiler
// specific.
static inline void* dereference_vptr(const void* addr) {
  return *(void**)addr;
}

//----------------------------------------------------------------------------------------------------
// String type aliases used by command line flag declarations and
// processing utilities.

typedef const char* ccstr;
typedef const char* ccstrlist;   // represents string arguments which accumulate

//----------------------------------------------------------------------------------------------------
// Default hash/equals functions used by ResourceHashtable

template<typename K> unsigned primitive_hash(const K& k) {
  unsigned hash = (unsigned)((uintptr_t)k);
  return hash ^ (hash >> 3); // just in case we're dealing with aligned ptrs
}

template<typename K> bool primitive_equals(const K& k0, const K& k1) {
  return k0 == k1;
}

template<typename K> int primitive_compare(const K& k0, const K& k1) {
  return ((k0 < k1) ? -1 : (k0 == k1) ? 0 : 1);
}

//----------------------------------------------------------------------------------------------------

// Allow use of C++ thread_local when approved - see JDK-8282469.
#define APPROVED_CPP_THREAD_LOCAL thread_local

// Converts any type T to a reference type.
template<typename T>
std::add_rvalue_reference_t<T> declval() noexcept;

// Quickly test to make sure IEEE-754 subnormal numbers are correctly
// handled.
bool IEEE_subnormal_handling_OK();
#endif // !NATIVE_IMAGE


} // namespace svm_container

#endif // SHARE_UTILITIES_GLOBALDEFINITIONS_HPP
