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
package com.oracle.svm.tutorial;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CFieldOffset;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.c.ProjectHeaderFile;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.tutorial.CInterfaceTutorial.CInterfaceTutorialDirectives;

@CContext(CInterfaceTutorialDirectives.class)
public class CInterfaceTutorial {

    static class CInterfaceTutorialDirectives implements CContext.Directives {

        @Override
        public List<String> getHeaderFiles() {
            /*
             * The header file with the C declarations that are imported. We use a helper class that
             * locates the file in our project structure.
             */
            return Collections.singletonList(ProjectHeaderFile.resolve("com.oracle.svm.tutorial", "native/mydata.h"));
        }
    }

    /* Import a C constant. A call to the function is replaced with the constant value. */
    @CConstant("DATA_ARRAY_LENGTH")
    protected static native int getDataLength();

    /* Import a C structure, with accessor methods for every field. */
    @CStruct("my_data")
    interface MyData extends PointerBase {

        /* Read access of a field. A call to the function is replaced with a raw memory load. */
        @CField("f_primitive")
        int getPrimitive();

        /* Write access of a field. A call to the function is replaced with a raw memory store. */
        @CField("f_primitive")
        void setPrimitive(int value);

        /* Address of a field. A call to the function is replaced with address arithmetic. */
        @CFieldAddress("f_array")
        CIntPointer addressOfArray();

        @CField("f_cstr")
        CCharPointer getCString();

        @CField("f_cstr")
        void setCString(CCharPointer value);

        @CField("f_java_object_handle")
        ObjectHandle getJavaObject();

        @CField("f_java_object_handle")
        void setJavaObject(ObjectHandle value);

        @CField("f_print_function")
        PrintFunctionPointer getPrintFunction();

        @CField("f_print_function")
        void setPrintFunction(PrintFunctionPointer printFunction);
    }

    @CEnum("day_of_the_week_t")
    enum DayOfTheWeek {
        /*
         * NOTE: unlike the C enum, the Java enum starts with SUNDAY, but the C values and Java
         * constants are still converted correctly.
         */
        SUNDAY,
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY;

        @CEnumValue
        public native int getCValue();

        @CEnumLookup
        public static native DayOfTheWeek fromCValue(int value);
    }

    /* Import of a C function pointer type. */
    interface PrintFunctionPointer extends CFunctionPointer {

        /*
         * Invocation of the function pointer. A call to the function is replaced with an indirect
         * call of the function pointer.
         */
        @InvokeCFunctionPointer
        void invoke(IsolateThread thread, CCharPointer cstr);
    }

    /* Import of a C function from the standard C library. */
    @CFunction
    protected static native PointerBase memcpy(PointerBase dest, PointerBase src, UnsignedWord n);

    /* Import of a C function. */
    @CFunction("c_print")
    protected static native void printingInC(IsolateThread thread, CCharPointer cstr);

    /*
     * Address of an externally visible Java function. The function pointer can be passed to C code
     * and invoked from there like any C function pointer.
     */
    protected static final CEntryPointLiteral<PrintFunctionPointer> javaPrintFunction = CEntryPointLiteral.create(CInterfaceTutorial.class, "printingInJava", IsolateThread.class, CCharPointer.class);

    /* The function addressed by the above function pointer. */
    @CEntryPoint
    protected static void printingInJava(@SuppressWarnings("unused") IsolateThread thread, CCharPointer cstr) {
        System.out.println("J: " + CTypeConversion.toJavaString(cstr));
    }

    protected static CCharPointerHolder pin;

    protected static void dump(MyData data) {
        System.out.format("**** In Java ****\n");
        System.out.format("primitive: %d\n", data.getPrimitive());
        System.out.format("length: %d\n", getDataLength());
        for (int i = 0; i < getDataLength(); i++) {
            System.out.format("%d ", data.addressOfArray().read(i));
        }
        System.out.format("\n");

        IsolateThread currentThread = CurrentIsolate.getCurrentThread();
        /* Call a C function directly. */
        if (OS.getCurrent() != OS.WINDOWS) {
            /*
             * Calling C functions provided by the main executable from a shared library produced by
             * the native-image is not yet supported on Windows.
             */
            printingInC(currentThread, data.getCString());
        }
        /* Call a C function indirectly via function pointer. */
        data.getPrintFunction().invoke(currentThread, data.getCString());
    }

