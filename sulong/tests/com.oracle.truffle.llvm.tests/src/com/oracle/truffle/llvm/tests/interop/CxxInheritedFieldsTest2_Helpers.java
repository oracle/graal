/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.tests.interop.values.StructObject;
import java.util.HashMap;

/**
 * Simulate an inheritance hierarchy of four classes, each having one additional field.
 */
public final class CxxInheritedFieldsTest2_Helpers {

    private CxxInheritedFieldsTest2_Helpers() {
    }

    private static HashMap<String, Object> createA0() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("a0", 0);
        return ret;
    }

    public static Object getA0() {
        return new StructObject(createA0());
    }

    private static HashMap<String, Object> createA1() {
        HashMap<String, Object> ret = createA0();
        ret.put("a1", 1);
        return ret;
    }

    public static Object getA1() {
        return new StructObject(createA1());
    }

    private static HashMap<String, Object> createA2() {
        HashMap<String, Object> ret = createA1();
        ret.put("a2", 2);
        return ret;
    }

    public static Object getA2() {
        return new StructObject(createA2());
    }

    private static HashMap<String, Object> createA3() {
        HashMap<String, Object> ret = createA2();
        ret.put("a3", 3);
        return ret;
    }

    public static Object getA3() {
        return new StructObject(createA3());
    }

    private static HashMap<String, Object> createA4() {
        HashMap<String, Object> ret = createA3();
        ret.put("a4", 4);
        return ret;
    }

    public static Object getA4() {
        return new StructObject(createA4());
    }

    private static HashMap<String, Object> createB0() {
        HashMap<String, Object> ret = new HashMap<>();
        ret.put("b0", 0);
        return ret;
    }

    public static Object getB0() {
        return new StructObject(createB0());
    }

    private static HashMap<String, Object> createB1() {
        HashMap<String, Object> ret = createB0();
        ret.put("b1", 1);
        return ret;
    }

    public static Object getB1() {
        return new StructObject(createB1());
    }

    private static HashMap<String, Object> createB2() {
        HashMap<String, Object> ret = createB1();
        ret.put("b2", 2);
        return ret;
    }

    public static Object getB2() {
        return new StructObject(createB2());
    }
}
