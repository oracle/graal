/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;

public class InsightExceptionTest {
    private static Method raiseMethod;

    @BeforeClass
    public static void initRaiseMethod() throws Exception {
        Class<?> insightException = Class.forName("com.oracle.truffle.tools.agentscript.impl.InsightException");
        raiseMethod = insightException.getDeclaredMethod("raise", Exception.class);
        raiseMethod.setAccessible(true);
    }

    @Test
    public void raiseNPE() throws Exception {
        try {
            raiseMethod.invoke(null, new NullPointerException());
            fail("Should raise an exception");
        } catch (InvocationTargetException invEx) {
            Throwable ex = invEx.getTargetException();
            assertNotNull(ex);
            final String msg = ex.getMessage();
            assertNotNull(msg);
            assertTrue(msg, msg.startsWith("insight: Unexpected NullPointerException"));
        }
    }

    @Test
    public void raisePatternException() throws Exception {
        try {
            try {
                Pattern.compile("(unclosed");
            } catch (PatternSyntaxException ex) {
                raiseMethod.invoke(null, ex);
            }
            fail("Should raise an exception");
        } catch (InvocationTargetException invEx) {
            Throwable ex = invEx.getTargetException();
            assertNotNull(ex);
            final String msg = ex.getMessage();
            assertNotNull(msg);
            assertTrue(msg, msg.startsWith("insight: Unclosed group near index 9: (unclosed"));
        }
    }
}
