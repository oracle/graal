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

import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.Set;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

import com.oracle.truffle.dsl.processor.SuppressFBWarnings;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeConfigEncoding;
import com.oracle.truffle.dsl.processor.bytecode.model.CustomOperationModel;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;

final class BytecodeConfigEncoderImplElement extends AbstractElement {

    final BytecodeConfigEncoding encoding;

    BytecodeConfigEncoderImplElement(BytecodeRootNodeElement parent) {
        super(parent, Set.of(PRIVATE, STATIC, FINAL), ElementKind.CLASS, null, "BytecodeConfigEncoderImpl");
        this.encoding = parent.model.bytecodeConfigEncoding;
        this.setSuperClass(types.BytecodeConfigEncoder);
        BytecodeRootNodeElement.addJavadoc(this, "Encoding: " + this.encoding.description());

        CodeExecutableElement constructor = this.add(new CodeExecutableElement(Set.of(), null, this.getSimpleName().toString()));
        CodeTreeBuilder b = constructor.createBuilder();
        b.startStatement().startSuperCall().staticReference(parent.bytecodeRootNodesImpl.asType(), "VISIBLE_TOKEN").end().end();

        CodeVariableElement configEncoderVar = this.add(new CodeVariableElement(Set.of(PRIVATE, STATIC, FINAL), this.asType(), "INSTANCE"));
        configEncoderVar.createInitBuilder().startNew(this.asType()).end();

        this.add(createEncodeInstrumentation());
        this.add(createDecode1());
        this.add(createDecode2());
        this.add(createEncodeTags());
        this.add(createEncodeTag());
    }