    /* Java function that can be called directly from C code. */
    @CEntryPoint(name = "java_entry_point")
    protected static void javaEntryPoint(@SuppressWarnings("unused") IsolateThread thread, MyData data) {
        /* Allocate a C structure in our stack frame. */
        MyData copy = StackValue.get(MyData.class);

        /* Get the size of a C structure. */
        int dataSize = SizeOf.get(MyData.class);

        /* Call a function from the standard C library. */
        memcpy(copy, data, WordFactory.unsigned(dataSize));

        dump(copy);

        /* Modify primitive data of a C structure. */
        data.setPrimitive(99);
        data.addressOfArray().write(1, 101);

        /* Pass out a pointer into a Java byte[] array that is pinned. */
        String javaString = CTypeConversion.toJavaString(data.getCString()) + " at " + new Date();
        pin = CTypeConversion.toCString(javaString);
        CCharPointer cString = pin.get();
        data.setCString(cString);

        /* Install a function pointer to a Java function. */
        data.setPrintFunction(javaPrintFunction.getFunctionPointer());

        /* Create a handle to a Java object. */
        data.setJavaObject(ObjectHandles.getGlobal().create(javaString));
    }

    /* Java function that can be called directly from C code. */
    @CEntryPoint(name = "java_release_data")
    protected static void releaseData(@SuppressWarnings("unused") IsolateThread thread, MyData data) {
        dump(data);

        /* Retrieve the object we have stored in a handle. */
        ObjectHandle handle = data.getJavaObject();
        String javaObject = ObjectHandles.getGlobal().get(handle);
        System.out.format("javaObject: %s\n", javaObject);
        /* Free the handle. After this call, the handle must not be used anymore. */
        ObjectHandles.getGlobal().destroy(handle);

        /*
         * Release the pin to the byte[] array. After this call, the field f_cstr of our data object
         * must not be accessed anymore, the memory it points to might no longer be valid.
         */
        pin.close();
    }

    /* C function for which Java enum constants are automatically converted to C enum values. */
    @CFunction("day_of_the_week_add")
    protected static native DayOfTheWeek dayOfTheWeekAdd(DayOfTheWeek day, int offset);

    /* Java function for which C enum values are automatically converted to Java enum constants. */
    @CEntryPoint(name = "java_print_day")
    protected static void printDay(@SuppressWarnings("unused") IsolateThread thread, DayOfTheWeek day) {
        System.out.format("Day: %s (Java ordinal: %d, C value: %d)%n", day.name(), day.ordinal(), day.getCValue());
        if (OS.getCurrent() != OS.WINDOWS) {
            /*
             * Calling C functions provided by the main executable from a shared library produced by
             * the native-image is not yet supported on Windows.
             */
            System.out.format(" follows %s and %s%n", dayOfTheWeekAdd(day, -2), dayOfTheWeekAdd(day, -1));
            System.out.format(" is followed by %s and %s%n", dayOfTheWeekAdd(day, +1), dayOfTheWeekAdd(day, +2));
        }
    }

    /*
     * Example of exploiting sub-typing. A common C programming idiom is to mimic inheritance by
     * embedding a common structure as first field. For example, a header structure h_t can be
     * embedded as first field in a number of other struct definition. This allows factoring code
     * that operates only on the header, and to downcast pointers to specific struct. You can
     * exploit Java's inheritance as shown below.
     */

    @CStruct("h_t")
    interface Header extends PointerBase {

        @CField
        byte type();

        @CFieldAddress("name")
        CCharPointer name();

        @CFieldAddress("type")
        CCharPointer typePtr();
    }

    @CStruct("subdata_t")
    interface Substruct1 extends Header {

        @CFieldAddress
        Header header();

        /*
         * This is how you get offset_of for a particular field. You can specify the name of the
         * field you want the offset of if you don't use the default naming (see below).
         *
         * Java does not allow interface methods to be defined "static native". So we need a method
         * body (which is ignored). The other option is to define the method non-static (see below).
         */
        @CFieldOffset("header")
        static int offsetOfHeader() {
            throw VMError.shouldNotReachHere("Calls to the method are replaced with a compile time constant for the offset, so this method body is not reachable.");
        }

        @CField
        int f1();

        @CFieldAddress
        CIntPointer addressOff1();

