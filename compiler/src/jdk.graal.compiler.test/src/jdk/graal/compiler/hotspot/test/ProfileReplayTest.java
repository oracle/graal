/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import java.io.IOException;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.ProfileReplaySupport;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ProfileReplayTest extends GraalCompilerTest {

    abstract static class A {
        abstract void bark();
    }

    static class B extends A {
        @Override
        void bark() {
            GraalDirectives.sideEffect(2);
        }
    }

    static class C extends A {
        @Override
        void bark() {
            GraalDirectives.sideEffect(3);
        }
    }

    static class D extends B {
        @Override
        void bark() {
            GraalDirectives.sideEffect(4);
        }
    }

    static int foo(int bar, int baz, A a) {
        a.bark();
        switch (baz) {
            case 1:
                GraalDirectives.sideEffect(1);
                break;
            case 2:
                GraalDirectives.sideEffect(3);
                break;
            case 3:
                GraalDirectives.sideEffect(7);
                break;
            default:
                break;
        }
        if (bar % 2 == 0) {
            return 0;
        } else {
            return 1;
        }
    }

    @SuppressWarnings("try")
    @Test
    public void testProfileReplay() throws IOException {
        HotSpotJVMCIRuntime jvmciRuntime = HotSpotJVMCIRuntime.runtime();
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) jvmciRuntime.getCompiler();

        for (int i = 0; i < 100000; i++) {
            A use = null;
            if (i % 2 == 0) {
                use = new B();
            } else if (i % 3 == 0) {
                use = new C();
            } else {
                use = new D();
            }
            foo(i, i % 4, use);
        }
        final ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        try (TemporaryDirectory temp = new TemporaryDirectory(getClass(), "ProfileReplayTest")) {
            OptionValues overrides = new OptionValues(getInitialOptions(), DebugOptions.DumpPath, temp.toString());
            runInitialCompilation(method, overrides, jvmciRuntime, compiler);
            runSanityCompilation(temp.toString(), method, overrides, jvmciRuntime, compiler);
        }
    }

    private static void runInitialCompilation(ResolvedJavaMethod m, OptionValues rootOptions, HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler) {
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) m, -1, 0L);
        OptionValues opt = new OptionValues(rootOptions, ProfileReplaySupport.Options.StrictProfiles, true, ProfileReplaySupport.Options.SaveProfiles, true);
        CompilationTask task = new CompilationTask(jvmciRuntime, compiler, request, true, false, true, false);
        runCompile(task, opt);
    }

    private static void runSanityCompilation(String profilesPath, ResolvedJavaMethod m, OptionValues rootOptions, HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalCompiler compiler) {
        HotSpotCompilationRequest request = new HotSpotCompilationRequest((HotSpotResolvedJavaMethod) m, -1, 0L);
        OptionValues opt = new OptionValues(rootOptions, ProfileReplaySupport.Options.StrictProfiles, true, ProfileReplaySupport.Options.LoadProfiles, profilesPath);
        CompilationTask task = new CompilationTask(jvmciRuntime, compiler, request, true, false, true, false);
        runCompile(task, opt);
    }

    private static void runCompile(CompilationTask task, OptionValues opt) {
        HotSpotCompilationRequestResult res = task.runCompilation(opt);
        InstalledCode installedCode = task.getInstalledCode();
        if (installedCode != null) {
            installedCode.invalidate();
        }
        Assert.assertNull(res.getFailureMessage());
    }
}
