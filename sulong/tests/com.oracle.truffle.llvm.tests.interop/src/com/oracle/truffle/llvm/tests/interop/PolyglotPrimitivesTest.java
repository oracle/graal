/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tests.interop;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.llvm.tests.interop.values.StructObject;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class PolyglotPrimitivesTest extends InteropTestBase {
    @FunctionalInterface
    interface AsObject {
        Object as(Value v);
    }

    @Parameter public String typeName;
    @Parameter(1) public Object expectedValue;
    @Parameter(2) public Object expectedValueJava;
    @Parameter(3) public AsObject conversion;
    @Parameter(4) public StructObject myClass;

    private String keyExport = typeName + "_export";
    private String keyImport = typeName + "_import";
    private String keyExportImport = typeName + "_export_import";

    public static class Library {
        Value lib = loadTestBitcodeValue("polyglotPrimitives.c");

        public void exportValue(String type, String key) {
            lib.getMember("export_" + type).execute(key);
        }

        public Value importValue(String type, String key) {
            return lib.getMember("import_" + type).execute(key);
        }

        public void putMember(String type, Object v) {
            lib.getMember("put_member_" + type).execute(v);
        }

        public Value getMember(String type, Object v) {
            return lib.getMember("get_member_" + type).execute(v);
        }

        public Value intOrLong(boolean b) {
            return lib.getMember("int_or_long").execute(b);
        }
    }

    public static StructObject makeStructObject(Object value) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("member", value);
        return new StructObject(properties);
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> types() {
        return Arrays.asList(new Object[][]{
                        {"boolean", true, true, (AsObject) Value::asBoolean, makeStructObject(true)},
                        {"i8", (byte) 42, (byte) 48, (AsObject) Value::asByte, makeStructObject((byte) 48)},
                        {"i16", (short) 43, (short) 49, (AsObject) Value::asShort, makeStructObject((short) 49)},
                        {"i32", 44, 50, (AsObject) Value::asInt, makeStructObject(50)},
                        {"i64", 45L, 51L, (AsObject) Value::asLong, makeStructObject(51L)},
                        {"float", 46.0f, 52.0f, (AsObject) Value::asFloat, makeStructObject(52.0f)},
                        {"double", 47.0, 53.0, (AsObject) Value::asDouble, makeStructObject(53.0)}
        });
    }

    static Library lib;
    static Value polyglotBindings;

    @BeforeClass
    public static void loadTestBitcode() {
        lib = new Library();
        polyglotBindings = Context.getCurrent().getPolyglotBindings();
    }

    @Test
    public void exportTest() {
        lib.exportValue(typeName, keyExport);
        Value v = polyglotBindings.getMember(keyExport);
        assertEquals(expectedValue, conversion.as(v));
    }

    @Test
    public void importTest() {
        polyglotBindings.putMember(keyImport, expectedValueJava);
        Value v = lib.importValue(typeName, keyImport);
        assertEquals(expectedValueJava, conversion.as(v));
    }

    @Test
    public void importExportTest() {
        lib.exportValue(typeName, keyExportImport);
        Value v = lib.importValue(typeName, keyExportImport);
        assertEquals(expectedValue, conversion.as(v));
    }

    @Test
    public void putMemberTest() {
        lib.putMember(typeName, myClass);
        assertEquals(expectedValue, myClass.get("member"));
    }

    @Test
    public void getMemberTest() {
        assertEquals(expectedValueJava, conversion.as(lib.getMember(typeName, myClass)));
    }
}
