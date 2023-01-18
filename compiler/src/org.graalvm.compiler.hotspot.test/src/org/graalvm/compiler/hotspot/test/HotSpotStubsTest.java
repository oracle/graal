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

package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.meta.DefaultHotSpotLoweringProvider.RuntimeCalls;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.junit.Test;

/**
 * Tests that stubs are successfully compiled. Stubs are normally lazily compiled.
 */
public class HotSpotStubsTest extends GraalCompilerTest {

    @Test
    public void test() throws Throwable {

        Backend backend = getBackend();

        ForeignCallsProvider foreignCalls = getProviders().getForeignCalls();
        for (Map.Entry<BytecodeExceptionKind, ForeignCallSignature> e : RuntimeCalls.runtimeCalls.entrySet()) {
            ForeignCallSignature sig = e.getValue();
            foreignCalls.lookupForeignCall(sig);
        }

        for (Class<?> c = backend.getClass(); c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    if (ForeignCallDescriptor.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        ForeignCallDescriptor fd = (ForeignCallDescriptor) f.get(null);
                        try {
                            foreignCalls.lookupForeignCall(fd);
                        } catch (GraalError e) {
                            handleError(e);
                        }
                    } else if (ForeignCallSignature.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        ForeignCallSignature fs = (ForeignCallSignature) f.get(null);
                        try {
                            foreignCalls.lookupForeignCall(fs);
                        } catch (GraalError e) {
                            handleError(e);
                        }
                    }
                }
            }
        }
    }

    private static void handleError(GraalError e) {
        if (!e.getMessage().contains("Missing implementation for runtime call")) {
            throw e;
        }
    }
}
