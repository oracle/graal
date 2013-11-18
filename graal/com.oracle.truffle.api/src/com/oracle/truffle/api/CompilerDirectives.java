/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

import com.oracle.truffle.api.frame.*;

import sun.misc.*;

/**
 * Directives that influence the optimizations of the Truffle compiler. All of the operations have
 * no effect when executed in the Truffle interpreter.
 */
public final class CompilerDirectives {

    public static final double LIKELY_PROBABILITY = 0.75;
    public static final double UNLIKELY_PROBABILITY = 1.0 - LIKELY_PROBABILITY;

    public static final double SLOWPATH_PROBABILITY = 0.0001;
    public static final double FASTPATH_PROBABILITY = 1.0 - SLOWPATH_PROBABILITY;

    private static final Unsafe UNSAFE = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter.
     */
    public static void transferToInterpreter() {
    }

    /**
     * Directive for the compiler to discontinue compilation at this code position and instead
     * insert a transfer to the interpreter, invalidating the currently executing machine code.
     */
    public static void transferToInterpreterAndInvalidate() {
    }

    /**
     * Returns a boolean value indicating whether the method is executed in the interpreter.
     * 
     * @return {@code true} when executed in the interpreter, {@code false} in compiled code.
     */
    public static boolean inInterpreter() {
        return true;
    }

    /**
     * Directive for the compiler that the given runnable should only be executed in the interpreter
     * and ignored in the compiled code.
     * 
     * @param runnable the closure that should only be executed in the interpreter
     */
    public static void interpreterOnly(Runnable runnable) {
        runnable.run();
    }

    /**
     * Directive for the compiler that the given callable should only be executed in the
     * interpreter.
     * 
     * @param callable the closure that should only be executed in the interpreter
     * @return the result of executing the closure in the interpreter and null in the compiled code
     * @throws Exception If the closure throws an exception when executed in the interpreter.
     */
    public static <T> T interpreterOnly(Callable<T> callable) throws Exception {
        return callable.call();
    }

    /**
     * Injects a probability for the given condition into the probability information of the
     * immediately succeeding branch instruction for the condition. The probability must be a value
     * between 0.0 and 1.0 (inclusive). The condition should not be a combined condition.
     * 
     * Example usage immediately before an if statement (it specifies that the likelihood for a to
     * be greater than b is 90%):
     * 
     * <code>
     * if (injectBranchProbability(0.9, a > b)) {
     *    // ...
     * }
     * </code>
     * 
     * Example usage for a combined condition (it specifies that the likelihood for a to be greater
     * than b is 90% and under the assumption that this is true, the likelihood for a being 0 is
     * 10%):
     * 
     * <code>
     * if (injectBranchProbability(0.9, a > b) && injectBranchProbability(0.1, a == 0)) {
     *    // ...
     * }
     * </code>
     * 
     * There are predefined constants for commonly used probabilities (see
     * {@link #LIKELY_PROBABILITY} , {@link #UNLIKELY_PROBABILITY}, {@link #SLOWPATH_PROBABILITY},
     * {@link #FASTPATH_PROBABILITY} ).
     * 
     * @param probability the probability value between 0.0 and 1.0 that should be injected
     */
    public static boolean injectBranchProbability(double probability, boolean condition) {
        assert probability >= 0.0 && probability <= 1.0;
        return condition;
    }

    /**
     * Bails out of a compilation (e.g., for guest language features that should never be compiled).
     * 
     * @param reason the reason for the bailout
     */
    public static void bailout(String reason) {
    }

    /**
     * Marks fields that should be considered final for a Truffle compilation although they are not
     * final while executing in the interpreter.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface CompilationFinal {
    }

    /**
     * Casts the given value to the value of the given type without any checks. The class must
     * evaluate to a constant. The condition parameter gives a hint to the compiler under which
     * circumstances this cast can be moved to an earlier location in the program.
     * 
     * @param value the value that is known to have the specified type
     * @param type the specified new type of the value
     * @param condition the condition that makes this cast safe also at an earlier location of the
     *            program
     * @return the value to be casted to the new type
     */
    @SuppressWarnings("unchecked")
    public static <T> T unsafeCast(Object value, Class<T> type, boolean condition) {
        return (T) value;
    }

    /**
     * Asserts that this value is not null and retrieved from a call to Frame.materialize.
     * 
     * @param value the value that is known to have been obtained via Frame.materialize
     * @return the value to be casted to the new type
     */
    public static MaterializedFrame unsafeFrameCast(MaterializedFrame value) {
        return unsafeCast(value, getUnsafeFrameType(), true);
    }

    private static Class<? extends MaterializedFrame> getUnsafeFrameType() {
        return MaterializedFrame.class;
    }

    /**
     * Unsafe access to a boolean value within an object. The condition parameter gives a hint to
     * the compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static boolean unsafeGetBoolean(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getBoolean(receiver, offset);
    }

    /**
     * Unsafe access to a byte value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static byte unsafeGetByte(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getByte(receiver, offset);
    }

    /**
     * Unsafe access to a short value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static short unsafeGetShort(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getShort(receiver, offset);
    }

    /**
     * Unsafe access to a int value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static int unsafeGetInt(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getInt(receiver, offset);
    }

    /**
     * Unsafe access to a long value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static long unsafeGetLong(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getLong(receiver, offset);
    }

    /**
     * Unsafe access to a float value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static float unsafeGetFloat(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getFloat(receiver, offset);
    }

    /**
     * Unsafe access to a double value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static double unsafeGetDouble(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getDouble(receiver, offset);
    }

    /**
     * Unsafe access to a Object value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     * 
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    public static Object unsafeGetObject(Object receiver, long offset, boolean condition, Object locationIdentity) {
        return UNSAFE.getObject(receiver, offset);
    }

    /**
     * Write a boolean value within an object. The location identity gives a hint to the compiler
     * for improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutBoolean(Object receiver, long offset, boolean value, Object locationIdentity) {
        UNSAFE.putBoolean(receiver, offset, value);
    }

    /**
     * Write a byte value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutByte(Object receiver, long offset, byte value, Object locationIdentity) {
        UNSAFE.putByte(receiver, offset, value);
    }

    /**
     * Write a short value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutShort(Object receiver, long offset, short value, Object locationIdentity) {
        UNSAFE.putShort(receiver, offset, value);
    }

    /**
     * Write a int value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutInt(Object receiver, long offset, int value, Object locationIdentity) {
        UNSAFE.putInt(receiver, offset, value);
    }

    /**
     * Write a long value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutLong(Object receiver, long offset, long value, Object locationIdentity) {
        UNSAFE.putLong(receiver, offset, value);
    }

    /**
     * Write a float value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutFloat(Object receiver, long offset, float value, Object locationIdentity) {
        UNSAFE.putFloat(receiver, offset, value);
    }

    /**
     * Write a double value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutDouble(Object receiver, long offset, double value, Object locationIdentity) {
        UNSAFE.putDouble(receiver, offset, value);
    }

    /**
     * Write a Object value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     * 
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    public static void unsafePutObject(Object receiver, long offset, Object value, Object locationIdentity) {
        UNSAFE.putObject(receiver, offset, value);
    }

    /**
     * Marks methods that are considered slowpath and should therefore not be inlined by default.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD})
    public @interface SlowPath {
    }

    /**
     * Marks classes as value types. Reference comparisons (==) between instances of those classes
     * have undefined semantics and can either return true or false.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface ValueType {
    }
}
