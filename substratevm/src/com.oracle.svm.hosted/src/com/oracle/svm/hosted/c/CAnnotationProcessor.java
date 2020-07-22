/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.codegen.QueryCodeWriter;
import com.oracle.svm.hosted.c.info.InfoTreeBuilder;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.query.QueryResultParser;
import com.oracle.svm.hosted.c.query.RawStructureLayoutPlanner;
import com.oracle.svm.hosted.c.query.SizeAndSignednessVerifier;
import org.graalvm.nativeimage.Platform;

/**
 * Processes native library information for one C Library header file (one { NativeCodeContext }).
 */
public class CAnnotationProcessor {

    private final NativeCodeContext codeCtx;
    private final NativeLibraries nativeLibs;
    private CCompilerInvoker compilerInvoker;
    private Path tempDirectory;

    private NativeCodeInfo codeInfo;
    private QueryCodeWriter writer;

    public CAnnotationProcessor(NativeLibraries nativeLibs, NativeCodeContext codeCtx) {
        this.nativeLibs = nativeLibs;
        this.codeCtx = codeCtx;
        if (!ImageSingletons.contains(CCompilerInvoker.class)) {
            assert CAnnotationProcessorCache.Options.UseCAPCache.getValue();
            return;
        }
        this.compilerInvoker = ImageSingletons.lookup(CCompilerInvoker.class);
        this.tempDirectory = compilerInvoker.tempDirectory;
    }

    public NativeCodeInfo process(CAnnotationProcessorCache cache) {
        InfoTreeBuilder constructor = new InfoTreeBuilder(nativeLibs, codeCtx);
        codeInfo = constructor.construct();
        if (nativeLibs.getErrors().size() > 0) {
            return codeInfo;
        }
        if (CAnnotationProcessorCache.Options.UseCAPCache.getValue()) {
            /* If using a CAP cache, short cut the whole building/compile/execute query. */
            cache.get(nativeLibs, codeInfo);
        } else {
            /*
             * Generate C source file (the "Query") that will produce the information needed (e.g.,
             * size of struct/union and offsets to their fields, value of enum/macros etc.).
             */
            writer = new QueryCodeWriter(tempDirectory);
            Path queryFile = writer.write(codeInfo);
            if (nativeLibs.getErrors().size() > 0) {
                return codeInfo;
            }
            assert Files.exists(queryFile);

            Path binary = compileQueryCode(queryFile);
            if (nativeLibs.getErrors().size() > 0) {
                return codeInfo;
            }

            makeQuery(cache, binary.toString());
            if (nativeLibs.getErrors().size() > 0) {
                return codeInfo;
            }
        }
        RawStructureLayoutPlanner.plan(nativeLibs, codeInfo);

        SizeAndSignednessVerifier.verify(nativeLibs, codeInfo);
        return codeInfo;
    }

    private void makeQuery(CAnnotationProcessorCache cache, String binaryName) {
        Process printingProcess = null;
        try {
            ProcessBuilder pb = new ProcessBuilder().command(binaryName).directory(tempDirectory.toFile());
            printingProcess = pb.start();
            try (InputStream is = printingProcess.getInputStream()) {
                List<String> lines = QueryResultParser.parse(nativeLibs, codeInfo, is);
                if (CAnnotationProcessorCache.Options.NewCAPCache.getValue()) {
                    cache.put(codeInfo, lines);
                }
            }
            printingProcess.waitFor();
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding();
        } finally {
            if (printingProcess != null) {
                printingProcess.destroy();
            }
        }
    }

    private Path compileQueryCode(Path queryFile) {
        /* replace the '.c' or '.cpp' from the end to get the binary name */
        Path fileNamePath = queryFile.getFileName();
        if (fileNamePath == null) {
            throw VMError.shouldNotReachHere(queryFile + " invalid queryFile");
        }
        String fileName = fileNamePath.toString();
        Path binary = queryFile.resolveSibling(compilerInvoker.asExecutableName(fileName.substring(0, fileName.lastIndexOf("."))));
        ArrayList<String> options = new ArrayList<>();
        options.addAll(codeCtx.getDirectives().getOptions());
        if (Platform.includedIn(Platform.LINUX.class)) {
            options.addAll(LibCBase.singleton().getAdditionalQueryCodeCompilerOptions());
        }
        compilerInvoker.compileAndParseError(options, queryFile, binary, this::reportCompilerError, nativeLibs.debug);
        return binary;
    }

    protected void reportCompilerError(ProcessBuilder current, Path queryFile, String line) {
        for (String header : codeCtx.getDirectives().getHeaderFiles()) {
            if (line.contains(header.substring(1, header.length() - 1) + ": No such file or directory")) {
                UserError.abort("Basic header file missing (" + header + "). Make sure headers are available on your system.");
            }
        }
        List<Object> elements = new ArrayList<>();
        int fileNameStart = line.indexOf(queryFile.toString());
        if (fileNameStart != -1) {
            int firstColon = line.indexOf(':', fileNameStart + 1);
            if (firstColon != -1) {
                int secondColon = line.indexOf(':', firstColon + 1);
                if (secondColon != -1) {
                    String lineNumberStr = line.substring(firstColon + 1, secondColon);
                    try {
                        int lineNumber = Integer.parseInt(lineNumberStr);
                        elements.add(writer.getElementForLineNumber(lineNumber));
                        elements.add("C file contents around line " + lineNumber + ":");
                        for (int i = Math.max(lineNumber - 1, 1); i <= lineNumber + 1; i++) {
                            elements.add(queryFile.toString() + ":" + i + ": " + writer.getLine(i));
                        }
                    } catch (NumberFormatException ex) {
                        /* Ignore if not a valid number. */
                    }
                }
            }
        }

        CInterfaceError error = new CInterfaceError(
                        String.format("Error compiling query code (in %s). Compiler command '%s' output included error: %s",
                                        queryFile,
                                        SubstrateUtil.getShellCommandString(current.command(), false),
                                        line),
                        elements);
        nativeLibs.getErrors().add(error);
    }
}
