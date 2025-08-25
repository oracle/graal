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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.util.BasedOnJDKFile;
import com.oracle.svm.core.util.ExitStatus;
import com.oracle.svm.driver.NativeImage.HostFlags;
import com.oracle.svm.driver.NativeImage.NativeImageError;

import jdk.jfr.internal.JVM;
import org.graalvm.collections.Pair;

public final class MemoryUtil {
    public static final long KiB_TO_BYTES = 1024L;
    public static final long MiB_TO_BYTES = 1024L * KiB_TO_BYTES;
    public static final long GiB_TO_BYTES = 1024L * MiB_TO_BYTES;
    public static final long TiB_TO_BYTES = 1024L * GiB_TO_BYTES;

    /* Builder needs at least 512MiB for building a helloworld in a reasonable amount of time. */
    private static final long MIN_HEAP_BYTES = 512L * MiB_TO_BYTES;

    /* Use 85% of total system memory (e.g., 7GiB * 85% ~ 6GiB) in dedicated mode. */
    private static final double DEDICATED_MODE_TOTAL_MEMORY_RATIO = 0.85D;

    /* If available memory is below 8GiB, fall back to dedicated mode. */
    private static final int MIN_AVAILABLE_MEMORY_THRESHOLD_GB = 8;

    /*
     * Builder uses at most 32GB to avoid disabling compressed oops (UseCompressedOops).
     * Deliberately use GB (not GiB) to stay well below 32GiB when relative maximum is calculated.
     */
    public static final long MAX_HEAP_BYTES = 32_000_000_000L;

    public static List<String> heuristicMemoryFlags(HostFlags hostFlags, List<String> memoryFlags) {
        /*
         * Use MaxRAMPercentage to allow users to overwrite max heap setting with
         * -XX:MaxRAMPercentage or -Xmx (though determineMemoryUsageFlags will detect that case and
         * not add any flag), and freely adjust the min heap with -XX:InitialRAMPercentage or -Xms.
         */
        if (hostFlags.hasMaxRAMPercentage()) {
            return determineMemoryUsageFlags(memoryFlags, value -> "-XX:MaxRAMPercentage=" + value);
        } else if (hostFlags.hasMaximumHeapSizePercent()) {
            return determineMemoryUsageFlags(memoryFlags, value -> "-XX:MaximumHeapSizePercent=" + value.intValue());
        } else {
            throw new Error("Neither -XX:MaxRAMPercentage= nor -XX:MaximumHeapSizePercent= are available");
        }
    }

    // A String in the memory reason to indicate that user memory flags overrode the heuristic
    private static final String SET_VIA = ", set via '";

    /**
     * Returns memory usage flags for the build process. Dedicated mode uses a fixed percentage of
     * total memory and is the default in containers. Shared mode tries to use available memory to
     * reduce memory pressure on the host machine. Note that this method uses OperatingSystemMXBean,
     * which is container-aware.
     */
    private static List<String> determineMemoryUsageFlags(List<String> memoryFlags, Function<Double, String> toMemoryFlag) {
        var osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        final long totalMemorySize = osBean.getTotalMemorySize();

        var maxMemoryAndUsageText = maxMemoryHeuristic(totalMemorySize, SubstrateUtil.isCISetToTrue(), isContainerized(), MemoryUtil::getAvailableMemorySize, memoryFlags);
        long maxMemory = maxMemoryAndUsageText.getLeft();
        String memoryUsageText = maxMemoryAndUsageText.getRight();
        String memoryUsageReason = "-D" + SubstrateOptions.BUILD_MEMORY_USAGE_REASON_TEXT_PROPERTY + "=" + memoryUsageText;

        if (memoryUsageText.contains(SET_VIA)) {
            return List.of(memoryUsageReason);
        } else {
            double maxRamPercentage = ((double) maxMemory) / totalMemorySize * 100.0;
            String memoryFlag = toMemoryFlag.apply(maxRamPercentage);
            return List.of(memoryFlag, memoryUsageReason);
        }
    }

