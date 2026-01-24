/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import org.graalvm.nativeimage.c.function.CEntryPoint;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Data from a {@link CEntryPoint} annotation.
 *
 * @param name {@link CEntryPoint#name()}
 * @param documentation {@link CEntryPoint#documentation()}
 * @param exceptionHandler {@link CEntryPoint#exceptionHandler()}
 * @param builtin {@link CEntryPoint#builtin()}
 * @param include {@link CEntryPoint#include()}
 * @param publishAs {@link CEntryPoint#publishAs()}
 */
public record CEntryPointValue(String name,
                List<String> documentation,
                ResolvedJavaType exceptionHandler,
                CEntryPoint.Builtin builtin,
                ResolvedJavaType include,
                CEntryPoint.Publish publishAs) {
    public static CEntryPointValue from(AnnotationValue av) {
        if (av == null) {
            return null;
        }
        return new CEntryPointValue(
                        av.getString("name"),
                        av.getList("documentation", String.class),
                        av.getType("exceptionHandler"),
                        av.getEnum(CEntryPoint.Builtin.class, "builtin"),
                        av.getType("include"),
                        av.getEnum(CEntryPoint.Publish.class, "publishAs"));
    }
}
