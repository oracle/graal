/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.api.CompilerDirectives;

import java.util.HashSet;
import java.util.Set;

public final class ThreadSuspension {

    @CompilerDirectives.CompilationFinal(dimensions = 1) private Object[] threads = new Object[0];

    @CompilerDirectives.CompilationFinal(dimensions = 1) private int[] suspensionCount = new int[0];

    private final Set<Object> hardSuspendedThreads = new HashSet<>();

    public void suspendThread(Object thread) {
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                // increase the suspension count
                suspensionCount[i]++;
                return;
            }
        }
        // not yet registered, so add to array
        Object[] expanded = new Object[threads.length + 1];
        System.arraycopy(threads, 0, expanded, 0, threads.length);
        expanded[threads.length] = thread;

        int[] temp = new int[threads.length + 1];
        System.arraycopy(suspensionCount, 0, temp, 0, threads.length);
        // set the thread as suspended with suspension count 1
        temp[threads.length] = 1;

        threads = expanded;
        suspensionCount = temp;
    }

    public void resumeThread(Object thread) {
        removeHardSuspendedThread(thread);
        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                if (suspensionCount[i] > 0) {
                    suspensionCount[i]--;
                    return;
                }
            }
        }
    }

    public int getSuspensionCount(Object thread) {
        // check if thread has been hard suspended
        if (hardSuspendedThreads.contains(thread)) {
            // suspended through a hard suspension, which means that thread is
            // still running until the callback from the Debug API is fired
            // or it's blocked or waiting
            return 1;
        }

        for (int i = 0; i < threads.length; i++) {
            if (thread == threads[i]) {
                return suspensionCount[i];
            }
        }
        // this should never be reached
        return 0;
    }

    public void addHardSuspendedThread(Object thread) {
        hardSuspendedThreads.add(thread);
    }

    public void removeHardSuspendedThread(Object thread) {
        hardSuspendedThreads.remove(thread);
    }
}
