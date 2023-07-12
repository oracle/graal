/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.compiler.TruffleCompiler;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.TruffleCallBoundary;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotNmethod;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A HotSpot specific {@link OptimizedCallTarget} whose machine code (if any) is represented by an
 * associated {@link InstalledCode}.
 */
public final class HotSpotOptimizedCallTarget extends OptimizedCallTarget {

    /**
     * Initial value for {@link #installedCode}.
     */
    private static final InstalledCode INVALID_CODE = new InstalledCode(null);

    /**
     * This field is read by the code injected by {@code TruffleCallBoundaryInstrumentationFactory}
     * into a method annotated by {@link TruffleCallBoundary}. The injected code assumes this field
     * is never null hence the use of {@link #INVALID_CODE}.
     *
     * Note: the only thread that writes to this field is the compilation thread. Thus, non-volatile
     * publication is safe if and only if the compiler thread makes sure that the writes to the
     * fields of the InstalledCode object are synchronized. One of those fields is name, which is
     * final, so this is ensured. Other fields are written behind the JNI calls (in the VM's native
     * code), which should ensure safe publication: these fields are version, address and
     * entryPoint. Therefore, any thread that sees an InstalledCode object through the installedCode
     * field, will also see all properly initialized fields, thus there is no need for a volatile
     * flag.
     */
    private InstalledCode installedCode;

    public HotSpotOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(sourceCallTarget, rootNode);
        this.installedCode = INVALID_CODE;
    }

    public HotSpotOptimizedCallTarget(EngineData engine) {
        super(engine);
        this.installedCode = INVALID_CODE;
    }

    /**
     * Reflective reference to {@code HotSpotNmethod.setSpeculationLog} so that this code can be
     * compiled against older JVMCI API.
     */
    private static final Method setSpeculationLog;

    /**
     * Reflective reference to {@code InstalledCode.invalidate(boolean deoptimize)} so that this
     * code can be compiled against older JVMCI API.
     */
    @SuppressWarnings("unused") private static final Method invalidateInstalledCode;

    static {
        Method method = null;
        try {
            method = HotSpotNmethod.class.getDeclaredMethod("setSpeculationLog", HotSpotSpeculationLog.class);
        } catch (NoSuchMethodException e) {
        }
        setSpeculationLog = method;
        method = null;
        try {
            method = InstalledCode.class.getDeclaredMethod("invalidate", boolean.class);
        } catch (NoSuchMethodException e) {
        }
        invalidateInstalledCode = method;
    }

    /**
     * This method may only be called during compilation, and only by the compiling thread.
     */
    public void setInstalledCode(InstalledCode code) {
        assert code != null : "code must never become null";
        InstalledCode oldCode = this.installedCode;
        if (oldCode == code) {
            return;
        }

        if (oldCode != INVALID_CODE && invalidateInstalledCode != null) {
            try {
                invalidateInstalledCode.invoke(oldCode, false);
            } catch (Error e) {
                throw e;
            } catch (Throwable throwable) {
                throw new InternalError(throwable);
            }
        }

        // A default nmethod can be called from entry points in the VM (e.g., Method::_code)
        // and so allowing it to be installed here would invalidate the truth of
        // `soleExecutionEntryPoint`
        if (code instanceof HotSpotNmethod) {
            HotSpotNmethod nmethod = (HotSpotNmethod) code;
            if (nmethod.isDefault()) {
                throw new IllegalArgumentException("Cannot install a default nmethod for a " + getClass().getSimpleName());
            }
            tetherSpeculationLog(nmethod);
        }

        this.installedCode = code;
    }

    /**
     * Tethers this object's speculation log with {@code nmethod} if the log has speculations and
     * manages its failed speculation list. This maintains the invariant described by
     * {@link HotSpotTruffleRuntime#createSpeculationLog}.
     */
    private void tetherSpeculationLog(HotSpotNmethod nmethod) throws Error, InternalError {
        if (setSpeculationLog != null) {
            if (speculationLog instanceof HotSpotSpeculationLog) {
                HotSpotSpeculationLog log = (HotSpotSpeculationLog) speculationLog;
                if (log.managesFailedSpeculations() && log.hasSpeculations()) {
                    try {
                        // com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime.createSpeculationLog()
                        setSpeculationLog.invoke(nmethod, log);
                    } catch (Error e) {
                        throw e;
                    } catch (Throwable throwable) {
                        throw new InternalError(throwable);
                    }
                }
            }
        }
    }

    @Override
    public boolean isValid() {
        return installedCode.isValid();
    }

    @Override
    public boolean isValidLastTier() {
        InstalledCode code = installedCode;
        return code.isValid() && code.getName().endsWith(TruffleCompiler.SECOND_TIER_COMPILATION_SUFFIX);
    }

    @Override
    public long getCodeAddress() {
        return installedCode.getStart();
    }

    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        return HotSpotTruffleRuntimeServices.getCompilationSpeculationLog(this);
    }

}
