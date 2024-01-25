/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.hotspot;

import static com.oracle.truffle.runtime.OptimizedTruffleRuntime.MAX_JDK_VERSION;
import static com.oracle.truffle.runtime.OptimizedTruffleRuntime.MIN_COMPILER_VERSION;
import static com.oracle.truffle.runtime.OptimizedTruffleRuntime.MIN_JDK_VERSION;
import static com.oracle.truffle.runtime.OptimizedTruffleRuntime.NEXT_VERSION_UPDATE;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import org.graalvm.home.Version;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.polyglot.PolyglotImpl;
import com.oracle.truffle.runtime.ModulesSupport;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalTruffleCompilationSupport;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.services.Services;

public final class HotSpotTruffleRuntimeAccess implements TruffleRuntimeAccess {

    public HotSpotTruffleRuntimeAccess() {
    }

    @Override
    public TruffleRuntime getRuntime() {
        return createRuntime();
    }

    @Override
    public int getPriority() {
        return 0;
    }

    protected static TruffleRuntime createRuntime() {
        String reason = ModulesSupport.exportJVMCI(HotSpotTruffleRuntimeAccess.class);
        if (reason != null) {
            return new DefaultTruffleRuntime(reason);
        }
        try {
            Services.initializeJVMCI();
        } catch (Error e) {
            // hack to detect the exact error that is thrown when
            if (e.getClass() == Error.class && e.getMessage().startsWith("The EnableJVMCI VM option must be true")) {
                return new DefaultTruffleRuntime(
                                "JVMCI is required to enable optimizations. Pass -XX:+EnableJVMCI as a virtual machine argument to the java executable to resolve this. This is necessary on JVMs that do not enable JVMCI by default.");
            }
            throw e;
        }
        HotSpotJVMCIRuntime hsRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(hsRuntime.getConfigStore());
        boolean useCompiler = config.getFlag("UseCompiler", Boolean.class);
        if (!useCompiler) {
            // compilation disabled in host VM -> fallback to default runtime
            return new DefaultTruffleRuntime("JVMCI compilation was disabled on this JVM. Pass -XX:+EnableJVMCI as a virtual machine argument to the java executable to resolve this.");
        }
        /*
         * Module integrity rules ignore qualified exports from a parent module layer to a child
         * module layer by default. This is why we do need to explicitly export the truffle.compiler
         * module to truffle.runtime to be able to access it in the Truffle runtime module which may
         * be spawned on a different module layer due to class loading isolation.
         *
         * We also need to do this for LibGraal as the truffle.compiler module may be installed as
         * part of the JDK if the JDK supports running with jar graal as well.
         */
        Module runtimeModule = HotSpotTruffleRuntimeAccess.class.getModule();
        ModuleLayer layer;
        if (runtimeModule.isNamed()) {
            layer = runtimeModule.getLayer();
        } else {
            layer = ModuleLayer.boot();
        }
        /*
         * If the compiler module is installed as part of the JDK we need to explicitly export it.
         */
        Module truffleCompilerModule = layer.findModule("org.graalvm.truffle.compiler").orElse(null);
        if (truffleCompilerModule != null) {
            ModulesSupport.exportJVMCI(truffleCompilerModule);
            for (String pack : truffleCompilerModule.getPackages()) {
                ModulesSupport.addExports(truffleCompilerModule, pack, runtimeModule);
            }
        }

        TruffleCompilationSupport compilationSupport;
        if (LibGraal.isAvailable()) {
            // try LibGraal
            compilationSupport = new LibGraalTruffleCompilationSupport();
            if (!Boolean.getBoolean("polyglotimpl.DisableVersionChecks")) {
                Version truffleVersion = getTruffleVersion();
                if (truffleVersion.compareTo(NEXT_VERSION_UPDATE) >= 0) {
                    throw new AssertionError("MIN_COMPILER_VERSION, MIN_JDK_VERSION and MAX_JDK_VERSION must be updated!");
                }
                Version compilerVersion = getCompilerVersion(compilationSupport);
                int jdkFeatureVersion = Runtime.version().feature();
                if (jdkFeatureVersion < MIN_JDK_VERSION || jdkFeatureVersion >= MAX_JDK_VERSION) {
                    throw throwVersionError("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    The Java runtime version must be greater or equal to JDK '%d' and smaller than JDK '%d'.
                                    Update your Java runtime to resolve this.
                                    """, Runtime.version(), compilerVersion, truffleVersion, MIN_JDK_VERSION, MAX_JDK_VERSION);
                } else if (compilerVersion.compareTo(truffleVersion) > 0) {
                    // no forward compatibility
                    throw throwVersionError("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    Update the org.graalvm.polyglot versions to at least '%s' to resolve this.
                                    """, Runtime.version(), compilerVersion, truffleVersion, compilerVersion);
                } else if (compilerVersion.compareTo(MIN_COMPILER_VERSION) < 0) {
                    throw throwVersionError("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    Update the Java runtime to the latest update release of JDK '%d'.
                                    """, Runtime.version(), compilerVersion, truffleVersion, jdkFeatureVersion);
                }
            }
        } else {
            // try jar graal
            try {
                Module compilerModule = layer.findModule("jdk.graal.compiler").or(() -> layer.findModule("jdk.internal.vm.compiler")).orElse(null);
                if (compilerModule == null) {
                    // jargraal compiler module not found -> fallback to default runtime
                    return new DefaultTruffleRuntime(
                                    "Libgraal compilation is not available on this JVM. Alternatively, the org.graalvm.compiler:compiler module can be put on the --upgrade-module-path.");
                }
                String pkg = getTruffleGraalHotSpotPackage(compilerModule);
                ModulesSupport.addExports(compilerModule, pkg, runtimeModule);
                Class<?> hotspotCompilationSupport = Class.forName(compilerModule, pkg + ".HotSpotTruffleCompilationSupport");
                compilationSupport = (TruffleCompilationSupport) hotspotCompilationSupport.getConstructor().newInstance();
                if (!Boolean.getBoolean("polyglotimpl.DisableVersionChecks")) {
                    Version truffleVersion = getTruffleVersion();
                    Version compilerVersion = getCompilerVersion(compilationSupport);
                    if (!compilerVersion.equals(truffleVersion)) {
                        throw throwVersionError("""
                                        The Graal compiler version '%s' is incompatible with polyglot version '%s'.
                                        Update the compiler version to '%s' to resolve this.
                                        """, compilerVersion, truffleVersion, truffleVersion);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }
        HotSpotTruffleRuntime rt = new HotSpotTruffleRuntime(compilationSupport);
        compilationSupport.registerRuntime(rt);
        return rt;
    }

    private static RuntimeException throwVersionError(String errorFormat, Object... args) {
        StringBuilder errorMessage = new StringBuilder("Polyglot version compatibility check failed.\n");
        errorMessage.append(String.format(errorFormat, args));
        errorMessage.append("""
                        Alternatively, it is possible to switch to the fallback runtime with -Dtruffle.UseFallbackRuntime=true.
                        The fallback runtime is compatible to any Java 17 capable JDK.
                        Execution with the fallback runtime does not support runtime compilation and therefore will negatively impact the guest application performance.
                        For more information see: https://www.graalvm.org/latest/reference-manual/embed-languages/#runtime-optimization-support.
                        To disable this version check the '-Dpolyglotimpl.DisableVersionChecks=true' system property can be used.
                        It is not recommended to disable version checks.
                        """);
        throw new IllegalStateException(errorMessage.toString());
    }

    /**
     * Reads reflectively the org.graalvm.truffle module version. The method uses reflection to
     * access the {@code PolyglotImpl#TRUFFLE_VERSION} field because the Truffle API may be of a
     * version earlier than graalvm-23.1.2 where the field does not exist.
     *
     * @return the Truffle API version or 23.1.1 if the {@code PolyglotImpl#TRUFFLE_VERSION} field
     *         does not exist.
     */
    private static Version getTruffleVersion() {
        try {
            Field versionField = PolyglotImpl.class.getDeclaredField("TRUFFLE_VERSION");
            versionField.setAccessible(true);
            return Version.parse((String) versionField.get(null));
        } catch (NoSuchFieldException nf) {
            return Version.create(23, 1, 1);
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
    }

    private static Version getCompilerVersion(TruffleCompilationSupport compilationSupport) {
        /*
         * The TruffleCompilationSupport is present in both the maven artifact
         * org.graalvm.truffle/truffle-compiler and the JDK org.graalvm.truffle.compiler module. The
         * JDK version of TruffleCompilationSupport may be outdated and lack the getCompilerVersion
         * method. To address this, we use reflection.
         */
        String compilerVersionString = null;
        try {
            Method getCompilerVersion = compilationSupport.getClass().getMethod("getCompilerVersion");
            compilerVersionString = (String) getCompilerVersion.invoke(compilationSupport);
        } catch (NoSuchMethodException noMethod) {
            // pass with compilerVersionString set to null
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
        return compilerVersionString != null ? Version.parse(compilerVersionString) : Version.create(23, 1, 1);
    }

    /**
     * Handle history of renamings applied to Graal.
     */
    private static String getTruffleGraalHotSpotPackage(Module compilerModule) {
        String[] history = {
                        "jdk.graal.compiler.truffle.hotspot",
                        "org.graalvm.compiler.truffle.compiler.hotspot"
        };
        Set<String> packages = compilerModule.getPackages();
        for (String name : history) {
            if (packages.contains(name)) {
                return name;
            }
        }
        throw new InternalError(String.format("Cannot find package containing Truffle runtime in %s module (names searched: %s)",
                        compilerModule.getName(), String.join(", ", history)));
    }
}
