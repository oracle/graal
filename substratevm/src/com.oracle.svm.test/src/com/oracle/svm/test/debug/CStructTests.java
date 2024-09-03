/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.debug;

import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;

import com.oracle.svm.core.NeverInline;

import jdk.graal.compiler.api.directives.GraalDirectives;

@CContext(CInterfaceDebugTestDirectives.class)
public class CStructTests {
    @CPointerTo(nameOfCType = "int")
    public interface MyCIntPointer extends PointerBase {
        void write(int value);

        void write(int index, int value);

        void write(SignedWord index, int value);

        MyCIntPointer addressOf(int index);

        MyCIntPointer addressOf(SignedWord index);
    }

    // Checkstyle: stop
    @CStruct("struct weird")
    interface Weird extends PointerBase {

        @CField
        short getf_short();

        @CField
        void setf_short(short value);

        @CField
        int getf_int();

        @CField
        void setf_int(int value);

        @CField
        long getf_long();

        @CField
        void setf_long(long value);

        @CField
        float getf_float();

        @CField
        void setf_float(float value);

        @CField
        double getf_double();

        @CField
        void setf_double(double value);

        @CFieldAddress
        MyCIntPointer addressOfa_int();

        @CFieldAddress
        CCharPointer addressOfa_char();

        Weird getElement(int index);
    }

    // Checkstyle: resume

    /*-
        checkedBreakpoint('com.oracle.svm.enterprise.debug.test.CStructTests.free', [
            {'frame#' : 1, 'eval' : [
                ('wd', [
                    chkVal('CStruct com.oracle.svm.enterprise.debug.test.CStructTests$Weird = {'
                           '  a_char = byte[12] = {48, 49, 50, 51, 52, 53, 54, 55, 56, 57, ...},'
                           '  a_int = int[8] = {0, 1, 2, 3, 4, 5, 6, 7},'
                           '  f_double = 4.5999999999999996,'
                           '  f_float = 4.5,'
                           '  f_int = 43,'
                           '  f_long = 44,'
                           '  f_short = 42'
                           '}', castFn=str)
                ])
            ]}
        ])
     */
    public static void weird() {
        Weird wd = UnmanagedMemory.malloc(SizeOf.get(Weird.class));

        wd.setf_short((short) 42);
        wd.setf_int(43);
        wd.setf_long(44);
        wd.setf_float(4.5F);
        wd.setf_double(4.6);

        final int intArraySize = 8;
        MyCIntPointer pi = wd.addressOfa_int();
        for (int i = 0; i < intArraySize; i++) {
            pi.write(i, i);
        }

        final String text = "0123456789AB";
        try (CCharPointerHolder pin = CTypeConversion.toCString(text)) {
            CCharPointer cstring = pin.get();
            long size = text.length();

            CCharPointer field = wd.addressOfa_char();
            for (int i = 0; i < size; i++) {
                field.write(i, cstring.read(i));
            }

            free(wd);
        }
    }

    // Checkstyle: stop
    @CStruct("struct simple_struct")
    interface SimpleStruct extends PointerBase {
        @CField("first")
        void setFirst(int value);

        @CField("second")
        void setSecond(int value);
    }// Checkstyle: resume

    // Checkstyle: stop
    @CStruct("struct simple_struct2")
    interface SimpleStruct2 extends PointerBase {
        @CField("alpha")
        void setAlpha(byte value);

        @CField("beta")
        void setBeta(long value);
    }// Checkstyle: resume

    // Checkstyle: stop
    @CStruct("struct composite_struct")
    interface CompositeStruct extends PointerBase {
        @CField("c1")
        void setC1(byte value);

        @CFieldAddress("c2")
        SimpleStruct getC2();

        @CField("c3")
        void setC3(int value);

        @CFieldAddress("c4")
        SimpleStruct2 getC4();

        @CField("c5")
        void setC5(short value);

    }// Checkstyle: resume

    /*-
        checkedBreakpoint('com.oracle.svm.enterprise.debug.test.CStructTests.free', [
            {'frame#' : 1, 'eval' : [
                ('cs', [
                    chkVal('CStruct com.oracle.svm.enterprise.debug.test.CStructTests$CompositeStruct = {'
                           '  c1 = 7,'
                           '  c2 = {'
                           '    first = 17,'
                           '    second = 19'
                           '  },'
                           '  c3 = 13,'
                           '  c4 = {'
                           '    alpha = 3,'
                           '    beta = 9223372036854775807'
                           '  },'
                           '  c5 = 32000}', castFn=str)
                ])
            ]}
        ])
     */
    public static void composite() {
        CompositeStruct cs = UnmanagedMemory.malloc(3 * SizeOf.get(CompositeStruct.class));
        cs.setC1((byte) 7);
        cs.setC3(13);
        cs.setC5((short) 32000);
        cs.getC2().setFirst(17);
        cs.getC2().setSecond(19);
        cs.getC4().setAlpha((byte) 3);
        cs.getC4().setBeta(Long.MAX_VALUE);
        free(cs);
    }

    public static void mixedArguments() {
        SimpleStruct ss1 = UnmanagedMemory.malloc(SizeOf.get(SimpleStruct.class));
        SimpleStruct2 ss2 = UnmanagedMemory.malloc(SizeOf.get(SimpleStruct2.class));
        String m1 = "a message in a bottle";
        String m2 = "a ship in a bottle";
        String m3 = "courage in a bottle";
        ss1.setFirst(1);
        ss1.setSecond(2);
        ss2.setAlpha((byte) 99);
        ss2.setBeta(100L);
        testMixedArguments(m1, (short) 1, ss1, 123456789L, m2, ss2, m3);
        free(ss1);
        free(ss2);
    }

    private static void testMixedArguments(String m1, short s, SimpleStruct ss1, long l, String m2, SimpleStruct2 ss2, String m3) {
        System.out.println("You find " + m1);
        System.out.println("You find " + m2);
        System.out.println("You find " + m3);
        GraalDirectives.blackhole(s);
        GraalDirectives.blackhole(ss1);
        GraalDirectives.blackhole(l);
        GraalDirectives.blackhole(ss2);
    }

    public static void main(String[] args) {
        CStructTests.composite();
        CStructTests.weird();
    }

    @NeverInline("Used as a hook to inspect the caller frame in GDB")
    static void free(PointerBase ptr) {
        UnmanagedMemory.free(ptr);
    }
}
