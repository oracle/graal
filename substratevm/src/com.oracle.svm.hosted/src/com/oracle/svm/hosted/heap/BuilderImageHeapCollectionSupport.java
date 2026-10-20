/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.svm.core.GuestImageSingletonSupport;
import com.oracle.svm.guest.staging.util.ImageHeapCollectionSupport;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.GuestInvoked;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/** Builder-side bridge to the guest image-heap collection support. */
final class BuilderImageHeapCollectionSupport {
    private BuilderImageHeapCollectionSupport() {
    }

    static void initialize() {
        GuestAccess guest = GuestAccess.get();
        ResolvedJavaType type = guest.lookupType(ImageHeapCollectionSupport.class);
        ResolvedJavaMethod constructor = JVMCIReflectionUtil.getDeclaredConstructor(type);
        JavaConstant support = guest.invoke(constructor, null);
        GuestImageSingletonSupport.add(type, support);
    }

    static Function<JavaConstant, JavaConstant> createObjectReplacer() {
        GuestAccess guest = GuestAccess.get();
        ResolvedJavaType type = guest.lookupType(ImageHeapCollectionSupport.class);
        JavaConstant support = GuestImageSingletonSupport.lookup(type);
        ResolvedJavaMethod replaceMethod = guest.lookupMethod(type, "replaceHostedWithRuntime", Object.class);

        return constant -> {
            if (constant instanceof PatchedWordConstant) {
                return constant;
            }
            return guest.invoke(replaceMethod, support, constant);
        };
    }

    /**
     * Helper to satisfy {@link GuestAccess#createHostProxy}.
     */
    @FunctionalInterface
    interface JavaConstantConsumer {
        @GuestInvoked
        void accept(JavaConstant object);
    }

    static void updateAndRescanCollections(JavaConstantConsumer rescanObject, Runnable triggerAnalysisIteration) {
        GuestAccess guest = GuestAccess.get();
        ResolvedJavaType type = guest.lookupType(ImageHeapCollectionSupport.class);
        JavaConstant support = GuestImageSingletonSupport.lookup(type);
        ResolvedJavaMethod updateMethod = guest.lookupMethod(type, "updateAndRescanCollections", Consumer.class, Runnable.class);
        JavaConstant rescanProxy = guest.createHostProxy(rescanObject, guest.lookupType(Consumer.class));
        JavaConstant iterationProxy = guest.createHostProxy(triggerAnalysisIteration, guest.lookupType(Runnable.class));
        guest.invoke(updateMethod, support, rescanProxy, iterationProxy);
    }

    static boolean needsUpdate() {
        GuestAccess guest = GuestAccess.get();
        ResolvedJavaType type = guest.lookupType(ImageHeapCollectionSupport.class);
        JavaConstant support = GuestImageSingletonSupport.lookup(type);
        return guest.invoke(guest.lookupMethod(type, "needsUpdate"), support).asBoolean();
    }

    static void verifyCollectionsAreStableAfterAnalysis() {
        GuestAccess guest = GuestAccess.get();
        ResolvedJavaType type = guest.lookupType(ImageHeapCollectionSupport.class);
        JavaConstant support = GuestImageSingletonSupport.lookup(type);
        guest.invoke(guest.lookupMethod(type, "verifyCollectionsAreStableAfterAnalysis"), support);
    }

}
