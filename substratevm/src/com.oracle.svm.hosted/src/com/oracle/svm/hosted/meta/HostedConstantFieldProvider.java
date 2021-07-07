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
package com.oracle.svm.hosted.meta;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.hosted.ameta.AnalysisConstantFieldProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public class HostedConstantFieldProvider extends SharedConstantFieldProvider {

    public HostedConstantFieldProvider(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {
        super(metaAccess, classInitializationSupport);
    }

    /**
     * Note that this method must return true for all cases where
     * {@link AnalysisConstantFieldProvider} returned true. Otherwise fields that were constant
     * folded during analysis are not constant folded for compilation.
     */
    @Override
    public boolean isFinalField(ResolvedJavaField f, ConstantFieldTool<?> tool) {
        HostedField field = (HostedField) f;

        if (field.location == HostedField.LOC_UNMATERIALIZED_STATIC_CONSTANT) {
            return true;
        } else if (!field.wrapped.isWritten()) {
            return true;
        }
        return super.isFinalField(field, tool);
    }
}
