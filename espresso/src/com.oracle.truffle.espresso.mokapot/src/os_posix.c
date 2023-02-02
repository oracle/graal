/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

#if !defined(_WIN32)

#include "os.h"
#include "mokapot.h"
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <sys/poll.h>
#include <sys/time.h>
#include <dlfcn.h>
#include <stdatomic.h>

// macros for restartable system calls

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(0)

char * os_native_path(char *path) {
    return path;
}

int os_open(const char *path, int oflag, int mode) {
    if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
    return -1;
  }
  int fd;
  int o_delete = (oflag & O_DELETE);
  oflag = oflag & ~O_DELETE;

  fd = open(path, oflag, mode);
  if (fd == -1) return -1;

  //If the open succeeded, the file might still be a directory
  {
    struct stat buf;
    int ret = fstat(fd, &buf);
    int st_mode = buf.st_mode;

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

    /*
     * All file descriptors that are opened in the JVM and not
     * specifically destined for a subprocess should have the
     * close-on-exec flag set.  If we don't set it, then careless 3rd
     * party native code might fork and exec without closing all
     * appropriate file descriptors (e.g. as we do in closeDescriptors in
     * UNIXProcess.c), and this in turn might:
     *
     * - cause end-of-file to fail to be detected on some file
     *   descriptors, resulting in mysterious hangs, or
     *
     * - might cause an fopen in the subprocess to fail on a system
     *   suffering from bug 1085341.
     *
     * (Yes, the default setting of the close-on-exec flag is a Unix
     * design flaw)
     *
     * See:
     * 1085341: 32-bit stdio routines should support file descriptors >255
     * 4843136: (process) pipe file descriptor from Runtime.exec not being closed
     * 6339493: (process) Runtime.exec does not close all file descriptors on Solaris 9
     */
#ifdef FD_CLOEXEC
    {
        int flags = fcntl(fd, F_GETFD);
        if (flags != -1)
            fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
    }
#endif

  if (o_delete != 0) {
    unlink(path);
  }

  return fd;
}

int os_close(int fd) {
    return close(fd);
}

int os_vsnprintf(char* buf, size_t len, const char* fmt, va_list args) {
  return vsnprintf(buf, len, fmt, args);
}

size_t os_lasterror(char *buf, size_t len) {
  if (errno == 0)  return 0;

  const char *s = strerror(errno);
  size_t n = strlen(s);
  if (n >= len) {
    n = len - 1;
  }
  strncpy(buf, s, n);
  buf[n] = '\0';
  return n;
}

// Socket interface
int os_socket(int domain, int type, int protocol) {
    return socket(domain, type, protocol);
}

int os_socket_close(int fd) {
    return close(fd);
}

int os_socket_shutdown(int fd, int howto) {
    return shutdown(fd, howto);
}

int os_recv(int fd, char* buf, size_t nBytes, uint flags) {    
  RESTARTABLE_RETURN_INT(recv(fd, buf, nBytes, flags));
}

int os_send(int fd, char* buf, size_t nBytes, uint flags) {
  RESTARTABLE_RETURN_INT(send(fd, buf, nBytes, flags));
}

int os_timeout(int fd, long timeout) {
  julong prevtime,newtime;
  struct timeval t;

  gettimeofday(&t, NULL);
  prevtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;

  for(;;) {
    struct pollfd pfd;

    pfd.fd = fd;
    pfd.events = POLLIN | POLLERR;

    int res = poll(&pfd, 1, timeout);

    if (res == OS_ERR && errno == EINTR) {

      // On Linux any value < 0 means "forever"

      if(timeout >= 0) {
        gettimeofday(&t, NULL);
        newtime = ((julong)t.tv_sec * 1000)  +  t.tv_usec / 1000;
        timeout -= newtime - prevtime;
        if(timeout <= 0)
          return OS_OK;
        prevtime = newtime;
      }
    } else
      return res;
  }
}

int os_listen(int fd, int count) {
    return listen(fd, count);
}

int os_connect(int fd, struct sockaddr* him, socklen_t len) {
  RESTARTABLE_RETURN_INT(connect(fd, him, len));
}

int os_bind(int fd, struct sockaddr* him, socklen_t len) {
    return bind(fd, him, len);
}

int os_accept(int fd, struct sockaddr* him, socklen_t* len) {
    return accept(fd, him, len);
}

int os_recvfrom(int fd, char* buf, size_t nBytes, uint flags,
                      struct sockaddr* from, socklen_t* fromlen) {
  RESTARTABLE_RETURN_INT((int)recvfrom(fd, buf, nBytes, flags, from, fromlen));
}

int os_get_sock_name(int fd, struct sockaddr* him, socklen_t* len) {
    return getsockname(fd, him, len);
}

int os_sendto(int fd, char* buf, size_t len, uint flags,
                    struct sockaddr* to, socklen_t tolen) {
  RESTARTABLE_RETURN_INT((int)sendto(fd, buf, len, flags, to, tolen));
}

int os_socket_available(int fd, jint* pbytes) {
  // Linux doc says EINTR not returned, unlike Solaris
  int ret = ioctl(fd, FIONREAD, pbytes);

  //%% note ioctl can return 0 when successful, JVM_SocketAvailable
  // is expected to return 0 on failure and 1 on success to the jdk.
  return (ret < 0) ? 0 : 1;
}

int os_get_sock_opt(int fd, int level, int optname,
                          char* optval, socklen_t* optlen) {
    return getsockopt(fd, level, optname, optval, optlen);
}

int os_set_sock_opt(int fd, int level, int optname,
                          const char* optval, socklen_t optlen) {
    return setsockopt(fd, level, optname, optval, optlen);
}

int os_get_host_name(char* name, int namelen) {
    return gethostname(name, namelen);
}

const char *os_current_library_path() {
    Dl_info info;
    if (dladdr(os_current_library_path, &info) == 0) {
        return NULL;
    }
    return info.dli_fname;
}

OS_DL_HANDLE os_dl_open(const char * path) {
    return dlopen(path, RTLD_LAZY | RTLD_LOCAL);
}

const char *os_dl_error() {
    return dlerror();
}

void *os_dl_sym(OS_DL_HANDLE handle, const char *sym) {
    return dlsym(handle, sym);
}

OS_DL_HANDLE os_get_RTLD_DEFAULT() {
    return RTLD_DEFAULT;
}

OS_DL_HANDLE os_get_ProcessHandle() {
    static void *procHandle = NULL;
    if (procHandle != NULL) {
        return procHandle;
    }
#ifdef __APPLE__
    procHandle = (void*)dlopen(NULL, RTLD_FIRST);
#else
    procHandle = (void*)dlopen(NULL, RTLD_LAZY);
#endif
    return procHandle;
}

void* os_atomic_load_ptr(void* OS_ATOMIC *ptr) {
    return atomic_load(ptr);
}

int os_atomic_compare_exchange_ptr(void* OS_ATOMIC *ptr, void* expected_value, void* new_value) {
    void* expected = expected_value;
    return atomic_compare_exchange_weak(ptr, &expected, new_value);
}

#endif // !defined(_WIN32)
