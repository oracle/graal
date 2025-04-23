/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef _WIN64

#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <unistd.h>

/*
 * This file is based on HotSpot C++ code. As we need a mix of Java and C code to replicate the
 * HotSpot logic, only the callers on the Java-side are annotated with @BasedOnJDKFile.
 */

#ifndef UNIX_PATH_MAX
#define UNIX_PATH_MAX   sizeof(((struct sockaddr_un *)0)->sun_path)
#endif

#define ROOT_UID 0

#define RESTARTABLE(_cmd, _result) do { \
    _result = _cmd; \
  } while(((int)_result == -1) && (errno == EINTR))

bool svm_is_root(uid_t uid){
    return ROOT_UID == uid;
}

bool svm_matches_effective_uid_or_root(uid_t uid) {
    return svm_is_root(uid) || geteuid() == uid;
}

bool svm_matches_effective_uid_and_gid_or_root(uid_t uid, gid_t gid) {
    return svm_is_root(uid) || (geteuid() == uid && getegid() == gid);
}

void svm_attach_startup(char* fn) {
  struct stat st;
  int ret;
  RESTARTABLE(stat(fn, &st), ret);
  if (ret == 0) {
    unlink(fn);
  }
}

void svm_attach_listener_cleanup(int s, char* path) {
  if (s != -1) {
    shutdown(s, SHUT_RDWR);
    close(s);
  }
  if (path != NULL) {
    unlink(path);
  }
}

/* Returns true if the socket file is valid. */
bool svm_attach_check_socket_file(char* path) {
  int ret;
  struct stat st;
  ret = stat(path, &st);
  if (ret == -1) { // need to restart attach listener.
    return false;
  }
  return true;
}

bool svm_attach_is_init_trigger(char* fn) {
  int ret;
  struct stat st;
  RESTARTABLE(stat(fn, &st), ret);
  if (ret == 0) {
    // simple check to avoid starting the attach mechanism when
    // a bogus non-root user creates the file
    if (svm_matches_effective_uid_or_root(st.st_uid)) {
      return true;
    }
  }
  return false;
}

int svm_attach_create_listener(char* path) {
  char initial_path[UNIX_PATH_MAX];  // socket file during setup
  int listener;                      // listener socket (file descriptor)

  int n = snprintf(initial_path, UNIX_PATH_MAX, "%s.tmp", path);
  if (n >= (int)UNIX_PATH_MAX) {
    return -1;
  }

  // create the listener socket
  listener = socket(PF_UNIX, SOCK_STREAM, 0);
  if (listener == -1) {
    return -1;
  }

  // bind socket
  struct sockaddr_un addr;
  memset((void *)&addr, 0, sizeof(addr));
  addr.sun_family = AF_UNIX;
  strncpy(addr.sun_path, initial_path, UNIX_PATH_MAX);
  unlink(initial_path);
  int res = bind(listener, (struct sockaddr*)&addr, sizeof(addr));
  if (res == -1) {
    close(listener);
    return -1;
  }

  // put in listen mode, set permissions, and rename into place
  res = listen(listener, 5);
  if (res == 0) {
    RESTARTABLE(chmod(initial_path, S_IREAD|S_IWRITE), res);
    if (res == 0) {
      // make sure the file is owned by the effective user and effective group
      // e.g. the group could be inherited from the directory in case the s bit
      // is set. The default behavior on mac is that new files inherit the group
      // of the directory that they are created in.
      RESTARTABLE(chown(initial_path, geteuid(), getegid()), res);
      if (res == 0) {
        res = rename(initial_path, path);
      }
    }
  }
  if (res == -1) {
    close(listener);
    unlink(initial_path);
    return -1;
  }

  return listener;
}

int svm_attach_wait_for_request(int listener) {
  for (;;) {
    int s;

    // wait for client to connect
    struct sockaddr addr;
    socklen_t len = sizeof(addr);
    RESTARTABLE(accept(listener, &addr, &len), s);
    if (s == -1) {
      return -1;      // log a warning?
    }

    // get the credentials of the peer and check the effective uid/guid
#ifdef LINUX
    struct ucred cred_info;
    socklen_t optlen = sizeof(cred_info);
    if (getsockopt(s, SOL_SOCKET, SO_PEERCRED, (void *)&cred_info, &optlen) ==
        -1) {
      close(s);
      continue;
    }

    if (!svm_matches_effective_uid_and_gid_or_root(cred_info.uid,
                                                          cred_info.gid)) {
      close(s);
      continue;
    }
#endif
#ifdef BSD
    uid_t puid;
    gid_t pgid;
    if (getpeereid(s, &puid, &pgid) != 0) {
      close(s);
      continue;
    }

    if (!svm_matches_effective_uid_and_gid_or_root(puid, pgid)) {
      close(s);
      continue;
    }
#endif

    return s;
  }
}

void svm_attach_shutdown_socket(int s) {
  shutdown(s, SHUT_RDWR);
}

#endif // !_WIN64

