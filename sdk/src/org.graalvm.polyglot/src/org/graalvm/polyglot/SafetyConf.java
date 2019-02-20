/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Represents an interop safety configuration.
 */
public final class SafetyConf {
    /** The default configuration. It is used by newly created {@link Engine} or
     * {@link Context} unless {@link Engine.Builder#safety reconfigured}.
     * This configuration allows access only to classes, methods or fields
     * annotated by {@link IamSafe} annotation.
     */
    public static final SafetyConf DEFAULT = null;

    /** The open wide configuration. It allows access to all the
     * {@code public} methods and fields of {@code public} classes.
     */
    public static final SafetyConf PUBLIC = null;


    SafetyConf() {
    }

    /** Allows one to {@link Builder#create create} own {@link SafetyConf}.
     * @return builder for the configuration
     */
    public static Builder newConfig() {
        return null;
    }

    /** Builder to create new instance of {@link SafetyConf}.
     */
    public final class Builder {
        /** Elements annotated by the {@code annotation} will be accessible
         * in the created configuration.
         * @param annotation the annotation
         * @return this builder
         */
        public Builder safeToAccessIfAnnotatedBy(Class<? extends Annotation> annotation) {
            return this;
        }

        /** It is safe to access given field or method.
         * @param element {@link Method} or {@link Field} exposed in this configuration.
         * @return this builder
         */
        public Builder safeToAccess(AccessibleObject element) {
            return this;
        }

        /** Creates a configuration based on {@code this} builder setup.
         *
         * @return new instance of a configuration
         */
        public SafetyConf build() {
            return null;
        }
    }
}
