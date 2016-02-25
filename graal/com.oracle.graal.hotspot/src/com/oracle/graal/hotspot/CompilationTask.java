/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import static com.oracle.graal.compiler.GraalCompilerOptions.ExitVMOnBailout;
import static com.oracle.graal.compiler.GraalCompilerOptions.ExitVMOnException;
import static com.oracle.graal.compiler.GraalCompilerOptions.PrintAfterCompilation;
import static com.oracle.graal.compiler.GraalCompilerOptions.PrintBailout;
import static com.oracle.graal.compiler.GraalCompilerOptions.PrintCompilation;
import static com.oracle.graal.compiler.GraalCompilerOptions.PrintFilter;
import static com.oracle.graal.compiler.GraalCompilerOptions.PrintStackTraceOnException;
import static com.oracle.graal.compiler.phases.HighTier.Options.Inline;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotInstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.hotspot.events.EmptyEventProvider;
import jdk.vm.ci.hotspot.events.EventProvider;
import jdk.vm.ci.hotspot.events.EventProvider.CompilationEvent;
import jdk.vm.ci.hotspot.events.EventProvider.CompilerFailureEvent;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.services.Services;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugDumpScope;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.debug.Management;
import com.oracle.graal.debug.TTY;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.OptionValue.OverrideScope;

//JaCoCo Exclude

public class CompilationTask {

    private static final DebugMetric BAILOUTS = Debug.metric("Bailouts");

    private static final EventProvider eventProvider;

    static {
        EventProvider provider = Services.loadSingle(EventProvider.class, false);
        if (provider == null) {
            eventProvider = new EmptyEventProvider();
        } else {
            eventProvider = provider;
        }
    }

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;

    private final HotSpotGraalCompiler compiler;
    private final HotSpotCompilationRequest request;

    private HotSpotInstalledCode installedCode;

    /**
     * Specifies whether the compilation result is installed as the
     * {@linkplain HotSpotNmethod#isDefault() default} nmethod for the compiled method.
     */
    private final boolean installAsDefault;

    private final boolean useProfilingInfo;

    static class Lazy {
        /**
         * A {@link com.sun.management.ThreadMXBean} to be able to query some information about the
         * current compiler thread, e.g. total allocated bytes.
         */
        static final com.sun.management.ThreadMXBean threadMXBean = (com.sun.management.ThreadMXBean) Management.getThreadMXBean();
    }

    public CompilationTask(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalCompiler compiler, HotSpotCompilationRequest request, boolean useProfilingInfo, boolean installAsDefault) {
        this.jvmciRuntime = jvmciRuntime;
        this.compiler = compiler;
        this.request = request;
        this.useProfilingInfo = useProfilingInfo;
        this.installAsDefault = installAsDefault;
    }

    public HotSpotResolvedJavaMethod getMethod() {
        return request.getMethod();
    }

    /**
     * Returns the compilation id of this task.
     *
     * @return compile id
     */
    public int getId() {
        return request.getId();
    }

    public int getEntryBCI() {
        return request.getEntryBCI();
    }

    public HotSpotInstalledCode getInstalledCode() {
        return installedCode;
    }

    /**
     * Time spent in compilation.
     */
    private static final DebugTimer CompilationTime = Debug.timer("CompilationTime");

    /**
     * Meters the {@linkplain CompilationResult#getBytecodeSize() bytecodes} compiled.
     */
    private static final DebugMetric CompiledBytecodes = Debug.metric("CompiledBytecodes");

    public static final DebugTimer CodeInstallationTime = Debug.timer("CodeInstallation");

