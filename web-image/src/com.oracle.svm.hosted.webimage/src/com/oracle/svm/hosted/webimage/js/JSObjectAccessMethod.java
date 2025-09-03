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

package com.oracle.svm.hosted.webimage.js;

import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.code.NonBytecodeMethod;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Stub method for accesses to {@link org.graalvm.webimage.api.JSObject} fields.
 * <p>
 * Such accesses are delegated to JavaScript code that modifies the JS mirror object.
 * <p>
 * This method is substituted by {@link JSSubstitutionProcessor} and treated as if it was implicitly
 * annotated with {@link org.graalvm.webimage.api.JS @JS}. The jsbody contents are defined in
 * {@link #buildJSCode()}.
 *
 * @see JSObjectAccessMethodSupport
 */
public class JSObjectAccessMethod extends NonBytecodeMethod {
    private final ResolvedJavaField targetField;
    private final boolean isLoad;
    /**
     * Computed lazily.
     */
    private JSBody.JSCode jsCode = null;

    public JSObjectAccessMethod(String name, ResolvedJavaField targetField, boolean isLoad, ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool) {
        super(name, true, declaringClass, signature, constantPool);
        this.targetField = targetField;
        this.isLoad = isLoad;
    }

    /**
     * Constructs the {@link org.graalvm.webimage.api.JS @JS} body code for this method.
     * <p>
     * For loads:
     *
     * <pre>{@code
     * return object.<fieldname>;
     * }</pre>
     *
     * For stores:
     *
     * <pre>{@code
     * object.<fieldname> = value;
     * }</pre>
     */
    private JSBody.JSCode buildJSCode() {
        String fieldAccess = "object." + targetField.getName();
        String body;
        if (isLoad) {
            body = "return " + fieldAccess + ";";
        } else {
            body = fieldAccess + " = value;";
        }

        return new JSBody.JSCode(new String[]{"object", "value"}, body);
    }

    public JSBody.JSCode getJSCode() {
        if (jsCode == null) {
            this.jsCode = buildJSCode();
        }
        return jsCode;
    }

    public boolean isLoad() {
        return isLoad;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        throw GraalError.shouldNotReachHere("This should never be called. This is a 'native' method that is substituted");
    }

    @Override
    public int getModifiers() {
        return Modifier.NATIVE | super.getModifiers();
    }
}
