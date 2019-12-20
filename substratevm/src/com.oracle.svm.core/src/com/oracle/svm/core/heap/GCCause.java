/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.DuplicatedInNativeCode;
import com.oracle.svm.core.util.VMError;

/**
 * This class holds garbage collection causes that are common and therefore shared between different
 * garbage collector implementations.
 */
public class GCCause {
    @Platforms(Platform.HOSTED_ONLY.class) private static final ArrayList<GCCause> HostedGCCauseList = new ArrayList<>();

    @DuplicatedInNativeCode public static final GCCause JavaLangSystemGC = new GCCause("java.lang.System.gc()", 0);
    @DuplicatedInNativeCode public static final GCCause UnitTest = new GCCause("UnitTest", 1);
    @DuplicatedInNativeCode public static final GCCause TestGCInDeoptimizer = new GCCause("TestGCInDeoptimizer", 2);

    protected static GCCause[] GCCauses = new GCCause[]{JavaLangSystemGC, UnitTest, TestGCInDeoptimizer};

    private final int id;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCCause(String name, int id) {
        /* Checkstyle: allow synchronization. */
        this.id = id;
        this.name = name;
        addGCCauseMapping();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void addGCCauseMapping() {
        synchronized (HostedGCCauseList) { /* Checkstyle: disallow synchronization. */
            while (HostedGCCauseList.size() <= id) {
                HostedGCCauseList.add(null);
            }
            VMError.guarantee(HostedGCCauseList.get(id) == null, name + " and another GCCause have the same id.");
            HostedGCCauseList.set(id, this);
        }
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public static GCCause fromId(int causeId) {
        return GCCauses[causeId];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void cacheReverseMapping() {
        GCCauses = HostedGCCauseList.toArray(new GCCause[HostedGCCauseList.size()]);
    }
}

@AutomaticFeature
class GCCauseFeature implements Feature {
    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        GCCause.cacheReverseMapping();
        access.registerAsImmutable(GCCause.GCCauses);
    }
}
