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
package com.oracle.svm.core.c;

import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.jvmci.shared.meta.SharedMethod;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@Platforms(Platform.HOSTED_ONLY.class)
@SingletonTraits(access = BuiltinTraits.BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class CGlobalDataLoadPolicy {
    private final Set<SharedMethod> accessViaImageHeap = new HashSet<>();

    public static CGlobalDataLoadPolicy singleton() {
        return ImageSingletons.lookup(CGlobalDataLoadPolicy.class);
    }

    public void markForAccessViaImageHeap(Set<? extends SharedMethod> methods) {
        accessViaImageHeap.addAll(methods);
    }

    public boolean shouldAccessViaImageHeap(SharedMethod method) {
        return accessViaImageHeap.contains(method);
    }
}
