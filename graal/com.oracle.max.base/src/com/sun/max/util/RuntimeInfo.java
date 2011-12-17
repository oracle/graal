/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.io.*;
import java.util.regex.*;

/**
 * Class for run-time system information.
 */
public final class RuntimeInfo {

    /**
     * Gets the suggested maximum number of processes to fork.
     * @param requestedMemorySize the physical memory size (in bytes) that each process will consume.
     * @return the suggested number of processes that should be started in parallel.
     */
    public static int getSuggestedMaximumProcesses(long requestedMemorySize) {
        final Runtime runtime = Runtime.getRuntime();
        final String os = System.getProperty("os.name");
        long freeMemory = 0L;
        try {
            if (os.equals("Linux")) {
                final Process process = runtime.exec(new String[] {"/usr/bin/free", "-ob"});
                final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                in.readLine();
                final String line = in.readLine();
                final String[] fields = line.split("\\s+");
                freeMemory = Long.parseLong(fields[3]);
            } else if (os.equals("SunOS")) {
                Process process = runtime.exec(new String[] {"/bin/kstat", "-p", "-nsystem_pages", "-sfreemem"});
                BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line = in.readLine();
                String[] fields = line.split("\\s+");
                freeMemory = Long.parseLong(fields[1]);
                process = runtime.exec(new String[] {"/bin/getconf", "PAGESIZE"});
                in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                line = in.readLine();
                freeMemory *= Long.parseLong(line);
            } else if (os.equals("Mac OS X") || os.equals("Darwin")) {
                final Process process = runtime.exec("/usr/bin/vm_stat");
                final BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
                final Matcher matcher = Pattern.compile("[^0-9]*([0-9]+)[^0-9]*").matcher(in.readLine());
                if (matcher.matches()) {
                    freeMemory = Long.parseLong(matcher.group(1));
                }
                matcher.reset(in.readLine());
                if (matcher.matches()) {
                    freeMemory *= Long.parseLong(matcher.group(1));
                }
            } else if (os.equals("MaxVE")) {
                freeMemory = 0L;
            }
        } catch (Exception e) {
            freeMemory = 0L;
        }
        final int processors = runtime.availableProcessors();
        if (freeMemory <= 0L || freeMemory >= requestedMemorySize * processors) {
            return processors;
        }
        return Math.max(1, (int) (freeMemory / requestedMemorySize));
    }

    private RuntimeInfo() {
    }
}
