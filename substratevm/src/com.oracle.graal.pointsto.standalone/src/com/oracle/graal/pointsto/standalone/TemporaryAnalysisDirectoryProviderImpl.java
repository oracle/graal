/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.CommonTemporaryDirectoryProviderImpl;
import org.graalvm.compiler.options.OptionValues;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class TemporaryAnalysisDirectoryProviderImpl extends CommonTemporaryDirectoryProviderImpl {
    private OptionValues optionValues;

    public TemporaryAnalysisDirectoryProviderImpl(OptionValues optionValues) {
        this.optionValues = optionValues;
    }

    @Override
    public synchronized Path getTemporaryBuildDirectory() {
        if (tempDirectory == null) {
            try {
                String tempName = StandaloneOptions.AnalysisTempDirectory.getValue(optionValues);
                if (tempName == null || tempName.isEmpty()) {
                    tempDirectory = Files.createTempDirectory("Pointsto-");
                    deleteTempDirectory = true;
                } else {
                    tempDirectory = FileSystems.getDefault().getPath(tempName).resolve("Pointsto-" + System.currentTimeMillis());
                    assert !Files.exists(tempDirectory);
                    Files.createDirectories(tempDirectory);
                }
            } catch (IOException ex) {
                throw throwException(null, ex);
            }
        }
        return tempDirectory.toAbsolutePath();
    }

    @Override
    public RuntimeException throwException(Path path, Exception cause) {
        return AnalysisError.shouldNotReachHere(cause);
    }
}
