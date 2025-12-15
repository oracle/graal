/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.dynamicaccess;

import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

/**
 * A condition that must be satisfied to register elements for dynamic access (i.e., reflection,
 * serialization, JNI access, resource access, and foreign access at run time).
 * {@link AccessCondition} is used for programmatic metadata registration in conjunction with:
 * <ul>
 * <li>{@link ReflectiveAccess}</li>
 * <li>{@link ResourceAccess}</li>
 * <li>{@link JNIAccess}</li>
 * <li>{@link ForeignAccess}</li>
 * </ul>
 * Conditions should be used whenever possible to constrain unnecessary growth of the binary size.
 * <p>
 * There are currently two types of conditions:
 * <ul>
 * <li>{@link #typeReached} - satisfied when the type is both reachable by static analysis at build
 * time, and reached at run time.</li>
 * <li>{@link #unconditional} - a condition that is always satisfied. This condition should be
 * avoided to prevent unnecessary increases in binary size.</li>
 * </ul>
 * <p>
 * Conditions can only be created via the {@link #unconditional} and {@link #typeReached} factory
 * methods. These methods are best used with static import methods. For example:
 *
 * <pre>{@code
 * reflection.register(unconditional(), ReflectivelyAccessed.class);
 * reflection.register(typeReached(ConditionalType.class), ConditionallyAccessed.class)
 * }</pre>
 *
 * @since 25.0.1
 */
public interface AccessCondition {

    /**
     * Returns a condition that is always satisfied. Any element that is predicated with this
     * condition will always be included and accessible.
     *
     * @return instance of the condition
     *
     * @since 25.0.1
     */
    static AccessCondition unconditional() {
        return TypeReachabilityCondition.JAVA_LANG_OBJECT_REACHED;
    }

    /**
     * Creates the {@code typeReached} condition that is satisfied when the type is reached at run
     * time. A type is reached at run time, if the class-initialization is triggered for that type
     * (right before the first step of initialization described in
     * <a href="https://docs.oracle.com/javase/specs/jvms/se14/html/jvms-5.html#jvms-5.5">Java Spec
     * - Initialization</a>), or any of the type's subtypes are reached. Elements predicated with
     * this condition will be included in the image only if the type is <em>reachable</em> at build
     * time, but will be accessible when the type is <em>reached</em> at run time.
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * public class App {
     *     public static void main(String[] args) {
     *         // ConditionalType not reached => element access not allowed
     *         Class<?> clazz = ConditionalType.class;
     *         // ConditionalType not reached (ConditionalType.class doesn't start class initialization)
     *         // => element access not allowed
     *         ConditionalType.singleton();
     *         // ConditionalType reached (already initialized) => element access allowed
     *     }
     * }
     *
     * class SuperType {
     *     static {
     *         // ConditionalType reached (subtype reached) => element access allowed
     *     }
     * }
     *
     * class ConditionalType extends SuperType {
     *     static {
     *         // ConditionalType reached (before static initializer) => element access allowed
     *     }
     *
     *     static ConditionalType singleton() {
     *         // ConditionalType reached (already initialized) => element access allowed
     *     }
     * }
     * }</pre>
     * <p>
     * A type is also considered as reached, if it is marked as {@code --initialize-at-build-time}
     * or if any of its subtypes on the classpath are marked as {@code --initialize-at-build-time}.
     * Array types (e.g., <code>int[]</code>) are never marked as reached and therefore cannot be
     * used as the <code>type</code> in a condition.
     *
     * @param type the type that has to be reached for this condition to be satisfied, must not be
     *            {@code null}
     *
     * @return instance of the condition
     *
     * @since 25.0.1
     */
    static AccessCondition typeReached(Class<?> type) {
        return TypeReachabilityCondition.create(type, true);
    }
}
