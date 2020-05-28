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
package com.oracle.svm.core.posix.linux.libc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public class MuslLibC implements LibCBase {

    private Path specFilePath;

    private static final String GCC_MUSL_TEMPLATE_PATH = "specs/gcc-musl-specs.input";
    private static final String GCC_MUSL_SPEC_PATH = "gcc-musl.specs";
    private static final String PATH_PLACEHOLDER = "__BASE_PATH__";

    @Override
    public String getName() {
        return "musl";
    }

    @Override
    public void prepare(Path directory) {
        String useMuslCFlag = SubstrateOptionsParser.commandArgument(AlternativeLibCFeature.LibCOptions.UseMuslC, "+");
        if (!SubstrateOptions.StaticExecutable.getValue()) {
            String staticExecutableFlag = SubstrateOptionsParser.commandArgument(SubstrateOptions.StaticExecutable, "+");
            UserError.abort(useMuslCFlag + " can only be used when producing a static executable. Please add " + staticExecutableFlag + " to the command line arguments, or remove " +
                            useMuslCFlag + ".");
        }
        if (JavaVersionUtil.JAVA_SPEC != 11) {
            UserError.abort(useMuslCFlag + " can only be used with JDK 11.");
        }
        setUpSpecFile(directory);
    }

    @Override
    public List<String> getAdditionalQueryCodeCompilerOptions() {
        /* Avoid the dependency to muslc for builds cross compiling to muslc. */
        return Collections.singletonList("--static");
    }

    @Override
    public List<String> getCCompilerOptions() {
        return Arrays.asList("-specs", getSpecFilePath().toString());
    }

    public void setUpSpecFile(Path directory) {
        VMError.guarantee(specFilePath == null);
        specFilePath = directory.resolve(GCC_MUSL_SPEC_PATH);

        InputStream stream = MuslLibC.class.getResourceAsStream(GCC_MUSL_TEMPLATE_PATH);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        String muslPath = Paths.get(AlternativeLibCFeature.LibCOptions.UseMuslC.getValue()).toAbsolutePath().toString();

        content = content.replaceAll(PATH_PLACEHOLDER, muslPath);
        try {
            Files.write(specFilePath, content.getBytes());
        } catch (IOException e) {
            UserError.abort("Unable to write the specs file to the temporary directory " + directory.toAbsolutePath().toString() + ". Please check if you have write access in the directory.");
        }
    }

    public Path getSpecFilePath() {
        VMError.guarantee(specFilePath != null);
        return specFilePath.toAbsolutePath();
    }

    @Override
    public boolean hasIsolatedNamespaces() {
        return false;
    }
}
