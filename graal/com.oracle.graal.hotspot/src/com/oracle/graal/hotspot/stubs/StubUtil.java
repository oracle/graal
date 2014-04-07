/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.nodes.CStringNode.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.word.Word.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;

//JaCoCo Exclude

/**
 * A collection of methods used in {@link Stub}s.
 */
public class StubUtil {

    public static final ForeignCallDescriptor VM_MESSAGE_C = descriptorFor(StubUtil.class, "vmMessageC");

    /**
     * Looks for a {@link StubForeignCallNode} node intrinsic named {@code name} in
     * {@code stubClass} and returns a {@link ForeignCallDescriptor} based on its signature and the
     * value of {@code hasSideEffect}.
     */
    public static ForeignCallDescriptor descriptorFor(Class<?> stubClass, String name) {
        Method found = null;
        for (Method method : stubClass.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getAnnotation(NodeIntrinsic.class) != null && method.getName().equals(name)) {
                if (method.getAnnotation(NodeIntrinsic.class).value() == StubForeignCallNode.class) {
                    assert found == null : "found more than one foreign call named " + name + " in " + stubClass;
                    assert method.getParameterTypes().length != 0 && method.getParameterTypes()[0] == ForeignCallDescriptor.class : "first parameter of foreign call '" + name + "' in " + stubClass +
                                    " must be of type " + ForeignCallDescriptor.class.getSimpleName();
                    found = method;
                }
            }
        }
        assert found != null : "could not find foreign call named " + name + " in " + stubClass;
        List<Class<?>> paramList = Arrays.asList(found.getParameterTypes());
        Class[] cCallTypes = paramList.subList(1, paramList.size()).toArray(new Class[paramList.size() - 1]);
        return new ForeignCallDescriptor(name, found.getReturnType(), cCallTypes);
    }

    public static void handlePendingException(Word thread, boolean isObjectResult) {
        if (clearPendingException(thread)) {
            if (isObjectResult) {
                getAndClearObjectResult(thread);
            }
            DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
        }
    }

    /**
     * Determines if this is a HotSpot build where the ASSERT mechanism is enabled.
     */
    @Fold
    public static boolean cAssertionsEnabled() {
        return runtime().getConfig().cAssertions;
    }

    @NodeIntrinsic(StubForeignCallNode.class)
    private static native void vmMessageC(@ConstantNodeParameter ForeignCallDescriptor stubPrintfC, boolean vmError, Word format, long v1, long v2, long v3);

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param message a message string
     */
    public static void printf(String message) {
        vmMessageC(VM_MESSAGE_C, false, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void printf(String format, long value) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), value, 0L, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long)} to avoid an object
     * constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, 0L);
    }

    /**
     * Prints a message to the log stream.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void printf(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, false, cstring(format), v1, v2, v3);
    }

    /**
     * Analyzes a given value and prints information about it to the log stream.
     */
    public static void decipher(long value) {
        vmMessageC(VM_MESSAGE_C, false, Word.zero(), value, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link VMErrorNode#vmError(String, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param message an error message
     */
    public static void fatal(String message) {
        vmMessageC(VM_MESSAGE_C, true, cstring(message), 0L, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param value the value associated with the first conversion specifier in {@code format}
     */
    public static void fatal(String format, long value) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), value, 0L, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, 0L);
    }

    /**
     * Exits the VM with a given error message.
     * <p>
     * <b>Stubs must use this instead of {@link Log#printf(String, long, long, long)} to avoid an
     * object constant in a RuntimeStub.</b>
     *
     * @param format a C style printf format value
     * @param v1 the value associated with the first conversion specifier in {@code format}
     * @param v2 the value associated with the second conversion specifier in {@code format}
     * @param v3 the value associated with the third conversion specifier in {@code format}
     */
    public static void fatal(String format, long v1, long v2, long v3) {
        vmMessageC(VM_MESSAGE_C, true, cstring(format), v1, v2, v3);
    }

    /**
     * Verifies that a given object value is well formed if {@code -XX:+VerifyOops} is enabled.
     */
    public static Object verifyObject(Object object) {
        if (verifyOops()) {
            Word verifyOopCounter = Word.unsigned(verifyOopCounterAddress());
            verifyOopCounter.writeInt(0, verifyOopCounter.readInt(0) + 1);

            Pointer oop = Word.fromObject(object);
            if (object != null) {
                GuardingNode anchorNode = SnippetAnchorNode.anchor();
                // make sure object is 'reasonable'
                if (!oop.and(unsigned(verifyOopMask())).equal(unsigned(verifyOopBits()))) {
                    fatal("oop not in heap: %p", oop.rawValue());
                }

                Word klass = loadHubIntrinsic(object, getWordKind(), anchorNode);
                if (klass.equal(Word.zero())) {
                    fatal("klass for oop %p is null", oop.rawValue());
                }
            }
        }
        return object;
    }

    @Fold
    private static long verifyOopCounterAddress() {
        return config().verifyOopCounterAddress;
    }

    @Fold
    private static long verifyOopMask() {
        return config().verifyOopMask;
    }

    @Fold
    private static long verifyOopBits() {
        return config().verifyOopBits;
    }

    @Fold
    private static int hubOffset() {
        return config().hubOffset;
    }
}
