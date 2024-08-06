/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.objectfile.pecoff.cv;

import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.CV_CALL_NEAR_C;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.FUNC_IS_CONSTRUCTOR;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_ARGLIST;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_ARRAY;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_BCLASS;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_CLASS;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_FIELDLIST;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_INDEX;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_MEMBER;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_METHOD;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_METHODLIST;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_MFUNCTION;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_ONEMETHOD;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD1;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD2;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD3;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_POINTER;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_STMEMBER;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_STRING_ID;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_UDT_SRC_LINE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_ABSTRACT;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_COMPGENX;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_FINAL_CLASS;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_FINAL_METHOD;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_IVIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PPP_MASK;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PSEUDO;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_PURE_IVIRTUAL;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.MPROP_VSF_MASK;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_NOTYPE;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.T_UINT8;

import java.util.ArrayList;

import jdk.graal.compiler.debug.GraalError;

/*
 * CV Type Record format (little-endian):
 * uint16 length
 * uint16 leaf (a.k.a. record type)
 * (contents)
 */
abstract class CVTypeRecord {

    static final int FIRST_TYPE_INDEX = 0x1000;
    static final int CV_TYPE_RECORD_MAX_SIZE = 0xffff;

    protected final short type;
    private int startPosition;
    private int sequenceNumber; /* CodeView type records are numbered 1000 on up. */

    CVTypeRecord(short type) {
        this.type = type;
        this.startPosition = -1;
        this.sequenceNumber = -1;
    }

    int getSequenceNumber() {
        return sequenceNumber;
    }

    void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    int computeFullSize(int initialPos) {
        assert sequenceNumber >= FIRST_TYPE_INDEX;
        this.startPosition = initialPos;
        int pos = initialPos + Short.BYTES * 2; /* Save room for length and leaf type. */
        pos = computeSize(pos);
        pos = alignPadded4(null, pos);
        return pos;
    }

    int computeFullContents(byte[] buffer, int initialPos) {
        assert sequenceNumber >= FIRST_TYPE_INDEX;
        int pos = initialPos + Short.BYTES; /* Save room for length short. */
        pos = CVUtil.putShort(type, buffer, pos);
        pos = computeContents(buffer, pos);
        /* Length does not include record length (2 bytes)) but does include end padding. */
        pos = alignPadded4(buffer, pos);
        int length = pos - initialPos - Short.BYTES;
        if (length > CV_TYPE_RECORD_MAX_SIZE) {
            throw GraalError.shouldNotReachHere(String.format("Type record too large: %d (maximum %d) bytes: %s", length, CV_TYPE_RECORD_MAX_SIZE, this)); // ExcludeFromJacocoGeneratedReport
        }
        CVUtil.putShort((short) length, buffer, initialPos);
        return pos;
    }

    public int computeSize(int initialPos) {
        return computeContents(null, initialPos);
    }

