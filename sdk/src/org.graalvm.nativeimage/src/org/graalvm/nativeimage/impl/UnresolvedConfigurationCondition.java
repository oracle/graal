/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl;

import java.util.Objects;

/**
 * This is an unresolved version of the {@link ConfigurationCondition} used only during parsing.
 */
public class UnresolvedConfigurationCondition implements Comparable<UnresolvedConfigurationCondition> {
    private final String typeName;
    private static final UnresolvedConfigurationCondition JAVA_LANG_OBJECT_REACHED = new UnresolvedConfigurationCondition(Object.class.getTypeName());

    public static UnresolvedConfigurationCondition create(String typeName) {
        Objects.requireNonNull(typeName);
        if (JAVA_LANG_OBJECT_REACHED.getTypeName().equals(typeName)) {
            return JAVA_LANG_OBJECT_REACHED;
        }
        return new UnresolvedConfigurationCondition(typeName);
    }

    protected UnresolvedConfigurationCondition(String typeName) {
        this.typeName = typeName;
    }

    public static UnresolvedConfigurationCondition alwaysTrue() {
        return JAVA_LANG_OBJECT_REACHED;
    }

    public String getTypeName() {
        return typeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnresolvedConfigurationCondition condition = (UnresolvedConfigurationCondition) o;
        return Objects.equals(typeName, condition.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeName);
    }

    @Override
    public String toString() {
        return "[\"typeReachable\": \"" + typeName + "\"" + "]";
    }

    @Override
    public int compareTo(UnresolvedConfigurationCondition c) {
        return this.typeName.compareTo(c.typeName);
    }

    public boolean isAlwaysTrue() {
        return typeName.equals(JAVA_LANG_OBJECT_REACHED.getTypeName());
    }
}
