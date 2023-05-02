/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot;

import java.lang.reflect.Method;

import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.TruffleCallBoundary;

import com.oracle.truffle.api.nodes.RootNode;

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
     * {@link AbstractHotSpotTruffleRuntime#createSpeculationLog}.
     */
    private void tetherSpeculationLog(HotSpotNmethod nmethod) throws Error, InternalError {
        if (setSpeculationLog != null) {
            if (speculationLog instanceof HotSpotSpeculationLog) {
                HotSpotSpeculationLog log = (HotSpotSpeculationLog) speculationLog;
                if (log.managesFailedSpeculations() && log.hasSpeculations()) {
                    try {
                        // org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime.createSpeculationLog()
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
