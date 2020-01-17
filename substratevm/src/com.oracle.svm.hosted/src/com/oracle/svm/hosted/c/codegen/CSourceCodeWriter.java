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
package com.oracle.svm.hosted.c.codegen;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C11;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;

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
    public static final String C_SOURCE_FILE_EXTENSION = ".c";
    public static final String CXX_SOURCE_FILE_EXTENSION = ".cpp";

    private final List<String> lines;
    private final StringBuilder currentLine;

    private int indentLevel = 0;
    protected final Path tempDirectory;

    public CSourceCodeWriter(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
        this.lines = new ArrayList<>();
        this.currentLine = new StringBuilder(100);
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
            String headerFileName = null;
            if (headerFile.startsWith("<") && headerFile.endsWith(">")) {
                headerFileName = headerFile.substring(1, headerFile.length() - 1);
                Path headerFilePath = Paths.get(headerFileName);
                appendln("#include " + "<" + headerFilePath.toString() + ">");
            } else if (headerFile.startsWith("\"") && headerFile.endsWith("\"")) {
                headerFileName = headerFile.substring(1, headerFile.length() - 1);
                Path headerFilePath = Paths.get(headerFileName);
                appendln("#include " + "\"" + headerFilePath.toString() + "\"");
            } else {
                throw UserError.abort("header file name must be surrounded by <...> or \"...\": " + headerFile);
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
        assert currentLine.length() == 0 : "indenting in the middle of a line";
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
        return writeFile(fileName, true);
    }

    public Path writeFile(String fileName, boolean ensureCorrectExtension) {
        assert currentLine.length() == 0 : "last line not finished";

        String fixedFileName = fileName;
        String srcFileExtension = Platform.includedIn(Platform.WINDOWS.class) ? CXX_SOURCE_FILE_EXTENSION : C_SOURCE_FILE_EXTENSION;
        if (!fileName.endsWith(srcFileExtension) && ensureCorrectExtension) {
            fixedFileName = fileName.concat(srcFileExtension);
        }

        Path outputFile = tempDirectory.resolve(fixedFileName);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, Charset.forName("UTF-8"))) {
            for (String line : lines) {
                writer.write(line);
                writer.write("\n");
            }
        } catch (ClosedByInterruptException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException ex) {
            throw shouldNotReachHere(ex);
        }

        return outputFile;
    }

    public static String toCTypeName(ResolvedJavaMethod method, ResolvedJavaType type, Optional<String> useSiteTypedef, boolean isConst, boolean isUnsigned, MetaAccessProvider metaAccess,
                    NativeLibraries nativeLibs) {
        boolean isNumericInteger = type.getJavaKind().isNumericInteger();
        UserError.guarantee(isNumericInteger || !isUnsigned,
                        "Only integer types can be unsigned. %s is not an integer type in %s", type, method);

        boolean isUnsignedWord = metaAccess.lookupJavaType(UnsignedWord.class).isAssignableFrom(type);
        boolean isSignedWord = metaAccess.lookupJavaType(SignedWord.class).isAssignableFrom(type);
        boolean isWord = isUnsignedWord || isSignedWord;
        boolean isObject = type.getJavaKind() == JavaKind.Object && !isWord;
        UserError.guarantee(isObject || !isConst,
                        "Only pointer types can be const. %s in method %s is not a pointer type.", type, method);

        if (useSiteTypedef.isPresent()) {
            return (isConst ? "const " : "") + useSiteTypedef.get();
        } else if (isNumericInteger) {
            return toCIntegerType(type, isUnsigned);
        } else if (isUnsignedWord) {
            return "size_t";
        } else if (isSignedWord) {
            return "ssize_t";
        } else if (isObject) {
            return (isConst ? "const " : "") + cTypeForObject(type, metaAccess, nativeLibs);
        } else {
            switch (type.getJavaKind()) {
                case Double:
                    return "double";
                case Float:
                    return "float";
                case Void:
                    return "void";
                default:
                    throw shouldNotReachHere();
            }
        }
    }

    private static String cTypeForObject(ResolvedJavaType type, MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        ElementInfo elementInfo = nativeLibs.findElementInfo(type);
        if (elementInfo instanceof PointerToInfo) {
            PointerToInfo pointerToInfo = (PointerToInfo) elementInfo;
            return (pointerToInfo.getTypedefName() != null ? pointerToInfo.getTypedefName() : pointerToInfo.getName() + "*");
        } else if (elementInfo instanceof StructInfo) {
            StructInfo structInfo = (StructInfo) elementInfo;
            return structInfo.getTypedefName() != null ? structInfo.getTypedefName() : structInfo.getName() + "*";
        } else if (elementInfo instanceof EnumInfo) {
            return elementInfo.getName();
        } else if (isFunctionPointer(metaAccess, type)) {
            return InfoTreeBuilder.getTypedefName(type) != null ? InfoTreeBuilder.getTypedefName(type) : "void *";
        }
        return "void *";
    }

    private static String toCIntegerType(ResolvedJavaType type, boolean isUnsigned) {
        boolean c11Compatible = NativeImageOptions.getCStandard().compatibleWith(C11);
        String prefix = "";
        if (isUnsigned) {
            prefix = c11Compatible ? "u" : "unsigned ";
        }
        switch (type.getJavaKind()) {
            case Boolean:
                if (NativeImageOptions.getCStandard().compatibleWith(CStandards.C99)) {
                    return "bool";
                } else {
                    return "int";
                }
            case Byte:
                return prefix + (c11Compatible ? "int8_t" : "char");
            case Char:
                return prefix + (c11Compatible ? "int16_t" : "short");
            case Short:
                return prefix + (c11Compatible ? "int16_t" : "short");
            case Int:
                return prefix + (c11Compatible ? "int32_t" : "int");
            case Long:
                return prefix + (c11Compatible ? "int64_t" : "long long int");
        }
        throw VMError.shouldNotReachHere("All types integer types should be covered. Got " + type.getJavaKind());
    }

    private static boolean isFunctionPointer(MetaAccessProvider metaAccess, ResolvedJavaType type) {
        boolean functionPointer = metaAccess.lookupJavaType(CFunctionPointer.class).isAssignableFrom(type);
        return functionPointer &&
                        Arrays.stream(type.getDeclaredMethods()).anyMatch(v -> v.getDeclaredAnnotation(InvokeCFunctionPointer.class) != null);
    }

    /**
     * Appends definition of "flags" like macro.
     */
    public void appendMacroDefinition(String preDefine) {
        appendln("#define " + preDefine);
    }
}
