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
package org.graalvm.compiler.hotspot.jdk20.test;

import static org.junit.Assume.assumeTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.api.test.ModuleSupport;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.test.HotSpotGraalCompilerTest;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.junit.Before;
import org.junit.Test;

import jdk.internal.misc.PreviewFeatures;

@AddExports("java.base/jdk.internal.misc")
public class LoomSupportsTest extends HotSpotGraalCompilerTest {

    @Before
    public void checkPreview() {
        assumeTrue("Preview Features not enabled, need to run with --enable-preview", PreviewFeatures.isEnabled());
    }

    @Test
    public void testGetCarrierThread() throws ClassNotFoundException, InterruptedException {
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");
        compileAndInstallSubstitution(Thread.class, "currentCarrierThread");
        // testing inline-only Thread.setCurrentThread
        getCode(getResolvedJavaMethod(Class.forName("java.lang.VirtualThread"), "mount"), null, true, true, getInitialOptions());

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
    }

    @Test
    public void testScopedValue() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assumeTrue(((HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class)).getVMConfig().threadScopedValueCacheOffset != -1);

        Method get = Thread.class.getDeclaredMethod("scopedValueCache");
        get.setAccessible(true);
        Object[] originalCache = (Object[]) get.invoke(null);

        // set extent cache with compiled Thread.setScopedValueCache
        compileAndInstallSubstitution(Thread.class, "setScopedValueCache");

        Object[] cache = new Object[1];
        Method set = Thread.class.getDeclaredMethod("setScopedValueCache", Object[].class);
        set.setAccessible(true);
        set.invoke(null, cache);

        // validate extent cache with interpreted Thread.scopedValueCache
        assumeTrue(cache == get.invoke(null));

        // validate extent cache with compiled Thread.scopedValueCache
        compileAndInstallSubstitution(Thread.class, "scopedValueCache");
        assumeTrue(cache == get.invoke(null));

        set.invoke(null, originalCache);
    }
}
