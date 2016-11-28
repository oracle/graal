/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.context.LLVMLanguage;

public class SulongLibrary {

    private static final int BUFFER_SIZE = 1024;

    private File file;

    public SulongLibrary(File file) {
        this.file = file;
    }

    public void readContents(Consumer<String> handleLibrary, Consumer<Source> handleSource) throws IOException {
        final byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry zipEntry = zipStream.getNextEntry();

            while (zipEntry != null) {
                if (zipEntry.isDirectory()) {
                    zipEntry = zipStream.getNextEntry();
                    continue;
                }

                final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

                while (true) {
                    final int read = zipStream.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    byteStream.write(buffer, 0, read);
                }

                final byte[] bytes = byteStream.toByteArray();

                if (zipEntry.getName().equals("libs")) {
                    final String libs = byteStream.toString(StandardCharsets.UTF_8.name());
                    try (Scanner scanner = new Scanner(libs)) {
                        while (scanner.hasNextLine()) {
                            handleLibrary.accept(scanner.nextLine());
                        }
                    }
                } else if (zipEntry.getName().endsWith("." + LLVMLanguage.LLVM_BITCODE_EXTENSION)) {
                    final String sourceCode = Base64.getEncoder().encodeToString(bytes);
                    handleSource.accept(Source.newBuilder(sourceCode).name(file.getPath() + "@" + zipEntry.getName()).mimeType(LLVMLanguage.LLVM_BITCODE_BASE64_MIME_TYPE).build());
                }

                zipEntry = zipStream.getNextEntry();
            }
        }
    }

}
