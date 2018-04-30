/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.polyglot.nativeapi;

import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C11;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.graalvm.nativeimage.Feature;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;

public class PolyglotNativeAPIFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!NativeImageOptions.getCStandard().compatibleWith(C11)) {
            throw UserError.abort("Polyglot native API supports only the C11 standard. Pass -H:CStandard=C11 on the command line to make the build work.");
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        Collection<String> headerFiles = Arrays.asList("polyglot_types.h", "polyglot_isolate.h");
        headerFiles.forEach(headerFile -> {
            Path source = Paths.get(System.getProperty("org.graalvm.polyglot.nativeapi.libraryPath"), headerFile);
            Path destination = access.getImagePath().getParent().resolve(headerFile);
            try {
                Files.copy(source, destination, REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        ((BeforeImageWriteAccessImpl) access).registerLinkerInvocationTransformer(li -> {
            li.addInputFile(Paths.get(System.getProperty("org.graalvm.polyglot.nativeapi.nativeLibraryPath"), "polyglot-nativeapi.o").toString());
            return li;
        });

    }
}
