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
package com.oracle.svm.core;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
class SubstrateSegfaultHandlerFeature implements Feature {
    @Override
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(SubstrateSegfaultHandler.class)) {
            return; /* No segfault handler. */
        }
        VMError.guarantee(ImageSingletons.contains(RegisterDumper.class));
        RuntimeSupport.getRuntimeSupport().addStartupHook(SubstrateSegfaultHandler::startupHook);
    }
}

public abstract class SubstrateSegfaultHandler {

    public static class Options {
        @Option(help = "Install segfault handler that prints register contents and full Java stacktrace. Default: enabled for an executable, disabled for a shared library.")//
        static final RuntimeOptionKey<Boolean> InstallSegfaultHandler = new RuntimeOptionKey<>(null);
    }

    static void startupHook() {
        Boolean optionValue = Options.InstallSegfaultHandler.getValue();
        if (optionValue == Boolean.TRUE || (optionValue == null && ImageInfo.isExecutable())) {
            ImageSingletons.lookup(SubstrateSegfaultHandler.class).install();
        }
    }

    /** Installs the platform dependent segfault handler. */
    protected abstract void install();
}
