/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core;

import static org.graalvm.compiler.debug.DebugContext.VERBOSE_LEVEL;
import static org.graalvm.compiler.debug.DebugOptions.Dump;
import static org.graalvm.compiler.debug.DebugOptions.DumpPath;
import static org.graalvm.compiler.debug.DebugOptions.MethodFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugOutputDirectory;
import org.graalvm.compiler.debug.DebugRetryableTask;
import org.graalvm.compiler.debug.PathUtilities;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BailoutException;

/**
 * Extends {@link DebugRetryableTask} to enable dumping for diagnosing a compilation failure.
 */
public abstract class RetryableCompilation<T> extends DebugRetryableTask<T> {

    private final DebugOutputDirectory outputDirectory;

    /**
     * @param outputDirectory object used to access a directory for dumping if the compilation is
     *            re-executed
     */
    public RetryableCompilation(DebugOutputDirectory outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /**
     * Gets a value that represents the input to the compilation.
     */
    @Override
    public abstract String toString();

    protected abstract DebugContext createRetryDebugContext(OptionValues options);

    @Override
    protected DebugContext openRetryContext(DebugContext initialDebug, Throwable t) {
        if (t instanceof BailoutException) {
            return null;
        }

        OptionValues initialOptions = initialDebug.getOptions();
        if (Dump.hasBeenSet(initialOptions)) {
            // If dumping is explicitly enabled, Graal is being debugged
            // so don't interfere with what the user is expecting to see.
            return null;
        }

        String dir = this.outputDirectory.getPath();
        if (dir == null) {
            return null;
        }
        String dumpName = PathUtilities.sanitizeFileName(toString());
        File dumpPath = new File(dir, dumpName);
        dumpPath.mkdirs();
        if (!dumpPath.exists()) {
            TTY.println("Warning: could not create dump directory " + dumpPath);
            return null;
        }

        TTY.println("Retrying compilation of " + this + " due to " + t);
        retryLogPath = new File(dumpPath, "retry.log").getPath();
        log("Exception causing retry", t);
        OptionValues retryOptions = new OptionValues(initialOptions,
                        Dump, ":" + VERBOSE_LEVEL,
                        MethodFilter, null,
                        DumpPath, dumpPath.getPath());
        return createRetryDebugContext(retryOptions);
    }

    private String retryLogPath;

    /**
     * Prints a message to a retry log file.
     *
     * @param message the message to print
     * @param t if non-{@code null}, the stack trace for this exception is written to the retry log
     *            after {@code message}
     */
    protected void log(String message, Throwable t) {
        if (retryLogPath != null) {
            try (PrintStream retryLog = new PrintStream(new FileOutputStream(retryLogPath), true)) {
                StringBuilder buf = new StringBuilder(Thread.currentThread() + ": " + message);
                if (t != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    t.printStackTrace(new PrintStream(baos));
                    buf.append(System.lineSeparator()).append(baos.toString());
                }
                retryLog.println(buf);
            } catch (FileNotFoundException e) {
                TTY.println("Warning: could not open retry log file " + retryLogPath + " [" + e + "]");
            }
        }
    }
}
