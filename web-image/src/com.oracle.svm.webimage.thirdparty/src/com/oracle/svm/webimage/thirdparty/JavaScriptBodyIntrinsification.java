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
package com.oracle.svm.webimage.thirdparty;

import java.lang.reflect.Method;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.AnalysisUtil;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.js.JSBody;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Signature;
import net.java.html.js.JavaScriptBody;

public class JavaScriptBodyIntrinsification {
    /**
     * Intrinsify the body of a JavaScriptBody and JS annotations.
     *
     * @param name A name for the annotation for debugging purposes
     * @param body body of JavaScriptBody (or JS)
     * @param jsLTools loweringTool
     * @return intrinsified body
     */
    public static JSIntrinsifyFile.FileData processJavaScriptBody(String name, String body, JSCodeGenTool jsLTools) {
        JSIntrinsifyFile.FileData fileData = new JSIntrinsifyFile.FileData(name, body);
        collectJSBody(fileData);
        JSIntrinsifyFile.process(fileData, jsLTools.getJSProviders(), JavaScriptBodyIntrinsification::intrinsifyMethod);
        return fileData;
    }

    /**
     * Parse the contents of the given file data and extract intrinsifications using the jsbody
     * syntax.
     */
    public static JSIntrinsifyFile.FileData collectJSBody(JSIntrinsifyFile.FileData fileData) throws IllegalArgumentException {
        String body = fileData.content;
        int where = -1;
        for (;;) {
            int at = body.indexOf("@", where + 1);
            if (at == -1) {
                break;
            }
            int fourDots = body.indexOf("::", at + 1);
            int beginSig = body.indexOf("(", fourDots);
            int endSig = body.indexOf(")", beginSig);

            if (fourDots == -1 || beginSig == -1 || endSig == -1) {
                throw new IllegalArgumentException("Doesn't look like a javacall: " + body.substring(at));
            }

            final JSIntrinsifyFile.TypeIntrinsification ti = new JSIntrinsifyFile.TypeIntrinsification();
            JSIntrinsifyFile.MethodIntrinsification mi = new JSIntrinsifyFile.MethodIntrinsification();

            ti.name = body.substring(at + 1, fourDots);
            mi.name = body.substring(fourDots + 2, beginSig);
            mi.sig = body.substring(beginSig + 1, endSig);

            ti.startIndex = at;
            ti.endIndex = at + 1;

            mi.precedingType = ti;
            mi.startIndex = at + 1;
            mi.endIndex = endSig + 1;

            fileData.addIntrinsic(ti);
            fileData.addIntrinsic(mi);

            where = endSig;
        }
        return fileData;
    }

    public static void intrinsifyMethod(WebImageJSProviders providers, StringBuilder sb, JSIntrinsifyFile.MethodIntrinsification methodIntrinsification, HostedMethod method, String ident) {
        sb.append("methodForJS(");
        if (methodIntrinsification.precedingType.emit) {
            assert methodIntrinsification.resolvedMethod.isStatic() : methodIntrinsification.resolvedMethod;
            HostedType previousType = methodIntrinsification.precedingType.resolvedType;
            sb.append(providers.typeControl().requestTypeName(previousType));
        } else {
            // For instance methods, we need to reference the method as a
            // symbol, and not as a string, to prevent minifiers from
            // removing the property.
            // We therefore pass a lambda that references the method using
            // the self parameter, to which methodForJS passes the this
            // value.
            sb.append("self => self");
        }
        sb.append('.').append(ident).append(", $$$");
        Signature sig = method.getSignature();
        switch (sig.getReturnKind()) {
            case Object:
                sb.append('l');
                break;
            case Long:
                sb.append('j');
                break;
            case Boolean:
                sb.append('z');
                break;
            default:
                sb.append('x');
                break;
        }
        int arity = sig.getParameterCount(false);
        for (int i = 0; i < arity; i++) {
            char typeChar = sig.getParameterKind(i).getTypeChar();
            sb.append(", $$$").append(typeChar);
        }
        sb.append(")");
    }

    static void findJSMethods(FeatureImpl.DuringAnalysisAccessImpl access) {
        for (Method m : access.findAnnotatedMethods(JavaScriptBody.class)) {
            JavaScriptBody jsb = AnnotationAccess.getAnnotation(m, JavaScriptBody.class);
            JSBody.JSCode jsCode = new JSBody.JSCode(jsb.args(), jsb.body());

            int numAnnotationArgs = jsCode.getArgs().length;
            int numMethodArgs = m.getParameterCount();

            if (numMethodArgs != numAnnotationArgs) {
                throw new GraalError("Method %s and its annotation @%s do not have the same number of arguments (%d vs %d)", m, JavaScriptBody.class.getCanonicalName(), numMethodArgs,
                                numAnnotationArgs);
            }
            if (!jsb.javacall() || !access.isReachable(m)) {
                continue;
            }
            JSIntrinsifyFile.FileData data = collectJSBody(new JSIntrinsifyFile.FileData(m.toString(), jsCode.getBody()));
            if (AnalysisUtil.processFileData(access, data)) {
                access.requireAnalysisIteration();
            }
        }
    }
}
