/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
  Taken from `src/hotspot/os/windows/os_windows.cpp`. Apart from adapting C++ to C,
  the main difference is that the handling of the `-Xrs` option has been removed.

  For ease of cross-referencing, function names are kept unchanged, and in addition,
  member functions are prefixed with the class name followed by a double underscore.
*/

#include <signal.h>

#define ARRAY_SIZE(array) (sizeof(array)/sizeof((array)[0]))

static int os__get_signal_number(const char* name) {
  static const struct {
    char* name;
    int   number;
  } siglabels [] =
    // derived from version 6.0 VC98/include/signal.h
  {"ABRT",      SIGABRT,        // abnormal termination triggered by abort cl
  "FPE",        SIGFPE,         // floating point exception
  "SEGV",       SIGSEGV,        // segment violation
  "INT",        SIGINT,         // interrupt
  "TERM",       SIGTERM,        // software term signal from kill
  "BREAK",      SIGBREAK,       // Ctrl-Break sequence
  "ILL",        SIGILL};        // illegal instruction
  unsigned i;
  for (i = 0; i < ARRAY_SIZE(siglabels); ++i) {
    if (strcmp(name, siglabels[i].name) == 0) {
      return siglabels[i].number;
    }
  }
  return -1;
}

static void os__signal_raise(int signal_number) {
  raise(signal_number);
}

// a counter for each possible signal value, including signal_thread exit signal
static volatile jint pending_signals[NSIG+1] = { 0 };
static HANDLE sig_sem = NULL;

static void Atomic__inc(volatile jint *dest) {
  InterlockedIncrement(dest);
}

static jint Atomic__cmpxchg(jint exchange_value, volatile jint *dest, jint compare_value) {
  return InterlockedCompareExchange(dest, exchange_value, compare_value);
}

static jboolean sig_sem__init() {
  sig_sem = CreateSemaphore(NULL, 0, LONG_MAX, NULL);
  return sig_sem != NULL;
}

#define assert(p, ...)

static void sig_sem__signal() {
  BOOL ret = ReleaseSemaphore(sig_sem, 1, NULL);
  assert(ret != 0, "ReleaseSemaphore failed with error code: %lu", GetLastError());
}

static void sig_sem__wait() {
  DWORD ret = WaitForSingleObject(sig_sem, INFINITE);
  assert(ret != WAIT_FAILED,   "WaitForSingleObject failed with error code: %lu", GetLastError());
  assert(ret == WAIT_OBJECT_0, "WaitForSingleObject failed with return value: %lu", ret);
}

static void os__signal_notify(int sig) {
  Atomic__inc(&pending_signals[sig]);
  sig_sem__signal();
}

// sun.misc.Signal
// NOTE that this is a workaround for an apparent kernel bug where if
// a signal handler for SIGBREAK is installed then that signal handler
// takes priority over the console control handler for CTRL_CLOSE_EVENT.
// See bug 4416763.
static void (*sigbreakHandler)(int) = NULL;

static void* os__signal(int signal_number, void* handler) {
  if (signal_number == SIGBREAK) {
    void (*oldHandler)(int) = sigbreakHandler;
    sigbreakHandler = (void (*)(int)) handler;
    return (void*) oldHandler;
  } else {
    return (void*)signal(signal_number, (void (*)(int))handler);
  }
}

static void UserHandler(int sig, void *siginfo, void *context) {
  os__signal_notify(sig);
  // We need to reinstate the signal handler each time...
  os__signal(sig, (void*)UserHandler);
}

static void* os__user_handler() {
  return (void*) UserHandler;
}

// The Win32 C runtime library maps all console control events other than ^C
// into SIGBREAK, which makes it impossible to distinguish ^BREAK from close,
// logoff, and shutdown events.  We therefore install our own console handler
// that raises SIGTERM for the latter cases.
//
static BOOL WINAPI consoleHandler(DWORD event) {
  switch (event) {
  case CTRL_C_EVENT:
    os__signal_raise(SIGINT);
    return TRUE;
    break;
  case CTRL_BREAK_EVENT:
    if (sigbreakHandler != NULL) {
      (*sigbreakHandler)(SIGBREAK);
    }
    return TRUE;
    break;
  case CTRL_LOGOFF_EVENT: {
    #pragma comment(lib, "user32")
    // Don't terminate JVM if it is running in a non-interactive session,
    // such as a service process.
    USEROBJECTFLAGS flags;
    HANDLE handle = GetProcessWindowStation();
    if (handle != NULL &&
        GetUserObjectInformation(handle, UOI_FLAGS, &flags,
        sizeof(USEROBJECTFLAGS), NULL)) {
      // If it is a non-interactive session, let next handler to deal
      // with it.
      if ((flags.dwFlags & WSF_VISIBLE) == 0) {
        return FALSE;
      }
    }
  }
  case CTRL_CLOSE_EVENT:
  case CTRL_SHUTDOWN_EVENT:
    os__signal_raise(SIGTERM);
    return TRUE;
    break;
  default:
    break;
  }
  return FALSE;
}

jboolean jdk_misc_signal_init() {
  // Initialize signal structures
  memset((void*)pending_signals, 0, sizeof(pending_signals));

  // Initialize signal semaphore
  if (!sig_sem__init()) {
    return JNI_FALSE;
  }

  // Add a CTRL-C handler
  if (!SetConsoleCtrlHandler(consoleHandler, TRUE)) {
    return JNI_FALSE;
  }

  return JNI_TRUE;
}

static int check_pending_signals() {
  for (;;) {
    int i;
    for (i = 0; i < NSIG + 1; i++) {
      jint n = pending_signals[i];
      if (n > 0 && n == Atomic__cmpxchg(n - 1, &pending_signals[i], n)) {
        return i;
      }
    }
    sig_sem__wait();
  }
}

int os__signal_wait() {
  return check_pending_signals();
}

// Return maximum OS signal used + 1 for internal use only
// Used as exit signal for signal_thread
int os__sigexitnum_pd() {
  return NSIG;
}

void os__terminate_signal_thread() {
  os__signal_notify(os__sigexitnum_pd());
}
