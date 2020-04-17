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
package com.oracle.svm.hosted.dashboard;

import com.oracle.shadowed.com.google.gson.JsonArray;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.meta.HostedMethod;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.shadowed.com.google.gson.JsonObject;

import java.util.Collection;

class CodeBreakdownDumper {

    JsonObject dump(Feature.AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl config = (FeatureImpl.AfterCompilationAccessImpl) access;
        final Collection<CompileQueue.CompileTask> compilationTasks = config.getCompilationTasks();
        JsonArray methodInfos = new JsonArray(compilationTasks.size());
        for (CompileQueue.CompileTask task : compilationTasks) {
            JsonObject methodInfo = new JsonObject();
            final HostedMethod method = task.method;
            final int targetSize = task.result.getTargetCodeSize();
            methodInfo.addProperty("name", method.format("%H.%n(%p) %r"));
            methodInfo.addProperty("size", targetSize);
            methodInfos.add(methodInfo);
        }
        final JsonObject breakdown = new JsonObject();
        breakdown.add("code-size", methodInfos);
        return breakdown;
    }
}