    private CodeExecutableElement createDecode1() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.STATIC), type(long.class), "decode");
        ex.addParameter(new CodeVariableElement(types.BytecodeConfig, "config"));
        CodeTreeBuilder b = ex.createBuilder();
        b.startReturn();
        b.startCall("decode").string("getEncoder(config)").string("getEncoding(config)").end();
        b.end();
        return ex;
    }

    @SuppressFBWarnings(value = "BSHIFT_WRONG_ADD_PRIORITY", justification = "the shift priority is expected. FindBugs false positive.")
    private CodeExecutableElement createDecode2() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, Modifier.STATIC), type(long.class), "decode");
        ex.addParameter(new CodeVariableElement(types.BytecodeConfigEncoder, "encoder"));
        ex.addParameter(new CodeVariableElement(type(long.class), "encoding"));
        CodeTreeBuilder b = ex.createBuilder();

        b.startIf().string("encoder != null && encoder != ").staticReference(this.asType(), "INSTANCE").end().startBlock();
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        b.startThrow().startNew(type(IllegalArgumentException.class)).doubleQuote("Encoded config is not compatible with this bytecode node.").end().end();
        b.end();

        b.startReturn().string("(encoding & 0x" + Long.toHexString(encoding.completeBitsMask()) + "L)").end();
        return ex;
    }

    private CodeExecutableElement createEncodeInstrumentation() {
        CodeExecutableElement encodeInstrumentation = GeneratorUtils.override(types.BytecodeConfigEncoder, "encodeInstrumentation", new String[]{"c"});
        CodeTreeBuilder b = encodeInstrumentation.createBuilder();

        if (parent.model.hasInstrumentations()) {
            b.declaration("long", "encoding", "0L");
            boolean elseIf = b.startIf(false);
            b.string("c == ").typeLiteral(types.InstructionTracer);
            b.end().startBlock();
            if (parent.model.enableInstructionTracing) {
                b.statement("encoding |= 0x" + Integer.toHexString(encoding.traceInstructionMask()));
            } else {
                b.lineComment("Instruction tracing disabled");
            }
            b.end();
            for (CustomOperationModel customOperation : parent.model.getInstrumentations()) {
                elseIf = b.startIf(elseIf);
                b.string("c == ").typeLiteral(customOperation.operation.instruction.nodeType.asType());
                b.end().startBlock();
                b.statement("encoding |= 0x" + Integer.toHexString(encoding.instrumentationMask(customOperation.operation)));
                b.end();
            }
            b.startElseBlock();
        }
        b.startThrow().startNew(type(IllegalArgumentException.class)).startCall("String.format").doubleQuote(
                        "Invalid instrumentation specified. Instrumentation '%s' does not exist or is not an instrumentation for '" + ElementUtils.getQualifiedName(parent.model.templateType) + "'. " +
                                        "Instrumentations can be specified using the @Instrumentation annotation.").string("c.getName()").end().end().end();
        if (parent.model.hasInstrumentations()) {
            b.end(); // else
            b.startReturn().string("encoding << ").string(encoding.instrumentationShift()).end();
        }
        return encodeInstrumentation;
    }

    private CodeExecutableElement createEncodeTag() {
        CodeExecutableElement encodeTag = GeneratorUtils.override(types.BytecodeConfigEncoder, "encodeTag", new String[]{"c"});
        CodeTreeBuilder b = encodeTag.createBuilder();

        if (parent.model.getProvidedTags().isEmpty()) {
            parent.createFailInvalidTag(b, "c");
        } else {
            b.startReturn().string("((long) CLASS_TO_TAG_MASK.get(c)) << " + encoding.tagShift()).end().build();
        }
        return encodeTag;
    }

    private CodeExecutableElement createEncodeTags() {
        CodeExecutableElement ex = new CodeExecutableElement(Set.of(PRIVATE, STATIC), type(int.class), "encodeTags");
        ex.addParameter(new CodeVariableElement(arrayOf(type(Class.class)), "tags"));
        ex.setVarArgs(true);
        CodeTreeBuilder b = ex.createBuilder();
        b.startIf().string("tags == null").end().startBlock();
        b.statement("return 0");
        b.end();

        if (parent.model.getProvidedTags().isEmpty()) {
            b.startIf().string("tags.length != 0").end().startBlock();
            parent.createFailInvalidTag(b, "tags[0]");
            b.end();
            b.startReturn().string("0").end();
        } else {
            b.statement("int tagMask = 0");
            b.startFor().string("Class<?> tag : tags").end().startBlock();
            b.statement("tagMask |= CLASS_TO_TAG_MASK.get(tag)");
            b.end();
            b.startReturn().string("tagMask").end();
        }

        return ex;
    }

    public String checkSourceBit(String encoded) {
        return String.format("(%s & 0x%s) != 0", encoded, Long.toHexString(encoding.sourceMask()));
    }

    public String checkSourceContentBit(String encoded) {
        if (parent.model.sourceContentSupplier == null) {
            throw new AssertionError("Tried to generate code to check the source content bit, but no source content supplier is present.");
        }
        return String.format("(%s & 0x%s) != 0", encoded, Long.toHexString(encoding.sourceContentMask()));
    }

    public String decodeSourceBits(String encoded) {
        return String.format("(int) (%s & 0x%s)", encoded, Long.toHexString(encoding.sourceBitsMask()));
    }

    public String decodeInstrumentations(String encoded) {
        return String.format("(int) ((%s >> %s) & 0x%s)", encoded, encoding.instrumentationShift(), Long.toHexString(encoding.instrumentationMask()));
    }

    public String decodeTags(String encoded) {
        return String.format("(int) ((%s >> %s) & 0x%s)", encoded, encoding.tagShift(), Long.toHexString(encoding.tagMask()));
    }

    public String checkHasYieldsBit(String encoded) {
        if (!parent.model.hasYieldOperation()) {
            throw new AssertionError("Tried to generate code to check the has-yields bit, but the interpreter has no yield operations.");
        }
        return String.format("(%s & 0x%sL) != 0", encoded, Long.toHexString(encoding.hasYieldsMask()));
    }

    public String compareHasYieldsBit(String encoded1, String encoded2) {
        if (!parent.model.hasYieldOperation()) {
            throw new AssertionError("Tried to generate code to compare the has-yields bit, but the interpreter has no yield operations.");
        }
        return String.format("(%s & 0x%sL) == (%s & 0x%sL)", encoded1, Long.toHexString(encoding.hasYieldsMask()), encoded2, Long.toHexString(encoding.hasYieldsMask()));
    }

    public String checkInstructionTracingEnabled(String instrumentations) {
        if (!parent.model.enableInstructionTracing) {
            throw new AssertionError("Tried to generate code to check instruction tracing bit, but instruction tracing is not enabled.");
        }
        return String.format("(%s & 0x%s) != 0", instrumentations, Integer.toHexString(encoding.traceInstructionMask()));
    }

    public String checkInstrumentationEnabled(String instrumentations, OperationModel instrumentation) {
        return checkInstrumentation(instrumentations, instrumentation, true);
    }

    public String checkInstrumentationDisabled(String instrumentations, OperationModel instrumentation) {
        return checkInstrumentation(instrumentations, instrumentation, false);
    }

    private String checkInstrumentation(String instrumentations, OperationModel instrumentation, boolean enabled) {
        int mask = encoding.instrumentationMask(instrumentation);
        String comparator = enabled ? "!=" : "==";
        return String.format("(%s & 0x%s) %s 0", instrumentations, Integer.toHexString(mask), comparator);
    }

    public String checkTagEnabled(String tags, int tagIndex) {
        return String.format("(%s & 0x%s) != 0", tags, Integer.toHexString(encoding.tagMask(tagIndex)));
    }

    public CodeTree encode(String sourceEnabled, String sourceContentEnabled, String instrumentations, String tags, String hasYields) {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        b.startParantheses().string(sourceEnabled).string(" ? 0x").string(Long.toHexString(encoding.sourceMask())).string("L : 0L").end();
        if (parent.model.sourceContentSupplier != null) {
            b.string(" | ");
            b.startParantheses().string(sourceContentEnabled).string(" ? 0x").string(Long.toHexString(encoding.sourceContentMask())).string("L : 0L").end();
        }
        b.string(" | ");
        b.startParantheses();
        b.startParantheses().string(instrumentations).string(" & 0x").string(Long.toHexString(encoding.instrumentationMask())).string("L").end();
        b.string(" << ").string(String.valueOf(encoding.instrumentationShift()));
        b.end();
        b.string(" | ");
        b.startParantheses();
        b.startParantheses().string(tags).string(" & 0x").string(Long.toHexString(encoding.tagMask())).string("L").end();
        b.string(" << ").string(String.valueOf(encoding.tagShift()));
        b.end();
        if (parent.model.hasYieldOperation()) {
            if (hasYields == null) {
                throw new AssertionError("Tried to generate code to encode the has-yields bit, but no expression was provided.");
            }
            b.string(" | ");
            b.startParantheses().string(hasYields).string(" ? 0x").string(Long.toHexString(encoding.hasYieldsMask())).string("L : 0L").end();
        }
        return b.build();
    }
}
