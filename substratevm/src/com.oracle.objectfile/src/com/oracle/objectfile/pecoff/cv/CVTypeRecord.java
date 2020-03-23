/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

/*
 * CV Type Record format (little-endian):
 * uint16 length
 * uint16 leaf (a.k.a. record type)
 * (contents)
 */
abstract class CVTypeRecord implements CVTypeConstants {

    protected final short type;
    private int startPosition;
    private int sequenceNumber; // 1000 on up

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
        this.startPosition = initialPos;
        int pos = initialPos + Short.BYTES * 2; /* save room for length and leaf type */
        pos = computeSize(pos);
        pos = alignPadded4(null, pos);
        return pos;
    }

    int computeFullContents(byte[] buffer, int initialPos) {
        int lenPos = initialPos;   /* save position of length short */
        int pos = initialPos + Short.BYTES; /* save room for length short */
        pos = CVUtil.putShort(type, buffer, pos);
        pos = computeContents(buffer, pos);
        /* length does not include record length (2 bytes)) but does include end padding */
        pos = alignPadded4(buffer, pos);
        int length = (short) (pos - lenPos - Short.BYTES);
        CVUtil.putShort((short) length, buffer, lenPos);
        return pos;
    }

    protected abstract int computeSize(int initialPos);
    protected abstract int computeContents(byte[] buffer, int initialPos);

    @Override
    public String toString() {
        return String.format("CVTypeRecord(type=0x%04x seq=0x%04x pos=0x%04x)", type, sequenceNumber, startPosition);
    }

    public void dump(PrintStream out) {
        out.format("%s\n", this);
    }

    private static int alignPadded4(byte[] buffer, int originalpos) {
        int pos = originalpos;
        int align = pos & 3;
        if (align == 1) {
            byte[] p3 = {LF_PAD3, LF_PAD2, LF_PAD1 };
            pos = CVUtil.putBytes(p3, buffer, pos);
        } else if (align == 2) {
            pos = CVUtil.putByte(LF_PAD2, buffer, pos);
            pos = CVUtil.putByte(LF_PAD1, buffer, pos);
        } else if (align == 3) {
            pos = CVUtil.putByte(LF_PAD1, buffer, pos);
        }
        return pos;
    }

    static final class CVTypeModifierRecord extends CVTypeRecord {

        int originalLeaf = -1;
        boolean addConst = false;
        boolean addVolatile = false;
        boolean addUnaligned = false;

        CVTypeModifierRecord(int originalLeaf) {
            super(LF_MODIFIER);
            this.originalLeaf = originalLeaf;
        }

        CVTypeModifierRecord(CVTypeRecord originalLeaf) {
            super(LF_MODIFIER);
            this.originalLeaf = originalLeaf.getSequenceNumber();
        }

        CVTypeModifierRecord addConst() {
            this.addConst = true;
            return this;
        }

        CVTypeModifierRecord addVolatile() {
            this.addVolatile = true;
            return this;
        }

        CVTypeModifierRecord addUnaligned() {
            this.addUnaligned = true;
            return this;
        }

        private short computeFlags() {
            return (short) ((addConst ? 0x01 : 0x00) | (addVolatile ? 0x02 : 0x00) | (addUnaligned ? 0x04 : 0));
        }
        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES + Short.BYTES;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(originalLeaf, buffer, initialPos);
            pos = CVUtil.putShort(computeFlags(), buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            String s = String.format("LF_MODIFIER(0x%04x", getSequenceNumber());
            if (addConst) {
                s += " const";
            }
            if (addVolatile) {
                s += " volatile";
            }
            if (addUnaligned) {
                s += "unaligned";
            }
            s += String.format(" 0x%04x)", originalLeaf);
            return s;
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + originalLeaf;
            h = 31 * h + computeFlags();
            return h;
        }
    }

    static final class CVTypePointerRecord extends CVTypeRecord {

        int originalLeaf = -1;
        int kind = 0;
        int mode = 0;
        int modifiers = 0;
        int size = 4;
        int flags = 0;
/*
        int kind      =  attributes & 0x00001f;
        int mode      = (attributes & 0x0000e0) >> 5;
        int modifiers = (attributes & 0x001f00) >> 8;
        int size      = (attributes & 0x07e000) >> 13;
        int flags     = (attributes & 0x380000) >> 19;
        out.printf("LF_POINTER len=%d leaf=0x%04x refType=0x%06x attrib=0x%06x\n", len, leaf, referentType, attributes);
        out.printf("           kind=%d mode=%d modifiers=%d size=%d flags=%d\n", kind, mode, modifiers, size, flags);
*/
        CVTypePointerRecord(int originalLeaf) {
            super(LF_POINTER);
            this.originalLeaf = originalLeaf;
        }

        CVTypePointerRecord(CVTypeRecord originalLeaf) {
            super(LF_POINTER);
            this.originalLeaf = originalLeaf.getSequenceNumber();
        }

        private int computeAttributes() {
            return kind | (mode << 5) | (modifiers << 8) | (size << 13) | (flags << 19);
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES * 2;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(originalLeaf, buffer, initialPos);
            pos = CVUtil.putInt(computeAttributes(), buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_POINTER(0x%04x * 0x%04x)", getSequenceNumber(), originalLeaf);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + originalLeaf;
            h = 31 * h + computeAttributes();
            return h;
        }
    }

    static final class CVTypeProcedureRecord extends CVTypeRecord {

        int returnType = -1;
        CVTypeArglistRecord argList = null;

        CVTypeProcedureRecord() {
            super(LF_PROCEDURE);
        }

        public CVTypeProcedureRecord returnType(int leaf) {
            this.returnType = leaf;
            return this;
        }

        public CVTypeProcedureRecord returnType(CVTypeRecord leaf) {
            this.returnType = leaf.getSequenceNumber();
            return this;
        }

        CVTypeProcedureRecord argList(CVTypeArglistRecord leaf) {
            this.argList = leaf;
            return this;
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES + Byte.BYTES + Byte.BYTES + Short.BYTES + Integer.BYTES;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(returnType, buffer, initialPos);
            pos = CVUtil.putByte((byte) 0, buffer, pos); // callType
            pos = CVUtil.putByte((byte) 0, buffer, pos); // funcAttr
            pos = CVUtil.putShort((short) argList.getSize(), buffer, pos);
            pos = CVUtil.putInt(argList.getSequenceNumber(), buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_PROCEDURE(0x%04x ret=0x%04x arg=0x%04x)", getSequenceNumber(), returnType, argList.getSequenceNumber());
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + returnType;
            h = 31 * h + argList.hashCode();
            // h = 31 * h + // callType + funcAttr
            return h;
        }
    }

    static final class CVTypeArglistRecord extends CVTypeRecord {

        ArrayList<Integer> args = new ArrayList<>();

        CVTypeArglistRecord() {
            super(LF_ARGLIST);
        }

        CVTypeArglistRecord add(int argType) {
            args.add(argType);
            return this;
        }

        CVTypeArglistRecord add(CVTypeRecord argType) {
            args.add(argType.getSequenceNumber());
            return this;
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
            StringBuilder s = new StringBuilder(String.format("LF_ARGLIST(0x%04x [", getSequenceNumber()));
            for (Integer at : args) {
                s.append(' ').append(at);
            }
            s.append("])");
            return s.toString();
        }

        @Override
        public int hashCode() {
            int h = type;
            h = h * 31 + args.size();
            for (Integer r : args) {
                h = 31 * h + r.hashCode();
            }
            return h;
        }
    }

    static final class CVMemberRecord extends CVTypeRecord {

        short  propertyAttributes;      /* property attribute field (prop_t) */
        int        fieldIndex;          /* type index of member type */
        /* TODO data */

        CVMemberRecord(short attrs, int fieldIndex) {
            super(LF_MEMBER);
            this.propertyAttributes = attrs;
            this.fieldIndex = fieldIndex;
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Short.BYTES + Integer.BYTES; /* + TODO */
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(propertyAttributes, buffer, initialPos);
            pos = CVUtil.putInt(fieldIndex, buffer, pos);
            /* TODO */
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_MEMBER(0x%04x attr=0x%04x fld=0x%x)", getSequenceNumber(), propertyAttributes, fieldIndex);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + propertyAttributes;
            h = 31 * h + fieldIndex;
            return h;
        }
    }

    static class CVBaseClassRecord extends CVTypeRecord {

        short  propertyAttributes;      /* property attribute field (prop_t) */
        int        fieldIndex;          /* type index of member type */
        /* TODO data */

        CVBaseClassRecord(short ltype, short attrs, int fieldIndex) {
            super(ltype);
            this.propertyAttributes = attrs;
            this.fieldIndex = fieldIndex;
        }

        CVBaseClassRecord(short attrs, int fieldIndex) {
            this(LF_BCLASS, attrs, fieldIndex);
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Short.BYTES + Integer.BYTES; // + TODO
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(propertyAttributes, buffer, initialPos);
            pos = CVUtil.putInt(fieldIndex, buffer, pos);
            // TODO
            return pos;
        }

        protected String toString(String leafStr) {
            return String.format("%s(0x%04x attr=0x%04x fld=0x%x)", leafStr, getSequenceNumber(), propertyAttributes, fieldIndex);
        }

        @Override
        public String toString() {
            return toString("LF_BASECLASS");
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + propertyAttributes;
            h = 31 * h + fieldIndex;
            return h;
        }
    }

    static class CVBaseIntefaceRecord extends CVBaseClassRecord {

        CVBaseIntefaceRecord(short attrs, int fieldIndex) {
            super(LF_BINTERFACE, attrs, fieldIndex);
        }

        @Override
        public String toString() {
            return toString("LF_BINTERFACE");
        }

    }

    static class CVClassRecord extends CVTypeRecord {

        short  count;                   /* count of number of elements in class */
        short  propertyAttributes;      /* property attribute field (prop_t) */
        int        fieldIndex;          /* type index of LF_FIELDLIST descriptor list */
        int        derivedFromIndex;    /* type index of derived from list if not zero */
        int        vshapeIndex;         /* type index of vshape table for this class */
        /* TODO data */

        CVClassRecord(short recType, short count, short attrs, int fieldIndex, int derivedFromIndex, int vshapeIndex) {
            super(recType);
            this.count = count;
            this.propertyAttributes = attrs;
            this.fieldIndex = fieldIndex;
            this.derivedFromIndex = derivedFromIndex;
            this.vshapeIndex = vshapeIndex;
        }

        CVClassRecord(short count, short attrs, int fieldIndex, int derivedFromIndex, int vshapeIndex) {
            this(LF_CLASS, count, attrs, fieldIndex, derivedFromIndex, vshapeIndex);
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Short.BYTES + Short.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES; // + TODO
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putShort(count, buffer, initialPos);
            pos = CVUtil.putShort(propertyAttributes, buffer, pos);
            pos = CVUtil.putInt(fieldIndex, buffer, pos);
            pos = CVUtil.putInt(derivedFromIndex, buffer, pos);
            pos = CVUtil.putInt(vshapeIndex, buffer, pos);
            // TODO
            return pos;
        }

        protected String toString(String lfTypeStr) {
            return String.format("%s(0x%04x count=%d attr=0x%04x fld=0x%x super=0x%x vshape=0x%x)", lfTypeStr, getSequenceNumber(), count, propertyAttributes, fieldIndex, derivedFromIndex, vshapeIndex);
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
            h = 31 * h + vshapeIndex;
            return h;
        }
    }

    static final class CVStructRecord extends CVClassRecord {
        CVStructRecord(short count, short attrs, int fieldIndex, int derivedFromIndex, int vshape) {
            super(LF_STRUCTURE, count, attrs, fieldIndex, derivedFromIndex, vshape);
        }

        @Override
        public String toString() {
            return toString("LF_STRUCT");
        }
    }

    static final class CVInterfaceRecord extends CVClassRecord {
        CVInterfaceRecord(short count, short attrs, int fieldIndex, int derivedFromIndex, int vshape) {
            super(LF_INTERFACE, count, attrs, fieldIndex, derivedFromIndex, vshape);
        }

        @Override
        public String toString() {
            return toString("LF_INTERFACE");
        }
    }

    static final class CVTypeBitfieldRecord extends CVTypeRecord {

        byte length;
        byte position;
        int typeIndex;

        CVTypeBitfieldRecord(int length, int position, int typeIndex) {
            super(LF_BITFIELD);
            this.length = (byte) length;
            this.position = (byte) position;
            this.typeIndex = typeIndex;
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES + Byte.BYTES + Byte.BYTES;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(typeIndex, buffer, initialPos);
            pos = CVUtil.putByte(length, buffer, pos);
            pos = CVUtil.putByte(position, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_BITFIELD(0x%04x, type=0x%04x len=%d pos=%d)", getSequenceNumber(), typeIndex, length, position);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + position;
            h = 31 * h + length;
            h = 31 * h + typeIndex;
            return h;
        }
    }

    static final class CVTypeArrayRecord extends CVTypeRecord {

        int elementType = -1;
        int indexType = -1;
        int length = -1;

        CVTypeArrayRecord(int elementType, int indexType, int length) {
            super(LF_ARRAY);
            this.elementType = elementType;
            this.indexType = indexType;
            this.length = length;
        }

        CVTypeArrayRecord(int elementType, int length) {
            super(LF_ARRAY);
            this.elementType = elementType;
            this.indexType = T_UQUAD;
            this.length = length;
        }

        CVTypeArrayRecord(CVTypeRecord elementType, int length) {
            super(LF_ARRAY);
            this.elementType = elementType.getSequenceNumber();
            this.indexType = T_UQUAD;
            this.length = length;
        }

        @Override
        public int computeSize(int initialPos) {
            return initialPos + Integer.BYTES * 3;
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(elementType, buffer, initialPos);
            pos = CVUtil.putInt(indexType, buffer, pos);
            pos = CVUtil.putInt(length, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_ARRAY(0x%04x type=0x%04x len=%d indexType=0x%04x)", getSequenceNumber(), elementType, length, indexType);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + elementType;
            h = 31 * h + indexType;
            h = 31 * h + length;
            return h;
        }
    }

    static final class CVTypeServer2Record extends CVTypeRecord {

        byte[] guid;
        int age;
        String fileName;

        CVTypeServer2Record(byte[] guid, int age, String fileName) {
            super(LF_TYPESERVER2);
            assert (guid.length == 16);
            this.guid = guid;
            this.age = age;
            this.fileName = fileName;

            /*  for some very odd reason GUID is stored like this:
                int guid1 = in.getInt();
                int guid2 = in.getShort();
                int guid3 = in.getShort();
                byte[] guid5[10]
                */
            swap(this.guid, 0, 3);
            swap(this.guid, 1, 2);
            swap(this.guid, 4, 5);
            swap(this.guid, 6, 7);
        }

        @Override
        public int computeSize(int initialPos) {
            return computeContents(null, initialPos);
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putBytes(guid, buffer, initialPos);
            pos = CVUtil.putInt(age, buffer, pos);
            pos = CVUtil.putUTF8StringBytes(fileName, buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_TYPESERVER2(0x%04x '%s' age=%d)", getSequenceNumber(), fileName, age);
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + Arrays.hashCode(guid);
            h = 31 * h + age;
            h = 31 * h + fileName.hashCode();
            return h;
        }

        private static void swap(byte[] b, int idx1, int idx2) {
            byte tmp = b[idx1];
            b[idx1] = b[idx2];
            b[idx2] = tmp;
        }
    }
}
