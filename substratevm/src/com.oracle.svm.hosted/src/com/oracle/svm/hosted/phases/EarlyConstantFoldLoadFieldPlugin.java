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
package com.oracle.svm.hosted.phases;

import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.nativeimage.impl.clinit.ClassInitializationTracking;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Early constant folding for well-known static fields. These constant foldings do not require and
 * {@link ConstantReflectionProvider}.
 */
public class EarlyConstantFoldLoadFieldPlugin implements NodePlugin {

    private final ResolvedJavaField isImageBuildTimeField;

    public EarlyConstantFoldLoadFieldPlugin(MetaAccessProvider metaAccess) {
        isImageBuildTimeField = metaAccess.lookupJavaField(ReflectionUtil.lookupField(ClassInitializationTracking.class, "IS_IMAGE_BUILD_TIME"));
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        if (field.equals(isImageBuildTimeField)) {
            /*
             * Loads of this field are injected into class initializers to provide stack traces when
             * a class is wrongly initialized at image build time.
             */
            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(false));
            return true;
        } else if (field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
            /*
             * Intercept assertion status: the value of the field during image generation does not
             * matter at all (because it is the hosted assertion status), we instead return the
             * appropriate runtime assertion status.
             */
            boolean assertionsEnabled = SubstrateOptions.getRuntimeAssertionsForClass(field.getDeclaringClass().toJavaName());
            b.addPush(JavaKind.Boolean, ConstantNode.forBoolean(!assertionsEnabled));
            return true;
        }
        return false;
    }
}
