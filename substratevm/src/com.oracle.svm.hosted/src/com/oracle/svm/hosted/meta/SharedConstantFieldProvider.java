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
package com.oracle.svm.hosted.meta;

import org.graalvm.compiler.core.common.spi.JavaConstantFieldProvider;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract class SharedConstantFieldProvider extends JavaConstantFieldProvider {

    protected final ClassInitializationSupport classInitializationSupport;

    public SharedConstantFieldProvider(MetaAccessProvider metaAccess, ClassInitializationSupport classInitializationSupport) {
        super(metaAccess);
        this.classInitializationSupport = classInitializationSupport;
    }

    @Override
    public boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        if (classInitializationSupport.shouldInitializeAtRuntime(field.getDeclaringClass())) {
            return false;
        }
        return super.isFinalField(field, tool);
    }

    @Override
    protected boolean isFinalFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (value.getJavaKind() == JavaKind.Object && SubstrateObjectConstant.asObject(value) instanceof MethodPointer) {
            /*
             * Prevent the constant folding of MethodPointer objects. MethodPointer is a "hosted"
             * type, so it cannot be present in compiler graphs.
             */
            return false;
        }
        return super.isFinalFieldValueConstant(field, value, tool);
    }
}