        /*
         * This is how you get offset_of for a particular field. This uses the default naming
         * convention, i.e., prefix the field name with offsetOf, so there's no need to pass the
         * field name in the annotation.
         */
        @CFieldOffset
        int offsetOff1();

        /*
         * This is how you can compute addresses to struct in an array of struct, e.g., subdata_t
         * array[10].
         */
        Substruct1 addressOf(int index);
    }

    @CStruct("subdata_t")
    interface Substruct2 extends PointerBase {

        @CFieldAddress
        Header header();

        @CFieldOffset("header")
        int offsetOfHeader();

        @CField
        int f1();

        @CFieldOffset
        UnsignedWord offsetOff1();
    }

    @CEntryPoint(name = "java_entry_point2")
    protected static void javaEntryPoint2(@SuppressWarnings("unused") IsolateThread thread, Substruct1 s1, Substruct2 s2) {
        System.out.println("*** In Java, demonstrating inheritance with @CStruct class");
        CCharPointer tp1 = s1.typePtr();
        CCharPointer tp2 = s2.header().typePtr();
        System.out.println("tp1 = 0x" + Long.toHexString(tp1.rawValue()) + " tp2 = 0x" + Long.toHexString(tp2.rawValue()));

        System.out.println("&s1.header = 0x" + Long.toHexString(s1.header().rawValue()) + " &s2.header = 0x" + Long.toHexString(s2.header().rawValue()));
        System.out.println("s1.f1 = " + s1.f1() + "  s2.f2 = " + s2.f1());
        System.out.println("&s1.f1 = 0x" + Long.toHexString(s1.addressOff1().rawValue()));
        System.out.println("*&s1.f1 = " + s1.addressOff1().read());
        System.out.println("offset_of(s1.f1) = " + s1.offsetOff1());

        System.out.println("s1.header.type = " + s1.type() + "  ((Header) s2).type = " + s2.header().type());

        System.out.println("*** In Java, demonstrating @CFieldOffset");
        System.out.println("offset_of(s1.header) = " + Substruct1.offsetOfHeader());
        System.out.println("offset_of(s2.header) = " + s2.offsetOfHeader());
        System.out.println("offset_of(s2.f1) = " + s2.offsetOff1().rawValue());

        /*
         * Accessing elements of an array of struct. Say that s1 is the first element of subdata_t
         * s[10].
         */
        Substruct1 ps1 = s1.addressOf(0);  // ps1 = &s[0]
        Substruct1 ps2 = s1.addressOf(1);  // ps2 = &s[1]
        long s = ps2.rawValue() - ps1.rawValue();
        System.out.print("sizeof(s1) =" + SizeOf.get(Substruct1.class));
        System.out.print(" s1 = 0x" + Long.toHexString(s1.rawValue()));
        System.out.print("  ps1 = 0x" + Long.toHexString(ps1.rawValue()));
        System.out.println("  ps2 = 0x" + Long.toHexString(ps2.rawValue()));
        System.out.println(" ps2 - ps1 " + (s == SizeOf.get(Substruct1.class) ? "=" : "!=") + " sizeof(substruct1)");
    }

    /*
     * The following shows one way to work with union type. A union type du_t has two members, d1_t
     * and d2_t. The members of the union type may be access via a @CFieldAddress. Accessing the
     * int_value field of union member d1 in C can be achieved with ((du_t*)p)->d1.int_value This
     * translates as ((DU) p).getD1().getIntValue()
     */

    @CStruct("d1_t")
    interface D1 extends PointerBase {
        @CField("int_value")
        int getIntValue();

        @CFieldAddress("int_value")
        CIntPointer getValuePointer();

        @CField("int_pointer")
        CIntPointer getIntPointer();
    }

    @CStruct("d2_t")
    interface D2 extends PointerBase {
        @CField("long_value")
        long getLongValue();

        @CFieldAddress("long_value")
        CLongPointer getValuePointer();

        @CField("long_pointer")
        CLongPointer getLongPointer();
    }

    /*
     * This is one way to access element of a pointer to an union.
     */
    @CStruct("du_t")
    interface DU extends PointerBase {
        @CFieldAddress("d1")
        D1 getD1();

        @CFieldAddress("d2")
        D2 getD2();
    }

