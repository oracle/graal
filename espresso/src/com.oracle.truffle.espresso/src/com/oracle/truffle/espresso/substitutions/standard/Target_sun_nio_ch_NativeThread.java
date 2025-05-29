/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.ffi.nfi.NFISulongNativeAccess;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.VersionFilter.Java17OrEarlier;
import com.oracle.truffle.espresso.substitutions.VersionFilter.Java21OrLater;

@EspressoSubstitutions
public final class Target_sun_nio_ch_NativeThread {
    /*
     * This doesn't exist on Windows, it just won't match
     */
    @Substitution
    abstract static class Init extends SubstitutionNode {
        abstract void execute();

        @Specialization
        static void doDefault(@Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().sun_nio_ch_NativeThread_init.getCallTargetNoSubstitution())") DirectCallNode original) {
            // avoid the installation of a signal handler except on SVM and not with llvm
            if (EspressoOptions.RUNNING_ON_SVM && !(context.getNativeAccess() instanceof NFISulongNativeAccess)) {
                original.call();
            }
        }
    }

    @Substitution(languageFilter = Java21OrLater.class)
    abstract static class IsNativeThread extends SubstitutionNode {
        abstract boolean execute(long tid);

        @Specialization
        static boolean doDefault(long tid,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().sun_nio_ch_NativeThread_isNativeThread.getCallTargetNoSubstitution())") DirectCallNode original) {
            if (context.getNativeAccess() instanceof NFISulongNativeAccess) {
                // sulong virtualizes pthread_self but not ptrhead_kill
                // signal to the JDK that we don't support signaling
                return false;
            } else {
                return (boolean) original.call(tid);
            }
        }
    }

    @Substitution(languageFilter = Java21OrLater.class)
    abstract static class Current0 extends SubstitutionNode {
        abstract long execute();

        @Specialization
        static long doDefault(@Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().sun_nio_ch_NativeThread_current0.getCallTargetNoSubstitution())") DirectCallNode original) {
            if (context.getNativeAccess() instanceof NFISulongNativeAccess) {
                // sulong virtualizes pthread_self but not ptrhead_kill
                // signal to the JDK that we don't support signaling
                return 0;
            } else {
                return (long) original.call();
            }
        }
    }

    @Substitution(languageFilter = Java17OrEarlier.class)
    abstract static class Signal extends SubstitutionNode {
        abstract void execute(long nt);

        @Specialization
        static void doDefault(long nt,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().sun_nio_ch_NativeThread_signal.getCallTargetNoSubstitution())") DirectCallNode original) {
            // sulong virtualizes pthread_self but not ptrhead_kill
            if (!(context.getNativeAccess() instanceof NFISulongNativeAccess)) {
                original.call(nt);
            }
        }
    }
}
