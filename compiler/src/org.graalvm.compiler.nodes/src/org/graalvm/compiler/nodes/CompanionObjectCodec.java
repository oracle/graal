/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.graph.Node;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encodes and decodes an object bound with a {@link StructuredGraph} (a companion object).
 *
 * A companion object is a non-trivial field of the {@link StructuredGraph}. It may point to graph
 * nodes, which require special handling during encoding/decoding. An example of a companion object
 * is the inlining log or the optimization log.
 *
 * The expected order of operations to encode a graph is the following:
 * <ol>
 * <li>{@link #prepare} is called,</li>
 * <li>the {@link StructuredGraph} is encoded,</li>
 * <li>{@link #encode} is called,</li>
 * <li>optionally, the encoding is {@link #verify verified}.</li>
 * </ol>
 *
 * In the {@link #prepare} phase, an instance of the encoded representation is created and added to
 * the list of encoded objects. The instance is initially empty and will get filled later. After the
 * graph is encoded, {@link #encode} fills the instance with the encoded representation. At this
 * point, graph nodes can be mapped to order IDs.
 *
 * The expected order of operations to decode a graph is:
 * <ol>
 * <li>{@link #decode} is called before graph decoding starts,</li>
 * <li>{@link #registerNode} is called for each created node.</li>
 * </ol>
 *
 * The {@link #decode} method decodes the encoded representation into the companion object of the
 * newly-created {@link StructuredGraph}. Order IDs cannot be mapped to the nodes of the new graph
 * yet, because it is empty at this point. Restoring the companion object before the actual graph is
 * decoded makes it possible to use the companion object during decoding. This is necessary e.g. for
 * the inlining and optimization log, because a decoder may optimize during decoding. Whenever a
 * node is created, {@link #registerNode} should be called. The decoder can utilize this to map
 * order IDs back to graph nodes.
 *
 * @param <T> the type of the companion object
 * @param <E>the type of the encoded companion object
 */
public abstract class CompanionObjectCodec<T, E extends CompanionObjectCodec.EncodedObject> {
    /**
     * An encoded representation of a companion object.
     */
    protected interface EncodedObject {

    }

    /**
     * Encodes a companion object into its encoded representation.
     *
     * @param <T> the type of the companion object
     * @param <E> the type of the encoded companion object
     */
    protected interface Encoder<T, E extends EncodedObject> {
        /**
         * Returns {@code true} if the provided companion object should be encoded.
         *
         * As an example, if the companion object is a log and logging is disabled, then it should
         * not be encoded.
         *
         * @param companionObject the provided companion object
         * @return {@code true} if the object should be encoded
         */
        boolean shouldBeEncoded(T companionObject);

        /**
         * Prepares an instance of the encoded representation of the companion object. The method is
         * called iff the companion object {@link #shouldBeEncoded(Object)}.
         *
         * @param companionObject the companion object to be encoded
         * @return an instance of the encoded representation of the companion object
         */
        E prepare(T companionObject);

        /**
         * Encodes a companion objects into the provided instance of the encoded representation. The
         * method is called after {@link #prepare} iff the companion object
         * {@link #shouldBeEncoded(Object)}. The provided mapper converts graph nodes to order IDs,
         * which can be used during decoding to restore fields pointing to graph nodes.
         *
         * @param encodedObject the instance of the encoded representation of the companion object
         * @param companionObject the companion object to be encoded
         * @param mapper map graph nodes to order IDs
         */
        void encode(E encodedObject, T companionObject, Function<Node, Integer> mapper);
    }

    /**
     * Decodes an encoded representation of a companion object into the companion of a newly-created
     * graph.
     *
     * @param <T> the type of the companion object
     */
    protected interface Decoder<T> {
        /**
         * Returns {@code true} if an encoded companion object should be decoded into the provided
         * companion object of a newly-created graph.
         *
         * As an example, when the companion object is a log and the new graph has logging disabled,
         * the companion object should not be decoded.
         *
         * @param emptyCompanionObject the companion object of a newly-created graph
         * @return {@code true} if a companion object should be decoded
         */
        boolean shouldBeDecoded(T emptyCompanionObject);

        /**
         * Decodes the provided encoded companion object into the empy companion object of a
         * newly-created graph. The method is called iff the companion object of the new graph
         * {@link #shouldBeDecoded}.
         *
         * @param emptyCompanionObject the companion object of a newly-created graph
         * @param encodedObject the encoded representation of a companion object
         */
        void decode(T emptyCompanionObject, Object encodedObject);

        /**
         * Registers a newly-created node during decoding. If the companion object of the
         * {@link #shouldBeDecoded}, this method is called for each created node after the companion
         * object is {@link #decode decoded}. The decoder should use this method to map order IDs
         * back to graph nodes.
         *
         * @param companionObject the decoded companion object
         * @param node the registered node
         * @param orderId the order ID of the registered node
         */
        void registerNode(T companionObject, Node node, int orderId);

        /**
         * Verifies the encoding of the companion object.
         *
         * @param original the original companion object that was encoded
         * @param decoded the companion object created by decoding
         * @return {@code true} iff the companion objects match
         */
        boolean verify(T original, T decoded);
    }

    /**
     * Maps {@link StructuredGraph#graphId() graph IDs} to encoded companion objects.
     */
    private final EconomicMap<Long, E> encodedObjects;

    /**
     * Gets the companion object from a graph.
     */
    private final Function<StructuredGraph, T> getter;

    /**
     * Encodes companion objects.
     */
    private final Encoder<T, E> encoder;

    /**
     * Decodes companion objects.
     */
    private final Decoder<T> decoder;

    protected CompanionObjectCodec(Function<StructuredGraph, T> getter, Encoder<T, E> encoder, Decoder<T> decoder) {
        this.encodedObjects = EconomicMap.create();
        this.getter = getter;
        this.encoder = encoder;
        this.decoder = decoder;
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
        T companionObject = getter.apply(graph);
        if (encoder.shouldBeEncoded(companionObject)) {
            E encodedObject = encoder.prepare(companionObject);
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
     * @param mapper map graph nodes to order IDs
     * @return the encoded companion object or {@code null}
     */
    public E encode(StructuredGraph graph, Function<Node, Integer> mapper) {
        T companionObject = getter.apply(graph);
        if (encoder.shouldBeEncoded(companionObject)) {
            E encodedObject = encodedObjects.get(graph.graphId());
            assert encodedObject != null;
            encoder.encode(encodedObject, companionObject, mapper);
            return encodedObject;
        } else {
            return null;
        }
    }

    /**
     * Decodes an encoded representation of a companion object into the companion object of the
     * provided newly-created graph. No decoding is performed if either the original companion
     * object was not encoded or the companion object of the newly-created graph
     * {@link Decoder#shouldBeDecoded should not be decoded}.
     *
     * @param emptyGraph the newly-created graph being decoded
     * @param encodedObject the encoded representation of a companion object
     */
    public void decode(StructuredGraph emptyGraph, Object encodedObject) {
        T emptyCompanionObject = getter.apply(emptyGraph);
        if (encodedObject == null || !decoder.shouldBeDecoded(emptyCompanionObject)) {
            return;
        }
        decoder.decode(emptyCompanionObject, encodedObject);
    }

    /**
     * Register a newly-created node in the given graph during decoding. This gives the decoder an
     * opportunity to map order ID back to graph nodes.
     *
     * @param graph the graph being decoded
     * @param node the created node
     * @param orderId the order ID of the created node
     */
    public void registerNode(StructuredGraph graph, Node node, int orderId) {
        T companionObject = getter.apply(graph);
        if (decoder.shouldBeDecoded(companionObject)) {
            decoder.registerNode(companionObject, node, orderId);
        }
    }

    /**
     * Verifies the encoding of the companion object. If either companion object
     * {@link Encoder#shouldBeEncoded should not be encoded}, the method returns {@code true}.
     *
     * @param original the original graph that was encoded
     * @param decoded the graph that was decoded from the encoded representation
     * @return {@code true} iff the companion objects of the graphs match
     */
    public boolean verify(StructuredGraph original, StructuredGraph decoded) {
        T originalObject = getter.apply(original);
        T decodedObject = getter.apply(decoded);
        if (encoder.shouldBeEncoded(originalObject) && encoder.shouldBeEncoded(decodedObject)) {
            return decoder.verify(originalObject, decodedObject);
        }
        return true;
    }
}
