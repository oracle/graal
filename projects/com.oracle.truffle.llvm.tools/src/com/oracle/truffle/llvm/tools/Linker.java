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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.oracle.truffle.llvm.runtime.LLVMLogger;

public class Linker {

    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        try {
            String outputFileName = null;
            final Collection<String> libraryNames = new ArrayList<>();
            final Collection<String> bitcodeFileNames = new ArrayList<>();

            int n = 0;

            while (n < args.length) {
                final String arg = args[n];

                if (arg.length() > 0 && arg.charAt(0) == '-') {
                    switch (arg) {
                        case "-h":
                        case "-help":
                        case "--help":
                        case "/?":
                        case "/help":
                            help();
                            break;

                        case "-l":
                            if (n + 1 >= args.length) {
                                throw new Exception("-l needs to be followed by a file name");
                            }

                            libraryNames.add(args[n + 1]);
                            n++;
                            break;

                        case "-o":
                            if (n + 1 >= args.length) {
                                throw new Exception("-o needs to be followed by a file name");
                            }

                            outputFileName = args[n + 1];
                            n++;
                            break;

                        default:
                            throw new Exception("Unknown argument " + arg);
                    }
                } else {
                    bitcodeFileNames.add(arg);
                }

                n++;
            }

            if (outputFileName == null) {
                outputFileName = "out.su";
            }

            link(outputFileName, libraryNames, bitcodeFileNames);
        } catch (Exception e) {
            LLVMLogger.error(e.getMessage());
            System.exit(1);
        }

    }

    private static void help() {
        LLVMLogger.info("su-link [-o out.su] [-l one.so -l two.so ...] one.ll two.ll ...");
        LLVMLogger.info("  Links multiple LLVM bitcode files into a single file which can be loaded by Sulong.");
    }

    public static void link(String outputFileName, Collection<String> libraryNames, Collection<String> bitcodeFileNames) throws IOException, NoSuchAlgorithmException {
        final byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(outputFileName))) {
            outputStream.putNextEntry(new ZipEntry("libs"));

            final PrintStream libsStream = new PrintStream(outputStream);

            for (String libraryName : libraryNames) {
                libsStream.println(libraryName);
            }

            libsStream.flush();

            outputStream.closeEntry();

            for (String bitcodeFileName : bitcodeFileNames) {
                final File bitcodeFile = new File(bitcodeFileName);

                final MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (RandomAccessFile inputFile = new RandomAccessFile(bitcodeFile, "r")) {
                    while (true) {
                        int count = inputFile.read(buffer);

                        if (count == -1) {
                            break;
                        }

                        digest.update(buffer, 0, count);
                    }

                    final String digestString = new BigInteger(1, digest.digest()).toString(16);
                    final String entryName = String.format("%s_%s", digestString, bitcodeFile.getName());

                    outputStream.putNextEntry(new ZipEntry(entryName));

                    inputFile.seek(0);

                    while (true) {
                        int count = inputFile.read(buffer);

                        if (count == -1) {
                            break;
                        }

                        outputStream.write(buffer, 0, count);
                    }

                    outputStream.closeEntry();
                }
            }
        }
    }

}
