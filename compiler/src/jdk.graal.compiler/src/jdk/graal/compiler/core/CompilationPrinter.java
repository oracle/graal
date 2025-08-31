/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core;

import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadAllocatedBytes;
import static jdk.graal.compiler.serviceprovider.GraalServices.getCurrentThreadCpuTime;
import static jdk.graal.compiler.serviceprovider.GraalServices.isCurrentThreadCpuTimeSupported;
import static jdk.graal.compiler.serviceprovider.GraalServices.isThreadAllocatedMemorySupported;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.debug.CSVUtil;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Utility for printing an informational line to {@link TTY} or a CSV file upon completion of
 * compiling a method.
 */
public final class CompilationPrinter {

    /**
     * The shared CSV stream or {@code null} if there is no open stream.
     */
    private static volatile PrintStream csvStream;

    /**
     * The ID of the compilation.
     */
    private final CompilationIdentifier id;

    /**
     * The compiled method or foreign call signature.
     */
    private final Object source;

    /**
     * The entry BCI of the compiled method.
     */
    private final int entryBCI;

    /**
     * The wall-time timestamp at the beginning of the compilation.
     */
    private final long beginWallTime;

    /**
     * The thread-time timestamp at the beginning of the compilation.
     */
    private final long beginThreadTime;

    /**
     * The allocated bytes at the beginning of the compilation.
     */
    private final long beginAllocatedBytes;

    /**
     * Whether information about this compilation should be printed to {@link TTY}.
     */
    private final boolean printTTY;

    /**
     * Gets an object that will report statistics for a compilation if
     * {@link GraalCompilerOptions#PrintCompilation} is enabled (and {@link TTY} is not suppressed)
     * or a CSV file is specified with {@link GraalCompilerOptions#PrintCompilationCSV}. This method
     * should be called just before a compilation starts as it captures pre-compilation data for the
     * purpose of {@linkplain #finish printing} the post-compilation statistics.
     *
     * @param options used to get the value of {@link GraalCompilerOptions#PrintCompilation}
     * @param id the identifier for the compilation
     * @param source describes the object for which code is being compiled. Must be a
     *            {@link JavaMethod} or a {@link ForeignCallSignature}
     * @param entryBCI the BCI at which compilation starts
     */
    public static CompilationPrinter begin(OptionValues options, CompilationIdentifier id, Object source, int entryBCI) {
        GraalError.guarantee(source instanceof JavaMethod || source instanceof ForeignCallSignature, "%s", source.getClass());
        boolean printTTY = GraalCompilerOptions.PrintCompilation.getValue(options) && !TTY.isSuppressed();
        String csvFilename = GraalCompilerOptions.PrintCompilationCSV.getValue(options);
        if (printTTY || csvFilename != null) {
            if (csvFilename != null && csvStream == null) {
                initializeStream(csvFilename);
            }
            return new CompilationPrinter(id, source, entryBCI, printTTY);
        }
        return DISABLED;
    }

    /**
     * Opens a shared CSV stream and prints the CSV header if it is not already open.
     *
     * @param csvFilename the filename template for the CSV file
     */
    private static synchronized void initializeStream(String csvFilename) {
        if (csvStream == null) {
            try {
                csvStream = new PrintStream(Files.newOutputStream(GlobalMetrics.generateFileName(csvFilename)), true);
                csvStream.println(
                                String.join(CSVUtil.SEPARATOR_STR, "compile_id", "method", "entry_bci", "wall_time_ns", "thread_time_ns", "allocated_memory", "compiled_bytecodes", "target_code_size",
                                                "start_address"));
            } catch (IOException e) {
                throw new GraalError(e);
            }
        }
    }

    private static final CompilationPrinter DISABLED = new CompilationPrinter();

    private CompilationPrinter() {
        this.source = null;
        this.id = null;
        this.entryBCI = -1;
        this.beginWallTime = -1;
        this.beginThreadTime = -1;
        this.beginAllocatedBytes = -1;
        this.printTTY = false;
    }

