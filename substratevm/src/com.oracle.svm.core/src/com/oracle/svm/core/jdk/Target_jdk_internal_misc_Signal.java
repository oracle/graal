/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.FromAlias;

import java.util.Hashtable;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "jdk.internal.misc.Signal")
public final class Target_jdk_internal_misc_Signal {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static Hashtable<?, ?> handlers = new Hashtable<>(4);
    @Alias @RecomputeFieldValue(kind = FromAlias)//
    private static Hashtable<?, ?> signals = new Hashtable<>(4);
    // Checkstyle: resume

    @Substitute
    private static long handle0(int sig, long nativeH) {
        if (!SubstrateOptions.EnableSignalHandling.getValue()) {
            throw new IllegalArgumentException("Signal handlers can't be installed if signal handling is disabled, see option '" + SubstrateOptions.EnableSignalHandling.getName() + "'.");
        }
        return SignalHandlerSupport.singleton().installJavaSignalHandler(sig, nativeH);
    }

    /** Called by the VM to execute Java signal handlers. */
    @Alias
    public static native void dispatch(int number);

    /** Constants for the longs from {@link jdk.internal.misc.Signal}. */
    public static class Constants {
        public static final long ERROR_HANDLER = -1;
        public static final long DEFAULT_HANDLER = 0;
        public static final long IGNORE_HANDLER = 1;
        public static final long DISPATCH_HANDLER = 2;
    }
}
