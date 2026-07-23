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

#include "memory/allocation.inline.hpp"
#include "runtime/os.inline.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/permitForbiddenFunctions.hpp"
int               os::_processor_count    = 0;

int os::snprintf_checked(char* buf, size_t len, const char* fmt, ...) {
  va_list args;
  va_start(args, fmt);
  int result = os::vsnprintf(buf, len, fmt, args);
  va_end(args);
  assert(result >= 0, "os::snprintf error");
  assert(static_cast<size_t>(result) < len, "os::snprintf truncated");
  return result;
}

int os::vsnprintf(char* buf, size_t len, const char* fmt, va_list args) {
  int result = permit_forbidden_function::vsnprintf(buf, len, fmt, args);
  // If an encoding error occurred (result < 0) then it's not clear
  // whether the buffer is NUL terminated, so ensure it is.
  if ((result < 0) && (len > 0)) {
    buf[len - 1] = '\0';
  }
  return result;
}


// --------------------- heap allocation utilities ---------------------

char *os::strdup(const char *str, MemTag mem_tag) {
  size_t size = strlen(str);
  char *dup_str = (char *)malloc(size + 1, mem_tag);
  if (dup_str == nullptr) return nullptr;
  memcpy(dup_str, str, size + 1);
  return dup_str;
}


void* os::malloc(size_t size, MemTag mem_tag) {
  // On malloc(0), implementations of malloc(3) have the choice to return either
  // null or a unique non-null pointer. To unify libc behavior across our platforms
  // we chose the latter.
  size = MAX2((size_t)1, size);
  return ::malloc(size);
}

void* os::realloc(void *memblock, size_t size, MemTag mem_tag) {
  if (memblock == nullptr) {
    return os::malloc(size, mem_tag);
  }

  // On realloc(p, 0), implementers of realloc(3) have the choice to return either
  // null or a unique non-null pointer. To unify libc behavior across our platforms
  // we chose the latter.
  size = MAX2((size_t)1, size);
  return ::realloc(memblock, size);
}

void  os::free(void *memblock) {
  if (memblock == nullptr) {
    return;
  }
  ::free(memblock);
}

// This function is a proxy to fopen, it tries to add a non standard flag ('e' or 'N')
// that ensures automatic closing of the file on exec. If it can not find support in
// the underlying c library, it will make an extra system call (fcntl) to ensure automatic
// closing of the file on exec.
FILE* os::fopen(const char* path, const char* mode) {
  char modified_mode[20];
  assert(strlen(mode) + 1 < sizeof(modified_mode), "mode chars plus one extra must fit in buffer");
  os::snprintf_checked(modified_mode, sizeof(modified_mode), "%s" LINUX_ONLY("e") BSD_ONLY("e") WINDOWS_ONLY("N"), mode);
  FILE* file = ::fopen(path, modified_mode);

#if !(defined LINUX || defined BSD || defined _WINDOWS)
  // assume fcntl FD_CLOEXEC support as a backup solution when 'e' or 'N'
  // is not supported as mode in fopen
  if (file != nullptr) {
    int fd = fileno(file);
    if (fd != -1) {
      int fd_flags = fcntl(fd, F_GETFD);
      if (fd_flags != -1) {
        fcntl(fd, F_SETFD, fd_flags | FD_CLOEXEC);
      }
    }
  }
#endif

  return file;
}

bool os::file_exists(const char* filename) {
  struct stat statbuf;
  if (filename == nullptr || strlen(filename) == 0) {
    return false;
  }
  return os::stat(filename, &statbuf) == 0;
}

