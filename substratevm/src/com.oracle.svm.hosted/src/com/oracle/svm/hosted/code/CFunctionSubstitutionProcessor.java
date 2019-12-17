/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.core.c.function.CFunctionOptions;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CFunctionSubstitutionProcessor extends SubstitutionProcessor {
    private final Map<ResolvedJavaMethod, CFunctionCallStubMethod> callWrappers = new ConcurrentHashMap<>();

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaMethod wrapper = method;
        if (method.isNative() && method.isAnnotationPresent(CFunction.class)) {
            wrapper = callWrappers.computeIfAbsent(method, m -> {
                CGlobalDataInfo linkage = CFunctionLinkages.singleton().addOrLookupMethod(m);
                return new CFunctionCallStubMethod(m, linkage, getNewThreadStatus(method));
            });
        }
        return wrapper;
    }

    private static int getNewThreadStatus(ResolvedJavaMethod method) {
        CFunctionOptions cFunctionOptions = method.getAnnotation(CFunctionOptions.class);
        if (cFunctionOptions != null) {
            return StatusSupport.getNewThreadStatus(cFunctionOptions.transition());
        }

        CFunction cFunctionAnnotation = method.getAnnotation(CFunction.class);
        if (cFunctionAnnotation != null) {
            return StatusSupport.getNewThreadStatus(cFunctionAnnotation.transition());
        }

        throw VMError.shouldNotReachHere("Method is not annotated with " + CFunction.class.getSimpleName());
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof CFunctionCallStubMethod) {
            return ((CFunctionCallStubMethod) method).getOriginal();
        }
        return method;
    }
}
