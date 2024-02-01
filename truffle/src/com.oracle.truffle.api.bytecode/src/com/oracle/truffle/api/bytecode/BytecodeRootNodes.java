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
package com.oracle.truffle.api.bytecode;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.serialization.BytecodeSerializer;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * A {@link BytecodeRootNodes} instance encapsulates one or more bytecode root nodes produced from a
 * single parse. To reduce interpreter footprint, it supports on-demand reparsing to compute source
 * and instrumentation metadata.
 * <p>
 * This class will be overridden by the Bytecode DSL. Do not override manually.
 *
 * @param <T> the type of the bytecode root node
 * @since 24.1
 */
public abstract class BytecodeRootNodes<T extends RootNode & BytecodeRootNode> {

    /**
     * A singleton object used to ensure certain Bytecode DSL APIs are only used by generated code.
     *
     * @since 24.1
     */
    protected static final Object TOKEN = new Object();

    private final BytecodeParser<? extends BytecodeBuilder> parser;
    /**
     * The array of parsed nodes.
     *
     * @since 24.1
     */
    @CompilationFinal(dimensions = 1) protected T[] nodes;

    protected BytecodeRootNodes(BytecodeParser<? extends BytecodeBuilder> parser) {
        this.parser = parser;
    }

    static void checkToken(Object token) {
        if (token != BytecodeRootNodes.TOKEN) {
            throw new IllegalArgumentException("Invalid usage token. Seriously, you shouldn't subclass this class manually.");
        }
    }

    /**
     * Returns the list of bytecode root nodes. The order of the list corresponds to the order of
     * {@code endRoot()} calls on the builder.
     *
     * @since 24.1
     */
    public final List<T> getNodes() {
        return List.of(nodes);
    }

    /**
     * Returns the bytecode root node at index {@code i}. The order of the list corresponds to the
     * order of {@code endRoot()} calls on the builder.
     *
     * @since 24.1
     */
    public final T getNode(int i) {
        return nodes[i];
    }

    /**
     * Returns the number of root nodes produced from the parse.
     *
     * @since 24.1
     */
    public final int count() {
        return nodes.length;
    }

    /**
     * Returns the parser used to parse the root nodes.
     *
     * @since 24.1
     */
    protected final BytecodeParser<? extends BytecodeBuilder> getParser() {
        return parser;
    }

    @SuppressWarnings("static-method")
    protected final boolean isAddSource(BytecodeConfig config) {
        return config.addSource;
    }

    @SuppressWarnings("static-method")
    protected final Class<?>[] getAddInstrumentations(BytecodeConfig config) {
        return config.addInstrumentations;
    }

    @SuppressWarnings("static-method")
    protected final Class<?>[] getRemoveInstrumentations(BytecodeConfig config) {
        return config.removeInstrumentations;
    }

    /**
     * Updates the configuration for the given bytecode nodes. If the new configuration requires
     * more information (e.g., sources or instrumentation tags), triggers a reparse to obtain it.
     * <p>
     * Returns whether a reparse was performed.
     *
     * @since 24.1
     */
    @TruffleBoundary
    public final void reparse(BytecodeConfig config) {
        reparseImpl(config);
    }

    @SuppressWarnings("static-method")
    protected final Class<?>[] getAddTags(BytecodeConfig config) {
        return config.addTags;
    }

    /**
     * Implementation of reparse.
     * <p>
     * This method will be generated by the Bytecode DSL. Do not override.
     *
     * @since 24.1
     */
    protected abstract boolean reparseImpl(BytecodeConfig config);

    /**
     * Serializes the nodes to a byte buffer. This method will always fail unless serialization is
     * {@link GenerateBytecode#enableSerialization enabled}.
     * <p>
     * This method will be overridden by the Bytecode DSL. Do not override.
     *
     * @param buffer The buffer to write the serialized bytes to.
     * @param callback A language-defined method for serializing language constants.
     * @throws IOException if an I/O error occurs with the buffer.
     */
    @SuppressWarnings("unused")
    public void serialize(DataOutput buffer, BytecodeSerializer callback) throws IOException {
        throw new IllegalArgumentException("Serialization is not enabled for this interpreter.");
    }

    /**
     * Ensures that sources are available, reparsing if necessary.
     *
     * @since 24.1
     */
    public final boolean ensureSources() {
        return reparseImpl(BytecodeConfig.WITH_SOURCE);
    }

    /**
     * Ensures the given instrumentations are available, reparsing if necessary.
     *
     * @since 24.1
     */
    public final boolean enableInstrumentations(Class<?>... instrumentations) {
        return reparseImpl(BytecodeConfig.newBuilder().addInstrumentations(instrumentations).build());
    }

    /**
     * Ensures the given tags are available, reparsing if necessary.
     *
     * @since 24.1
     */
    @SuppressWarnings("unchecked")
    public final boolean ensureTags(Class<? extends Tag>... tags) {
        return reparseImpl(BytecodeConfig.newBuilder().addTags(tags).build());
    }

    /**
     * Removes the given instrumentations, reparsing if necessary.
     *
     * @since 24.1
     */
    public final boolean disableInstrumentations(Class<?>... instrumentations) {
        return reparseImpl(BytecodeConfig.newBuilder().removeInstrumentations(instrumentations).build());
    }

    /**
     * Returns a string representation of a {@link BytecodeRootNodes}.
     *
     * @since 24.1
     */
    @Override
    public String toString() {
        return String.format("BytecodeNodes %s", Arrays.toString(nodes));
    }
}
