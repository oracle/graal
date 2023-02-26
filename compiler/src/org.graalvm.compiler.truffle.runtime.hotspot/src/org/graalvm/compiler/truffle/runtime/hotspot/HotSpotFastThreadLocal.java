/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;

import org.graalvm.compiler.truffle.runtime.GraalFastThreadLocal;
import com.oracle.truffle.api.CompilerDirectives;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

final class HotSpotFastThreadLocal extends GraalFastThreadLocal {

    static final HotSpotFastThreadLocal SINGLETON = new HotSpotFastThreadLocal();

    static final MethodHandle FALLBACK_SET = AbstractHotSpotTruffleRuntime.getRuntime().getSetThreadLocalObject();
    static final MethodHandle FALLBACK_GET = AbstractHotSpotTruffleRuntime.getRuntime().getGetThreadLocalObject();

    /*
     * This threshold determines how many recursive invocations of a no fallback method need to
     * occur until we detect that Java debug stepping is active.
     */
    private static final int DEBUG_STEPPING_DETECTION_THRESHOLD = 10;

    private static final ThreadLocal<MutableInt> fallbackThreadLocal = new ThreadLocal<>() {
        @Override
        protected MutableInt initialValue() {
            return new MutableInt();
        }
    };

    HotSpotFastThreadLocal() {
    }

    /*
     * This method is intrinsified for partial evaluation. See HotSpotTruffleGraphBuilderPlugins for
     * details.
     */
    @Override
    public void set(Object[] data) {
        setJVMCIReservedReference(data);
    }

    /*
     * This method is intrinsified for partial evaluation. See HotSpotTruffleGraphBuilderPlugins for
     * details.
     */
    @Override
    public Object[] get() {
        return getJVMCIReservedReference();
    }

    /*
     * This method is intrinsified for interpreter execution. The get and set methods of this class
     * are precompiled with stubs when the truffle compiler is initialized. Compiler plugins are
     * installed for getJMVCIReservedReference and setJVMCIReservedReference in order to access the
     * thread local JVMCI reserved field directly. Breakpoints in this method may cause Truffle
     * compiler initialization failures.
     *
     * See AbstractHotSpotTruffleRuntime.installReservedOopMethods
     */
    @SuppressWarnings("cast")
    static Object[] getJVMCIReservedReference() {
        boolean waitForInstall = FALLBACK_GET == null;
        if (AbstractHotSpotTruffleRuntime.getRuntime().bypassedReservedOop(waitForInstall)) {
            if (waitForInstall) {
                /*
                 * No JVMCI fallback: We waited for the stub installation therefore we should be
                 * able to call recursively and get to call the stub.
                 */
                return getJVMCIReservedReferenceNoFallback();
            } else {
                /*
                 * JVMCI fallback: Just call the fallback method while the compiler is initializing.
                 * We do not want to stall guest code execution for this.
                 */
                try {
                    return (Object[]) (Object) FALLBACK_GET.invokeExact(HotSpotJVMCIRuntime.runtime(), 0);
                } catch (Throwable e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
        } else {
            /*
             * We can assume the current context is null as setJVMCIReservedReference was not yet
             * called because it would fail. As soon as setJVMCIReservedReference was executed
             * without error this method must not reach this branch anymore. It is not easy to
             * assert this though.
             */
            return null;
        }
    }

    private static Object[] getJVMCIReservedReferenceNoFallback() {
        MutableInt value = fallbackThreadLocal.get();
        if (value.value > DEBUG_STEPPING_DETECTION_THRESHOLD) {
            throw failDebugStepping();
        }
        value.value++;
        try {
            return SINGLETON.get();
        } finally {
            value.value--;
        }
    }

    /*
     * This method is intrinsified for interpreter execution. The get and set methods of this class
     * are precompiled with stubs when the truffle compiler is initialized. Compiler plugins are
     * installed for getJMVCIReservedReference and setJVMCIReservedReference in order to access the
     * thread local JVMCI reserved field directly. Breakpoints in this method may cause Truffle
     * compiler initialization failures.
     *
     * See AbstractHotSpotTruffleRuntime.installReservedOopMethods
     */
    static void setJVMCIReservedReference(Object[] v) {
        boolean waitForInstall = FALLBACK_SET == null;
        if (AbstractHotSpotTruffleRuntime.getRuntime().bypassedReservedOop(waitForInstall)) {
            if (waitForInstall) {
                /*
                 * No JVMCI fallback: We waited for the stub installation therefore we should be
                 * able to call recursively and get to call the stub.
                 */
                setJVMCIReservedReferenceNoFallback(v);
            } else {
                /*
                 * JVMCI fallback: Just call the fallback method while the compiler is initializing.
                 * We do not want to stall guest code execution for this.
                 */
                try {
                    FALLBACK_SET.invokeExact(HotSpotJVMCIRuntime.runtime(), 0, (Object) v);
                } catch (Throwable e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
        } else {
            throw CompilerDirectives.shouldNotReachHere("Bypassed reserved oop without compiler initialization triggered.");
        }
    }

    private static void setJVMCIReservedReferenceNoFallback(Object[] v) {
        MutableInt invocations = fallbackThreadLocal.get();
        if (invocations.value > DEBUG_STEPPING_DETECTION_THRESHOLD) {
            throw failDebugStepping();
        }
        invocations.value++;
        try {
            SINGLETON.set(v);
        } finally {
            invocations.value--;
        }
    }

    static final class MutableInt {
        int value;
    }

    private static RuntimeException failDebugStepping() {
        /*
         * This might happen if you single step through this method as the debugger will ignore the
         * installed code and just run this code in the host interpreter.
         */
        throw new UnsupportedOperationException("Cannot step through the fast thread local with the debugger without JVMCI API fallback methods. " +
                        "Make sure the JVMCI version is up-to-date or switch to a runtime without Truffle compilation for debugging to resolve this. " +
                        "Use -Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime to switch to a runtime without compilation. " +
                        "Remember to remove this option again after debugging.");
    }

}
