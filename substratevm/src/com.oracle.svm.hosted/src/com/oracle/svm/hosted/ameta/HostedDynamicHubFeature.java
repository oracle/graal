/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.SVMHost;

@AutomaticFeature
public class HostedDynamicHubFeature implements Feature {
    private AnalysisMetaAccess metaAccess;
    private SVMHost hostVM;

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        metaAccess = access.getMetaAccess();
        hostVM = access.getHostVM();

        access.registerObjectReplacer(this::replace);
    }

    private Object replace(Object source) {
        if (source instanceof Class) {
            Class<?> clazz = (Class<?>) source;
            DynamicHub dynamicHub = hostVM.dynamicHub(metaAccess.lookupJavaType(clazz));

            AnalysisConstantReflectionProvider.registerHub(hostVM, dynamicHub);
            return dynamicHub;

        } else if (source instanceof DynamicHub) {
            AnalysisConstantReflectionProvider.registerHub(hostVM, (DynamicHub) source);
        }
        return source;
    }
}
