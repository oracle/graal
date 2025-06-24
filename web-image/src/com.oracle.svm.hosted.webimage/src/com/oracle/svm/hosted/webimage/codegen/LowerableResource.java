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
package com.oracle.svm.hosted.webimage.codegen;

import java.io.File;
import java.io.InputStream;
import java.util.Scanner;

import com.oracle.svm.hosted.webimage.LowerableFile;
import com.oracle.svm.hosted.webimage.codegen.JSIntrinsifyFile.FileData;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.debug.GraalError;

/**
 * Represents a hand-written JavaScript file that is part of Web Image. The content is accessed as a
 * Java resource.
 */
public final class LowerableResource implements LowerableFile {
    private final String name;
    private final Class<?> clazz;

    private final boolean shouldIntrinsify;

    /**
     * Cached {@link FileData} for this resource.
     *
     * Only needed for intrinsified code.
     */
    private FileData data = null;

    /**
     * Construct a new {@code LowerableResource} object (without resolving the resource).
     *
     * @param name resource name, as in {@link Class#getResource(java.lang.String)}
     * @param clazz the class used to resolve the name
     * @param shouldIntrinsify true if the file should be {@linkplain JSIntrinsifyFile
     *            intrinsified}, false if it should be used as-is
     */
    public LowerableResource(String name, Class<?> clazz, boolean shouldIntrinsify) {
        this.name = name;
        this.clazz = clazz;
        this.shouldIntrinsify = shouldIntrinsify;
    }

    public boolean shouldIntrinsify() {
        return shouldIntrinsify;
    }

    /**
     * Whether this resource was registered during setup.
     *
     * All resources that are intrinsified must be registered. For resources that are lowered
     * conditionally (e.g. only for certain targets), the registered status can be used to check
     * whether the resource should be lowered. This avoids repeating the code for the conditional
     * check.
     */
    public boolean isRegistered() {
        return data != null;
    }

    public void markRegistered(FileData fileData) {
        this.data = fileData;
    }

    public FileData getData() {
        return data;
    }

    @Override
    public String getName() {
        return name.substring(name.lastIndexOf(File.separatorChar) + 1);
    }

    @Override
    public void lower(CodeGenTool jsLTools) {
        if (shouldIntrinsify) {
            assert isRegistered() : "Resource " + name + " must be registered.";
            assert data.isProcessed() : "Resource " + name + " was not processed.";
            jsLTools.getCodeBuffer().emitText(data.getProcessed());
        } else {
            CodeBuffer codeBuffer = jsLTools.getCodeBuffer();
            try (Scanner s = new Scanner(getStream())) {
                while (s.hasNextLine()) {
                    String line = s.nextLine();
                    codeBuffer.emitText(line);
                    codeBuffer.emitNewLine();
                }
            }
        }
    }

    public InputStream getStream() {
        InputStream stream = clazz.getResourceAsStream(name);

        GraalError.guarantee(stream != null, "Couldn't find lowerable resource %s relative to %s", name, clazz.getName());

        return stream;
    }
}
