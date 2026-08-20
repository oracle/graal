/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.hosted;

/**
 * Marker interface to denote that a field value transformer
 * requires the analysis/hosted universe or internal operations that expand the universe or image heap,
 * such as creating new universe types, methods or fields, modifying existing ones,
 * marking constants for heap scanning, or adding new entries to an {@code ImageHeapList}
 * or {@code ImageHeapMap}.
 *
 * <p>
 * For example, {@code com.oracle.svm.core.reflect.target.ExecutableAccessorComputer}
 * may create an accessor for a method or a constructor, which requires the analysis.
 * </p>
 *
 * <p>
 * This marker interface is used by {@code PredictableBuildTimeInitializationFeature}
 * to differentiate between fields that can be read outside the universe and
 * those that cannot be read are processed only if the field is read and its receiver is reachable.
 * </p>
 *
 * <p>
 * This marker interface can be used on external code, but is mostly relevant for internal transformers.
 * It is placed in this package in order to be available for {@code jdk.graal.compiler.libgraal.LibGraalFeature}.
 * </p>
 */
public interface FieldValueTransformerRequiresUniverse {
}
