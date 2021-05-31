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
import static com.oracle.svm.hosted.c.query.QueryResultFormat.DELIMINATOR;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.svm.hosted.c.info.RawPointerToInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.CContext;

import com.oracle.svm.hosted.c.DirectivesExtension;
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

    private static final String formatFloat = "%.15e";
    private static final String formatString = QueryResultFormat.STRING_MARKER + "%s" + QueryResultFormat.STRING_MARKER;

    private final CSourceCodeWriter writer;

    private final List<Object> elementForLineNumber;
    private final boolean isWindows;

    private final String formatSInt64;
    private final String formatUInt64;
    private final String formatUInt64Hex;

    private final String uInt64;
    private final String sInt64;

    public QueryCodeWriter(Path tempDirectory) {
        writer = new CSourceCodeWriter(tempDirectory);
        elementForLineNumber = new ArrayList<>();
        isWindows = Platform.includedIn(Platform.WINDOWS.class);

        String formatL64 = "%" + (isWindows ? "ll" : "l");
        formatSInt64 = formatL64 + "d";
        formatUInt64 = formatL64 + "u";
        formatUInt64Hex = formatL64 + "X";

        uInt64 = int64(isWindows, true);
        sInt64 = int64(isWindows, false);
    }

    private static String int64(boolean isWindows, boolean unsigned) {
        /* Linux uses LP64, Windows uses LLP64 */
        return (unsigned ? "unsigned " : "") + (isWindows ? "long long" : "long");
    }

    public Path write(NativeCodeInfo nativeCodeInfo) {
        nativeCodeInfo.accept(this);

        String srcFileExtension = CSourceCodeWriter.C_SOURCE_FILE_EXTENSION;
        String sourceFileName = nativeCodeInfo.getName().replaceAll("\\W", "_") + srcFileExtension;
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
        CContext.Directives directives = nativeCodeInfo.getDirectives();

        /* Write general macro definitions. */
        List<String> macroDefinitions = directives.getMacroDefinitions();
        if (!macroDefinitions.isEmpty()) {
            macroDefinitions.forEach(writer::appendMacroDefinition);
            writer.appendln();
        }

        /* Standard header file inclusions. */
        writer.includeFiles(Arrays.asList("<stdio.h>", "<stddef.h>", "<memory.h>"));
        writer.writeCStandardHeaders();
        writer.appendln();

        /* Workaround missing bool type in old cl.exe. */
        if (isWindows) {
            writer.appendln("#ifndef bool");
            writer.appendln("#define bool char");
            writer.appendln("#define false ((bool)0)");
            writer.appendln("#define true  ((bool)1)");
            writer.appendln("#endif");
            writer.appendln("");
        }

        /* Inject CContext specific C header file snippet. */
        if (directives instanceof DirectivesExtension) {
            List<String> headerSnippet = ((DirectivesExtension) directives).getHeaderSnippet();
            if (!headerSnippet.isEmpty()) {
                headerSnippet.forEach(writer::appendln);
                writer.appendln();
            }
        }

        /* CContext specific header file inclusions. */
        List<String> headerFiles = directives.getHeaderFiles();
        if (!headerFiles.isEmpty()) {
            writer.includeFiles(headerFiles);
            writer.appendln();
        }

        /* Write query code for nativeCodeInfo. */
        String functionName = nativeCodeInfo.getName().replaceAll("\\W", "_");
        writer.appendln("int " + functionName + "() {");
        writer.indent();
        processChildren(nativeCodeInfo);
        writer.indents().appendln("return 0;");
        writer.outdent();
        writer.appendln("}");

        /* Write main function. */
        writer.appendln();
        writer.appendln("int main(void) {");
        writer.indent();
        writer.indents().appendln("return " + functionName + "();");
        writer.outdent();
        writer.appendln("}");
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
            registerElementForCurrentLine(fieldInfo.getParent().getAnnotatedElement());
            writer.indents().appendln("{");
            writer.indent();
            writer.indents().appendln("int is_unsigned;");
            writer.indents().appendln(uInt64 + " all_bits_set = -1;");
            writer.indents().appendln(fieldInfo.getParent().getName() + " fieldHolder;");
            writer.indents().appendln("memset(&fieldHolder, 0x0, sizeof(fieldHolder));");
            writer.indents().appendln("fieldHolder." + fieldInfo.getName() + " = all_bits_set;");
            writer.indents().appendln("is_unsigned = fieldHolder." + fieldInfo.getName() + " > 0;");
            printIsUnsigned(fieldInfo.getSignednessInfo(), "is_unsigned");
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
        writer.indents().appendln("  " + sInt64 + " pad;");
        writer.indents().appendln("} w;");
        writer.indents().appendln("int is_unsigned;");
        writer.indents().appendln("char *p;");
        writer.indents().appendln("unsigned int byte_offset;");
        writer.indents().appendln("int start_bit, end_bit;");
        writer.indents().appendln(uInt64 + " v;");
        writer.indents().appendln(uInt64 + " all_bits_set = -1;");
        /* Set the structure to 0 bits (including the padding space). */
        writer.indents().appendln("memset(&w, 0x0, sizeof(w));");
        /* Fill the actual bitfield with 1 bits. Maximum size is 64 bits. */
        registerElementForCurrentLine(bitfieldInfo.getAnnotatedElement());
        writer.indents().appendln("w.s." + bitfieldName + " = all_bits_set;");
        /* All bits are set, so signed bitfields are < 0; */
        writer.indents().appendln("is_unsigned = w.s." + bitfieldName + " > 0;");
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
        writer.indents().appendln("  v = *((" + uInt64 + "*) (p + byte_offset));");
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
        String sizeOfExpr = sizeOf(pointerToInfo);
        if (pointerToInfo.getKind() == ElementKind.POINTER && pointerToInfo.getName().startsWith("struct ")) {
            /* Eliminate need for struct forward declarations */
            sizeOfExpr = "sizeof(void *)";
        } else {
            sizeOfExpr = sizeOf(pointerToInfo);
        }
        printUnsignedLong(pointerToInfo.getSizeInfo(), sizeOfExpr);

        if (pointerToInfo.getKind() == ElementKind.INTEGER) {
            registerElementForCurrentLine(pointerToInfo.getAnnotatedElement());
            writer.indents().appendln("{");
            writer.indent();
            writer.indents().appendln("int is_unsigned;");
            writer.indents().appendln(uInt64 + " all_bits_set = -1;");
            writer.indents().appendln(pointerToInfo.getName() + " fieldHolder = all_bits_set;");
            writer.indents().appendln("is_unsigned = fieldHolder > 0;");
            printIsUnsigned(pointerToInfo.getSignednessInfo(), "is_unsigned");
            writer.outdent();
            writer.indents().appendln("}");
        }
    }

    @Override
    protected void visitRawPointerToInfo(RawPointerToInfo pointerToInfo) {
        /* Nothing to do, do not visit children. */
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
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + formatString, arg).semicolon();
    }

    private static String cast(String targetType, String value) {
        return "((" + targetType + ")" + value + ")";
    }

    private void printSignedLong(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + formatSInt64, cast(sInt64, arg)).semicolon();
    }

    private void printUnsignedLong(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + formatUInt64, cast(uInt64, arg)).semicolon();
    }

    private void printLongHex(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + formatUInt64Hex, cast(uInt64, arg)).semicolon();
    }

    private void printFloat(ElementInfo info, String arg) {
        registerElementForCurrentLine(info.getAnnotatedElement());
        writer.indents().printf(info.getUniqueID() + DELIMINATOR + formatFloat, arg).semicolon();
    }

    private void printIsUnsigned(ElementInfo info, String arg) {
        printString(info, "(" + arg + ") ? \"" + SignednessValue.UNSIGNED.name() + "\" : \"" + SignednessValue.SIGNED.name() + "\"");

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

    private static String isConstUnsigned(String symbolName) {
        return "(" + symbolName + ">=0 ? 1 : 0)";
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
