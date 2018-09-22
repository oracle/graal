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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.codegen.QueryCodeWriter;
import com.oracle.svm.hosted.c.info.InfoTreeBuilder;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.query.QueryResultParser;
import com.oracle.svm.hosted.c.query.RawStructureLayoutPlanner;
import com.oracle.svm.hosted.c.query.SizeAndSignednessVerifier;

/**
 * Processes native library information for one C Library header file (one { NativeCodeContext }).
 */
public class CAnnotationProcessor extends CCompilerInvoker {

    private final NativeCodeContext codeCtx;

    private NativeCodeInfo codeInfo;
    private QueryCodeWriter writer;

    public CAnnotationProcessor(NativeLibraries nativeLibs, NativeCodeContext codeCtx, Path tempDirectory) {
        super(nativeLibs, tempDirectory);
        this.codeCtx = codeCtx;
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
        List<String> command = new ArrayList<>();
        command.add(binaryName);
        Process printingProcess = null;
        try {
            printingProcess = startCommand(command);
            InputStream is = printingProcess.getInputStream();
            List<String> lines = QueryResultParser.parse(nativeLibs, codeInfo, is);
            is.close();
            if (CAnnotationProcessorCache.Options.NewCAPCache.getValue()) {
                cache.put(codeInfo, lines);
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
        /* remove the '.c' or '.cpp' from the end to get the binary name */
        String binaryName = queryFile.toString().substring(0, queryFile.toString().lastIndexOf("."));
        if (OS.getCurrent() == OS.WINDOWS) {
            binaryName = binaryName + ".exe";
        }
        Path binary = Paths.get(binaryName);
        return compileAndParseError(codeCtx.getDirectives().getOptions(), queryFile.normalize(), binary.normalize());
    }

    @Override
    protected void reportCompilerError(Path queryFile, String line) {
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

        nativeLibs.getErrors().add(new CInterfaceError("Error compiling query code (in " + queryFile + "). Compiler command " + lastExecutedCommand() + " output included error: " + line, elements));
    }
}
