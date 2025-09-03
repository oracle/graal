/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.function.Consumer;

import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.HotSpotCompiledCodeBuilder;
import jdk.graal.compiler.lir.FullInfopointOp;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Value;

public class JVMCIInfopointErrorTest extends GraalCompilerTest {

    private static class ValueDef extends LIRInstruction {
        private static final LIRInstructionClass<ValueDef> TYPE = LIRInstructionClass.create(ValueDef.class);

        @Def({REG, STACK}) AllocatableValue value;

        ValueDef(AllocatableValue value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    private static class ValueUse extends LIRInstruction {
        private static final LIRInstructionClass<ValueUse> TYPE = LIRInstructionClass.create(ValueUse.class);

        @Use({REG, STACK}) AllocatableValue value;

        ValueUse(AllocatableValue value) {
            super(TYPE);
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    private static class TestNode extends DeoptimizingFixedWithNextNode implements LIRLowerable {
        private static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        private final TestSpec spec;

        protected TestNode(TestSpec spec) {
            super(TYPE, StampFactory.forVoid());
            this.spec = spec;
        }

        @Override
        public boolean canDeoptimize() {
            return true;
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            LIRGeneratorTool tool = gen.getLIRGeneratorTool();
            LIRFrameState state = gen.state(this);
            spec.spec(tool, state, st -> {
                tool.append(new FullInfopointOp(st, InfopointReason.SAFEPOINT));
            });
        }
    }

    @FunctionalInterface
    private interface TestSpec {
        void spec(LIRGeneratorTool tool, LIRFrameState state, Consumer<LIRFrameState> safepoint);
    }

    public static void testMethod() {
    }

    private void test(TestSpec spec) {
        test(getDebugContext(), spec);
    }

    /**
     * Avoids dumping during tests which are expected to fail.
     */
    private void testNoDump(TestSpec spec) {
        OptionValues options = new OptionValues(getInitialOptions(), DumpOnError, false);
        test(getDebugContext(options, null, null), spec);
    }

    private void test(DebugContext debug, TestSpec spec) {
        ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");

        StructuredGraph graph = parseForCompile(method, debug);
        TestNode test = graph.add(new TestNode(spec));
        graph.addAfterFixed(graph.start(), test);

        CompilationResult compResult = compile(method, graph);
        CodeCacheProvider codeCache = getCodeCache();
        HotSpotCompiledCode compiledCode = HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, method, null, compResult, getInitialOptions());
        codeCache.addCode(method, compiledCode, null, null, true);
    }

    @Test(expected = Error.class)
    public void testInvalidShortOop() {
        testNoDump((tool, state, safepoint) -> {
            PlatformKind kind = tool.target().arch.getPlatformKind(JavaKind.Short);
            LIRKind lirKind = LIRKind.reference(kind);

            Variable var = tool.newVariable(lirKind);
            tool.append(new ValueDef(var));
            safepoint.accept(state);
            tool.append(new ValueUse(var));
        });
    }

    @Test(expected = Error.class)
    public void testInvalidShortDerivedOop() {
        testNoDump((tool, state, safepoint) -> {
            Variable baseOop = tool.newVariable(LIRKind.fromJavaKind(tool.target().arch, JavaKind.Object));
            tool.append(new ValueDef(baseOop));

            PlatformKind kind = tool.target().arch.getPlatformKind(JavaKind.Short);
            LIRKind lirKind = LIRKind.derivedReference(kind, baseOop, false);

            Variable var = tool.newVariable(lirKind);
            tool.append(new ValueDef(var));
            safepoint.accept(state);
            tool.append(new ValueUse(var));
        });
    }

    private static LIRFrameState modifyTopFrame(LIRFrameState state, JavaValue[] values, JavaKind[] slotKinds, int locals, int stack, int locks) {
        return modifyTopFrame(state, null, values, slotKinds, locals, stack, locks);
    }

    private static LIRFrameState modifyTopFrame(LIRFrameState state, VirtualObject[] vobj, JavaValue[] values, JavaKind[] slotKinds, int locals, int stack, int locks) {
        BytecodeFrame top = state.topFrame;
        top = new BytecodeFrame(top.caller(), top.getMethod(), top.getBCI(), top.rethrowException, top.duringCall, values, slotKinds, locals, stack, locks);
        return new LIRFrameState(top, vobj, state.exceptionEdge, true);
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedScopeValuesLength() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.FALSE}, new JavaKind[0], 0, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedScopeSlotKindsLength() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[0], new JavaKind[]{JavaKind.Boolean}, 0, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testWrongMonitorType() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.INT_0}, new JavaKind[]{}, 0, 0, 1);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedIllegalValue() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{Value.ILLEGAL}, new JavaKind[]{JavaKind.Int}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedTypeInRegister() {
        test((tool, state, safepoint) -> {
            Variable var = tool.newVariable(LIRKind.fromJavaKind(tool.target().arch, JavaKind.Int));
            tool.append(new ValueDef(var));
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{var}, new JavaKind[]{JavaKind.Void}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testWrongConstantType() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.INT_0}, new JavaKind[]{JavaKind.Object}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnsupportedConstantType() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.forShort((short) 0)}, new JavaKind[]{JavaKind.Short}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedNull() {
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.NULL_POINTER}, new JavaKind[]{JavaKind.Int}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedObject() {
        JavaValue wrapped = getSnippetReflection().forObject(this);
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{wrapped}, new JavaKind[]{JavaKind.Int}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    private static final class UnknownJavaValue implements JavaValue {
    }

    @Test(expected = Error.class)
    public void testUnknownJavaValue() {
        DebugContext debug = new Builder(getInitialOptions()).build();
        try (Scope _ = debug.disable()) {
            /*
             * Expected: either AssertionError or GraalError, depending on whether the unit test run
             * is with assertions enabled or disabled.
             */
            test(debug, (_, state, safepoint) -> {
                LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{new UnknownJavaValue()}, new JavaKind[]{JavaKind.Int}, 1, 0, 0);
                safepoint.accept(newState);
            });
        }
    }

    @Test(expected = Error.class)
    public void testMissingIllegalAfterDouble() {
        /*
         * Expected: either AssertionError or GraalError, depending on whether the unit test run is
         * with assertions enabled or disabled.
         */
        test((_, state, safepoint) -> {
            LIRFrameState newState = modifyTopFrame(state, new JavaValue[]{JavaConstant.DOUBLE_0, JavaConstant.INT_0}, new JavaKind[]{JavaKind.Double, JavaKind.Int}, 2, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidVirtualObjectId() {
        ResolvedJavaType obj = getMetaAccess().lookupJavaType(Object.class);
        test((_, state, safepoint) -> {
            VirtualObject o = VirtualObject.get(obj, 5);
            o.setValues(new JavaValue[0], new JavaKind[0]);

            safepoint.accept(new LIRFrameState(state.topFrame, new VirtualObject[]{o}, state.exceptionEdge, true));
        });
    }

    @Test(expected = JVMCIError.class)
    public void testDuplicateVirtualObject() {
        ResolvedJavaType obj = getMetaAccess().lookupJavaType(Object.class);
        test((_, state, safepoint) -> {
            VirtualObject o1 = VirtualObject.get(obj, 0);
            o1.setValues(new JavaValue[0], new JavaKind[0]);

            VirtualObject o2 = VirtualObject.get(obj, 0);
            o2.setValues(new JavaValue[0], new JavaKind[0]);

            safepoint.accept(new LIRFrameState(state.topFrame, new VirtualObject[]{o1, o2}, state.exceptionEdge, true));
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUnexpectedVirtualObject() {
        ResolvedJavaType obj = getMetaAccess().lookupJavaType(Object.class);
        test((_, state, safepoint) -> {
            VirtualObject o = VirtualObject.get(obj, 0);
            o.setValues(new JavaValue[0], new JavaKind[0]);

            LIRFrameState newState = modifyTopFrame(state, new VirtualObject[]{o}, new JavaValue[]{o}, new JavaKind[]{JavaKind.Int}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }

    @Test(expected = JVMCIError.class)
    public void testUndefinedVirtualObject() {
        ResolvedJavaType obj = getMetaAccess().lookupJavaType(Object.class);
        test((_, state, safepoint) -> {
            VirtualObject o0 = VirtualObject.get(obj, 0);
            o0.setValues(new JavaValue[0], new JavaKind[0]);

            VirtualObject o1 = VirtualObject.get(obj, 1);
            o1.setValues(new JavaValue[0], new JavaKind[0]);

            LIRFrameState newState = modifyTopFrame(state, new VirtualObject[]{o0}, new JavaValue[]{o1}, new JavaKind[]{JavaKind.Object}, 1, 0, 0);
            safepoint.accept(newState);
        });
    }
}
