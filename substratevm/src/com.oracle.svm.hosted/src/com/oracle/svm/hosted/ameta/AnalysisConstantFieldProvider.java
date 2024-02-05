/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.meta.SharedConstantFieldProvider;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantFieldProvider extends SharedConstantFieldProvider {

    public AnalysisConstantFieldProvider(MetaAccessProvider metaAccess, SVMHost hostVM) {
        super(metaAccess, hostVM);
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField f, ConstantFieldTool<T> analysisTool) {
        AnalysisField field = (AnalysisField) f;

        T foldedValue = super.readConstantField(field, analysisTool);

        if (foldedValue != null) {
            if (!BuildPhaseProvider.isAnalysisFinished()) {
                field.registerAsFolded(nonNullReason(analysisTool.getReason()));
            }
        }
        return foldedValue;
    }

    @Override
    protected AnalysisField asAnalysisField(ResolvedJavaField field) {
        return (AnalysisField) field;
    }

    private static Object nonNullReason(Object reason) {
        return reason == null ? "Unknown constant fold location." : reason;
    }
}
