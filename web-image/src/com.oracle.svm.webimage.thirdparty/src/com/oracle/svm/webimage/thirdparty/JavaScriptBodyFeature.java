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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.webimage.codegen.LowerableResource;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.codegen.oop.ClassLowerer;
import com.oracle.svm.hosted.webimage.util.AnnotationUtil;
import com.oracle.svm.util.ModuleSupport;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import net.java.html.js.JavaScriptBody;
import net.java.html.js.JavaScriptResource;

/**
 * Modifies our codegen in order to support {@link JavaScriptBody} and {@link JavaScriptResource}.
 */
public class JavaScriptBodyFeature implements Feature {

    private static String[] getJSResources(ResolvedJavaType type) {
        var requiredJavaScriptResources = AnnotationUtil.getDeclaredAnnotationsByType(type, JavaScriptResource.class, JavaScriptResource.Group.class, JavaScriptResource.Group::value);
        assert requiredJavaScriptResources.size() != 0 || !type.isAnnotationPresent(JavaScriptResource.Group.class) : "Repeated annotation not detected by getDeclaredAnnotationsByType";
        return requiredJavaScriptResources.stream().map(JavaScriptResource::value).toArray(String[]::new);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        /*
         * Grant access to packages used in this package. Since this feature is part of a library
         * that users have to explicitly include, we don't want them to have to add the exports
         * themselves.
         */
        ModuleSupport.accessPackagesToClass(ModuleSupport.Access.EXPORT, JavaScriptBodyFeature.class, false, "jdk.internal.vm.ci", "jdk.vm.ci.meta");

        LowerableResources.thirdParty.add(new LowerableResource("jsbody-compat.js", JavaScriptBodyFeature.class, true));
        ClassLowerer.addResourceProvider(JavaScriptBodyFeature::getJSResources);
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        FeatureImpl.DuringSetupAccessImpl access = (FeatureImpl.DuringSetupAccessImpl) a;
        access.getHostVM().registerNeverInlineTrivialHandler(this::neverInlineTrivial);
        access.registerSubstitutionProcessor(new JavaScriptBodySubstitutitionProcessor());
    }

    private boolean neverInlineTrivial(@SuppressWarnings("unused") AnalysisMethod caller, AnalysisMethod callee) {
        /*
         * Methods annotated with @JavaScriptBody are never trivial
         */
        return AnnotationAccess.isAnnotationPresent(callee, JavaScriptBody.class);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        JavaScriptBodyIntrinsification.findJSMethods(accessImpl);
    }

    static class JavaScriptBodySubstitutitionProcessor extends SubstitutionProcessor {

        private final Map<ResolvedJavaMethod, CustomSubstitutionMethod> callWrappers = new ConcurrentHashMap<>();

        @Override
        public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
            ResolvedJavaMethod wrapper = method;
            if (isJavaScriptBodyStubMethod(method)) {
                wrapper = callWrappers.computeIfAbsent(method, JSBodyStubMethod::new);
            }
            return wrapper;
        }

        private static boolean isJavaScriptBodyStubMethod(ResolvedJavaMethod method) {
            if (AnnotationAccess.isAnnotationPresent(method, JavaScriptBody.class)) {
                return true;
            }
            return false;
        }
    }
}
