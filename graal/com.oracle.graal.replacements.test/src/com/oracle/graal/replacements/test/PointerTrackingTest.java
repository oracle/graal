/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.replacements.test;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import com.oracle.graal.api.directives.GraalDirectives;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugConfigScope;
import com.oracle.graal.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Registration;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.replacements.Snippets;
import com.oracle.graal.word.Word;

public class PointerTrackingTest extends GraalCompilerTest implements Snippets {

    @Test
    public void testTracking() {
        Result result = executeActual(getResolvedJavaMethod("trackingSnippet"), null, new Object());
        assertEquals(new Result("OK", null), result);
    }

    public static String trackingSnippet(Object obj) {
        long trackedBeforeGC = getTrackedPointer(obj);
        long untrackedBeforeGC = getUntrackedPointer(obj);

        int i = 0;
        while (untrackedBeforeGC == getTrackedPointer(obj)) {
            System.gc();
            if (i++ > 100) {
                return "Timeout! Object didn't move after 100 GCs.";
            }
        }

        long trackedAfterGC = getTrackedPointer(obj);
        long untrackedAfterGC = getUntrackedPointer(obj);

        if (untrackedBeforeGC == untrackedAfterGC) {
            /*
             * The untracked pointer isn't supposed to be updated, so it should be different before
             * and after GC.
             */
            return "untrackedBeforeGC == untrackedAfterGC";
        }
        if (trackedBeforeGC != trackedAfterGC) {
            /*
             * The trackedBeforeGC variable should be updated to the new location by the GC, so it
             * should be equal to trackedAfterGC.
             */
            return "trackedBeforeGC != trackedAfterGC";
        }

        return "OK";
    }

    @Test(expected = JVMCIError.class)
    @SuppressWarnings("try")
    public void testVerification() {
        try (DebugConfigScope scope = Debug.disableIntercept()) {
            compile(getResolvedJavaMethod("verificationSnippet"), null);
        }
    }

    public static long verificationSnippet(Object obj) {
        long value = getTrackedPointer(obj) * 7 + 3;

        /*
         * Ensure usage before and after GC to force the value to be alive across the safepoint.
         * This should lead to a compiler error, since value can not be tracked in the reference
         * map.
         */
        GraalDirectives.blackhole(value);
        System.gc();
        return value;
    }

    static long getTrackedPointer(@SuppressWarnings("unused") Object obj) {
        throw JVMCIError.shouldNotReachHere("should be intrinsified");
    }

    static long getUntrackedPointer(@SuppressWarnings("unused") Object obj) {
        throw JVMCIError.shouldNotReachHere("should be intrinsified");
    }

    static long getTrackedPointerIntrinsic(Object obj) {
        return Word.objectToTrackedPointer(obj).rawValue();
    }

    static long getUntrackedPointerIntrinsic(Object obj) {
        return Word.objectToUntrackedPointer(obj).rawValue();
    }

    private void register(Registration r, String fnName) {
        ResolvedJavaMethod intrinsic = getResolvedJavaMethod(fnName + "Intrinsic");
        r.register1(fnName, Object.class, new InvocationPlugin() {
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.intrinsify(targetMethod, intrinsic, new ValueNode[]{arg});
                return true;
            }
        });
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(plugins.getInvocationPlugins(), PointerTrackingTest.class);

        register(r, "getTrackedPointer");
        register(r, "getUntrackedPointer");

        return plugins;
    }
}
