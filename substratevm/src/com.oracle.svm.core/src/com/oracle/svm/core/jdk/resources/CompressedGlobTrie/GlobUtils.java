/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources.CompressedGlobTrie;

import java.util.List;

import com.oracle.svm.util.StringUtil;

public class GlobUtils {

    /* list of glob wildcards we are always escaping because they are not supported yet */
    public static final List<Character> ALWAYS_ESCAPED_GLOB_WILDCARDS = List.of('?', '[', ']', '{', '}');

    public static String transformToTriePath(String resource, String module) {
        String resolvedModuleName;
        if (module == null || module.isEmpty()) {
            resolvedModuleName = "ALL_UNNAMED";
        } else {
            resolvedModuleName = StringUtil.toSlashSeparated(module);
        }

        /* prepare for concatenation */
        if (!resolvedModuleName.endsWith("/")) {
            resolvedModuleName += "/";
        }

        /*
         * if somebody wrote resource like: /foo/bar/** we already append / in resolvedModuleName,
         * and we don't want module//foo/bar/**
         */
        String resolvedResourceName;
        if (resource.startsWith("/")) {
            resolvedResourceName = resource.substring(1);
        } else {
            resolvedResourceName = resource;
        }

        return resolvedModuleName + resolvedResourceName;
    }
}
