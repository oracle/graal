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
package org.graalvm.compiler.core;

import static org.graalvm.compiler.core.GraalCompilerOptions.PrintCompilation;
import static org.graalvm.compiler.serviceprovider.GraalServices.getCurrentThreadAllocatedBytes;
import static org.graalvm.compiler.serviceprovider.GraalServices.isThreadAllocatedMemorySupported;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.runtime.JVMCICompiler;

/**
 * Utility for printing an informational line to {@link TTY} upon completion of compiling a method.
 */
public final class CompilationPrinter {

    private final CompilationIdentifier id;
    private final Object source;
    private final int entryBCI;
    private final long start;
    private final long allocatedBytesBefore;

    /**
     * Gets an object that will report statistics for a compilation if
     * {@link GraalCompilerOptions#PrintCompilation} is enabled and {@link TTY} is not suppressed.
     * This method should be called just before a compilation starts as it captures pre-compilation
     * data for the purpose of {@linkplain #finish(CompilationResult) printing} the post-compilation
     * statistics.
     *
     * @param options used to get the value of {@link GraalCompilerOptions#PrintCompilation}
     * @param id the identifier for the compilation
     * @param source describes the object for which code is being compiled. Must be a
     *            {@link JavaMethod} or a {@link ForeignCallSignature}
     * @param entryBCI the BCI at which compilation starts
     */
    public static CompilationPrinter begin(OptionValues options, CompilationIdentifier id, Object source, int entryBCI) {
        GraalError.guarantee(source instanceof JavaMethod || source instanceof ForeignCallSignature, "%s", source.getClass());
        if (PrintCompilation.getValue(options) && !TTY.isSuppressed()) {
            return new CompilationPrinter(id, source, entryBCI);
        }
        return DISABLED;
    }

    private static final CompilationPrinter DISABLED = new CompilationPrinter();

    private CompilationPrinter() {
        this.source = null;
        this.id = null;
        this.entryBCI = -1;
        this.start = -1;
        this.allocatedBytesBefore = -1;
    }

    private CompilationPrinter(CompilationIdentifier id, Object source, int entryBCI) {
        this.source = source;
        this.id = id;
        this.entryBCI = entryBCI;

        start = System.nanoTime();
        allocatedBytesBefore = isThreadAllocatedMemorySupported() ? getCurrentThreadAllocatedBytes() : -1;
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
     * printed to {@link TTY}.
     */
    public void finish(CompilationResult result) {
        if (id != null) {
            final long stop = System.nanoTime();
            final long duration = (stop - start) / 1000;
            final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
            final int bytecodeSize = result != null ? result.getBytecodeSize() : 0;
            if (allocatedBytesBefore == -1) {
                TTY.println(getMethodDescription() + String.format(" | %4dus %5dB bytecodes %5dB codesize", duration, bytecodeSize, targetCodeSize));
            } else {
                final long allocatedBytesAfter = getCurrentThreadAllocatedBytes();
                final long allocatedKBytes = (allocatedBytesAfter - allocatedBytesBefore) / 1024;
                TTY.println(getMethodDescription() + String.format(" | %4dus %5dB bytecodes %5dB codesize %5dkB allocated", duration, bytecodeSize, targetCodeSize, allocatedKBytes));
            }
        }
    }
}
