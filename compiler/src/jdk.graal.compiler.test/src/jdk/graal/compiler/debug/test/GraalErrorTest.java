/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.serviceprovider.GraalServices;

public class GraalErrorTest extends GraalCompilerTest {

    @FunctionalInterface
    interface Error {
        void call();
    }

    /**
     * Test that the errors actually fail and provide the correct message.
     */
    @Test
    public void testErrors() {
        error("unimplemented: test", () -> GraalError.unimplemented("test"));
        error("unimplemented override", () -> GraalError.unimplementedOverride());
        error("unimplemented method in parent class, should be overridden", () -> GraalError.unimplementedParent());
        error("should not reach here: test", () -> GraalError.shouldNotReachHere("test"));
        error("test", () -> GraalError.shouldNotReachHere(new Exception("test")));
        error("should not reach here: test", () -> GraalError.shouldNotReachHere(new Exception(), "test"));
        error("guarantee", () -> GraalError.guarantee(false, "guarantee", this));
        error("guarantee", () -> GraalError.guarantee(false, "guarantee", this, this));
        error("guarantee", () -> GraalError.guarantee(false, "guarantee", this, this, this));
        error("guarantee", () -> GraalError.guarantee(false, "guarantee", this, this, this, this));
        error("guarantee", () -> GraalError.guarantee(false, "guarantee", this, this, this, this, this));
    }

    @Test
    public void testConstructors() {
        Assert.assertTrue(new GraalError(new GraalError("test")).getMessage().contains("test"));

        GraalError ex = new GraalError(new Throwable("test"), "msg%s", "str1");
        Assert.assertTrue(ex.getMessage().contains("msgstr1"));
        Assert.assertTrue(ex.getCause().getMessage().contains("test"));
    }

    @Test
    public void testErrorContext() {
        GraalError inner = new GraalError("inner").addContext("innercontext");
        GraalError outer = new GraalError(inner).addContext("outercontext");
        Assert.assertTrue(outer.toString().contains("at innercontext"));
        Assert.assertTrue(outer.toString().contains("at outercontext"));
    }

    private static void error(String msg, Error error) {
        try {
            error.call();
            Assert.fail();
        } catch (Throwable ex) {
            Assert.assertTrue(ex.getMessage().contains(msg));
        }
    }

    public static final boolean LOG_TTY = Boolean.parseBoolean(GraalServices.getSavedProperty("test.GraalErrorTest.stdout"));

    static int foo(int limit) {
        int res = 0;
        for (int i = 0; i < limit; i++) {
            res += GraalDirectives.sideEffect(i);
        }
        return res;
    }

    // run with -Dtest.GraalErrorTest.stdout=true to inspect how error messages are formatted
    @Test
    public void testErrorExampels() {
        StructuredGraph g = parseEager("foo", StructuredGraph.AllowAssumptions.NO);

        FixedNode loopBeginNode = g.getNodes(LoopBeginNode.TYPE).first();

        try {
            assert false : Assertions.errorMessage("Message", loopBeginNode);
        } catch (Throwable t) {
            if (LOG_TTY) {
                TTY.printf("Assertions.errorMessage(..) -> %s%n%n", t.getMessage());
            }
        }

        try {
            assert false : Assertions.errorMessageContext("Message", loopBeginNode);
        } catch (Throwable t) {
            if (LOG_TTY) {
                TTY.printf("Assertions.errorMessageContext(..) -> %s%n%n", t.getMessage());
            }
        }

        try {
            GraalError.guarantee(false, "Message %s", loopBeginNode);
        } catch (Throwable t) {
            if (LOG_TTY) {
                TTY.printf("Guarantee -> %s%n%n", t.getMessage());
                t.printStackTrace();
            }
        }

    }

    @Test
    public void testNull() {
        try {
            throw new GraalError("I have a null arg %s %s", "abc", null);
        } catch (Throwable t) {
            if (LOG_TTY) {
                t.printStackTrace();
            }
        }
    }
}
