/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Consumer;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.impl.TruffleVersions;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.runtime.ModulesSupport;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalTruffleCompilationSupport;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.services.Services;
import org.graalvm.home.Version;

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
        Module javaBase = layer.findModule("java.base").orElseThrow();
        Module compilerModule = layer.findModule("jdk.graal.compiler").or(() -> layer.findModule("jdk.internal.vm.compiler")).orElse(null);
        /*
         * The Graal compiler uses jdk.internal.misc.Unsafe. On non-Graal JDKs, it is necessary to
         * export `jdk.internal.misc` to the compiler. On Graal JDK, this export is already included
         * during the GraalVM build process by jlink. The export must be added before initializing
         * the JVMCI runtime by calling JVMCI.getRuntime().
         */
        if (compilerModule != null) {
            ModulesSupport.addExports(javaBase, "jdk.internal.misc", compilerModule);
        }
        HotSpotJVMCIRuntime hsRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(hsRuntime.getConfigStore());
        boolean useCompiler = config.getFlag("UseCompiler", Boolean.class);
        if (!useCompiler) {
            // compilation disabled in host VM -> fallback to default runtime
            return new DefaultTruffleRuntime("JVMCI compilation was disabled on this JVM. Pass -XX:+EnableJVMCI as a virtual machine argument to the java executable to resolve this.");
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
            if (TruffleVersions.isVersionCheckEnabled()) {
                Version truffleVersion = TruffleVersions.TRUFFLE_API_VERSION;
                if (truffleVersion.compareTo(TruffleVersions.NEXT_VERSION_UPDATE) >= 0) {
                    throw new AssertionError("MIN_COMPILER_VERSION, MIN_JDK_VERSION and MAX_JDK_VERSION must be updated!");
                }
                Version truffleMajorMinorVersion = stripUpdateVersion(truffleVersion);
                Version compilerVersion = getCompilerVersion(compilationSupport);
                Version compilerMajorMinorVersion = stripUpdateVersion(compilerVersion);
                int jdkFeatureVersion = Runtime.version().feature();
                if (jdkFeatureVersion < TruffleVersions.MIN_JDK_VERSION || jdkFeatureVersion >= TruffleVersions.MAX_JDK_VERSION) {
                    return new DefaultTruffleRuntime(formatVersionWarningMessage("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    The Java runtime version must be greater or equal to JDK '%d' and smaller than JDK '%d'.
                                    Update your Java runtime to resolve this.
                                    """, Runtime.version(), compilerVersion, truffleVersion, TruffleVersions.MIN_JDK_VERSION, TruffleVersions.MAX_JDK_VERSION));
                } else if (compilerMajorMinorVersion.compareTo(truffleMajorMinorVersion) > 0) {
                    /*
                     * Forward compatibility is supported only for minor updates, not for major
                     * releases.
                     */
                    return new DefaultTruffleRuntime(formatVersionWarningMessage("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    Update the org.graalvm.polyglot versions to at least '%s' to resolve this.
                                    """, Runtime.version(), compilerVersion, truffleVersion, compilerVersion));
                } else if (compilerVersion.compareTo(TruffleVersions.MIN_COMPILER_VERSION) < 0) {
                    return new DefaultTruffleRuntime(formatVersionWarningMessage("""
                                    Your Java runtime '%s' with compiler version '%s' is incompatible with polyglot version '%s'.
                                    Update the Java runtime to the latest update release of JDK '%d'.
                                    """, Runtime.version(), compilerVersion, truffleVersion, jdkFeatureVersion));
                }
            }
        } else {
            // try jar graal
            try {
                if (compilerModule == null) {
                    // jargraal compiler module not found -> fallback to default runtime
                    return new DefaultTruffleRuntime(
                                    "Libgraal compilation is not available on this JVM. Alternatively, the org.graalvm.compiler:compiler module can be put on the --upgrade-module-path.");
                }
                String pkg = getTruffleGraalHotSpotPackage(compilerModule);
                ModulesSupport.addExports(compilerModule, pkg, runtimeModule);
                Class<?> hotspotCompilationSupport = Class.forName(compilerModule, pkg + ".HotSpotTruffleCompilationSupport");
                compilationSupport = (TruffleCompilationSupport) hotspotCompilationSupport.getConstructor().newInstance();
                if (TruffleVersions.isVersionCheckEnabled()) {
                    String jvmciVersionCheckError = verifyJVMCIVersion(compilationSupport.getClass());
                    if (jvmciVersionCheckError != null) {
                        return new DefaultTruffleRuntime(jvmciVersionCheckError);
                    }
                    Version truffleVersion = TruffleVersions.TRUFFLE_API_VERSION;
                    Version truffleMajorMinorVersion = stripUpdateVersion(truffleVersion);
                    Version compilerVersion = getCompilerVersion(compilationSupport);
                    Version compilerMajorMinorVersion = stripUpdateVersion(compilerVersion);
                    if (!compilerMajorMinorVersion.equals(truffleMajorMinorVersion)) {
                        return new DefaultTruffleRuntime(formatVersionWarningMessage("""
                                        The Graal compiler version '%s' is incompatible with polyglot version '%s'.
                                        Update the compiler version to '%s' to resolve this.
                                        """, compilerVersion, truffleVersion, truffleVersion));
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }
        /*
         * Ensure that HotSpotThreadLocalHandshake and HotSpotFastThreadLocal are loaded before the
         * hooks are called. This prevents class initialization during the virtual thread hooks
         * These hooks must not trigger class loading or suspend the VirtualThread (per their
         * specification).
         */
        HotSpotThreadLocalHandshake.initializePendingOffset();
        HotSpotFastThreadLocal.ensureLoaded();
        HotSpotTruffleRuntime rt = new HotSpotTruffleRuntime(compilationSupport);
        registerVirtualThreadMountHooks();
        compilationSupport.registerRuntime(rt);
        return rt;
    }

    /**
     * Triggers verification of JVMCI.
     */
    private static String verifyJVMCIVersion(Class<?> hotspotCompilationSupport) {
        /*
         * The TruffleCompilationSupport is present in both the maven artifact
         * org.graalvm.truffle/truffle-compiler and the JDK org.graalvm.truffle.compiler module. The
         * JDK version of TruffleCompilationSupport may be outdated and lack the verifyJVMCIVersion
         * method. To address this, we use reflection.
         */
        String errorMessage = null;
        try {
            Method verifyJVMCIVersion = hotspotCompilationSupport.getDeclaredMethod("verifyJVMCIVersion");
            errorMessage = (String) verifyJVMCIVersion.invoke(null);
        } catch (NoSuchMethodException noMethod) {
            // pass with result set to true
        } catch (ReflectiveOperationException e) {
            throw new InternalError(e);
        }
        return errorMessage;
    }

    /**
     * Retrieves the compiler version from the provided {@link TruffleCompilationSupport} instance
     * using reflection. If the method is unavailable, a fallback version of 23.1.1 is returned.
     */
    public static Version getCompilerVersion(TruffleCompilationSupport compilationSupport) {
        Version version = null;
        try {
            version = Version.parse(compilationSupport.getCompilerVersion());
        } catch (NoSuchMethodError noMethod) {
            /*
             * The TruffleCompilationSupport is present in both the maven artifact
             * org.graalvm.truffle/truffle-compiler and the JDK org.graalvm.truffle.compiler module.
             * The JDK version of TruffleCompilationSupport may be outdated and lack the
             * getCompilerVersion method. To address this, we use reflection.
             */
            version = Version.create(23, 1, 1);
        }
        return version;
    }

    private static Version stripUpdateVersion(Version version) {
        int major = version.getComponent(0);
        int minor = version.getComponent(1);
        if (major == 0 && minor == 0) {
            /*
             * Version represents a pure snapshot version without any numeric component.
             */
            return version;
        } else {
            return Version.create(major, minor);
        }
    }

    private static String formatVersionWarningMessage(String errorFormat, Object... args) {
        StringBuilder errorMessage = new StringBuilder("Version check failed.\n");
        errorMessage.append(String.format(errorFormat, args));
        errorMessage.append("""
                        To disable this version check the '-Dpolyglotimpl.DisableVersionChecks=true' system property can be used.
                        It is not recommended to disable version checks.
                        """);
        return errorMessage.toString();
    }

    private static void registerVirtualThreadMountHooks() {
        Consumer<Thread> onMount = (t) -> {
            HotSpotFastThreadLocal.mount();
            HotSpotThreadLocalHandshake.setPendingFlagForVirtualThread();
        };
        Consumer<Thread> onUmount = (t) -> HotSpotFastThreadLocal.unmount();
        ModulesSupport.getJavaLangSupport().registerVirtualThreadMountHooks(onMount, onUmount);
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
