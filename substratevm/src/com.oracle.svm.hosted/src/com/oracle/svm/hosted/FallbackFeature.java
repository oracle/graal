/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.AfterAnalysisAccessImpl;
import com.oracle.svm.hosted.image.AbstractBootImage;

@AutomaticFeature
public class FallbackFeature implements Feature {
    private static final int ForceFallback = 10;
    private static final int Automatic = 5;
    private static final int NoFallback = 0;

    static FallbackImageRequest reportFallback(String message) {
        throw new FallbackImageRequest(message);
    }

    static UserError.UserException reportAsFallback(RuntimeException original) {
        if (Options.FallbackThreshold.getValue() == NoFallback) {
            throw UserError.abort(original.getMessage(), original);
        }
        throw reportFallback("Abort stand-alone image build. " + original.getMessage());
    }

    public static class Options {
        @APIOption(name = "force-fallback", fixedValue = "" + ForceFallback, customHelp = "force building of fallback image") //
        @APIOption(name = "no-fallback", fixedValue = "" + NoFallback, customHelp = "build stand-alone image or report failure") //
        @Option(help = "Define when fallback-image generation should be used.")//
        public static final HostedOptionKey<Integer> FallbackThreshold = new HostedOptionKey<>(Automatic);
    }

    @SuppressWarnings("serial")
    public static final class FallbackImageRequest extends UserError.UserException {
        private FallbackImageRequest(String message) {
            super(message);
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess a) {
        if (Options.FallbackThreshold.getValue() == ForceFallback) {
            String fallbackArgument = SubstrateOptionsParser.commandArgument(Options.FallbackThreshold, "" + Options.FallbackThreshold.getValue());
            reportFallback("Abort stand-alone image build due to native-image option " + fallbackArgument);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        if (Options.FallbackThreshold.getValue() == NoFallback ||
                        NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue() ||
                        NativeImageOptions.AllowIncompleteClasspath.getValue() ||
                        !AbstractBootImage.NativeImageKind.EXECUTABLE.name().equals(NativeImageOptions.Kind.getValue())) {
            /*
             * Any of the above ensures we unconditionally allow stand-alone image to be generated.
             */
            return;
        }

        AfterAnalysisAccessImpl access = (AfterAnalysisAccessImpl) a;
        if (access.getBigBang().getUnsupportedFeatures().exist()) {
            /* If we detect use of unsupported features we trigger fallback image build. */
            String optionString = SubstrateOptionsParser.commandArgument(PointstoOptions.ReportUnsupportedFeaturesDuringAnalysis, "+");
            reportFallback("Abort stand-alone image build due to unsupported features (use " + optionString + " for report)");
        }
    }
}
