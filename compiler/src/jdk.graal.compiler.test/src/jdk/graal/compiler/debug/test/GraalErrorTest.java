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

import jdk.graal.compiler.debug.GraalError;
import org.junit.Assert;
import org.junit.Test;

public class GraalErrorTest {

    @FunctionalInterface
    interface Error {
        void call();
    }

    @Test
    /**
     * Test that the errors actually fail and provide the correct message.
     */
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
}
