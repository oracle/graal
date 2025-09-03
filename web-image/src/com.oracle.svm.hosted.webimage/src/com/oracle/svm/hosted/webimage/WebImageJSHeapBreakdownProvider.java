/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.HeapBreakdownProvider;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;

public class WebImageJSHeapBreakdownProvider extends HeapBreakdownProvider {

    /**
     * The heap breakdown entries produced here are only an approximation, the true size can only be
     * known after writing the image.
     * <p>
     * The counts are still accurate though. The object sizes correspond approximately to the number
     * of bytes used if they were laid out in binary without alignment gaps.
     */
    @Override
    protected void calculate(FeatureImpl.BeforeImageWriteAccessImpl access, boolean resourcesAreReachable) {
        long totalByteSize = 0;
        WebImageJSProviders providers = (WebImageJSProviders) ImageSingletons.lookup(WebImageProviders.class);
        ConstantIdentityMapping identityMapping = providers.typeControl().getConstantMap().identityMapping;

        Map<HostedClass, HeapBreakdownEntry> objectTypeEntries = new HashMap<>();
        for (ConstantIdentityMapping.IdentityNode node : identityMapping.identityNodes()) {
            HostedClass type = (HostedClass) providers.getMetaAccess().lookupJavaType(node.getDefinition().getConstant());
            long size = node.getDefinition().getSize();
            objectTypeEntries.computeIfAbsent(type, HeapBreakdownEntry::of).add(size);
            totalByteSize += size;
        }

        setTotalHeapSize(totalByteSize);
        setBreakdownEntries(objectTypeEntries.values().stream().toList());
    }
}
