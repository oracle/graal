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
package com.oracle.truffle.llvm.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Linker {

    public static void main(String[] args) {
        try {
            String outputFileName = null;
            final Collection<String> bitcodeFileNames = new ArrayList<>();

            for (int n = 0; n < args.length; n++) {
                final String arg = args[n];

                switch (arg) {
                    case "-o":
                        if (n + 1 >= args.length) {
                            throw new Exception("-o needs to be followed by a file name");
                        }

                        outputFileName = args[n + 1];
                        n++;
                        break;

                    default:
                        bitcodeFileNames.add(arg);
                        break;
                }
            }

            if (outputFileName == null) {
                outputFileName = "out.su";
            }

            link(outputFileName, bitcodeFileNames);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }

    }

    public static void link(String outputFileName, Collection<String> bitcodeFileNames) throws IOException {
        final byte[] buffer = new byte[1024];

        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(outputFileName))) {
            for (String bitcodeFileName : bitcodeFileNames) {
                final File bitcodeFile = new File(bitcodeFileName);

                try (InputStream inputStream = new FileInputStream(bitcodeFile)) {
                    outputStream.putNextEntry(new ZipEntry(bitcodeFile.getName()));

                    while (true) {
                        int count = inputStream.read(buffer);

                        if (count == -1) {
                            break;
                        }

                        outputStream.write(buffer, 0, count);
                    }
                }
            }
        }
    }

}