static const char* errno_to_string (int e, bool short_text) {
  #define ALL_SHARED_ENUMS(X) \
    X(E2BIG, "Argument list too long") \
    X(EACCES, "Permission denied") \
    X(EADDRINUSE, "Address in use") \
    X(EADDRNOTAVAIL, "Address not available") \
    X(EAFNOSUPPORT, "Address family not supported") \
    X(EAGAIN, "Resource unavailable, try again") \
    X(EALREADY, "Connection already in progress") \
    X(EBADF, "Bad file descriptor") \
    X(EBADMSG, "Bad message") \
    X(EBUSY, "Device or resource busy") \
    X(ECANCELED, "Operation canceled") \
    X(ECHILD, "No child processes") \
    X(ECONNABORTED, "Connection aborted") \
    X(ECONNREFUSED, "Connection refused") \
    X(ECONNRESET, "Connection reset") \
    X(EDEADLK, "Resource deadlock would occur") \
    X(EDESTADDRREQ, "Destination address required") \
    X(EDOM, "Mathematics argument out of domain of function") \
    X(EEXIST, "File exists") \
    X(EFAULT, "Bad address") \
    X(EFBIG, "File too large") \
    X(EHOSTUNREACH, "Host is unreachable") \
    X(EIDRM, "Identifier removed") \
    X(EILSEQ, "Illegal byte sequence") \
    X(EINPROGRESS, "Operation in progress") \
    X(EINTR, "Interrupted function") \
    X(EINVAL, "Invalid argument") \
    X(EIO, "I/O error") \
    X(EISCONN, "Socket is connected") \
    X(EISDIR, "Is a directory") \
    X(ELOOP, "Too many levels of symbolic links") \
    X(EMFILE, "Too many open files") \
    X(EMLINK, "Too many links") \
    X(EMSGSIZE, "Message too large") \
    X(ENAMETOOLONG, "Filename too long") \
    X(ENETDOWN, "Network is down") \
    X(ENETRESET, "Connection aborted by network") \
    X(ENETUNREACH, "Network unreachable") \
    X(ENFILE, "Too many files open in system") \
    X(ENOBUFS, "No buffer space available") \
    X(ENODATA, "No message is available on the STREAM head read queue") \
    X(ENODEV, "No such device") \
    X(ENOENT, "No such file or directory") \
    X(ENOEXEC, "Executable file format error") \
    X(ENOLCK, "No locks available") \
    X(ENOLINK, "Reserved") \
    X(ENOMEM, "Not enough space") \
    X(ENOMSG, "No message of the desired type") \
    X(ENOPROTOOPT, "Protocol not available") \
    X(ENOSPC, "No space left on device") \
    X(ENOSR, "No STREAM resources") \
    X(ENOSTR, "Not a STREAM") \
    X(ENOSYS, "Function not supported") \
    X(ENOTCONN, "The socket is not connected") \
    X(ENOTDIR, "Not a directory") \
    X(ENOTEMPTY, "Directory not empty") \
    X(ENOTSOCK, "Not a socket") \
    X(ENOTSUP, "Not supported") \
    X(ENOTTY, "Inappropriate I/O control operation") \
    X(ENXIO, "No such device or address") \
    X(EOPNOTSUPP, "Operation not supported on socket") \
    X(EOVERFLOW, "Value too large to be stored in data type") \
    X(EPERM, "Operation not permitted") \
    X(EPIPE, "Broken pipe") \
    X(EPROTO, "Protocol error") \
    X(EPROTONOSUPPORT, "Protocol not supported") \
    X(EPROTOTYPE, "Protocol wrong type for socket") \
    X(ERANGE, "Result too large") \
    X(EROFS, "Read-only file system") \
    X(ESPIPE, "Invalid seek") \
    X(ESRCH, "No such process") \
    X(ETIME, "Stream ioctl() timeout") \
    X(ETIMEDOUT, "Connection timed out") \
    X(ETXTBSY, "Text file busy") \
    X(EWOULDBLOCK, "Operation would block") \
    X(EXDEV, "Cross-device link")

  #define DEFINE_ENTRY(e, text) { e, #e, text },

  static const struct {
    int v;
    const char* short_text;
    const char* long_text;
  } table [] = {

    ALL_SHARED_ENUMS(DEFINE_ENTRY)

    // The following enums are not defined on all platforms.
    #ifdef ESTALE
    DEFINE_ENTRY(ESTALE, "Reserved")
    #endif
    #ifdef EDQUOT
    DEFINE_ENTRY(EDQUOT, "Reserved")
    #endif
    #ifdef EMULTIHOP
    DEFINE_ENTRY(EMULTIHOP, "Reserved")
    #endif

    // End marker.
    { -1, "Unknown errno", "Unknown error" }

  };

  #undef DEFINE_ENTRY
  #undef ALL_FLAGS

  int i = 0;
  while (table[i].v != -1 && table[i].v != e) {
    i ++;
  }

  return short_text ? table[i].short_text : table[i].long_text;

}

const char* os::strerror(int e) {
  return errno_to_string(e, false);
}

const char* os::errno_name(int e) {
  return errno_to_string(e, true);
}

