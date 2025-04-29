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

package com.oracle.svm.hosted.webimage.logging.visualization;

import java.io.PrintStream;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;

import jdk.graal.compiler.options.Option;

public abstract class VisualizationSupport {

    public static class Options {
        @Option(help = "Type of image-build statistics at the end of the build (CLI, or empty).")//
        public static final HostedOptionKey<String> Visualization = new HostedOptionKey<>("CLI");

        @Option(help = "Whether to make the CLI image-build visualization monochrome.")//
        public static final HostedOptionKey<Boolean> CLIVisualizationMonochrome = new HostedOptionKey<>(false);
    }

    public static VisualizationSupport get() {
        return ImageSingletons.lookup(VisualizationSupport.class);
    }

    public abstract void visualize(PrintStream printStream);

}

@AutomaticallyRegisteredFeature
class VisualizationFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        String id = VisualizationSupport.Options.Visualization.getValue();
        switch (id) {
            case "CLI":
                ImageSingletons.add(VisualizationSupport.class, new CLIVisualizationSupport());
                break;
            case "":
                ImageSingletons.add(VisualizationSupport.class, new NoVisualizationSupport());
                break;
            default:
                throw new IllegalArgumentException("Unknown visualization support: " + id);
        }
    }
}
