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

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.compiler.TruffleCompilationSupport;
import com.oracle.truffle.runtime.ModulesSupport;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraal;
import com.oracle.truffle.runtime.hotspot.libgraal.LibGraalTruffleCompilationSupport;

import jdk.internal.module.Modules;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.services.Services;

import java.util.Set;

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
        ModuleLayer layer = HotSpotTruffleRuntimeAccess.class.getModule().getLayer();
        Module truffleCompilerModule = layer.findModule("org.graalvm.truffle.compiler").orElse(null);
        if (truffleCompilerModule == null) {
            return new DefaultTruffleRuntime("Truffle compiler module is missing. This is likely an installation error.");
        }
        for (String pack : truffleCompilerModule.getPackages()) {
            Modules.addExports(truffleCompilerModule, pack, HotSpotTruffleRuntimeAccess.class.getModule());
        }
        TruffleCompilationSupport compilationSupport;
        if (LibGraal.isAvailable()) {
            // try LibGraal
            compilationSupport = new LibGraalTruffleCompilationSupport();
        } else {
            // try jar graal
            try {
                Module compilerModule = layer.findModule("jdk.graal.compiler").or(() -> layer.findModule("jdk.internal.vm.compiler")).orElse(null);
                if (compilerModule == null) {
                    // jargraal compiler module not found -> fallback to default runtime
                    return new DefaultTruffleRuntime(
                                    "Libgraal compilation is not available on this JVM. Alternatively, the org.graalvm.compiler:compiler module can be put on the --upgrade-module-path.");
                }
                /*
                 * That the compiler has a qualified export to Truffle may not be enough if truffle
                 * is running in an isolated module layer.
                 */
                String pkg = getTruffleGraalHotSpotPackage(compilerModule);
                Modules.addExports(compilerModule, pkg, HotSpotTruffleRuntimeAccess.class.getModule());
                Class<?> hotspotCompilationSupport = Class.forName(compilerModule, pkg + ".HotSpotTruffleCompilationSupport");
                compilationSupport = (TruffleCompilationSupport) hotspotCompilationSupport.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }
        HotSpotTruffleRuntime rt = new HotSpotTruffleRuntime(compilationSupport);
        compilationSupport.registerRuntime(rt);
        return rt;
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
