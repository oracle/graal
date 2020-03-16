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

#ifndef OS_H
#define OS_H

#include <jni.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
# include "jvm_windows.h"
#else
# include "jvm_posix.h"
#endif

// Additional Java basic types

typedef uint8_t  jubyte;
typedef uint16_t jushort;
typedef uint32_t juint;
typedef uint64_t julong;

// Platform-independent error return values from OS functions
enum OSReturn {
  OS_OK         =  0,        // Operation was successful
  OS_ERR        = -1,        // Operation failed
  OS_INTRPT     = -2,        // Operation was interrupted
  OS_TIMEOUT    = -3,        // Operation timed out
  OS_NOMEM      = -5,        // Operation failed for lack of memory
  OS_NORESOURCE = -6         // Operation failed for lack of nonmemory resource
};


// File I/O operations  
int os_open(const char *path, int oflag, int mode);  
int os_close(int fd);

int os_vsnprintf(char* buf, size_t len, const char* fmt, va_list args);

size_t os_lasterror(char *buf, size_t len);

char * os_native_path(char *path);

// Socket interface
int os_socket(int domain, int type, int protocol);
int os_socket_close(int fd);
int os_socket_shutdown(int fd, int howto);
int os_recv(int fd, char* buf, size_t nBytes, uint flags);
int os_send(int fd, char* buf, size_t nBytes, uint flags);  
int os_timeout(int fd, long timeout);
int os_listen(int fd, int count);
int os_connect(int fd, struct sockaddr* him, socklen_t len);
int os_bind(int fd, struct sockaddr* him, socklen_t len);
int os_accept(int fd, struct sockaddr* him, socklen_t* len);
int os_recvfrom(int fd, char* buf, size_t nbytes, uint flags,
                      struct sockaddr* from, socklen_t* fromlen);
int os_get_sock_name(int fd, struct sockaddr* him, socklen_t* len);
int os_sendto(int fd, char* buf, size_t len, uint flags,
                    struct sockaddr* to, socklen_t tolen);
int os_socket_available(int fd, jint* pbytes);

int os_get_sock_opt(int fd, int level, int optname,
                          char* optval, socklen_t* optlen);
int os_set_sock_opt(int fd, int level, int optname,
                          const char* optval, socklen_t optlen);
int os_get_host_name(char* name, int namelen);

#endif // OS_H
