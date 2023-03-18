/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
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

#include <errno.h> 
#include <fcntl.h>
#include <signal.h>
#include <string.h>
#include <unistd.h>
#include <sys/stat.h>

#define MAX_PATH 2048

int iohelper_open_file(const char *path, int oflag, int mode) {
  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }

  // All file descriptors that are opened in the Java process and not
  // specifically destined for a subprocess should have the close-on-exec
  // flag set.  If we don't set it, then careless 3rd party native code
  // might fork and exec without closing all appropriate file descriptors
  // (e.g. as we do in closeDescriptors in UNIXProcess.c), and this in
  // turn might:
  //
  // - cause end-of-file to fail to be detected on some file
  //   descriptors, resulting in mysterious hangs, or
  //
  // - might cause an fopen in the subprocess to fail on a system
  //   suffering from bug 1085341.
  //
  // (Yes, the default setting of the close-on-exec flag is a Unix
  // design flaw)
  //
  // See:
  // 1085341: 32-bit stdio routines should support file descriptors >255
  // 4843136: (process) pipe file descriptor from Runtime.exec not being closed
  // 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
  //
  // Modern Linux kernels (after 2.6.23 2007) support O_CLOEXEC with open().
  // O_CLOEXEC is preferable to using FD_CLOEXEC on an open file descriptor
  // because it saves a system call and removes a small window where the flag
  // is unset.  On ancient Linux kernels the O_CLOEXEC flag will be ignored
  // and we fall back to using FD_CLOEXEC (see below).
#ifdef O_CLOEXEC
  oflag |= O_CLOEXEC;
#endif

  int fd = open64(path, oflag, mode);
  if (fd == -1) return -1;

  //If the open succeeded, the file might still be a directory
  {
    struct stat64 buf64;
    int ret = fstat64(fd, &buf64);
    int st_mode = buf64.st_mode;

    if (ret != -1) {
      if ((st_mode & S_IFMT) == S_IFDIR) {
        errno = EISDIR;
        close(fd);
        return -1;
      }
    } else {
      close(fd);
      return -1;
    }
  }

#ifdef FD_CLOEXEC
  // Validate that the use of the O_CLOEXEC flag on open above worked.
  // With recent kernels, we will perform this check exactly once.
  static sig_atomic_t O_CLOEXEC_is_known_to_work = 0;
  if (!O_CLOEXEC_is_known_to_work) {
    int flags = fcntl(fd, F_GETFD);
    if (flags != -1) {
      if ((flags & FD_CLOEXEC) != 0)
        O_CLOEXEC_is_known_to_work = 1;
      else
        fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
    }
  }
#endif

  return fd;
}

