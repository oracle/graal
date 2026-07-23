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

#ifndef SHARE_RUNTIME_OS_HPP
#define SHARE_RUNTIME_OS_HPP

#include "jvm_md.h"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// Preserve errno across a range of calls

class ErrnoPreserver {
  int _e;

public:
  ErrnoPreserver() { _e = errno; }

  ~ErrnoPreserver() { errno = _e; }

  int saved_errno() { return _e; }
};

class os: AllStatic {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class MallocTracker;


 public:

  static julong physical_memory();

  // number of CPUs
  static int processor_count() {
    return _processor_count;
  }
  static void set_processor_count(int count) { _processor_count = count; }

  static FILE* fopen(const char* path, const char* mode);
  static bool file_exists(const char* file);


  // Provide wrapper versions of these functions to guarantee NUL-termination
  // in all cases.
  static int vsnprintf(char* buf, size_t len, const char* fmt, va_list args) ATTRIBUTE_PRINTF(3, 0);

  // Performs snprintf and asserts the result is non-negative (so there was not
  // an encoding error) and that the output was not truncated.
  static int snprintf_checked(char* buf, size_t len, const char* fmt, ...) ATTRIBUTE_PRINTF(3, 4);


  // Replacement for strerror().
  // Will return the english description of the error (e.g. "File not found", as
  //  suggested in the POSIX standard.
  // Will return "Unknown error" for an unknown errno value.
  // Will not attempt to localize the returned string.
  // Will always return a valid string which is a static constant.
  // Will not change the value of errno.
  static const char* strerror(int e);

  // Will return the literalized version of the given errno (e.g. "EINVAL"
  //  for EINVAL).
  // Will return "Unknown error" for an unknown errno value.
  // Will always return a valid string which is a static constant.
  // Will not change the value of errno.
  static const char* errno_name(int e);

  // IO operations, non-JVM_ version.
  static int stat(const char* path, struct stat* sbuf);

  // General allocation (must be MT-safe)
  static void* malloc  (size_t size, MemTag mem_tag);
  static void* realloc (void *memblock, size_t size, MemTag mem_tag);

  // handles null pointers
  static void  free    (void *memblock);
  static char* strdup(const char *, MemTag mem_tag = mtInternal);  // Like strdup

  // Platform-specific code for interacting with individual OSes.
  // TODO: This is for compatibility only with current usage of os::Linux, etc.
  // We can get rid of the following block if we rename such a class to something
  // like ::LinuxUtils
#if defined(AIX)
  class Aix;
#elif defined(BSD)
  class Bsd;
#elif defined(LINUX)
  class Linux;
#elif defined(_WINDOWS)
  class win32;
#endif

  // Ditto - Posix-specific API. Ideally should be moved to something like ::PosixUtils.
#ifndef _WINDOWS
  class Posix;
#endif


 protected:
  static int _processor_count;                // number of processors
};


#endif // SHARE_RUNTIME_OS_HPP
