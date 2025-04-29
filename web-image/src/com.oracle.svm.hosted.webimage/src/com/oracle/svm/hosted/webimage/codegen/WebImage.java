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

package com.oracle.svm.hosted.webimage.codegen;

import static com.oracle.svm.hosted.webimage.codegen.WebImageCodeGen.CODE_GEN_SCOPE_NAME;
import static com.oracle.svm.hosted.webimage.codegen.WebImageJSCodeGen.WriteTimer;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.webimage.WebImageCodeCache;
import com.oracle.svm.hosted.webimage.WebImageGenerator;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.metrickeys.ImageBreakdownMetricKeys;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.util.metrics.ImageMetricsCollector;

import jdk.graal.compiler.debug.DebugContext;

public class WebImage extends AbstractImage {
    protected WebImageCodeGen webImageCodeGen;

    private final ImageClassLoader imageClassLoader;

    protected final HostedMethod mainEntryPoint;

    protected long imageHeapSize = -1;

    public WebImage(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ImageClassLoader imageClassLoader, HostedMethod mainEntryPoint) {
        super(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, imageClassLoader.getClassLoader());
        this.imageClassLoader = imageClassLoader;
        this.mainEntryPoint = mainEntryPoint;
    }

    private static int getConstantsSize() {
        String scopeName;
        if (WebImageProviders.isLabelInjectionEnabled()) {
            scopeName = ImageMetricsCollector.CLOSURE_SCOPE_NAME;
        } else if (!WebImageOptions.ClosureCompiler.getValue()) {
            scopeName = ImageMetricsCollector.PRE_CLOSURE_SCOPE_NAME;
        } else {
            // Note - We cannot determine constants size when we use Closure compiler without label
            // injection.
            return 0;
        }

        String qualifiedScopeName = LoggerContext.getQualifiedScopeName(CODE_GEN_SCOPE_NAME, scopeName);
        Map<String, Number> savedCounters = LoggerContext.currentContext().getSavedCounters(qualifiedScopeName, ImageMetricsCollector.SAVED_SIZE_BREAKDOWN_KEYS);

        return savedCounters.get(ImageBreakdownMetricKeys.CONSTANTS_SIZE.getName()).intValue();
    }

    @SuppressWarnings("try")
    @Override
    public void build(String imageName, DebugContext debug) {
        WebImageProviders webImageProviders = ImageSingletons.lookup(WebImageProviders.class);
        try (Timer.StopTimer codeGenTimer = TimerCollection.createTimerAndStart(WebImageGenerator.CodegenTimer)) {
            webImageCodeGen = WebImageCodeGen.generateCode(
                            (WebImageCodeCache) codeCache,
                            entryPoints,
                            mainEntryPoint,
                            webImageProviders,
                            debug,
                            WebImageHostedConfiguration.get(),
                            imageClassLoader);
        }
    }

    @Override
    @SuppressWarnings("try")
    public LinkerInvocation write(DebugContext debug, Path outputDirectory, Path tempDirectory, String imageName, FeatureImpl.BeforeImageWriteAccessImpl config) {
        try (Timer.StopTimer t = TimerCollection.createTimerAndStart(WriteTimer)) {
            Collection<Path> paths = webImageCodeGen.writeFiles();
            this.imageFileSize = (int) paths.stream().map(Path::toFile).mapToLong(File::length).sum();
            this.imageHeapSize = getConstantsSize();
            this.debugInfoSize = (int) paths.stream().filter(p -> p.getFileName().toString().endsWith(".map")).map(Path::toFile).mapToLong(File::length).sum();
        }

        return null;
    }

    @Override
    public String[] makeLaunchCommand(NativeImageKind k, String imageName, Path binPath, Path workPath, Method method) {
        return new String[0];
    }

    @Override
    public long getImageHeapSize() {
        return imageHeapSize;
    }

    @Override
    public ObjectFile getObjectFile() {
        return null;
    }
}
