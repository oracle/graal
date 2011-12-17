/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package test.com.sun.max.util;

import com.sun.max.ide.*;
import com.sun.max.util.*;

/**
 * Tests for com.sun.max.util.Enumerator.
 */
public class EnumeratorTest extends MaxTestCase {

    public EnumeratorTest(String name) {
        super(name);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(EnumeratorTest.class);
    }

    private static final class NonSuccessiveEnumerator<E extends Enum<E> & Enumerable<E>> extends Enumerator<E> {
        private NonSuccessiveEnumerator(Class<E> type) {
            super(type);
        }
    }
    private static enum NonSuccessiveEnum implements Enumerable<NonSuccessiveEnum> {
        E0(0), E100(100), E1000(1000);

        private final int value;
        private NonSuccessiveEnum(int value) {
            this.value = value;
        }
        public int value() {
            return value;
        }
        public Enumerator<NonSuccessiveEnum> enumerator() {
            return new NonSuccessiveEnumerator<NonSuccessiveEnum>(NonSuccessiveEnum.class);
        }
    }

    public void test_value() {
        assertTrue(NonSuccessiveEnum.E0.ordinal() == 0);
        assertTrue(NonSuccessiveEnum.E100.ordinal() == 1);
        assertTrue(NonSuccessiveEnum.E1000.ordinal() == 2);
        assertTrue(NonSuccessiveEnum.E0.value() == 0);
        assertTrue(NonSuccessiveEnum.E100.value() == 100);
        assertTrue(NonSuccessiveEnum.E1000.value() == 1000);
    }

    public void test_enumerator() {
        final Enumerator<NonSuccessiveEnum> enumerator = NonSuccessiveEnum.E0.enumerator();
        assertTrue(enumerator.type() == NonSuccessiveEnum.class);
        int sum = 0;
        for (NonSuccessiveEnum e : enumerator) {
            sum += e.value();
        }
        assertTrue(sum == 1100);
        assertTrue(enumerator.fromValue(0) == NonSuccessiveEnum.E0);
        assertTrue(enumerator.fromValue(100) == NonSuccessiveEnum.E100);
        assertTrue(enumerator.fromValue(1000) == NonSuccessiveEnum.E1000);
        assertTrue(enumerator.fromValue(1) == null);
    }
}
