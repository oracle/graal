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

import com.oracle.svm.hosted.dashboard.ToJson.JsonObject;
import com.oracle.svm.hosted.dashboard.ToJson.JsonString;
import com.oracle.svm.hosted.dashboard.ToJson.JsonNumber;
import com.oracle.svm.hosted.dashboard.ToJson.JsonValue;
import com.oracle.svm.hosted.dashboard.ToJson.JsonArray;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.CompileQueue;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

class CodeBreakdownJsonObject extends JsonObject {

    private final Feature.AfterCompilationAccess access;

    private static final String INFO_NAME = "name";
    private static final List<String> NAMES = Arrays.asList(INFO_NAME, "size");

    CodeBreakdownJsonObject(Feature.AfterCompilationAccess access) {
        this.access = access;
    }

    @Override
    Stream<String> getNames() {
        return Arrays.asList("code-size").stream();
    }

    @Override
    JsonValue getValue(String name) {
        return JsonArray.get(((FeatureImpl.AfterCompilationAccessImpl) access).getCompilationTasks().stream().map(MethodJsonObject::new));
    }

    private static class MethodJsonObject extends JsonObject {

        private final CompileQueue.CompileTask task;

        MethodJsonObject(CompileQueue.CompileTask task) {
            this.task = task;
        }

        @Override
        Stream<String> getNames() {
            return NAMES.stream();
        }

        @Override
        JsonValue getValue(String name) {
            return name == INFO_NAME
                            ? JsonString.get(task.method.format("%H.%n(%p) %r"))
                            : JsonNumber.get(task.result.getTargetCodeSize());
        }
    }
}
