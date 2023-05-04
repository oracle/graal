/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

#ifdef __linux__

#include <stdio.h>
#include <unistd.h>

#define NANOS_PER_SECOND 1000000000
#define STAT_FILE_NAME_SIZE 64
#define STAT_SIZE 2048

/*
 * Returns the thread user time. Based on slow_thread_cpu_time(...) method from
 * https://github.com/openjdk/jdk/blob/612d8c6cb1d0861957d3f6af96556e2739283800/src/hotspot/os/linux/os_linux.cpp#L5012
 */
long getThreadUserTime(long tid) {
    char statFileName[STAT_FILE_NAME_SIZE];
    char stat[STAT_SIZE];
    int statSize;
    char *s;
    int matchedCount;
    long userTime;
    FILE *fp;

    static long clockTicksPerSeconds = -1;

    if (clockTicksPerSeconds == -1) {
        clockTicksPerSeconds = sysconf(_SC_CLK_TCK);
    }

    snprintf(statFileName, STAT_FILE_NAME_SIZE, "/proc/self/task/%ld/stat", tid);
    fp = fopen(statFileName, "r");

    if (fp == NULL) {
        return -1;
    }

    statSize = fread(stat, 1, STAT_SIZE - 1, fp);
    stat[statSize] = '\0';
    fclose(fp);

    // File /proc/self/task/tid/stat
    // Field 1 is the process id
    // Field 2 is a command in parentheses
    // Field 3 is a character
    // Fields 1,4-8 are integers
    // Fields 9-14 are unsigned integers
    // Field 14 is the CPU time spent in user code
    //
    // Skip pid and the command string. Note that we could be dealing with
    // weird command names, e.g. user could decide to rename java launcher
    // to "java 1.4.2 :)", then the stat file would look like
    //                1234 (java 1.4.2 :)) R ... ...
    // We don't really need to know the command string, just find the last
    // occurrence of ")" and then start parsing from there. See bug 4726580.
    s = strrchr(stat, ')');

    if (s == NULL)  {
        return -1;
    }

    // Skip blank chars
    do {
        s++;
    } while (s && isspace(*s));

    matchedCount = sscanf(s, "%*c %*d %*d %*d %*d %*d %*lu %*lu %*lu %*lu %*lu %lu", &userTime);

    if (matchedCount != 1) {
        return -1;
    }

    return userTime * NANOS_PER_SECOND / clockTicksPerSeconds;
}

#else

long getThreadUserTime(long tid) {
    return -1;
}

#endif
