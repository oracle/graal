/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.query;

import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;

import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumConstantInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.PropertyInfo;
import com.oracle.svm.hosted.c.info.RawPointerToInfo;
import com.oracle.svm.hosted.c.info.RawStructureInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.SizableInfo.SignednessValue;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Parses query result described in {@link QueryResultFormat}.
 */
public final class QueryResultParser extends NativeInfoTreeVisitor {

    private final Map<String, String> idToResult;

    private QueryResultParser(NativeLibraries nativeLibs) {
        super(nativeLibs);
        this.idToResult = new HashMap<>();
    }

    public static List<String> parse(NativeLibraries nativeLibs, NativeCodeInfo nativeCodeInfo, InputStream source) {
        QueryResultParser parser = new QueryResultParser(nativeLibs);
        List<String> lines = FileUtils.readAllLines(source);
        for (String line : lines) {
            String[] keyValuePair = line.split(QueryResultFormat.DELIMINATOR);
            assert keyValuePair.length == 2;
            parser.idToResult.put(keyValuePair[0], keyValuePair[1]);
        }

        nativeCodeInfo.accept(parser);
        return lines;
    }

    @Override
    protected void visitConstantInfo(ConstantInfo constantInfo) {
        switch (constantInfo.getKind()) {
            case INTEGER -> {
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseSignedness(constantInfo.getSignednessInfo());
                parseIntegerConstantValue(constantInfo.getValueInfo());
                tryChangeSizeToDeclaredReturnType(constantInfo);
            }
            case POINTER -> {
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseIntegerConstantValue(constantInfo.getValueInfo());
            }
            case FLOAT -> {
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseFloatValue(constantInfo.getValueInfo());
            }
            case STRING -> parseStringValue(constantInfo.getValueInfo());
            case BYTEARRAY -> parseByteArrayValue(constantInfo.getValueInfo());
            default -> throw shouldNotReachHereUnexpectedInput(constantInfo.getKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * When a {@link CConstant} is accessed with a non-matching Java type (e.g., a C int is accessed
     * as a Java short or Java long), then we try to implicitly cast the constant to the Java type.
     * This implicit cast avoids the need for {@link AllowNarrowingCast}/{@link AllowWideningCast}.
     */
    private void tryChangeSizeToDeclaredReturnType(ConstantInfo constantInfo) {
        ResolvedJavaType javaReturnType = AccessorInfo.getReturnType(constantInfo.getAnnotatedElement());
        JavaKind javaReturnKind = nativeLibs.getWordTypes().asKind(javaReturnType);
        int bytesInJava = javaReturnKind.getByteCount();

        int bytesInC = constantInfo.getSizeInBytes();
        long cValue = (long) constantInfo.getValue();
        if (javaReturnKind == JavaKind.Boolean) {
            /* Casts to boolean are always allowed because we convert the value to 0 or 1. */
            long newValue = cValue != 0L ? 1L : 0L;
            constantInfo.getValueInfo().setProperty(newValue);
            constantInfo.getSizeInfo().setProperty(bytesInJava);
        } else if (bytesInJava != bytesInC) {
            /*
             * Only allow casts if the constant can be represented accurately. Note that a simple
             * range check is not enough because we store the value of the C constant as a Java
             * long, which will overflow if we have a large unsigned 64-bit value.
             */
            boolean withinRange = cValue >= javaReturnKind.getMinValue() && cValue <= javaReturnKind.getMaxValue();
            boolean negativeCValueAsUnsigned64BitValue = !constantInfo.isUnsigned() && cValue < 0 && !nativeLibs.isSigned(javaReturnType) && javaReturnKind.getBitCount() == Long.SIZE;
            boolean largeUnsignedCValue = constantInfo.isUnsigned() && cValue < 0;
            if (withinRange && !negativeCValueAsUnsigned64BitValue && !largeUnsignedCValue) {
                constantInfo.getSizeInfo().setProperty(bytesInJava);
            }
        }
    }

    @Override
    public void visitStructInfo(StructInfo structInfo) {
        if (!structInfo.isIncomplete()) {
            parseIntegerProperty(structInfo.getSizeInfo());
        }
        processChildren(structInfo);
    }

    @Override
    protected void visitRawStructureInfo(RawStructureInfo info) {
        /* Nothing to do, do not visit children. */
    }

    @Override
    public void visitStructFieldInfo(StructFieldInfo fieldInfo) {
        parseIntegerProperty(fieldInfo.getSizeInfo());
        parseIntegerProperty(fieldInfo.getOffsetInfo());

        if (fieldInfo.getKind() == ElementKind.INTEGER) {
            parseSignedness(fieldInfo.getSignednessInfo());
        }
    }

    @Override
    public void visitStructBitfieldInfo(StructBitfieldInfo bitfieldInfo) {
        parseIntegerProperty(bitfieldInfo.getByteOffsetInfo());
        parseIntegerProperty(bitfieldInfo.getStartBitInfo());
        parseIntegerProperty(bitfieldInfo.getEndBitInfo());
        parseSignedness(bitfieldInfo.getSignednessInfo());
    }

    @Override
    public void visitPointerToInfo(PointerToInfo pointerToInfo) {
        parseIntegerProperty(pointerToInfo.getSizeInfo());

        if (pointerToInfo.getKind() == ElementKind.INTEGER) {
            parseSignedness(pointerToInfo.getSignednessInfo());
        }
    }

    @Override
    public void visitRawPointerToInfo(RawPointerToInfo pointerToInfo) {
        /* Nothing to do, do not visit children. */
    }

    @Override
    protected void visitEnumInfo(EnumInfo info) {
        assert info.getKind() == ElementKind.INTEGER;
        parseIntegerProperty(info.getSizeInfo());
        parseSignedness(info.getSignednessInfo());

        super.visitEnumInfo(info);
    }

    @Override
    protected void visitEnumConstantInfo(EnumConstantInfo constantInfo) {
        assert constantInfo.getKind() == ElementKind.INTEGER;
        parseIntegerProperty(constantInfo.getSizeInfo());
        parseSignedness(constantInfo.getSignednessInfo());
        parseIntegerConstantValue(constantInfo.getValueInfo());
    }

    private void parseSignedness(PropertyInfo<SignednessValue> info) {
        info.setProperty(SignednessValue.valueOf(stringLiteral(info)));
    }

    private void parseIntegerConstantValue(PropertyInfo<Object> info) {
        String hex = idToResult.get(info.getUniqueID());
        long value = parseHexToLong(hex);

        SizableInfo parent = (SizableInfo) info.getParent();
        int bitsInC = parent.getSizeInBytes() * Byte.SIZE;
        if (bitsInC < Long.SIZE) {
            value = parent.isUnsigned() ? CodeUtil.zeroExtend(value, bitsInC) : CodeUtil.signExtend(value, bitsInC);
        }

        info.setProperty(value);
    }

    private static long parseHexToLong(String hex) {
        assert hex.length() <= 16;

        if (hex.length() > 8) {
            String msb = hex.substring(0, hex.length() - 8);
            String lsb = hex.substring(hex.length() - 8);
            long msbValue = Long.parseLong(msb, 16);
            long lsbValue = Long.parseLong(lsb, 16);
            return msbValue << 32 | lsbValue;
        }
        return Long.parseLong(hex, 16);
    }

    private void parseFloatValue(PropertyInfo<Object> info) {
        String str = idToResult.get(info.getUniqueID());
        double value = Double.parseDouble(str);
        info.setProperty(value);
    }

    private void parseStringValue(PropertyInfo<Object> info) {
        info.setProperty(stringLiteral(info));
    }

    private String stringLiteral(ElementInfo info) {
        String str = idToResult.get(info.getUniqueID());
        if (str.startsWith(QueryResultFormat.STRING_MARKER) && str.endsWith(QueryResultFormat.STRING_MARKER)) {
            return str.substring(QueryResultFormat.STRING_MARKER.length(), str.length() - QueryResultFormat.STRING_MARKER.length());
        } else {
            addError("String constant not deliminated correctly", info);
            return "";
        }
    }

    private void parseByteArrayValue(PropertyInfo<Object> info) {
        info.setProperty(byteArrayLiteral(info));
    }

    private byte[] byteArrayLiteral(ElementInfo info) {
        String str = stringLiteral(info);
        if (!str.isEmpty()) {
            return str.getBytes(StandardCharsets.UTF_8);
        } else {
            return new byte[0];
        }
    }

    private void parseIntegerProperty(PropertyInfo<Integer> info) {
        int value = Integer.parseInt(idToResult.get(info.getUniqueID()));
        info.setProperty(value);
    }
}
