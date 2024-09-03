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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;

import jdk.graal.compiler.util.json.JsonBuilder;

class CodeBreakdown {
    private boolean built = false;
    private Feature.AfterCompilationAccess access;
    private final Map<String, Integer> data = new HashMap<>();

    CodeBreakdown(Feature.AfterCompilationAccess access) {
        this.access = access;
    }

    private void build() {
        if (built) {
            return;
        }
        for (CompileQueue.CompileTask task : ((FeatureImpl.AfterCompilationAccessImpl) access).getCompilationTasks()) {
            data.merge(task.method.format("%H.%n(%p) %r"), task.result.getTargetCodeSize(), Integer::sum);
        }
        access = null;
        built = true;
    }

    public Map<String, Integer> getData() {
        build();
        return data;
    }

    public void toJson(JsonBuilder.ObjectBuilder builder) throws IOException {
        build();
        try (JsonBuilder.ArrayBuilder array = builder.append("code-size").array()) {
            for (Map.Entry<String, Integer> entry : data.entrySet()) {
                try (JsonBuilder.ObjectBuilder object = array.nextEntry().object()) {
                    object
                                    .append("name", entry.getKey())
                                    .append("size", entry.getValue());
                }
            }
        }
    }
}
