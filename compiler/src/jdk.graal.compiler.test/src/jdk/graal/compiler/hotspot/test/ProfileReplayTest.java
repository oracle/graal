/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.collections.EconomicMap;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.core.ProfileReplaySupport;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.hotspot.CompilationTask;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.nodes.spi.StableProfileProvider.LambdaNameFormatter;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonPrettyWriter;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ProfileReplayTest extends GraalCompilerTest {

    private static final LambdaNameFormatter NON_LAMBDA_FORMATTER = new LambdaNameFormatter() {
        @Override
        public boolean isLambda(ResolvedJavaMethod method) {
            return false;
        }

        @Override
        public String formatLamdaName(ResolvedJavaMethod method) {
            return method.format("%H.%n(%p)%r");
        }
    };

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
        try (TemporaryDirectory temp = new TemporaryDirectory("ProfileReplayTest")) {
            OptionValues overrides = new OptionValues(getInitialOptions(), DebugOptions.DumpPath, temp.toString());
            runInitialCompilation(method, overrides, jvmciRuntime, compiler);
            runSanityCompilation(temp.toString(), method, overrides, jvmciRuntime, compiler);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testFailingProfileReplay() throws IOException {
        final ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        try (TemporaryDirectory temp = new TemporaryDirectory("ProfileReplayTest")) {
            OptionValues overrides = new OptionValues(getInitialOptions(),
                            ProfileReplaySupport.Options.SaveProfilesPath, temp.toString(),
                            ProfileReplaySupport.Options.OverrideProfiles, false,
                            ProfileReplaySupport.Options.SaveProfiles, true);
            try (DebugContext debug = getDebugContext(overrides)) {

                String profile = PathUtilities.sanitizeFileName(method.format("%h.%n(%p)%r") + ".glog");
                Files.createFile(Path.of(temp.toString(), profile));

                // make sure the ProfileReplay fails when trying to save the profiles
                ProfileReplaySupport profileReplay = ProfileReplaySupport.profileReplayPrologue(debug, 0, method, new StableProfileProvider(), null);

                try (DebugContext.Scope scope = debug.scope("TestScope"); DebugCloseable closable = debug.disableIntercept()) {
                    /*
                     * we haven't compiled anything, we just want ProfileReplay to save the profiles
                     * (and fail)
                     */
                    Assert.assertThrows(InternalError.class, () -> profileReplay.profileReplayEpilogue(debug, null, null, new StableProfileProvider(), getCompilationId(method), 0, method));
                }
            }
        }
    }

    @Test
    public void testStableDeoptimizationCounts() throws IOException {
        ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        AtomicInteger liveCount = new AtomicInteger(13);
        ProfilingInfo live = (ProfilingInfo) Proxy.newProxyInstance(ProfilingInfo.class.getClassLoader(), new Class<?>[]{ProfilingInfo.class}, (proxy, query, arguments) -> {
            Assert.assertEquals("getDeoptimizationCount", query.getName());
            return liveCount.get();
        });
        StableProfileProvider recording = new StableProfileProvider() {
            @Override
            public ProfilingInfo deriveForContext(NodeSourcePosition context, ResolvedJavaMethod callee, boolean includeNormal, boolean includeOSR) {
                return live;
            }
        };
        ProfilingInfo cached = recording.getProfilingInfo(null, method);
        Assert.assertEquals(13, cached.getDeoptimizationCount(DeoptimizationReason.NullCheckException));
        liveCount.set(0);
        Assert.assertEquals(0, cached.getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
        liveCount.set(99);
        Assert.assertEquals(13, cached.getDeoptimizationCount(DeoptimizationReason.NullCheckException));
        Assert.assertEquals(0, cached.getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));

        EconomicMap<String, Object> data = recording.recordProfiles(EconomicMap.create(), null, NON_LAMBDA_FORMATTER);
        try (TemporaryDirectory temp = new TemporaryDirectory("DeoptimizationProfileReplay")) {
            Path path = Path.of(temp.toString(), "counts.glog");
            try (JsonPrettyWriter writer = new JsonPrettyWriter(new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8)))) {
                writer.print(data);
            }
            Object parsed;
            try (FileReader reader = new FileReader(path.toFile(), StandardCharsets.UTF_8)) {
                parsed = new JsonParser(reader).parse();
            }
            StableProfileProvider replay = new StableProfileProvider().load(parsed, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
            replay.freeze();
            ProfilingInfo loaded = replay.getProfilingInfo(null, method);
            Assert.assertEquals(13, loaded.getDeoptimizationCount(DeoptimizationReason.NullCheckException));
            Assert.assertEquals(0, loaded.getDeoptimizationCount(DeoptimizationReason.BoundsCheckException));
            InternalError error = Assert.assertThrows(InternalError.class, () -> loaded.getDeoptimizationCount(DeoptimizationReason.ArithmeticException));
            Assert.assertTrue(error.getMessage().contains("getDeoptimizationCount(ArithmeticException)"));
        }
    }

    @Test
    public void testStableSwitchProbabilities() {
        ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        BytecodeStream stream = new BytecodeStream(method.getCode());
        while (stream.currentBC() != Bytecodes.TABLESWITCH && stream.currentBC() != Bytecodes.LOOKUPSWITCH) {
            Assert.assertNotEquals(Bytecodes.END, stream.currentBC());
            stream.next();
        }
        int bci = stream.currentBCI();
        Assert.assertTrue(bci > 0);
        for (double[] expected : new double[][]{null, {0.1, 0.2, 0.3, 0.4}}) {
            AtomicInteger liveQueries = new AtomicInteger();
            ProfilingInfo live = (ProfilingInfo) Proxy.newProxyInstance(ProfilingInfo.class.getClassLoader(), new Class<?>[]{ProfilingInfo.class}, (proxy, query, arguments) -> {
                if (query.getName().equals("getSwitchProbabilities")) {
                    liveQueries.incrementAndGet();
                    return expected;
                }
                return query.invoke(method.getProfilingInfo(), arguments);
            });
            StableProfileProvider recording = new StableProfileProvider() {
                @Override
                public ProfilingInfo deriveForContext(NodeSourcePosition context, ResolvedJavaMethod callee, boolean includeNormal, boolean includeOSR) {
                    return live;
                }
            };
            Assert.assertArrayEquals(expected, recording.getProfilingInfo(null, method).getSwitchProbabilities(bci), 0);
            var data = recording.recordProfiles(EconomicMap.create(), null, NON_LAMBDA_FORMATTER);
            StableProfileProvider replay = new StableProfileProvider().load(data, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
            replay.freeze();
            ProfilingInfo frozen = replay.getProfilingInfo(null, method);
            Assert.assertArrayEquals(expected, frozen.getSwitchProbabilities(bci), 0);
            Assert.assertEquals(1, liveQueries.get());
            Assert.assertThrows(InternalError.class, () -> frozen.getSwitchProbabilities(0));
        }
    }

    @Test
    public void testLegacyDeoptimizationCounts() {
        ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        StableProfileProvider recording = new StableProfileProvider();
        recording.getProfilingInfo(null, method);
        EconomicMap<String, Object> legacy = recording.recordProfiles(EconomicMap.create(), null, NON_LAMBDA_FORMATTER);
        @SuppressWarnings("unchecked")
        EconomicMap<String, Object> legacyMethod = (EconomicMap<String, Object>) ((List<?>) legacy.get("profiles")).getFirst();
        legacyMethod.removeKey("includeNormal");
        legacyMethod.removeKey("includeOSR");

        StableProfileProvider permissive = new StableProfileProvider().load(legacy, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
        Assert.assertEquals(method.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.ArithmeticException),
                        permissive.getProfilingInfo(null, method).getDeoptimizationCount(DeoptimizationReason.ArithmeticException));

        StableProfileProvider strict = new StableProfileProvider().load(legacy, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
        strict.freeze();
        Assert.assertThrows(InternalError.class, () -> strict.getProfilingInfo(null, method).getDeoptimizationCount(DeoptimizationReason.ArithmeticException));
    }

    @Test
    public void testDistinctProfileViews() {
        ResolvedJavaMethod method = getResolvedJavaMethod("foo");
        StableProfileProvider recording = new StableProfileProvider() {
            @Override
            public ProfilingInfo deriveForContext(NodeSourcePosition context, ResolvedJavaMethod callee, boolean includeNormal, boolean includeOSR) {
                int count = includeOSR ? 13 : 7;
                return (ProfilingInfo) Proxy.newProxyInstance(ProfilingInfo.class.getClassLoader(), new Class<?>[]{ProfilingInfo.class}, (proxy, query, arguments) -> {
                    Assert.assertEquals("getDeoptimizationCount", query.getName());
                    return count;
                });
            }
        };
        Assert.assertEquals(7, recording.getProfilingInfo(null, method, true, false).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        Assert.assertEquals(13, recording.getProfilingInfo(null, method, true, true).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        EconomicMap<String, Object> data = recording.recordProfiles(EconomicMap.create(), null, NON_LAMBDA_FORMATTER);
        StableProfileProvider partial = new StableProfileProvider().load(data, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
        partial.freeze();
        Assert.assertEquals(7, partial.getProfilingInfo(null, method, true, false).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        EconomicMap<String, Object> saved = partial.recordProfiles(EconomicMap.create(), null, NON_LAMBDA_FORMATTER);
        StableProfileProvider savedReplay = new StableProfileProvider().load(saved, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
        savedReplay.freeze();
        Assert.assertEquals(7, savedReplay.getProfilingInfo(null, method, true, false).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        StableProfileProvider replay = new StableProfileProvider().load(data, method.getDeclaringClass(), false, NON_LAMBDA_FORMATTER);
        replay.freeze();
        Assert.assertEquals(7, replay.getProfilingInfo(null, method, true, false).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        Assert.assertEquals(13, replay.getProfilingInfo(null, method, true, true).getDeoptimizationCount(DeoptimizationReason.UnreachedCode));
        Assert.assertThrows(InternalError.class, () -> replay.getProfilingInfo(null, method, false, true));
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
