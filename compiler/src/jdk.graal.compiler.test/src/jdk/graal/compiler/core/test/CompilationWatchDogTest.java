/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import static jdk.graal.compiler.core.CompilationWatchDog.Options.CompilationWatchDogStartDelay;
import static jdk.graal.compiler.core.CompilationWatchDog.Options.CompilationWatchDogVMExitDelay;
import static jdk.graal.compiler.core.GraalCompilerOptions.InjectedCompilationDelay;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.CompilationWatchDog.EventHandler;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests the {@link CompilationWatchDog}.
 */
public class CompilationWatchDogTest extends GraalCompilerTest {

    /**
     * Configures the watch dog to monitor a compilation after it has run for 1 second and report a
     * compiler stack trace every 1 second after that but consider the compilation stuck after
     * seeing the same stack in 2 consecutive reports.
     *
     * A compilation is then started with a 6-second injected delay. Asserts that at least 1 long
     * compilation event and 1 stuck compilation event is seen.
     */
    @Test
    public void test() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InjectedCompilationDelay, 6,
                        CompilationWatchDogStartDelay, 1,
                        CompilationWatchDogVMExitDelay, 2);
        test(options, "snippet", 42);
    }

    public static int snippet(int value) {
        return value;
    }

    /**
     * Access to this list is synchronized to avoid a ConcurrentModificationException.
     */
    private List<String> events = new ArrayList<>();

    private long firstEvent = System.currentTimeMillis();

    private synchronized void event(String label) {
        long when = System.currentTimeMillis() - firstEvent;
        events.add(String.format("after %d ms: %s", when, label));
    }

    private synchronized String eventLog() {
        return events.stream().collect(Collectors.joining(System.lineSeparator()));
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        // Make 3 attempts to provoke a stuck compilation.
        try {
            return getCodeHelper(installedCodeOwner, graph, true, installAsDefault, options);
        } catch (AssertionError e) {
            try {
                return getCodeHelper(installedCodeOwner, graph, true, installAsDefault, options);
            } catch (AssertionError e2) {
                try {
                    return getCodeHelper(installedCodeOwner, graph, true, installAsDefault, options);
                } catch (AssertionError e3) {
                    throw e3;
                }
            }
        }
    }

    @SuppressWarnings("try")
    private InstalledCode getCodeHelper(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        CompilationIdentifier compilation = new CompilationIdentifier() {
            @Override
            public String toString(Verbosity verbosity) {
                return installedCodeOwner.format("%H.%n(%p)");
            }

            @Override
            public String toString() {
                return toString(Verbosity.DETAILED);
            }
        };

        List<CompilationIdentifier> longCompilations = new ArrayList<>();
        List<CompilationIdentifier> stuckCompilations = new ArrayList<>();

        EventHandler longCompilationHandler = new EventHandler() {

            @Override
            public void onLongCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier longCompilation, long elapsed, StackTraceElement[] stackTrace) {
                event("onLongCompilation");
                longCompilations.add(longCompilation);
            }

            @Override
            public void onStuckCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier stuckCompilation, StackTraceElement[] stackTrace, int stackTraceCount) {
                event("onStuckCompilation");
                stuckCompilations.add(stuckCompilation);
            }

        };

        CompilationWatchDog watch = CompilationWatchDog.watch(compilation, options, false, longCompilationHandler);
        try (CompilationWatchDog watchScope = watch) {
            event("start compiling");
            try (TTY.Filter f = new TTY.Filter()) {
                return super.getCode(installedCodeOwner, graph, forceCompile, installAsDefault, options);
            }
        } finally {
            check(!longCompilations.isEmpty());
            check(longCompilations.stream().allMatch(id -> id == compilation));
            check(!stuckCompilations.isEmpty());
            check(longCompilations.stream().allMatch(id -> id == compilation));
        }
    }

    /**
     * Factored out to only fetch the event log if the condition fails.
     */
    private void check(boolean condition) {
        if (!condition) {
            Assert.fail(eventLog());
        }
    }
}
