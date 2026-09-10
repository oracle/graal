/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.config;

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.junit.Assert;
import org.junit.Test;

import sun.misc.Unsafe;

/** Regression fixture for native-image-agent field access tracing. */
public class FieldAccessAgentTest {
    private static final String GENERATOR_ENABLED_PROPERTY = FieldAccessAgentTest.class.getName() + ".generator.enabled";
    static final String QUERIED_FIELD_NAME = "queriedField";
    static final String ACCESSED_FIELD_NAME = "accessedField";
    static final String OVERRIDDEN_FIELD_NAME = "overriddenField";
    static final String REFERENCE_FIELD_NAME = "referenceField";
    static final String INTEGER_FIELD_NAME = "integerField";
    static final String LONG_FIELD_NAME = "longField";
    static final String UNSAFE_FIELD_NAME = "unsafeField";
    static final int REFLECTIVE_ACCESS_COUNT = 3;

    @Test
    public void accessFields() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is designed to run under the agent",
                        Boolean.getBoolean(GENERATOR_ENABLED_PROPERTY));

        Assert.assertNotNull(FieldHolder.class.getDeclaredField(QUERIED_FIELD_NAME));

        FieldHolder holder = new FieldHolder();
        Object value = new Object();
        holder.accessedField = value;
        Field accessedField = FieldHolder.class.getDeclaredField(ACCESSED_FIELD_NAME);
        for (int i = 0; i < REFLECTIVE_ACCESS_COUNT; i++) {
            Assert.assertSame(value, accessedField.get(holder));
        }

        Field overriddenField = FieldHolder.class.getDeclaredField(OVERRIDDEN_FIELD_NAME);
        overriddenField.setAccessible(true);
        for (int i = 0; i < REFLECTIVE_ACCESS_COUNT; i++) {
            overriddenField.set(holder, value);
        }
        Assert.assertSame(value, overriddenField.get(holder));

        AtomicReferenceFieldUpdater<FieldHolder, Object> referenceUpdater = AtomicReferenceFieldUpdater.newUpdater(FieldHolder.class, Object.class, REFERENCE_FIELD_NAME);
        Assert.assertTrue(referenceUpdater.compareAndSet(holder, null, value));

        AtomicIntegerFieldUpdater<FieldHolder> integerUpdater = AtomicIntegerFieldUpdater.newUpdater(FieldHolder.class, INTEGER_FIELD_NAME);
        Assert.assertTrue(integerUpdater.compareAndSet(holder, 0, 1));

        AtomicLongFieldUpdater<FieldHolder> longUpdater = AtomicLongFieldUpdater.newUpdater(FieldHolder.class, LONG_FIELD_NAME);
        Assert.assertTrue(longUpdater.compareAndSet(holder, 0, 1));

        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);
        Assert.assertTrue(unsafe.objectFieldOffset(FieldHolder.class.getDeclaredField(UNSAFE_FIELD_NAME)) >= 0);
    }

    static final class FieldHolder {
        volatile Object queriedField;
        volatile Object accessedField;
        private volatile Object overriddenField;
        volatile Object referenceField;
        volatile int integerField;
        volatile long longField;
        volatile Object unsafeField;
    }
}
