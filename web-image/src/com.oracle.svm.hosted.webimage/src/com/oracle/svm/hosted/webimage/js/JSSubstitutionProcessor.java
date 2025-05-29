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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.webimage.api.JS;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Methods annotated with {@link JS @JS} are substituted with {@link JSStubMethod}.
 * <p>
 * The {@link JSObjectAccessMethod} is implicitly annotated with {@link JS @JS} and is also
 * substituted here.
 */
public class JSSubstitutionProcessor extends SubstitutionProcessor {

    private final Map<ResolvedJavaMethod, CustomSubstitutionMethod> callWrappers = new ConcurrentHashMap<>();

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaMethod wrapper = method;
        if (isJSStubMethod(method)) {
            wrapper = callWrappers.computeIfAbsent(method, JSStubMethod::new);
        }
        return wrapper;
    }

    private static boolean isJSStubMethod(ResolvedJavaMethod method) {
        // If AnalysisMethods appeared here, they would first need to be unwrapped
        assert !(method instanceof AnalysisMethod) : method;
        return method instanceof JSObjectAccessMethod || AnnotationAccess.isAnnotationPresent(method, JS.class);
    }
}
