/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

import java.util.ArrayList;

import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_ARGLIST;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD1;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD2;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PAD3;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_PROCEDURE;

/*
 * CV Type Record format (little-endian):
 * uint16 length
 * uint16 leaf (a.k.a. record type)
 * (contents)
 */
abstract class CVTypeRecord {

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
        this.startPosition = initialPos;
        int pos = initialPos + Short.BYTES * 2; /* Save room for length and leaf type. */
        pos = computeSize(pos);
        pos = alignPadded4(null, pos);
        return pos;
    }

    int computeFullContents(byte[] buffer, int initialPos) {
        int pos = initialPos + Short.BYTES; /* Save room for length short. */
        pos = CVUtil.putShort(type, buffer, pos);
        pos = computeContents(buffer, pos);
        /* Length does not include record length (2 bytes)) but does include end padding. */
        pos = alignPadded4(buffer, pos);
        int length = (short) (pos - initialPos - Short.BYTES);
        CVUtil.putShort((short) length, buffer, initialPos);
        return pos;
    }

    protected abstract int computeSize(int initialPos);

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
            return computeContents(null, initialPos);
        }

        @Override
        public int computeContents(byte[] buffer, int initialPos) {
            int pos = CVUtil.putInt(returnType, buffer, initialPos);
            pos = CVUtil.putByte((byte) 0, buffer, pos); /* callType */
            pos = CVUtil.putByte((byte) 0, buffer, pos); /* funcAttr */
            pos = CVUtil.putShort((short) argList.getSize(), buffer, pos);
            pos = CVUtil.putInt(argList.getSequenceNumber(), buffer, pos);
            return pos;
        }

        @Override
        public String toString() {
            return String.format("LF_PROCEDURE 0x%04x ret=0x%04x arg=0x%04x ", getSequenceNumber(), returnType, argList.getSequenceNumber());
        }

        @Override
        public int hashCode() {
            int h = type;
            h = 31 * h + returnType;
            h = 31 * h + argList.hashCode();
            /* callType and funcAttr are always zero so do not add them to the hash */
            return h;
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }
            CVTypeProcedureRecord other = (CVTypeProcedureRecord) obj;
            return this.returnType == other.returnType && this.argList == other.argList;
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
}
