/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.runtime.OptimizedFastThreadLocal;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

final class HotSpotFastThreadLocal extends OptimizedFastThreadLocal {

    static final HotSpotFastThreadLocal SINGLETON = new HotSpotFastThreadLocal();

    static final MethodHandle FALLBACK_SET = HotSpotTruffleRuntime.getRuntime().getSetThreadLocalObject();
    static final MethodHandle FALLBACK_GET = HotSpotTruffleRuntime.getRuntime().getGetThreadLocalObject();

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
     * This method is intrinsified for partial evaluation. See HotSpotGraphBuilderPlugins for
     * details.
     */
    @Override
    public void set(Object[] data) {
        setJVMCIReservedReference(data);
    }

    /*
     * This method is intrinsified for partial evaluation. See HotSpotGraphBuilderPlugins for
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
     * See HotSpotTruffleRuntime.installReservedOopMethods
     */
    @SuppressWarnings("cast")
    static Object[] getJVMCIReservedReference() {
        boolean waitForInstall = FALLBACK_GET == null;
        if (HotSpotTruffleRuntime.getRuntime().bypassedReservedOop(waitForInstall)) {
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
     * See HotSpotTruffleRuntime.installReservedOopMethods
     */
    static void setJVMCIReservedReference(Object[] v) {
        boolean waitForInstall = FALLBACK_SET == null;
        if (HotSpotTruffleRuntime.getRuntime().bypassedReservedOop(waitForInstall)) {
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
