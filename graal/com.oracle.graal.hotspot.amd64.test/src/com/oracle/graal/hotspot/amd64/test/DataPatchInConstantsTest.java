/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.hotspot.amd64.test;

import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.meta.Constant;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import org.junit.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.nodes.CompressionNode.CompressionOp;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.hotspot.test.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.jvmci.amd64.*;
import com.oracle.jvmci.asm.amd64.*;
import com.oracle.jvmci.hotspot.*;

public class DataPatchInConstantsTest extends HotSpotGraalCompilerTest {

    @Before
    public void checkAMD64() {
        Assume.assumeTrue("skipping AMD64 specific test", getTarget().arch instanceof AMD64);
    }

    private static final Object object = new Object() {

        @Override
        public String toString() {
            return "testObject";
        }
    };

    private static Object loadThroughPatch(Object obj) {
        return obj;
    }

    public static Object oopSnippet() {
        Object patch = loadThroughPatch(object);
        if (object != patch) {
            return "invalid patch";
        }
        System.gc();
        patch = loadThroughPatch(object);
        if (object != patch) {
            return "failed after gc";
        }
        return patch;
    }

    @Test
    public void oopTest() {
        test("oopSnippet");
    }

    private static Object loadThroughCompressedPatch(Object obj) {
        return obj;
    }

    public static Object narrowOopSnippet() {
        Object patch = loadThroughCompressedPatch(object);
        if (object != patch) {
            return "invalid patch";
        }
        System.gc();
        patch = loadThroughCompressedPatch(object);
        if (object != patch) {
            return "failed after gc";
        }
        return patch;
    }

    @Test
    public void narrowOopTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", config.useCompressedOops);
        test("narrowOopSnippet");
    }

    public static Object compareSnippet() {
        Object uncompressed = loadThroughPatch(object);
        Object compressed = loadThroughCompressedPatch(object);
        if (object != uncompressed) {
            return "uncompressed failed";
        }
        if (object != compressed) {
            return "compressed failed";
        }
        if (uncompressed != compressed) {
            return "uncompressed != compressed";
        }
        return object;
    }

    @Test
    public void compareTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", config.useCompressedOops);
        test("compareSnippet");
    }

    private static final HotSpotVMConfig config = HotSpotGraalRuntime.runtime().getConfig();
    private static boolean initReplacements = false;

    @Before
    public void initReplacements() {
        if (!initReplacements) {
            getReplacements().registerSubstitutions(DataPatchInConstantsTest.class, DataPatchInConstantsTestSubstitutions.class);
            initReplacements = true;
        }
    }

    @ClassSubstitution(DataPatchInConstantsTest.class)
    private static class DataPatchInConstantsTestSubstitutions {

        @MethodSubstitution
        public static Object loadThroughPatch(Object obj) {
            return LoadThroughPatchNode.load(obj);
        }

        @MethodSubstitution
        public static Object loadThroughCompressedPatch(Object obj) {
            Object compressed = CompressionNode.compression(CompressionOp.Compress, obj, config.getOopEncoding());
            Object patch = LoadThroughPatchNode.load(compressed);
            return CompressionNode.compression(CompressionOp.Uncompress, patch, config.getOopEncoding());
        }
    }

    @NodeInfo
    private static final class LoadThroughPatchNode extends FixedWithNextNode implements LIRLowerable {
        public static final NodeClass<LoadThroughPatchNode> TYPE = NodeClass.create(LoadThroughPatchNode.class);

        @Input protected ValueNode input;

        public LoadThroughPatchNode(ValueNode input) {
            super(TYPE, input.stamp());
            this.input = input;
        }

        public void generate(NodeLIRBuilderTool generator) {
            assert input.isConstant();

            LIRGeneratorTool gen = generator.getLIRGeneratorTool();
            Variable ret = gen.newVariable(gen.getLIRKind(stamp()));

            gen.append(new LoadThroughPatchOp(input.asConstant(), stamp() instanceof NarrowOopStamp, ret));
            generator.setResult(this, ret);
        }

        @NodeIntrinsic
        public static native Object load(Object obj);
    }

    private static final class LoadThroughPatchOp extends LIRInstruction {
        public static final LIRInstructionClass<LoadThroughPatchOp> TYPE = LIRInstructionClass.create(LoadThroughPatchOp.class);

        final Constant c;
        final boolean compressed;
        @Def({REG}) AllocatableValue result;

        LoadThroughPatchOp(Constant c, boolean compressed, AllocatableValue result) {
            super(TYPE);
            this.c = c;
            this.compressed = compressed;
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(c, compressed ? 4 : 8);
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            if (compressed) {
                asm.movl(asRegister(result), address);
            } else {
                asm.movq(asRegister(result), address);
            }
        }
    }
}
