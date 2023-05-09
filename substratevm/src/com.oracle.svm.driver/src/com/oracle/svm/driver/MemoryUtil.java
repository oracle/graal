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
package com.oracle.svm.driver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.driver.NativeImage.NativeImageError;

class MemoryUtil {
    private static final long KiB_TO_BYTES = 1024;
    private static final long MiB_TO_BYTES = 1024 * KiB_TO_BYTES;

    /* Builder needs at least 512MiB for building a helloworld in a reasonable amount of time. */
    private static final long MIN_HEAP_BYTES = 512 * MiB_TO_BYTES;

    /*
     * Builder uses at most 32GB to avoid disabling compressed oops (UseCompressedOops).
     * Deliberately use GB (not GiB) to stay well below 32GiB when relative maximum is calculated.
     */
    private static final long MAX_HEAP_BYTES = 32_000_000_000L;

    /* Use 80% of total system memory in case available memory cannot be determined. */
    private static final double FALLBACK_MAX_RAM_PERCENTAGE = 80.0;

    public static List<String> determineMemoryFlags() {
        return List.of(
                        /*
                         * Use MaxRAMPercentage to allow users to overwrite max heap setting with
                         * -XX:MaxRAMPercentage or -Xmx, and freely adjust the min heap with
                         * -XX:InitialRAMPercentage or -Xms.
                         */
                        "-XX:MaxRAMPercentage=" + determineReasonableMaxRAMPercentage(),
                        /*
                         * Optimize for throughput by increasing the goal of the total time for
                         * garbage collection from 1% to 5% (N=19). This also reduces peak RSS.
                         */
                        "-XX:GCTimeRatio=19", // 1/(1+N) time for GC
                        /*
                         * Let builder exit on first OutOfMemoryError to provide for shorter
                         * feedback loops.
                         */
                        "-XX:+ExitOnOutOfMemoryError");
    }

    /**
     * Returns a percentage (0.0-100.0) to be used as a value for the -XX:MaxRAMPercentage flag of
     * the builder process. Prefer available memory over total memory to reduce memory pressure on
     * the host machine.
     */
    private static double determineReasonableMaxRAMPercentage() {
        var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemorySize = osBean.getTotalMemorySize();
        long reasonableMaxMemorySize = -1;
        reasonableMaxMemorySize = switch (OS.getCurrent()) {
            case LINUX -> getAvailableMemorySizeLinux();
            case DARWIN -> getAvailableMemorySizeDarwin();
            case WINDOWS -> getAvailableMemorySizeWindows();
        };
        if (reasonableMaxMemorySize < 0 || reasonableMaxMemorySize > totalMemorySize) {
            return FALLBACK_MAX_RAM_PERCENTAGE;
        }
        if (reasonableMaxMemorySize < MIN_HEAP_BYTES) {
            throw new NativeImageError(
                            "There is not enough memory available on the system (got %sMiB, need at least %sMiB). Consider freeing up memory if builds are slow, for example, by closing applications that you do not need."
                                            .formatted(reasonableMaxMemorySize / MiB_TO_BYTES, MIN_HEAP_BYTES / MiB_TO_BYTES),
                            null, ExitStatus.OUT_OF_MEMORY.getValue());
        }
        reasonableMaxMemorySize = Math.min(reasonableMaxMemorySize, MAX_HEAP_BYTES);
        return (double) reasonableMaxMemorySize / totalMemorySize * 100;
    }

    /**
     * Returns the total amount of available memory in bytes on Linux based on
     * <code>/proc/meminfo</code>, otherwise <code>-1</code>.
     *
     * @see <a href=
     *      "https://github.com/torvalds/linux/blob/865fdb08197e657c59e74a35fa32362b12397f58/mm/page_alloc.c#L5137">page_alloc.c#L5137</a>
     */
    private static long getAvailableMemorySizeLinux() {
        try {
            String memAvailableLine = Files.readAllLines(Paths.get("/proc/meminfo")).stream().filter(l -> l.startsWith("MemAvailable")).findFirst().orElse("");
            Matcher m = Pattern.compile("^MemAvailable:\\s+(\\d+) kB").matcher(memAvailableLine);
            if (m.matches()) {
                return Long.parseLong(m.group(1)) * KiB_TO_BYTES;
            }
        } catch (Exception e) {
        }
        return -1;
    }

    /**
     * Returns the total amount of available memory in bytes on Darwin based on
     * <code>vm_stat</code>, otherwise <code>-1</code>.
     *
     * @see <a href=
     *      "https://opensource.apple.com/source/system_cmds/system_cmds-496/vm_stat.tproj/vm_stat.c.auto.html">vm_stat.c</a>
     */
    private static long getAvailableMemorySizeDarwin() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"vm_stat"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line1 = reader.readLine();
                if (line1 == null) {
                    return -1;
                }
                Matcher m1 = Pattern.compile("^Mach Virtual Memory Statistics: \\(page size of (\\d+) bytes\\)").matcher(line1);
                long pageSize = -1;
                if (m1.matches()) {
                    pageSize = Long.parseLong(m1.group(1));
                }
                if (pageSize <= 0) {
                    return -1;
                }
                String line2 = reader.readLine();
                Matcher m2 = Pattern.compile("^Pages free:\\s+(\\d+).").matcher(line2);
                long freePages = -1;
                if (m2.matches()) {
                    freePages = Long.parseLong(m2.group(1));
                }
                if (freePages <= 0) {
                    return -1;
                }
                String line3 = reader.readLine();
                if (!line3.startsWith("Pages active")) {
                    return -1;
                }
                String line4 = reader.readLine();
                Matcher m4 = Pattern.compile("^Pages inactive:\\s+(\\d+).").matcher(line4);
                long inactivePages = -1;
                if (m4.matches()) {
                    inactivePages = Long.parseLong(m4.group(1));
                }
                if (inactivePages <= 0) {
                    return -1;
                }
                assert freePages > 0 && inactivePages > 0 && pageSize > 0;
                return (freePages + inactivePages) * pageSize;
            } finally {
                p.waitFor();
            }
        } catch (Exception e) {
        }
        return -1;
    }

    /**
     * Returns the total amount of available memory in bytes on Windows based on <code>wmic</code>,
     * otherwise <code>-1</code>.
     *
     * @see <a href=
     *      "https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-operatingsystem">Win32_OperatingSystem
     *      class</a>
     */
    private static long getAvailableMemorySizeWindows() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", "wmic", "OS", "get", "FreePhysicalMemory"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line1 = reader.readLine();
                if (line1 == null || !line1.startsWith("FreePhysicalMemory")) {
                    return -1;
                }
                String line2 = reader.readLine();
                if (line2 == null) {
                    return -1;
                }
                String line3 = reader.readLine();
                if (line3 == null) {
                    return -1;
                }
                Matcher m = Pattern.compile("^(\\d+)\\s+").matcher(line3);
                if (m.matches()) {
                    return Long.parseLong(m.group(1)) * KiB_TO_BYTES;
                }
            }
            p.waitFor();
        } catch (Exception e) {
        }
        return -1;
    }
}