    protected abstract int computeContents(byte[] buffer, int initialPos);

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        return this.type == ((CVTypeRecord) obj).type;
    }

    @Override
    public abstract int hashCode();

    @Override
    public String toString() {
        return String.format("CVTypeRecord seq=0x%04x type=0x%04x pos=0x%04x ", sequenceNumber, type, startPosition);
    }

    private static int alignPadded4(byte[] buffer, int originalpos) {
        int pos = originalpos;
        int align = pos & 3;
        if (align == 1) {
            byte[] p3 = {LF_PAD3, LF_PAD2, LF_PAD1};
            pos = CVUtil.putBytes(p3, buffer, pos);
        } else if (align == 2) {
            pos = CVUtil.putByte(LF_PAD2, buffer, pos);
            pos = CVUtil.putByte(LF_PAD1, buffer, pos);
        } else if (align == 3) {
            pos = CVUtil.putByte(LF_PAD1, buffer, pos);
        }
        return pos;
    }

    static final class CVTypePrimitive extends CVTypeRecord {

        private final int length;

        CVTypePrimitive(short cvtype, int length) {
            super(cvtype);
            assert cvtype < FIRST_TYPE_INDEX;
            this.length = length;
            setSequenceNumber(cvtype);
        }

        @Override
        public int computeSize(int initialPos) {
            throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public int hashCode() {
            throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public String toString() {
            return String.format("PRIMITIVE 0x%04x (len=%d)", getSequenceNumber(), length);
        }
    }

    static final class CVTypePointerRecord extends CVTypeRecord {

        static final int KIND_64 = 0x0000c;
        static final int SIZE_8 = 8 << 13;

        /* Standard 64-bit absolute pointer type. */
        static final int NORMAL_64 = KIND_64 | SIZE_8;

        private final int pointsTo;

        /*-
         * int kind      =  attributes & 0x00001f;
         * int mode      = (attributes & 0x0000e0) >> 5;
         * int modifiers = (attributes & 0x001f00) >> 8;
         * int size      = (attributes & 0x07e000) >> 13;
         * int flags     = (attributes & 0x380000) >> 19;
         */
        private final int attrs;

        CVTypePointerRecord(int pointTo, int attrs) {
            super(LF_POINTER);
            this.pointsTo = pointTo;
            this.attrs = attrs;
        }

        @SuppressWarnings("unused")
        int getPointsTo() {
            return pointsTo;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(pointsTo, buffer, initialPos);
            return CVUtil.putInt(attrs, buffer, pos);
        }

        static String[] ptrType = {"near16", "far16", "huge", "base-seg", "base-val", "base-segval", "base-addr", "base-segaddr", "base-type", "base-self", "near32", "far32", "64"};
        static String[] modeStrs = {"normal", "lvalref", "datamem", "memfunc", "rvalref"};

        @Override
        public String toString() {
            int kind = attrs & 0x00001f;
            int mode = (attrs & 0x0000e0) >> 5;
            int flags1 = (attrs & 0x001f00) >> 8;
            int size = (attrs & 0x07e000) >> 13;
            int flags2 = (attrs & 0x380000) >> 19;
            StringBuilder sb = new StringBuilder();
            sb.append((flags1 & 1) != 0 ? "flat32" : "");
            sb.append((flags1 & 2) != 0 ? " volatile" : "");
            sb.append((flags1 & 4) != 0 ? " const" : "");
            sb.append((flags1 & 8) != 0 ? " unaligned" : "");
            sb.append((flags1 & 16) != 0 ? " restricted" : "");
            return String.format("LF_POINTER 0x%04x attrs=0x%x(kind=%d(%s) mode=%d(%s) flags1=0x%x(%s) size=%d flags2=0x%x) pointTo=0x%04x", getSequenceNumber(), attrs, kind, ptrType[kind], mode,
                            modeStrs[mode], flags1, sb, size, flags2, pointsTo);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + pointsTo;
            h = 31 * h + attrs;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypePointerRecord other = (CVTypePointerRecord) obj;
            return this.pointsTo == other.pointsTo && this.attrs == other.attrs;
        }
    }

    static class CVUdtTypeLineRecord extends CVTypeRecord {

        final int typeIndex;
        int fileIndex;
        int line;

        CVUdtTypeLineRecord(int typeIndex, int fileIndex, int line) {
            this(LF_UDT_SRC_LINE, typeIndex, fileIndex, line);
        }

        CVUdtTypeLineRecord(short t, int typeIndex, int fileIndex, int line) {
            super(t);
            this.typeIndex = typeIndex;
            this.fileIndex = fileIndex;
            this.line = line;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIndex, buffer, initialPos);
            pos = CVUtil.putInt(fileIndex, buffer, pos);
            return CVUtil.putInt(line, buffer, pos);
        }

        @Override
        public String toString() {
            return String.format("LF_UDT_SRC_LINE 0x%04x typeIdx=0x%x fileIdx=0x%x line=%d", getSequenceNumber(), typeIndex, fileIndex, line);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + typeIndex;
            h = 31 * h + fileIndex;
            h = 31 * h + line;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVUdtTypeLineRecord other = (CVUdtTypeLineRecord) obj;
            /*
             * NB: if the record has the same type but different file or line, it's probably an
             * error.
             */
            return this.typeIndex == other.typeIndex && this.fileIndex == other.fileIndex && this.line == other.line;
        }
    }

    static final class CVTypeStringIdRecord extends CVTypeRecord {

        String string;
        int substringListIndex;

        CVTypeStringIdRecord(int substringListIndex, String string) {
            super(LF_STRING_ID);
            this.substringListIndex = substringListIndex;
            this.string = string;
        }

        CVTypeStringIdRecord(String string) {
            this(T_NOTYPE, string);
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(substringListIndex, buffer, initialPos);
            return CVUtil.putUTF8StringBytes(string, buffer, pos);
        }

        @Override
        public String toString() {
            return String.format("LF_STRING_ID 0x%04x substringListIdx=0x%x %s", getSequenceNumber(), substringListIndex, string);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + substringListIndex;
            h = 31 * h + string.hashCode();
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeStringIdRecord other = (CVTypeStringIdRecord) obj;
            return this.string.equals(other.string) && this.substringListIndex == other.substringListIndex;
        }
    }

    static final class CVTypeArglistRecord extends CVTypeRecord {

        private final ArrayList<Integer> args = new ArrayList<>();

        CVTypeArglistRecord() {
            super(LF_ARGLIST);
        }

        void add(int argType) {
            args.add(argType);
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES + Integer.BYTES * args.size();
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(args.size(), buffer, initialPos);
            for (Integer at : args) {
                pos = CVUtil.putInt(at, buffer, pos);
            }
            return pos;
        }

        int getSize() {
            return args.size();
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder(String.format("LF_ARGLIST 0x%04x [", getSequenceNumber()));
            for (Integer at : args) {
                s.append(String.format(" 0x%04x", at));
            }
            s.append("])");
            return s.toString();
        }

        @Override
        public int hashCode() {
            return type * 31 + args.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeArglistRecord other = (CVTypeArglistRecord) obj;
            return this.args.equals(other.args);
        }
    }

    static final class CVTypeMFunctionRecord extends CVTypeRecord {

        private int returnType = -1;
        private int classType = -1;
        private int thisType = -1;
        private byte callType = CV_CALL_NEAR_C;
        private byte funcAttr = 0;
        private int thisAdjust = 0;

        private CVTypeArglistRecord argList = null;

        CVTypeMFunctionRecord() {
            super(LF_MFUNCTION);
        }

        void setReturnType(int returnType) {
            this.returnType = returnType;
        }

        void setClassType(int classType) {
            this.classType = classType;
        }

        void setThisType(int thisType) {
            this.thisType = thisType;
        }

        @SuppressWarnings("SameParameterValue")
        void setCallType(byte callType) {
            this.callType = callType;
        }

        void setFuncAttr(byte funcAttr) {
            this.funcAttr = funcAttr;
        }

        @SuppressWarnings("unused")
        void setThisAdjust(int thisAdjust) {
            this.thisAdjust = thisAdjust;
        }

        void setArgList(CVTypeArglistRecord argList) {
            this.argList = argList;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(returnType, buffer, initialPos);
            pos = CVUtil.putInt(classType, buffer, pos);
            pos = CVUtil.putInt(thisType, buffer, pos);
            pos = CVUtil.putByte(callType, buffer, pos);
            pos = CVUtil.putByte(funcAttr, buffer, pos);
            pos = CVUtil.putShort((short) argList.getSize(), buffer, pos);
            pos = CVUtil.putInt(argList.getSequenceNumber(), buffer, pos);
            pos = CVUtil.putInt(thisAdjust, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            String attrString = (funcAttr & FUNC_IS_CONSTRUCTOR) == FUNC_IS_CONSTRUCTOR ? "(ctor)" : "";
            return String.format("LF_MFUNCTION 0x%04x ret=0x%04x this=0x%04x *this=0x%04x+%d calltype=0x%x attr=0x%x%s, argcount=0x%04x ", getSequenceNumber(), returnType, classType, thisType,
                            thisAdjust, callType, funcAttr, attrString, argList.getSequenceNumber());
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + returnType;
            h = 31 * h + classType;
            h = 31 * h + thisType;
            h = 31 * h + callType;
            h = 31 * h + funcAttr;
            h = 31 * h + thisAdjust;
            h = 31 * h + argList.getSequenceNumber();
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeMFunctionRecord other = (CVTypeMFunctionRecord) obj;
            return this.returnType == other.returnType && this.classType == other.classType && this.thisType == other.thisType && this.callType == other.callType && this.funcAttr == other.funcAttr &&
                            this.thisAdjust == other.thisAdjust && this.argList.getSequenceNumber() == other.argList.getSequenceNumber();
        }
    }

    static final class CVTypeMethodListRecord extends CVTypeRecord {

        static class MDef extends CVOneMethodRecord {

            MDef(short attrs, int funcIdx, int vtbleOffset, String name) {
                super(attrs, funcIdx, vtbleOffset, name);
            }

            @Override
            public int computeContents(byte[] buffer, int initialPos) {
                int pos = initialPos;
                pos = CVUtil.putShort(attrs, buffer, pos);
                /* Two bytes of padding. */
                pos = CVUtil.putShort((short) 0, buffer, pos);
                pos = CVUtil.putInt(funcIdx, buffer, pos);
                if (hasVtableOffset()) {
                    assert vtbleOffset >= 0;
                    pos = CVUtil.putInt(vtbleOffset, buffer, pos);
                }
                return pos;
            }
        }

        static final int INITIAL_CAPACITY = 10;

        private final ArrayList<MDef> methods = new ArrayList<>(INITIAL_CAPACITY);

        CVTypeMethodListRecord() {
            super(LF_METHODLIST);
        }

        public void add(short attrs, int idx, int offset, String name) {
            methods.add(new MDef(attrs, idx, offset, name));
        }

        public int count() {
            return methods.size();
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = initialPos;
            for (MDef f : methods) {
                pos = f.computeContents(buffer, pos);
            }
            return pos;
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + methods.hashCode();
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeMethodListRecord other = (CVTypeMethodListRecord) obj;
            return this.methods.equals(other.methods);
        }

        @Override
        public String toString() {
            return String.format("LF_METHODLIST idx=0x%04x count=%d", getSequenceNumber(), methods.size());
        }
    }

    static String attrString(short attrs) {
        final StringBuilder sb = new StringBuilder();

        /* Low byte. */
        if ((attrs & MPROP_PPP_MASK) != 0) {
            String[] aStr = {"", "private", "protected", "public"};
            sb.append(aStr[attrs & MPROP_PPP_MASK]);
        }
        if ((attrs & MPROP_VSF_MASK) != 0) {
            int p = (attrs & MPROP_VSF_MASK) >> 2;
            String[] pStr = {"", " virtual", " static", " friend", " intro", " pure", " intro-pure", " (*7*)"};
            sb.append(pStr[p]);
        }
        if ((attrs & MPROP_PSEUDO) != 0) {
            sb.append(" pseudo");
        }
        if ((attrs & MPROP_FINAL_CLASS) != 0) {
            sb.append(" final-class");
        }
        if ((attrs & MPROP_ABSTRACT) != 0) {
            sb.append(" abstract");
        }
        if ((attrs & MPROP_COMPGENX) != 0) {
            sb.append(" compgenx");
        }
        if ((attrs & MPROP_FINAL_METHOD) != 0) {
            sb.append(" final-method");
        }
        return sb.toString();
    }

    abstract static class FieldRecord {

        protected final short type;
        protected final short attrs; /* property attribute field (prop_t) */
        protected final String name;

        protected FieldRecord(short leafType, short attrs, String name) {
            assert name != null;
            this.type = leafType;
            this.attrs = attrs;
            this.name = name;
        }

        protected FieldRecord(short leafType) {
            this(leafType, (short) 0, "");
        }

        public int computeSize() {
            return computeContents(null, 0);
        }

        public abstract int computeContents(byte[] buffer, int initialPos);

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + attrs;
            h = 31 * h + name.hashCode();
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            FieldRecord other = (FieldRecord) obj;
            return this.type == other.type && this.attrs == other.attrs && this.name.equals(other.name);
        }
    }

    static final class CVOverloadedMethodRecord extends FieldRecord {

        private final int methodListIndex; /* index of method list record */
        private final short count;

        CVOverloadedMethodRecord(short count, int methodListIndex, String methodName) {
            super(LF_METHOD, (short) 0, methodName);
            this.methodListIndex = methodListIndex;
            this.count = count;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort(count, buffer, pos);
            pos = CVUtil.putInt(methodListIndex, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_METHOD(0x%04x) count=0x%x listIdx=0x%04x %s", type, count, methodListIndex, name);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + methodListIndex;
            h = 31 + h + count;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVOverloadedMethodRecord other = (CVOverloadedMethodRecord) obj;
            return this.methodListIndex == other.methodListIndex && this.count == other.count;
        }
    }

    static final class CVIndexRecord extends FieldRecord {

        private final int index; /* index of continuation record */

        CVIndexRecord(int index) {
            super(LF_INDEX);
            this.index = index;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort((short) 0, buffer, pos); /* padding. */
            pos = CVUtil.putInt(index, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_INDEX(0x%04x) index=0x%04x", type, index);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + index;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVIndexRecord other = (CVIndexRecord) obj;
            return this.index == other.index;
        }
    }

    static final class CVMemberRecord extends FieldRecord {

        private final int underlyingTypeIndex; /* type index of member type */
        private int offset;

        CVMemberRecord(short attrs, int underlyingTypeIndex, int offset, String name) {
            super(LF_MEMBER, attrs, name);
            this.underlyingTypeIndex = underlyingTypeIndex;
            this.offset = offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort(attrs, buffer, pos);
            pos = CVUtil.putInt(underlyingTypeIndex, buffer, pos);
            pos = CVUtil.putLfNumeric(offset, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_MEMBER(0x%04x) attr=0x%x(%s) t=0x%x off=%d 0x%x %s", type, attrs, attrString(attrs), underlyingTypeIndex, offset, offset & 0xffff, name);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + underlyingTypeIndex;
            h = 31 * h + offset;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVMemberRecord other = (CVMemberRecord) obj;
            return this.offset == other.offset && this.underlyingTypeIndex == other.underlyingTypeIndex;
        }
    }

    static final class CVStaticMemberRecord extends FieldRecord {

        /* Type index of member type. */
        private final int underlyingTypeIndex;

        CVStaticMemberRecord(short attrs, int underlyingTypeIndex, String name) {
            super(LF_STMEMBER, attrs, name);
            this.underlyingTypeIndex = underlyingTypeIndex;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort(attrs, buffer, pos);
            pos = CVUtil.putInt(underlyingTypeIndex, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_STMEMBER(0x%04x) attr=0x%x(%s) t=0x%x %s", type, attrs, attrString(attrs), underlyingTypeIndex, name);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + underlyingTypeIndex;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVStaticMemberRecord other = (CVStaticMemberRecord) obj;
            return this.underlyingTypeIndex == other.underlyingTypeIndex;
        }
    }

    static class CVOneMethodRecord extends FieldRecord {

        protected final int funcIdx; /* type index of member type */
        protected final int vtbleOffset;

        CVOneMethodRecord(short attrs, int funcIdx, int vtbleOffset, String name) {
            super(LF_ONEMETHOD, attrs, name);
            this.funcIdx = funcIdx;
            this.vtbleOffset = vtbleOffset;
        }

        boolean hasVtableOffset() {
            return (attrs & MPROP_VSF_MASK) == MPROP_IVIRTUAL || (attrs & MPROP_VSF_MASK) == MPROP_PURE_IVIRTUAL;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort(attrs, buffer, pos);
            pos = CVUtil.putInt(funcIdx, buffer, pos);
            if (hasVtableOffset()) {
                assert vtbleOffset >= 0;
                pos = CVUtil.putInt(vtbleOffset, buffer, pos);
            }
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_ONEMETHOD(0x%04x) attr=0x%x(%s) funcIdx=0x%x vtbloffet=0x%x %s", type, attrs, attrString(attrs), funcIdx, vtbleOffset, name);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + funcIdx;
            h = 31 * h + vtbleOffset;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVOneMethodRecord other = (CVOneMethodRecord) obj;
            return this.vtbleOffset == other.vtbleOffset && this.funcIdx == other.funcIdx;
        }
    }

    static class CVBaseMemberRecord extends FieldRecord {

        private final int basetypeIndex; /* type index of member type */
        private final int offset; /* in java, usually 0 as there is no multiple inheritance. */

        CVBaseMemberRecord(short attrs, int basetypeIndex, int offset) {
            super(LF_BCLASS, attrs, "");
            this.basetypeIndex = basetypeIndex;
            this.offset = offset;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(type, buffer, initialPos);
            pos = CVUtil.putShort(attrs, buffer, pos);
            pos = CVUtil.putInt(basetypeIndex, buffer, pos);
            pos = CVUtil.putLfNumeric(offset, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_BCLASS(0x%04x) attr=0x%04x(%s ?) baseIdx=0x%04x offset=0x%x", LF_BCLASS, attrs, attrString(attrs), basetypeIndex, offset);
        }

        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = 31 * h + basetypeIndex;
            h = 31 * h + offset;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVBaseMemberRecord other = (CVBaseMemberRecord) obj;
            return this.basetypeIndex == other.basetypeIndex && this.offset == other.offset;
        }
    }

    static class CVClassRecord extends CVTypeRecord {

        static final int ATTR_FORWARD_REF = 0x0080;
        static final int ATTR_HAS_UNIQUENAME = 0x0200;

        /* Count of number of elements in class field list. */
        private final short count;

        /* Property attribute field (prop_t). */
        private final short propertyAttributes;

        /* Type index of LF_FIELDLIST descriptor list. */
        private final int fieldIndex;

        /* Type index of derived from list if not zero */
        /*
         * For Java, there is only one class, so LF_BCLASS is in the member list and
         * derivedFromIndex is 0.
         */
        private final int derivedFromIndex;

        /* Type index of vshape table for this class. */
        private final int vshapeIndex;

        /* Size (in bytes) of an instance. */
        private final long size;

        /* Class name. */
        private final String className;

        /* Linker class name. */
        private final String uniqueName;

        CVClassRecord(short recType, short count, short attrs, int fieldIndex, int derivedFromIndex, int vshapeIndex, long size, String className, String uniqueName) {
            super(recType);
            this.count = count;
            this.propertyAttributes = (short) (attrs | (short) (uniqueName != null ? ATTR_HAS_UNIQUENAME : 0));
            this.fieldIndex = fieldIndex;
            this.derivedFromIndex = derivedFromIndex;
            this.vshapeIndex = vshapeIndex;
            this.size = size;
            this.className = className;
            this.uniqueName = uniqueName;
        }

        @SuppressWarnings("unused")
        CVClassRecord(short count, short attrs, int fieldIndex, int derivedFromIndex, int vshapeIndex, long size, String className, String uniqueName) {
            this(LF_CLASS, count, attrs, fieldIndex, derivedFromIndex, vshapeIndex, size, className, uniqueName);
        }

        CVClassRecord(short attrs, String className, String uniqueName) {
            this(LF_CLASS, (short) 0, attrs, 0, 0, 0, 0, className, uniqueName);
        }

        String getClassName() {
            return className;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(count, buffer, initialPos);
            pos = CVUtil.putShort(propertyAttributes, buffer, pos);
            pos = CVUtil.putInt(fieldIndex, buffer, pos);
            pos = CVUtil.putInt(derivedFromIndex, buffer, pos);
            pos = CVUtil.putInt(vshapeIndex, buffer, pos);
            pos = CVUtil.putLfNumeric(size, buffer, pos);
            String fixedName = CVNames.typeNameToCodeViewName(className);
            pos = CVUtil.putUTF8StringBytes(fixedName, buffer, pos);
            if (hasUniqueName()) {
                assert uniqueName != null;
                pos = CVUtil.putUTF8StringBytes(uniqueName, buffer, pos);
            }
            return pos;
        }

        boolean isForwardRef() {
            return (propertyAttributes & ATTR_FORWARD_REF) != 0;
        }

        @SuppressWarnings("unused")
        public boolean hasUniqueName() {
            return (propertyAttributes & ATTR_HAS_UNIQUENAME) != 0;
        }

        protected String toString(String lfTypeStr) {
            return String.format("%s 0x%04x count=%d attr=0x%x(%s) fld=0x%x super=0x%x vshape=0x%x size=%d %s%s", lfTypeStr, getSequenceNumber(), count, propertyAttributes,
                            propertyString(propertyAttributes), fieldIndex, derivedFromIndex,
                            vshapeIndex, size, className, uniqueName != null ? " (" + uniqueName + ")" : "");
        }

        @Override
        public String toString() {
            return toString("LF_CLASS");
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + count;
            h = 31 * h + propertyAttributes;
            h = 31 * h + fieldIndex;
            h = 31 * h + derivedFromIndex;
            h = 31 * h + (int) size;
            h = 31 * h + className.hashCode();
            if (uniqueName != null) {
                h = 31 * h + uniqueName.hashCode();
            }
            h = 31 * h + vshapeIndex;
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVClassRecord other = (CVClassRecord) obj;
            return this.count == other.count && this.propertyAttributes == other.propertyAttributes && this.fieldIndex == other.fieldIndex && this.derivedFromIndex == other.derivedFromIndex &&
                            this.size == other.size && this.className.equals(other.className) && (this.uniqueName != null ? this.uniqueName.equals(other.uniqueName) : other.uniqueName == null) &&
                            this.vshapeIndex == other.vshapeIndex;
        }
    }

    static final class CVFieldListRecord extends CVTypeRecord {

        static final int INITIAL_CAPACITY = 10;

        /* Size includes type field but not record length field. */
        private int estimatedSize = CVUtil.align4(Short.BYTES);

        private final ArrayList<FieldRecord> members = new ArrayList<>(INITIAL_CAPACITY);

        CVFieldListRecord() {
            super(LF_FIELDLIST);
        }

        void add(FieldRecord m) {
            /* Keep a running total. */
            estimatedSize += CVUtil.align4(m.computeSize());
            members.add(m);
        }

        int getEstimatedSize() {
            return estimatedSize;
        }

        @Override
        protected int computeContents(byte[] buffer, int initialPos) {
            int pos = initialPos;
            for (FieldRecord field : members) {
                pos = field.computeContents(buffer, pos);
                pos = CVTypeRecord.alignPadded4(buffer, pos);
            }
            return pos;
        }

        @Override
        public int hashCode() {
            int hash = type;
            hash = 31 * hash + members.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVFieldListRecord other = (CVFieldListRecord) obj;
            return this.members.equals(other.members);
        }

        @Override
        public String toString() {
            return String.format("LF_FIELDLIST idx=0x%x count=%d", getSequenceNumber(), members.size());
        }
    }

    static final class CVTypeArrayRecord extends CVTypeRecord {

        private final int elementTypeIndex;
        private final int indexType;
        private final int length;
        private final String name;

        CVTypeArrayRecord(int elementTypeIndex, int indexType, int length, String name) {
            super(LF_ARRAY);
            this.elementTypeIndex = elementTypeIndex;
            this.indexType = indexType;
            this.length = length;
            this.name = name;
        }

        CVTypeArrayRecord(int elementTypeIndex, int indexType, int length) {
            this(elementTypeIndex, indexType, length, "");
        }

        @SuppressWarnings("unused")
        CVTypeArrayRecord(int elementTypeIndex, int length) {
            this(elementTypeIndex, T_UINT8, length);
        }

        @SuppressWarnings("unused")
        CVTypeArrayRecord(CVTypeRecord elementType, int length) {
            this(elementType.getSequenceNumber(), T_UINT8, length);
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(elementTypeIndex, buffer, initialPos);
            pos = CVUtil.putInt(indexType, buffer, pos);
            pos = CVUtil.putLfNumeric(length, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(name, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_ARRAY 0x%04x type=0x%04x len=%d indexType=0x%04x%s", getSequenceNumber(), elementTypeIndex, length, indexType, name.isEmpty() ? "" : "name=" + name);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + elementTypeIndex;
            h = 31 * h + indexType;
            h = 31 * h + length;
            h = 31 * h + name.hashCode();
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeArrayRecord other = (CVTypeArrayRecord) obj;
            return this.elementTypeIndex == other.elementTypeIndex && this.indexType == other.indexType && this.length == other.length && this.name.equals(other.name);
        }
    }

    static String propertyString(int properties) {
        final StringBuilder sb = new StringBuilder();

        /* Low byte. */
        if ((properties & 0x0001) != 0) {
            sb.append(" packed");
        }
        if ((properties & 0x0002) != 0) {
            sb.append(" ctor");
        }
        if ((properties & 0x0004) != 0) {
            sb.append(" ovlops");
        }
        if ((properties & 0x0008) != 0) {
            sb.append(" isnested");
        }
        if ((properties & 0x0010) != 0) {
            sb.append(" cnested");
        }
        if ((properties & 0x0020) != 0) {
            sb.append(" opassign");
        }
        if ((properties & 0x0040) != 0) {
            sb.append(" opcast");
        }
        if ((properties & 0x0080) != 0) {
            sb.append(" forwardref");
        }

        /* High byte. */
        if ((properties & 0x0100) != 0) {
            sb.append(" scope");
        }
        if ((properties & 0x0200) != 0) {
            sb.append(" hasuniquename");
        }
        if ((properties & 0x0400) != 0) {
            sb.append(" sealed");
        }
        if ((properties & 0x1800) != 0) {
            sb.append(" hfa...");
        }
        if ((properties & 0x2000) != 0) {
            sb.append(" intrinsic");
        }
        if ((properties & 0xc000) != 0) {
            sb.append(" macom...");
        }
        return sb.toString();
    }
}
