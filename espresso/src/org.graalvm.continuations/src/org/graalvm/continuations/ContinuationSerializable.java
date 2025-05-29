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
package org.graalvm.continuations;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * This class provides methods to cooperate with non-jdk serialization frameworks.
 *
 * <p>
 * This class handles serialization of all continuation-related classes. These classes are
 * {@link Continuation}, and {@link SuspendCapability}.
 *
 * <p>
 * All relevant classes will be subclasses of {@link ContinuationSerializable this class}, so
 * serialization specification is only required for a single class, even if the API should be
 * extended.
 */
public abstract class ContinuationSerializable implements Serializable {
    @Serial private static final long serialVersionUID = -9026047718562866108L;

    ContinuationSerializable() {
    }

    /**
     * Serialize the given continuation-related to the provided {@link ObjectOutput}.
     *
     * <p>
     * This method may be used to better cooperate with non-jdk serialization frameworks.
     */
    public static <T extends ContinuationSerializable> void writeObjectExternal(T object, ObjectOutput out) throws IOException {
        object.writeObjectExternal(out);
    }

    /**
     * Deserialize a continuation-related object from the provided {@link ObjectInput}.
     *
     * <p>
     * This method is provided to cooperate with non-jdk serialization frameworks.
     *
     * @param type The type of the object to deserialize. Can be either {@link Continuation} or
     *            {@link SuspendCapability}.
     * @param loader The class loader that will be used to deserialize the stack record. If
     *            {@code null}, the {@link Thread#getContextClassLoader() current thread context
     *            class loader} will be used.
     * @param registerFreshObject A callback to the serialization framework. Intended to be used to
     *            register the in-construction object, so the framework may handle object graph
     *            cycles.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ContinuationSerializable> T readObjectExternal(Class<T> type, ObjectInput in, ClassLoader loader, Consumer<T> registerFreshObject)
                    throws IOException, ClassNotFoundException {
        if (Continuation.class.isAssignableFrom(type)) {
            return (T) ContinuationImpl.readObjectExternal(in, loader, (Consumer<Continuation>) registerFreshObject);
        } else if (SuspendCapability.class.isAssignableFrom(type)) {
            return (T) SuspendCapability.readObjectExternal(in, (Consumer<SuspendCapability>) registerFreshObject);
        } else {
            throw new FormatVersionException("Could not deserialize a continuation object.");
        }
    }

    // region internals
    abstract void writeObjectExternal(ObjectOutput out) throws IOException;
}
