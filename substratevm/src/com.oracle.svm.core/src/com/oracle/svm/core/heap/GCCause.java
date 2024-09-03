/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.DuplicatedInNativeCode;
import com.oracle.svm.core.util.ImageHeapList;
import com.oracle.svm.core.util.VMError;

/**
 * This class holds garbage collection causes that are common and therefore shared between different
 * garbage collector implementations.
 */
public class GCCause {

    @DuplicatedInNativeCode public static final GCCause JavaLangSystemGC = new GCCause("java.lang.System.gc()", 0);
    @DuplicatedInNativeCode public static final GCCause UnitTest = new GCCause("Forced GC in unit test", 1);
    @DuplicatedInNativeCode public static final GCCause TestGCInDeoptimizer = new GCCause("Test GC in deoptimizer", 2);
    @DuplicatedInNativeCode public static final GCCause HintedGC = new GCCause("Hinted GC", 3);
    @DuplicatedInNativeCode public static final GCCause JvmtiForceGC = new GCCause("JvmtiEnv ForceGarbageCollection", 4);
    @DuplicatedInNativeCode public static final GCCause HeapDump = new GCCause("Heap Dump Initiated GC ", 5);

    private final int id;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCCause(String name, int id) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getId() {
        return id;
    }

    public static GCCause fromId(int causeId) {
        return getGCCauses().get(causeId);
    }

    public static List<GCCause> getGCCauses() {
        return ImageSingletons.lookup(GCCauseSupport.class).gcCauses;
    }
}

@AutomaticallyRegisteredImageSingleton
class GCCauseSupport {
    final List<GCCause> gcCauses = ImageHeapList.create(GCCause.class, null);

    @Platforms(Platform.HOSTED_ONLY.class)
    Object collectGCCauses(Object obj) {
        if (obj instanceof GCCause gcCause) {
            synchronized (gcCauses) {
                int id = gcCause.getId();
                while (gcCauses.size() <= id) {
                    gcCauses.add(null);
                }
                var existing = gcCauses.set(id, gcCause);
                if (existing != null && existing != gcCause) {
                    throw VMError.shouldNotReachHere("Two GCCause objects have the same id " + id + ": " + gcCause.getName() + ", " + existing.getName());
                }
            }
        }
        return obj;
    }
}

@AutomaticallyRegisteredFeature
class GCCauseFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(ImageSingletons.lookup(GCCauseSupport.class)::collectGCCauses);
    }
}
