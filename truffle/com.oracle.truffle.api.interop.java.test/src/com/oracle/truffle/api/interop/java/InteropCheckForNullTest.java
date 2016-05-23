/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java;

import com.oracle.truffle.api.interop.TruffleObject;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SeparateClassloaderTestRunner.class)
public class InteropCheckForNullTest {
    @Test
    public void showHowToCheckForNull() {
        TruffleObject nullValue = JavaObject.NULL;

        IsNullChecker check = JavaInterop.asJavaFunction(IsNullChecker.class, nullValue);
        assertTrue("Yes, it is null", check.isNull());

        TruffleObject nonNullValue = JavaInterop.asTruffleObject(this);

        IsNullChecker check2 = JavaInterop.asJavaFunction(IsNullChecker.class, nonNullValue);
        assertFalse("No, it is not null", check2.isNull());
    }

    public interface IsNullChecker {
        @MethodMessage(message = "IS_NULL")
        boolean isNull();
    }
}
