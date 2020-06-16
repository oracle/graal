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
import static com.oracle.svm.hosted.NativeImageOptions.CStandards.C99;
import static com.oracle.svm.hosted.c.query.QueryResultFormat.DELIMINATOR;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.c.NativeImageHeaderPreamble;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumConstantInfo;
import com.oracle.svm.hosted.c.info.InfoTreeVisitor;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.RawStructureInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.SizableInfo.SignednessValue;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.c.query.QueryResultFormat;

public class QueryCodeWriter extends InfoTreeVisitor {

    private static final String FORMATOR_SIGNED_LONG = "%ld";
    private static final String FORMATOR_UNSIGNED_LONG = "%lu";
    private static final String FORMATOR_LONG_HEX = "%lX";
    private static final String FORMATOR_FLOAT = "%.15e";
    private static final String FORMATOR_STRING = QueryResultFormat.STRING_MARKER + "%s" + QueryResultFormat.STRING_MARKER;

    private final CSourceCodeWriter writer;

    private final List<Object> elementForLineNumber;

    public QueryCodeWriter(Path tempDirectory) {
        writer = new CSourceCodeWriter(tempDirectory);
        elementForLineNumber = new ArrayList<>();
    }

    public Path write(NativeCodeInfo nativeCodeInfo) {
        nativeCodeInfo.accept(this);

        String srcFileExtension = Platform.includedIn(Platform.WINDOWS.class) ? CSourceCodeWriter.CXX_SOURCE_FILE_EXTENSION : CSourceCodeWriter.C_SOURCE_FILE_EXTENSION;
        String sourceFileName = nativeCodeInfo.getName().replaceAll("\\W", "_").concat(srcFileExtension);
        return writer.writeFile(sourceFileName);
    }

    public Object getElementForLineNumber(int lineNumber) {
        int index = lineNumber - 1;
        if (index >= 0 && index < elementForLineNumber.size()) {
            return elementForLineNumber.get(index);
        }
        return null;
    }

    public String getLine(int lineNumber) {
        return writer.getLine(lineNumber);
    }

    @Override
    protected void visitNativeCodeInfo(NativeCodeInfo nativeCodeInfo) {
        NativeImageHeaderPreamble.read(getClass().getClassLoader(), "graal_isolate.preamble")
                        .forEach(writer::appendln);

        for (String preDefine : nativeCodeInfo.getDirectives().getMacroDefinitions()) {
            writer.appendMacroDefinition(preDefine);
        }

        if (!nativeCodeInfo.isBuiltin()) {
            writer.includeFiles(nativeCodeInfo.getDirectives().getHeaderFiles());
        }

        writer.includeFiles(Arrays.asList("<stdio.h>", "<stddef.h>", "<memory.h>"));

        writeCStandardHeaders(writer);

        /* Write general macro definitions. */
        writer.appendln();

        /*
         * On Posix systems we use the GNU C extension, typeof() to prevent the type promotion (to
         * signed int) caused by the inversion operation. On Windows we generate c++ files so we can
         * use decltype.
         */
        writer.appendln("#ifndef _WIN64");
        writer.appendln("#define ISUNSIGNED(a) ((a) >= 0L && (typeof(a)) ~(a) >= 0L)");
        writer.appendln("#else");
        writer.appendln("#define ISUNSIGNED(a) ((a) >= 0L && (decltype(a)) ~(a) >= 0L)");
        writer.appendln("#endif");
        writer.appendln("#define IS_CONST_UNSIGNED(a) (a>=0 ? 1 : 0)");

        /* Write the main function with all the outputs for the children. */
        writer.appendln();
        writer.appendln("int main(void) {");
        writer.indent();
        processChildren(nativeCodeInfo);
        writer.indents().appendln("return 0;");
        writer.outdent();
        writer.appendln("}");
    }

    public static void writeCStandardHeaders(CSourceCodeWriter writer) {
        if (NativeImageOptions.getCStandard().compatibleWith(C99)) {
            if (!Platform.includedIn(Platform.WINDOWS.class)) {
                /*
                 * No stdbool.h in Windows SDK 7.1. If we add native-compiler version detection this
                 * should only be omitted if cl.exe version is < 19.*.
                 */
                writer.includeFiles(Collections.singletonList("<stdbool.h>"));
            }
        }
        if (NativeImageOptions.getCStandard().compatibleWith(C11)) {
            writer.includeFiles(Collections.singletonList("<stdint.h>"));
        }
    }

