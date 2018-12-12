/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

/* JvmFuncs is currently only used on Windows */

#ifdef _WIN64

#include <stdio.h>
#include <windows.h>

#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)

JNIEXPORT initialize() {
}

/* Only called in java.lang.Runtime native methods. */
JNIEXPORT JVM_FreeMemory() {
    printf("JVM_FreeMemory called:  Unimplemented\n");
}

JNIEXPORT JVM_TotalMemory() {
    printf("JVM_TotalMemory called:  Unimplemented\n");
}


JNIEXPORT JVM_MaxMemory() {
    printf("JVM_MaxMemory called:  Unimplemented\n");
}

JNIEXPORT JVM_GC() {
    printf("JVM_GC called:  Unimplemented\n");
}

JNIEXPORT JVM_TraceInstructions() {
    printf("JVM_TraceInstructions called:  Unimplemented\n");
}

JNIEXPORT JVM_TraceMethodCalls() {
    printf("JVM_TraceMethods called:  Unimplemented\n");
}

JNIEXPORT JVM_ActiveProcessorCount() {
    printf("JVM_ActiveProcessorCount called:  Unimplemented\n");
}


HANDLE interrupt_event = NULL;

JNIEXPORT HANDLE JVM_GetThreadInterruptEvent() {
    if (interrupt_event != NULL) {
        return interrupt_event;
    }
    interrupt_event = CreateEvent(NULL, TRUE, FALSE, NULL);
    return interrupt_event;
}


/* Called directly from several native functions */
JNIEXPORT int JVM_InitializeSocketLibrary() { 
    /* A noop, returns 0 in hotspot */
   return 0;
}

JNIEXPORT JVM_CurrentTimeMillis() {
    printf("JVM_CurrentTimeMillis called:  Unimplemented\n");
}

JNIEXPORT JVM_GetLastErrorString() {
    printf("JVM_GetLastErrorString called:  Unimplemented\n");
}

int jio_vsnprintf(char *str, size_t count, const char *fmt, va_list args) {
  int result;

  if ((intptr_t)count <= 0) return -1;

  result = vsnprintf(str, count, fmt, args);
  if ((result > 0 && (size_t)result >= count) || result == -1) {
    str[count - 1] = '\0';
    result = -1;
  }

  return result;
}

int jio_snprintf(char *str, size_t count, const char *fmt, ...) {
  va_list args;
  int len;
  va_start(args, fmt);
  len = jio_vsnprintf(str, count, fmt, args);
  va_end(args);
  return len;
}

int jio_fprintf(FILE* f, const char *fmt, ...) {
  int len;
  va_list args;
  va_start(args, fmt);
  len = jio_vfprintf(f, fmt, args);
  va_end(args);
  return len;
}

int jio_vfprintf(FILE* f, const char *fmt, va_list args) {
  return vfprintf(f, fmt, args);
}

#endif
