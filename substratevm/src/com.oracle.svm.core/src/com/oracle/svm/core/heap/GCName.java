/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
/**
 * This class holds supported garbage collector names.
 */
public class GCName {
    @Platforms(Platform.HOSTED_ONLY.class) private static final ArrayList<GCName> HostedGCNameList = new ArrayList<>();

    @UnknownObjectField(types = {GCName[].class}) protected static GCName[] GCNames;

    private final int id;
    private final String name;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected GCName(String name) {
        this.name = name;
        this.id = addGCNameMapping();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private int addGCNameMapping() {
        synchronized (HostedGCNameList) {
            int newId = HostedGCNameList.size();
            HostedGCNameList.add(newId, this);
            return newId;
        }
    }

    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getId() {
        return id;
    }

    public static GCName[] getGCNames() {
        return GCNames;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void cacheReverseMapping() {
        GCNames = HostedGCNameList.toArray(new GCName[HostedGCNameList.size()]);
    }
}

@AutomaticFeature
class GCNameFeature implements Feature {
    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        GCName.cacheReverseMapping();
        access.registerAsImmutable(GCName.GCNames);
    }
}
