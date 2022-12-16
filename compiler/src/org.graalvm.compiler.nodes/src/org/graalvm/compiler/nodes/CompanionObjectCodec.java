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

public abstract class CompanionObjectCodec<T, E extends CompanionObjectCodec.EncodedObject> {
    protected interface EncodedObject {

    }

    protected interface Encoder<T, E extends EncodedObject> {
        boolean shouldBeEncoded(T companionObject);

        E prepare(T companionObject);

        void encode(E encodedObject, T companionObject, Function<Node, Integer> mapper);
    }

    protected interface Decoder<T> {
        void decode(T emptyCompanionObject, Object encodedObject, Function<Integer, Node> mapper);

        boolean verify(T original, T decoded);
    }

    private final EconomicMap<Long, E> encodedObjects;

    private final Function<StructuredGraph, T> getter;

    private final Encoder<T, E> encoder;

    private final Decoder<T> decoder;

    protected CompanionObjectCodec(Function<StructuredGraph, T> getter, Encoder<T, E> encoder, Decoder<T> decoder) {
        this.encodedObjects = EconomicMap.create();
        this.getter = getter;
        this.encoder = encoder;
        this.decoder = decoder;
    }

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

    public void decode(StructuredGraph emptyGraph, Object encodedObject, Function<Integer, Node> mapper) {
        if (encodedObject == null) {
            return;
        }
        T emptyCompanionObject = getter.apply(emptyGraph);
        assert emptyCompanionObject != null;
        decoder.decode(emptyCompanionObject, encodedObject, mapper);
    }

    public boolean verify(StructuredGraph original, StructuredGraph decoded) {
        T originalObject = getter.apply(original);
        T decodedObject = getter.apply(decoded);
        if (encoder.shouldBeEncoded(originalObject) && encoder.shouldBeEncoded(decodedObject)) {
            return decoder.verify(originalObject, decodedObject);
        } else {
            return encoder.shouldBeEncoded(originalObject) == encoder.shouldBeEncoded(decodedObject);
        }
    }
}
