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
package com.oracle.svm.driver;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BundleSupportArgumentRewriter {
    private static final String OUTPUT_OPTION = "-o";

    private final APIOptionHandler apiOptionHandler;
    private final BundlePathMap.PathStyle sourcePathStyle;
    private final Map<Path, Path> pathCanonicalizations;
    private final Map<Path, Path> pathSubstitutions;
    private final Path rootDir;

    BundleSupportArgumentRewriter(APIOptionHandler apiOptionHandler, BundlePathMap.PathStyle sourcePathStyle, Map<Path, Path> pathCanonicalizations, Map<Path, Path> pathSubstitutions, Path rootDir) {
        this.apiOptionHandler = Objects.requireNonNull(apiOptionHandler);
        this.sourcePathStyle = sourcePathStyle;
        this.pathCanonicalizations = pathCanonicalizations;
        this.pathSubstitutions = pathSubstitutions;
        this.rootDir = rootDir;
    }

    /**
     * Rewrites bundle-stored build arguments so that path-bearing arguments point either to
     * extracted bundle inputs or to platform-native path strings on the replay host.
     */
    List<String> rewrite(List<String> args) {
        ArrayList<String> rewritten = new ArrayList<>(args.size());
        ArrayDeque<String> queue = new ArrayDeque<>(args);
        while (!queue.isEmpty()) {
            DriverPathOptions.Match pathOption = DriverPathOptions.matchAny(queue);
            if (pathOption != null) {
                rewritten.addAll(pathOption.render(pathOption.rewriteValue(sourcePathStyle, this::rewriteSinglePath)));
                continue;
            }

            List<String> apiRewrite = apiOptionHandler.rewriteBundleAPIOptionArgument(queue, this::rewriteSinglePath);
            if (apiRewrite != null) {
                rewritten.addAll(apiRewrite);
                continue;
            }

            String arg = queue.poll();
            if (arg.startsWith(NativeImage.oH)) {
                rewritten.add(apiOptionHandler.rewriteBundleHostedOptionArgument(arg, this::rewriteSinglePath));
                continue;
            }

            if (OUTPUT_OPTION.equals(arg) && !queue.isEmpty()) {
                String rawImageName = queue.poll();
                rewritten.add(arg);
                rewritten.add(rewriteImageName(rawImageName));
                continue;
            }

            rewritten.add(arg);
        }
        return rewritten;
    }

    private String rewriteImageName(String rawImageName) {
        if (BundlePathMap.isSourceAbsolute(rawImageName, sourcePathStyle)) {
            return BundlePathMap.sourceFileName(rawImageName, sourcePathStyle);
        }
        return BundlePathMap.toCurrentPlatformRelativePath(rawImageName, sourcePathStyle);
    }

    /**
     * Rewrites a single source-platform path using portable lookup keys and the recorded bundle
     * canonicalization/substitution maps.
     */
    private String rewriteSinglePath(String rawPath) {
        if (rawPath.isEmpty()) {
            return rawPath;
        }
        Path portablePath = BundlePathMap.portableSourcePath(rawPath, sourcePathStyle);
        Path canonicalPath = pathCanonicalizations.getOrDefault(portablePath, portablePath);
        Path substitutedPath = pathSubstitutions.get(canonicalPath);
        if (substitutedPath != null) {
            return rootDir.resolve(substitutedPath).toString();
        }
        if (sourcePathStyle == BundlePathMap.PathStyle.Windows && BundlePathMap.PathStyle.currentSourceStyle() == BundlePathMap.PathStyle.Windows) {
            return BundlePathMap.decodeToCurrentPlatformPath(portablePath.toString(), sourcePathStyle);
        }
        return portablePath.toString();
    }
}
