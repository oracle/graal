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
                return new DefaultTruffleRuntime("JVMCI is not enabled on this JVM. JVMCI may be enabled using -XX:+EnableJVMCI.");
            }
            throw e;
        }
        HotSpotJVMCIRuntime hsRuntime = (HotSpotJVMCIRuntime) JVMCI.getRuntime();
        HotSpotVMConfigAccess config = new HotSpotVMConfigAccess(hsRuntime.getConfigStore());
        boolean useCompiler = config.getFlag("UseCompiler", Boolean.class);
        if (!useCompiler) {
            // compilation disabled in host VM -> fallback to default runtime
            return new DefaultTruffleRuntime("JVMCI compilation was disabled on this JVM. JVMCI may be enabled using -XX:+EnableJVMCI.");
        }
        TruffleCompilationSupport compilationSupport;
        if (LibGraal.isAvailable()) {
            // try LibGraal
            compilationSupport = new LibGraalTruffleCompilationSupport();
        } else {
            // try jar graal
            try {
                Module compilerModule = HotSpotTruffleRuntimeAccess.class.getModule().getLayer().findModule("jdk.internal.vm.compiler").orElse(null);
                if (compilerModule == null) {
                    // jargraal compiler module not found -> fallback to default runtime
                    return new DefaultTruffleRuntime(
                                    "Libgraal compilation is not available on this JVM. Alternatively, the compiler module jdk.internal.vm.compiler was not found on the --upgrade-module-path.");
                }
                Modules.addExports(compilerModule, "org.graalvm.compiler.truffle.compiler.hotspot", HotSpotTruffleRuntimeAccess.class.getModule());
                Class<?> hotspotCompilationSupport = Class.forName("org.graalvm.compiler.truffle.compiler.hotspot.HotSpotTruffleCompilationSupport");
                compilationSupport = (TruffleCompilationSupport) hotspotCompilationSupport.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new InternalError(e);
            }
        }
        HotSpotTruffleRuntime rt = new HotSpotTruffleRuntime(compilationSupport);
        compilationSupport.registerRuntime(rt);
        return rt;
    }

}
