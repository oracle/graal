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
package com.oracle.svm.hosted.webimage.phases;

import java.util.EnumMap;
import java.util.Locale;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.webimage.api.JSResource;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSBodyWithExceptionNode;
import com.oracle.svm.webimage.functionintrinsics.JSCallNode;
import com.oracle.svm.webimage.functionintrinsics.JSSystemFunction;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class WebImageHostedGraphKit extends HostedGraphKit {
    public static final EnumMap<JavaKind, JSSystemFunction> TO_JS_CONVERSION_FUNCTIONS = new EnumMap<>(JavaKind.class);

    static {
        for (JavaKind value : JavaKind.values()) {
            if (value == JavaKind.Illegal || value == JavaKind.Void) {
                continue;
            }
            Stamp returnStamp = StampFactory.objectNonNull();
            TO_JS_CONVERSION_FUNCTIONS.put(value, new JSSystemFunction(("$$$" + value.getTypeChar()).toLowerCase(Locale.ROOT), returnStamp, value));
        }
    }

    @SuppressWarnings("unused")
    public WebImageHostedGraphKit(DebugContext debug, HostedProviders providers, ResolvedJavaMethod method, GraphProvider.Purpose purpose) {
        super(debug, providers, method);
    }

    public JSCallNode createJSFunctionCall(JSSystemFunction function, ValueNode... args) {
        return append(new JSCallNode(function, function.stamp(), args));
    }

    public ValueNode createConvertToJs(ValueNode value, JavaKind kind) {
        return createJSFunctionCall(TO_JS_CONVERSION_FUNCTIONS.get(kind), value);
    }

    public JSBody createJSBody(JSBody.JSCode jsCode, ResolvedJavaMethod method, ValueNode[] argNodes, Stamp returnStamp, FrameStateBuilder state, int bci,
                    ResolvedJavaMethod exceptionHandler) {
        ExceptionObjectNode exceptionObject = createExceptionObjectNode(state, bci);
        boolean declaresResource = AnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), JSResource.class);
        JSBodyWithExceptionNode jsBody = append(new JSBodyWithExceptionNode(jsCode, method, argNodes, returnStamp, exceptionObject, declaresResource));
        startWithException(jsBody, exceptionObject, state, bci);
        exceptionPart();

        if (exceptionHandler == null) {
            // If there's no exception handler. Immediately rethrow
            append(new UnwindNode(exceptionObject()));
        } else {
            // exception path of JSBodyWithExceptionNode
            createInvokeWithExceptionAndUnwind(exceptionHandler, CallTargetNode.InvokeKind.Static, getFrameState(), bci(), exceptionObject());
            append(new UnreachableControlSinkNode());
        }

        endWithException();
        return jsBody;
    }

}
