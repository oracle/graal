/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;

/**
 * A transformer for a field value that can be registered using
 * {@link BeforeAnalysisAccess#registerFieldValueTransformer}.
 * <p>
 * At image build time, the field value transformer provides the value of the field for the image
 * heap. Without a transformer, the value of the field in the image heap is the same as the hosted
 * value in the image generator. A field value transformer allows to intercept the value. A field
 * value transformer can, for example, reset a field to the default null/0 value, replace a filled
 * collection with a new empty collection, or provide any kind of new value for a field.
 * <p>
 * Only one field value transformer can be registered for each field.
 * <p>
 * A field value transformer can be registered for fields of classes that are initialized at image
 * run time. That allows constant folding of final fields even though the declaring class is not
 * initialized at image build time.
 * <p>
 * A field value transformer must be registered before the field is seen as reachable by the static
 * analysis. It is generally safe to register a transformer in {@link Feature#beforeAnalysis} before
 * the static analysis is started, in a {@link BeforeAnalysisAccess#registerReachabilityHandler type
 * reachability handler} for the declaring class of the field, or a
 * {@link BeforeAnalysisAccess#registerSubtypeReachabilityHandler subtype reachability handler} for
 * a super-type of the declaring class of the field.
 *
 * @since 22.3
 */
@Platforms(Platform.HOSTED_ONLY.class)
public interface FieldValueTransformer {

    /**
     * Transforms the field value for the provided receiver. The receiver is null for static fields.
     * The original value of the field, i.e., the hosted value of the field in the image generator,
     * is also provided as an argument.
     * <p>
     * The type of the returned object must be assignable to the declared type of the field. If the
     * field has a primitive type, the return value must be a boxed value, and must not be null.
     * 
     * @since 22.3
     */
    Object transform(Object receiver, Object originalValue);
}
