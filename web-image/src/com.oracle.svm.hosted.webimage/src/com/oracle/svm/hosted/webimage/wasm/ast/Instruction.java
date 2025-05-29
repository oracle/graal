/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.ast;

import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f32;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.f64;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i32;
import static com.oracle.svm.webimage.wasm.types.WasmPrimitiveType.i64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.Reference;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.VMConstant;

/**
 * Object representation of WASM instructions.
 * <p>
 * Instructions directly hold their stack operands, instead of having them appear before in the
 * instruction list, to preserve some structure. This resembles the WASM text format's folded
 * instruction format.
 * <p>
 * In WASM the semantics of a folded instruction {@code (op input1 input2)} is the same as the
 * non-folded code (with {@code input1} and {@code input2} unfolded recursively):
 *
 * <pre>
 * {@code
 * input1
 * input2
 * op
 * }
 * </pre>
 *
 * In theory, inputs to instructions here could be {@link WasmBlock} instructions (WASM folded
 * instructions also allow this). Currently, this representation does not allow blocks consume or
 * produce stack values, so using them as inputs does not make sense yet.
 *
 * <p>
 * Ref: https://webassembly.github.io/spec/core/text/instructions.html
 */
public abstract class Instruction {

    protected Object comment = null;

    public Object getComment() {
        return comment;
    }

    public Instruction setComment(Object comment) {
        this.comment = comment;
        return this;
    }

