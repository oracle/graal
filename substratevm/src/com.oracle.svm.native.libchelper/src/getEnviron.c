/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
extern char **environ;

char **getEnviron() {
  return environ;
}

#else

#include <windows.h>

static char **envptr = NULL; 
static char *env = NULL;

char **getEnviron() {
    int i;
    int numStrings, numChars;
    LPSTR  lpszVariable; 
    LPVOID lpvEnv; 
    char **p;

    if (envptr != NULL ) {
        return envptr;
    }

    // Get a pointer to the environment block. 
    lpvEnv = GetEnvironmentStringsA();

    // If the returned pointer is NULL, exit.
    if (lpvEnv == NULL) {
        printf("GetEnvironmentStrings failed (%d)", GetLastError()); 
        return NULL;
    }
 
    // Calculate the size of the buffer needed
    numStrings = 0;
    numChars = 0;
    for (lpszVariable = (LPTSTR)lpvEnv; *lpszVariable; lpszVariable++) { 
       numStrings++;
       while (*lpszVariable) {
           lpszVariable++;
           numChars++;
       }
       numChars++;
    }

    // Duplcate the env strings
    p = envptr = malloc((numStrings+1)*sizeof(char *));
    env = malloc(numChars);
    memcpy(env, lpvEnv, numChars);

    // Create the pointer array
    for (lpszVariable = (LPTSTR)env; *lpszVariable; lpszVariable++) { 
       *p++ = lpszVariable;
       while (*lpszVariable) {
           lpszVariable++;
       }
    }
    *p = NULL;

    FreeEnvironmentStringsA((LPTSTR)lpvEnv);

    return 0;
}

#if _MSC_VER <= 1600
/*
 * __report_rangecheckfailure is not available in the VS 2010 MSVCRT library
 * so we declare it here, if we are building with VS 2010, to allow us to
 * link libraries built with later VS versions.
 */
void __report_rangecheckfailure() {
}
#endif
#endif

