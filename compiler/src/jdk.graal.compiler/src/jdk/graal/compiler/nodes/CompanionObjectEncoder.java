/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import org.graalvm.collections.EconomicMap;
import jdk.graal.compiler.graph.Node;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encodes an object bound to a {@link StructuredGraph} (a companion object).
 *
 * A companion object is a non-trivial field of the {@link StructuredGraph}. It may point to graph
 * nodes, which require special handling during encoding/decoding. An example of a companion object
 * is the inlining log or the optimization log.
 *
 * The expected order of operations to encode a graph is the following:
 * <ol>
 * <li>each companion object's {@link #prepare} is called,</li>
 * <li>the {@link StructuredGraph} is encoded,</li>
 * <li>each companion object's {@link #encode} is called,</li>
 * <li>optionally, each companion object's encoding is {@link #verify verified}.</li>
 * </ol>
 *
 * In the {@link #prepare} phase, an instance of the encoded representation is created and added to
 * the list of encoded objects. After the graph is encoded, {@link #encode} fills the instance with
 * the encoded representation. At this point, graph nodes can be mapped to order IDs.
 *
 * @param <T> the type of the companion object
 * @param <E> the type of the encoded companion object
 */
public abstract class CompanionObjectEncoder<T, E extends CompanionObjectEncoder.EncodedObject> {
    /**
     * An encoded representation of a companion object.
     */
    protected interface EncodedObject {

    }

    /**
     * Maps {@link StructuredGraph#graphId() graph IDs} to encoded companion objects.
     */
    private final EconomicMap<Long, E> encodedObjects;

    protected CompanionObjectEncoder() {
        this.encodedObjects = EconomicMap.create();
    }

    /**
     * Creates an instance of encoded representation of the companion object and adds to the
     * encoding, if the companion object should be encoded. If the companion object should not be
     * encoded, no instance is created and {@code null} is added to the encoding.
     *
     * @param graph the graph to be encoded
     * @param adder adds an object to the encoding
     */
    public void prepare(StructuredGraph graph, Consumer<Object> adder) {
        T companionObject = getCompanionObject(graph);
        if (shouldBeEncoded(companionObject)) {
            E encodedObject = createInstance(companionObject);
            assert encodedObject != null;
            adder.accept(encodedObject);
            assert !encodedObjects.containsKey(graph.graphId());
            encodedObjects.put(graph.graphId(), encodedObject);
        } else {
            adder.accept(null);
        }
    }

    /**
     * Encodes the companion object of the provided graph using the instance created in the
     * {@link #prepare} phase. Returns either the previously-created instance or {@code null} if
     * there is nothing to be encoded.
     *
     * @param graph the graph to be encoded
     * @param mapper maps graph nodes to order IDs
     * @return the encoded companion object or {@code null}
     */
    public E encode(StructuredGraph graph, Function<Node, Integer> mapper) {
        T companionObject = getCompanionObject(graph);
        if (shouldBeEncoded(companionObject)) {
            E encodedObject = encodedObjects.get(graph.graphId());
            assert encodedObject != null;
            encodeIntoInstance(encodedObject, companionObject, mapper);
            return encodedObject;
        } else {
            return null;
        }
    }

    /**
     * Gets the companion object bound to the provided graph.
     *
     * @param graph a graph
     * @return the companion object bound to the graph
     */
    protected abstract T getCompanionObject(StructuredGraph graph);

    /**
     * Returns {@code true} if the provided companion object should be encoded.
     *
     * As an example, if the companion object is a log and logging is disabled, then it should not
     * be encoded.
     *
     * @param companionObject the provided companion object
     * @return {@code true} if the object should be encoded
     */
    protected abstract boolean shouldBeEncoded(T companionObject);

    /**
     * Creates an instance for the encoded representation of the companion object. The method is
     * called iff the companion object {@link #shouldBeEncoded(Object)}.
     *
     * @param companionObject the companion object to be encoded
     * @return an instance of the encoded representation of the companion object
     */
    protected abstract E createInstance(T companionObject);

    /**
     * Encodes a companion object into the provided instance of the encoded representation. The
     * method is called after {@link #prepare} iff the companion object
     * {@link #shouldBeEncoded(Object)}. The provided mapper converts graph nodes to order IDs,
     * which can be used during decoding to restore fields pointing to graph nodes.
     *
     * @param encodedObject the instance of the encoded representation of the companion object
     * @param companionObject the companion object to be encoded
     * @param mapper map graph nodes to order IDs
     */
    protected abstract void encodeIntoInstance(E encodedObject, T companionObject, Function<Node, Integer> mapper);

    /**
     * Verifies the encoding of the companion object in a graph.
     *
     * @param original the graph that was originally encoded
     * @param decoded the graph obtained by decoding the original graph
     * @return {@code true} iff the companion objects of the graphs match
     */
    public abstract boolean verify(StructuredGraph original, StructuredGraph decoded);
}