    /**
     * Resolves the given instruction as a constant, will throw an error if the instruction does not
     * have a constant value.
     */
    public static Const asConst(Instruction instr) {
        if (instr instanceof Const constant) {
            return constant;
        } else if (instr instanceof Relocation relocation) {
            GraalError.guarantee(relocation.wasProcessed(), "Found unprocessed relocation when trying to resolve constant: %s", relocation);
            return asConst(relocation.getValue());
        } else {
            throw GraalError.shouldNotReachHere("Found non-constant instruction when trying to resolve constant: " + instr); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Override
    public final String toString() {
        String innerString = toInnerString();
        Object commentString = getComment();

        String finalInnerString;

        if (innerString != null) {
            if (commentString != null) {
                finalInnerString = innerString + ", " + commentString;
            } else {
                finalInnerString = innerString;
            }
        } else {
            finalInnerString = String.valueOf(commentString);
        }

        return ClassUtil.getUnqualifiedName(getClass()) + "{" + finalInnerString + "}";
    }

    protected String toInnerString() {
        return null;
    }

    // region Control Instructions

    /**
     * Represents a labeled WASM block instruction (block, loop, if-else).
     * <p>
     * Currently, can't consume or produce stack values as it is not necessary in current lowering
     * strategies.
     * <p>
     * Ref: https://webassembly.github.io/spec/core/text/instructions.html#text-blockinstr
     */
    public abstract static class WasmBlock extends Instruction {

        /**
         * Possibly empty label identifying the block instruction.
         * <p>
         * If set to null, the block has no named identifier
         */
        protected final WasmId.Label label;

        public WasmBlock(WasmId.Label label) {
            this.label = label;
        }

        public WasmId.Label getLabel() {
            return label;
        }
    }

    /**
     * Labeled WASM block.
     */
    public static final class Block extends WasmBlock {
        public final Instructions instructions = new Instructions();

        public Block(WasmId.Label label) {
            super(label);
        }
    }

    /**
     * Labeled WASM loop.
     */
    public static final class Loop extends WasmBlock {
        public final Instructions instructions = new Instructions();

        public Loop(WasmId.Label label) {
            super(label);
        }
    }

    /**
     * Labeled WASM if-then-else statement with optional else.
     */
    public static final class If extends WasmBlock {
        public final Instructions thenInstructions = new Instructions();
        public final Instructions elseInstructions = new Instructions();

        public final Instruction condition;

        public If(WasmId.Label label, Instruction condition) {
            super(label);
            this.condition = condition;
        }

        public boolean hasElse() {
            return !elseInstructions.get().isEmpty();
        }
    }

    /**
     * The WASM try block from the exception handling proposal.
     * <p>
     * Ref: https://github.com/WebAssembly/exception-handling
     */
    public static final class Try extends WasmBlock {

        /**
         * A catch block for the given tag in the try block.
         */
        public static final class Catch {
            public final WasmId.Tag tag;
            public final Instructions instructions = new Instructions();

            private Catch(WasmId.Tag tag) {
                this.tag = tag;
            }
        }

        public final Instructions instructions = new Instructions();
        public final List<Catch> catchBlocks = new ArrayList<>();

        public Try(WasmId.Label label) {
            super(label);
        }

        public Instructions addCatch(WasmId.Tag tag) {
            var catchBlock = new Catch(tag);
            catchBlocks.add(catchBlock);
            return catchBlock.instructions;
        }
    }

    public static final class Nop extends Instruction {
        public Nop() {
        }
    }

    public static final class Unreachable extends Instruction {
        public Unreachable() {
        }
    }

    /**
     * WASM br and br_if instructions.
     */
    public static final class Break extends Instruction {

        private final WasmId.Label target;

        /**
         * Condition under which the break occurs.
         * <p>
         * If not null, this is a br_if instruction
         */
        public final Instruction condition;

        /**
         * Constructor for the 'br' instruction.
         */
        public Break(WasmId.Label target) {
            this(target, null);
        }

        /**
         * Constructor for the 'br_if' instruction.
         */
        public Break(WasmId.Label target, Instruction condition) {
            this.target = target;
            this.condition = condition;
        }

        public WasmId.Label getTarget() {
            return target;
        }
    }

    /**
     * Represents the br_table instruction.
     */
    public static final class BreakTable extends Instruction {

        /**
         * Targets emitted for the br_table instruction.
         * <p>
         * null values will target the default target.
         */
        private final List<WasmId.Label> targets = new ArrayList<>();
        private WasmId.Label defaultTarget;

        /**
         * Instruction that produces the index to switch on.
         */
        public final Instruction index;

        public BreakTable(Instruction index) {
            this.index = index;
        }

        public int numTargets() {
            return targets.size();
        }

        public WasmId.Label getTarget(int idx) {
            return targets.get(idx);
        }

        /**
         * Sets the jump target for the given key.
         * <p>
         * If the key is out-of-bounds, targets in between are set to null
         *
         * @param key The key for which this instruction jumps to the given target.
         * @param target The target block
         */
        public void setTarget(int key, WasmId.Label target) {
            assert NumUtil.assertNonNegativeInt(key);

            // We need to extend the list with null first
            if (key >= targets.size()) {
                targets.addAll(Collections.nCopies(key - targets.size() + 1, null));
            }

            targets.set(key, target);
        }

        /**
         * Fills all empty ({@code null}) targets with the default target.
         * <p>
         * Only call this when {@link #setDefaultTarget(WasmId.Label)} is not called anymore.
         */
        public void fillTargets() {
            targets.replaceAll(t -> t == null ? defaultTarget : t);
        }

        public WasmId.Label getDefaultTarget() {
            return defaultTarget;
        }

        public void setDefaultTarget(WasmId.Label defaultTarget) {
            this.defaultTarget = defaultTarget;
        }
    }

    /**
     * Common base class for call-like instructions.
     */
    public abstract static class AbstractCall extends Instruction {
        public final Instructions args;

        protected AbstractCall(Instructions args) {
            this.args = args;
        }

        protected AbstractCall(Instruction... args) {
            this(Instructions.asInstructions(args));
        }
    }

    /**
     * The {@code call} instruction.
     * <p>
     * Will call the given {@link #target} function, which is statically known.
     */
    public static final class Call extends AbstractCall {

        private final WasmId.Func target;

        public Call(WasmId.Func target, Instructions args) {
            super(args);
            this.target = target;
        }

        public Call(WasmId.Func target, Instruction... args) {
            this(target, Instructions.asInstructions(args));
        }

        public WasmId.Func getTarget() {
            return target;
        }
    }

    /**
     * The {@code call_ref} instruction.
     * <p>
     * Calls the given {@link #functionReference}, which is not known statically.
     */
    public static final class CallRef extends AbstractCall {
        /**
         * Signature of target function.
         */
        public final WasmId.Type functionType;

        public final Instruction functionReference;

        public CallRef(WasmId.Type functionType, Instruction functionReference, Instructions args) {
            super(args);
            this.functionType = functionType;
            this.functionReference = functionReference;
        }
    }

    /**
     * The {@code call_indirect} instruction.
     * <p>
     * Will invoke the function stored at {@link #index} in {@link #table}.
     */
    public static final class CallIndirect extends AbstractCall {
        /**
         * The {@link Table} where to look up the target function.
         */
        public final WasmId.Table table;

        /**
         * Index into the table.
         */
        public final Instruction index;

        /**
         * Reference to type definition of target function.
         */
        public final WasmId.FuncType funcId;

        public final TypeUse signature;

        public CallIndirect(WasmId.Table table, Instruction index, WasmId.FuncType funcId, TypeUse signature, Instructions args) {
            super(args);
            assert signature.params.size() == args.get().size() : "Number of arguments in signature (" + signature.params.size() + ") must match number of arguments given (" + args.get().size() + ")";
            this.table = table;
            this.index = index;
            this.funcId = funcId;
            this.signature = signature;
        }

        @Override
        public String toInnerString() {
            return "call_indirect " + table + " " + signature;
        }
    }

    /**
     * The WASM return instruction.
     * <p>
     * In theory, WASM can return multiple values, but the Graal IR only has single return values.
     */
    public static final class Return extends Instruction {
        public final Instruction result;

        /**
         * Constructor for 'return void'.
         */
        public Return() {
            this(null);
        }

        /**
         * Constructor for 'return result'.
         */
        public Return(Instruction result) {
            this.result = result;
        }

        public boolean isVoid() {
            return result == null;
        }
    }

    /**
     * The WASM throw instruction from the exception handling proposal.
     * <p>
     * Ref: https://github.com/WebAssembly/exception-handling
     */
    public static final class Throw extends Instruction {

        /**
         * The tag with which the exception is thrown.
         */
        public final WasmId.Tag tag;

        /**
         * The values to throw.
         * <p>
         * This must match the types in the arguments of {@link TypeUse} in {@link #tag}.
         */
        public final Instructions arguments;

        public Throw(WasmId.Tag tag, Instructions arguments) {
            this.tag = tag;
            this.arguments = arguments;
        }

        public Throw(WasmId.Tag tag, Instruction... args) {
            this(tag, Instructions.asInstructions(args));
        }
    }
    // endregion

    // region Numeric Instructions

    /**
     * Constant with primitive value.
     * <p>
     * Cannot represent references.
     */
    public static final class Const extends Instruction {
        public final Literal literal;

        public Const(Literal literal) {
            this.literal = literal;
        }

        public static Const defaultForType(WasmValType type) {
            return new Const(Literal.defaultForType(type.asPrimitive()));
        }

        public static Const forConstant(PrimitiveConstant primitiveConstant) {
            return new Const(Literal.forConstant(primitiveConstant));
        }

        public static Const forBoolean(boolean value) {
            return forInt(value ? 1 : 0);
        }

        public static Const forInt(int value) {
            return new Const(Literal.forInt(value));
        }

        public static Const forLong(long value) {
            return new Const(Literal.forLong(value));
        }

        public static Const forFloat(float value) {
            return new Const(Literal.forFloat(value));
        }

        public static Const forDouble(double value) {
            return new Const(Literal.forDouble(value));
        }

        public static Const forNull() {
            return forInt(0);
        }

        public static Const forWord(WordBase word) {
            // TODO GR-42105 Use forInt
            return forLong(word.rawValue());
        }
    }

    /**
     * Does not directly correspond to a WASM instruction, instead it is a placeholder for an
     * instruction to be filled later.
     * <p>
     * More or less corresponds to {@link com.oracle.svm.hosted.image.RelocatableBuffer.Info}, but
     * targeting a node in the AST instead of a location in a buffer.
     * <p>
     * Is used to hold values which are not known during compilation. For example pointers to
     * objects in the image heap; their address is only known after the heap layouting and thus a
     * relocation is inserted during compilation with {@link #target} pointing to the object.
     * <p>
     * When emitting code, {@link #value} is emitted in place of this "instruction".
     */
    public static final class Relocation extends Instruction {

        /**
         * The value this relocation targets.
         * <p>
         * In the end {@link #value} should hold the value that corresponds to this reference. E.g.
         * for {@link ConstantReference} which holds a {@link ImageHeapConstant}, {@link #value}
         * will hold the address of the object in memory.
         */
        public final Reference target;

        /**
         * The actual value of this instruction.
         * <p>
         * Is initially empty and only set when the final module is constructed.
         */
        private Instruction value;

        public Relocation(Reference target) {
            this.target = Objects.requireNonNull(target);
        }

        /**
         * @see #forConstant(VMConstant)
         */
        public static Relocation forConstant(JavaConstant constant) {
            return forConstant((VMConstant) constant);
        }

        /**
         * Creates a relocation that references the given {@link VMConstant}.
         * <p>
         * These relocations are usually replaced with a pointer to an image heap object. Care has
         * to be taken that the targets of the relocation is registered to be available for
         * relocation.
         */
        public static Relocation forConstant(VMConstant constant) {
            return new Relocation(new ConstantReference(constant));
        }

        /**
         * Whether the relocation was already processed and now contains a value.
         */
        public boolean wasProcessed() {
            return value != null;
        }

        public Instruction getValue() {
            assert wasProcessed() : "Relocation has no value yet";
            return value;
        }

        public void setValue(Instruction value) {
            assert !wasProcessed() : "Value is already set";
            assert value != null;
            this.value = value;
        }

        @Override
        protected String toInnerString() {
            return target.toString();
        }
    }

    /**
     * Represents all side effect free unary instructions.
     */
    public static final class Unary extends Instruction {

        public final Op op;
        public final Instruction value;

        private Unary(Op op, Instruction value) {
            this.op = op;
            this.value = value;
        }

        public enum Op {
            /**
             * No operation performed.
             * <p>
             * This must never appear in a final module, it is used exclusively during lowering to
             * indicate that a node doesn't perform any operation.
             */
            Nop("", null, null),
            /**
             * Count leading zeros.
             */
            I32Clz("i32.clz", i32, i32),
            /**
             * Count trailing zeros.
             */
            I32Ctz("i32.ctz", i32, i32),
            /**
             * Count non-zero bits.
             */
            I32Popcnt("i32.popcnt", i32, i32),
            /**
             * Equals to zero?
             */
            I32Eqz("i32.eqz", i32, i32),
            /**
             * Count leading zeros.
             */
            I64Clz("i64.clz", i64, i64),
            /**
             * Count trailing zeros.
             */
            I64Ctz("i64.ctz", i64, i64),
            /**
             * Count non-zero bits.
             */
            I64Popcnt("i64.popcnt", i64, i64),
            /**
             * Equals to zero?
             */
            I64Eqz("i64.eqz", i64, i32),
            /**
             * i8 (stored as i32) -> i32.
             */
            I32Extend8("i32.extend8_s", i32, i32),
            /**
             * i8 (stored as i64) -> i64.
             */
            I64Extend8("i64.extend8_s", i64, i64),
            /**
             * i16 (stored as i32)-> i32.
             */
            I32Extend16("i32.extend16_s", i32, i32),
            /**
             * i16 (stored as i64)-> i64.
             */
            I64Extend16("i64.extend16_s", i64, i64),
            /**
             * i32 (stored as i64)-> i64.
             */
            I64Extend32("i64.extend32_s", i64, i64),
            /**
             * i32 -> i64 as unsigned.
             */
            I64ExtendI32U("i64.extend_i32_u", i32, i64),
            /**
             * i32 -> i64 as signed.
             */
            I64ExtendI32S("i64.extend_i32_s", i32, i64),
            F32Abs("f32.abs", f32, f32),
            F32Neg("f32.neg", f32, f32),
            F32Sqrt("f32.sqrt", f32, f32),
            F32Ceil("f32.ceil", f32, f32),
            F32Floor("f32.floor", f32, f32),
            F32Trunc("f32.trunc", f32, f32),
            F32Nearest("f32.nearest", f32, f32),
            F64Abs("f64.abs", f64, f64),
            F64Neg("f64.neg", f64, f64),
            F64Sqrt("f64.sqrt", f64, f64),
            F64Ceil("f64.ceil", f64, f64),
            F64Floor("f64.floor", f64, f64),
            F64Trunc("f64.trunc", f64, f64),
            F64Nearest("f64.nearest", f64, f64),
            /**
             * i64 -> i32 (narrowing).
             */
            I32Wrap64("i32.wrap_i64", i64, i32),
            /**
             * f64 -> f32.
             */
            F32Demote64("f32.demote_f64", f64, f32),
            /**
             * f32 -> f64.
             */
            F64Promote32("f64.promote_f32", f32, f64),
            /**
             * i32 -> f32.
             */
            F32ConvertI32S("f32.convert_i32_s", i32, f32),
            /**
             * i64 -> f32.
             */
            F32ConvertI64S("f32.convert_i64_s", i64, f32),
            /**
             * i32 -> f64.
             */
            F64ConvertI32S("f64.convert_i32_s", i32, f64),
            /**
             * i64 -> f64.
             */
            F64ConvertI64S("f64.convert_i64_s", i64, f64),
            /**
             * f32 -> i32 (F2I).
             */
            I32TruncSatF32S("i32.trunc_sat_f32_s", f32, i32),
            /**
             * f64 -> i32 (D2I).
             */
            I32TruncSatF64S("i32.trunc_sat_f64_s", f64, i32),
            /**
             * f32 -> i64 (F2L).
             */
            I64TruncSatF32S("i64.trunc_sat_f32_s", f32, i64),
            /**
             * f64 -> i64 (D2L).
             */
            I64TruncSatF64S("i64.trunc_sat_f64_s", f64, i64),
            /**
             * f32 -> i32 (reinterpret).
             */
            I32ReinterpretF32("i32.reinterpret_f32", f32, i32),
            /**
             * f64 -> i64 (reinterpret).
             */
            I64ReinterpretF64("i64.reinterpret_f64", f64, i64),
            /**
             * i32 -> f32 (reinterpret).
             */
            F32ReinterpretI32("f32.reinterpret_i32", i32, f32),
            /**
             * i64 -> f64 (reinterpret).
             */
            F64ReinterpretI64("f64.reinterpret_i64", i64, f64),
            RefIsNull("ref.is_null", WasmRefType.ANYREF, i32),
            RefAsNonNull("ref.as_non_null", WasmRefType.ANYREF, WasmRefType.ANYREF.asNonNull());

            public final String opName;
            public final WasmValType inputType;
            public final WasmValType outputType;

            Op(String opName, WasmValType inputType, WasmValType outputType) {
                this.opName = opName;
                this.inputType = inputType;
                this.outputType = outputType;
            }

            public Unary create(Instruction input) {
                return new Unary(this, input);
            }
        }
    }

    /**
     * Represents all side effect free binary instructions.
     */
    public static final class Binary extends Instruction {

        public final Op op;
        public final Instruction left;
        public final Instruction right;

        private Binary(Op op, Instruction left, Instruction right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        public enum Op {
            I32Add("i32.add", i32, i32, i32),
            I32Sub("i32.sub", i32, i32, i32),
            I32Mul("i32.mul", i32, i32, i32),
            I32DivU("i32.div_u", i32, i32, i32),
            I32DivS("i32.div_s", i32, i32, i32),
            I32RemU("i32.rem_u", i32, i32, i32),
            I32RemS("i32.rem_s", i32, i32, i32),
            I32And("i32.and", i32, i32, i32),
            I32Or("i32.or", i32, i32, i32),
            I32Xor("i32.xor", i32, i32, i32),
            I32Shl("i32.shl", i32, i32, i32),
            I32ShrU("i32.shr_u", i32, i32, i32),
            I32ShrS("i32.shr_s", i32, i32, i32),
            I32Eq("i32.eq", i32, i32, i32),
            I32Ne("i32.ne", i32, i32, i32),
            I32LtU("i32.lt_u", i32, i32, i32),
            I32LtS("i32.lt_s", i32, i32, i32),
            I32GtU("i32.gt_u", i32, i32, i32),
            I32GtS("i32.gt_s", i32, i32, i32),
            I32LeU("i32.le_u", i32, i32, i32),
            I32LeS("i32.le_s", i32, i32, i32),
            I32GeU("i32.ge_u", i32, i32, i32),
            I32GeS("i32.ge_s", i32, i32, i32),
            I64Add("i64.add", i64, i64, i64),
            I64Sub("i64.sub", i64, i64, i64),
            I64Mul("i64.mul", i64, i64, i64),
            I64DivU("i64.div_u", i64, i64, i64),
            I64DivS("i64.div_s", i64, i64, i64),
            I64RemU("i64.rem_u", i64, i64, i64),
            I64RemS("i64.rem_s", i64, i64, i64),
            I64And("i64.and", i64, i64, i64),
            I64Or("i64.or", i64, i64, i64),
            I64Xor("i64.xor", i64, i64, i64),
            I64Shl("i64.shl", i64, i64, i64),
            I64ShrU("i64.shr_u", i64, i64, i64),
            I64ShrS("i64.shr_s", i64, i64, i64),
            I64Eq("i64.eq", i64, i64, i32),
            I64Ne("i64.ne", i64, i64, i32),
            I64LtU("i64.lt_u", i64, i64, i32),
            I64LtS("i64.lt_s", i64, i64, i32),
            I64GtU("i64.gt_u", i64, i64, i32),
            I64GtS("i64.gt_s", i64, i64, i32),
            I64LeU("i64.le_u", i64, i64, i32),
            I64LeS("i64.le_s", i64, i64, i32),
            I64GeU("i64.ge_u", i64, i64, i32),
            I64GeS("i64.ge_s", i64, i64, i32),

            F32Add("f32.add", f32, f32, f32),
            F32Sub("f32.sub", f32, f32, f32),
            F32Mul("f32.mul", f32, f32, f32),
            F32Div("f32.div", f32, f32, f32),
            F32Min("f32.min", f32, f32, f32),
            F32Max("f32.max", f32, f32, f32),
            F32CopySign("f32.copysign", f32, f32, f32),

            F64Add("f64.add", f64, f64, f64),
            F64Sub("f64.sub", f64, f64, f64),
            F64Mul("f64.mul", f64, f64, f64),
            F64Div("f64.div", f64, f64, f64),
            F64Min("f64.min", f64, f64, f64),
            F64Max("f64.max", f64, f64, f64),
            F64CopySign("f64.copysign", f64, f64, f64),
            F32Eq("f32.eq", f32, f32, i32),
            F32Lt("f32.lt", f32, f32, i32),
            F64Eq("f64.eq", f64, f64, i32),
            F64Lt("f64.lt", f64, f64, i32),
            RefEq("ref.eq", WasmRefType.ANYREF, WasmRefType.ANYREF, i32);

            public final String opName;
            public final WasmValType leftInputType;
            public final WasmValType rightInputType;
            public final WasmValType outputType;

            Op(String opName, WasmValType leftInputType, WasmValType rightInputType, WasmValType outputType) {
                this.opName = opName;
                this.leftInputType = leftInputType;
                this.rightInputType = rightInputType;
                this.outputType = outputType;
            }

            public Binary create(Instruction left, Instruction right) {
                return new Binary(this, left, right);
            }
        }
    }
    // endregion

    // region Parametric Instructions
    public static final class Drop extends Instruction {
        public final Instruction value;

        public Drop(Instruction value) {
            this.value = value;
        }
    }

    public static final class Select extends Instruction {

        public final Instruction condition;
        public final Instruction trueValue;
        public final Instruction falseValue;

        /**
         * Type of the produced value.
         * <p>
         * The type is optional in the generated WASM module, but it is required in this
         * representation.
         */
        public final WasmValType type;

        public Select(Instruction trueValue, Instruction falseValue, Instruction condition, WasmValType type) {
            this.condition = condition;
            this.trueValue = trueValue;
            this.falseValue = falseValue;
            this.type = type;

            assert type != null;
        }
    }
    // endregion

    // region Variable Instructions

    /**
     * The {@code local.get} instruction.
     */
    public static final class LocalGet extends Instruction {
        private final WasmId.Local local;

        public LocalGet(WasmId.Local local) {
            this.local = local;
        }

        public WasmId.Local getLocal() {
            return local;
        }

        public WasmValType getType() {
            return local.getVariableType();
        }
    }

    /**
     * The {@code local.set} instruction.
     */
    public static final class LocalSet extends Instruction {
        private final WasmId.Local local;
        public final Instruction value;

        public LocalSet(WasmId.Local local, Instruction value) {
            this.local = local;
            this.value = value;
        }

        public WasmId.Local getLocal() {
            return local;
        }

        public WasmValType getType() {
            return local.getVariableType();
        }
    }

    public static final class LocalTee extends Instruction {
        private final WasmId.Local local;
        public final Instruction value;

        public LocalTee(WasmId.Local local, Instruction value) {
            this.local = local;
            this.value = value;
        }

        public WasmId.Local getLocal() {
            return local;
        }

        public WasmValType getType() {
            return local.getVariableType();
        }
    }

    /**
     * The {@code global.get} instruction.
     */
    public static final class GlobalGet extends Instruction {
        private final WasmId.Global global;

        public GlobalGet(WasmId.Global global) {
            this.global = global;
        }

        public WasmId.Global getGlobal() {
            return global;
        }

        public WasmValType getType() {
            return global.getVariableType();
        }
    }

    /**
     * The {@code global.set} instruction.
     */
    public static final class GlobalSet extends Instruction {
        private final WasmId.Global global;
        public final Instruction value;

        public GlobalSet(WasmId.Global global, Instruction value) {
            this.global = global;
            this.value = value;
        }

        public WasmId.Global getGlobal() {
            return global;
        }

        public WasmValType getType() {
            return global.getVariableType();
        }
    }
    // endregion

    // region Table Instructions

    /**
     * The {@code table.get} instruction.
     * <p>
     * Will load the element at {@link #index} from the given {@link #table}.
     */
    public static final class TableGet extends Instruction {
        public final WasmId.Table table;

        public final Instruction index;

        public TableGet(WasmId.Table table, Instruction index) {
            this.table = table;
            this.index = index;
        }
    }

    /**
     * The {@code table.set} instruction.
     * <p>
     * Will set the element at {@link #index} in the given {@link #table} to {@link #value}.
     */
    public static final class TableSet extends Instruction {
        public final WasmId.Table table;

        public final Instruction index;
        public final Instruction value;

        public TableSet(WasmId.Table table, Instruction index, Instruction value) {
            this.table = table;
            this.index = index;
            this.value = value;
        }
    }

    /**
     * The {@code table.size} instruction.
     * <p>
     * Gets the size of the given {@link #table}.
     */
    public static final class TableSize extends Instruction {
        public final WasmId.Table table;

        public TableSize(WasmId.Table table) {
            this.table = table;
        }
    }

    /**
     * The {@code table.grow} instruction.
     * <p>
     * Will grow the given {@link #table} by the given {@link #delta} entries and fill the new
     * entries with the given {@link #initValue}.
     * <p>
     * Will produce the previous size of the table on success or {@code -1} on error.
     */
    public static final class TableGrow extends Instruction {
        public final WasmId.Table table;

        public final Instruction initValue;
        public final Instruction delta;

        public TableGrow(WasmId.Table table, Instruction initValue, Instruction delta) {
            this.table = table;
            this.initValue = initValue;
            this.delta = delta;
        }
    }

    /**
     * The {@code table.fill} instruction.
     * <p>
     * Fills the range {@code [offset, offset + size)} of the given {@link #table} with
     * {@link #value}>
     */
    public static final class TableFill extends Instruction {
        public final WasmId.Table table;

        public final Instruction offset;
        public final Instruction value;
        public final Instruction size;

        public TableFill(WasmId.Table table, Instruction offset, Instruction value, Instruction size) {
            this.table = table;
            this.offset = offset;
            this.value = value;
            this.size = size;
        }
    }

    /**
     * The {@code table.copy} instruction.
     * <p>
     * Copies the range {@code [srcOffset, srcOffset + size)} of the given {@link #srcTable} to
     * {@code [destOffset, destOffset + size)} in {@link #destTable}.
     */
    public static final class TableCopy extends Instruction {
        public final WasmId.Table destTable;
        public final WasmId.Table srcTable;

        public final Instruction destOffset;
        public final Instruction srcOffset;
        public final Instruction size;

        public TableCopy(WasmId.Table destTable, WasmId.Table srcTable, Instruction destOffset, Instruction srcOffset, Instruction size) {
            this.destTable = destTable;
            this.srcTable = srcTable;
            this.destOffset = destOffset;
            this.srcOffset = srcOffset;
            this.size = size;
        }
    }
    // endregion

    // region Memory Instructions
    public abstract static class Memory extends Instruction {
        /**
         * Whether this instruction is a load or a store.
         */
        public final boolean isLoad;

        /**
         * The type of the value this instruction consumes from (stores) or produces onto (loads)
         * the stack.
         */
        public final WasmPrimitiveType stackType;

        /**
         * The width, in bits, of the accessed memory.
         * <p>
         * If set to zero, matches the bit width of {@link #stackType}
         */
        public final int memoryWidth;

        /**
         * This offset is added to the base address read from the stack.
         * <p>
         * Must be a statically computable constant 32-bit integer (see {@link #asConst}).
         * <p>
         * We treat negative values as sentinels for illegal values. This reduces the range of the
         * immediate offset to 2GiB, which is not a problem in practice.
         */
        private final Instruction offset;

        protected Memory(boolean isLoad, WasmPrimitiveType stackType, int memoryWidth, Instruction offset) {
            assert memoryWidth == 0 || memoryWidth == 8 || memoryWidth == 16 || memoryWidth == 32 || memoryWidth == 64 : memoryWidth;
            this.memoryWidth = memoryWidth == stackType.getBitCount() ? 0 : memoryWidth;
            this.isLoad = isLoad;
            this.stackType = stackType;
            this.offset = offset;
        }

        /**
         * Resolves the {@link #offset} instruction and returns its numeric value.
         * <p>
         * Will throw an error if the offset does not have a constant value.
         *
         * @return A 32-bit signed integer offset. Negative values are illegal.
         * @see #asConst
         */
        public int calculateOffset() {
            Literal lit = asConst(offset).literal;
            GraalError.guarantee(lit.type == i32, "Memory offset is not a 32-bit integer: %s", lit);
            int val = lit.getI32();
            GraalError.guarantee(val >= 0, "Memory offset is negative: %d", val);
            return val;
        }

        public Instruction getOffset() {
            return offset;
        }
    }

    public static final class Load extends Memory {
        public final Instruction baseAddress;

        /**
         * Whether the load requires a sign extension (otherwise requires zero extension).
         * <p>
         * Only applies if {@link #memoryWidth} is not 0.
         */
        public final boolean signed;

        public Load(WasmPrimitiveType stackType, int offset, Instruction baseAddress, int memoryWidth, boolean signed) {
            this(stackType, Const.forInt(offset), baseAddress, memoryWidth, signed);
        }

        public Load(WasmPrimitiveType stackType, Instruction offset, Instruction baseAddress, int memoryWidth, boolean signed) {
            super(true, stackType, memoryWidth, offset);
            this.baseAddress = baseAddress;
            this.signed = signed;
        }
    }

    public static final class Store extends Memory {
        /**
         * Value to be written to memory.
         */
        public final Instruction value;

        public final Instruction baseAddress;

        public Store(WasmPrimitiveType stackType, Instruction value, Instruction baseAddress) {
            this(stackType, 0, value, baseAddress);
        }

        public Store(WasmPrimitiveType stackType, int offset, Instruction value, Instruction baseAddress) {
            this(stackType, offset, value, baseAddress, 0);
        }

        public Store(WasmPrimitiveType stackType, int offset, Instruction value, Instruction baseAddress, int memoryWidth) {
            this(stackType, Const.forInt(offset), value, baseAddress, memoryWidth);
        }

        public Store(WasmPrimitiveType stackType, Instruction offset, Instruction value, Instruction baseAddress, int memoryWidth) {
            super(false, stackType, memoryWidth, offset);
            this.value = value;
            this.baseAddress = baseAddress;
        }
    }

    public static final class MemorySize extends Instruction {
        public MemorySize() {
        }
    }

    public static final class MemoryGrow extends Instruction {
        public final Instruction numPages;

        public MemoryGrow(Instruction numPages) {
            this.numPages = numPages;
        }
    }

    /**
     * The {@code memory.fill} instruction.
     * <p>
     * Will set all bytes in {@code [start, start + size)} to the fill value.
     */
    public static final class MemoryFill extends Instruction {
        public final Instruction start;
        public final Instruction fillValue;
        public final Instruction size;

        public MemoryFill(Instruction start, Instruction fillValue, Instruction size) {
            this.start = start;
            this.fillValue = fillValue;
            this.size = size;
        }
    }

    /**
     * The {@code memory.copy} instruction.
     * <p>
     * Will copy byte range {@code [srcOffset, srcOffset + size)} to
     * {@code [destOffset, destOffset + size)}.
     */
    public static final class MemoryCopy extends Instruction {
        public final Instruction destOffset;
        public final Instruction srcOffset;
        public final Instruction size;

        public MemoryCopy(Instruction destOffset, Instruction srcOffset, Instruction size) {
            this.destOffset = destOffset;
            this.srcOffset = srcOffset;
            this.size = size;
        }
    }

    /**
     * The {@code memory.init} instruction.
     * <p>
     * Like {@link MemoryCopy}, but the source is {@link #dataSegment}.
     */
    public static final class MemoryInit extends Instruction {
        public final WasmId.Data dataSegment;

        public final Instruction destOffset;
        public final Instruction srcOffset;
        public final Instruction size;

        public MemoryInit(WasmId.Data dataSegment, Instruction destOffset, Instruction srcOffset, Instruction size) {
            this.dataSegment = dataSegment;
            this.destOffset = destOffset;
            this.srcOffset = srcOffset;
            this.size = size;
        }
    }

    /**
     * The {@code data.drop} instruction.
     * <p>
     * Prevents further use the given passive {@link #dataSegment}. After this, the runtime may free
     * the associated memory.
     */
    public static final class DataDrop extends Instruction {
        public final WasmId.Data dataSegment;

        public DataDrop(WasmId.Data dataSegment) {
            this.dataSegment = dataSegment;
        }
    }
    // endregion

    // region Reference Instructions

    /**
     * The {@code ref.null} instruction.
     */
    public static final class RefNull extends Instruction {

        /**
         * The heap type of the null value.
         * <p>
         * This does not actually represent a reference type, but the underlying heap type. Just
         * uses the {@link WasmRefType} class since it has the same structure (just with a slightly
         * different semantic).
         */
        public final WasmRefType heapType;

        public RefNull(WasmRefType heapType) {
            this.heapType = heapType;

            assert heapType.nullable : "Given type for ref.null must be nullable: " + heapType;
        }

        public RefNull(WasmId.Type heapType) {
            this(heapType.asNullable());
        }
    }

    /**
     * The {@code ref.func} instruction.
     * <p>
     * Creates a non-null function reference for the given function.
     */
    public static final class RefFunc extends Instruction {
        public final WasmId.Func func;

        public RefFunc(WasmId.Func func) {
            this.func = func;
        }
    }

    /**
     * The {@code ref.test} instruction.
     */
    public static final class RefTest extends Instruction {

        public final Instruction input;

        public final WasmRefType testType;

        public RefTest(Instruction input, WasmRefType testType) {
            this.input = input;
            this.testType = testType;
        }

    }

    public static final class RefCast extends Instruction {

        public final Instruction input;

        public final WasmRefType newType;

        public RefCast(Instruction input, WasmRefType newType) {
            this.input = input;
            this.newType = newType;
        }

    }
    // endregion

    // region WasmGC Instructions

    /**
     * Represents both {@code struct.new} and {@code struct.new_default}.
     */
    public static final class StructNew extends Instruction {

        public final WasmId.StructType type;

        /**
         * Field values used to initialize the struct.
         * <p>
         * Must match the number of fields in the struct definition.
         * <p>
         * If {@code null}, this instruction represents {@code struct.new_default}.
         */
        private final Instructions fieldValues;

        /**
         * Constructor for {@code struct.new_default}.
         */
        public StructNew(WasmId.StructType type) {
            this.type = type;
            this.fieldValues = null;
        }

        /**
         * Constructor for {@code struct.new}.
         */
        public StructNew(WasmId.StructType type, Instruction... fieldValues) {
            this.type = type;
            this.fieldValues = Instructions.asInstructions(fieldValues);
        }

        /**
         * Whether this is a {@code struct.new_default} instruction.
         */
        public boolean isDefault() {
            return fieldValues == null;
        }

        /**
         * Instructions for each field value.
         * <p>
         * Only call this if {@link #isDefault()} return false
         */
        public Instructions getFieldValues() {
            assert !isDefault() : "There are no field values for struct.new_default";
            return fieldValues;
        }
    }

    /**
     * The {@code struct.get(_u|s)} instructions.
     * <p>
     * Reads field {@link #fieldId} from {@link #ref} using the given {@link #extension extension
     * mode}.
     */
    public static final class StructGet extends Instruction {

        public final WasmId.StructType refType;
        public final WasmId.Field fieldId;
        public final WasmUtil.Extension extension;

        public final Instruction ref;

        public StructGet(WasmId.StructType refType, WasmId.Field fieldId, WasmUtil.Extension extension, Instruction ref) {
            this.refType = refType;
            this.fieldId = fieldId;
            this.extension = extension;
            this.ref = ref;
        }
    }

    public static final class StructSet extends Instruction {
        public final WasmId.StructType refType;
        public final WasmId.Field fieldId;

        public final Instruction ref;
        public final Instruction value;

        public StructSet(WasmId.StructType refType, WasmId.Field fieldId, Instruction ref, Instruction value) {
            this.refType = refType;
            this.fieldId = fieldId;
            this.ref = ref;
            this.value = value;
        }
    }

    public static final class ArrayNew extends Instruction {
        public final WasmId.ArrayType type;

        /**
         * Element value used to initialize all array elements.
         * <p>
         * If {@code null}, this instruction represents {@code array.new_default}.
         */
        private final Instruction elementValue;

        public final Instruction length;

        /**
         * Constructor for {@code array.new_default}.
         */
        public ArrayNew(WasmId.ArrayType type, Instruction length) {
            this(type, null, length);
        }

        /**
         * Constructor for {@code array.new}.
         */
        public ArrayNew(WasmId.ArrayType type, Instruction elementValue, Instruction length) {
            this.type = type;
            this.elementValue = elementValue;
            this.length = length;
        }

        public boolean isDefault() {
            return elementValue == null;
        }

        /**
         * Instruction for the initial array value.
         * <p>
         * Only call this if {@link #isDefault()} return false
         */
        public Instruction getElementValue() {
            assert !isDefault() : "There is no element value for array.new_default";
            return elementValue;
        }
    }

    /**
     * Represents {@code array.new_fixed}.
     */
    public static final class ArrayNewFixed extends Instruction {
        /**
         * Maximum number of elements in this instruction.
         * <p>
         * The <a href="https://webassembly.github.io/gc/js-api/index.html#limits">WebAssembly
         * JavaScript Interface</a> sets an implementation limit of 10'000 elements for this
         * instruction in JavaScript embedders.
         */
        public static final int MAX_LENGTH = 10000;

        public final WasmId.ArrayType type;

        /**
         * Element values used to initialize array elements.
         */
        public final Instructions elementValues;

        public ArrayNewFixed(WasmId.ArrayType type, Instruction... elements) {
            this.type = type;
            this.elementValues = Instructions.asInstructions(elements);

            assert elements.length <= MAX_LENGTH : "array.new_fixed has too many elements: " + elements.length + " (max: " + MAX_LENGTH + ")";
        }

        public int getLength() {
            return elementValues.get().size();
        }
    }

    /**
     * Represents {@code array.new_data}.
     * <p>
     * Creates a new array from data segment {@link #dataSegment} in range
     * {@code [offset, offset + size)}.
     */
    public static final class ArrayNewData extends Instruction {
        public final WasmId.ArrayType type;
        public final WasmId.Data dataSegment;

        public final Instruction offset;
        public final Instruction size;

        public ArrayNewData(WasmId.ArrayType type, WasmId.Data dataSegment, Instruction offset, Instruction size) {
            this.type = type;
            this.dataSegment = dataSegment;
            this.offset = offset;
            this.size = size;
        }
    }

    public static final class ArrayLen extends Instruction {
        public final Instruction ref;

        public ArrayLen(Instruction ref) {
            this.ref = ref;
        }
    }

    /**
     * The {@code array.fill} instruction.
     * <p>
     * Fills {@code array[offset, offset + size)} with {@link #value}.
     */
    public static final class ArrayFill extends Instruction {
        public final WasmId.ArrayType arrayType;

        public final Instruction array;
        public final Instruction offset;
        public final Instruction value;
        public final Instruction size;

        public ArrayFill(WasmId.ArrayType arrayType, Instruction array, Instruction offset, Instruction value, Instruction size) {
            this.arrayType = arrayType;
            this.array = array;
            this.offset = offset;
            this.value = value;
            this.size = size;
        }
    }

    /**
     * The {@code array.get(_u|s)} instructions.
     * <p>
     * Reads value at index {@link #idx} in {@link #ref} using the given {@link #extension extension
     * mode}.
     */
    public static final class ArrayGet extends Instruction {

        public final WasmId.ArrayType refType;
        public final WasmUtil.Extension extension;

        public final Instruction ref;
        public final Instruction idx;

        public ArrayGet(WasmId.ArrayType refType, WasmUtil.Extension extension, Instruction ref, Instruction idx) {
            this.refType = refType;
            this.extension = extension;
            this.ref = ref;
            this.idx = idx;
        }
    }

    public static final class ArraySet extends Instruction {

        public final WasmId.ArrayType refType;

        public final Instruction ref;
        public final Instruction idx;
        public final Instruction value;

        public ArraySet(WasmId.ArrayType refType, Instruction ref, Instruction idx, Instruction value) {
            this.refType = refType;
            this.ref = ref;
            this.idx = idx;
            this.value = value;
        }
    }

    /**
     * Represents the {@code array.copy} Instruction.
     * <p>
     * The range {@code [destOffset, destOffset + size)} is copied from {@code dest} to
     * {@code [srcOffset, srcOffset + size)} in {@code src}.
     */
    public static final class ArrayCopy extends Instruction {
        public final WasmId.ArrayType destType;
        public final WasmId.ArrayType srcType;

        public final Instruction dest;
        public final Instruction destOffset;
        public final Instruction src;
        public final Instruction srcOffset;
        public final Instruction size;

        public ArrayCopy(WasmId.ArrayType destType, WasmId.ArrayType srcType, Instruction dest, Instruction destOffset, Instruction src, Instruction srcOffset, Instruction size) {
            this.destType = destType;
            this.srcType = srcType;
            this.dest = dest;
            this.destOffset = destOffset;
            this.src = src;
            this.srcOffset = srcOffset;
            this.size = size;
        }
    }

    /**
     * Represents {@code array.init_data}.
     * <p>
     * Like {@link ArrayCopy}, but the source is {@link #dataSegment}.
     */
    public static final class ArrayInitData extends Instruction {
        public final WasmId.ArrayType type;
        public final WasmId.Data dataSegment;

        public final Instruction dest;
        public final Instruction destOffset;
        public final Instruction srcOffset;
        public final Instruction size;

        public ArrayInitData(WasmId.ArrayType type, WasmId.Data dataSegment, Instruction dest, Instruction destOffset, Instruction srcOffset, Instruction size) {
            this.type = type;
            this.dataSegment = dataSegment;
            this.dest = dest;
            this.destOffset = destOffset;
            this.srcOffset = srcOffset;
            this.size = size;
        }
    }

    /**
     * The {@code extern.convert_any} and {@code any.convert_extern} instructions.
     * <p>
     * Is the former if {@link #isToExtern} is {@code true} and the latter otherwise.
     */
    public static final class AnyExternConversion extends Instruction {
        public final boolean isToExtern;
        public final Instruction input;

        private AnyExternConversion(boolean isToExtern, Instruction input) {
            this.isToExtern = isToExtern;
            this.input = input;
        }

        public static AnyExternConversion toExtern(Instruction input) {
            return new AnyExternConversion(true, input);
        }

        public static AnyExternConversion toAny(Instruction input) {
            return new AnyExternConversion(false, input);
        }
    }
    // endregion
}
