/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.jtt;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.UNINITIALIZED;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.FullInfopointOp;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.framemap.FrameMapBuilderTool;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlot;
import jdk.graal.compiler.lir.framemap.SimpleVirtualStackSlotAlias;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.stackslotalloc.GraphColoringStackSlotAllocator;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.Location;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaValue;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * Generates small LIR programs with virtual stack slots and checks their physical locations after
 * the graph-coloring allocator has rewritten the LIR. The instructions below only describe operand
 * lifetimes; they emit no machine code. Each {@link LIRTestSpecification} is inserted through
 * {@link #slots(LIRTestSpecification)}, and the tests inspect its rewritten operands after compiling.
 */
public class GraphColoringStackSlotAllocatorTest extends LIRTest {

    /** An instruction with stack-slot definitions and uses, retained to inspect rewritten operands. */
    private static final class SlotOp extends LIRInstruction {
        private static final LIRInstructionClass<SlotOp> TYPE = LIRInstructionClass.create(SlotOp.class);

        @Def({STACK}) private Value[] defs;
        @Use({STACK}) private Value[] uses;
        @UseKill({STACK}) private Value[] useKills;

        SlotOp(Value[] defs, Value[] uses, Value[] useKills) {
            super(TYPE);
            this.defs = defs;
            this.uses = uses;
            this.useKills = useKills;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }

        StackSlot location() {
            Value value = defs.length != 0 ? defs[0] : uses.length != 0 ? uses[0] : useKills[0];
            Assert.assertTrue(value.toString(), value instanceof StackSlot);
            return (StackSlot) value;
        }
    }

    /** An uninitialized use reserves its slot even though it does not require a prior definition. */
    private static final class UninitializedOp extends LIRInstruction {
        private static final LIRInstructionClass<UninitializedOp> TYPE = LIRInstructionClass.create(UninitializedOp.class);

        @Use({STACK, UNINITIALIZED}) private Value slot;

        UninitializedOp(Value slot) {
            super(TYPE);
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }
    }

    /** Holds a stack memory range so its allocated offset can be checked. */
    private static final class RangeOp extends LIRInstruction {
        private static final LIRInstructionClass<RangeOp> TYPE = LIRInstructionClass.create(RangeOp.class);

        @Temp({STACK}) private Value slot;

        RangeOp(Value slot) {
            super(TYPE);
            this.slot = slot;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }

        StackSlot location() {
            Assert.assertTrue(slot.toString(), slot instanceof StackSlot);
            return (StackSlot) slot;
        }
    }

    /** Checks that an alive operand and a temporary in the same instruction do not share a slot. */
    private static final class AliveTempOp extends LIRInstruction {
        private static final LIRInstructionClass<AliveTempOp> TYPE = LIRInstructionClass.create(AliveTempOp.class);

        @Alive({STACK}) private Value alive;
        @Temp({STACK}) private Value temp;

        AliveTempOp(Value alive, Value temp) {
            super(TYPE);
            this.alive = alive;
            this.temp = temp;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }

        void checkDistinct() {
            Assert.assertTrue(alive instanceof StackSlot);
            Assert.assertTrue(temp instanceof StackSlot);
            Assert.assertNotEquals(((StackSlot) alive).getRawOffset(), ((StackSlot) temp).getRawOffset());
        }
    }

    /** Holds a slot in a frame state to check that state values are rewritten too. */
    private static final class StateOp extends LIRInstruction {
        private static final LIRInstructionClass<StateOp> TYPE = LIRInstructionClass.create(StateOp.class);

        @State private LIRFrameState state;

        StateOp(VirtualStackSlot slot, ResolvedJavaMethod method) {
            super(TYPE);
            state = frameState(slot, method, JavaKind.Long);
        }

        static LIRFrameState frameState(VirtualStackSlot slot, ResolvedJavaMethod method, JavaKind kind) {
            BytecodeFrame frame = new BytecodeFrame(null, method, BytecodeFrame.UNKNOWN_BCI, false, false, new JavaValue[]{slot}, new JavaKind[]{kind}, 1, 0, 0);
            return new LIRFrameState(frame, null, null, false);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
        }

        StackSlot location() {
            JavaValue value = state.topFrame.values[0];
            Assert.assertTrue(value.toString(), value instanceof StackSlot);
            return (StackSlot) value;
        }
    }

    /** Two-slot lifetime arrangements used by {@link #testInstructionLevel()}. */
    private enum Pattern {
        /** One slot dies before the other is defined, so they can share a location. */
        DISJOINT,
        /** Both slots are live together and need distinct locations. */
        OVERLAPPING,
        /** The first slot is redefined after the second dies, leaving a reusable gap. */
        HOLE,
        /** A use-kill of the first slot coincides with the second slot's definition. */
        USE_KILL,
        /** An uninitialized use of the first slot prevents reuse. */
        UNINITIALIZED,
        /** An alias of the first slot keeps its location but has a different value kind. */
        ALIAS,
        /**
         * Different-sized slots are allocated separately regardless of liveness, so the disjoint
         * case also covers the outcome for overlapping slots.
         */
        DIFFERENT_SIZE,
        /** A disjoint reference and primitive slot can share a location. */
        REFERENCE,
        /** A reference and primitive slot that overlap need distinct locations. */
        REFERENCE_OVERLAPPING,
        /** A frame state keeps the first slot live while the second is defined. */
        FRAME_STATE,
        /** Overlapping slots with different use counts check placement by frequency. */
        FREQUENCY
    }

    /** Generates a pair of virtual slots and records operands to compare their allocated offsets. */
    private static final class SlotSpec extends LIRTestSpecification {
        private final Pattern pattern;
        private SlotOp first;
        private SlotOp second;
        private StateOp state;
        private ResolvedJavaMethod stateMethod;

        SlotSpec(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder builder = gen.getResult().getFrameMapBuilder();
            LIRKind kind = LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long));
            VirtualStackSlot a = builder.allocateSpillSlot(kind);
            LIRKind secondKind = switch (pattern) {
                case DIFFERENT_SIZE -> LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Int));
                case REFERENCE, REFERENCE_OVERLAPPING -> LIRKind.reference(gen.target().arch.getPlatformKind(JavaKind.Object));
                default -> kind;
            };
            VirtualStackSlot b = builder.allocateSpillSlot(secondKind);
            first = gen.append(def(a));
            // The same disjoint lifetime sequence also tests size and reference-kind compatibility.
            switch (pattern) {
                case DISJOINT, DIFFERENT_SIZE, REFERENCE -> {
                    gen.append(use(a));
                    second = gen.append(def(b));
                    gen.append(use(b));
                }
                case OVERLAPPING, REFERENCE_OVERLAPPING -> {
                    second = gen.append(def(b));
                    gen.append(use(a, b));
                }
                case HOLE -> {
                    gen.append(use(a));
                    second = gen.append(def(b));
                    gen.append(use(b));
                    gen.append(def(a));
                    gen.append(use(a));
                }
                case USE_KILL -> {
                    second = gen.append(new SlotOp(new Value[]{b}, new Value[0], new Value[]{a}));
                    gen.append(use(b));
                }
                case UNINITIALIZED -> {
                    gen.append(new UninitializedOp(a));
                    second = gen.append(def(b));
                    gen.append(use(b));
                }
                case ALIAS -> {
                    SimpleVirtualStackSlotAlias alias = new SimpleVirtualStackSlotAlias(LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Int)), (SimpleVirtualStackSlot) a);
                    second = gen.append(use(alias));
                }
                case FRAME_STATE -> {
                    second = gen.append(def(b));
                    state = gen.append(new StateOp(a, stateMethod));
                    gen.append(use(b));
                }
                case FREQUENCY -> {
                    second = gen.append(def(b));
                    gen.append(use(a, b));
                    gen.append(use(b));
                    gen.append(use(b));
                }
            }
        }

        private static SlotOp def(Value value) {
            return new SlotOp(new Value[]{value}, new Value[0], new Value[0]);
        }

        private static SlotOp use(Value... values) {
            return new SlotOp(new Value[0], values, new Value[0]);
        }
    }

    /** Generates one slot or range for the control-flow and range checks. */
    private static final class SingleSlotSpec extends LIRTestSpecification {
        private final boolean range;
        private SlotOp definition;
        private SlotOp use;
        private RangeOp rangeOp;

        SingleSlotSpec(boolean range) {
            this.range = range;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder builder = gen.getResult().getFrameMapBuilder();
            VirtualStackSlot slot = range ? builder.allocateStackMemory(16, 8) : builder.allocateSpillSlot(LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long)));
            if (range) {
                rangeOp = gen.append(new RangeOp(slot));
                return;
            }
            definition = gen.append(new SlotOp(new Value[]{slot}, new Value[0], new Value[0]));
            use = gen.append(new SlotOp(new Value[0], new Value[]{slot}, new Value[0]));
        }
    }

    /** Shares two virtual slots among LIR instructions placed in different control-flow blocks. */
    private static final class InterBlockSlots {
        private enum Action {
            DEFINE_A,
            USE_A,
            DEFINE_B,
            USE_B
        }

        private static final class ActionSpec extends LIRTestSpecification {
            private final InterBlockSlots owner;
            private final Action action;

            ActionSpec(InterBlockSlots owner, Action action) {
                this.owner = owner;
                this.action = action;
            }

            @Override
            public void generate(LIRGeneratorTool gen) {
                owner.generate(gen, action);
            }
        }

        final LIRTestSpecification defineA = new ActionSpec(this, Action.DEFINE_A);
        final LIRTestSpecification useA = new ActionSpec(this, Action.USE_A);
        final LIRTestSpecification defineB = new ActionSpec(this, Action.DEFINE_B);
        final LIRTestSpecification useB = new ActionSpec(this, Action.USE_B);
        private VirtualStackSlot a;
        private VirtualStackSlot b;
        private SlotOp definitionA;
        private SlotOp useOfA;
        private SlotOp definitionB;
        private SlotOp useOfB;
        private BasicBlock<?> definitionABlock;
        private BasicBlock<?> definitionBBlock;
        private BasicBlock<?> useOfABlock;

        void reset() {
            a = null;
            b = null;
            definitionA = null;
            useOfA = null;
            definitionB = null;
            useOfB = null;
            definitionABlock = null;
            definitionBBlock = null;
            useOfABlock = null;
        }

        private void generate(LIRGeneratorTool gen, Action action) {
            if (a == null) {
                FrameMapBuilder builder = gen.getResult().getFrameMapBuilder();
                LIRKind kind = LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long));
                a = builder.allocateSpillSlot(kind);
                b = builder.allocateSpillSlot(kind);
            }
            switch (action) {
                case DEFINE_A -> {
                    definitionABlock = gen.getCurrentBlock();
                    definitionA = gen.append(SlotSpec.def(a));
                }
                case USE_A -> {
                    useOfABlock = gen.getCurrentBlock();
                    useOfA = gen.append(SlotSpec.use(a));
                }
                case DEFINE_B -> {
                    definitionBBlock = gen.getCurrentBlock();
                    definitionB = gen.append(SlotSpec.def(b));
                }
                case USE_B -> useOfB = gen.append(SlotSpec.use(b));
            }
        }

        void checkInterference() {
            Assert.assertNotNull(definitionA);
            Assert.assertNotNull(useOfA);
            Assert.assertNotNull(definitionB);
            Assert.assertNotNull(useOfB);
            Assert.assertEquals(definitionA.location().getRawOffset(), useOfA.location().getRawOffset());
            Assert.assertEquals(definitionB.location().getRawOffset(), useOfB.location().getRawOffset());
            Assert.assertNotEquals(definitionA.location().getRawOffset(), definitionB.location().getRawOffset());
            Assert.assertNotSame("The definitions must be in different LIR blocks", definitionABlock, definitionBBlock);
        }

        void checkLoopCarried() {
            checkInterference();
            Assert.assertSame("A is used before B in the loop body", useOfABlock, definitionBBlock);
        }

        void checkSuccessor() {
            checkInterference();
            Assert.assertNotSame("B is in the loop body and A is used after the loop", definitionBBlock, useOfABlock);
            for (int i = 0; i < definitionBBlock.getSuccessorCount(); i++) {
                Assert.assertNotSame("A must remain live across at least two block edges", useOfABlock, definitionBBlock.getSuccessorAt(i));
            }
        }
    }

    /** Checks final frame offsets when reference and primitive slots coexist, optionally with a range. */
    private static final class ReferencePlacementSpec extends LIRTestSpecification {
        private final boolean withRange;
        private FrameMap frameMap;
        private SlotOp primitive;
        private SlotOp reference;
        private RangeOp range;

        ReferencePlacementSpec(boolean withRange) {
            this.withRange = withRange;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilderTool builder = (FrameMapBuilderTool) gen.getResult().getFrameMapBuilder();
            frameMap = builder.getFrameMap();
            VirtualStackSlot primitiveSlot = builder.allocateSpillSlot(LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long)));
            VirtualStackSlot referenceSlot = builder.allocateSpillSlot(LIRKind.reference(gen.target().arch.getPlatformKind(JavaKind.Object)));
            primitive = gen.append(SlotSpec.def(primitiveSlot));
            reference = gen.append(SlotSpec.def(referenceSlot));
            gen.append(SlotSpec.use(primitiveSlot, referenceSlot));
            if (withRange) {
                range = gen.append(new RangeOp(builder.allocateStackMemory(1024, 8)));
            }
        }

        void check() {
            int frameSize = frameMap.totalFrameSize();
            int referenceOffset = reference.location().getOffset(frameSize);
            Assert.assertTrue("Reference must be closer to the stack pointer than primitive spill", referenceOffset < primitive.location().getOffset(frameSize));
            if (withRange) {
                Assert.assertTrue("Reference must be closer to the stack pointer than stack range", referenceOffset < range.location().getOffset(frameSize));
            }
        }
    }

    /** Records frame states before and after a reference slot's color is reused by a primitive. */
    private static final class ReferenceMapSpec extends LIRTestSpecification {
        private ResolvedJavaMethod method;
        private FrameMap frameMap;
        private SlotOp reference;
        private SlotOp primitive;

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilderTool builder = (FrameMapBuilderTool) gen.getResult().getFrameMapBuilder();
            frameMap = builder.getFrameMap();
            VirtualStackSlot referenceSlot = builder.allocateSpillSlot(LIRKind.reference(gen.target().arch.getPlatformKind(JavaKind.Object)));
            VirtualStackSlot primitiveSlot = builder.allocateSpillSlot(LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long)));
            reference = gen.append(SlotSpec.def(referenceSlot));
            gen.append(new FullInfopointOp(StateOp.frameState(referenceSlot, method, JavaKind.Object), InfopointReason.SAFEPOINT));
            primitive = gen.append(SlotSpec.def(primitiveSlot));
            gen.append(new FullInfopointOp(StateOp.frameState(primitiveSlot, method, JavaKind.Long), InfopointReason.SAFEPOINT));
        }

        void check(CompilationResult result) {
            Assert.assertEquals("Disjoint reference and primitive slots should reuse a color", reference.location().getRawOffset(), primitive.location().getRawOffset());
            String expectedReferenceMap = "[" + Location.stack(reference.location().getOffset(frameMap.totalFrameSize())) + "]";
            int states = 0;
            for (Infopoint infopoint : result.getInfopoints()) {
                if (infopoint.reason == InfopointReason.SAFEPOINT && infopoint.debugInfo != null && infopoint.debugInfo.frame().getMethod().equals(method)) {
                    JavaKind kind = infopoint.debugInfo.frame().getLocalValueKind(0);
                    String actualReferenceMap = infopoint.debugInfo.getReferenceMap().toString();
                    if (kind == JavaKind.Object) {
                        Assert.assertEquals(expectedReferenceMap, actualReferenceMap);
                    } else {
                        Assert.assertEquals(JavaKind.Long, kind);
                        Assert.assertEquals("[]", actualReferenceMap);
                    }
                    states++;
                }
            }
            Assert.assertEquals("Expected reference and primitive frame states", 2, states);
        }
    }

    /** Generates the simultaneous alive and temporary operands checked by {@link AliveTempOp}. */
    private static final class AliveTempSpec extends LIRTestSpecification {
        private AliveTempOp op;

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder builder = gen.getResult().getFrameMapBuilder();
            LIRKind kind = LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long));
            VirtualStackSlot a = builder.allocateSpillSlot(kind);
            VirtualStackSlot b = builder.allocateSpillSlot(kind);
            gen.append(new SlotOp(new Value[]{a}, new Value[0], new Value[0]));
            op = gen.append(new AliveTempOp(a, b));
        }
    }

    /** Interference graphs with predictable slot-sharing requirements. */
    private enum GraphShape {
        CHAIN,
        STAR,
        UNINITIALIZED_HUB,
        CLIQUE
    }

    /** Generates larger graphs to check coloring across many slots. */
    private static final class GraphSpec extends LIRTestSpecification {
        private final GraphShape shape;
        private final int count;
        private SlotOp[] definitions;

        GraphSpec(GraphShape shape, int count) {
            this.shape = shape;
            this.count = count;
        }

        @Override
        public void generate(LIRGeneratorTool gen) {
            FrameMapBuilder builder = gen.getResult().getFrameMapBuilder();
            LIRKind kind = LIRKind.value(gen.target().arch.getPlatformKind(JavaKind.Long));
            VirtualStackSlot[] slots = new VirtualStackSlot[count];
            definitions = new SlotOp[count];
            for (int i = 0; i < count; i++) {
                slots[i] = builder.allocateSpillSlot(kind);
            }
            switch (shape) {
                case CHAIN -> {
                    definitions[0] = gen.append(SlotSpec.def(slots[0]));
                    for (int i = 1; i < count; i++) {
                        definitions[i] = gen.append(SlotSpec.def(slots[i]));
                        gen.append(SlotSpec.use(slots[i - 1]));
                    }
                    gen.append(SlotSpec.use(slots[count - 1]));
                }
                case STAR -> {
                    definitions[count - 1] = gen.append(SlotSpec.def(slots[count - 1]));
                    for (int i = 0; i < count - 1; i++) {
                        definitions[i] = gen.append(SlotSpec.def(slots[i]));
                        gen.append(SlotSpec.use(slots[i], slots[count - 1]));
                    }
                }
                case UNINITIALIZED_HUB -> {
                    // The last slot ID would create a large adjacency bitset in every leaf.
                    definitions[count - 1] = gen.append(SlotSpec.def(slots[count - 1]));
                    gen.append(new UninitializedOp(slots[count - 1]));
                    for (int i = 0; i < count - 1; i++) {
                        definitions[i] = gen.append(SlotSpec.def(slots[i]));
                        gen.append(SlotSpec.use(slots[i]));
                    }
                }
                case CLIQUE -> {
                    for (int i = 0; i < count; i++) {
                        definitions[i] = gen.append(SlotSpec.def(slots[i]));
                    }
                    Value[] allSlots = new Value[count];
                    System.arraycopy(slots, 0, allSlots, 0, count);
                    gen.append(SlotSpec.use(allSlots));
                }
            }
        }

        int offset(int index) {
            return definitions[index].location().getRawOffset();
        }
    }

    // Each specification retains its generated LIR operands for checks after compilation.
    private static final SlotSpec DISJOINT = new SlotSpec(Pattern.DISJOINT);
    private static final SlotSpec OVERLAPPING = new SlotSpec(Pattern.OVERLAPPING);
    private static final SlotSpec HOLE = new SlotSpec(Pattern.HOLE);
    private static final SlotSpec USE_KILL = new SlotSpec(Pattern.USE_KILL);
    private static final SlotSpec UNINITIALIZED_SPEC = new SlotSpec(Pattern.UNINITIALIZED);
    private static final SlotSpec ALIAS = new SlotSpec(Pattern.ALIAS);
    private static final SlotSpec DIFFERENT_SIZE = new SlotSpec(Pattern.DIFFERENT_SIZE);
    private static final SlotSpec REFERENCE = new SlotSpec(Pattern.REFERENCE);
    private static final SlotSpec REFERENCE_OVERLAPPING = new SlotSpec(Pattern.REFERENCE_OVERLAPPING);
    private static final SlotSpec FRAME_STATE = new SlotSpec(Pattern.FRAME_STATE);
    private static final SlotSpec FREQUENCY = new SlotSpec(Pattern.FREQUENCY);
    private static final SingleSlotSpec BRANCH_A = new SingleSlotSpec(false);
    private static final SingleSlotSpec BRANCH_B = new SingleSlotSpec(false);
    private static final SingleSlotSpec LOOP = new SingleSlotSpec(false);
    private static final InterBlockSlots LOOP_CARRIED = new InterBlockSlots();
    private static final InterBlockSlots LOOP_EXIT_LIVE = new InterBlockSlots();
    private static final SingleSlotSpec RANGE = new SingleSlotSpec(true);
    private static final ReferencePlacementSpec REFERENCE_PLACEMENT = new ReferencePlacementSpec(false);
    private static final ReferencePlacementSpec REFERENCE_PLACEMENT_WITH_RANGE = new ReferencePlacementSpec(true);
    private static final ReferenceMapSpec REFERENCE_MAP = new ReferenceMapSpec();
    private static final AliveTempSpec ALIVE_TEMP = new AliveTempSpec();
    private static final GraphSpec STRESS_CHAIN = new GraphSpec(GraphShape.CHAIN, 512);
    private static final GraphSpec STRESS_STAR = new GraphSpec(GraphShape.STAR, 512);
    private static final GraphSpec UNINITIALIZED_HUB = new GraphSpec(GraphShape.UNINITIALIZED_HUB, 64);
    private static final GraphSpec STRESS_CLIQUE = new GraphSpec(GraphShape.CLIQUE, 512);

    @LIRIntrinsic
    public static void slots(LIRTestSpecification spec) {
    }

    public static int disjoint() {
        slots(DISJOINT);
        return 1;
    }

    public static int overlapping() {
        slots(OVERLAPPING);
        return 2;
    }

    public static int hole() {
        slots(HOLE);
        return 3;
    }

    public static int useKill() {
        slots(USE_KILL);
        return 4;
    }

    public static int uninitialized() {
        slots(UNINITIALIZED_SPEC);
        return 5;
    }

    public static int alias() {
        slots(ALIAS);
        return 6;
    }

    public static int differentSize() {
        slots(DIFFERENT_SIZE);
        return 7;
    }

    public static int reference() {
        slots(REFERENCE);
        return 8;
    }

    public static int referenceOverlapping() {
        slots(REFERENCE_OVERLAPPING);
        return 14;
    }

    public static int frameState() {
        slots(FRAME_STATE);
        return 13;
    }

    public static int frequency() {
        slots(FREQUENCY);
        return 15;
    }

    public static int diamond(boolean left) {
        if (left) {
            slots(BRANCH_A);
        } else {
            slots(BRANCH_B);
        }
        return 9;
    }

    public static int loop(int count) {
        for (int i = 0; i < count; i++) {
            slots(LOOP);
        }
        return 10;
    }

    public static int loopCarried(int count) {
        slots(LOOP_CARRIED.defineA);
        for (int i = 0; i < count; i++) {
            slots(LOOP_CARRIED.useA);
            // A must survive B's lifetime to be used in the next iteration.
            slots(LOOP_CARRIED.defineB);
            slots(LOOP_CARRIED.useB);
        }
        return 23;
    }

    public static int loopExitLive(int count) {
        slots(LOOP_EXIT_LIVE.defineA);
        for (int i = 0; i < count; i++) {
            slots(LOOP_EXIT_LIVE.defineB);
            slots(LOOP_EXIT_LIVE.useB);
        }
        // A is live through the loop body because it is used after the exit.
        slots(LOOP_EXIT_LIVE.useA);
        return 24;
    }

    public static int range() {
        slots(RANGE);
        return 11;
    }

    public static int aliveTemp() {
        slots(ALIVE_TEMP);
        return 12;
    }

    public static int referencePlacement() {
        slots(REFERENCE_PLACEMENT);
        return 16;
    }

    public static int referencePlacementWithRange() {
        slots(REFERENCE_PLACEMENT_WITH_RANGE);
        return 17;
    }

    public static int referenceMap() {
        slots(REFERENCE_MAP);
        return 25;
    }

    public static int stressChainGraph() {
        slots(STRESS_CHAIN);
        return 19;
    }

    public static int stressStarGraph() {
        slots(STRESS_STAR);
        return 20;
    }

    public static int uninitializedHubGraph() {
        slots(UNINITIALIZED_HUB);
        return 22;
    }

    public static int stressCliqueGraph() {
        slots(STRESS_CLIQUE);
        return 21;
    }

    private OptionValues options() {
        return new OptionValues(getInitialOptions(),
                        GraphColoringStackSlotAllocator.Options.LIROptGraphColoringStackSlotAllocator, true);
    }

    /** Compares two rewritten stack slots by raw offset: equal offsets mean physical slot reuse. */
    private void check(String method, SlotSpec spec, boolean sameOffset) {
        if (spec == FRAME_STATE) {
            spec.stateMethod = getResolvedJavaMethod(method);
        }
        compile(getResolvedJavaMethod(method), null, options());
        Assert.assertNotNull(spec.first);
        Assert.assertNotNull(spec.second);
        int firstOffset = spec.first.location().getRawOffset();
        int secondOffset = spec.second.location().getRawOffset();
        Assert.assertEquals(method, sameOffset, firstOffset == secondOffset);
    }

    private void checkControlFlow() {
        compile(getResolvedJavaMethod("diamond"), null, options());
        Assert.assertEquals(BRANCH_A.definition.location().getRawOffset(), BRANCH_B.definition.location().getRawOffset());
        compile(getResolvedJavaMethod("loop"), null, options());
        Assert.assertEquals(LOOP.definition.location().getRawOffset(), LOOP.use.location().getRawOffset());
        compile(getResolvedJavaMethod("range"), null, options());
        Assert.assertEquals(0, RANGE.rangeOp.location().getRawOffset() % 8);
        compile(getResolvedJavaMethod("aliveTemp"), null, options());
        ALIVE_TEMP.op.checkDistinct();
    }

    @Test
    public void testInstructionLevel() {
        check("disjoint", DISJOINT, true);
        check("overlapping", OVERLAPPING, false);
        check("hole", HOLE, true);
        check("useKill", USE_KILL, false);
        check("uninitialized", UNINITIALIZED_SPEC, false);
        check("alias", ALIAS, true);
        Assert.assertEquals(LIRKind.value(getBackend().getTarget().arch.getPlatformKind(JavaKind.Int)), ALIAS.second.location().getValueKind());
        check("differentSize", DIFFERENT_SIZE, false);
        check("reference", REFERENCE, true);
        check("referenceOverlapping", REFERENCE_OVERLAPPING, false);
        check("frequency", FREQUENCY, false);
        Assert.assertTrue("More referenced slot should be closer to the stack pointer",
                        FREQUENCY.second.location().getRawOffset() < FREQUENCY.first.location().getRawOffset());
        check("frameState", FRAME_STATE, false);
        Assert.assertEquals(FRAME_STATE.first.location().getRawOffset(), FRAME_STATE.state.location().getRawOffset());
        checkControlFlow();
    }

    @Test
    public void testReferencePlacement() {
        compile(getResolvedJavaMethod("referencePlacement"), null, options());
        REFERENCE_PLACEMENT.check();
        compile(getResolvedJavaMethod("referencePlacementWithRange"), null, options());
        REFERENCE_PLACEMENT_WITH_RANGE.check();
    }

    @Test
    public void testReferenceMap() {
        REFERENCE_MAP.method = getResolvedJavaMethod("referenceMap");
        REFERENCE_MAP.check(compile(REFERENCE_MAP.method, null, options()));
    }

    @Test
    public void testInterBlockLiveness() {
        LOOP_CARRIED.reset();
        compile(getResolvedJavaMethod("loopCarried"), null, options());
        LOOP_CARRIED.checkLoopCarried();
        LOOP_EXIT_LIVE.reset();
        compile(getResolvedJavaMethod("loopExitLive"), null, options());
        LOOP_EXIT_LIVE.checkSuccessor();
    }

    @Test
    public void testStressChain() {
        compile(getResolvedJavaMethod("stressChainGraph"), null, options());
        for (int i = 0; i < STRESS_CHAIN.count; i++) {
            Assert.assertEquals(STRESS_CHAIN.offset(0) == STRESS_CHAIN.offset(i), i % 2 == 0);
        }
    }

    @Test
    public void testStressStar() {
        compile(getResolvedJavaMethod("stressStarGraph"), null, options());
        for (int i = 1; i < STRESS_STAR.count - 1; i++) {
            Assert.assertEquals(STRESS_STAR.offset(0), STRESS_STAR.offset(i));
        }
        Assert.assertNotEquals(STRESS_STAR.offset(0), STRESS_STAR.offset(STRESS_STAR.count - 1));
    }

    @Test
    public void testUninitializedHub() {
        compile(getResolvedJavaMethod("uninitializedHubGraph"), null, options());
        for (int i = 1; i < UNINITIALIZED_HUB.count - 1; i++) {
            Assert.assertEquals(UNINITIALIZED_HUB.offset(0), UNINITIALIZED_HUB.offset(i));
        }
        Assert.assertNotEquals(UNINITIALIZED_HUB.offset(0), UNINITIALIZED_HUB.offset(UNINITIALIZED_HUB.count - 1));
    }

    @Test
    public void testStressClique() {
        compile(getResolvedJavaMethod("stressCliqueGraph"), null, options());
        for (int i = 0; i < STRESS_CLIQUE.count; i++) {
            for (int j = i + 1; j < STRESS_CLIQUE.count; j++) {
                Assert.assertNotEquals(STRESS_CLIQUE.offset(i), STRESS_CLIQUE.offset(j));
            }
        }
    }

}
