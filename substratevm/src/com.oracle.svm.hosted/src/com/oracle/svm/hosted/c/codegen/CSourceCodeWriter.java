/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.codegen;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C11;
import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C99;

import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.AnnotatedType;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CTypedef;

import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.NativeImageOptions.CStandards;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.InfoTreeBuilder;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.StructInfo;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CSourceCodeWriter {
    private static final String INDENT4 = "    ";

    private final List<String> lines = new ArrayList<>();
    private final StringBuilder currentLine = new StringBuilder(100);
    private final Path tempDirectory;
    private int indentLevel = 0;

    public CSourceCodeWriter(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public void writeCStandardHeaders() {
        if (NativeImageOptions.getCStandard().compatibleWith(C99)) {
            includeFiles(Collections.singletonList("<stdbool.h>"));
        }
        if (NativeImageOptions.getCStandard().compatibleWith(C11)) {
            includeFiles(Collections.singletonList("<stdint.h>"));
        }
    }

    public int currentLineNumber() {
        return lines.size() + 1;
    }

    public String getLine(int lineNumber) {
        int index = lineNumber - 1;
        if (index >= 0 && index < lines.size()) {
            return lines.get(index);
        }
        return "";
    }

    public void includeFiles(List<String> headerFiles) {
        for (String headerFile : headerFiles) {
            String headerFileName;
            if (headerFile.startsWith("<") && headerFile.endsWith(">")) {
                headerFileName = headerFile.substring(1, headerFile.length() - 1);
                Path headerFilePath = Paths.get(headerFileName);
                appendln("#include " + "<" + headerFilePath + ">");
            } else if (headerFile.startsWith("\"") && headerFile.endsWith("\"")) {
                headerFileName = headerFile.substring(1, headerFile.length() - 1);
                Path headerFilePath = Paths.get(headerFileName);
                appendln("#include " + "\"" + headerFilePath + "\"");
            } else {
                throw UserError.abort("Header file name must be surrounded by <...> or \"...\": %s", headerFile);
            }
        }
    }

    public CSourceCodeWriter printf(String firstArg, String secondArg) {
        append("printf(\"" + firstArg + "\\n\", " + secondArg + ")");
        return this;
    }

    public CSourceCodeWriter printf(String firstArg, String secondArg, String thirdArg) {
        append("printf(\"" + firstArg + "\\n\", " + secondArg + ", " + thirdArg + ")");
        return this;
    }

    public CSourceCodeWriter indents() {
        assert currentLine.isEmpty() : "indenting in the middle of a line";
        for (int i = 0; i < indentLevel; i++) {
            append(INDENT4);
        }
        return this;
    }

    public void indent() {
        indentLevel++;
    }

    public void outdent() {
        indentLevel--;
    }

    public void semicolon() {
        appendln(";");
    }

    public void appendln(String str) {
        append(str);
        appendln();
    }

    public void appendln() {
        assert currentLine.indexOf("\n") == -1 : "line must not contain newline character";
        lines.add(currentLine.toString());
        currentLine.delete(0, currentLine.length());
    }

    public CSourceCodeWriter append(String str) {
        assert !str.contains("\n") : "line must not contain newline character";
        currentLine.append(str);
        return this;
    }

    public Path writeFile(String fileName) {
        assert currentLine.isEmpty() : "last line not finished";

        Path outputFile = tempDirectory.resolve(fileName);
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (ClosedByInterruptException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        }

        return outputFile;
    }

    public static String toCTypeName(ResolvedJavaMethod method, ResolvedJavaType type, AnnotatedType annotatedType, boolean isConst, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        CTypedef typeDef = annotatedType.getAnnotation(CTypedef.class);
        if (typeDef != null) {
            return (isConst ? "const " : "") + typeDef.name();
        }

        JavaKind kind = type.getJavaKind();
        if (kind.isObject() && !nativeLibs.isIntegerType(type)) {
            return (isConst ? "const " : "") + cTypeForObject(type, metaAccess, nativeLibs);
        }

        UserError.guarantee(!isConst, "Only pointer types can be const. %s in method %s is not a pointer type.", type, method);
        return cTypeForPrimitive(method, type, annotatedType, nativeLibs);
    }

    private static String cTypeForObject(ResolvedJavaType type, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        ElementInfo elementInfo = nativeLibs.findElementInfo(type);
        if (elementInfo instanceof PointerToInfo pointerToInfo) {
            return (pointerToInfo.getTypedefName() != null ? pointerToInfo.getTypedefName() : pointerToInfo.getName() + "*");
        } else if (elementInfo instanceof StructInfo structInfo) {
            return structInfo.getTypedefName() != null ? structInfo.getTypedefName() : structInfo.getName() + "*";
        } else if (elementInfo instanceof EnumInfo) {
            return elementInfo.getName();
        } else if (isFunctionPointer(metaAccess, type) && InfoTreeBuilder.getTypedefName(type) != null) {
            return InfoTreeBuilder.getTypedefName(type);
        } else {
            return "void *";
        }
    }

    private static String cTypeForPrimitive(ResolvedJavaMethod method, ResolvedJavaType type, AnnotatedType annotatedType, NativeLibraries nativeLibs) {
        boolean c11Compatible = NativeImageOptions.getCStandard().compatibleWith(C11);
        String prefix = "";
        if (isUnsigned(annotatedType)) {
            prefix = c11Compatible ? "u" : "unsigned ";
        }

        JavaKind javaKind = type.getJavaKind();
        switch (javaKind) {
            case Byte:
                return prefix + (c11Compatible ? "int8_t" : "char");
            case Short:
            case Char:
                return prefix + (c11Compatible ? "int16_t" : "short");
            case Int:
                return prefix + (c11Compatible ? "int32_t" : "int");
            case Long:
                return prefix + (c11Compatible ? "int64_t" : "long long int");
        }

        UserError.guarantee(prefix.isEmpty(), "Only integer types can be annotated with @%s. %s in method %s is not an integer type.",
                        org.graalvm.nativeimage.c.type.CUnsigned.class.getSimpleName(), type, method);
        switch (javaKind) {
            case Boolean:
                if (NativeImageOptions.getCStandard().compatibleWith(CStandards.C99)) {
                    return "bool";
                } else {
                    return "int";
                }
            case Float:
                return "float";
            case Double:
                return "double";
            case Void:
                return "void";
            case Object:
                /* SignedWord or UnsignedWord. */
                assert nativeLibs.isIntegerType(type);
                return nativeLibs.isSigned(type) ? "ssize_t" : "size_t";
            default:
                throw VMError.shouldNotReachHere("Unexpected Java kind " + javaKind);
        }
    }

    private static boolean isUnsigned(AnnotatedType type) {
        return type.isAnnotationPresent(org.graalvm.nativeimage.c.type.CUnsigned.class) || type.isAnnotationPresent(com.oracle.svm.core.c.CUnsigned.class);
    }

    private static boolean isFunctionPointer(MetaAccessProvider metaAccess, ResolvedJavaType type) {
        boolean functionPointer = metaAccess.lookupJavaType(CFunctionPointer.class).isAssignableFrom(type);
        return functionPointer && Arrays.stream(type.getDeclaredMethods(false)).anyMatch(v -> v.getDeclaredAnnotation(InvokeCFunctionPointer.class) != null);
    }

    /**
     * Appends definition of "flags" like macro.
     */
    public void appendMacroDefinition(String preDefine) {
        appendln("#ifndef " + preDefine);
        appendln("#define " + preDefine);
        appendln("#endif");
    }
}
