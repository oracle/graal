/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.hosted.c.CGlobalDataFeature;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CFunctionLinkages {
    public static CFunctionLinkages singleton() {
        return ImageSingletons.lookup(CFunctionLinkages.class);
    }

    private final Map<String, CGlobalDataInfo> nameToFunction = new ConcurrentHashMap<>();

    CFunctionLinkages() {
    }

    public CGlobalDataInfo addOrLookupMethod(ResolvedJavaMethod method) {
        if (method.getAnnotation(NodeIntrinsic.class) != null || method.getAnnotation(Word.Operation.class) != null) {
            return null;
        }
        return nameToFunction.computeIfAbsent(linkageName(method), symbolName -> {
            CGlobalData<CFunctionPointer> linkage = CGlobalDataFactory.forSymbol(symbolName);
            return CGlobalDataFeature.singleton().registerAsAccessedOrGet(linkage);
        });
    }

    private static String linkageName(ResolvedJavaMethod method) {
        String annotationLinkageName = getLinkageNameFromAnnotation(method);
        if (annotationLinkageName != null && !annotationLinkageName.isEmpty()) {
            return annotationLinkageName;
        }
        return method.getName();
    }

    private static String getLinkageNameFromAnnotation(ResolvedJavaMethod method) {
        CFunction cFunctionAnnotation = method.getAnnotation(CFunction.class);
        if (cFunctionAnnotation != null) {
            return cFunctionAnnotation.value();
        }
        return null;
    }
}

@AutomaticFeature
class CFunctionLinkagesFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CFunctionLinkages.class, new CFunctionLinkages());
    }
}
