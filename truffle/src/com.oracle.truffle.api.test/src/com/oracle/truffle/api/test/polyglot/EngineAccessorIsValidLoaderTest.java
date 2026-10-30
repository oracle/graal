/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

/**
 * Verifies that {@code EngineAccessor.isValidLoader} compares Truffle class identity without
 * initializing the class seen by the given loader (see
 * <a href="https://github.com/oracle/graal/issues/14692">#14692</a>).
 */
public class EngineAccessorIsValidLoaderTest {

    /**
     * Set by the static initializer of a loader-defined substitute for {@link Truffle}.
     */
    public static volatile boolean initialized;

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    @Test
    public void isValidLoaderDoesNotInitializeLoadedClass() throws Throwable {
        Assume.assumeFalse(ImageInfo.inImageCode());
        initialized = false;
        ClassLoader loader = new TruffleInitProbeLoader();
        boolean valid = invokeIsValidLoader(loader);
        assertFalse("Substitute Truffle class must not be identity-equal to the runtime class", valid);
        assertFalse("isValidLoader must not run the loaded class static initializer", initialized);
        Class<?> loaded = Class.forName(Truffle.class.getName(), false, loader);
        assertNotSame(Truffle.class, loaded);
        Class.forName(Truffle.class.getName(), true, loader);
        assertTrue("Sanity check: explicit initialization still runs the static initializer", initialized);
    }

    private static boolean invokeIsValidLoader(ClassLoader loader) throws Throwable {
        try {
            Class<?> engineAccessor = Class.forName("com.oracle.truffle.polyglot.EngineAccessor");
            return (boolean) ReflectionUtils.invokeStatic(engineAccessor, "isValidLoader", new Class<?>[]{ClassLoader.class}, loader);
        } catch (AssertionError ae) {
            if (ae.getCause() instanceof InvocationTargetException ite) {
                throw ite.getCause();
            }
            throw ae;
        }
    }

    /**
     * Defines a distinct {@code com.oracle.truffle.api.Truffle} whose {@code <clinit>} sets
     * {@link #initialized}.
     */
    private static final class TruffleInitProbeLoader extends ClassLoader {

        TruffleInitProbeLoader() {
            super(EngineAccessorIsValidLoaderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (Truffle.class.getName().equals(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        byte[] bytes = generateTruffleSubstitute();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    /**
     * Minimal class file for {@code public class com.oracle.truffle.api.Truffle} with a static
     * initializer that assigns {@link #initialized}{@code = true}.
     */
    private static byte[] generateTruffleSubstitute() {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(raw);
            out.writeInt(0xCAFEBABE);
            out.writeShort(0);
            out.writeShort(61); // Java 17
            // Constant pool: index 0 is reserved, count is 1 + number of entries.
            String[] utf8 = {
                            "com/oracle/truffle/api/Truffle", // 1
                            "java/lang/Object", // 3
                            "com/oracle/truffle/api/test/polyglot/EngineAccessorIsValidLoaderTest", // 5
                            "initialized", // 7
                            "Z", // 8
                            "<clinit>", // 11
                            "()V", // 12
                            "Code", // 13
                            "<init>" // 14
            };
            out.writeShort(17);
            writeUtf8(out, utf8[0]);
            writeClass(out, 1);
            writeUtf8(out, utf8[1]);
            writeClass(out, 3);
            writeUtf8(out, utf8[2]);
            writeClass(out, 5);
            writeUtf8(out, utf8[3]);
            writeUtf8(out, utf8[4]);
            writeNameAndType(out, 7, 8);
            writeFieldref(out, 6, 9);
            writeUtf8(out, utf8[5]);
            writeUtf8(out, utf8[6]);
            writeUtf8(out, utf8[7]);
            writeUtf8(out, utf8[8]);
            writeNameAndType(out, 14, 12);
            writeMethodref(out, 4, 15);

            out.writeShort(0x0021); // ACC_PUBLIC | ACC_SUPER
            out.writeShort(2); // this_class
            out.writeShort(4); // super_class
            out.writeShort(0); // interfaces_count
            out.writeShort(0); // fields_count
            out.writeShort(2); // methods_count

            // public <init>()V
            out.writeShort(0x0001);
            out.writeShort(14);
            out.writeShort(12);
            out.writeShort(1);
            out.writeShort(13); // Code
            out.writeInt(17);
            out.writeShort(1); // max_stack
            out.writeShort(1); // max_locals
            out.writeInt(5);
            out.writeByte(0x2a); // aload_0
            out.writeByte(0xb7); // invokespecial
            out.writeShort(16);
            out.writeByte(0xb1); // return
            out.writeShort(0); // exception_table_length
            out.writeShort(0); // attributes_count

            // static <clinit>()V { initialized = true; }
            out.writeShort(0x0008); // ACC_STATIC
            out.writeShort(11);
            out.writeShort(12);
            out.writeShort(1);
            out.writeShort(13); // Code
            out.writeInt(17);
            out.writeShort(1); // max_stack
            out.writeShort(0); // max_locals
            out.writeInt(5);
            out.writeByte(0x04); // iconst_1
            out.writeByte(0xb3); // putstatic
            out.writeShort(10);
            out.writeByte(0xb1); // return
            out.writeShort(0);
            out.writeShort(0);

            out.writeShort(0); // class attributes_count
            out.flush();
            return raw.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeUtf8(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeByte(1);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void writeClass(DataOutputStream out, int nameIndex) throws IOException {
        out.writeByte(7);
        out.writeShort(nameIndex);
    }

    private static void writeNameAndType(DataOutputStream out, int nameIndex, int descriptorIndex) throws IOException {
        out.writeByte(12);
        out.writeShort(nameIndex);
        out.writeShort(descriptorIndex);
    }

    private static void writeFieldref(DataOutputStream out, int classIndex, int nameAndTypeIndex) throws IOException {
        out.writeByte(9);
        out.writeShort(classIndex);
        out.writeShort(nameAndTypeIndex);
    }

    private static void writeMethodref(DataOutputStream out, int classIndex, int nameAndTypeIndex) throws IOException {
        out.writeByte(10);
        out.writeShort(classIndex);
        out.writeShort(nameAndTypeIndex);
    }
}
