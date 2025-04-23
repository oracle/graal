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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.runtime.OptimizedFastThreadLocal;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

final class HotSpotFastThreadLocal extends OptimizedFastThreadLocal {

    static final HotSpotFastThreadLocal SINGLETON = new HotSpotFastThreadLocal();

    private static final ThreadLocal<Object[]> VIRTUAL_THREADS_THREAD_LOCAL = new ThreadLocal<>();

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
    static Object[] getJVMCIReservedReference() {
        if (HotSpotTruffleRuntime.getRuntime().bypassedReservedOop()) {
            /*
             * JVMCI fallback: Just call the fallback method while the compiler is initializing. We
             * do not want to stall guest code execution for this.
             */
            return (Object[]) RUNTIME.getThreadLocalObject(0);
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
        if (HotSpotTruffleRuntime.getRuntime().bypassedReservedOop()) {
            /*
             * JVMCI fallback: Just call the fallback method while the compiler is initializing. We
             * do not want to stall guest code execution for this.
             */
            RUNTIME.setThreadLocalObject(0, v);
        } else {
            throw CompilerDirectives.shouldNotReachHere("Bypassed reserved oop without compiler initialization triggered.");
        }
    }

    // We do not want any synchronization or park during JVMTI hooks
    private static final HotSpotJVMCIRuntime RUNTIME = HotSpotJVMCIRuntime.runtime();

    // We do not use getJVMCIReservedReference() here, we want to avoid any potential park() and
    // control what Java code is run
    static void unmount() {
        Object[] threadLocals = (Object[]) RUNTIME.getThreadLocalObject(0);
        VIRTUAL_THREADS_THREAD_LOCAL.set(threadLocals);
    }

    // We do not use setJVMCIReservedReference() here, we want to avoid any potential park() and
    // control what Java code is run
    static void mount() {
        Object[] threadLocals = VIRTUAL_THREADS_THREAD_LOCAL.get();
        RUNTIME.setThreadLocalObject(0, threadLocals);
    }

    static void ensureLoaded() {
    }
}
