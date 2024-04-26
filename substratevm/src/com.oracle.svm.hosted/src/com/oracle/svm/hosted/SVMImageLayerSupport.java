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
package com.oracle.svm.hosted;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.heap.SVMImageLayerLoader;
import com.oracle.svm.hosted.heap.SVMImageLayerWriter;

public final class SVMImageLayerSupport {
    private final SVMImageLayerLoader loader;
    private final SVMImageLayerWriter writer;
    private final boolean loadImageSingletons;
    private final boolean persistImageSingletons;
    private final boolean loadAnalysis;
    private final boolean persistAnalysis;

    private SVMImageLayerSupport(SVMImageLayerLoader loader, SVMImageLayerWriter writer, boolean loadImageSingletons, boolean persistImageSingletons, boolean loadAnalysis, boolean persistAnalysis) {
        this.loader = loader;
        this.writer = writer;
        this.loadImageSingletons = loadImageSingletons;
        this.persistImageSingletons = persistImageSingletons;
        this.loadAnalysis = loadAnalysis;
        this.persistAnalysis = persistAnalysis;
    }

    public static SVMImageLayerSupport singleton() {
        return ImageSingletons.lookup(SVMImageLayerSupport.class);
    }

    public boolean hasLoader() {
        return loader != null;
    }

    public SVMImageLayerLoader getLoader() {
        return loader;
    }

    boolean hasWriter() {
        return writer != null;
    }

    public SVMImageLayerWriter getWriter() {
        return writer;
    }

    public boolean loadImageSingletons() {
        return loadImageSingletons;
    }

    public boolean persistImageSingletons() {
        return persistImageSingletons;
    }

    public boolean loadAnalysis() {
        return loadAnalysis;
    }

    public boolean persistAnalysis() {
        return persistAnalysis;
    }

    static SVMImageLayerSupport initialize(HostedOptionValues values) {
        SVMImageLayerWriter writer = null;
        boolean persistImageSingletons = false;
        boolean persistAnalysis = false;
        if (SubstrateOptions.PersistImageLayerAnalysis.getValue(values) || SubstrateOptions.PersistImageLayerSingletons.getValue(values)) {
            writer = new SVMImageLayerWriter();
            persistImageSingletons = SubstrateOptions.PersistImageLayerSingletons.getValue(values);
            persistAnalysis = SubstrateOptions.PersistImageLayerAnalysis.getValue(values);
        }
        boolean loadImageSingletons = false;
        boolean loadAnalysis = false;
        SVMImageLayerLoader loader = null;
        if (SubstrateOptions.LoadImageLayer.hasBeenSet(values)) {
            loader = new SVMImageLayerLoader(SubstrateOptions.LoadImageLayer.getValue(values).values());
            loadImageSingletons = SubstrateOptions.LoadImageLayerSingletons.getValue(values);
            loadAnalysis = SubstrateOptions.LoadImageLayerAnalysis.getValue(values);
        }

        return new SVMImageLayerSupport(loader, writer, loadImageSingletons, persistImageSingletons, loadAnalysis, persistAnalysis);
    }
}
