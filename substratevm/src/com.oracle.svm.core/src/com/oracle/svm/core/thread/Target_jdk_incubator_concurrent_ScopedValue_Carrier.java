/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.ModuleUtil;

@Platforms(Platform.HOSTED_ONLY.class)
final class IncubatorConcurrentModule implements BooleanSupplier {
    @Override
    public boolean getAsBoolean() {
        return JavaVersionUtil.JAVA_SPEC >= 20 && ModuleUtil.bootLayerContainsModule("jdk.incubator.concurrent");
    }
}

/**
 * Substituted to directly call {@link Target_java_lang_Thread#setScopedValueBindings} for forced
 * inlining.
 */
@TargetClass(className = "jdk.incubator.concurrent.ScopedValue", innerClass = "Carrier", onlyWith = IncubatorConcurrentModule.class)
final class Target_jdk_incubator_concurrent_ScopedValue_Carrier {
    @Alias int bitmask;

    @Substitute
    private <R> R runWith(Target_jdk_incubator_concurrent_ScopedValue_Snapshot newSnapshot, Callable<R> op) throws Exception {
        Target_java_lang_Thread.setScopedValueBindings(newSnapshot);
        try {
            return Target_jdk_internal_vm_ScopedValueContainer.call(op);
        } finally {
            Target_java_lang_Thread.setScopedValueBindings(newSnapshot.prev);
            Target_jdk_incubator_concurrent_ScopedValue_Cache.invalidate(bitmask);
        }
    }

    @Substitute
    private void runWith(Target_jdk_incubator_concurrent_ScopedValue_Snapshot newSnapshot, Runnable op) {
        Target_java_lang_Thread.setScopedValueBindings(newSnapshot);
        try {
            Target_jdk_internal_vm_ScopedValueContainer.run(op);
        } finally {
            Target_java_lang_Thread.setScopedValueBindings(newSnapshot.prev);
            Target_jdk_incubator_concurrent_ScopedValue_Cache.invalidate(bitmask);
        }
    }
}

@TargetClass(className = "jdk.internal.vm.ScopedValueContainer", onlyWith = IncubatorConcurrentModule.class)
final class Target_jdk_internal_vm_ScopedValueContainer {
    @Alias
    static native <V> V call(Callable<V> op) throws Exception;

    @Alias
    static native void run(Runnable op);
}

@TargetClass(className = "jdk.incubator.concurrent.ScopedValue", innerClass = "Snapshot", onlyWith = IncubatorConcurrentModule.class)
final class Target_jdk_incubator_concurrent_ScopedValue_Snapshot {
    @Alias //
    Target_jdk_incubator_concurrent_ScopedValue_Snapshot prev;
}

@TargetClass(className = "jdk.incubator.concurrent.ScopedValue", innerClass = "Cache", onlyWith = IncubatorConcurrentModule.class)
final class Target_jdk_incubator_concurrent_ScopedValue_Cache {
    @Alias
    static native void invalidate(int toClearBits);
}