    private CompilationPrinter(CompilationIdentifier id, Object source, int entryBCI, boolean printTTY) {
        this.source = source;
        this.id = id;
        this.entryBCI = entryBCI;
        this.printTTY = printTTY;
        this.beginWallTime = System.nanoTime();
        this.beginThreadTime = isCurrentThreadCpuTimeSupported() ? getCurrentThreadCpuTime() : -1;
        this.beginAllocatedBytes = isThreadAllocatedMemorySupported() ? getCurrentThreadAllocatedBytes() : -1;
    }

    private String getMethodDescription() {
        if (source instanceof JavaMethod) {
            JavaMethod method = (JavaMethod) source;
            return String.format("%-30s %-70s %-45s %-50s %s", id.toString(CompilationIdentifier.Verbosity.ID),
                            method.getDeclaringClass().getName(), method.getName(),
                            method.getSignature().toMethodDescriptor(),
                            entryBCI == JVMCICompiler.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + entryBCI + ") ");
        } else {
            ForeignCallSignature sig = (ForeignCallSignature) source;
            return String.format("%-30s %-70s %-45s %-50s %s", id.toString(CompilationIdentifier.Verbosity.ID),
                            "<stub>", sig.getName(),
                            sig.toString(false),
                            "");
        }
    }

    /**
     * Notifies this object that the compilation finished and the informational line should be
     * printed to {@link TTY} or a CSV file.
     */
    public void finish(CompilationResult result, InstalledCode installedCode) {
        if (id != null) {
            final long endWallTime = System.nanoTime();
            final long endThreadTime = (beginThreadTime != -1) ? getCurrentThreadCpuTime() : -1;
            final long endAllocatedBytes = (beginAllocatedBytes != -1) ? getCurrentThreadAllocatedBytes() : -1;
            final long wallTime = endWallTime - beginWallTime;
            final long threadTime = endThreadTime - beginThreadTime;
            final long allocatedBytes = endAllocatedBytes - beginAllocatedBytes;
            final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
            final int bytecodeSize = result != null ? result.getBytecodeSize() : 0;
            maybePrintCSV(wallTime, threadTime, allocatedBytes, bytecodeSize, targetCodeSize, installedCode);
            if (!printTTY) {
                return;
            }
            String allocated = "";
            String installed = "";
            if (beginAllocatedBytes != -1) {
                final long allocatedKBytes = allocatedBytes / 1024;
                allocated = String.format(" %5dkB allocated", allocatedKBytes);
            }
            if (installedCode != null) {
                installed = String.format(" start=0x%016x", installedCode.getStart());
            }
            TTY.println(getMethodDescription() + String.format(" | %4dus %5dB bytecodes %5dB codesize%s%s", wallTime / 1000, bytecodeSize, targetCodeSize, allocated, installed));
        }
    }

    /**
     * Prints an information line to the shared CSV stream if it is open.
     */
    private void maybePrintCSV(long wallTime, long threadTime, long allocatedBytes, int bytecodeSize, int targetCodeSize, InstalledCode installedCode) {
        if (csvStream == null) {
            return;
        }
        String methodName;
        if (source instanceof JavaMethod method) {
            methodName = method.format("%H.%n(%p)");
        } else {
            ForeignCallSignature sig = (ForeignCallSignature) source;
            methodName = sig.toString();
        }
        long start = (installedCode == null) ? 0 : installedCode.getStart();
        String message = String.format(CSVUtil.buildFormatString("%s", "%s", "%d", "%d", "%d", "%d", "%d", "%d", "0x%016x"),
                        CSVUtil.Escape.escapeArgs(id.toString(CompilationIdentifier.Verbosity.ID), methodName, entryBCI, wallTime, threadTime, allocatedBytes, bytecodeSize, targetCodeSize, start));
        synchronized (CompilationPrinter.class) {
            PrintStream stream = csvStream;
            if (stream != null) {
                stream.println(message);
            }
        }
    }

    /**
     * Closes the shared CSV stream if it is open.
     */
    public static synchronized void close() {
        if (csvStream != null) {
            csvStream.close();
            csvStream = null;
        }
    }

    /**
     * Returns true if there is an open CSV stream.
     */
    public static synchronized boolean printingToCSV() {
        return csvStream != null;
    }
}
