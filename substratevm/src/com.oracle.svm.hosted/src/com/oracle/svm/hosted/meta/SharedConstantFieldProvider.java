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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.FieldValueInterceptionSupport;

import jdk.graal.compiler.core.common.spi.JavaConstantFieldProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public abstract class SharedConstantFieldProvider extends JavaConstantFieldProvider {

    protected final UniverseMetaAccess metaAccess;
    protected final SVMHost hostVM;
    protected final FieldValueInterceptionSupport fieldValueInterceptionSupport = FieldValueInterceptionSupport.singleton();

    public SharedConstantFieldProvider(MetaAccessProvider metaAccess, SVMHost hostVM) {
        super(metaAccess);
        this.metaAccess = (UniverseMetaAccess) metaAccess;
        this.hostVM = hostVM;
    }

    protected abstract AnalysisField asAnalysisField(ResolvedJavaField field);

    @Override
    public boolean isFinalField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        return super.isFinalField(field, tool) && allowConstantFolding(field, tool);
    }

    @Override
    public boolean isStableField(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        boolean stable;
        /*
         * GR-46030: JVMCI does not provide access yet to the proper "stable" flag that also takes
         * the class loader of the using class into account. So we look at the annotation directly
         * for now.
         */
        if (field.isAnnotationPresent(jdk.internal.vm.annotation.Stable.class)) {
            stable = true;
        } else {
            stable = super.isStableField(field, tool);
        }
        return stable && allowConstantFolding(field, tool);
    }

    private boolean allowConstantFolding(ResolvedJavaField field, ConstantFieldTool<?> tool) {
        var aField = asAnalysisField(field);

        /*
         * During compiler optimizations, it is possible to see field loads with a constant receiver
         * of a wrong type that might not even be an ImageHeapConstant. Also, we need to ensure that
         * the ImageHeapConstant allows constant folding of its fields.
         */
        if (!field.isStatic() && (!(tool.getReceiver() instanceof ImageHeapConstant receiver) || !receiver.allowConstantFolding())) {
            return false;
        }

        /*
         * This code should run as late as possible, because it has side effects. So we only do it
         * after we have already checked that the field is `final` or `stable`. It marks the
         * declaring class of the field as reachable, in order to trigger computation of automatic
         * substitutions. It also ensures that the class is initialized (if the class is registered
         * for initialization at build time) before any constant folding of static fields is
         * attempted.
         */
        if (!fieldValueInterceptionSupport.isValueAvailable(aField)) {
            return false;
        }

        if (field.isStatic() && !isClassInitialized(field) && !fieldValueInterceptionSupport.hasFieldValueTransformer(aField)) {
            /*
             * The class is not initialized at image build time, so we do not have a static field
             * value to constant fold. Note that a FieldValueTransformer is able to provide a field
             * value also for non-initialized classes.
             */
            return false;
        }
        return hostVM.allowConstantFolding(field);
    }

    protected boolean isClassInitialized(ResolvedJavaField field) {
        return field.getDeclaringClass().isInitialized();
    }

    @Override
    protected boolean isFinalFieldValueConstant(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (value.getJavaKind() == JavaKind.Object && metaAccess.isInstanceOf(value, MethodPointer.class)) {
            /*
             * Prevent the constant folding of MethodPointer objects. MethodPointer is a "hosted"
             * type, so it cannot be present in compiler graphs.
             */
            return false;
        }
        return super.isFinalFieldValueConstant(field, value, tool);
    }
}
