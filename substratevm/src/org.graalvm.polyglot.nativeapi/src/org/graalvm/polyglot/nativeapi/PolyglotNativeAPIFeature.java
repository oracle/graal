/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.nativeapi;

import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C11;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.util.FileUtils;

public class PolyglotNativeAPIFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!NativeImageOptions.getCStandard().compatibleWith(C11)) {
            throw UserError.abort("Polyglot native API supports only the C11 standard. Pass -H:CStandard=C11 on the command line to make the build work.");
        }
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        List<String> headerFiles = Collections.singletonList("polyglot_types.h");
        Path imagePath = access.getImagePath();
        headerFiles.forEach(headerFile -> {
            Path source = Paths.get(System.getProperty("org.graalvm.polyglot.nativeapi.libraryPath"), headerFile);
            Path destination = imagePath.getParent().resolve(headerFile);
            try {
                Files.copy(source, destination, REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        if (Platform.includedIn(Platform.DARWIN.class)) {
            // on Darwin, change the `id` install name
            String id = System.getProperty("org.graalvm.polyglot.install_name_id");
            if (id == null) {
                String msg = String.format("Warning: no id passed through `org.graalvm.polyglot.install_name_id`:" +
                                "\n%s might include its absolute path as id (see man install_name_tool)", imagePath);
                System.err.println(msg);
            } else {
                List<String> command = Arrays.asList("install_name_tool", "-id", id, imagePath.toString());
                try {
                    int exitCode = FileUtils.executeCommand(command);
                    if (exitCode != 0) {
                        System.err.printf("Failed to set `id` install name. install_name_tool exited with code %d%n", exitCode);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new InterruptImageBuilding("Interrupted in call to: " + String.join(" ", command));
                }
            }
        }
    }
}
