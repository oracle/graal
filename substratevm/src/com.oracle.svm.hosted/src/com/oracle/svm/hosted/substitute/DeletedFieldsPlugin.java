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
package com.oracle.svm.hosted.substitute;

import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class DeletedFieldsPlugin implements NodePlugin {

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        return handleField(b, field, true);
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        return handleField(b, field, true);
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        return handleField(b, field, false);
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return handleField(b, field, false);
    }

    private static boolean handleField(GraphBuilderContext b, ResolvedJavaField field, boolean isLoad) {
        Delete deleteAnnotation = field.getAnnotation(Delete.class);
        if (deleteAnnotation == null) {
            return false;
        }

        String msg = AnnotationSubstitutionProcessor.deleteErrorMessage(field, deleteAnnotation, false);
        ValueNode msgNode = ConstantNode.forConstant(SubstrateObjectConstant.forObject(msg), b.getMetaAccess(), b.getGraph());
        ResolvedJavaMethod reportErrorMethod = b.getMetaAccess().lookupJavaMethod(DeletedMethod.reportErrorMethod);
        b.handleReplacedInvoke(InvokeKind.Static, reportErrorMethod, new ValueNode[]{msgNode}, false);

        JavaKind returnKind = reportErrorMethod.getSignature().getReturnKind();
        if (returnKind != JavaKind.Void) {
            b.pop(returnKind);
        }
        if (isLoad) {
            // Push dummy value.
            b.addPush(field.getJavaKind(), ConstantNode.defaultForKind(field.getJavaKind()));
        }

        return true;
    }
}
