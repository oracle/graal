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
package com.oracle.svm.hosted.phases;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.vm.ci.meta.ResolvedJavaField;

public final class InjectedAccessorsPlugin implements NodePlugin {

    @Override
    public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
        return handleField(b, (AnalysisField) field, false, object, true, null);
    }

    @Override
    public boolean handleLoadStaticField(GraphBuilderContext b, ResolvedJavaField field) {
        return handleField(b, (AnalysisField) field, true, null, true, null);
    }

    @Override
    public boolean handleStoreField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field, ValueNode value) {
        return handleField(b, (AnalysisField) field, false, object, false, value);
    }

    @Override
    public boolean handleStoreStaticField(GraphBuilderContext b, ResolvedJavaField field, ValueNode value) {
        return handleField(b, (AnalysisField) field, true, null, false, value);
    }

    private static boolean handleField(GraphBuilderContext b, AnalysisField field, boolean isStatic, ValueNode receiver, boolean isGet, ValueNode value) {
        InjectAccessors injectAccesors = field.getAnnotation(InjectAccessors.class);
        if (injectAccesors == null) {
            return false;
        }

        var metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
        Class<?> accessorsClass = injectAccesors.value();
        AnalysisType accessorsType = metaAccess.lookupJavaType(accessorsClass);

        String shortName = isGet ? "get" : "set";
        String fieldName = field.getName();
        String longName = shortName + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        AnalysisMethod foundMethod = null;
        for (AnalysisMethod method : accessorsType.getDeclaredMethods(false)) {
            if (method.getName().equals(shortName) || method.getName().equals(longName)) {
                if (foundMethod != null) {
                    error(field, accessorsType, null, "found two methods " + foundMethod.format("%n(%p)") + " and " + method.format("%n(%p)"));
                }
                foundMethod = method;
            }
        }

        if (foundMethod == null) {
            error(field, accessorsType, null, "found no method named " + shortName + " or " + longName);
        }
        if (!foundMethod.isStatic()) {
            error(field, accessorsType, foundMethod, "method is not static");
        }

        int paramIdx = 0;
        if (!isStatic) {
            if (foundMethod.getSignature().getParameterCount(false) < paramIdx + 1) {
                error(field, accessorsType, foundMethod, "not enough parameters");
            }

            AnalysisType actualReceiver = foundMethod.getSignature().getParameterType(paramIdx);
            AnalysisType expectedReceiver = field.getDeclaringClass();
            boolean match = false;
            for (AnalysisType cur = expectedReceiver; cur != null; cur = cur.getSuperclass()) {
                if (actualReceiver.equals(cur)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                error(field, accessorsType, foundMethod, "wrong receiver type: expected " + expectedReceiver.toJavaName(true) + " or a superclass, found " + actualReceiver.toJavaName(true));
            }
            paramIdx++;
        }

        AnalysisType expectedValue = field.getType();
        if (isGet) {
            AnalysisType actualValue = foundMethod.getSignature().getReturnType();
            if (!actualValue.equals(expectedValue)) {
                error(field, accessorsType, foundMethod, "wrong return type: expected " + expectedValue.toJavaName(true) + ", found " + actualValue.toJavaName(true));
            }

        } else {
            if (foundMethod.getSignature().getParameterCount(false) < paramIdx + 1) {
                error(field, accessorsType, foundMethod, "not enough parameters");
            }

            AnalysisType actualValue = foundMethod.getSignature().getParameterType(paramIdx);
            if (!actualValue.equals(expectedValue)) {
                error(field, accessorsType, foundMethod, "wrong value type: expected " + expectedValue.toJavaName(true) + ", found " + actualValue.toJavaName(true));
            }
            paramIdx++;
        }

        if (foundMethod.getSignature().getParameterCount(false) != paramIdx) {
            error(field, accessorsType, foundMethod, "Wrong number of parameters: expected " + paramIdx + ", found " + foundMethod.getSignature().getParameterCount(false));
        }

        List<ValueNode> args = new ArrayList<>();
        if (!isStatic) {
            args.add(receiver);
        }
        if (!isGet) {
            args.add(value);
        }
        b.handleReplacedInvoke(InvokeKind.Static, foundMethod, args.toArray(new ValueNode[args.size()]), false);

        return true;
    }

    private static void error(AnalysisField field, AnalysisType accessorsType, AnalysisMethod method, String msg) {
        throw VMError.shouldNotReachHere("Error in @" + InjectAccessors.class.getSimpleName() + " handling of field " + field.format("%H.%n") + ", accessors class " +
                        accessorsType.toJavaName(true) + (method == null ? "" : ", method " + method.format("%n(%p)")) + ": " + msg);
    }
}
