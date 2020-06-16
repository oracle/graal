/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.option;

import java.lang.reflect.Field;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;

@AutomaticFeature
public class HostedOptionFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /*
         * By default, all RuntimeOptionKeys that correspond to XOptions are unused. This happens
         * because we never use those options directly but instead rely on 'onValueUpdate()' to
         * update the corresponding XOption when the value of the runtime option changes. To enable
         * the use of -XX:OptionName at runtime, it is therefore necessary to explicitly register
         * all those options.
         */
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        registerOptionAsRead(accessImpl, SubstrateOptions.class, SubstrateOptions.MaxHeapSize.getName());
        registerOptionAsRead(accessImpl, SubstrateOptions.class, SubstrateOptions.MinHeapSize.getName());
        registerOptionAsRead(accessImpl, SubstrateOptions.class, SubstrateOptions.MaxNewSize.getName());
        registerOptionAsRead(accessImpl, SubstrateOptions.class, SubstrateOptions.StackSize.getName());
    }

    private static void registerOptionAsRead(BeforeAnalysisAccessImpl accessImpl, Class<?> clazz, String fieldName) {
        try {
            Field javaField = clazz.getField(fieldName);
            AnalysisField analysisField = accessImpl.getMetaAccess().lookupJavaField(javaField);
            accessImpl.registerAsRead(analysisField);
        } catch (NoSuchFieldException | SecurityException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}
