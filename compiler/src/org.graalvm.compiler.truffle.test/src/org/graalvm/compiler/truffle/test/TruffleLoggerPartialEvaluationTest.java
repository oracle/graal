/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import com.oracle.truffle.api.frame.FrameDescriptor;
import java.util.logging.Level;
import org.graalvm.compiler.truffle.test.nodes.IsLoggableNode;
import org.graalvm.compiler.truffle.test.nodes.LoggingNode;
import org.graalvm.compiler.truffle.test.nodes.RootTestNode;
import org.junit.Test;

public class TruffleLoggerPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void logBelowLevel() {
        final LoggingNode result = new LoggingNode(Level.FINE, "Logging", 42);
        final RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "log", result);
        assertPartialEvalEquals(TruffleLoggerPartialEvaluationTest::constant42, rootNode);
    }

    @Test
    public void isLoggableBelowLevel() {
        final IsLoggableNode result = new IsLoggableNode(Level.FINE, 42);
        final RootTestNode rootNode = new RootTestNode(new FrameDescriptor(), "isLoggable", result);
        assertPartialEvalEquals(TruffleLoggerPartialEvaluationTest::constant42, rootNode);
    }
}
