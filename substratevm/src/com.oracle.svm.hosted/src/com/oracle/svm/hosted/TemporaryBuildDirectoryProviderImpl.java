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
package com.oracle.svm.hosted;

import com.oracle.svm.common.CommonTemporaryDirectoryProviderImpl;
import com.oracle.svm.core.util.VMError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class TemporaryBuildDirectoryProviderImpl extends CommonTemporaryDirectoryProviderImpl {

    @Override
    public synchronized Path getTemporaryBuildDirectory() {
        if (tempDirectory == null) {
            try {
                Optional<Path> tempName = NativeImageOptions.TempDirectory.getValue().lastValue();
                if (tempName.isEmpty()) {
                    tempDirectory = Files.createTempDirectory("SVM-");
                    deleteTempDirectory = true;
                } else {
                    tempDirectory = tempName.get().resolve("SVM-" + System.currentTimeMillis());
                    assert !Files.exists(tempDirectory);
                    Files.createDirectories(tempDirectory);
                }
            } catch (IOException ex) {
                throw VMError.shouldNotReachHere(ex);
            }
        }
        return tempDirectory.toAbsolutePath();
    }

    @Override
    public RuntimeException throwException(Path path, Exception cause) {
        if (path == null) {
            return VMError.shouldNotReachHere(cause);
        } else {
            return VMError.shouldNotReachHere(
                            String.format("Unable to remove the temporary build directory at '%s'. If you are using the '%s' option, you may want to delete the temporary directory manually.",
                                            path, NativeImageOptions.TempDirectory.getName()),
                            cause);
        }
    }
}
