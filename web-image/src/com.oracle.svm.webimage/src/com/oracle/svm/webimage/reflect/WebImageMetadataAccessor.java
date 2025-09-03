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

package com.oracle.svm.webimage.reflect;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.AfterCompilation;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.reflect.RuntimeMetadataDecoder;
import com.oracle.svm.core.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Disallowed;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.webimage.platform.WebImagePlatform;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Provides access to meta data without relying on {@link com.oracle.svm.core.code.CodeInfo}.
 */
@AutomaticallyRegisteredImageSingleton(RuntimeMetadataDecoder.MetadataAccessor.class)
@Platforms(WebImagePlatform.class)
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Disallowed.class)
public class WebImageMetadataAccessor implements RuntimeMetadataDecoder.MetadataAccessor {

    @UnknownObjectField(availability = AfterCompilation.class) private Object[] constants;
    @UnknownObjectField(availability = AfterCompilation.class) private Class<?>[] classes;
    @UnknownObjectField(availability = AfterCompilation.class) private String[] memberNames;
    @UnknownObjectField(availability = AfterCompilation.class) private String[] otherStrings;

    @Platforms(Platform.HOSTED_ONLY.class)
    public void installMetadata(JavaConstant[] constantsArray, Class<?>[] classesArray, String[] memberNamesArray, String[] otherStringsArray, CodeInfoEncoder codeInfoEncoder) {
        this.constants = new Object[constantsArray.length];
        for (int i = 0; i < constantsArray.length; i++) {
            this.constants[i] = codeInfoEncoder.getFrameInfoEncoder().getConstantAccess().asObject(constantsArray[i]);
        }
        this.classes = classesArray;
        this.memberNames = memberNamesArray;
        this.otherStrings = otherStringsArray;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getObject(int index, int layerId) {
        return (T) constants[index];
    }

    @Override
    public Class<?> getClass(int index, int layerId) {
        return classes[index];
    }

    @Override
    public String getMemberName(int index, int layerId) {
        return memberNames[index];
    }

    @Override
    public String getOtherString(int index, int layerId) {
        return otherStrings[index];
    }
}
