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
package com.oracle.truffle.api.bytecode;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * Sharing layer specific data for bytecode DSL interpreters.
 * <p>
 * Note we do not create the engine data per sharing layer and not per polyglot engine in order to
 * support features like {@link BytecodeDescriptor#update(TruffleLanguage, BytecodeConfig)} in the
 * language. Bytecode updates must never occur across multiple sharing layers. It is a bit awkward
 * for engine data like engineTracers which may print data for each sharing layer.
 */
final class BytecodeEngineData {

    private final Object sharingLayer; // PolyglotSharingLayer
    private final ConcurrentHashMap<BytecodeDescriptor<?, ?, ?>, DescriptorData> descriptorData = new ConcurrentHashMap<>();
    private volatile Map<Function<BytecodeDescriptor<?, ?, ?>, InstructionTracer>, Map<BytecodeDescriptor<?, ?, ?>, InstructionTracer>> engineTracerFactories;

    BytecodeEngineData(Object sharingLayer) {
        this.sharingLayer = sharingLayer;
    }

    DescriptorData getDescriptor(BytecodeDescriptor<?, ?, ?> descriptor) {
        DescriptorData d = descriptorData.get(descriptor);
        if (d == null) {
            d = initializeDescriptor(descriptor);
        }
        return d;
    }

    private synchronized DescriptorData initializeDescriptor(BytecodeDescriptor<?, ?, ?> descriptor) {
        DescriptorData d = descriptorData.get(descriptor);
        if (d != null) {
            return d;
        }

        DescriptorData newData = new DescriptorData(this, descriptor);

        DescriptorData prev = descriptorData.putIfAbsent(descriptor, newData);
        if (prev != null) {
            return prev;
        }

        if (engineTracerFactories != null) {
            for (var entry : engineTracerFactories.entrySet()) {
                InstructionTracer tracer = entry.getKey().apply(descriptor);
                newData.addInstructionTracer(tracer);
                entry.getValue().put(descriptor, tracer);
            }
        }

        return newData;
    }

    synchronized List<InstructionTracer> getEngineInstructionTracers(Function<BytecodeDescriptor<?, ?, ?>, InstructionTracer> tracerFactory) {
        Map<BytecodeDescriptor<?, ?, ?>, InstructionTracer> tracers = engineTracerFactories.get(tracerFactory);
        if (tracers == null) {
            return List.of();
        }
        return List.copyOf(tracers.values());
    }

    synchronized void addEngineTracerFactory(Function<BytecodeDescriptor<?, ?, ?>, InstructionTracer> tracerFactory) {
        if (engineTracerFactories == null) {
            engineTracerFactories = new LinkedHashMap<>();
        }
        Map<BytecodeDescriptor<?, ?, ?>, InstructionTracer> tracers = new LinkedHashMap<>();
        var prev = engineTracerFactories.putIfAbsent(tracerFactory, tracers);
        if (prev != null) {
            throw new IllegalArgumentException("tracer factory already registered");
        }
        for (var entry : descriptorData.entrySet()) {
            BytecodeDescriptor<?, ?, ?> descriptor = entry.getKey();
            DescriptorData data = entry.getValue();
            InstructionTracer tracer = tracerFactory.apply(descriptor);
            data.addInstructionTracer(tracer);
            tracers.put(descriptor, tracer);
        }
        BytecodeDescriptor.enableEngineDescriptorLookup();
    }

    static BytecodeEngineData get(TruffleLanguage<?> language) {
        Object languageInstance = BytecodeAccessor.LANGUAGE.getPolyglotLanguageInstance(language);
        Object sharingLayer = BytecodeAccessor.ENGINE.getSharingLayer(languageInstance);
        return get(sharingLayer);
    }

    static BytecodeEngineData get(Object sharingLayer) {
        BytecodeEngineData data = BytecodeAccessor.ENGINE.getOrCreateBytecodeData(sharingLayer, BytecodeEngineData::new);
        if (data == null) {
            throw new IllegalStateException("Could not load the bytecode engine data. Context not yet initialized?");
        }
        return data;
    }

    /**
     * Sharing layer specific data for an individual bytecode interpreter specification.
     */
    static final class DescriptorData {

        private static final InstructionTracer[] EMPTY = new InstructionTracer[0];

        private final BytecodeEngineData bytecodeData;
        private final BytecodeDescriptor<?, ?, ?> descriptor;
        private final long traceInstructionEncoding;
        private final BytecodeConfigEncoder configEncoder;

        /**
         * Stores the global config encoding for all bytecode interpreters of this language.
         */
        private volatile long descriptorConfig;

        /**
         * Stores all installed global tracers.
         */
        private volatile InstructionTracer[] descriptorTracers = EMPTY;

        DescriptorData(BytecodeEngineData bytecodeData, BytecodeDescriptor<?, ?, ?> descriptor) {
            this.bytecodeData = bytecodeData;
            this.descriptor = descriptor;
            BytecodeConfig c = descriptor.newConfigBuilder().addInstructionTracing().build();
            this.traceInstructionEncoding = c.encoding;
            this.configEncoder = c.encoder;
        }

        void addInstructionTracer(InstructionTracer tracer) {
            Objects.requireNonNull(tracer);
            InstructionTracer[] current = descriptorTracers;
            for (InstructionTracer t : current) {
                if (t == tracer) {
                    return;
                }
            }
            InstructionTracer[] updated = Arrays.copyOf(current, current.length + 1);
            updated[current.length] = tracer;
            updateTracers(updated);
        }

        void removeInstructionTracer(InstructionTracer tracer) {
            List<InstructionTracer> tracers = List.of(descriptorTracers);
            if (!tracers.contains(tracer)) {
                return;
            }
            InstructionTracer[] newArray = new InstructionTracer[tracers.size() - 1];
            int index = 0;
            for (InstructionTracer t : tracers) {
                if (!t.equals(tracer)) {
                    newArray[index++] = t;
                }
            }
            assert index == newArray.length : "tracer is stored exactly once";
            updateTracers(newArray);
        }

        private synchronized void updateTracers(InstructionTracer[] update) {
            InstructionTracer[] tracers = this.descriptorTracers;
            if (Arrays.equals(tracers, update)) {
                // other update already did the work
                return;
            }
            this.descriptorTracers = update;
            this.descriptorConfig |= traceInstructionEncoding;
            this.descriptor.enableDescriptorLookup();
            /*
             * For each already loaded bytecode root node we need to explicitly update.
             */
            forEachLoadedBytecodeRoot((r) -> {
                updateRootNode(r);
            });
        }

        private void forEachLoadedBytecodeRoot(Consumer<BytecodeRootNode> action) {
            BytecodeAccessor.ENGINE.forEachLoadedRootNode(bytecodeData.sharingLayer, (rootNode) -> {
                BytecodeRootNode resolved = descriptor.cast(rootNode);
                if (resolved != null) {
                    action.accept(resolved);
                }
            });
        }

        long updateBytecodeConfig(long config) {
            return config | this.descriptorConfig;
        }

        void onPrepareForCall(BytecodeRootNode rootNode) {
            updateRootNode(rootNode);
        }

        private void updateRootNode(BytecodeRootNode rootNode) {
            BytecodeRootNodes<?> r = rootNode.getRootNodes();
            long encoding = this.descriptorConfig;
            if (encoding != 0) {
                r.updateImpl(this.configEncoder, encoding);
            }
            if ((encoding & this.traceInstructionEncoding) != 0) {
                r.updateGlobalInstructionTracers(this.descriptorTracers);
            }
        }

        void updateConfig(BytecodeConfig config) {
            long newEncoding = config.encoding;
            long oldEncoding = this.descriptorConfig;
            if ((oldEncoding | newEncoding) == oldEncoding) {
                // double checked locking
                return;
            }
            updateConfigImpl(newEncoding);
        }

        private synchronized void updateConfigImpl(long newEncoding) {
            long oldEncoding = this.descriptorConfig;
            if ((oldEncoding | newEncoding) == oldEncoding) {
                return;
            }
            this.descriptorConfig |= newEncoding;
            this.descriptor.enableDescriptorLookup();
            forEachLoadedBytecodeRoot((e) -> {
                onPrepareForCall(e);
            });
        }

    }

}
