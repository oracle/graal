/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;

public final class DynamicHubSupport {

    private int maxTypeId;
    @UnknownObjectField(types = {byte[].class}) private byte[] referenceMapEncoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHubSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMaxTypeId(int maxTypeId) {
        this.maxTypeId = maxTypeId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxTypeId() {
        return maxTypeId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setData(NonmovableArray<Byte> referenceMapEncoding) {
        this.referenceMapEncoding = NonmovableArrays.getHostedArray(referenceMapEncoding);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static NonmovableArray<Byte> getReferenceMapEncoding() {
        return NonmovableArrays.fromImageHeap(ImageSingletons.lookup(DynamicHubSupport.class).referenceMapEncoding);
    }
}

@AutomaticFeature
class DynamicHubFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(DynamicHubSupport.class, new DynamicHubSupport());
    }
}