    @Override
    protected void visitConstantInfo(ConstantInfo constantInfo) {
        switch (constantInfo.getKind()) {
            case INTEGER:
                printUnsignedLong(constantInfo.getSizeInfo(), sizeOf(constantInfo));
                printIsUnsigned(constantInfo.getSignednessInfo(), isConstUnsigned(constantInfo.getName()));
                printLongHex(constantInfo.getValueInfo(), constantInfo.getName());
                break;
            case POINTER:
                printUnsignedLong(constantInfo.getSizeInfo(), sizeOf(constantInfo));
                printLongHex(constantInfo.getValueInfo(), constantInfo.getName());
                break;
            case FLOAT:
                printUnsignedLong(constantInfo.getSizeInfo(), sizeOf(constantInfo));
                printFloat(constantInfo.getValueInfo(), constantInfo.getName());
                break;
            case BYTEARRAY:
            case STRING:
                printString(constantInfo.getValueInfo(), constantInfo.getName());
                break;
            default:
                throw shouldNotReachHere();
        }
    }

    @Override
    protected void visitStructInfo(StructInfo structInfo) {
        if (!structInfo.isIncomplete()) {
            printUnsignedLong(structInfo.getSizeInfo(), sizeOf(structInfo));
        }
        processChildren(structInfo);
    }

    @Override
    protected void visitRawStructureInfo(RawStructureInfo info) {
        /* Nothing to do, do not visit children. */
    }

    @Override
    protected void visitStructFieldInfo(StructFieldInfo fieldInfo) {
        printUnsignedLong(fieldInfo.getSizeInfo(), sizeOfField(fieldInfo));
        printUnsignedLong(fieldInfo.getOffsetInfo(), offsetOfField(fieldInfo));

        if (fieldInfo.getKind() == ElementKind.INTEGER) {
            String tempVar = getUniqueTempVarName(fieldInfo.getParent());
            registerElementForCurrentLine(fieldInfo.getParent().getAnnotatedElement());
            writer.indents().appendln("{");
            writer.indent();
            writer.indents().appendln(fieldInfo.getParent().getName() + " " + tempVar + ";");
            printIsUnsigned(fieldInfo.getSignednessInfo(), isUnsigned(tempVar + "." + fieldInfo.getName()));
            writer.outdent();
            writer.indents().appendln("}");
        }
    }

    @Override
    protected void visitStructBitfieldInfo(StructBitfieldInfo bitfieldInfo) {
        String structName = bitfieldInfo.getParent().getName();
        String bitfieldName = bitfieldInfo.getName();

        /* Wrap everything in a block to contain the local variables. */
        writer.indents().appendln("{");
        writer.indent();
        /* Define a larger structure with some padding space. */
        writer.indents().appendln("struct _w {");
        registerElementForCurrentLine(bitfieldInfo.getParent().getAnnotatedElement());
        writer.indents().appendln("  " + structName + " s;");
        writer.indents().appendln("  long long int pad;");
        writer.indents().appendln("} w;");
        writer.indents().appendln("int is_unsigned;");
        writer.indents().appendln("char *p;");
        writer.indents().appendln("unsigned int byte_offset;");
        writer.indents().appendln("int start_bit, end_bit;");
        writer.indents().appendln("unsigned long long int v;");
        /* Set the structure to 0 bits (including the padding space). */
        writer.indents().appendln("memset(&w, 0x0, sizeof(w));");
        /* Fill the actual bitfield with 1 bits. Maximum size is 64 bits. */
        registerElementForCurrentLine(bitfieldInfo.getAnnotatedElement());
        writer.indents().appendln("w.s." + bitfieldName + " = 0xffffffffffffffff;");
        /* All bits are set, so signed bitfields are < 0; */
        writer.indents().appendln("is_unsigned =  (w.s." + bitfieldName + " >= 0);");
        /* Find the first byte that is used by the bitfield, i.e., the first byte with a bit set. */
        writer.indents().appendln("p = (char*)&w.s;");
        writer.indents().appendln("byte_offset = 0;");
        writer.indents().appendln("while (byte_offset < sizeof(w.s) && *(p + byte_offset) == 0) {");
        writer.indents().appendln("  byte_offset++;");
        writer.indents().appendln("}");
        /* It is an error if no non-zero byte was found. */
        writer.indents().appendln("start_bit = 0, end_bit = 0;");
        writer.indents().appendln("if (byte_offset >= sizeof(w.s)) {");
        writer.indents().appendln("  start_bit = end_bit = -1;");
        writer.indents().appendln("} else {");
        /* Read the 64 bits starting at the byte offset we found. */
        writer.indents().appendln("  v = *((unsigned long long int*) (p + byte_offset));");
        /* Find the first bit that is set. */
        writer.indents().appendln("  while ((v & 0x1) == 0) {");
        writer.indents().appendln("    start_bit++;");
        writer.indents().appendln("    v = v >> 1;");
        writer.indents().appendln("  }");
        /* Find the last bit that is set. */
        writer.indents().appendln("  end_bit = start_bit;");
        writer.indents().appendln("  while (v != 1) {");
        writer.indents().appendln("    end_bit++;");
        writer.indents().appendln("    v = v >> 1;");
        writer.indents().appendln("  }");
        writer.indents().appendln("}");
        /* Print the results. */
        printUnsignedLong(bitfieldInfo.getByteOffsetInfo(), "byte_offset");
        printSignedLong(bitfieldInfo.getStartBitInfo(), "start_bit");
        printSignedLong(bitfieldInfo.getEndBitInfo(), "end_bit");
        printIsUnsigned(bitfieldInfo.getSignednessInfo(), "is_unsigned");
        writer.outdent();
        writer.indents().appendln("}");
    }