    /**
     * Returns the max memory (decided by the heuristic or by the user memory flags) in bytes and
     * the reason.
     */
    public static Pair<Long, String> maxMemoryHeuristic(long totalMemorySize, boolean isCISetToTrue, boolean isContainerized, Supplier<Long> getAvailableMemorySize, List<String> memoryFlags) {
        final long dedicatedMemorySize = (long) (totalMemorySize * DEDICATED_MODE_TOTAL_MEMORY_RATIO);

        long maxMemory;
        String reason;
        if (isCISetToTrue) {
            reason = "85% of system memory because $CI set to 'true'";
            maxMemory = dedicatedMemorySize;
        } else if (isContainerized) {
            reason = "85% of system memory because in container";
            maxMemory = dedicatedMemorySize;
        } else {
            long availableMemorySize = getAvailableMemorySize.get();
            if (availableMemorySize >= MIN_AVAILABLE_MEMORY_THRESHOLD_GB * GiB_TO_BYTES) {
                reason = percentageOfSystemMemoryText(availableMemorySize, totalMemorySize) + ", using all available memory";
                maxMemory = availableMemorySize;
            } else { // fall back to dedicated mode
                reason = "85%% of system memory because less than %dGiB available".formatted(MIN_AVAILABLE_MEMORY_THRESHOLD_GB);
                maxMemory = dedicatedMemorySize;
            }
        }

        if (maxMemory < MIN_HEAP_BYTES) {
            throw new NativeImageError(
                            "There is not enough memory available on the system (got %sMiB, need at least %sMiB). Consider freeing up memory if builds are slow, for example, by closing applications that you do not need."
                                            .formatted(maxMemory / MiB_TO_BYTES, MIN_HEAP_BYTES / MiB_TO_BYTES),
                            null, ExitStatus.OUT_OF_MEMORY.getValue());
        }

        // Ensure max memory size does not exceed upper limit
        if (maxMemory > MAX_HEAP_BYTES) {
            maxMemory = MAX_HEAP_BYTES;
            reason = percentageOfSystemMemoryText(maxMemory, totalMemorySize) + ", capped at 32GB";
        }

        // Handle memory flags
        if (!memoryFlags.isEmpty()) {
            long newMaxMemory = determineMaxHeapBasedOnMemoryFlags(memoryFlags, maxMemory, totalMemorySize);
            if (newMaxMemory > 0) {
                reason = percentageOfSystemMemoryText(newMaxMemory, totalMemorySize) + SET_VIA + String.join(" ", memoryFlags) + "'";
                maxMemory = newMaxMemory;
            } else {
                reason += ", user flags: '%s'".formatted(String.join(" ", memoryFlags));
            }
        }

        double maxMemoryGiB = (double) maxMemory / GiB_TO_BYTES;
        String memoryUsageText = "%.2fGiB of memory (%s)".formatted(maxMemoryGiB, reason);

        return Pair.create(maxMemory, memoryUsageText);
    }

