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
package com.oracle.truffle.dsl.processor.bytecode.generator;

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.addField;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.generic;
import static com.oracle.truffle.dsl.processor.generator.GeneratorUtils.createConstructorUsingFields;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class SerializationEncodingElement extends AbstractElement {

    static final int BUILDER_MAGIC = 0x54524242; // TRBB
    static final int EXECUTION_BYTECODE_MAGIC = 0x54524243; // TRBC
    // Increment when changing this encoding.
    static final short FORMAT_VERSION = 1;
    static final int CONSTANT_TAG_OBJECT = 0;
    static final int CONSTANT_TAG_LOCAL_ACCESSOR = 1;
    static final int CONSTANT_TAG_LOCAL_RANGE_ACCESSOR = 2;
    static final int CONSTANT_TAG_MATERIALIZED_LOCAL_ACCESSOR = 3;
    static final int CONSTANT_TAG_CONTINUATION = 4;
    static final int CONSTANT_TAG_NULL = 5;

    final CodeVariableElement builderMagic;
    final CodeVariableElement executionBytecodeMagic;
    final CodeVariableElement formatVersion;
    final CodeVariableElement constantTagObject;
    final CodeVariableElement constantTagLocalAccessor;
    final CodeVariableElement constantTagLocalRangeAccessor;
    final CodeVariableElement constantTagMaterializedLocalAccessor;
    final CodeVariableElement constantTagContinuation;
    final CodeVariableElement constantTagNull;

    private final CodeTypeElement executionBytecodeSerializationContext;
    private final CodeTypeElement executionBytecodeDeserializationContext;
    private final CodeTypeElement continuationData;

    SerializationEncodingElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "SerializationEncoding");

        this.builderMagic = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "BUILDER_MAGIC", String.format("0x%08x", BUILDER_MAGIC));
        this.executionBytecodeMagic = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "EXECUTION_BYTECODE_MAGIC", String.format("0x%08x", EXECUTION_BYTECODE_MAGIC));
        this.formatVersion = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "FORMAT_VERSION", Short.toString(FORMAT_VERSION));
        this.constantTagObject = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_OBJECT", Integer.toString(CONSTANT_TAG_OBJECT));
        this.constantTagLocalAccessor = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_LOCAL_ACCESSOR", Integer.toString(CONSTANT_TAG_LOCAL_ACCESSOR));
        this.constantTagLocalRangeAccessor = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_LOCAL_RANGE_ACCESSOR", Integer.toString(CONSTANT_TAG_LOCAL_RANGE_ACCESSOR));
        this.constantTagMaterializedLocalAccessor = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_MATERIALIZED_LOCAL_ACCESSOR",
                        Integer.toString(CONSTANT_TAG_MATERIALIZED_LOCAL_ACCESSOR));
        this.constantTagContinuation = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_CONTINUATION", Integer.toString(CONSTANT_TAG_CONTINUATION));
        this.constantTagNull = addField(this, Set.of(PRIVATE, STATIC, FINAL), int.class, "CONSTANT_TAG_NULL", Integer.toString(CONSTANT_TAG_NULL));

        this.executionBytecodeSerializationContext = this.add(createExecutionBytecodeSerializationContext());
        this.executionBytecodeDeserializationContext = this.add(createExecutionBytecodeDeserializationContext());
        this.continuationData = parent.model.hasYieldOperation() ? this.add(createContinuationData()) : null;

        this.add(createSerializeBuilderBytecode());
        this.add(createSerializeExecutionBytecode());
        this.add(createDeserializeExecutionBytecode());
        this.add(createReadBuilderPayload());
        this.add(createWriteBuilderPayload());
        this.add(createWriteExecutionBytecodePayload());
        this.add(createReadExecutionBytecode());
        this.add(createReadExecutionBytecodePayload());
        this.add(createWriteRootConstruction());
        this.add(createReadRootConstruction());
        this.add(createWriteRootPayload());
        this.add(createReadRootPayload());
        if (!parent.model.serializedFields.isEmpty()) {
            this.add(createWriteRootFields());
            this.add(createReadRootFields());
        }
        this.add(createWriteConstantPool());
        this.add(createReadConstantPool());
        this.add(createCollectConstants());
        if (parent.model.hasYieldOperation()) {
            this.add(createNormalizeConstantPoolValue());
        }
        this.add(createWriteRootConstants());
        this.add(createReadRootConstants());
        this.add(createWriteObjectReference());
        this.add(createReadObjectReference());
        this.add(createWriteSourceTable());
        this.add(createReadSourceTable());
        if (parent.model.enableTagInstrumentation) {
            this.add(createWriteTagTree());
            this.add(createReadTagTree());
            this.add(createWriteTagRecord());
            this.add(createReadTagRecord());
            this.add(createWriteTagChildren());
            this.add(createReadTagChildren());
        }
        this.add(createWriteByteArray());
        this.add(createReadByteArray());
        this.add(createWriteIntArray());
        this.add(createReadIntArray());
        this.add(createCheckReadInt());
        this.add(createCheckReadIndex());

        BytecodeRootNodeElement.addJavadoc(this, """
                        Defines the builder and execution bytecode serialization encodings for this generated interpreter.
                        Each payload can be stored and decoded independently.
                        <pre>
                        BuilderPayload :=
                            MAGIC:u32 = 0x54524242 ("TRBB", Truffle builder bytecodes)
                            FORMAT_VERSION:u16
                            sequence of builder bytecodes to replay builder calls, including field object definitions
                            i32[ROOT_COUNT][FIELD_COUNT]
                                numeric references to field objects for each root node

                        ExecutionBytecodePayload :=
                            MAGIC:u32 = 0x54524243 ("TRBC", Truffle bytecodes)
                            FORMAT_VERSION:u16
                            CONFIG_ENCODING:u64
                                execution bytecode config
                            ROOT_COUNT:i32
                            RootConstruction[ROOT_COUNT]
                                array of root metadata used to initialize root nodes, ordered by ascending root index
                            ConstantPool
                            SourceTable                         if sources are enabled in CONFIG_ENCODING
                            RootPayload[ROOT_COUNT]
                                array of bytecode metadata used to populate bytecode node for each root

                        RootConstruction :=
                            STACK_BASE:i32
                            FRAME_SLOT_COUNT:i32
                            NUM_LOCALS:i32                      if boxing elimination is enabled

                        RootPayload :=
                            NUM_NODES:i32
                            RootConstants
                            BYTECODE_LENGTH:i32 u8[BYTECODE_LENGTH]
                            HANDLER_TABLE_LENGTH:i32 i32[HANDLER_TABLE_LENGTH]
                            LOCAL_TABLE_LENGTH:i32 i32[LOCAL_TABLE_LENGTH]
                            SourceInfoTable                     if sources are enabled in ExecutionBytecodePayload.CONFIG_ENCODING
                            StableBciTable                      if stable BCI remapping is generated
                            TagTree                             if tag instrumentation is generated
                            RootFields

                        ConstantPool := CONSTANT_COUNT:i32 Constant[CONSTANT_COUNT]
                        Constant :=
                            CONSTANT_TAG_OBJECT:u8 object encoded using the user-provided serializer
                          | CONSTANT_TAG_LOCAL_ACCESSOR:u8 OFFSET:i32 INDEX:i32
                          | CONSTANT_TAG_LOCAL_RANGE_ACCESSOR:u8 START_OFFSET:i32 START_INDEX:i32 LENGTH:i32
                          | CONSTANT_TAG_MATERIALIZED_LOCAL_ACCESSOR:u8 ROOT_INDEX:i32 OFFSET:i32 INDEX:i32
                          | CONSTANT_TAG_CONTINUATION:u8 RESUME_BCI:i32 SP:i32
                          | CONSTANT_TAG_NULL:u8

                        RootConstants :=
                            CONSTANT_COUNT:i32
                            ObjectReference[CONSTANT_COUNT]
                                excludes the tracer at local slot zero when instruction tracing is enabled

                        SourceTable :=
                            SOURCE_COUNT:i32
                            ObjectReference[SOURCE_COUNT]
                                source list shared by all root nodes
                        SourceInfoTable :=
                            ELEMENT_COUNT:i32
                            u8[ELEMENT_COUNT]                   if source compression is enabled
                            i32[ELEMENT_COUNT]                  otherwise

                        StableBciTable :=
                            STABLE_BCI_TABLE_LENGTH:i32
                            i32[STABLE_BCI_TABLE_LENGTH]        if STABLE_BCI_TABLE_LENGTH != -1

                        TagTree :=
                            TAG_NODE_COUNT:i32
                                zero represents a null tag tree
                            TagRecord[TAG_NODE_COUNT]
                                original tag nodes array in reverse order (so that children appear before parents)
                            TAG_ROOT_INDEX:i32                  if TAG_NODE_COUNT != 0
                            TagChildren                         if TAG_NODE_COUNT != 0 &amp;&amp; TAG_ROOT_INDEX == -1
                                children for synthetic tag root node
                        TagRecord :=
                            TAGS:u32
                            ENTER_BCI:i32
                            RETURN_BCI:i32
                            TagChildren
                        TagChildren :=
                            CHILD_COUNT:i32
                            i32[CHILD_COUNT]
                                int indices into original tag nodes array

                        RootFields :=
                            ObjectReference[FIELD_COUNT]
                                references for the FIELD_COUNT root node fields (count is statically known)

                        ObjectReference := CONSTANT_INDEX:i32 index into the shared ConstantPool
                        </pre>
                        Execution bytecode decoding notes:
                        <ul>
                        <li>Root nodes can appear in the shared constant table, so they are first populated with the data in RootConstruction before parsing ConstantPool.</li>
                        <li>The SerializerContext for constants encodes root references as an i32 index into the RootConstruction array.</li>
                        <li>Continuations are encoded with placeholder ContinuationData entries and patched once the BytecodeNode is available.</li>
                        <li>When instruction tracing is enabled, decoding implicitly inserts a fresh tracer access instance at the start of each root's constants array.</li>
                        </ul>
                        """);
    }

    private CodeExecutableElement createSerializeBuilderBytecode() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "serializeBuilderBytecode");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parent.parserType, "parser"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startTryBlock();
        b.startStatement().startCall("buffer", "writeInt").staticReference(builderMagic).end(2);
        b.startStatement().startCall("buffer", "writeShort").staticReference(formatVersion).end(2);
        b.statement("writeBuilderPayload(buffer, callback, parser, existingNodes)");
        b.end();
        b.startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();
        return method;
    }

    private CodeExecutableElement createWriteBuilderPayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeBuilderPayload");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parent.parserType, "parser"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startDeclaration(parent.bytecodeRootNodesImpl.asType(), "rootNodes").startNew(parent.bytecodeRootNodesImpl.asType());
        b.string("parser");
        b.startStaticCall(parent.configEncoder.asType(), "normalizeEncoding").staticReference(types.BytecodeConfig, "COMPLETE").end();
        b.end(2);
        b.startDeclaration(parent.builder.asType(), "builder").startNew(parent.builder.asType());
        b.string("null").string("rootNodes").string("rootNodes.encoding");
        b.end(2);
        b.statement("builder.serialize(buffer, callback, existingNodes != null ? existingNodes.getNodes() : null)");
        return method;
    }

    private CodeExecutableElement createSerializeExecutionBytecode() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "serializeExecutionBytecode");
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(parent.parserType, "parser"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startTryBlock();
        b.startDeclaration(type(long.class), "executionBytecodeEncoding").startStaticCall(parent.configEncoder.asType(), "normalizeEncoding").string("config");
        b.end(2);
        b.startStatement().startCall("buffer", "writeInt").staticReference(executionBytecodeMagic).end(2);
        b.startStatement().startCall("buffer", "writeShort").staticReference(formatVersion).end(2);
        b.statement("buffer.writeLong(executionBytecodeEncoding)");
        b.statement("writeExecutionBytecodePayload(language, buffer, callback, executionBytecodeEncoding, parser, existingNodes)");
        b.end();
        b.startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();
        return method;
    }

    private CodeExecutableElement createDeserializeExecutionBytecode() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), generic(types.BytecodeRootNodes, parent.model.getTemplateType().asType()), "deserializeExecutionBytecode");
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        method.addParameter(new CodeVariableElement(generic(Supplier.class, DataInput.class), "builderBytecodeInput"));
        method.addParameter(new CodeVariableElement(generic(type(Function.class), types.BytecodeConfig, type(DataInput.class)), "executionBytecodeInput"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startStatement().startStaticCall(type(Objects.class), "requireNonNull").string("builderBytecodeInput").end(2);
        b.startTryBlock();
        // Only capture the builder input and callback, allowing the execution bytecode input to be released.
        b.startDeclaration(parent.parserType, "parser");
        b.string("(b) -> ").startStaticCall(asType(), "readBuilderPayload");
        b.startGroup().maybeCast(parent.parserType.getTypeArguments().getFirst(), parent.builder.asType(), "b").end();
        b.string("builderBytecodeInput.get()").string("callback");
        b.end(2);
        b.declaration(type(long.class), "encoding", "BYTECODE.getConfigEncodingWithGlobalConfig(language, config)");
        b.startDeclaration(parent.bytecodeRootNodesImpl.asType(), "nodes").startNew(parent.bytecodeRootNodesImpl.asType());
        b.string("parser").string("encoding");
        b.end(2);

        b.startIf().string("executionBytecodeInput != null").end().startBlock();
        b.startDeclaration(types.BytecodeConfig, "executionBytecodeConfig");
        b.startStaticCall(parent.configEncoder.asType(), "decode").string("encoding").end();
        b.end();
        b.declaration(type(DataInput.class), "executionBytecodes", "executionBytecodeInput.apply(executionBytecodeConfig)");
        b.startIf().string("executionBytecodes != null").end().startBlock();
        b.statement("readExecutionBytecode(executionBytecodes, callback, encoding, language, nodes)");
        b.statement("return nodes");
        b.end();
        b.end();

        b.startDeclaration(parent.builder.asType(), "builder").startNew(parent.builder.asType());
        b.string("language").string("nodes").string("encoding");
        b.end(2);
        b.statement("parser.parse(builder)");
        b.statement("builder.finish(true)");
        b.statement("return nodes");
        b.end().startCatchBlock(type(IOError.class), "e");
        b.startThrow().cast(type(IOException.class), "e.getCause()").end();
        b.end();
        return method;
    }

    private CodeExecutableElement createReadBuilderPayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readBuilderPayload");
        method.addParameter(new CodeVariableElement(parent.builder.asType(), "builder"));
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));

        CodeTreeBuilder b = method.createBuilder();
        b.startTryBlock();
        b.declaration(type(int.class), "magic", "buffer.readInt()");
        b.startIf().string("magic != ").staticReference(builderMagic).end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid bytecode serialization magic.").end(2);
        b.end();
        b.declaration(type(int.class), "version", "buffer.readUnsignedShort()");
        b.startIf().string("version != ").staticReference(formatVersion).end().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Unsupported bytecode serialization format version: \" + version").end(2);
        b.end();
        b.statement("builder.deserialize(buffer, callback, null)");
        b.end();
        b.startCatchBlock(type(IOException.class), "e");
        b.startThrow().startNew(type(IOError.class)).string("e").end(2);
        b.end();
        return method;
    }

    private CodeExecutableElement createWriteExecutionBytecodePayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeExecutionBytecodePayload");
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        method.addParameter(new CodeVariableElement(type(long.class), "executionBytecodeEncoding"));
        method.addParameter(new CodeVariableElement(parent.parserType, "parser"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startDeclaration(parent.bytecodeRootNodesImpl.asType(), "nodes").startNew(parent.bytecodeRootNodesImpl.asType());
        b.string("parser").string("executionBytecodeEncoding");
        b.end(2);
        b.startDeclaration(parent.builder.asType(), "builder").startNew(parent.builder.asType());
        b.string("language").string("nodes").string("executionBytecodeEncoding");
        b.end(2);
        b.statement("parser.parse(builder)");
        /*
         * Keep temporary roots unregistered so prepareForCall rejects serializer callbacks that
         * could trigger reparsing while their bytecodes are being written.
         */
        b.statement("builder.finish(false)");

        b.declaration(type(int.class), "rootCount", "builder.builtNodes.size()");
        b.statement("buffer.writeInt(rootCount)");
        b.startFor().string("int rootIndex = 0; rootIndex < rootCount; rootIndex++").end().startBlock();
        b.startStatement().startCall("writeRootConstruction");
        b.string("buffer");
        b.string("builder.builtNodes.get(rootIndex)");
        b.end(2);
        b.end();
        b.startDeclaration(executionBytecodeSerializationContext.asType(), "context").startNew(executionBytecodeSerializationContext.asType());
        b.string("callback").string("nodes").string("existingNodes");
        b.end(2);
        b.statement("writeConstantPool(buffer, builder, existingNodes, context)");
        b.startIf().string(parent.configEncoder.checkSourceBit("executionBytecodeEncoding")).end().startBlock();
        b.statement("writeSourceTable(buffer, builder.sources, context)");
        b.end();
        b.startFor().string("int rootIndex = 0; rootIndex < rootCount; rootIndex++").end().startBlock();
        b.startStatement().startCall("writeRootPayload");
        b.string("buffer");
        b.string("builder.builtNodes.get(rootIndex)");
        if (!parent.model.serializedFields.isEmpty()) {
            b.string("existingNodes != null ? existingNodes.getNode(rootIndex) : builder.builtNodes.get(rootIndex)");
        }
        b.string("context");
        b.end(2);
        b.end();
        return method;
    }

    private CodeExecutableElement createReadExecutionBytecode() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readExecutionBytecode");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addParameter(new CodeVariableElement(type(long.class), "requestedEncoding"));
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "nodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "magic", "buffer.readInt()");
        b.startIf().string("magic != ").staticReference(executionBytecodeMagic).end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid execution bytecode serialization magic.").end(2);
        b.end();
        b.declaration(type(int.class), "version", "buffer.readUnsignedShort()");
        b.startIf().string("version != ").staticReference(formatVersion).end().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Unsupported execution bytecode serialization format version: \" + version").end(2);
        b.end();
        b.declaration(type(long.class), "executionBytecodeEncoding", "buffer.readLong()");
        b.startIf().string("executionBytecodeEncoding != requestedEncoding").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Execution bytecode configuration does not match the requested configuration.").end(2);
        b.end();
        b.statement("readExecutionBytecodePayload(buffer, callback, executionBytecodeEncoding, language, nodes)");
        return method;
    }

    private CodeExecutableElement createReadExecutionBytecodePayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readExecutionBytecodePayload");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        method.addParameter(new CodeVariableElement(type(long.class), "executionBytecodeEncoding"));
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "nodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "rootCount", "checkReadInt(buffer, 0)");
        b.startDeclaration(arrayOf(parent.asType()), "roots");
        b.startNewArray(arrayOf(parent.asType()), CodeTreeBuilder.singleString("rootCount")).end();
        b.end();
        b.startFor().string("int rootIndex = 0; rootIndex < rootCount; rootIndex++").end().startBlock();
        b.statement("roots[rootIndex] = readRootConstruction(buffer, language, nodes, rootIndex)");
        b.end();
        b.startDeclaration(executionBytecodeDeserializationContext.asType(), "context").startNew(executionBytecodeDeserializationContext.asType());
        b.string("callback").string("language").string("roots");
        b.end(2);
        b.statement("readConstantPool(buffer, context)");
        b.declaration(generic(type(List.class), types.Source), "sources", "null");
        b.startIf().string(parent.configEncoder.checkSourceBit("executionBytecodeEncoding")).end().startBlock();
        b.statement("sources = readSourceTable(buffer, context)");
        b.end();
        b.startFor().string("int rootIndex = 0; rootIndex < rootCount; rootIndex++").end().startBlock();
        b.statement("readRootPayload(buffer, roots[rootIndex], executionBytecodeEncoding, sources, context)");
        b.end();
        b.startAssert().startStaticCall(parent.bytecodeRootNodesImpl.asType(), "validate");
        b.startStaticCall(type(Arrays.class), "asList").string("roots").end();
        b.end(2);
        /*
         * The user serializer could erroneously trigger call target preparation (and consequently
         * reparsing) before the root is ready. We only call setNodes once the roots are ready; the
         * prepareForCall implementation will correctly throw an error if called before this point.
         */
        b.statement("nodes.setNodes(roots)");
        return method;
    }

    private CodeExecutableElement createWriteRootConstruction() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeRootConstruction");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.asType(), "root"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(root.stackBase)");
        b.statement("buffer.writeInt(root.getFrameDescriptor().getNumberOfSlots())");
        if (parent.model.usesBoxingElimination()) {
            b.statement("buffer.writeInt(root.numLocals)");
        }
        return method;
    }

    private CodeExecutableElement createReadRootConstruction() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), parent.asType(), "readRootConstruction");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "nodes"));
        method.addParameter(new CodeVariableElement(type(int.class), "rootIndex"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "stackBase", "checkReadInt(buffer, 0)");
        b.declaration(type(int.class), "frameSlotCount", "checkReadInt(buffer, 0)");
        if (parent.model.usesBoxingElimination()) {
            b.declaration(type(int.class), "numLocals", "checkReadInt(buffer, 0)");
        }
        b.startIf().string("stackBase > frameSlotCount").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Stack base exceeds frame slot count.").end(2);
        b.end();

        parent.emitInitializeFrameDescriptorBuilder(b, "frameDescriptorBuilder", CodeTreeBuilder.singleString("frameSlotCount"));

        b.startReturn().startNew(parent.asType());
        b.string("language").string("frameDescriptorBuilder").string("nodes").string("stackBase").string("frameSlotCount");
        if (parent.model.usesBoxingElimination()) {
            b.string("numLocals");
        }
        b.string("rootIndex");
        b.end(2);
        return method;
    }

    private CodeExecutableElement createWriteRootPayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeRootPayload");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.asType(), "root"));
        if (!parent.model.serializedFields.isEmpty()) {
            method.addParameter(new CodeVariableElement(parent.model.getTemplateType().asType(), "fieldsRoot"));
        }
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(parent.abstractBytecodeNode.asType(), "bytecode", "root.bytecode");
        b.statement("buffer.writeInt(bytecode.numNodes)");
        b.statement("writeRootConstants(buffer, root, context)");
        b.statement("writeByteArray(buffer, bytecode.bytecodes)");
        b.statement("writeIntArray(buffer, bytecode.handlers)");
        b.statement("writeIntArray(buffer, bytecode.locals)");
        b.startIf().string(parent.configEncoder.checkSourceBit("bytecode.configEncoding")).end().startBlock();
        b.startAssert().string("bytecode.sourceInfo != null").end();
        b.statement(parent.model.enableCompressedSources ? "writeByteArray(buffer, bytecode.sourceInfo)" : "writeIntArray(buffer, bytecode.sourceInfo)");
        b.end();
        if (parent.model.needsStableBciRemappings()) {
            b.statement("writeIntArray(buffer, bytecode.stableBciDeltas)");
        }
        if (parent.model.enableTagInstrumentation) {
            b.statement("writeTagTree(buffer, bytecode.tagRoot)");
        }
        if (!parent.model.serializedFields.isEmpty()) {
            b.statement("writeRootFields(buffer, fieldsRoot, context)");
        }
        return method;
    }

    private CodeExecutableElement createReadRootPayload() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readRootPayload");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.asType(), "root"));
        method.addParameter(new CodeVariableElement(type(long.class), "executionBytecodeEncoding"));
        method.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "sources"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "numNodes", "checkReadInt(buffer, 0)");
        b.declaration(type(long.class), "configEncoding", "executionBytecodeEncoding");
        b.declaration(arrayOf(type(Object.class)), "constants", "readRootConstants(buffer, executionBytecodeEncoding, context)");
        if (parent.model.hasYieldOperation()) {
            b.lineComment("Derive the root's has-yields bit from its continuation constants.");
            b.startFor().string("Object constant : constants").end().startBlock();
            b.startIf().string("constant instanceof ").type(continuationData.asType()).end().startBlock();
            b.statement("configEncoding |= 0x" + Long.toHexString(parent.configEncoder.encoding.hasYieldsMask()) + "L");
            b.statement("break");
            b.end();
            b.end();
        }
        b.declaration(type(byte[].class), "bytecodes", "readByteArray(buffer)");
        b.startIf().string("bytecodes == null").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Missing bytecode array.").end(2);
        b.end();
        b.declaration(type(int[].class), "handlers", "readIntArray(buffer)");
        b.startIf().string("handlers == null || handlers.length % EXCEPTION_HANDLER_LENGTH != 0").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid exception handler table.").end(2);
        b.end();
        b.declaration(type(int[].class), "locals", "readIntArray(buffer)");
        b.startIf().string("locals == null || locals.length % LOCALS_LENGTH != 0").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid local table.").end(2);
        b.end();
        b.declaration(parent.sourceInfoTable.getSourceInfoType(), "sourceInfo", "null");
        b.startIf().string(parent.configEncoder.checkSourceBit("executionBytecodeEncoding")).end().startBlock();
        b.statement(parent.model.enableCompressedSources ? "sourceInfo = readByteArray(buffer)" : "sourceInfo = readIntArray(buffer)");
        b.startIf().string("sourceInfo == null").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Missing source information table.").end(2);
        b.end();
        if (!parent.model.enableCompressedSources) {
            b.startIf().string("sourceInfo.length % ").staticReference(parent.sourceInfoTable.entryLengthVariable).string(" != 0").end().startBlock();
            b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid source information table.").end(2);
            b.end();
        }
        b.end();
        if (parent.model.needsStableBciRemappings()) {
            b.declaration(type(int[].class), "stableBciDeltas", "readIntArray(buffer)");
            b.startIf().string("stableBciDeltas != null && stableBciDeltas.length % 2 != 0").end().startBlock();
            b.startThrow().startNew(type(IOException.class)).doubleQuote("Invalid stable BCI table.").end(2);
            b.end();
        }
        if (parent.model.enableTagInstrumentation) {
            b.declaration(parent.tagRootNode.asType(), "tagRoot", "readTagTree(buffer)");
        }
        b.startStatement().startCall("root", "setInitialBytecodeNode");
        for (VariableElement var : ElementFilter.fieldsIn(parent.abstractBytecodeNode.getEnclosedElements())) {
            b.string(var.getSimpleName().toString());
        }
        b.end(2);
        if (parent.model.hasYieldOperation()) {
            b.declaration(parent.abstractBytecodeNode.asType(), "bytecode", "root.bytecode");
            b.startFor().string("int i = 0; i < constants.length; i++").end().startBlock();
            b.startIf().string("constants[i] instanceof ").type(continuationData.asType()).string(" continuation").end().startBlock();
            b.declaration(types.BytecodeLocation, "location", "continuation.resumeBci == -1 ? null : bytecode.getBytecodeLocation(continuation.resumeBci)");
            b.startAssign("constants[i]").startNew(parent.continuationRootNodeImpl.asType());
            b.string("context.language").string("root").string("continuation.sp").string("location");
            b.end(2);
            b.end();
            b.end();
        }
        if (!parent.model.serializedFields.isEmpty()) {
            b.statement("readRootFields(buffer, root, context)");
        }
        return method;
    }

    private CodeExecutableElement createWriteRootFields() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeRootFields");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.model.getTemplateType().asType(), "node"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        for (VariableElement field : parent.model.serializedFields) {
            b.startStatement().startCall("writeObjectReference");
            b.string("buffer");
            b.field("node", field);
            b.string("context");
            b.end(2);
        }
        return method;
    }

    private CodeExecutableElement createReadRootFields() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readRootFields");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.asType(), "node"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        for (VariableElement field : parent.model.serializedFields) {
            b.startAssign("node", field);
            TypeMirror fieldType = field.asType();
            if (ElementUtils.needsCastTo(type(Object.class), fieldType)) {
                BuilderElement.emitCastWithSuppression(method, b, fieldType);
            }
            b.startCall("readObjectReference").string("buffer").string("context").end();
            b.end();
        }
        return method;
    }

    private CodeExecutableElement createCollectConstants() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "collectConstants");
        method.addParameter(new CodeVariableElement(parent.builder.asType(), "builder"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("builder.sources != null").end().startBlock();
        b.startFor().type(types.Source).string(" source : builder.sources").end().startBlock();
        b.statement("context.addObject(source)");
        b.end();
        b.end();
        b.startFor().type(parent.asType()).string(" root : builder.builtNodes").end().startBlock();
        b.declaration(parent.abstractBytecodeNode.asType(), "bytecode", "root.bytecode");
        if (parent.model.enableInstructionTracing) {
            b.declaration(type(int.class), "constantOffset", "bytecode.isInstructionTracingEnabled() ? 1 : 0");
            b.startAssert().string("constantOffset == 0 || bytecode.constants[0] instanceof ").type(parent.instructionTracerAccessImplElement.asType()).end();
            b.startFor().string("int i = constantOffset; i < bytecode.constants.length; i++").end().startBlock();
        } else {
            b.startFor().string("int i = 0; i < bytecode.constants.length; i++").end().startBlock();
        }
        b.startStatement().startCall("context", "addObject");
        emitNormalizedConstantPoolValue(b, "bytecode.constants[i]");
        b.end(2);
        b.end();
        if (!parent.model.serializedFields.isEmpty()) {
            b.declaration(parent.model.getTemplateType().asType(), "fieldsRoot", "existingNodes != null ? existingNodes.getNode(root.buildIndex) : root");
            for (VariableElement field : parent.model.serializedFields) {
                b.startStatement().startCall("context", "addObject").field("fieldsRoot", field).end(2);
            }
        }
        b.end();
        return method;
    }

    private CodeExecutableElement createNormalizeConstantPoolValue() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(Object.class), "normalizeConstantPoolValue");
        method.addParameter(new CodeVariableElement(type(Object.class), "constant"));
        method.addParameter(new CodeVariableElement(parent.asType(), "root"));
        method.addParameter(new CodeVariableElement(parent.abstractBytecodeNode.asType(), "bytecode"));
        CodeTreeBuilder b = method.createBuilder();
        b.startIf().string("constant instanceof ").type(parent.continuationRootNodeImpl.asType()).string(" continuation").end().startBlock();
        b.startAssert().string("continuation.root == root : ").doubleQuote("Continuation belongs to another root.").end();
        b.declaration(types.BytecodeLocation, "location", "continuation.location");
        b.startAssert().string("location == null || location.getBytecodeNode() == bytecode : ").doubleQuote("Continuation location belongs to another bytecode node.").end();
        b.startReturn().startNew(continuationData.asType()).string("location != null ? location.getBytecodeIndex() : -1").string("continuation.sp").end(2);
        b.end();
        b.startReturn().string("constant").end();
        return method;
    }

    private void emitNormalizedConstantPoolValue(CodeTreeBuilder b, String constant) {
        if (parent.model.hasYieldOperation()) {
            b.startCall("normalizeConstantPoolValue").string(constant).string("root").string("bytecode").end();
        } else {
            b.string(constant);
        }
    }

    private CodeExecutableElement createWriteRootConstants() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeRootConstants");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.asType(), "root"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));
        CodeTreeBuilder b = method.createBuilder();
        b.declaration(parent.abstractBytecodeNode.asType(), "bytecode", "root.bytecode");
        if (parent.model.enableInstructionTracing) {
            b.declaration(type(int.class), "constantOffset", "bytecode.isInstructionTracingEnabled() ? 1 : 0");
            b.startAssert().string("constantOffset == 0 || bytecode.constants[0] instanceof ").type(parent.instructionTracerAccessImplElement.asType()).end();
            b.statement("buffer.writeInt(bytecode.constants.length - constantOffset)");
            b.startFor().string("int i = constantOffset; i < bytecode.constants.length; i++").end().startBlock();
        } else {
            b.statement("buffer.writeInt(bytecode.constants.length)");
            b.startFor().string("int i = 0; i < bytecode.constants.length; i++").end().startBlock();
        }
        b.startStatement().startCall("writeObjectReference").string("buffer");
        emitNormalizedConstantPoolValue(b, "bytecode.constants[i]");
        b.string("context").end(2);
        b.end();
        return method;
    }

    private CodeExecutableElement createReadRootConstants() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(type(Object.class)), "readRootConstants");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(long.class), "executionBytecodeEncoding"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));
        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "constantCount", "checkReadInt(buffer, 0)");
        if (parent.model.enableInstructionTracing) {
            b.declaration(arrayOf(type(Object.class)), "constants");
            b.declaration(type(int.class), "i", "0");
            b.startIf().string(parent.configEncoder.checkInstructionTracingBit("executionBytecodeEncoding")).end().startBlock();
            b.statement("constants = new Object[constantCount + 1]");
            b.startAssign("constants[i++]").startNew(parent.instructionTracerAccessImplElement.asType()).string("context.language").end(2);
            b.end().startElseBlock();
            b.statement("constants = new Object[constantCount]");
            b.end();
            b.startFor().string("; i < constants.length; i++").end().startBlock();
            b.statement("constants[i] = readObjectReference(buffer, context)");
            b.end();
        } else {
            b.declaration(arrayOf(type(Object.class)), "constants", "new Object[constantCount]");
            b.startFor().string("int i = 0; i < constantCount; i++").end().startBlock();
            b.statement("constants[i] = readObjectReference(buffer, context)");
            b.end();
        }
        b.startReturn().string("constants").end();
        return method;
    }

    private CodeExecutableElement createWriteConstantPool() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeConstantPool");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.builder.asType(), "builder"));
        method.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.lineComment("Collect constants first; we need to emit the count before the object encodings.");
        b.statement("collectConstants(builder, existingNodes, context)");
        b.statement("buffer.writeInt(context.constants.size())");
        b.startFor().string("Object constant : context.constants").end().startBlock();
        b.startIf().string("constant == null").end().startBlock();
        b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagNull).end(2);
        b.end().startElseIf().string("constant instanceof ").type(types.LocalAccessor).string(" accessor").end().startBlock();
        b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagLocalAccessor).end(2);
        b.statement("buffer.writeInt(ACCESS.getLocalOffset(accessor))");
        b.statement("buffer.writeInt(ACCESS.getLocalIndex(accessor))");
        b.end().startElseIf().string("constant instanceof ").type(types.LocalRangeAccessor).string(" accessor").end().startBlock();
        b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagLocalRangeAccessor).end(2);
        b.statement("buffer.writeInt(ACCESS.getStartOffset(accessor))");
        b.statement("buffer.writeInt(ACCESS.getStartIndex(accessor))");
        b.statement("buffer.writeInt(accessor.getLength())");
        b.end().startElseIf().string("constant instanceof ").type(types.MaterializedLocalAccessor).string(" accessor").end().startBlock();
        b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagMaterializedLocalAccessor).end(2);
        b.statement("buffer.writeInt(ACCESS.getRootIndex(accessor))");
        b.statement("buffer.writeInt(ACCESS.getLocalOffset(accessor))");
        b.statement("buffer.writeInt(ACCESS.getLocalIndex(accessor))");
        if (parent.model.hasYieldOperation()) {
            b.end().startElseIf().string("constant instanceof ").type(continuationData.asType()).string(" continuation").end().startBlock();
            b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagContinuation).end(2);
            b.statement("buffer.writeInt(continuation.resumeBci)");
            b.statement("buffer.writeInt(continuation.sp)");
        }
        b.end().startElseBlock();
        b.startStatement().startCall("buffer", "writeByte").staticReference(constantTagObject).end(2);
        b.statement("context.callback.serialize(context, buffer, constant)");
        b.end();
        b.end();
        return method;
    }

    private CodeExecutableElement createReadConstantPool() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "readConstantPool");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "constantCount", "checkReadInt(buffer, 0)");
        b.startAssign("context.objects");
        b.startNewArray(arrayOf(type(Object.class)), CodeTreeBuilder.singleString("constantCount")).end();
        b.end();
        b.startFor().string("int i = 0; i < constantCount; i++").end().startBlock();
        b.declaration(type(int.class), "tag", "buffer.readUnsignedByte()");
        b.startSwitch().string("tag").end().startBlock();
        b.startCase().staticReference(constantTagNull).end().startBlock();
        b.statement("break");
        b.end();
        b.startCase().staticReference(constantTagObject).end().startBlock();
        b.statement("context.objects[i] = context.callback.deserialize(context, buffer)");
        b.statement("break");
        b.end();
        b.startCase().staticReference(constantTagLocalAccessor).end().startBlock();
        emitReadLocalAccessorIndices(b);
        b.startAssign("context.objects[i]").startCall("ACCESS", "getLocalAccessorConstant").string("offset").string("index").end(2);
        b.statement("break");
        b.end();
        b.startCase().staticReference(constantTagLocalRangeAccessor).end().startBlock();
        emitReadLocalAccessorIndices(b);
        b.startAssign("context.objects[i]").startCall("ACCESS", "getLocalRangeAccessorConstant");
        b.string("offset").string("index").string("checkReadInt(buffer, 0)");
        b.end(2);
        b.statement("break");
        b.end();
        b.startCase().staticReference(constantTagMaterializedLocalAccessor).end().startBlock();
        b.declaration(type(int.class), "rootIndex", "checkReadIndex(buffer, context.roots.length, 0)");
        emitReadLocalAccessorIndices(b);
        b.startAssign("context.objects[i]").startCall("ACCESS", "getMaterializedLocalAccessorConstant");
        b.string("rootIndex").string("offset").string("index");
        b.end(2);
        b.statement("break");
        b.end();
        if (parent.model.hasYieldOperation()) {
            b.startCase().staticReference(constantTagContinuation).end().startBlock();
            b.startAssign("context.objects[i]").startNew(continuationData.asType()).string("buffer.readInt()").string("buffer.readInt()").end(2);
            b.statement("break");
            b.end();
        }
        b.caseDefault().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Unknown constant tag: \" + tag").end(2);
        b.end();
        b.end();
        b.end();
        return method;
    }

    private void emitReadLocalAccessorIndices(CodeTreeBuilder b) {
        b.declaration(type(int.class), "offset", "checkReadInt(buffer, 0)");
        b.declaration(type(int.class), "index", "checkReadInt(buffer, 0)");
        b.startIf().string("offset > index").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Invalid local accessor indices: \" + offset + \", \" + index").end(2);
        b.end();
    }

    private CodeExecutableElement createWriteObjectReference() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeObjectReference");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(Object.class), "object"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startDeclaration(type(Object.class), "key").startCall("ACCESS", "getSerializationKey").string("object").end(2);
        b.declaration(type(Integer.class), "index", "context.objects.get(key)");
        b.startAssert().string("index != null : ").doubleQuote("Object was not collected for serialization.").end();
        b.statement("buffer.writeInt(index)");
        return method;
    }

    private CodeExecutableElement createReadObjectReference() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(Object.class), "readObjectReference");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.startReturn().string("context.objects[checkReadIndex(buffer, context.objects.length, 0)]").end();
        return method;
    }

    private CodeExecutableElement createWriteSourceTable() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeSourceTable");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(generic(type(List.class), types.Source), "sources"));
        method.addParameter(new CodeVariableElement(executionBytecodeSerializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(sources.size())");
        b.startFor().type(types.Source).string(" source : sources").end().startBlock();
        b.statement("writeObjectReference(buffer, source, context)");
        b.end();
        return method;
    }

    private CodeExecutableElement createReadSourceTable() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), generic(type(List.class), types.Source), "readSourceTable");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(executionBytecodeDeserializationContext.asType(), "context"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "sourceCount", "checkReadInt(buffer, 0)");
        b.startDeclaration(generic(type(List.class), types.Source), "sources").startNew("ArrayList<>").string("sourceCount").end(2);
        b.startFor().string("int i = 0; i < sourceCount; i++").end().startBlock();
        b.startStatement().startCall("sources", "add");
        b.startGroup().cast(types.Source).string("readObjectReference(buffer, context)").end();
        b.end(2);
        b.end();
        b.startReturn().string("sources").end();
        return method;
    }

    private CodeExecutableElement createWriteTagTree() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeTagTree");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.tagRootNode.asType(), "tagRoot"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "nodeCount", "tagRoot != null ? tagRoot.tagNodes.length : 0");
        b.statement("buffer.writeInt(nodeCount)");
        b.startIf().string("nodeCount == 0").end().startBlock();
        b.statement("return");
        b.end();
        b.declaration(arrayOf(parent.tagNode.asType()), "tagNodes", "tagRoot.tagNodes");
        b.startDeclaration(generic(type(IdentityHashMap.class), parent.tagNode.asType(), type(Integer.class)), "indices").startNew("IdentityHashMap<>").end(2);
        b.startFor().string("int i = 0; i < nodeCount; i++").end().startBlock();
        b.statement("indices.put(tagNodes[i], i)");
        b.end();
        b.startFor().string("int i = nodeCount - 1; i >= 0; i--").end().startBlock();
        b.statement("writeTagRecord(buffer, tagNodes[i], indices, i + 1)");
        b.end();
        b.declaration(type(int.class), "rootIndex", "indices.getOrDefault(tagRoot.root, -1)");
        b.statement("buffer.writeInt(rootIndex)");
        b.startIf().string("rootIndex == -1").end().startBlock();
        b.statement("writeTagChildren(buffer, tagRoot.root.children, indices, 0)");
        b.end();
        return method;
    }

    private CodeExecutableElement createReadTagTree() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), parent.tagRootNode.asType(), "readTagTree");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "nodeCount", "checkReadInt(buffer, 0)");
        b.startIf().string("nodeCount == 0").end().startBlock();
        b.startReturn().string("null").end();
        b.end();
        b.startDeclaration(arrayOf(parent.tagNode.asType()), "tagNodes");
        b.startNewArray(arrayOf(parent.tagNode.asType()), CodeTreeBuilder.singleString("nodeCount")).end();
        b.end();
        b.lineComment("Nodes are serialized by decreasing index. This ensures children are constructed before parents.");
        b.startFor().string("int i = nodeCount - 1; i >= 0; i--").end().startBlock();
        b.statement("tagNodes[i] = readTagRecord(buffer, tagNodes)");
        b.end();
        b.declaration(type(int.class), "rootIndex", "checkReadIndex(buffer, tagNodes.length, -1)");
        b.declaration(parent.tagNode.asType(), "tree");
        b.startIf().string("rootIndex == -1").end().startBlock();
        b.startAssign("tree").startNew(parent.tagNode.asType()).string("0").string("-1").end(2);
        b.statement("tree.children = readTagChildren(buffer, tagNodes)");
        b.end().startElseBlock();
        b.statement("tree = tagNodes[rootIndex]");
        b.end();
        b.startReturn().startNew(parent.tagRootNode.asType()).string("tree").string("tagNodes").end(2);
        return method;
    }

    private CodeExecutableElement createWriteTagRecord() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeTagRecord");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(parent.tagNode.asType(), "node"));
        method.addParameter(new CodeVariableElement(generic(type(IdentityHashMap.class), parent.tagNode.asType(), type(Integer.class)), "indices"));
        method.addParameter(new CodeVariableElement(type(int.class), "minChildIndex"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(node.tags)");
        b.statement("buffer.writeInt(node.enterBci)");
        b.statement("buffer.writeInt(node.returnBci)");
        b.statement("writeTagChildren(buffer, node.children, indices, minChildIndex)");
        return method;
    }

    private CodeExecutableElement createReadTagRecord() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), parent.tagNode.asType(), "readTagRecord");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "tagNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "tags", "buffer.readInt()");
        b.declaration(type(int.class), "enterBci", "buffer.readInt()");
        b.startDeclaration(parent.tagNode.asType(), "node").startNew(parent.tagNode.asType()).string("tags").string("enterBci").end(2);
        b.statement("node.returnBci = buffer.readInt()");
        b.statement("node.children = readTagChildren(buffer, tagNodes)");
        b.startReturn().string("node").end();
        return method;
    }

    private CodeExecutableElement createWriteTagChildren() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeTagChildren");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "children"));
        method.addParameter(new CodeVariableElement(generic(type(IdentityHashMap.class), parent.tagNode.asType(), type(Integer.class)), "indices"));
        method.addParameter(new CodeVariableElement(type(int.class), "minChildIndex"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(children.length)");
        b.startFor().type(parent.tagNode.asType()).string(" child : children").end().startBlock();
        b.declaration(type(int.class), "childIndex", "indices.get(child)");
        b.startAssert().string("childIndex >= minChildIndex : ").doubleQuote("Child was not written before its parent.").end();
        b.statement("buffer.writeInt(childIndex)");
        b.end();
        return method;
    }

    private CodeExecutableElement createReadTagChildren() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), arrayOf(parent.tagNode.asType()), "readTagChildren");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(arrayOf(parent.tagNode.asType()), "tagNodes"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "childCount", "checkReadInt(buffer, 0)");
        b.startIf().string("childCount == 0").end().startBlock();
        b.startReturn().staticReference(parent.tagNode.asType(), "EMPTY_ARRAY").end();
        b.end();
        b.startDeclaration(arrayOf(parent.tagNode.asType()), "children");
        b.startNewArray(arrayOf(parent.tagNode.asType()), CodeTreeBuilder.singleString("childCount")).end();
        b.end();
        b.startFor().string("int i = 0; i < childCount; i++").end().startBlock();
        b.statement("children[i] = tagNodes[checkReadIndex(buffer, tagNodes.length, 0)]");
        b.startIf().string("children[i] == null").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).doubleQuote("Child was not read before its parent.").end(2);
        b.end();
        b.end();
        b.startReturn().string("children").end();
        return method;
    }

    private CodeExecutableElement createWriteByteArray() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeByteArray");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(byte[].class), "array"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(array != null ? array.length : -1)");
        b.startIf().string("array != null").end().startBlock();
        b.statement("buffer.write(array)");
        b.end();
        return method;
    }

    private CodeExecutableElement createReadByteArray() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(byte[].class), "readByteArray");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "length", "checkReadInt(buffer, -1)");
        b.startIf().string("length == -1").end().startBlock();
        b.startReturn().string("null").end();
        b.end();
        if (parent.model.enableCompressedSources) {
            b.startIf().string("length == 0").end().startBlock();
            b.startReturn().string(BytecodeRootNodeElement.EMPTY_BYTE_ARRAY).end();
            b.end();
        }
        b.startDeclaration(type(byte[].class), "array");
        b.startNewArray(arrayOf(type(byte.class)), CodeTreeBuilder.singleString("length")).end();
        b.end();
        b.statement("buffer.readFully(array)");
        b.startReturn().string("array").end();
        return method;
    }

    private CodeExecutableElement createWriteIntArray() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(void.class), "writeIntArray");
        method.addParameter(new CodeVariableElement(type(DataOutput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(int[].class), "array"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.statement("buffer.writeInt(array != null ? array.length : -1)");
        b.startIf().string("array != null").end().startBlock();
        b.startFor().string("int element : array").end().startBlock();
        b.statement("buffer.writeInt(element)");
        b.end();
        b.end();
        return method;
    }

    private CodeExecutableElement createReadIntArray() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int[].class), "readIntArray");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "length", "checkReadInt(buffer, -1)");
        b.startIf().string("length == -1").end().startBlock();
        b.startReturn().string("null").end();
        b.end();
        b.startIf().string("length == 0").end().startBlock();
        b.startReturn().string(BytecodeRootNodeElement.EMPTY_INT_ARRAY).end();
        b.end();
        b.startDeclaration(type(int[].class), "array");
        b.startNewArray(arrayOf(type(int.class)), CodeTreeBuilder.singleString("length")).end();
        b.end();
        b.startFor().string("int i = 0; i < length; i++").end().startBlock();
        b.statement("array[i] = buffer.readInt()");
        b.end();
        b.startReturn().string("array").end();
        return method;
    }

    private CodeExecutableElement createCheckReadInt() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "checkReadInt");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(int.class), "minimumValue"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "value", "buffer.readInt()");
        b.startIf().string("value < minimumValue").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Invalid integer value: \" + value").end(2);
        b.end();
        b.startReturn().string("value").end();
        return method;
    }

    private CodeExecutableElement createCheckReadIndex() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "checkReadIndex");
        method.addParameter(new CodeVariableElement(type(DataInput.class), "buffer"));
        method.addParameter(new CodeVariableElement(type(int.class), "size"));
        method.addParameter(new CodeVariableElement(type(int.class), "minimumValue"));
        method.addThrownType(type(IOException.class));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "index", "buffer.readInt()");
        b.startIf().string("index < minimumValue || index >= size").end().startBlock();
        b.startThrow().startNew(type(IOException.class)).string("\"Invalid reference index: \" + index").end(2);
        b.end();
        b.startReturn().string("index").end();
        return method;
    }

    private CodeTypeElement createExecutionBytecodeSerializationContext() {
        CodeTypeElement element = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ExecutionBytecodeSerializationContext");
        element.getImplements().add(types.BytecodeSerializer_SerializerContext);
        addField(element, Set.of(PRIVATE, FINAL), types.BytecodeSerializer, "callback");
        addField(element, Set.of(PRIVATE, FINAL), parent.bytecodeRootNodesImpl.asType(), "nodes");
        addField(element, Set.of(PRIVATE, FINAL), parent.bytecodeRootNodesImpl.asType(), "existingNodes");
        addField(element, Set.of(PRIVATE, FINAL), generic(HashMap.class, Object.class, Integer.class), "objects").createInitBuilder().startNew("HashMap<>").end();
        addField(element, Set.of(PRIVATE, FINAL), generic(ArrayList.class, Object.class), "constants").createInitBuilder().startNew("ArrayList<>").end();

        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, element.getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(types.BytecodeSerializer, "callback"));
        ctor.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "nodes"));
        ctor.addParameter(new CodeVariableElement(parent.bytecodeRootNodesImpl.asType(), "existingNodes"));
        CodeTreeBuilder b = ctor.createBuilder();
        b.statement("this.callback = callback");
        b.statement("this.nodes = nodes");
        b.statement("this.existingNodes = existingNodes");
        element.add(ctor);

        CodeExecutableElement addObject = new CodeExecutableElement(Set.of(PRIVATE), type(void.class), "addObject");
        addObject.addParameter(new CodeVariableElement(type(Object.class), "object"));
        b = addObject.createBuilder();
        b.startDeclaration(type(Object.class), "key").startCall("ACCESS", "getSerializationKey").string("object").end(2);
        b.startIf().string("!objects.containsKey(key)").end().startBlock();
        b.statement("objects.put(key, constants.size())");
        b.statement("constants.add(object)");
        b.end();
        element.add(addObject);

        CodeExecutableElement writeRoot = GeneratorUtils.override(types.BytecodeSerializer_SerializerContext, "writeBytecodeNode", new String[]{"buffer", "node"});
        b = writeRoot.createBuilder();
        b.startDeclaration(parent.asType(), "root").cast(parent.asType()).string("node").end();
        b.startAssert();
        b.string("root.nodes == nodes || (existingNodes != null && root.nodes == existingNodes) : ");
        b.doubleQuote("Root does not belong to the serialized parse.");
        b.end();
        b.statement("buffer.writeInt(root.buildIndex)");
        element.add(writeRoot);
        return element;
    }

    private CodeTypeElement createExecutionBytecodeDeserializationContext() {
        CodeTypeElement element = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ExecutionBytecodeDeserializationContext");
        element.getImplements().add(types.BytecodeDeserializer_DeserializerContext);
        addField(element, Set.of(PRIVATE, FINAL), types.BytecodeDeserializer, "callback");
        addField(element, Set.of(PRIVATE, FINAL), parent.model.languageClass, "language");
        addField(element, Set.of(PRIVATE, FINAL), arrayOf(parent.asType()), "roots");
        addField(element, Set.of(PRIVATE), arrayOf(type(Object.class)), "objects");

        CodeExecutableElement ctor = new CodeExecutableElement(Set.of(PRIVATE), null, element.getSimpleName().toString());
        ctor.addParameter(new CodeVariableElement(types.BytecodeDeserializer, "callback"));
        ctor.addParameter(new CodeVariableElement(parent.model.languageClass, "language"));
        ctor.addParameter(new CodeVariableElement(arrayOf(parent.asType()), "roots"));
        CodeTreeBuilder b = ctor.createBuilder();
        b.statement("this.callback = callback");
        b.statement("this.language = language");
        b.statement("this.roots = roots");
        element.add(ctor);

        CodeExecutableElement readRoot = GeneratorUtils.override(types.BytecodeDeserializer_DeserializerContext, "readBytecodeNode", new String[]{"buffer"});
        b = readRoot.createBuilder();
        b.statement("return roots[checkReadIndex(buffer, roots.length, 0)]");
        element.add(readRoot);
        return element;
    }

    private CodeTypeElement createContinuationData() {
        CodeTypeElement element = new CodeTypeElement(Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "ContinuationData");
        addField(element, Set.of(PRIVATE, FINAL), int.class, "resumeBci");
        addField(element, Set.of(PRIVATE, FINAL), int.class, "sp");
        element.add(createConstructorUsingFields(Set.of(PRIVATE), element, null));

        CodeExecutableElement equals = new CodeExecutableElement(Set.of(PUBLIC), type(boolean.class), "equals");
        equals.addParameter(new CodeVariableElement(type(Object.class), "other"));
        GeneratorUtils.addOverride(equals);
        CodeTreeBuilder b = equals.createBuilder();
        b.startReturn().string("other instanceof ContinuationData data && resumeBci == data.resumeBci && sp == data.sp").end();
        element.add(equals);

        CodeExecutableElement hashCode = new CodeExecutableElement(Set.of(PUBLIC), type(int.class), "hashCode");
        GeneratorUtils.addOverride(hashCode);
        b = hashCode.createBuilder();
        b.startReturn().startStaticCall(type(Objects.class), "hash").string("resumeBci").string("sp").end(2);
        element.add(hashCode);

        BytecodeRootNodeElement.addJavadoc(element, "Abstract continuation metadata, replaced with a continuation root when its bytecode node is available.");
        return element;
    }
}