    @Override
    protected void visitPointerToInfo(PointerToInfo pointerToInfo) {
        printUnsignedLong(pointerToInfo.getSizeInfo(), sizeOf(pointerToInfo));

        if (pointerToInfo.getKind() == ElementKind.INTEGER) {
            String tempVar = getUniqueTempVarName(pointerToInfo);
            registerElementForCurrentLine(pointerToInfo.getAnnotatedElement());
            writer.indents().appendln("{");
            writer.indent();
            writer.indents().appendln(pointerToInfo.getName() + " " + tempVar + ";");
            printIsUnsigned(pointerToInfo.getSignednessInfo(), isUnsigned(tempVar));
            writer.outdent();
            writer.indents().appendln("}");
        }
    }

    @Override
    protected void visitEnumConstantInfo(EnumConstantInfo constantInfo) {
        assert constantInfo.getKind() == ElementKind.INTEGER;
        printUnsignedLong(constantInfo.getSizeInfo(), sizeOf(constantInfo));
        printIsUnsigned(constantInfo.getSignednessInfo(), isConstUnsigned(constantInfo.getName()));
        printLongHex(constantInfo.getValueInfo(), constantInfo.getName());
    }

    private void printString(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + FORMATOR_STRING, arg).semicolon();
    }

    private void printLongHex(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + FORMATOR_LONG_HEX, arg).semicolon();
    }

    private void printSignedLong(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + FORMATOR_SIGNED_LONG, arg).semicolon();
    }

    private void printUnsignedLong(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + FORMATOR_UNSIGNED_LONG, arg).semicolon();
    }

    private void printFloat(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + FORMATOR_FLOAT, arg).semicolon();
    }

    private void printIsUnsigned(ElementInfo info, String arg) {
        printString(info, "(" + arg + ") ? \"" + SignednessValue.UNSIGNED.name() + "\" : \"" + SignednessValue.SIGNED.name() + "\"");

    }

    private int tempVarCounter;

    private String getUniqueTempVarName(ElementInfo info) {
        tempVarCounter++;
        return "tmp_" + info.getName().replaceAll("\\W", "_") + "_" + tempVarCounter;
    }

    private static String sizeOf(ElementInfo element) {
        String elementName = element.getName();
        /* sizeof(void) is undefined and an error on some compilers */
        return elementName.equals("void") ? "1" : "sizeof(" + elementName + ")";
    }

    private static String sizeOfField(StructFieldInfo field) {
        return "sizeof(((" + field.getParent().getName() + " *) 0)->" + field.getName() + ")";
    }

    private static String offsetOfField(StructFieldInfo field) {
        return "offsetof(" + field.getParent().getName() + ", " + field.getName() + ")";
    }

    private static String isUnsigned(String symbolName) {
        return "ISUNSIGNED(" + symbolName + ")";
    }

    private static String isConstUnsigned(String symbolName) {
        return "IS_CONST_UNSIGNED(" + symbolName + ")";
    }

    private void registerElementForCurrentLine(Object element) {
        assert element != null;

        int currentLineNumber = writer.currentLineNumber();
        while (elementForLineNumber.size() <= currentLineNumber) {
            elementForLineNumber.add(null);
        }
        assert elementForLineNumber.get(currentLineNumber) == null : "element already registered for this line";
        elementForLineNumber.set(currentLineNumber, element);
    }
}
