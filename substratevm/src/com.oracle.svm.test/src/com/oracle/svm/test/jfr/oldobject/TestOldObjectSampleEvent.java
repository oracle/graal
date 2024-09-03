/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr.oldobject;

import org.junit.Test;

public class TestOldObjectSampleEvent extends JfrOldObjectTest {

    @Test
    public void testObjectLeak() throws Throwable {
        int arrayLength = Integer.MIN_VALUE;
        testSampling(new TinyObject(43), arrayLength, events -> validateEvents(events, TinyObject.class, arrayLength));
    }

    @Test
    public void testSmallArrayLeak() throws Throwable {
        int arrayLength = 3;
        testSampling(new TinyObject[arrayLength], arrayLength, events -> validateEvents(events, TinyObject.class.arrayType(), arrayLength));
    }

    @Test
    public void testLargeArrayLeak() throws Throwable {
        int arrayLength = 1024 * 1024;
        testSampling(new TinyObject[arrayLength], arrayLength, events -> validateEvents(events, TinyObject.class.arrayType(), arrayLength));
    }
}