    @CEntryPoint(name = "java_entry_point3")
    protected static void javaEntryPoint3(@SuppressWarnings("unused") IsolateThread thread, DU du1, DU du2, D1 d1, D2 d2) {
        System.out.println("*** In Java, demonstrating access to union type member with @CStruct class");

        if (du1.getD1().notEqual(d1)) {
            System.out.println("*** Error with Union test1: du1 should be equal to d1");
        } else {
            System.out.println("Union test 1 passed (0x" + Long.toHexString(d1.rawValue()).toLowerCase() + ")");
        }

        if (!du2.getD2().equal(d2)) {
            System.out.println("*** Error with Union test2: du2 should be equal to d2");
        } else {
            System.out.println("Union test 2 passed (0x" + Long.toHexString(d2.rawValue()).toLowerCase() + ")");
        }

        if (d2.getValuePointer().notEqual(d2.getLongPointer())) {
            System.out.println("*** Error with Union test3: d2.long_pointer != &d2.long_value");
        } else {
            System.out.println("Union test 3 passed (0x" + Long.toHexString(d2.getLongPointer().rawValue()).toLowerCase() + ")");
        }

        if (d2.getValuePointer().read() != d2.getLongValue()) {
            System.out.println("*** Error with Union test4: *d2.long_pointer != d2.long_value");
        } else {
            System.out.println("Union test 4 passed (" + d2.getLongValue() + ")");
        }
    }

    /*
     * Showing how to access unsigned integer types in @CStruct.
     */
    @CStruct("sudata_t")
    interface SUData extends PointerBase {
        @CField("f_ub1")
        byte getUB1();

        @AllowWideningCast
        @CField("f_ub1")
        UnsignedWord getUB1Unsigned();

        @CField("f_sb1")
        byte getSB1();

        @AllowWideningCast
        @CField("f_sb1")
        SignedWord getSB1Signed();
    }

    @CEntryPoint(name = "getUB1_raw_value")
    protected static long getUB1RawValue(@SuppressWarnings("unused") IsolateThread thread, SUData sudata) {
        return sudata.getUB1();
    }

    @CEntryPoint(name = "getUB1_masked_raw_value")
    protected static long getUB1MaskedRawValue(@SuppressWarnings("unused") IsolateThread thread, SUData sudata) {
        return sudata.getUB1() & 0xFF;
    }

    @CEntryPoint(name = "getUB1_as_Unsigned_raw_value")
    protected static long getUB1AsUnsignedRawValue(@SuppressWarnings("unused") IsolateThread thread, SUData sudata) {
        return sudata.getUB1Unsigned().rawValue();
    }

    @CEntryPoint(name = "java_entry_point4")
    protected static void javaEntryPoint4(@SuppressWarnings("unused") IsolateThread thread, SUData sudata) {
        int i = 0;
        UnsignedWord u = sudata.getUB1Unsigned();
        SignedWord s = sudata.getSB1Signed();
        i = sudata.getUB1();
        System.out.println(" getUB1() = " + i + (i < 0 ? "<" : ">=") + " 0");

        System.out.println(" getUB1Unsigned() = " + u.rawValue() + (u.rawValue() < 0 ? "<" : ">=") + " 0");
        i = sudata.getSB1();
        System.out.println(" getSB1() = " + i + (i < 0 ? "<" : ">=") + " 0");
        System.out.println(" getSB1Signed() = " + s.rawValue() + (s.rawValue() < 0 ? "<" : ">=") + " 0");

        System.out.println("(byte) 245        = " + ((byte) 245));
        System.out.println("(byte) 245 & 0xFF = " + (((byte) 245) & 0xFF));
        System.out.println("sudata.getUB1Unsigned().aboveOrEqual(220) = " + sudata.getUB1Unsigned().aboveOrEqual(220));
        System.out.println("sudata.getUB1Unsigned().aboveOrEqual(245) = " + sudata.getUB1Unsigned().aboveOrEqual(245));
        System.out.println("sudata.getUB1Unsigned().aboveOrEqual((byte)220) = " + sudata.getUB1Unsigned().aboveOrEqual((byte) 220));
        System.out.println("sudata.getUB1Unsigned().aboveOrEqual((byte)245) = " + sudata.getUB1Unsigned().aboveOrEqual((byte) 245));
        System.out.println("sudata.getUB1() && 0xFF >  220 = " + ((sudata.getUB1() & 0xFF) > 220));
        System.out.println("sudata.getUB1() && 0xFF >  245 = " + ((sudata.getUB1() & 0xFF) > 245));
    }
}
