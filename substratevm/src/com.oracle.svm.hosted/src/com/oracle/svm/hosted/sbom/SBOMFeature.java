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
package com.oracle.svm.hosted.sbom;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.HostedOptionKey;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;

/**
 * The Software Bill of Materials (SBOM) feature is only available for Oracle GraalVM. The purpose
 * of this package is to display a helpful error message if the SBOM feature is activated with
 * GraalVM Community Edition.
 */
@AutomaticallyRegisteredFeature
public class SBOMFeature implements InternalFeature {
    protected static final String sbomResourceLocation = "META-INF/native-image/sbom.json";

    public static final class Options {
        public static final String name = "--enable-sbom";
        @APIOption(name = name, defaultValue = "") //
        @Option(help = "Assemble a Software Bill of Materials (SBOM) for the executable or shared library based on the results from the static analysis " +
                        " (only available in Oracle GraalVM). Comma-separated list can contain " +
                        "'" + SBOMValues.StorageOption.embed + "' to store the SBOM in data sections of the binary, " +
                        "'" + SBOMValues.StorageOption.export + "' to save the SBOM in the output directory, " +
                        "'" + SBOMValues.StorageOption.classpath + "' to include the SBOM as a Java resource on the classpath at '" + sbomResourceLocation + "', " +
                        "'" + SBOMValues.strict + "' to abort the build if any type (such as a class, interface, or annotation) cannot be matched to an SBOM component, " +
                        "'" + SBOMValues.cyclonedxFormat + "' (the only format currently supported), and '" + SBOMValues.classLevel + "' to include class-level " +
                        "metadata. The default in Oracle Oracle GraalVM is to embed an SBOM: '" + name + "=" + SBOMValues.StorageOption.embed + "'. " +
                        "To disable the SBOM feature, use '" + name + "=" + SBOMValues.disableSBOM + "' on the command line.", type = OptionType.User, stability = OptionStability.STABLE) //
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> IncludeSBOM = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter(),
                        (options) -> SBOMValueValidator.getInstance().validateSBOMValues(options));
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SBOMValueValidator.class, new UnsupportedSBOMValueValidator());
    }
}
