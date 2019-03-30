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

import org.graalvm.compiler.core.common.spi.JavaConstantFieldProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.meta.ReadableJavaField;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantFieldProvider extends JavaConstantFieldProvider {
    private final AnalysisUniverse universe;
    private final AnalysisConstantReflectionProvider constantReflection;
    private final ClassInitializationSupport classInitializationSupport;

    public AnalysisConstantFieldProvider(AnalysisUniverse universe, MetaAccessProvider metaAccess, AnalysisConstantReflectionProvider constantReflection,
                    ClassInitializationSupport classInitializationSupport) {
        super(metaAccess);
        this.universe = universe;
        this.constantReflection = constantReflection;
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> analysisTool) {
        AnalysisField f = (AnalysisField) field;
        if (SVMHost.isUnknownObjectField(f) || SVMHost.isUnknownPrimitiveField(f)) {
            return null;
        }
        if (f.wrapped instanceof ReadableJavaField) {
            ReadableJavaField readableField = (ReadableJavaField) f.wrapped;
            if (readableField.allowConstantFolding()) {
                JavaConstant fieldValue = readableField.readValue(universe.toHosted(analysisTool.getReceiver()));
                if (fieldValue != null) {
                    return analysisTool.foldConstant(constantReflection.interceptValue(f, universe.lookup(fieldValue)));
                }
            }
            return null;
        }

        return super.readConstantField(field, analysisTool);
    }

    @Override
    protected boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass())) {
            return false;
        }
        return super.isFinalField(field, tool);
    }
}
