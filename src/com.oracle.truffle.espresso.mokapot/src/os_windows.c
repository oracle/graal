/*
 * Copyright (c) 1997, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

#if defined(_WIN32)

#include "os.h"
#include <windows.h>
#include <sys/types.h>
#include <sys/stat.h>

#include <malloc.h>
#include <errno.h>
#include <fcntl.h>
#include <io.h>

// macros for restartable system calls

#define RESTARTABLE_RETURN_INT(_cmd) do { \
  int _result; \
  RESTARTABLE(_cmd, _result); \
  return _result; \
} while(0)

/* Convert a pathname to native format.  On win32, this involves forcing all
   separators to be '\\' rather than '/' (both are legal inputs, but Win95
   sometimes rejects '/') and removing redundant separators.  The input path is
   assumed to have been converted into the character encoding used by the local
   system.  Because this might be a double-byte encoding, care is taken to
   treat double-byte lead characters correctly.

   This procedure modifies the given path in place, as the result is never
   longer than the original.  There is no error return; this operation always
   succeeds. */
char * os_native_path(char *path) {
  char *src = path, *dst = path, *end = path;
  char *colon = NULL;           /* If a drive specifier is found, this will
                                        point to the colon following the drive
                                        letter */

  /* Assumption: '/', '\\', ':', and drive letters are never lead bytes */
  assert(((!::IsDBCSLeadByte('/'))
    && (!::IsDBCSLeadByte('\\'))
    && (!::IsDBCSLeadByte(':'))),
    "Illegal lead byte");

  /* Check for leading separators */
#define isfilesep(c) ((c) == '/' || (c) == '\\')
  while (isfilesep(*src)) {
    src++;
  }

  if (::isalpha(*src) && !::IsDBCSLeadByte(*src) && src[1] == ':') {
    /* Remove leading separators if followed by drive specifier.  This
      hack is necessary to support file URLs containing drive
      specifiers (e.g., "file://c:/path").  As a side effect,
      "/c:/path" can be used as an alternative to "c:/path". */
    *dst++ = *src++;
    colon = dst;
    *dst++ = ':';
    src++;
  } else {
    src = path;
    if (isfilesep(src[0]) && isfilesep(src[1])) {
      /* UNC pathname: Retain first separator; leave src pointed at
         second separator so that further separators will be collapsed
         into the second separator.  The result will be a pathname
         beginning with "\\\\" followed (most likely) by a host name. */
      src = dst = path + 1;
      path[0] = '\\';     /* Force first separator to '\\' */
    }
  }

  end = dst;

  /* Remove redundant separators from remainder of path, forcing all
      separators to be '\\' rather than '/'. Also, single byte space
      characters are removed from the end of the path because those
      are not legal ending characters on this operating system.
  */
  while (*src != '\0') {
    if (isfilesep(*src)) {
      *dst++ = '\\'; src++;
      while (isfilesep(*src)) src++;
      if (*src == '\0') {
        /* Check for trailing separator */
        end = dst;
        if (colon == dst - 2) break;                      /* "z:\\" */
        if (dst == path + 1) break;                       /* "\\" */
        if (dst == path + 2 && isfilesep(path[0])) {
          /* "\\\\" is not collapsed to "\\" because "\\\\" marks the
            beginning of a UNC pathname.  Even though it is not, by
            itself, a valid UNC pathname, we leave it as is in order
            to be consistent with the path canonicalizer as well
            as the win32 APIs, which treat this case as an invalid
            UNC pathname rather than as an alias for the root
            directory of the current drive. */
          break;
        }
        end = --dst;  /* Path does not denote a root directory, so
                                    remove trailing separator */
        break;
      }
      end = dst;
    } else {
      if (::IsDBCSLeadByte(*src)) { /* Copy a double-byte character */
        *dst++ = *src++;
        if (*src) *dst++ = *src++;
        end = dst;
      } else {         /* Copy a single-byte character */
        char c = *src++;
        *dst++ = c;
        /* Space is not a legal ending character */
        if (c != ' ') end = dst;
      }
    }
  }

  *end = '\0';

  /* For "z:", add "." to work around a bug in the C runtime library */
  if (colon == dst - 1) {
          path[2] = '.';
          path[3] = '\0';
  }

  return path;
}


int os_open(const char *path, int oflag, int mode) {
  char pathbuf[MAX_PATH];

  if (strlen(path) > MAX_PATH - 1) {
    errno = ENAMETOOLONG;
          return -1;
  }
  os_native_path(strcpy(pathbuf, path));
  return ::open(pathbuf, oflag | O_BINARY | O_NOINHERIT, mode);
}

int os_close(int fd) {
    return close(fd);
}

int os_vsnprintf(char* buf, size_t len, const char* fmt, va_list args) ATTRIBUTE_PRINTF(3, 0);
int os_snprintf(char* buf, size_t len, const char* fmt, ...) ATTRIBUTE_PRINTF(3, 4);

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

int os_socket_close(int fd) {
  return closesocket(fd);
}

int os_socket_available(int fd, jint *pbytes) {
  int ret = ioctlsocket(fd, FIONREAD, (u_long*)pbytes);
  return (ret < 0) ? 0 : 1;
}

int os_socket(int domain, int type, int protocol) {
  return socket(domain, type, protocol);
}

int os_listen(int fd, int count) {
  return listen(fd, count);
}

int os_connect(int fd, struct sockaddr* him, socklen_t len) {
  return connect(fd, him, len);
}

int os_accept(int fd, struct sockaddr* him, socklen_t* len) {
  return accept(fd, him, len);
}

int os_sendto(int fd, char* buf, size_t len, uint flags,
               struct sockaddr* to, socklen_t tolen) {

  return sendto(fd, buf, (int)len, flags, to, tolen);
}

int os_recvfrom(int fd, char *buf, size_t nBytes, uint flags,
                 sockaddr* from, socklen_t* fromlen) {

  return recvfrom(fd, buf, (int)nBytes, flags, from, fromlen);
}

int os_recv(int fd, char* buf, size_t nBytes, uint flags) {
  return recv(fd, buf, (int)nBytes, flags);
}

int os_send(int fd, char* buf, size_t nBytes, uint flags) {
  return send(fd, buf, (int)nBytes, flags);
}

int os_raw_send(int fd, char* buf, size_t nBytes, uint flags) {
  return send(fd, buf, (int)nBytes, flags);
}

int os_timeout(int fd, long timeout) {
  fd_set tbl;
  struct timeval t;

  t.tv_sec  = timeout / 1000;
  t.tv_usec = (timeout % 1000) * 1000;

  tbl.fd_count    = 1;
  tbl.fd_array[0] = fd;

  return select(1, &tbl, 0, 0, &t);
}

int os_get_host_name(char* name, int namelen) {
  return gethostname(name, namelen);
}

int os_socket_shutdown(int fd, int howto) {
  return shutdown(fd, howto);
}

int os_bind(int fd, struct sockaddr* him, socklen_t len) {
  return bind(fd, him, len);
}

int os_get_sock_name(int fd, struct sockaddr* him, socklen_t* len) {
  return getsockname(fd, him, len);
}

int os_get_sock_opt(int fd, int level, int optname,
                     char* optval, socklen_t* optlen) {
  return getsockopt(fd, level, optname, optval, optlen);
}

int os_set_sock_opt(int fd, int level, int optname,
                     const char* optval, socklen_t optlen) {
  return setsockopt(fd, level, optname, optval, optlen);
}

#endif // defined(_WIN32)