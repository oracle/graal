/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDK22OrLater;

import jdk.internal.foreign.MemorySessionImpl;

/**
 * Gracefully handle unsupported features.
 * <p>
 * It seems like this could be easily supported once thread-local handshakes are supported.
 */
@TargetClass(className = "jdk.internal.misc.ScopedMemoryAccess", onlyWith = ForeignFunctionsEnabled.class)
public final class Target_jdk_internal_misc_ScopedMemoryAccess {
    @Substitute
    static void registerNatives() {
    }

    /**
     * Performs a thread-local handshake
     * 
     * <pre>
     * {@code
     * JVM_ENTRY(jboolean, ScopedMemoryAccess_closeScope(JNIEnv *env, jobject receiver, jobject deopt, jobject exception))
     *   CloseScopedMemoryClosure cl(deopt, exception);
     *   Handshake::execute(&cl);
     *   return !cl._found;
     * JVM_END
     * }
     * </pre>
     *
     * <code>CloseScopedMemoryClosure</code> can be summarised as follows: Each thread checks the
     * last <code>max_critical_stack_depth</code> (fixed to 10) frames of its own stack trace. If it
     * contains any <code>@Scoped</code>-annotated method called on the sessions being freed, it
     * sets <code>_found</code> to true.
     * <p>
     * See scopedMemoryAccess.cpp in HotSpot.
     * <p>
     * As one might notice, what is not supported is not creating shared arenas, but closing them.
     */
    @Substitute
    @TargetElement(onlyWith = JDK22OrLater.class)
    void closeScope0(MemorySessionImpl session, Target_jdk_internal_misc_ScopedMemoryAccess_ScopedAccessError error) {
        throw unsupportedFeature("GR-52276: Arena.ofShared not supported");
    }

    @Substitute
    @TargetElement(onlyWith = JDK21OrEarlier.class)
    boolean closeScope0(MemorySessionImpl session) {
        throw unsupportedFeature("GR-52276: Arena.ofShared not supported");
    }
}

@TargetClass(className = "jdk.internal.misc.ScopedMemoryAccess$ScopedAccessError", onlyWith = {JDK22OrLater.class, ForeignFunctionsEnabled.class})
final class Target_jdk_internal_misc_ScopedMemoryAccess_ScopedAccessError {
}
