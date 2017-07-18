/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class MetadataList {

    private final MetadataList parent;

    private final List<MDBaseNode> metadata = new ArrayList<>();

    private final List<MDNamedNode> namedMetadata = new ArrayList<>();

    private final List<MDKind> mdKinds = new ArrayList<>();

    // TODO associate this with the actual instruction
    // TODO multiple attachments to the same instruction are possible
    private final Map<Long, MDAttachment> instructionAttachments = new HashMap<>();

    // TODO associate this with the actual global symbol
    private final Map<Long, List<MDAttachment>> globalAttachments = new HashMap<>();

    private final List<MDAttachment> functionAttachments = new ArrayList<>();

    public MetadataList() {
        this(null);
    }

    private MetadataList(MetadataList parent) {
        this.parent = parent;
    }

    public Map<Long, MDAttachment> getInstructionAttachments() {
        return instructionAttachments;
    }

    public List<MDAttachment> getFunctionAttachments() {
        return functionAttachments;
    }

    public void add(MDBaseNode element) {
        metadata.add(element);
    }

    public void addNamed(MDNamedNode namedNode) {
        namedMetadata.add(namedNode);
    }

    public void addKind(MDKind newKind) {
        mdKinds.add(newKind);
    }

    public void addGlobalAttachment(long globalIndex, MDAttachment attachment) {
        final List<MDAttachment> attachments;
        if (globalAttachments.containsKey(globalIndex)) {
            attachments = globalAttachments.get(globalIndex);
        } else {
            attachments = new ArrayList<>();
            globalAttachments.put(globalIndex, attachments);
        }
        attachments.add(attachment);
    }

    public void addAttachment(long instructionIndex, MDAttachment attachment) {
        instructionAttachments.put(instructionIndex, attachment);
    }

    public void addAttachment(MDAttachment attachment) {
        functionAttachments.add(attachment);
    }

    public MDBaseNode removeLast() {
        if (metadata.size() <= 0) {
            throw new IllegalStateException();
        }
        return metadata.remove(metadata.size() - 1);
    }

    public MDReference getMDRef(long index) {
        return MDReference.fromIndex((int) index, this);
    }

    public MDReference getMDRefOrNullRef(long index) {
        // offsets into the metadatalist are incremented by 1 so 0 can indicate a nullpointer
        if (index == 0) {
            return MDReference.VOID;
        } else {
            return getMDRef(index - 1);
        }
    }

    MDBaseNode getFromRef(int index) {
        if (parent != null && index < parent.size()) {
            return parent.getFromRef(index);

        } else {
            return metadata.get(index - (parent != null ? parent.size() : 0));
        }
    }

    public MDKind getKind(long id) {
        Stream<MDKind> kindStream = mdKinds.stream();
        for (MetadataList x = parent; x != null; x = x.parent) {
            kindStream = Stream.concat(kindStream, x.mdKinds.stream());
        }
        return kindStream.filter(kind -> kind.getId() == id).findAny().orElseThrow(() -> new AssertionError("No kind with id: " + id));
    }

    public int size() {
        return parent == null ? metadata.size() : metadata.size() + parent.size();
    }

    public void print(Consumer<String> target) {
        print(target, null);
    }

    public void print(Consumer<String> target, String functionName) {
        if (metadata.isEmpty() && mdKinds.isEmpty()) {
            // function-level metadata is often empty
            return;

        } else if (functionName == null) {
            target.accept("Module-Level Metadata");

        } else {
            target.accept(String.format("Function-Level Metadata: %s", functionName));
        }

        final int startIndex = (parent != null ? parent.size() : 0);
        IntStream.range(0, metadata.size()).mapToObj(i -> String.format("-> !%d %s", i + startIndex, metadata.get(i))).forEachOrdered(target);
        namedMetadata.stream().map(n -> String.format("-> !%s", n)).forEach(target);
        mdKinds.stream().map(k -> String.format("-> %s", k)).forEach(target);
    }

    public void accept(MetadataVisitor visitor) {
        mdKinds.forEach(md -> md.accept(visitor));
        namedMetadata.forEach(md -> md.accept(visitor));
        metadata.forEach(md -> md.accept(visitor));
    }

    @Override
    public String toString() {
        return String.format("MetadataList [size=%d, parent=%s]", size(), parent);
    }

    public MetadataList instantiate() {
        return new MetadataList(this);
    }
}
