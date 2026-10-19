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
package com.oracle.svm.core.meta;

import java.util.Objects;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredImageSingleton
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
@Platforms(Platform.HOSTED_ONLY.class)
public final class MethodRefSupport {

    private final EconomicMap<MethodRef, ResolvedJavaMethod> methods = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);

    MethodRefSupport() {
    }

    public static MethodPointer createMethodPointer(ResolvedJavaMethod method) {
        return singleton().register(new MethodPointer(), method);
    }

    public static MethodPointer createMethodPointer(ResolvedJavaMethod method, boolean permitsRewriteToPLT) {
        return singleton().register(new MethodPointer(permitsRewriteToPLT), method);
    }

    public static MethodOffset createMethodOffset(ResolvedJavaMethod method) {
        return singleton().register(new MethodOffset(), method);
    }

    public static MethodOffset createMethodOffset(ResolvedJavaMethod method, boolean permitsRewriteToPLT) {
        return singleton().register(new MethodOffset(permitsRewriteToPLT), method);
    }

    public static ResolvedJavaMethod getMethod(MethodRef methodRef) {
        return singleton().resolve(methodRef);
    }

    private static MethodRefSupport singleton() {
        return ImageSingletons.lookup(MethodRefSupport.class);
    }

    private synchronized <T extends MethodRef> T register(T methodRef, ResolvedJavaMethod method) {
        methods.put(Objects.requireNonNull(methodRef), Objects.requireNonNull(method));
        return methodRef;
    }

    private synchronized ResolvedJavaMethod resolve(MethodRef methodRef) {
        Objects.requireNonNull(methodRef);
        ResolvedJavaMethod method = methods.get(methodRef);
        VMError.guarantee(method != null, "No resolved method registered for method reference type %s", methodRef.getClass().getName());
        return method;
    }
}
