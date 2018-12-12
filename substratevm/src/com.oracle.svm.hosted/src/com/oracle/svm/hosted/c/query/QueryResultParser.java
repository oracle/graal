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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.hosted.c.query.QueryParserUtil.parseHexToLong;
import static com.oracle.svm.hosted.c.query.QueryParserUtil.parseSigned;
import static com.oracle.svm.hosted.c.query.QueryParserUtil.unsignedExtendToSize;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumConstantInfo;
import com.oracle.svm.hosted.c.info.NativeCodeInfo;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.PropertyInfo;
import com.oracle.svm.hosted.c.info.RawStructureInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.c.info.SizableInfo.ElementKind;
import com.oracle.svm.hosted.c.info.SizableInfo.SignednessValue;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.vm.ci.meta.JavaKind;

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
            case INTEGER:
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseSignedness(constantInfo.getSignednessInfo());
                parseIntegerConstantValue(constantInfo.getValueInfo());

                /*
                 * From the point of view of the C compiler, plain #define constants have the type
                 * int and therefore size 4. But sometimes we want to access such values as short or
                 * byte to avoid casts. Check the actual value of the constant, and if it fits the
                 * declared type of the constant, then change the actual size to the declared size.
                 */
                JavaKind returnKind = AccessorInfo.getReturnType(constantInfo.getAnnotatedElement()).getJavaKind();
                if (returnKind == JavaKind.Object) {
                    returnKind = nativeLibs.getTarget().wordJavaKind;
                }
                int declaredSize = getSizeInBytes(returnKind);
                int actualSize = constantInfo.getSizeInfo().getProperty();
                if (declaredSize != actualSize) {
                    long value = (long) constantInfo.getValueInfo().getProperty();
                    if (value >= returnKind.getMinValue() && value <= returnKind.getMaxValue()) {
                        constantInfo.getSizeInfo().setProperty(declaredSize);
                    }
                }

                break;
            case POINTER:
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseIntegerConstantValue(constantInfo.getValueInfo());
                break;
            case FLOAT:
                parseIntegerProperty(constantInfo.getSizeInfo());
                parseFloatValue(constantInfo.getValueInfo());
                break;
            case STRING:
                parseStringValue(constantInfo.getValueInfo());
                break;
            case BYTEARRAY:
                parseByteArrayValue(constantInfo.getValueInfo());
                break;
            default:
                throw shouldNotReachHere();
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
        boolean isUnsigned = ((SizableInfo) info.getParent()).isUnsigned();
        int size = ((SizableInfo) info.getParent()).getSizeInfo().getProperty();
        String hex = idToResult.get(info.getUniqueID());
        int hexSize = hex.length() / 2;

        if (hexSize < size) {
            hex = unsignedExtendToSize(size, hex);
        }

        if (isUnsigned) {
            parseHexToLong(info, hex);
        } else {
            parseSigned(info, hex);
        }
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
            return str.getBytes(Charset.forName("UTF-8"));
        } else {
            return new byte[0];
        }
    }

    private void parseIntegerProperty(PropertyInfo<Integer> info) {
        int value = Integer.parseInt(idToResult.get(info.getUniqueID()));
        info.setProperty(value);
    }
}
