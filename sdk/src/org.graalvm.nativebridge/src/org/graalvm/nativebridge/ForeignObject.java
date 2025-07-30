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
package org.graalvm.nativebridge;

import java.util.Objects;

/**
 * Represents a proxy object that delegates all public, non-final operations inherited from a base
 * class or implemented interfaces to an implementation in a foreign isolate or process. The
 * corresponding foreign object is represented by its {@linkplain #getPeer() peer}.
 *
 * @since 25.0
 */
public interface ForeignObject {

    /**
     * Returns the peer object that represents the foreign object in another isolate or process.
     *
     * @since 25.0
     */
    Peer getPeer();

    /**
     * Sets the peer object that represents the foreign object in another isolate or process.
     * <p>
     * The generated {@link ForeignObject} implementation supports this method only if the defining
     * class is annotated with {@link MutablePeer}. Without this annotation, invoking
     * {@code setPeer(Peer)} will result in an {@link UnsupportedOperationException}.
     *
     * @param newPeer the new peer object to associate with the foreign object
     * @throws UnsupportedOperationException if the defining class is not annotated with
     *             {@link MutablePeer}
     * @throws IllegalArgumentException if {@code newPeer} is not compatible with the generated
     *             {@link ForeignObject} implementation
     *
     * @see MutablePeer
     * @since 25.0
     */
    void setPeer(Peer newPeer);

    /**
     * Creates a {@link ForeignObject} instance for the given {@code peer} without associating it
     * with any service class or implementing any service interface.
     * <p>
     * This method is intended for use cases where a foreign object needs to be used as a generic
     * receiver in a custom dispatch class. A typical custom dispatch class is a final class
     * containing two final fields: {@code dispatch} and {@code receiver}. The {@code dispatch}
     * field implements an SPI interface whose methods correspond to those of the dispatch class,
     * but with the {@code receiver} passed explicitly as the first parameter. For more details, see
     * <a href=
     * "http://github.com/oracle/graal/blob/master/compiler/docs/NativeBridgeProcessor.md#bridging-a-class-with-a-custom-dispatch">.
     *
     * @see CustomDispatchAccessor
     * @see CustomReceiverAccessor
     * @see CustomDispatchFactory
     *
     */
    static ForeignObject createUnbound(Peer peer) {
        Objects.requireNonNull(peer, "Peer must be non-null.");
        return new ForeignObject() {
            @Override
            public Peer getPeer() {
                return peer;
            }

            @Override
            public void setPeer(Peer newPeer) {
                throw new UnsupportedOperationException("Default ForeignObject does not support mutable peer");
            }
        };
    }
}