    @SuppressWarnings("try")
    public CompilationRequestResult runCompilation() {
        HotSpotVMConfig config = jvmciRuntime.getConfig();
        final long threadId = Thread.currentThread().getId();
        int entryBCI = getEntryBCI();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
        HotSpotResolvedJavaMethod method = getMethod();

        // Log a compilation event.
        CompilationEvent compilationEvent = eventProvider.newCompilationEvent();

        // If there is already compiled code for this method on our level we simply return.
        // JVMCI compiles are always at the highest compile level, even in non-tiered mode so we
        // only need to check for that value.
        if (method.hasCodeAtLevel(entryBCI, config.compilationLevelFullOptimization)) {
            return null;
        }

        CompilationResult result = null;
        try (DebugCloseable a = CompilationTime.start()) {
            CompilationStatistics stats = CompilationStatistics.create(method, isOSR);
            final boolean printCompilation = PrintCompilation.getValue() && !TTY.isSuppressed();
            final boolean printAfterCompilation = PrintAfterCompilation.getValue() && !TTY.isSuppressed();
            if (printCompilation) {
                TTY.println(getMethodDescription() + "...");
            }

            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            final long start;
            final long allocatedBytesBefore;
            if (printAfterCompilation || printCompilation) {
                start = System.currentTimeMillis();
                allocatedBytesBefore = printAfterCompilation || printCompilation ? Lazy.threadMXBean.getThreadAllocatedBytes(threadId) : 0L;
            } else {
                start = 0L;
                allocatedBytesBefore = 0L;
            }

            try (Scope s = Debug.scope("Compiling", new DebugDumpScope(String.valueOf(getId()), true))) {
                // Begin the compilation event.
                compilationEvent.begin();
                /*
                 * Disable inlining if HotSpot has it disabled unless it's been explicitly set in
                 * Graal.
                 */
                boolean disableInlining = !config.inline && !Inline.hasBeenSet();
                try (OverrideScope s1 = disableInlining ? OptionValue.override(Inline, false) : null) {
                    result = compiler.compile(method, entryBCI, useProfilingInfo);
                }
            } catch (Throwable e) {
                throw Debug.handle(e);
            } finally {
                // End the compilation event.
                compilationEvent.end();

                filter.remove();

                if (printAfterCompilation || printCompilation) {
                    final long stop = System.currentTimeMillis();
                    final int targetCodeSize = result != null ? result.getTargetCodeSize() : -1;
                    final long allocatedBytesAfter = Lazy.threadMXBean.getThreadAllocatedBytes(threadId);
                    final long allocatedBytes = (allocatedBytesAfter - allocatedBytesBefore) / 1024;

                    if (printAfterCompilation) {
                        TTY.println(getMethodDescription() + String.format(" | %4dms %5dB %5dkB", stop - start, targetCodeSize, allocatedBytes));
                    } else if (printCompilation) {
                        TTY.println(String.format("%-6d JVMCI %-70s %-45s %-50s | %4dms %5dB %5dkB", getId(), "", "", "", stop - start, targetCodeSize, allocatedBytes));
                    }
                }
            }

            if (result != null) {
                try (DebugCloseable b = CodeInstallationTime.start()) {
                    installMethod(result);
                }
            }
            stats.finish(method, installedCode);
            if (result != null) {
                return CompilationRequestResult.success(result.getBytecodeSize() - method.getCodeSize());
            }
            return null;
        } catch (BailoutException bailout) {
            BAILOUTS.increment();
            if (ExitVMOnBailout.getValue()) {
                TTY.out.println(method.format("Bailout in %H.%n(%p)"));
                bailout.printStackTrace(TTY.out);
                System.exit(-1);
            } else if (PrintBailout.getValue()) {
                TTY.out.println(method.format("Bailout in %H.%n(%p)"));
                bailout.printStackTrace(TTY.out);
            }
            /*
             * Treat bailouts as retryable.
             */
            return CompilationRequestResult.failure(bailout.getMessage(), true);
        } catch (Throwable t) {
            // Log a failure event.
            CompilerFailureEvent event = eventProvider.newCompilerFailureEvent();
            if (event.shouldWrite()) {
                event.setCompileId(getId());
                event.setMessage(t.getMessage());
                event.commit();
            }

            handleException(t);
            /*
             * Treat random exceptions from the compiler as indicating a problem compiling this
             * method. Report the result of toString instead of getMessage to ensure that the
             * exception type is included in the output in case there's no detail mesage.
             */
            return CompilationRequestResult.failure(t.toString(), false);
        } finally {
            try {
                int compiledBytecodes = 0;
                int codeSize = 0;
                if (result != null) {
                    compiledBytecodes = result.getBytecodeSize();
                }
                if (installedCode != null) {
                    codeSize = installedCode.getSize();
                }
                CompiledBytecodes.add(compiledBytecodes);

                // Log a compilation event.
                if (compilationEvent.shouldWrite()) {
                    compilationEvent.setMethod(method.format("%H.%n(%p)"));
                    compilationEvent.setCompileId(getId());
                    compilationEvent.setCompileLevel(config.compilationLevelFullOptimization);
                    compilationEvent.setSucceeded(result != null && installedCode != null);
                    compilationEvent.setIsOsr(isOSR);
                    compilationEvent.setCodeSize(codeSize);
                    compilationEvent.setInlinedBytes(compiledBytecodes);
                    compilationEvent.commit();
                }
            } catch (Throwable t) {
                handleException(t);
            }
        }
    }

    protected void handleException(Throwable t) {
        /*
         * Automatically enable ExitVMOnException when asserts are enabled but respect
         * ExitVMOnException if it's been explicitly set.
         */
        boolean exitVMOnException = ExitVMOnException.getValue();
        assert ExitVMOnException.hasBeenSet() || (exitVMOnException = true) == true;

        if (PrintStackTraceOnException.getValue() || exitVMOnException) {
            try {
                t.printStackTrace(TTY.out);
            } catch (Throwable throwable) {
                // Don't let an exception here change the other control flow
            }
        }

        if (exitVMOnException) {
            System.exit(-1);
        }
    }

    private String getMethodDescription() {
        HotSpotResolvedJavaMethod method = getMethod();
        return String.format("%-6d JVMCI %-70s %-45s %-50s %s", getId(), method.getDeclaringClass().getName(), method.getName(), method.getSignature().toMethodDescriptor(),
                        getEntryBCI() == JVMCICompiler.INVOCATION_ENTRY_BCI ? "" : "(OSR@" + getEntryBCI() + ") ");
    }

    @SuppressWarnings("try")
    private void installMethod(final CompilationResult compResult) {
        final CodeCacheProvider codeCache = jvmciRuntime.getHostJVMCIBackend().getCodeCache();
        installedCode = null;
        try (Scope s = Debug.scope("CodeInstall", new DebugDumpScope(String.valueOf(getId()), true), codeCache, getMethod())) {
            HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(request.getMethod(), request, compResult);
            installedCode = (HotSpotInstalledCode) codeCache.installCode(request.getMethod(), compiledCode, null, request.getMethod().getSpeculationLog(), installAsDefault);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    @Override
    public String toString() {
        return "Compilation[id=" + getId() + ", " + getMethod().format("%H.%n(%p)") + (getEntryBCI() == JVMCICompiler.INVOCATION_ENTRY_BCI ? "" : "@" + getEntryBCI()) + "]";
    }
}
