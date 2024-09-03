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

import com.oracle.objectfile.debugentry.TypeEntry;
import com.oracle.objectfile.io.Utf8;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;

import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_AL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_AX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_BL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_BP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_BPL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_BX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_CL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_CX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DIL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_DX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EAX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EBP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EBX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_ECX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EDI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_EDX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_ESI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_ESP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R10;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R10B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R10D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R10W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R11;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R11B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R11D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R11W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R12;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R12B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R12D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R12W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R13;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R13B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R13D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R13W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R14;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R14B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R14D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R14W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R15;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R15B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R15D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R15W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R8W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9B;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9D;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_R9W;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RAX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RBP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RBX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RCX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RDI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RDX;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RSI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_RSP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SI;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SIL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SP;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_SPL;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM0L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM0_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM10L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM10_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM11L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM11_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM12L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM12_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM13L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM13_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM14L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM14_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM15L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM15_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM1L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM1_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM2L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM2_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM3L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM3_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM4L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM4_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM5L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM5_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM6L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM6_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM7L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM7_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM8L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM8_0;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM9L;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_AMD64_XMM9_0;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_CHAR;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_LONG;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_QUADWORD;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_SHORT;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_ULONG;
import static com.oracle.objectfile.pecoff.cv.CVTypeConstants.LF_USHORT;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class CVUtil {

    /**
     * Store a byte value in the buffer.
     *
     * @param value value to store
     * @param buffer buffer to store value in
     * @param initialPos initial position in buffer
     * @return position in buffer following stored value
     */
    static int putByte(byte value, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Byte.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = value;
        return pos;
    }

    /**
     * Store a short value in the buffer.
     *
     * @param value value to store
     * @param buffer buffer to store value in
     * @param initialPos initial position in buffer
     * @return position in buffer following stored value
     */
    static int putShort(short value, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Short.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (value & 0xff);
        buffer[pos++] = (byte) ((value >> 8) & 0xff);
        return pos;
    }

    /**
     * Store an integer value in the buffer.
     *
     * @param value value to store
     * @param buffer buffer to store value in
     * @param initialPos initial position in buffer
     * @return position in buffer following stored value
     */
    static int putInt(int value, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Integer.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (value & 0xff);
        buffer[pos++] = (byte) ((value >> 8) & 0xff);
        buffer[pos++] = (byte) ((value >> 16) & 0xff);
        buffer[pos++] = (byte) ((value >> 24) & 0xff);
        return pos;
    }

    /**
     * Store a long value in the buffer.
     *
     * @param value value to store
     * @param buffer buffer to store value in
     * @param initialPos initial position in buffer
     * @return position in buffer following stored value
     */
    @SuppressWarnings("unused")
    static int putLong(long value, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + Long.BYTES;
        }
        int pos = initialPos;
        buffer[pos++] = (byte) (value & 0xff);
        buffer[pos++] = (byte) ((value >> 8) & 0xff);
        buffer[pos++] = (byte) ((value >> 16) & 0xff);
        buffer[pos++] = (byte) ((value >> 24) & 0xff);
        buffer[pos++] = (byte) ((value >> 32) & 0xff);
        buffer[pos++] = (byte) ((value >> 40) & 0xff);
        buffer[pos++] = (byte) ((value >> 48) & 0xff);
        buffer[pos++] = (byte) ((value >> 56) & 0xff);
        return pos;
    }

    static int putBytes(byte[] inbuff, byte[] buffer, int initialPos) {
        if (buffer == null) {
            return initialPos + inbuff.length;
        }
        int pos = initialPos;
        for (byte b : inbuff) {
            buffer[pos++] = b;
        }
        return pos;
    }

    static int putUTF8StringBytes(String s, byte[] buffer, int initialPos) {
        assert !s.contains("\0");
        if (buffer == null) {
            return initialPos + Utf8.utf8Length(s) + 1;
        }
        byte[] buff = s.getBytes(UTF_8);
        int pos = putBytes(buff, buffer, initialPos);
        buffer[pos++] = '\0';
        return pos;
    }

    /**
     * Some CodeView numeric fields can be variable length, depending on the value.
     *
     * @param value value to store
     * @param buffer buffer to store value in
     * @param initialPos initial position in buffer
     * @return position in buffer following stored value
     */
    static int putLfNumeric(long value, byte[] buffer, int initialPos) {
        if (0 <= value && value < 0x8000) {
            return putShort((short) value, buffer, initialPos);
        } else if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE) {
            int pos = putShort(LF_CHAR, buffer, initialPos);
            return putByte((byte) value, buffer, pos);
        } else if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE) {
            int pos = putShort(LF_SHORT, buffer, initialPos);
            return putShort((short) value, buffer, pos);
        } else if (0 <= value && value <= 0xffff) {
            int pos = putShort(LF_USHORT, buffer, initialPos);
            return putShort((short) value, buffer, pos);
        } else if (Integer.MIN_VALUE <= value && value <= Integer.MAX_VALUE) {
            int pos = putShort(LF_LONG, buffer, initialPos);
            return putInt((int) value, buffer, pos);
        } else if (0 <= value && value <= 0xffffffffL) {
            int pos = putShort(LF_ULONG, buffer, initialPos);
            return putInt((int) value, buffer, pos);
        } else {
            int pos = putShort(LF_QUADWORD, buffer, initialPos);
            return putLong(value, buffer, pos);
        }
    }

    /**
     * Align on 4 byte boundary.
     *
     * @param initialPos initial unaligned position
     * @return pos aligned on 4 byte boundary
     */
    static int align4(int initialPos) {
        int pos = initialPos;
        while ((pos & 0x3) != 0) {
            pos++;
        }
        return pos;
    }

    static class CvRegDef {
        final Register register;
        final short cv1;
        final short cv2;
        final short cv4;
        final short cv8;
        CvRegDef(Register r, short cv1, short cv2, short cv4, short cv8) {
            this.register = r;
            this.cv1 = cv1;
            this.cv2 = cv2;
            this.cv4 = cv4;
            this.cv8 = cv8;
        }
        CvRegDef(Register r, short cv4, short cv8) {
            this.register = r;
            this.cv1 = -1;
            this.cv2 = -2;
            this.cv4 = cv4;
            this.cv8 = cv8;
        }
    }

    /* First index is AMD64.(register).number, second is 1,2,4,8 bytes. */
    private static final CvRegDef[] compactRegDefs = {
        /* 8, 16, 32, 64 bits */
        new CvRegDef(AMD64.rax, CV_AMD64_AL, CV_AMD64_AX, CV_AMD64_EAX, CV_AMD64_RAX), /* rax=0 */
        new CvRegDef(AMD64.rcx, CV_AMD64_CL, CV_AMD64_CX, CV_AMD64_ECX, CV_AMD64_RCX), /* rcx */
        new CvRegDef(AMD64.rdx, CV_AMD64_DL, CV_AMD64_DX, CV_AMD64_EDX, CV_AMD64_RDX), /* rdx */
        new CvRegDef(AMD64.rbx, CV_AMD64_BL, CV_AMD64_BX, CV_AMD64_EBX, CV_AMD64_RBX), /* rbx */
        new CvRegDef(AMD64.rsp, CV_AMD64_SPL, CV_AMD64_SP, CV_AMD64_ESP, CV_AMD64_RSP), /* rsp */
        new CvRegDef(AMD64.rbp, CV_AMD64_BPL, CV_AMD64_BP, CV_AMD64_EBP, CV_AMD64_RBP), /* rbp */
        new CvRegDef(AMD64.rsi, CV_AMD64_SIL, CV_AMD64_SI, CV_AMD64_ESI, CV_AMD64_RSI), /* rsi */
        new CvRegDef(AMD64.rdi, CV_AMD64_DIL, CV_AMD64_DI, CV_AMD64_EDI, CV_AMD64_RDI), /* rdi */
        new CvRegDef(AMD64.r8, CV_AMD64_R8B, CV_AMD64_R8W, CV_AMD64_R8D, CV_AMD64_R8), /* r8 */
        new CvRegDef(AMD64.r9, CV_AMD64_R9B, CV_AMD64_R9W, CV_AMD64_R9D, CV_AMD64_R9), /* r9 */
        new CvRegDef(AMD64.r10, CV_AMD64_R10B, CV_AMD64_R10W, CV_AMD64_R10D, CV_AMD64_R10), /* r10 */
        new CvRegDef(AMD64.r11, CV_AMD64_R11B, CV_AMD64_R11W, CV_AMD64_R11D, CV_AMD64_R11), /* r11 */
        new CvRegDef(AMD64.r12, CV_AMD64_R12B, CV_AMD64_R12W, CV_AMD64_R12D, CV_AMD64_R12), /* r12 */
        new CvRegDef(AMD64.r13, CV_AMD64_R13B, CV_AMD64_R13W, CV_AMD64_R13D, CV_AMD64_R13), /* r13 */
        new CvRegDef(AMD64.r14, CV_AMD64_R14B, CV_AMD64_R14W, CV_AMD64_R14D, CV_AMD64_R14), /* r14 */
        new CvRegDef(AMD64.r15, CV_AMD64_R15B, CV_AMD64_R15W, CV_AMD64_R15D, CV_AMD64_R15), /* r15 */

        new CvRegDef(AMD64.xmm0, CV_AMD64_XMM0_0, CV_AMD64_XMM0L), /* xmm0=16 */
        new CvRegDef(AMD64.xmm1, CV_AMD64_XMM1_0, CV_AMD64_XMM1L), /* xmm1 */
        new CvRegDef(AMD64.xmm2, CV_AMD64_XMM2_0, CV_AMD64_XMM2L), /* xmm2 */
        new CvRegDef(AMD64.xmm3, CV_AMD64_XMM3_0, CV_AMD64_XMM3L), /* xmm3 */
        new CvRegDef(AMD64.xmm4, CV_AMD64_XMM4_0, CV_AMD64_XMM4L), /* xmm4 */
        new CvRegDef(AMD64.xmm5, CV_AMD64_XMM5_0, CV_AMD64_XMM5L), /* xmm5 */
        new CvRegDef(AMD64.xmm6, CV_AMD64_XMM6_0, CV_AMD64_XMM6L), /* xmm6 */
        new CvRegDef(AMD64.xmm7, CV_AMD64_XMM7_0, CV_AMD64_XMM7L), /* xmm7=23 */

        new CvRegDef(AMD64.xmm8, CV_AMD64_XMM8_0, CV_AMD64_XMM8L), /* xmm8=24 */
        new CvRegDef(AMD64.xmm9, CV_AMD64_XMM9_0, CV_AMD64_XMM9L), /* xmm9 */
        new CvRegDef(AMD64.xmm10, CV_AMD64_XMM10_0, CV_AMD64_XMM10L), /* xmm10 */
        new CvRegDef(AMD64.xmm11, CV_AMD64_XMM11_0, CV_AMD64_XMM11L), /* xmm11 */
        new CvRegDef(AMD64.xmm12, CV_AMD64_XMM12_0, CV_AMD64_XMM12L), /* xmm12 */
        new CvRegDef(AMD64.xmm13, CV_AMD64_XMM13_0, CV_AMD64_XMM13L), /* xmm13 */
        new CvRegDef(AMD64.xmm14, CV_AMD64_XMM14_0, CV_AMD64_XMM14L), /* xmm14 */
        new CvRegDef(AMD64.xmm15, CV_AMD64_XMM15_0, CV_AMD64_XMM15L), /* xmm15 */
    };

    private static final CvRegDef[] javaToCvRegisters = new CvRegDef[AMD64.xmm15.number + 1];

    static {
        for (CvRegDef def : compactRegDefs) {
            assert 0 <= def.register.number && def.register.number <= AMD64.xmm15.number;
            javaToCvRegisters[def.register.number] = def;
        }
    }

    /* convert a Java register number to a CodeView register code */
    /* thos Codeview code depends upon the register type and size */
    static short getCVRegister(int javaReg, TypeEntry typeEntry) {
        assert 0 <= javaReg && javaReg <= AMD64.xmm15.number;
        CvRegDef cvReg = javaToCvRegisters[javaReg];
        assert cvReg != null;

        final short cvCode;
        if (typeEntry.isPrimitive()) {
            switch (typeEntry.getSize()) {
                case 1:
                    cvCode = cvReg.cv1;
                    break;
                case 2:
                    cvCode = cvReg.cv2;
                    break;
                case 4:
                    cvCode = cvReg.cv4;
                    break;
                case 8:
                    cvCode = cvReg.cv8;
                    break;
                default:
                    cvCode = -1;
                    break;
            }
        } else {
            /* Objects are represented by 8 byte pointers. */
            cvCode = cvReg.cv8;
        }
        assert cvCode != -1;
        return cvCode;
    }
}