    static boolean isMemoryFlag(String flag) {
        return Stream.of("-Xmx", "-Xms", "-XX:MaxRAMPercentage=", "-XX:MaximumHeapSizePercent=").anyMatch(flag::startsWith);
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+10/src/hotspot/share/runtime/arguments.cpp#L1530-L1532")
    private static long determineMaxHeapBasedOnMemoryFlags(List<String> memoryFlags, long heuristicMaxMemory, long totalMemory) {
        // Priority: Xmx, MaxRAMPercentage, MaximumHeapSizePercent
        var xmx = getMaxMemoryFlagValue("-Xmx", memoryFlags, totalMemory);
        var maxRAMPercentage = getMaxMemoryFlagValue("-XX:MaxRAMPercentage=", memoryFlags, totalMemory);
        var maximumHeapSizePercent = getMaxMemoryFlagValue("-XX:MaximumHeapSizePercent=", memoryFlags, totalMemory);
        var xms = getMaxMemoryFlagValue("-Xms", memoryFlags, totalMemory);
        long newMaxMemory = 0;
        if (xmx > 0) {
            newMaxMemory = xmx;
        } else if (maxRAMPercentage > 0) {
            newMaxMemory = maxRAMPercentage;
        } else if (maximumHeapSizePercent > 0) {
            newMaxMemory = maximumHeapSizePercent;
        }

        if (newMaxMemory == 0 ? xms > heuristicMaxMemory : xms > newMaxMemory) {
            // Xms only affects max memory if the value is higher than the current max memory value
            newMaxMemory = xms;
        }
        return newMaxMemory;
    }

    private static String percentageOfSystemMemoryText(long maxMemory, long totalMemory) {
        return "%.1f%% of system memory".formatted(toPercentage(maxMemory, totalMemory));
    }

    private static long getMaxMemoryFlagValue(String prefix, List<String> memoryFlags, long totalMemory) {
        long max = 0;
        for (String flag : memoryFlags) {
            if (flag.startsWith(prefix)) {
                long value = parseMemoryFlagValue(flag, totalMemory);
                if (value > max) {
                    max = value;
                }
            }
        }
        return max;
    }

    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-26+10/src/hotspot/share/utilities/parseInteger.hpp#L105-L160")
    public static long parseMemoryFlagValue(String flag, long totalMemory) {
        if (flag.startsWith("-Xmx") || flag.startsWith("-Xms")) {
            String valuePart = flag.substring(4);
            if (valuePart.isEmpty()) {
                throw new Error("Invalid value for: " + flag);
            }
            char unit = valuePart.charAt(valuePart.length() - 1);
            long multiplier = switch (unit) {
                case 'T', 't' -> TiB_TO_BYTES;
                case 'G', 'g' -> GiB_TO_BYTES;
                case 'M', 'm' -> MiB_TO_BYTES;
                case 'K', 'k' -> KiB_TO_BYTES;
                default -> 1;
            };
            if (multiplier != 1) {
                valuePart = valuePart.substring(0, valuePart.length() - 1);
            }
            long value = parseLongOrFlagError(flag, valuePart);
            return value * multiplier;
        } else if (flag.startsWith("-XX:MaxRAMPercentage=")) {
            String valuePart = flag.substring("-XX:MaxRAMPercentage=".length());
            double value = parseDoubleOrFlagError(flag, valuePart);
            return (long) (value / 100.0 * totalMemory);
        } else if (flag.startsWith("-XX:MaximumHeapSizePercent=")) {
            String valuePart = flag.substring("-XX:MaximumHeapSizePercent=".length());
            double value = parseLongOrFlagError(flag, valuePart);
            return (long) (value / 100.0 * totalMemory);
        } else {
            throw new Error("Unknown flag: " + flag);
        }
    }

    private static long parseLongOrFlagError(String flag, String valuePart) {
        try {
            return Long.parseLong(valuePart);
        } catch (NumberFormatException e) {
            throw new Error("Invalid value for: " + flag);
        }
    }

    private static double parseDoubleOrFlagError(String flag, String valuePart) {
        try {
            return Double.parseDouble(valuePart);
        } catch (NumberFormatException e) {
            throw new Error("Invalid value for: " + flag);
        }
    }

    private static double toPercentage(long part, long total) {
        return part / (double) total * 100;
    }

    private static boolean isContainerized() {
        if (!OS.LINUX.isCurrent()) {
            return false;
        }
        return JVM.isContainerized();
    }

    private static long getAvailableMemorySize() {
        return switch (OS.getCurrent()) {
            case LINUX -> getAvailableMemorySizeLinux();
            case DARWIN -> getAvailableMemorySizeDarwin();
            case WINDOWS -> getAvailableMemorySizeWindows();
        };
    }

    /**
     * Returns the total amount of available memory in bytes on Linux based on
     * <code>/proc/meminfo</code>, otherwise <code>-1</code>. Note that this metric is not
     * container-aware (does not take cgroups into account) and may report available memory of the
     * host.
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
