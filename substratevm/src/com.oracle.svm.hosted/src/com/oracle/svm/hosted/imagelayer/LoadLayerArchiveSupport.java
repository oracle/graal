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
package com.oracle.svm.hosted.imagelayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.util.DiffTool;
import com.oracle.svm.hosted.util.DiffTool.DiffResult.Kind;
import com.oracle.svm.util.LogUtils;

public class LoadLayerArchiveSupport extends LayerArchiveSupport {

    public LoadLayerArchiveSupport(String layerName, Path layerFile, Path tempDir, ArchiveSupport archiveSupport) {
        super(layerName, layerFile, tempDir.resolve(LAYER_TEMP_DIR_PREFIX + "load"), archiveSupport);
        this.archiveSupport.expandJarToDir(layerFile, layerDir);
        layerProperties.loadAndVerify();
        loadBuilderArgumentsFile();
    }

    private void loadBuilderArgumentsFile() {
        try (Stream<String> lines = Files.lines(getBuilderArgumentsFilePath())) {
            lines.forEach(builderArguments::add);
        } catch (IOException e) {
            throw UserError.abort("Unable to load builder arguments from file " + getBuilderArgumentsFilePath());
        }
    }

    protected void validateLayerFile(Path layerFile) {
        super.validateLayerFile(layerFile);

        if (!Files.isReadable(layerFile)) {
            throw UserError.abort("The given layer file " + layerFile + " cannot be read.");
        }
    }

    public void verifyCompatibility(NativeImageClassLoaderSupport classLoaderSupport) {
        // var errorMessagePrefix = "Layer Compatibility Error: ";
        var strippedBuilderArguments = builderArguments.stream()
                        .map(argument -> splitArgumentOrigin(argument).argument)
                        .toList();
        List<String> currentBuilderArguments = classLoaderSupport.getHostedOptionParser().getArguments();
        var strippedCurrentBuilderArguments = currentBuilderArguments.stream()
                        .map(argument -> splitArgumentOrigin(argument).argument)
                        .toList();

        var diffResults = DiffTool.diffResults(strippedBuilderArguments, strippedCurrentBuilderArguments);
        for (var diffResult : diffResults) {
            if (diffResult.kind() == Kind.Removed) {
                ArgumentOrigin argumentOrigin = splitArgumentOrigin(diffResult.getEntry(builderArguments, currentBuilderArguments));
                OptionOrigin origin = OptionOrigin.from(argumentOrigin.origin);
                LogUtils.warning("The used parent layer '" + layerProperties.layerName() + "' specified via layer-file '" + layerFile.getFileName() + "'" +
                                " was built with option argument '" + argumentOrigin.argument + "' from " + origin + "." +
                                " The same option argument is required to also be used at the same place for this layered image build.");
            }
        }

        System.out.println("============================================================");
        for (var diffResult : diffResults) {
            System.out.println(diffResult.toString(builderArguments, currentBuilderArguments));
        }
        System.out.println("============================================================");
    }

    record ArgumentOrigin(String argument, String origin) {
    }

    private static ArgumentOrigin splitArgumentOrigin(String argumentWithOrigin) {
        int booleanPrefixPos = argumentWithOrigin.indexOf(':') + 1;
        char booleanPrefixChar = argumentWithOrigin.charAt(booleanPrefixPos);
        boolean booleanOption = booleanPrefixChar == '+' || booleanPrefixChar == '-';
        String argument = argumentWithOrigin;
        String origin = "";
        if (booleanOption) {
            // e.g. -R:+Bar@originInfoB -> -R:+Bar, originInfoB
            int originSeperatorPos = argumentWithOrigin.lastIndexOf('@');
            if (originSeperatorPos >= 0) {
                argument = argumentWithOrigin.substring(0, originSeperatorPos);
                origin = argumentWithOrigin.substring(originSeperatorPos + 1);
            }
        } else {
            // e.g. -H:Foo@originInfoA=value1 -> -H:Foo=value1, originInfoA
            int originSeparatorPos = argumentWithOrigin.indexOf('@');
            if (originSeparatorPos >= 0) {
                int keyValueSeparatorPos = argumentWithOrigin.indexOf('=');
                argument = argumentWithOrigin.substring(0, originSeparatorPos) + argumentWithOrigin.substring(keyValueSeparatorPos);
                origin = argumentWithOrigin.substring(originSeparatorPos + 1, keyValueSeparatorPos);
            }
        }
        return new ArgumentOrigin(argument, origin);
    }
}
