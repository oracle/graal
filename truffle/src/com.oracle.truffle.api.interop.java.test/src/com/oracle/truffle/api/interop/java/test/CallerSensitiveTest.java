/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop.java.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

import org.junit.Test;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;

@SuppressWarnings("deprecation")
public class CallerSensitiveTest {
    @Test
    public void testLogger() throws InteropException {
        TruffleObject loggerClass = com.oracle.truffle.api.interop.java.JavaInterop.asTruffleObject(Logger.class);
        String loggerName = "test-logger-name";
        TruffleObject logger;

        TruffleObject getLogger = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), loggerClass, "getLogger");
        logger = (TruffleObject) ForeignAccess.sendExecute(Message.createExecute(1).createNode(), getLogger, loggerName);
        assertTrue(com.oracle.truffle.api.interop.java.JavaInterop.isJavaObject(Logger.class, logger));
        assertEquals(loggerName, com.oracle.truffle.api.interop.java.JavaInterop.asJavaObject(Logger.class, logger).getName());

        logger = (TruffleObject) ForeignAccess.sendInvoke(Message.createInvoke(1).createNode(), loggerClass, "getLogger", loggerName);
        assertTrue(com.oracle.truffle.api.interop.java.JavaInterop.isJavaObject(Logger.class, logger));
        assertEquals(loggerName, com.oracle.truffle.api.interop.java.JavaInterop.asJavaObject(Logger.class, logger).getName());
    }
}
