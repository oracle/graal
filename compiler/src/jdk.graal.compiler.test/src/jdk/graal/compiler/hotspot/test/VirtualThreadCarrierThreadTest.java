/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jdk.graal.compiler.api.test.ModuleSupport;
import org.junit.Test;

public class VirtualThreadCarrierThreadTest extends HotSpotGraalCompilerTest {

    public static void testSnippet() {
        try {
            Thread.ofVirtual().start(() -> {
                try {
                    Method m = Thread.class.getDeclaredMethod("currentCarrierThread");
                    m.setAccessible(true);
                    Thread t = (Thread) m.invoke(null);
                    assumeTrue(t != Thread.currentThread());
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }
            }).join();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testGetCarrierThread() {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");
        compileAndInstallSubstitution(Thread.class, "currentCarrierThread");
        testSnippet();
    }

}
