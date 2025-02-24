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
 * A condition that must be satisfied to register elements for dynamic access (e.g., reflection,
 * serialization, JNI access, resource access, and foreign access at runtime). Conditions prevent
 * unnecessary growth of the native binary size.
 * <p>
 * There are currently two types of conditions:
 * <ul>
 * <li>{@link #typeReached} - satisfied when the type is both reachable by static analysis at build
 * time, and reached at run time.</li>
 * <li>{@link #alwaysTrue} - a condition that is always satisfied.</li>
 * </ul>
 * <p>
 * Conditions can be created via the {@link #alwaysTrue} and {@link #typeReached} factory methods.
 *
 * @since 25.0
 */
public interface AccessCondition {

    /**
     * Creates the condition that is always satisfied. Any metadata that is predicated with this
     * condition will always be included.
     *
     * @return instance of the condition
     *
     * @since 25.0
     */
    static AccessCondition alwaysTrue() {
        return TypeReachabilityCondition.JAVA_LANG_OBJECT_REACHED;
    }

    /**
     * Creates the {@code typeReached} condition that is satisfied when the type is reached at
     * runtime. A type is reached at runtime, right before the class-initialization routine starts
     * for that type, or any of the type's subtypes are reached. Metadata predicated with this
     * condition is only included if the condition is satisfied.
     * <p>
     * <strong>Example:</strong>
     * 
     * <pre>{@code
     * class SuperType {
     *     static {
     *         // ConditionType reached (subtype reached) => metadata is available
     *     }
     * }
     * 
     * class ConditionType extends SuperType {
     *     static {
     *         // ConditionType reached (before static initializer) => metadata is available
     *     }
     * 
     *     static ConditionType singleton() {
     *         // ConditionType reached (already initialized) => metadata is available
     *     }
     * }
     * 
     * public class App {
     *     public static void main(String[] args) {
     *         // ConditionType not reached => metadata is not available
     *         Class<?> clazz = ConditionType.class;
     *         // ConditionType not reached (ConditionType.class doesn't start class initialization)
     *         // => metadata is not available
     *         ConditionType.singleton();
     *         // ConditionType reached (already initialized) => metadata is available
     *     }
     * }
     * }</pre>
     * <p>
     * Type is also reached, if it is marked as {@code --initialize-at-build-time} or any of its
     * subtypes are marked as {@code --initialize-at-build-time} and they exist on the classpath.
     * Array types are never marked as reached and therefore cannot be used as the type in a
     * condition.
     *
     * @param type the type that has to be reached for this condition to be satisfied, must not be
     *            {@code null}
     *
     * @return instance of the condition
     *
     * @since 25.0
     */
    static AccessCondition typeReached(Class<?> type) {
        return TypeReachabilityCondition.create(type, true);
    }
}
