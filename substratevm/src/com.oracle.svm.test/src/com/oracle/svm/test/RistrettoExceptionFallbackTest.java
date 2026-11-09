/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import java.lang.reflect.Method;
import java.io.InputStream;

import org.junit.Test;
import org.junit.Assume;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import com.oracle.svm.interpreter.InterpreterKnownCompiledEntryPoints;
import com.oracle.svm.interpreter.metadata.CremaMethodAccess;
import com.oracle.svm.interpreter.ristretto.RistrettoDirectives;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoParser;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import jdk.graal.compiler.nodes.InvokeWithExceptionNode;

/** Verifies compiled calls and local exception edges for runtime-loaded bytecode. */
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions", "-H:+RuntimeClassLoading", "-H:+GraalJITCompileAtRuntime",
                "--add-opens=org.graalvm.nativeimage.builder/com.oracle.svm.interpreter.ristretto.compile=ALL-UNNAMED",
                "--features=com.oracle.svm.test.RistrettoExceptionFallbackTest$ProbeFeature"
})
public final class RistrettoExceptionFallbackTest {
    public static final class ProbeFeature implements Feature {
        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            access.getResourceAccess().register(AccessCondition.unconditional(), RistrettoExceptionFallbackTest.class.getModule(),
                            "com/oracle/svm/test/RistrettoExceptionFallbackPayload*.class");
        }

        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            try {
                RuntimeReflection.register(SentinelTarget.class);
                RuntimeReflection.registerAsQueried(SentinelTarget.class.getDeclaredMethod("uncompiled"));
                try {
                    RuntimeReflection.register(RistrettoParser.class.getDeclaredMethod("hasCompiledEntry", ResolvedJavaMethod.class));
                } catch (NoSuchMethodException baselineWithoutFallback) {
                    // The identical test payload is also used on the upstream baseline.
                }
                RuntimeReflection.register(Object.class.getDeclaredMethod("clone"));
                RuntimeReflection.register(RistrettoDirectives.class);
                RuntimeReflection.register(RistrettoDirectives.class.getDeclaredMethod("inRuntimeCompiledCode"));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private static final class PayloadLoader extends ClassLoader {
        PayloadLoader() {
            super(RistrettoExceptionFallbackTest.class.getClassLoader());
        }

        Class<?> define(String name) throws Exception {
            try (InputStream input = RistrettoExceptionFallbackTest.class.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
                check(input != null, "Payload bytes must be available");
                byte[] bytes = input.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @Test
    public void testRuntimeCloneUnderCatch() throws Exception {
        Assume.assumeTrue("requires native Ristretto", ImageInfo.inImageRuntimeCode());
        PayloadLoader loader = new PayloadLoader();
        Class<?> base = loader.define("com.oracle.svm.test.RistrettoExceptionFallbackPayload");
        Class<?> copyable = loader.define("com.oracle.svm.test.RistrettoExceptionFallbackPayload$Copyable");
        check(base.getClassLoader() == loader && copyable.getClassLoader() == loader, "Payload must be runtime-loaded");

        var cloneMetadata = CremaMethodAccess.toJVMCI(Object.class.getDeclaredMethod("clone"));
        check(cloneMetadata.hasNativeEntryPoint(), "Object.clone must have a holder");
        check(cloneMetadata.getNativeEntryPoint().isNonNull(), "Object.clone must have a nonnull entry");
        check(!cloneMetadata.getNativeEntryPoint().equal(InterpreterKnownCompiledEntryPoints.getMethodNotCompiledHandler()),
                        "Object.clone must have a real AOT entry, not the uncompiled sentinel");

        Object source = copyable.getConstructor().newInstance();
        Object nonCloneable = base.getConstructor().newInstance();
        Method method = base.getMethod("cloneUnderCatch");
        check("success-interpreted".equals(method.invoke(source)), "warmup clone must execute interpreted");
        check("caught-interpreted".equals(method.invoke(nonCloneable)), "warmup catch must execute interpreted");
        RistrettoMethod runtimeMethod = RistrettoMethod.getOrCreate(CremaMethodAccess.toJVMCI(method));
        var graph = RistrettoUtils.parseOnly(runtimeMethod);
        check(graph != null, "parseOnly must return a graph");
        boolean foundCloneEdge = false;
        for (InvokeWithExceptionNode invoke : graph.getNodes().filter(InvokeWithExceptionNode.class)) {
            if (invoke.callTarget().targetMethod() != null && invoke.callTarget().targetMethod().getName().equals("clone")) {
                check(invoke.exceptionEdge() != null, "clone invoke must retain its exception successor");
                foundCloneEdge = true;
            }
        }
        check(foundCloneEdge, "clone must be an ordinary InvokeWithExceptionNode");
        var installed = RistrettoUtils.compileAndInstallInCrema(runtimeMethod);
        check(installed != null && installed.isAlive(), "Caller must compile and install successfully");
        check(runtimeMethod.installedCode == installed, "Installed code must be published on the runtime method");

        Object copy = method.invoke(source);
        check(copy != source && copy.getClass() == copyable, "Compiled clone must produce a distinct object of the same runtime class");
        check(base.getField("value").getInt(copy) == 42, "Clone must copy fields");
        check("caught-compiled".equals(method.invoke(nonCloneable)), "Compiled clone exception must reach the local catch; directive must be true");
        check(installed.isAlive(), "Caller must remain installed after both executions");

    }

    public static final class SentinelTarget {
        public static int uncompiled() {
            return 42;
        }
    }

    @Test
    public void testUncompiledEntryIsRejected() throws Exception {
        Assume.assumeTrue("requires native Ristretto", ImageInfo.inImageRuntimeCode());
        Method guard;
        try {
            guard = RistrettoParser.class.getDeclaredMethod("hasCompiledEntry", ResolvedJavaMethod.class);
        } catch (NoSuchMethodException baselineWithoutFallback) {
            Assume.assumeNoException("upstream baseline has no fallback guard", baselineWithoutFallback);
            return;
        }
        guard.setAccessible(true);
        // A constant reflection lookup would itself make the target invocable at image build time.
        String methodName = System.getProperty("ristretto.test.sentinelMethod", "uncompiled");
        var metadata = CremaMethodAccess.toJVMCI(SentinelTarget.class.getDeclaredMethod(methodName));
        check(metadata.hasNativeEntryPoint(), "test must exercise an existing entry-point holder");
        check(metadata.getNativeEntryPoint().equal(InterpreterKnownCompiledEntryPoints.getMethodNotCompiledHandler()),
                        "test target must carry the actual not-compiled sentinel");
        check(Boolean.FALSE.equals(guard.invoke(null, metadata)), "sentinel must not be callable");
        check(Boolean.FALSE.equals(guard.invoke(null, RistrettoMethod.getOrCreate(metadata))), "wrapped sentinel must not be callable");
    }
}
