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
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.SharedConstantFieldProvider;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantFieldProvider extends SharedConstantFieldProvider {
    private final AnalysisUniverse universe;
    private final AnalysisConstantReflectionProvider constantReflection;

    public AnalysisConstantFieldProvider(AnalysisUniverse universe, AnalysisMetaAccess metaAccess, AnalysisConstantReflectionProvider constantReflection,
                    ClassInitializationSupport classInitializationSupport) {
        super(metaAccess, classInitializationSupport);
        this.universe = universe;
        this.constantReflection = constantReflection;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> analysisTool) {
        AnalysisField f = (AnalysisField) field;
        if (SVMHost.isUnknownObjectField(f) || SVMHost.isUnknownPrimitiveField(f)) {
            return null;
        }
        T foldedValue = null;
        if (f.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) f.wrapped;
            if (readableField.allowConstantFolding() && readableField.isValueAvailable()) {
                JavaConstant fieldValue = readableField.readValue(metaAccess, universe.toHosted(analysisTool.getReceiver()));
                if (fieldValue != null) {
                    foldedValue = analysisTool.foldConstant(constantReflection.interceptValue(metaAccess, f, universe.lookup(fieldValue)));
                }
            }
        } else {
            foldedValue = super.readConstantField(field, analysisTool);
        }

        if (foldedValue != null) {
            if (!BuildPhaseProvider.isAnalysisFinished()) {
                f.registerAsFolded(nonNullReason(analysisTool.getReason()));
            }
        }
        return foldedValue;
    }

    public static Object nonNullReason(Object reason) {
        return reason == null ? "Unknown constant fold location." : reason;
    }

}
