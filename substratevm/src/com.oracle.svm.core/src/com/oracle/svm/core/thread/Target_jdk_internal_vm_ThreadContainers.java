/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.stream.Stream;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * We currently don't provide/expose means for debugging and monitoring of threads, so we replace
 * these methods with an empty implementation.
 */
@TargetClass(className = "jdk.internal.vm.ThreadContainers")
@SuppressWarnings("unused")
final class Target_jdk_internal_vm_ThreadContainers {
    // Checkstyle: stop
    @Delete static Set<WeakReference<Target_jdk_internal_vm_ThreadContainer>> CONTAINER_REGISTRY;
    @Delete static ReferenceQueue<Object> QUEUE;
    // Checkstyle: resume

    @Substitute
    public static Object registerContainer(Target_jdk_internal_vm_ThreadContainer container) {
        return null;
    }

    @Substitute
    public static void deregisterContainer(Object key) {
    }

    @Substitute
    static Stream<Target_jdk_internal_vm_ThreadContainer> children(Target_jdk_internal_vm_ThreadContainer container) {
        return Stream.empty();
    }
}

@TargetClass(className = "jdk.internal.vm.ThreadContainer")
final class Target_jdk_internal_vm_ThreadContainer {
}
