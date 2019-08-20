/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;

@TargetClass(className = "java.lang.Shutdown")
public final class Target_java_lang_Shutdown {
    /**
     * Re-initialize the map of registered hooks, because any hooks registered during native image
     * construction can not survive into the running image.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    static Runnable[] hooks;

    static {
        hooks = new Runnable[Util_java_lang_Shutdown.MAX_SYSTEM_HOOKS];
        /*
         * We use the last system hook slot (index 9), which is currently not used by the JDK, for
         * our own shutdown hooks that are registered during image generation. The JDK currently
         * uses slots 0, 1, and 2.
         */
        hooks[hooks.length - 1] = RuntimeSupport::executeShutdownHooks;
    }

    /* Wormhole for invoking java.lang.ref.Finalizer.runAllFinalizers */
    @Substitute
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    static void runAllFinalizers() {
        throw VMError.unsupportedFeature("java.lang.Shudown.runAllFinalizers()");
    }

    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    static void beforeHalt() {
    }

    /**
     * Invoked by the JNI DestroyJavaVM procedure when the last non-daemon thread has finished.
     * Unlike the exit method, this method does not actually halt the VM.
     */
    @Alias
    static native void shutdown();

    @Alias
    public static native void add(int slot, boolean registerShutdownInProgress, Runnable hook);
}

/** Utility methods for Target_java_lang_Shutdown. */
final class Util_java_lang_Shutdown {

    /**
     * Value *copied* from {@code java.lang.Shutdown.MAX_SYSTEM_HOOKS} so that the value can be used
     * during image generation (@Alias values are only visible at run time).
     */
    static final int MAX_SYSTEM_HOOKS = 10;
}
