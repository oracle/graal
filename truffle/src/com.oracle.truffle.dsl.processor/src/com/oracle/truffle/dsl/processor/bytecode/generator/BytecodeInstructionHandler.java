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

import static com.oracle.truffle.dsl.processor.bytecode.generator.BytecodeInstructionHandler.ExecutionMode.FAST_PATH;
import static com.oracle.truffle.dsl.processor.bytecode.generator.ElementHelpers.arrayOf;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.firstLetterUpperCase;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel;
import com.oracle.truffle.dsl.processor.bytecode.model.BytecodeDSLModel.LoadIllegalLocalStrategy;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionImmediate;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.QuickeningKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.bytecode.model.ShortCircuitInstructionModel;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature;
import com.oracle.truffle.dsl.processor.bytecode.model.Signature.Operand;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.SpecializationData;

/**
 * Represents the handler methods for instruction execution.
 */
final class BytecodeInstructionHandler extends CodeExecutableElement implements ElementHelpers {

    private final ProcessorContext context;
    private final BytecodeNodeElement parent;
    private final InstructionModel instruction;
    private final TruffleTypes types;
    private final HandlerKind handlerKind;
    private final CodeTree effectGroup;

    @SuppressWarnings("hiding")
    BytecodeInstructionHandler(BytecodeNodeElement parent, InstructionModel instruction, CodeTree effectGroup) {
        super(null, "dummy");
        this.context = parent.context;
        this.types = parent.types;
        this.parent = parent;
        this.effectGroup = effectGroup;
        this.instruction = instruction;
        this.handlerKind = HandlerKind.resolve(instruction, effectGroup != null);

        boolean earlyInline = false;
        boolean needsCounter = false;
        TypeMirror returnType;
        switch (handlerKind) {
            case NONE:
            case THROW:
                returnType = (type(void.class));
                break;
            case STACK_POP:
            case BRANCH:
            case BRANCH_FALSE:
                earlyInline = true;
                returnType = (type(int.class));
                break;
            case BRANCH_BACKWARD:
                earlyInline = true;
                needsCounter = true;
                returnType = type(Object.class);
                break;
            case NEXT:
                returnType = type(int.class);
                break;
            case SHORT_CIRCUIT_BOOLEAN:
            case SHORT_CIRCUIT_VALUE:
                earlyInline = true;
                returnType = type(boolean.class);
                break;
            case YIELD:
            case RETURN:
            case INVALIDATE:
                needsCounter = true;
                returnType = type(long.class);
                break;
            case EFFECT_GROUP:
                Objects.requireNonNull(effectGroup, "Effect group must be set");
                returnType = type(int.class);
                break;
            default:
                throw new AssertionError(handlerKind.toString());
        }

        if (instruction.isInliningCutoff()) {
            this.addAnnotationMirror(new CodeAnnotationMirror(types.HostCompilerDirectives_InliningCutoff));
        }

        parent.initializeInstructionHandler(this, returnType, "handle" + firstLetterUpperCase(instruction.getInternalName()));

        if (earlyInline && getTier().isCached()) {
            this.addAnnotationMirror(new CodeAnnotationMirror(types.CompilerDirectives_EarlyInline));
        }
        if (needsCounter && getTier().isCached()) {
            this.getParameters().add(new CodeVariableElement(type(int.class), "counter"));
        }
    }

    public CodeExecutableElement emit(CodeTreeBuilder caseBuilder) {
        CodeTreeBuilder b = this.createBuilder();
        final Signature signature = instruction.signature;
        final boolean hasUnexpectedReturn = hasExecuteUnexpectedReturn();
        final boolean hasUnexpectedOperand = signature.operands().stream().filter((o) -> isEmitLoadOperand(instruction, o, FAST_PATH)).anyMatch(this::hasUnexpectedValue);

        if (BytecodeRootNodeElement.isStoreBciBeforeExecute(model(), parent.tier, instruction)) {
            parent.storeBciInFrame(b);
        }

        if (instruction.isYield()) {
            emitYieldProlog(b);
        }

        if (isQuickeningRootSlowPath() && getTier().isCached() && instruction.isQuickeningRoot()) {
            emitSlowPath(b, name -> name, "null");
        } else {
            // we compute this ahead of time to find out whether we need to wrap in try-catch
            if (hasUnexpectedOperand) {
                b.startTryBlock();
            }

            emitDynamicOperands(b, (name) -> name, ExecutionMode.FAST_PATH, null);
            emitConstantOperand(b);

            if (hasUnexpectedReturn) {
                b.startTryBlock();
            }

            TypeMirror returnType = emitExecute(b, "result", ExecutionMode.FAST_PATH);
            emitPushResult(b, instruction, returnType, "result", ExecutionMode.FAST_PATH, true);

            if (hasUnexpectedReturn) {
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                emitUnexpectedReturn(b);
                b.end(); // catch
            }

            if (hasUnexpectedOperand) {
                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                emitSlowPath(b, (name) -> name + "_", "ex.getResult()");
                b.end();
            }
        }

        if (handlerKind.isReturn()) {
            emitBeforeReturnProfilingHandler(b);
        }

        // emit instruction prolog

        switch (handlerKind) {
            case NONE:
            case THROW:
            case BRANCH:
            case BRANCH_FALSE:
            case BRANCH_BACKWARD:
            case SHORT_CIRCUIT_BOOLEAN:
            case SHORT_CIRCUIT_VALUE:
                break;
            case NEXT:
                b.statement("return bci + ", String.valueOf(instruction.getInstructionLength()));
                break;
            case STACK_POP:
                b.statement("return stackSize");
                break;
            case EFFECT_GROUP:
                b.startReturn().tree(effectGroup).end();
                break;
            case YIELD:
            case RETURN:
                String returnSp = (instruction.signature.dynamicOperandCount() == 0) ? "sp" : "sp - " + instruction.signature.dynamicOperandCount();
                if (model().overridesBytecodeDebugListenerMethod("afterRootExecute")) {
                    b.startStatement();
                    b.startCall("getRoot().afterRootExecute");
                    parent.parent.emitParseInstruction(b, "this", "bci", CodeTreeBuilder.singleString("readValidBytecode(bc, bci)"));
                    BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class)).string("(", returnSp, ")");
                    b.end();
                    b.string("null");
                    b.end();
                    b.end();
                }
                b.startReturn().string(BytecodeRootNodeElement.encodeReturnState("(" + returnSp + ")")).end();
                break;
            case INVALIDATE:
                b.startReturn().string(parent.parent.encodeState("bci", "sp")).end();
                break;
            default:
                throw new AssertionError();
        }

        emitCaseBlock(caseBuilder);
        return this;
    }

    private void emitCaseBlock(CodeTreeBuilder b) {
        switch (handlerKind) {
            case NONE:
                b.startStatement();
                emitCallHandler(b);
                b.end();
                break;
            case EFFECT_GROUP:
                b.startStatement();
                emitCallHandler(b);
                b.end();
                break;
            case RETURN:
            case YIELD:
            case INVALIDATE:
                b.startReturn();
                emitCallHandler(b);
                b.end();
                break;
            case BRANCH_BACKWARD:
                emitBranchBackwardCaseBlock(b);
                b.statement("break");
                break;
            case STACK_POP:
                b.startStatement();
                b.string("sp -= ");
                emitCallHandler(b);
                if (!instruction.signature.isVoid()) {
                    b.string(" - 1");
                }
                b.end();
                b.statement("bci += " + instruction.getInstructionLength());
                b.statement("break");
                break;
            case SHORT_CIRCUIT_VALUE:
                b.startIf();
                emitCallHandler(b);
                b.end().startBlock();
                b.startAssign("bci").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX))).end();
                // Stack: [..., value, convertedValue]
                // pop convertedValue
                b.statement("sp -= 1");
                b.end().startElseBlock();
                b.statement("bci += " + instruction.getInstructionLength());
                // Stack: [..., value, convertedValue]
                // clear convertedValue and value
                b.statement("sp -= 2");
                b.end();
                b.statement("break");
                break;
            case SHORT_CIRCUIT_BOOLEAN:
                b.startIf();
                emitCallHandler(b);
                b.end().startBlock();
                b.startAssign("bci").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX))).end();

                // Stack: [..., convertedValue]
                // leave convertedValue on the top of stack

                b.end().startElseBlock();
                b.statement("bci += " + instruction.getInstructionLength());
                // Stack: [..., convertedValue]
                // clear convertedValue
                b.statement("sp -= 1");
                b.end();
                b.statement("break");
                break;
            case THROW:
            case NEXT:
            case BRANCH_FALSE:
            case BRANCH:
                boolean bciReturn = ElementUtils.typeEquals(type(int.class), this.getReturnType());
                if (bciReturn) {
                    b.startAssign("bci");
                    emitCallHandler(b);
                    b.end();
                } else {
                    b.startStatement();
                    emitCallHandler(b);
                    b.end();
                    b.startStatement().string("bci += ").string(instruction.getInstructionLength()).end();
                }
                BytecodeNodeElement.emitCustomStackEffect(b, instruction.getStackEffect());
                b.statement("break");
                break;
            default:
                throw new AssertionError();
        }

    }

    private TypeMirror emitExecute(CodeTreeBuilder b, String resultLocalName, ExecutionMode mode) {
        switch (instruction.kind) {
            case DUP:
                return emitDup();
            case YIELD:
                return emitYield(b, resultLocalName);
            case CUSTOM:
                return emitCustom(b, resultLocalName, mode);
            case CUSTOM_SHORT_CIRCUIT:
                return emitCustomShortCircuit(b);
            case LOAD_ARGUMENT:
                return emitLoadArgument(b, resultLocalName, mode);
            case BRANCH:
                b.statement("return " + BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));
                return null;
            case BRANCH_FALSE:
                return emitBranchFalse(b, mode);
            case BRANCH_BACKWARD:
                return emitBranchBackward(b);
            case POP:
                return emitPop(b, mode);
            case CLEAR_LOCAL:
                return emitClearLocal(b);
            case LOAD_LOCAL:
            case LOAD_LOCAL_MATERIALIZED:
                return emitLoadLocal(b, resultLocalName, mode);
            case STORE_LOCAL:
            case STORE_LOCAL_MATERIALIZED:
                return emitStoreLocal(b, mode);
            case LOAD_CONSTANT:
                return emitLoadConstant(b, resultLocalName);
            case LOAD_NULL:
                b.declaration(type(Object.class), resultLocalName, "null");
                return type(Object.class);
            case LOAD_EXCEPTION:
                return emitLoadException(b, resultLocalName);
            case RETURN:
                return null;
            case INVALIDATE:
                emitInvalidate(b);
                return null;
            case MERGE_CONDITIONAL:
                emitMergeConditional(b, resultLocalName, mode);
                return null;
            case THROW:
                return emitThrow(b);
            case CREATE_VARIADIC:
                return emitCreateVariadic(b, resultLocalName);
            case LOAD_VARIADIC:
                return emitLoadVariadic(b);
            case EMPTY_VARIADIC:
                return emitEmptyVariadic(b, resultLocalName);
            case SPLAT_VARIADIC:
                return emitSplatVariadic(b, resultLocalName);
            case TAG_RESUME:
                return emitTagResume(b);
            case TAG_ENTER:
                return emitTagEnter(b);
            case TAG_YIELD:
            case TAG_YIELD_NULL:
                return emitTagYield(b);
            case TAG_LEAVE:
                return emitTagLeave(b, mode);
            case TAG_LEAVE_VOID:
                return emitTagLeaveVoid(b);
            case TRACE_INSTRUCTION:
                return emitTraceInstruction(b);
            default:
                throw new AssertionError("Unhandled instruction.");
        }
    }

    private void emitSlowPath(CodeTreeBuilder b, Function<String, String> nameFunction, String unexpectedResult) {
        b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        if (handlerKind.isBranch()) {
            b.startReturn();
        } else {
            b.startStatement();
        }
        emitCallSlowPath(b, nameFunction, unexpectedResult);
        b.end();
    }

    private void emitCallSlowPath(CodeTreeBuilder b, Function<String, String> nameFunction, String unexpectedResult) {
        BytecodeRootNodeElement.emitCallDefault(b, emitSlowPathMethod(b), (name, sb) -> {
            switch (name) {
                case "frame":
                case "localFrame":
                case "bc":
                case "bci":
                case "sp":
                    sb.string(name);
                    break;
                case "unexpectedResult":
                    if (unexpectedResult == null) {
                        throw new IllegalStateException("Could not bind unexpected result.");
                    }
                    sb.string(unexpectedResult);
                    break;
                default:
                    sb.string(nameFunction.apply(name));
                    break;
            }
        });
    }

    private CodeExecutableElement emitSlowPathMethod(CodeTreeBuilder b) {
        InstructionModel rootInstruction = instruction.getQuickeningRoot();
        CodeExecutableElement method = parent.instructionSlowPaths.get(rootInstruction);
        if (method == null) {
            String methodName = "handle" + firstLetterUpperCase(rootInstruction.getInternalName()) + "$slow";
            method = parent.add(parent.createInstructionHandler(b.findMethod().getReturnType(), methodName));
            String unexpectedResult = null;
            if (findSingleUnexpectedOperand() != null) {
                method.addParameter(new CodeVariableElement(type(Object.class), "unexpectedResult"));
                unexpectedResult = "unexpectedResult";
            }

            TypeMirror returnType;

            if (instruction.isTransparent()) {
                returnType = type(void.class);
            } else if (handlerKind.isBranch()) {
                returnType = type(int.class);
            } else {
                returnType = type(void.class);
            }
            method.setReturnType(returnType);

            CodeTreeBuilder mb = method.createBuilder();

            emitDynamicOperands(mb, (name) -> name, ExecutionMode.SLOW_PATH, unexpectedResult);

            for (Operand operand : rootInstruction.signature.operands()) {
                if (operand.isConstant()) {
                    emitReadConstantOperand(instruction, mb, operand);
                }
            }

            String resultName = "result";
            if (instruction.isTransparent() || handlerKind.isBranch()) {
                resultName = null;
            }
            emitExecute(mb, resultName, ExecutionMode.SLOW_PATH);

            if (resultName != null) {
                emitPushResult(mb, instruction, rootInstruction.signature.returnType(), resultName, ExecutionMode.SLOW_PATH, true);
            }

            parent.instructionSlowPaths.put(rootInstruction, method);
        }
        return method;
    }

    private Operand findSingleUnexpectedOperand() {
        int unexpectedCount = 0;
        Operand found = null;
        for (Operand dynamicOperand : instruction.signature.dynamicOperands()) {
            if (hasUnexpectedValue(dynamicOperand)) {
                unexpectedCount++;
                found = dynamicOperand;
            }
        }
        return unexpectedCount == 1 ? found : null;
    }

    private void emitCallHandler(CodeTreeBuilder caseBuilder) {
        BytecodeRootNodeElement.emitCallDefault(caseBuilder, this, (name, innerB) -> {
            switch (name) {
                case "counter":
                    innerB.string("(");
                    innerB.startStaticCall(types.CompilerDirectives, "inCompiledCode").end();
                    innerB.string(" && ");
                    innerB.startStaticCall(types.CompilerDirectives, "hasNextTier").end();
                    innerB.string(" ? loopCounter.value : counter)");
                    break;
                default:
                    innerB.string(name);
                    break;
            }

        });
    }

    private TypeMirror emitBranchBackward(CodeTreeBuilder b) {
        if (getTier().isCached()) {
            b.startStatement().startStaticCall(types.LoopNode, "reportLoopCount");
            b.string("this");
            b.string("counter");
            b.end().end(); // statement

            b.startIf().startStaticCall(types.CompilerDirectives, "inInterpreter").end().string("&&").startStaticCall(types.BytecodeOSRNode, "pollOSRBackEdge").string("this").string(
                            "counter").end().end().startBlock();
            /**
             * When a while loop is compiled by OSR, its "false" branch profile may be zero, in
             * which case the compiler will stop at loop exits. To coerce the compiler to compile
             * the code after the loop, we encode the branch profile index in the branch.backwards
             * instruction and use it here to force the false profile to a non-zero value.
             */
            InstructionImmediate branchProfile = model().branchBackwardInstruction.findImmediate(ImmediateKind.BRANCH_PROFILE, "loop_header_branch_profile");
            b.declaration(type(int.class), "branchProfileIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", branchProfile));
            b.startStatement().startCall("ensureFalseProfile").tree(BytecodeRootNodeElement.uncheckedCast(arrayOf(type(int.class)), "this.branchProfiles_")).string("branchProfileIndex").end(
                            2);

            b.startReturn();
            b.startStaticCall(types.BytecodeOSRNode, "tryOSR");
            b.string("this");
            String bci = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)).toString();
            b.string(parent.parent.encodeState(bci, "sp", model().hasYieldOperation() ? "frame != " + parent.parent.localFrame() : null));
            b.string("null"); // interpreterState
            b.string("null"); // beforeTransfer
            b.string("frame"); // parentFrame
            b.end(); // static call
            b.end(); // return

            b.end(); // if pollOSRBackEdge
        }

        b.returnNull();
        return null;
    }

    private void emitBranchBackwardCaseBlock(CodeTreeBuilder b) {
        b.startStatement().startStaticCall(types.TruffleSafepoint, "poll").string("this").end().end();
        if (getTier().isUncached()) {
            b.statement("bci = " + BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            b.startIf().string("uncachedExecuteCount_ <= 1").end().startBlock();
            /*
             * The force uncached check is put in here so that we don't need to check it in the
             * common case (the else branch where we just decrement).
             */
            b.startIf().string("uncachedExecuteCount_ != ", BytecodeNodeElement.FORCE_UNCACHED_THRESHOLD).end().startBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.statement("$root.transitionToCached()");
            b.statement("return ", parent.parent.encodeState("bci", "sp"));
            b.end(2);
            b.startElseBlock();
            b.statement("uncachedExecuteCount_--");
            b.end();
        } else {
            b.startIf().startStaticCall(types.CompilerDirectives, "hasNextTier").end().end().startBlock();
            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("counter = ++loopCounter.value");
            b.end().startElseBlock();
            b.statement("counter++");
            b.end();

            b.startIf();
            b.startStaticCall(types.CompilerDirectives, "injectBranchProbability");
            b.staticReference(parent.parent.loopCounter.asType(), "REPORT_LOOP_PROBABILITY");
            b.startGroup();
            b.string("counter >= ").staticReference(parent.parent.loopCounter.asType(), "REPORT_LOOP_STRIDE");
            b.end();
            b.end(); // static call
            b.end().startBlock();

            b.startDeclaration(type(Object.class), "osrResult");
            emitCallHandler(b);
            b.end();

            b.startIf().string("osrResult != null").end().startBlock();
            /**
             * executeOSR invokes BytecodeNode#continueAt, which returns a long encoding the sp and
             * bci when it returns/when the bytecode is rewritten. Returning this value is correct
             * in either case: If it's a return, we'll read the result out of the frame (the OSR
             * code copies the OSR frame contents back into our frame first); if it's a rewrite,
             * we'll transition and continue executing.
             */
            b.startReturn().cast(type(long.class)).string("osrResult").end();
            b.end(); // osrResult != null

            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("loopCounter.value = 0");
            b.end().startElseBlock();
            b.statement("counter = 0");
            b.end();

            b.end(); // if counter >= REPORT_LOOP_STRIDE

            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            b.statement("counter = 0");
            b.end();

            b.end();
            b.statement("bci = " + BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));
        }
    }

    private void emitInvalidate(CodeTreeBuilder b) {
        if (getTier().isCached()) {
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
        }
    }

    private void emitMergeConditional(CodeTreeBuilder b, String resultLocalName, ExecutionMode mode) {
        if (!model().usesBoxingElimination()) {
            throw new AssertionError("Merge.conditional only supports boxing elimination enabled.");
        }
        Operand condition = instruction.signature.dynamicOperands().get(0);
        Operand value = instruction.signature.dynamicOperands().get(1);

        if (mode.isFastPath()) {
            b.startDeclaration(instruction.signature.returnType(), resultLocalName).string(value.localName()).end();
        } else {
            InstructionImmediate operand0 = instruction.getImmediates(ImmediateKind.BYTECODE_INDEX).get(0);
            InstructionImmediate operand1 = instruction.getImmediates(ImmediateKind.BYTECODE_INDEX).get(1);

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "newOtherOperand");
            b.declaration(type(int.class), "operandIndex");
            b.declaration(type(int.class), "otherOperandIndex");

            b.startIf().string(condition.localName()).end().startBlock();
            b.startAssign("operandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand0)).end();
            b.startAssign("otherOperandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand1)).end();
            b.end().startElseBlock();
            b.startAssign("operandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand1)).end();
            b.startAssign("otherOperandIndex").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", operand0)).end();
            b.end();

            b.startIf().string("operandIndex != -1 && otherOperandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));
            b.declaration(type(short.class), "otherOperand", BytecodeRootNodeElement.readInstruction("bc", "otherOperandIndex"));
            InstructionModel genericInstruction = instruction.findQuickening(QuickeningKind.GENERIC, null, false);

            boolean elseIf = false;
            for (TypeMirror boxingType : model().boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string(value.localName()).instanceOf(ElementUtils.boxType(boxingType));
                b.newLine().string("   && (");
                b.string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1)");
                b.end().startBlock();

                InstructionModel boxedInstruction = instruction.findQuickening(QuickeningKind.SPECIALIZED, boxingType, false);
                InstructionModel unboxedInstruction = boxedInstruction.findQuickening(QuickeningKind.SPECIALIZED_UNBOXED, boxingType, false);
                b.startSwitch().tree(BytecodeRootNodeElement.readInstruction("bc", "bci")).end().startBlock();
                b.startCase().tree(parent.parent.createInstructionConstant(boxedInstruction.getQuickeningRoot())).end();
                b.startCase().tree(parent.parent.createInstructionConstant(boxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(parent.parent.createInstructionConstant(boxedInstruction)).end();
                b.statement("break");
                b.end();
                b.startCase().tree(parent.parent.createInstructionConstant(unboxedInstruction)).end();
                b.startCaseBlock();
                b.statement("newOtherOperand = otherOperand");
                b.startAssign("newInstruction").tree(parent.parent.createInstructionConstant(unboxedInstruction)).end();
                b.statement("break");
                b.end();
                b.caseDefault();
                b.startCaseBlock();
                b.statement("newOtherOperand = undoQuickening(otherOperand)");
                b.startAssign("newInstruction").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
                b.statement("break");
                b.end();
                b.end(); // switch

                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = operand");
            b.statement("newOtherOperand = undoQuickening(otherOperand)");
            b.startAssign("newInstruction").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.end();

            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "otherOperandIndex", "otherOperand", "newOtherOperand");

            b.end(); // case both operand indices are valid
            b.startElseBlock();
            b.startAssign("newInstruction").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.end(); // case either operand index is invalid

            parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            b.declaration(type(Object.class), resultLocalName, value.localName());
        }
    }

    private TypeMirror emitCustomShortCircuit(CodeTreeBuilder b) {
        ShortCircuitInstructionModel shortCircuitInstruction = instruction.shortCircuitModel;

        b.startIf();

        if (getTier().isCached()) {
            b.startCall("profileBranch");
            b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BRANCH_PROFILE)));
            b.startGroup();
        }

        if (shortCircuitInstruction.continueWhen()) {
            b.string("!");
        }

        b.string(instruction.signature.dynamicOperands().getLast().localName());

        if (getTier().isCached()) {
            b.end(2); // profileBranch call
        }

        b.end().startBlock();
        /*
         * NB: Short circuit operations can evaluate to an operand or to the boolean conversion of
         * an operand. The stack is different in either case.
         */
        if (shortCircuitInstruction.producesBoolean()) {
            // Stack: [..., convertedValue]
            // leave convertedValue on the top of stack
        } else {
            // Stack: [..., value, convertedValue]
            // pop convertedValue
            emitClearStackValue(b, "sp - 1", type(boolean.class), FAST_PATH);
        }
        b.statement("return true");
        b.end().startElseBlock();
        if (shortCircuitInstruction.producesBoolean()) {
            emitClearStackValue(b, "sp - 1", type(boolean.class), FAST_PATH);
        } else {
            // Stack: [..., value, convertedValue]
            // clear convertedValue and value
            emitClearStackValue(b, "sp - 1", type(boolean.class), FAST_PATH);
            emitClearStackValue(b, "sp - 2", type(Object.class), FAST_PATH);
        }

        b.statement("return false");
        b.end(); // else

        return type(boolean.class);
    }

    private TypeMirror emitDup() {
        // nothing to do. handled by emitReturn
        return type(Object.class);
    }

    private TypeMirror emitEmptyVariadic(CodeTreeBuilder b, String resultLocalName) {
        b.startDeclaration(type(Object[].class), resultLocalName);
        b.string("EMPTY_ARRAY");
        b.end();
        return type(Object[].class);
    }

    private TypeMirror emitCreateVariadic(CodeTreeBuilder b, String resultLocalName) {
        b.startDeclaration(type(int.class), "count");
        b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.INTEGER, "count")));
        b.end();
        InstructionImmediate offsetImmediate = instruction.findImmediate(ImmediateKind.INTEGER, "offset");
        if (offsetImmediate != null) {
            b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", offsetImmediate));
        }
        if (model().hasVariadicReturn) {
            b.declaration(type(int.class), "mergeCount", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.SHORT, "merge_count")));
        }
        b.declaration(type(int.class), "stackSize", "Math.min(count, VARIADIC_STACK_LIMIT)");
        b.startDeclaration(type(Object[].class), resultLocalName);
        BytecodeRootNodeElement.emitCallDefault(b, parent.add(createExecuteCreateVariadic(instruction)));
        b.end();
        return null;
    }

    private CodeExecutableElement createExecuteCreateVariadic(InstructionModel instr) {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(Object[].class), "executeCreateVariadic");
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        method.addParameter(new CodeVariableElement(type(int.class), "count"));
        method.addParameter(new CodeVariableElement(type(int.class), "stackSize"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));

        InstructionImmediate offsetImmediate = instr.findImmediate(ImmediateKind.INTEGER, "offset");
        if (offsetImmediate != null) {
            method.addParameter(new CodeVariableElement(type(int.class), "offset"));
        }

        if (model().hasVariadicReturn) {
            method.addParameter(new CodeVariableElement(type(int.class), "mergeCount"));
        }

        method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));

        CodeTreeBuilder b = method.createBuilder();

        String addOffset = (offsetImmediate == null ? "" : "offset + ");

        b.declaration(type(int.class), "newSize", addOffset + "count");
        if (model().hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement("newSize += dynamicArray.length - 1");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
        }

        b.declaration(type(Object[].class), "result", "new Object[newSize]");

        if (model().hasVariadicReturn) {
            b.startFor().string("int i = 0; i < stackSize - mergeCount; i++").end().startBlock();
        } else {
            b.startFor().string("int i = 0; i < stackSize; i++").end().startBlock();
        }
        b.startStatement();
        if (offsetImmediate == null) {
            b.string("result[i] = ");
        } else {
            b.string("result[offset + i] = ");
        }
        b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - stackSize + i"));
        b.end();

        emitClearStackValue(b, "sp - stackSize + i", type(Object.class), FAST_PATH);

        b.end();

        if (model().hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.declaration(type(int.class), "mergeIndex", addOffset + "stackSize - mergeCount");
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();

            emitClearStackValue(b, "sp - mergeCount + i", type(Object[].class), FAST_PATH);

            b.declaration(type(int.class), "dynamicLength", "dynamicArray.length");
            b.startStatement().startStaticCall(type(System.class), "arraycopy");
            b.string("dynamicArray").string("0").string("result").string("mergeIndex").string("dynamicLength");
            b.end().end(); // static call, statement

            b.statement("mergeIndex += dynamicLength");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
        }

        b.statement("return result");

        return method;
    }

    private TypeMirror emitLoadVariadic(CodeTreeBuilder b) {
        b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.INTEGER, "offset")));

        b.startDeclaration(type(int.class), "stackSize");
        b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.SHORT, "count")));
        b.end();

        if (model().hasVariadicReturn) {
            b.declaration(type(int.class), "mergeCount", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.SHORT, "merge_count")));
        }

        b.startDeclaration(type(Object[].class), "array");
        b.startCall("ACCESS.uncheckedCast");
        b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - stackSize - 1"));
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end();

        if (model().hasVariadicReturn) {
            b.startAssign("array");
        } else {
            b.startStatement();
        }
        BytecodeRootNodeElement.emitCallDefault(b, parent.add(createExecuteLoadVariadic(instruction)), (name, sb) -> {
            switch (name) {
                case "count":
                    sb.string("stackSize");
                    break;
                default:
                    sb.string(name);
                    break;
            }
        });
        b.end();

        if (model().hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.statement(BytecodeRootNodeElement.setFrameObject("frame", "sp - stackSize - 1", "array"));
            b.end();
        }

        return null;
    }

    private CodeExecutableElement createExecuteLoadVariadic(InstructionModel instr) {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(Object[].class), "executeLoadVariadic");
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        method.addParameter(new CodeVariableElement(type(Object[].class), "array"));
        method.addParameter(new CodeVariableElement(type(int.class), "count"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));

        InstructionImmediate offsetImmediate = instr.findImmediate(ImmediateKind.INTEGER, "offset");
        if (offsetImmediate != null) {
            method.addParameter(new CodeVariableElement(type(int.class), "offset"));
        }

        if (model().hasVariadicReturn) {
            method.addParameter(new CodeVariableElement(type(int.class), "mergeCount"));
            method.setReturnType(type(Object[].class));
        } else {
            method.setReturnType(type(void.class));
        }

        method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));

        CodeTreeBuilder b = method.createBuilder();

        b.declaration(type(Object[].class), "result", "array");
        if (model().hasVariadicReturn) {
            b.declaration(type(int.class), "newSize", "offset + count");
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            b.statement("newSize += dynamicArray.length - 1");

            b.end(); // for mergeDynamicCount

            b.startStatement().string("result = ");
            b.startStaticCall(type(Arrays.class), "copyOf").string("result").string("newSize").end();
            b.end(); // statement
            b.end(); // if mergeDynamicCount > 0

            b.startFor().string("int i = 0; i < count - mergeCount; i++").end().startBlock();
        } else {
            b.startFor().string("int i = 0; i < count; i++").end().startBlock();
        }
        b.startStatement();
        b.string("result[offset + i] = ").string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - count + i"));
        b.end();

        emitClearStackValue(b, "sp - count + i", type(Object.class), FAST_PATH);
        b.end(); // for

        if (model().hasVariadicReturn) {
            b.startIf().string("mergeCount > 0").end().startBlock();
            b.declaration(type(int.class), "mergeIndex", "offset + count - mergeCount");
            b.startFor().string("int i = 0; i < mergeCount; i++").end().startBlock();

            b.startDeclaration(type(Object[].class), "dynamicArray");
            b.startCall("ACCESS.uncheckedCast");
            b.string(BytecodeRootNodeElement.uncheckedGetFrameObject("sp - mergeCount + i"));
            b.typeLiteral(type(Object[].class));
            b.end();
            b.end();
            emitClearStackValue(b, "sp - mergeCount + i", type(Object[].class), FAST_PATH);

            b.declaration(type(int.class), "dynamicLength", "dynamicArray.length");
            b.startStatement().startStaticCall(type(System.class), "arraycopy");
            b.string("dynamicArray").string("0").string("result").string("mergeIndex").string("dynamicLength");
            b.end().end(); // static call, statement

            b.statement("mergeIndex += dynamicLength");

            b.end(); // for mergeDynamicCount
            b.end(); // if mergeDynamicCount > 0
            b.statement("return result");
        }

        return method;
    }

    private TypeMirror emitSplatVariadic(CodeTreeBuilder b, String resultLocalName) {
        b.declaration(type(int.class), "offset", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.INTEGER, "offset")));
        b.declaration(type(int.class), "count", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.INTEGER, "count")));

        Operand operand = instruction.signature.singleDynamicOperand();
        b.startDeclaration(type(Object[].class), resultLocalName);
        BytecodeRootNodeElement.emitCallDefault(b, parent.add(createExecuteSplatVariadic()), (name, sb) -> {
            switch (name) {
                case "frame":
                case "count":
                case "sp":
                case "offset":
                    sb.string(name);
                    break;
                case "array":
                    sb.string(operand.localName());
                    break;
                default:
                    throw new AssertionError("Invalid argument");
            }
        });
        b.end();
        return type(Object[].class);
    }

    private CodeExecutableElement createExecuteSplatVariadic() {
        CodeExecutableElement method = new CodeExecutableElement(Set.of(PRIVATE), type(Object[].class), "executeSplatVariadic");
        method.addParameter(new CodeVariableElement(types.FrameWithoutBoxing, "frame"));
        method.addParameter(new CodeVariableElement(type(Object[].class), "array"));
        method.addParameter(new CodeVariableElement(type(int.class), "count"));
        method.addParameter(new CodeVariableElement(type(int.class), "sp"));

        InstructionImmediate offsetImmediate = instruction.findImmediate(ImmediateKind.INTEGER, "offset");
        if (offsetImmediate != null) {
            method.addParameter(new CodeVariableElement(type(int.class), "offset"));
        }

        method.setReturnType(type(Object[].class));
        method.getAnnotationMirrors().add(new CodeAnnotationMirror(types.ExplodeLoop));

        CodeTreeBuilder b = method.createBuilder();
        b.declaration(type(int.class), "newSize", "array.length");
        b.startFor().string("int i = 0; i < count; i++").end().startBlock();

        b.startDeclaration(type(Object[].class), "dynamicArray");
        b.startCall("ACCESS.uncheckedCast");
        b.string("array[offset + i]");
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end();
        b.statement("newSize += dynamicArray.length - 1");

        b.end(); // for count

        b.declaration(type(Object[].class), "newArray", "new Object[newSize]");

        b.lineComment("copy prefixed elements");
        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("array").string("0").string("newArray").string("0").string("offset");
        b.end().end();

        // copy dynamic arrays
        b.lineComment("copy dynamic elements");
        b.declaration(type(int.class), "mergeIndex", "offset");
        b.startFor().string("int i = 0; i < count; i++").end().startBlock();
        b.startDeclaration(type(Object[].class), "dynamicArray");
        b.startCall("ACCESS.uncheckedCast");
        b.string("array[offset + i]");
        b.typeLiteral(type(Object[].class));
        b.end();
        b.end(); // declaration

        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("dynamicArray").string("0").string("newArray").string("mergeIndex").string("dynamicArray.length");
        b.end().end();
        b.statement("mergeIndex += dynamicArray.length");
        b.end(); // for count

        b.lineComment("copy suffix elements");
        b.startStatement().startStaticCall(type(System.class), "arraycopy");
        b.string("array").string("offset + count").string("newArray").string("mergeIndex").string("array.length - offset - count");
        b.end().end();

        b.statement("return newArray");

        return method;
    }

    private TypeMirror emitTraceInstruction(CodeTreeBuilder b) {
        b.startStatement();
        b.tree(parent.parent.readConstFastPath(CodeTreeBuilder.singleString("Builder.INSTRUCTION_TRACER_CONSTANT_INDEX"), "this.constants",
                        parent.parent.instructionTracerAccessImplElement.asType()));
        b.startCall(".onInstructionEnter").string("this").string("bci").string(parent.parent.localFrame()).end();
        b.end(); // statement
        return null;
    }

    private TypeMirror emitClearLocal(CodeTreeBuilder b) {
        String index = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.FRAME_INDEX)).toString();
        if (model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.DEFAULT_VALUE) {
            b.statement(BytecodeRootNodeElement.setFrameObject("frame", index, "DEFAULT_LOCAL_VALUE"));
        } else {
            b.statement(BytecodeRootNodeElement.clearFrame("frame", index));
        }
        return null;
    }

    private TypeMirror emitThrow(CodeTreeBuilder b) {
        b.statement("throw sneakyThrow(", instruction.signature.singleDynamicOperand().localName(), ")");
        return null;
    }

    private TypeMirror emitLoadException(CodeTreeBuilder b, String resultLocalName) {
        b.startDeclaration(type(Object.class), resultLocalName);
        BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", type(Object.class));
        b.startGroup().string("getRoot().maxLocals + ").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.STACK_POINTER))).end();
        b.end(); // getFrameUnsafe
        b.end(); // declaration
        return type(Throwable.class);
    }

    private TypeMirror emitTagLeave(CodeTreeBuilder b, ExecutionMode mode) {
        Operand valueOperand = instruction.signature.singleDynamicOperand();
        if (mode.isFastPath()) {
            InstructionImmediate imm = instruction.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
            b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
            b.end();
            b.startStatement().startCall("tagNode.findProbe().onReturnValue");
            b.string(parent.parent.localFrame());
            b.string(valueOperand.localName());
            b.end().end();
        } else { // slow-path

            Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
            InstructionModel genericInstruction = null;
            for (InstructionModel quickening : instruction.quickenedInstructions) {
                switch (quickening.quickeningKind) {
                    case SPECIALIZED -> {
                        if (!model().isBoxingEliminated(quickening.specializedType)) {
                            throw new AssertionError();
                        }
                        typeToSpecialization.put(quickening.specializedType, quickening);
                    }
                    case GENERIC -> genericInstruction = quickening;
                    default -> throw new AssertionError("Unexpected tag.leave quickening kind " + quickening.quickeningKind);
                }
            }

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

            boolean elseIf = false;
            for (var entry : typeToSpecialization.entrySet()) {
                TypeMirror typeGroup = entry.getKey();
                elseIf = b.startIf(elseIf);
                b.string(valueOperand.localName(), " instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
                b.newLine().string("     (newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
                b.end().startBlock();

                InstructionModel specialization = entry.getValue();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(specialization)).end();
                b.end(); // else block
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = undoQuickening(operand)");
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.end();

            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            InstructionImmediate imm = instruction.getImmediate(ImmediateKind.TAG_NODE);
            b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
            b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
            b.end();
            b.startStatement().startCall("tagNode.findProbe().onReturnValue");
            b.string(parent.parent.localFrame());
            b.string(valueOperand.localName());
            b.end(2);
        }
        return null;
    }

    private TypeMirror emitTagLeaveVoid(CodeTreeBuilder b) {
        b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.TAG_NODE))));
        b.end();
        b.startStatement().startCall("tagNode.findProbe().onReturnValue");
        b.string(parent.parent.localFrame());
        b.string("null");
        b.end(2);
        return null;
    }

    private TypeMirror emitTagYield(CodeTreeBuilder b) throws AssertionError {
        Operand operand = switch (instruction.kind) {
            case TAG_YIELD -> instruction.signature.singleDynamicOperand();
            case TAG_YIELD_NULL -> null;
            default -> throw new AssertionError("unexpected tag yield instruction " + instruction);
        };

        InstructionImmediate imm = instruction.getImmediate(ImmediateKind.TAG_NODE);
        b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", imm)));
        b.end();

        b.startStatement().startCall("tagNode.findProbe().onYield");
        b.string(parent.parent.localFrame());
        if (operand != null) {
            b.string(operand.localName());
        } else {
            b.string("null");
        }
        b.end().end();
        return null;
    }

    private TypeMirror emitTagEnter(CodeTreeBuilder b) {
        b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.TAG_NODE))));
        b.end();
        b.startStatement().startCall("tagNode.findProbe().onEnter").string(parent.parent.localFrame()).end(2);
        return null;
    }

    private TypeMirror emitTagResume(CodeTreeBuilder b) {
        b.startDeclaration(parent.parent.tagNode.asType(), "tagNode");
        b.tree(BytecodeRootNodeElement.readTagNode(parent.parent.tagNode.asType(), BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.TAG_NODE))));
        b.end();
        b.startStatement().startCall("tagNode.findProbe().onResume").string(parent.parent.localFrame()).end(2);
        return null;
    }

    private TypeMirror emitLoadConstant(CodeTreeBuilder b, String resultLocalName) {
        TypeMirror returnType = instruction.signature.returnType();
        if (getTier().isUncached() || (model().usesBoxingElimination() && !ElementUtils.isObject(returnType))) {
            b.startDeclaration(returnType, resultLocalName);
            b.tree(parent.parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.CONSTANT)), "this.constants", returnType));
            b.end();
            b.end();
        } else {
            b.declaration(returnType, resultLocalName);
            b.startIf().startStaticCall(types.HostCompilerDirectives, "inInterpreterFastPath").end(2).startBlock();
            b.statement(resultLocalName, " = ",
                            parent.parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.CONSTANT)), "this.constants").toString());
            b.end().startElseBlock();
            b.statement(resultLocalName, " = loadConstantCompiled(frame, bc, bci)");
            b.end();
        }
        return returnType;
    }

    private TypeMirror emitPop(CodeTreeBuilder b, ExecutionMode mode) {
        Operand valueOperand = instruction.signature.singleDynamicOperand();
        if (mode.isFastPath()) {
            if (ElementUtils.isObject(valueOperand.type())) {
                emitClearStackValue(b, "sp - 1", valueOperand.type(), mode);
            } else {
                b.startIf().string("frame.getTag(sp - 1) != ").staticReference(parent.parent.frameTagsElement.get(valueOperand.type())).end().startBlock();
                emitSlowPath(b, name -> name, "null");
                b.end().startElseBlock();
                emitClearStackValue(b, "sp - 1", valueOperand.type(), mode);
                b.end();
            }
        } else { // slow-path
            emitClearStackValue(b, "sp - 1", valueOperand.type(), mode);
            Map<TypeMirror, InstructionModel> typeToSpecialization = new LinkedHashMap<>();
            InstructionModel genericInstruction = null;
            for (InstructionModel quickening : instruction.quickenedInstructions) {
                switch (quickening.quickeningKind) {
                    case SPECIALIZED -> {
                        if (!model().isBoxingEliminated(quickening.specializedType)) {
                            throw new AssertionError();
                        }
                        typeToSpecialization.put(quickening.specializedType, quickening);
                    }
                    case GENERIC -> genericInstruction = quickening;
                    default -> throw new AssertionError("Unexpected pop quickening kind " + quickening.quickeningKind);
                }
            }

            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));

            // Pop may not have a valid child bci.
            b.startIf().string("operandIndex != -1").end().startBlock();

            b.declaration(type(short.class), "newOperand");
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

            boolean elseIf = false;
            for (var entry : typeToSpecialization.entrySet()) {
                TypeMirror typeGroup = entry.getKey();
                elseIf = b.startIf(elseIf);
                b.string(valueOperand.localName(), " instanceof ").type(ElementUtils.boxType(typeGroup)).string(" && ");
                b.newLine().string("     (newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(typeGroup)).string("operand").end().string(") != -1");
                b.end().startBlock();

                InstructionModel specialization = entry.getValue();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(specialization)).end();
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.statement("newOperand = undoQuickening(operand)");
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.end();

            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");

            b.end(); // case operandIndex != -1
            b.startElseBlock();
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.end(); // case operandIndex == -1

            parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");
        }
        return null;
    }

    private TypeMirror emitLoadArgument(CodeTreeBuilder b, String resultLocalName, ExecutionMode mode) {
        InstructionImmediate argIndex = instruction.getImmediate(ImmediateKind.SHORT);
        if (mode.isFastPath()) {
            if (instruction.isReturnTypeQuickening()) {
                TypeMirror returnType = instruction.signature.returnType();
                b.startDeclaration(returnType, resultLocalName);
                b.startStaticCall(parent.parent.lookupExpectMethod(type(Object.class), returnType));
                b.startGroup();
                b.startCall(parent.parent.localFrame(), "getArguments").end();
                b.string("[").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", argIndex)).string("]");
                b.end(); // expect
                b.end(); // group
                b.end(); // declaration
                return returnType;
            } else {
                b.startDeclaration(type(Object.class), resultLocalName);
                b.startCall(parent.parent.localFrame(), "getArguments").end();
                b.string("[").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", argIndex)).string("]");
                b.end(); // declaration
                return type(Object.class);
            }
        } else { // slow-path
            parent.parent.emitQuickening(b, "this", "bc", "bci", null,
                            b.create().tree(parent.parent.createInstructionConstant(instruction.getQuickeningRoot())).build());

            b.startDeclaration(type(Object.class), resultLocalName);
            b.startCall(parent.parent.localFrame(), "getArguments").end();
            b.string("[").tree(BytecodeRootNodeElement.readImmediate("bc", "bci", argIndex)).string("]");
            b.end(); // declaration
            return type(Object.class);
        }
    }

    private TypeMirror emitLoadLocal(CodeTreeBuilder b, String resultLocalName, ExecutionMode mode) {
        final boolean materialized = instruction.kind.isLocalVariableMaterializedAccess();
        // Local loads are quickened for BE and to optimize away clear checks.
        final boolean loadLocalQuickenable = model().usesBoxingElimination() ||
                        (model().enableQuickening && model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION);

        final boolean readLocalIndex = instruction.isQuickening() || getTier().isUncached() || !loadLocalQuickenable;
        final boolean checked;
        if (instruction.isQuickening()) {
            checked = instruction.checked;
        } else {
            checked = model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION;
        }

        final CodeTree slot;
        if (materialized || checked || !readLocalIndex) {
            b.declaration(type(int.class), "slot", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.FRAME_INDEX)));
            slot = CodeTreeBuilder.singleString("slot");
        } else {
            slot = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.FRAME_INDEX));
        }

        final CodeTree localIndex;
        if (model().localAccessesNeedLocalIndex()) {
            if (materialized || checked || !readLocalIndex) {
                b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = CodeTreeBuilder.singleString("localIndex");
            } else {
                localIndex = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_INDEX));
            }
        } else {
            localIndex = CodeTreeBuilder.createBuilder().tree(slot).string(" - ").string(BytecodeRootNodeElement.USER_LOCALS_START_INDEX).build();
        }

        final String localsFrame;
        if (materialized) {
            Operand materializedFrame = instruction.signature.singleDynamicOperand();
            localsFrame = materializedFrame.localName();
        } else {
            localsFrame = parent.parent.localFrame();
        }

        if (materialized) {
            b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_ROOT)));
        }

        final CodeTree bytecodeNode;
        if (materialized) {
            b.startDeclaration(parent.parent.asType(), "localRoot");
            b.startCall("this.getRoot().getBytecodeRootNodeImpl").string("localRootIndex").end();
            b.end();
            CodeTree localRoot = CodeTreeBuilder.singleString("localRoot");

            if (readLocalIndex) {
                bytecodeNode = CodeTreeBuilder.createBuilder().startCall(localRoot, "getBytecodeNodeImpl").end().build();
            } else {
                b.startDeclaration(parent.parent.abstractBytecodeNode.asType(), "bytecodeNode");
                b.startCall(localRoot, "getBytecodeNodeImpl").end();
                b.end();
                bytecodeNode = CodeTreeBuilder.singleString("bytecodeNode");
            }

            emitValidateMaterializedAccess(b, localsFrame, localRoot, bytecodeNode, localIndex);
        } else {
            bytecodeNode = CodeTreeBuilder.singleString("this");
        }

        if (mode.isFastPath()) {
            // Fast path: execute load local.
            final TypeMirror resultType = instruction.signature.returnType();
            final TypeMirror slotType = instruction.specializedType != null ? instruction.specializedType : type(Object.class);

            if (checked) {
                b.startIf();
                b.startCall(localsFrame, "getTag").tree(slot).end();
                b.string(" == ");
                b.staticReference(parent.parent.frameTagsElement.getIllegal());
                b.end().startBlock();
                BytecodeNodeElement.emitThrowIllegalLocalException(model(), b, CodeTreeBuilder.singleString("bci"), bytecodeNode, localIndex, true);
                b.end();
            }
            boolean specialized = instruction.quickeningKind.isSpecialized();
            boolean needsCatchIllegal = specialized && model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION;
            if (needsCatchIllegal) {
                b.declaration(resultType, resultLocalName);
                b.startTryBlock();
                b.startAssign(resultLocalName);
            } else {
                b.startDeclaration(resultType, resultLocalName);
            }

            if (instruction.specializedType != null) {
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, slotType).tree(slot).end();
            } else {
                BytecodeRootNodeElement.startRequireFrame(b, slotType).string(localsFrame).tree(slot).end();
            }

            b.end(); // assignment

            if (needsCatchIllegal) {
                b.end().startCatchBlock(types.FrameSlotTypeException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startStatement();
                emitCallSlowPath(b, name -> name, null);
                b.end(); // statement
                b.statement("return bci + ", String.valueOf(instruction.getInstructionLength()));

                b.end(); // catch
            }
            return resultType;
        } else { // slow-path
            // Slow path: quicken.
            if (model().usesBoxingElimination()) {
                // Use cached local tag to determine a quickening.
                if (parent.localAccessNeedsLocalTags(instruction)) {
                    b.declaration(type(byte[].class), "localTags", parent.readLocalTagsFastPath());
                }

                b.startDeclaration(type(byte.class), "tag");
                b.startCall(bytecodeNode, "getCachedLocalTagInternal");
                if (materialized) {
                    b.startCall(bytecodeNode, "getLocalTags").end();
                } else {
                    b.string("localTags");
                }
                b.tree(localIndex);
                b.end(); // call
                b.end(); // declaration

                if (model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION) {
                    b.startIf();
                    b.startCall(localsFrame, "getTag").string("slot").end();
                    b.string(" == ");
                    b.staticReference(parent.parent.frameTagsElement.getIllegal());
                    b.end().startBlock();
                    // If tag illegal, quicken to checked. Update the cached tags if
                    // necessary.
                    b.startIf().string("tag != ").staticReference(parent.parent.frameTagsElement.getObject()).end().startBlock();
                    b.startStatement().startCall(bytecodeNode, "setCachedLocalTagInternal");
                    if (materialized) {
                        b.startCall(bytecodeNode, "getLocalTags").end();
                    } else {
                        b.string("localTags");
                    }
                    b.tree(localIndex);
                    b.staticReference(parent.parent.frameTagsElement.getObject());
                    b.end(2);
                    b.end(); // if cached tag != Object
                    parent.parent.emitQuickening(b, "this", "bc", "bci", null, parent.parent.createInstructionConstant(instruction.findQuickening(QuickeningKind.GENERIC, null, true)));
                    BytecodeNodeElement.emitThrowIllegalLocalException(model(), b, CodeTreeBuilder.singleString("bci"), bytecodeNode, localIndex, false);
                    b.end(); // if
                }

                b.declaration(type(Object.class), resultLocalName);
                b.declaration(type(short.class), "newInstruction");
                InstructionModel genericTypeInstruction;
                if (model().loadIllegalLocalStrategy == LoadIllegalLocalStrategy.CUSTOM_EXCEPTION) {
                    genericTypeInstruction = instruction.findQuickening(QuickeningKind.SPECIALIZED, null, false);
                } else {
                    genericTypeInstruction = instruction.findQuickening(QuickeningKind.GENERIC, null, false);
                }

                b.startTryBlock();

                b.startSwitch().string("tag").end().startBlock();
                for (TypeMirror boxingType : model().boxingEliminatedTypes) {
                    InstructionModel boxedInstruction = instruction.findQuickening(QuickeningKind.SPECIALIZED, boxingType, false);

                    b.startCase().staticReference(parent.parent.frameTagsElement.get(boxingType)).end();
                    b.startCaseBlock();
                    b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(boxedInstruction)).end();
                    parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + boxedInstruction.getQuickeningName());
                    b.startStatement();
                    b.string("result = ");
                    BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, boxingType).string("slot").end();
                    b.end();
                    b.statement("break");
                    b.end();
                }

                b.startCase().staticReference(parent.parent.frameTagsElement.getObject()).end();
                b.startCase().staticReference(parent.parent.frameTagsElement.getIllegal()).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericTypeInstruction)).end();
                parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + genericTypeInstruction.getQuickeningName());
                b.startStatement();
                b.string(resultLocalName, " = ");
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, localsFrame, type(Object.class)).string("slot").end();
                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
                b.end();

                b.end(); // switch

                b.end().startCatchBlock(types.UnexpectedResultException, "ex");
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());

                // If an UnexpectedResultException occurs, specialize to the generic
                // version.
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericTypeInstruction)).end();
                parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "LoadLocal$" + genericTypeInstruction.getQuickeningName());
                b.startStatement();
                b.string(resultLocalName, " = ex.getResult()");
                b.end();
                b.end(); // catch

                parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");
            } else {
                // Use frame tag to determine a quickening.
                if (model().loadIllegalLocalStrategy != LoadIllegalLocalStrategy.CUSTOM_EXCEPTION) {
                    throw new AssertionError("Unexpected load local quickening. The only supported quickening for local loads outside of BE is for illegalLocalExceptions.");
                }

                b.startIf();
                b.startCall(localsFrame, "getTag").string("slot").end();
                b.string(" == ");
                b.staticReference(parent.parent.frameTagsElement.getIllegal());
                b.end().startBlock();
                // If tag illegal, quicken to checked.
                parent.parent.emitQuickening(b, "this", "bc", "bci", null, parent.parent.createInstructionConstant(instruction.findQuickening(QuickeningKind.GENERIC, null, true)));
                BytecodeNodeElement.emitThrowIllegalLocalException(model(), b, CodeTreeBuilder.singleString("bci"), bytecodeNode, localIndex, false);
                b.end(); // if

                // If tag not illegal, quicken to unchecked.
                parent.parent.emitQuickening(b, "this", "bc", "bci", null, parent.parent.createInstructionConstant(instruction.findQuickening(QuickeningKind.SPECIALIZED, null, false)));
                b.startDeclaration(type(Object.class), "result");
                BytecodeRootNodeElement.startRequireFrame(b, type(Object.class)).string(localsFrame).string("slot").end();
                b.end(2);
            }
            return type(Object.class);
        }
    }

    /**
     * Helper that emits common validation code for materialized local reads/writes.
     */
    private void emitValidateMaterializedAccess(CodeTreeBuilder b, String frame, CodeTree localRoot, CodeTree bytecodeNode, CodeTree localIndex) {
        b.startIf().tree(localRoot).string(".getFrameDescriptor() != ", frame, ".getFrameDescriptor()");
        b.end().startBlock();
        BytecodeRootNodeElement.emitThrowIllegalArgumentException(b, "Materialized frame belongs to the wrong root node.");
        b.end();

        // Check that the local is live at the current bci.
        if (model().canValidateMaterializedLocalLiveness()) {
            b.startAssert().startCall(bytecodeNode, "validateLocalLivenessInternal");
            b.string(frame);
            b.string("slot");
            b.tree(localIndex);
            b.string("frame");
            b.string("bci");
            b.end(2);
        }
    }

    private TypeMirror emitStoreLocal(CodeTreeBuilder b, ExecutionMode mode) {
        boolean materialized = instruction.kind.isLocalVariableMaterializedAccess();
        boolean fastPath = instruction.isQuickening() || getTier().isUncached() || !model().usesBoxingElimination();
        final TypeMirror inputType = instruction.signature.getDynamicOperandType(0);
        final TypeMirror slotType = instruction.specializedType != null ? instruction.specializedType : type(Object.class);
        final boolean generic = ElementUtils.typeEquals(type(Object.class), inputType) && ElementUtils.typeEquals(inputType, slotType);

        Operand value = instruction.signature.dynamicOperands().get(instruction.signature.dynamicOperandCount() - 1);

        final String localsFrame;
        if (materialized) {
            Operand materializedFrame = instruction.signature.dynamicOperands().get(instruction.signature.dynamicOperandCount() - 2);
            localsFrame = materializedFrame.localName();
        } else {
            localsFrame = parent.parent.localFrame();
        }

        final CodeTree slot;
        if (!generic || materialized || !fastPath) {
            b.declaration(type(int.class), "slot", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.FRAME_INDEX)));
            slot = CodeTreeBuilder.singleString("slot");
        } else {
            slot = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.FRAME_INDEX));
        }

        if (materialized) {
            b.declaration(type(int.class), "localRootIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_ROOT)));
        }

        final CodeTree localIndex;
        if (model().localAccessesNeedLocalIndex()) {
            if (!generic || materialized || !fastPath) {
                b.declaration(type(int.class), "localIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_INDEX)));
                localIndex = CodeTreeBuilder.singleString("localIndex");
            } else {
                localIndex = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.LOCAL_INDEX));
            }
        } else {
            localIndex = CodeTreeBuilder.createBuilder().tree(slot).string(" - ").string(BytecodeRootNodeElement.USER_LOCALS_START_INDEX).build();
        }

        final CodeTree bytecodeNode;
        if (materialized) {
            b.startDeclaration(parent.parent.asType(), "localRoot");
            b.startCall("this.getRoot().getBytecodeRootNodeImpl").string("localRootIndex").end();
            b.end();
            CodeTree localRoot = CodeTreeBuilder.singleString("localRoot");

            b.startDeclaration(parent.parent.abstractBytecodeNode.asType(), "bytecodeNode");
            b.startCall(localRoot, "getBytecodeNodeImpl").end();
            b.end();
            bytecodeNode = CodeTreeBuilder.singleString("bytecodeNode");

            emitValidateMaterializedAccess(b, localsFrame, localRoot, bytecodeNode, localIndex);
        } else {
            bytecodeNode = CodeTreeBuilder.singleString("this");
        }

        if (parent.localAccessNeedsLocalTags(instruction) && (!generic || !fastPath)) {
            b.declaration(type(byte[].class), "localTags", parent.readLocalTagsFastPath());
        }

        if (mode.isFastPath()) {
            // Fast path: execute load local.
            if (generic) {
                if (materialized) {
                    if (model().usesBoxingElimination()) {
                        CodeTree localOffset = CodeTreeBuilder.singleString("slot - " + BytecodeRootNodeElement.USER_LOCALS_START_INDEX);
                        // We need to update the tags. Call the setter method on the
                        // bytecodeNode.
                        b.startStatement().startCall(bytecodeNode, "setLocalValueInternal");
                        b.string(localsFrame);
                        b.tree(localOffset);
                        if (model().localAccessesNeedLocalIndex()) {
                            b.tree(localIndex);
                        } else {
                            b.tree(localOffset);
                        }
                        b.string(value.localName());
                        b.end(2);
                    } else {
                        b.startStatement();
                        BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).tree(slot);
                        b.string(value.localName());
                        b.end();
                        b.end();
                    }
                } else {
                    b.startStatement();
                    BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).tree(slot);
                    b.string(value.localName());
                    b.end();
                    b.end();
                }
            } else {
                if (!model().usesBoxingElimination()) {
                    throw new AssertionError("Unexpected path.");
                }

                b.startDeclaration(type(byte.class), "tag");
                b.startCall(bytecodeNode, "getCachedLocalTagInternal");
                if (materialized) {
                    b.startCall(bytecodeNode, "getLocalTags").end();
                } else {
                    b.string("localTags");
                }
                b.tree(localIndex);
                b.end(); // call
                b.end(); // declaration

                b.startIf().string("tag != ").staticReference(parent.parent.frameTagsElement.get(slotType)).end().startBlock();
                b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
                b.startThrow().startNew(types.UnexpectedResultException).string(value.localName()).end().end();
                b.end();

                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, slotType).string(localsFrame).string("slot");
                if (ElementUtils.needsCastTo(inputType, slotType)) {
                    b.startStaticCall(parent.parent.lookupExpectMethod(inputType, slotType));
                    b.string(value.localName());
                    b.end();
                } else {
                    b.string(value.localName());
                }
                b.end(); // set frame
                b.end(); // statement
            }
            return null;
        } else { // slow-path
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BYTECODE_INDEX)));
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

            b.startDeclaration(type(byte.class), "oldTag");
            b.startCall(bytecodeNode, "getCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.tree(localIndex);
            b.end(); // call
            b.end(); // declaration

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(byte.class), "newTag");

            InstructionModel genericInstruction = instruction.findQuickening(QuickeningKind.GENERIC, null, false);

            boolean elseIf = false;
            for (TypeMirror boxingType : model().boxingEliminatedTypes) {
                elseIf = b.startIf(elseIf);
                b.string(value.localName()).instanceOf(ElementUtils.boxType(boxingType)).end().startBlock();

                // instruction for unsuccessful operand quickening
                InstructionModel boxedInstruction = instruction.findQuickening(QuickeningKind.SPECIALIZED, boxingType, false);
                // instruction for successful operand quickening
                InstructionModel unboxedInstruction = boxedInstruction.findQuickening(QuickeningKind.SPECIALIZED_UNBOXED, boxingType, false);

                b.startSwitch().string("oldTag").end().startBlock();

                b.startCase().staticReference(parent.parent.frameTagsElement.get(boxingType)).end();
                b.startCase().staticReference(parent.parent.frameTagsElement.getIllegal()).end();
                b.startCaseBlock();

                b.startIf().string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(unboxedInstruction)).end();
                b.end().startElseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(boxedInstruction)).end();
                b.startStatement().string("newOperand = operand").end();
                b.end(); // else block
                String kindName = ElementUtils.firstLetterUpperCase(ElementUtils.getSimpleName(boxingType));
                parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + kindName);
                b.startStatement().string("newTag = ").staticReference(parent.parent.frameTagsElement.get(boxingType)).end();
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, boxingType).string(localsFrame).string("slot").startGroup().cast(boxingType).string(value.localName()).end().end();
                b.end();
                b.statement("break");
                b.end();

                for (TypeMirror otherType : model().boxingEliminatedTypes) {
                    if (ElementUtils.typeEquals(otherType, boxingType)) {
                        continue;
                    }
                    b.startCase().staticReference(parent.parent.frameTagsElement.get(otherType)).end();
                }

                b.startCase().staticReference(parent.parent.frameTagsElement.getObject()).end();
                b.startCaseBlock();
                b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
                b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
                b.startStatement().string("newTag = ").staticReference(parent.parent.frameTagsElement.getObject()).end();
                parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string(localsFrame).string("slot").string(value.localName()).end();
                b.end();
                b.statement("break");
                b.end();

                b.caseDefault().startCaseBlock();
                b.tree(GeneratorUtils.createShouldNotReachHere("Unexpected frame tag."));
                b.end();

                b.end(); // switch
                b.end(); // if block
            }

            b.startElseBlock(elseIf);
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(genericInstruction)).end();
            b.startStatement().string("newOperand = ").startCall("undoQuickening").string("operand").end().end();
            b.startStatement().string("newTag = ").staticReference(parent.parent.frameTagsElement.getObject()).end();
            parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "StoreLocal$" + genericInstruction.getQualifiedQuickeningName());
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class)).string(localsFrame).string("slot").string(value.localName()).end();
            b.end();
            b.end(); // else

            b.startIf().string("newTag != oldTag").end().startBlock();
            b.startStatement().startCall(bytecodeNode, "setCachedLocalTagInternal");
            if (materialized) {
                b.startCall(bytecodeNode, "getLocalTags").end();
            } else {
                b.string("localTags");
            }
            b.tree(localIndex);
            b.string("newTag");
            b.end(2);
            b.end(); // if newTag != oldTag

            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            return null;
        }
    }

    private TypeMirror emitBranchFalse(CodeTreeBuilder b, ExecutionMode mode) {
        Operand conditionOperand = instruction.signature.singleDynamicOperand();
        String operandValue = conditionOperand.localName();

        if (mode.isFastPath()) {
            if (getTier().isUncached()) {
                b.startIf().string(operandValue).end().startBlock();
            } else {
                b.startIf();
                b.startCall("profileBranch");
                b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BRANCH_PROFILE)));
                b.string(operandValue);
                b.end();
                b.end().startBlock();
            }
        } else { // slow-path
            TypeMirror boxingType = type(boolean.class);

            InstructionModel boxedInstruction = instruction.findQuickening(QuickeningKind.GENERIC, null, false);
            InstructionModel unboxedInstruction = instruction.findQuickening(QuickeningKind.SPECIALIZED, type(boolean.class), false);

            if (boxedInstruction == null || unboxedInstruction == null) {
                throw new AssertionError("Unexpected quickenings " + instruction);
            }

            b.declaration(type(short.class), "newInstruction");
            b.declaration(type(short.class), "newOperand");
            b.declaration(type(int.class), "operandIndex", BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.findImmediate(ImmediateKind.BYTECODE_INDEX, "child0")));
            b.declaration(type(short.class), "operand", BytecodeRootNodeElement.readInstruction("bc", "operandIndex"));

            b.startIf().string("(newOperand = ").startCall(BytecodeRootNodeElement.createApplyQuickeningName(boxingType)).string("operand").end().string(") != -1").end().startBlock();
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(unboxedInstruction)).end();
            parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "BranchFalse$" + unboxedInstruction.getQuickeningName());
            b.end().startElseBlock();
            b.startStatement().string("newInstruction = ").tree(parent.parent.createInstructionConstant(boxedInstruction)).end();
            b.startStatement().string("newOperand = operand").end();
            parent.parent.emitOnSpecialize(b, "this", "bci", BytecodeRootNodeElement.readInstruction("bc", "bci"), "BranchFalse$" + boxedInstruction.getQuickeningName());
            b.end(); // else block

            parent.parent.emitQuickeningOperand(b, "this", "bc", "bci", null, 0, "operandIndex", "operand", "newOperand");
            parent.parent.emitQuickening(b, "this", "bc", "bci", null, "newInstruction");

            b.startIf();
            b.startCall("profileBranch");
            b.tree(BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.BRANCH_PROFILE)));
            b.string(operandValue);
            b.end();
            b.end().startBlock();
        }
        b.statement("return bci + " + instruction.getInstructionLength());
        b.end().startElseBlock();
        b.statement("return " + BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate("branch_target")));
        b.end();

        return null;
    }

    private void emitYieldProlog(CodeTreeBuilder b) {
        b.statement("int maxLocals = getRoot().maxLocals");
        /*
         * The yield result will be stored at sp + stackEffect - 1 = sp + (1 - n) - 1 = sp - n (for
         * n dynamic operands). We need to copy operands lower on the stack for resumption.
         */
        String yieldResultIndex = (instruction.signature.dynamicOperandCount() == 0) ? "sp" : "sp - " + instruction.signature.dynamicOperandCount();
        b.lineCommentf("The yield result will be stored at %s. The operands below it need to be preserved for resumption.", yieldResultIndex);
        b.lineCommentf("These operands belong to the interval [maxLocals, %s).", yieldResultIndex);
        b.startIf().string("maxLocals < " + yieldResultIndex).end().startBlock();
        b.statement(BytecodeRootNodeElement.copyFrameTo("frame", "maxLocals", "localFrame", "maxLocals", yieldResultIndex + " - maxLocals"));
        b.end();
    }

    private TypeMirror emitYield(CodeTreeBuilder b, String resultLocalName) {
        InstructionImmediate continuationIndex = instruction.getImmediate(ImmediateKind.CONSTANT);
        b.startDeclaration(parent.parent.continuationRootNodeImpl.asType(), "continuationRootNode");
        b.tree(parent.parent.readConstFastPath(BytecodeRootNodeElement.readImmediate("bc", "bci", continuationIndex), "this.constants", parent.parent.continuationRootNodeImpl.asType()));
        b.end();

        if (instruction.signature.dynamicOperandCount() != 1) {
            throw new AssertionError("Invalid number of operands for yield.");
        }
        Operand resultOperand = instruction.signature.dynamicOperands().get(0);
        b.startDeclaration(types.ContinuationResult, resultLocalName);
        b.startCall("continuationRootNode.createContinuation");
        b.string(parent.parent.localFrame());
        b.string(resultOperand.localName());
        b.end(2);

        return types.ContinuationResult;
    }

    private TypeMirror emitCustom(CodeTreeBuilder b, String resultLocalName, ExecutionMode mode) {
        TypeMirror cachedType = BytecodeRootNodeElement.getCachedDataClassType(instruction);
        if (getTier().isCached()) {
            if (instruction.isEpilogExceptional()) {
                b.startDeclaration(cachedType, "node").string("this.epilogExceptionalNode_").end();
            } else if (instruction.canUseNodeSingleton()) {
                b.startDeclaration(cachedType, "node").staticReference(cachedType, "SINGLETON").end();
            } else {
                CodeTree nodeIndex = BytecodeRootNodeElement.readImmediate("bc", "bci", instruction.getImmediate(ImmediateKind.NODE_PROFILE));
                CodeTree readNode = CodeTreeBuilder.createBuilder().tree(parent.parent.readNodeProfile(cachedType, nodeIndex)).build();
                b.declaration(cachedType, "node", readNode);
            }
        }
        String evaluatedArg = null;
        if (instruction.isEpilogExceptional()) {
            evaluatedArg = "exception";
        }

        CodeExecutableElement targetMethod = findCustomExecuteMethod(mode);
        TypeMirror returnType = targetMethod.getReturnType();
        if (ElementUtils.isVoid(returnType)) {
            b.startStatement();
        } else {
            b.startDeclaration(returnType, resultLocalName);
        }

        if (targetMethod.getModifiers().contains(STATIC)) {
            b.startStaticCall(cachedType, targetMethod.getSimpleName().toString());
        } else {
            b.startCall("node", targetMethod.getSimpleName().toString());
        }

        // If we support yield, the frame forwarded to specializations should be the local frame
        // and not the stack frame.
        if (parent.parent.needsFrame(instruction, parent.tier)) {
            b.string(parent.parent.localFrame());
        }

        if (evaluatedArg != null) {
            b.string(evaluatedArg);
        } else {
            for (String name : instruction.signature.operands().stream().map(s -> s.localName()).toList()) {
                b.string(name);
            }
        }

        b.string("this");
        b.string("bc");
        b.string("bci");
        b.end(); // call
        b.end(); // statement|declaration

        return returnType;
    }

    private boolean hasUnexpectedValue(Operand operand) {
        if (!operand.isDynamic()) {
            return false;
        } else if (!getTier().isCached()) {
            return false;
        }
        if (instruction.getQuickeningRoot().needsChildBciForBoxingElimination(model(), operand)) {
            return true;
        }
        if (instruction.kind == InstructionKind.MERGE_CONDITIONAL) {
            // the boolean condition can be boxing eliminated, but its only read in slow-path.
            return true;
        }

        return false;

    }

    private CodeExecutableElement findCustomExecuteMethod(ExecutionMode mode) {
        CodeExecutableElement targetMethod;
        if (mode.isSlowPath()) {
            targetMethod = parent.parent.specializeExecuteMethods.get(instruction.getQuickeningRoot());
        } else if (getTier().isUncached()) {
            targetMethod = parent.parent.uncachedExecuteMethods.get(instruction.getQuickeningRoot());
        } else {
            targetMethod = parent.parent.cachedExecuteMethods.get(instruction);
        }
        // we allow null methods for slow-path
        if (targetMethod == null) {
            throw new AssertionError("Target method for instruction " + instruction + " and mode " + mode + " not yet emitted.");
        }
        return targetMethod;
    }

    private boolean needsClearing(InstructionModel instr, Operand operand) {
        if (!canBeCleared(instr, operand)) {
            return false;
        }
        if (instr.signature.isVoid()) {
            return true;
        } else {
            // no clear if not last
            return operand.dynamicIndex() != instr.signature.dynamicOperandCount() - 1;
        }
    }

    private static String createStackIndex(InstructionModel instruction, Operand operand) {
        return "sp - " + (instruction.signature.dynamicOperandCount() - operand.dynamicIndex());
    }

    private static boolean isEmitLoadOperand(InstructionModel instr, Operand operand, ExecutionMode mode) {
        if (operand.isConstant()) {
            return false;
        }
        if (instr.hasVariableStackEffect()) {
            // handled by the instruction itself
            return false;
        }
        if (instr.isEpilogExceptional()) {
            return false;
        }

        switch (instr.kind) {
            case RETURN:
                return false;
            case MERGE_CONDITIONAL:
                if (mode.isSlowPath()) {
                    return true;
                }
                // condition not needed on fast-path
                return operand.dynamicIndex() != 0;
            case POP:
                // only slow path
                return mode.isSlowPath();
        }

        return true;
    }

    /**
     * Whether {@link #emitExecute(CodeTreeBuilder, String, ExecutionMode)} throws an
     * UnexpectedResultException with the instruction return value.
     */
    private boolean hasExecuteUnexpectedReturn() {
        QuickeningKind quickeningKind = instruction.quickeningKind;
        switch (instruction.kind) {
            case CUSTOM:
                return findCustomExecuteMethod(ExecutionMode.FAST_PATH).getThrownTypes().contains(types.UnexpectedResultException);
            case LOAD_ARGUMENT:
            case LOAD_LOCAL:
            case LOAD_LOCAL_MATERIALIZED:
                return instruction.specializedType != null && quickeningKind == QuickeningKind.SPECIALIZED || quickeningKind == QuickeningKind.SPECIALIZED_UNBOXED;
            default:
                return false;
        }
    }

    /**
     * Returns true if for this instruction should invoke the slow path method for unexpected
     * returns. We cannot do this by default, as the slow-path may cause side-effects.
     */
    private boolean isInvokeSlowPathOnUnexpectedReturn() {
        switch (instruction.kind) {
            case LOAD_LOCAL:
            case LOAD_LOCAL_MATERIALIZED:
            case LOAD_ARGUMENT:
                return true;
        }
        return false;
    }

    /**
     * Returns <code>true</code> if the quickening root serves as uninitialized case and is supposed
     * to directly invoke the slow-path, else <code>false</code>. This applies in
     * {@link InterpreterTier#CACHED} only.
     */
    private boolean isQuickeningRootSlowPath() {
        switch (instruction.kind) {
            case LOAD_LOCAL:
            case LOAD_LOCAL_MATERIALIZED:
            case STORE_LOCAL:
            case STORE_LOCAL_MATERIALIZED:
            case POP:
            case BRANCH_FALSE:
            case TAG_LEAVE:
            case MERGE_CONDITIONAL:
                return true;
        }
        return false;
    }

    private void emitUnexpectedReturn(CodeTreeBuilder b) {
        if (isBoxingOverloadReturnTypeQuickening()) {
            InstructionModel generic = instruction.quickeningBase.findQuickening(QuickeningKind.GENERIC, null, false);
            if (generic == instruction) {
                throw new AssertionError("Unexpected generic instruction.");
            }
            parent.parent.emitQuickening(b, "this", "bc", "bci", null, parent.parent.createInstructionConstant(generic));
            emitPushResult(b, instruction, generic.signature.returnType(), "ex.getResult()", ExecutionMode.SLOW_PATH, false);
        } else if (isInvokeSlowPathOnUnexpectedReturn()) {
            b.startStatement();
            emitCallSlowPath(b, name -> name, null);
            b.end();
        } else {
            emitPushResult(b, instruction, instruction.signature.returnType(), "ex.getResult()", ExecutionMode.SLOW_PATH, true);
        }
    }

    private void emitBeforeReturnProfilingHandler(CodeTreeBuilder b) {
        if (getTier().isCached()) {
            b.startIf().string("counter > 0").end().startBlock();
            b.startStatement().startStaticCall(types.LoopNode, "reportLoopCount");
            b.string("this");
            b.string("counter");
            b.end().end();  // statement
            b.end();  // if counter > 0
        }
    }

    private void emitConstantOperand(CodeTreeBuilder b) {
        for (Operand operand : instruction.signature.operands()) {
            if (operand.isConstant()) {
                emitReadConstantOperand(instruction, b, operand);
            }
        }
    }

    private void emitDynamicOperands(CodeTreeBuilder b, Function<String, String> nameFunction,
                    ExecutionMode mode, String unexpectedResult) {
        InstructionModel useInstruction = this.instruction;
        if (mode.isSlowPath()) {
            useInstruction = useInstruction.getQuickeningRoot();
        }

        Signature customSignature = useInstruction.signature;
        for (Operand operand : customSignature.operands()) {
            if (!isEmitLoadOperand(instruction, operand, mode)) {
                continue;
            }

            TypeMirror targetType = operand.staticType();
            b.startDeclaration(targetType, nameFunction.apply(operand.localName()));
            /*
             * We only want boxing elimination in the cached interpreter, when the operand type is
             * boxing eliminated and the instruction is a quickening. Without the quickening we
             * cannot boxing eliminate as the operands need to be switched as well.
             */
            final boolean boxingEliminated = getTier().isCached() && model().isBoxingEliminated(operand.type()) && useInstruction.isQuickening();

            /*
             * true if we ever expect any other types other than Object in this stack slot, else
             * false.
             */
            final boolean expectOtherTypes = hasUnexpectedValue(operand);

            if (ElementUtils.typeEquals(operand.type(), parent.context.getType(Object[].class))) {
                b.cast(operand.type());
            } else if (!ElementUtils.typeEquals(targetType, operand.type())) {
                b.cast(targetType);
            }

            String stackIndex = createStackIndex(useInstruction, operand);
            Operand singleUnexpected = findSingleUnexpectedOperand();
            boolean hasUnexpected = unexpectedResult != null && singleUnexpected.dynamicIndex() == operand.dynamicIndex();
            if (hasUnexpected) {
                b.string("(", unexpectedResult, " != null ? ", unexpectedResult, " : (");
            }
            if (!expectOtherTypes) {
                // FRAMES.uncheckedGetObject
                b.string(BytecodeRootNodeElement.uncheckedGetFrameObject(stackIndex));
            } else if (mode.isSlowPath()) {
                // frame.getValue(index)
                BytecodeRootNodeElement.startGetFrameUnsafe(b, "frame", null);
                b.string(stackIndex);
                b.end();
            } else if (boxingEliminated) {
                // frame.expect${type}
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", operand.type());
                b.string(stackIndex);
                b.end();
            } else {
                // frame.expectObject
                BytecodeRootNodeElement.startExpectFrameUnsafe(b, "frame", type(Object.class));
                b.string(stackIndex);
                b.end();
            }
            if (hasUnexpected) {
                b.string("))");
            }
            b.end();
        }

        emitClearOperands(b, useInstruction, mode);
    }

    private static boolean isPassFirstOperandAsReturnValue(InstructionModel instr) {
        if (instr.isTransparent() && instr.signature.dynamicOperandCount() == 1) {
            return true;
        }
        if (instr.kind == InstructionKind.LOAD_VARIADIC) {
            // load variadic the first argument is Object[] and passed as return
            return true;
        }
        return false;
    }

    private void emitClearOperands(CodeTreeBuilder b, InstructionModel useInstruction, ExecutionMode mode) {
        if (handlerKind.isReturn()) {
            // no clearing needed for returns
            return;
        }
        List<Operand> toClear = new ArrayList<>();
        List<Operand> compiledClear = new ArrayList<>();
        for (Operand operand : useInstruction.signature.dynamicOperands()) {
            if (needsClearing(useInstruction, operand)) {
                if (operand.isPrimitive()) {
                    compiledClear.add(operand);
                } else {
                    toClear.add(operand);
                }
            } else {
                if (canBeCleared(useInstruction, operand)) {
                    // important for liveness analysis
                    compiledClear.add(operand);
                }
            }
        }

        for (Operand operand : toClear) {
            // unconditional clear in both interpreter and compiled
            b.statement(BytecodeRootNodeElement.clearFrame("frame", createStackIndex(useInstruction, operand)));
        }

        if (!compiledClear.isEmpty() && !mode.isSlowPath() && getTier().isCached()) {
            b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
            for (Operand operand : compiledClear) {
                b.statement(BytecodeRootNodeElement.clearFrame("frame", createStackIndex(useInstruction, operand)));
            }
            b.end();
        }
    }

    private boolean canBeCleared(InstructionModel instr, Operand operand) {
        if (!operand.isDynamic()) {
            return false;
        }
        if (instr.isEpilogExceptional()) {
            return false;
        }
        if (handlerKind.isShortCircuit()) {
            // short-circuit clearing depends on input value
            return false;
        }
        if (instr.kind == InstructionKind.LOAD_LOCAL_MATERIALIZED && !instr.checked) {
            /*
             * Load local checked with catch frame FrameSlotTypeException and translate it into a
             * slow-path call. In such a case we cannot eagerly clear.
             */
            return false;
        }
        if (instr.kind == InstructionKind.POP) {
            // custom clearing for pop
            return false;
        }
        if (handlerKind.isReturn()) {
            // no clearing needed
            return false;
        }
        if (isPassFirstOperandAsReturnValue(instr) && operand.dynamicIndex() == 0) {
            return false;
        }
        return true;
    }

    private void emitReadConstantOperand(InstructionModel instr, CodeTreeBuilder b, Operand operand) {
        b.startDeclaration(operand.type(), operand.localName());
        b.tree(parent.parent.readConstantImmediate("bc", "bci", "this", instr.constantOperandImmediates.get(operand.constant()), operand.type()));
        b.end();
    }

    private void emitPushResult(CodeTreeBuilder b, InstructionModel instr,
                    TypeMirror currentType, String resultName, ExecutionMode mode, boolean enableCast) {
        emitPushResult(b, instr, currentType, CodeTreeBuilder.singleString(resultName), mode, enableCast);
    }

    /**
     * Most operands are cleared by
     * {@link #emitClearOperands(CodeTreeBuilder, InstructionModel, ExecutionMode)}. If custom
     * clearing is needed this method is used.
     */
    private void emitClearStackValue(CodeTreeBuilder b, String stackPointer, TypeMirror staticType, ExecutionMode mode) {
        if (!ElementUtils.isObject(staticType) && model().additionalAssertions) {
            // we only verify the guaranteed type with additional assertions enabled
            // to ensure we do not impact any heuristics on the host VM.
            b.startAssert();
            if (getTier().isCached()) {
                BytecodeRootNodeElement.startGetFrame(b, "frame", null, true);
                b.string(stackPointer);
                b.end(); // get frame
            } else {
                b.string(BytecodeRootNodeElement.uncheckedGetFrameObject(stackPointer));
            }
            b.instanceOf(ElementUtils.boxType(staticType));
            b.end();
        }

        if (ElementUtils.isPrimitive(staticType)) {
            if (mode.isFastPath() && getTier().isCached()) {
                b.startIf().startStaticCall(types.CompilerDirectives, "inCompiledCode").end().end().startBlock();
                b.lineComment("Clear for liveness analysis.");
                b.statement(BytecodeRootNodeElement.clearFrame("frame", stackPointer));
                b.end(); // if
            } else {
                // we do not clear primitives for slow-paths at all.
                b.lineComment("Primitive clear omitted for " + stackPointer);
            }
        } else {
            // unconditionally clear possible references to ensure the value
            // is not alive longer than necessary for garbage collection.
            b.statement(BytecodeRootNodeElement.clearFrame("frame", stackPointer));
        }
    }

    private void emitPushResult(CodeTreeBuilder b, InstructionModel instr,
                    TypeMirror currentType, CodeTree result, ExecutionMode mode, boolean enableCast) {
        if (instr.signature.isVoid()) {
            return;
        }

        if (instr.kind == InstructionKind.DUP) {
            // we should not do stack effects in execute so it needs to be part of the
            // instruction layout.
            b.statement(BytecodeRootNodeElement.copyFrameSlot("sp - 1", "sp"));
            return;
        }

        CodeTree useResult = result;
        if (instr.isTransparent()) {
            /*
             * Transparent instructions don't need to push any result with the exception of
             * transparent instructions that need to box their input value. For example unboxed int
             * as input, but Object as return type.
             *
             * Since the instruction is not supposed to make stack modifications we make sure the
             * return value is always bound to the input operand and the instruction cannot modify
             * it.
             */
            if (needsTransparentBoxing(instr)) {
                useResult = CodeTreeBuilder.singleString(instr.signature.singleDynamicOperand().localName());
            } else {
                return;
            }
        }

        if (instr.nonNull) {
            b.startStatement().startStaticCall(type(Objects.class), "requireNonNull");
            b.tree(result).doubleQuote("The operation " + instr.operation.name + " must return a non-null value, but did return a null value.");
            b.end().end();
        }

        String returnIndex;
        if (instr.hasVariableStackEffect()) {
            returnIndex = "sp - stackSize";
        } else {
            returnIndex = instr.getStackEffect() == 1 ? "sp" : ("sp - " + (1 - instr.getStackEffect()));
        }

        // Update the stack.
        if (instr.isReturnTypeQuickening()) {
            if (mode.isSlowPath()) {
                if (enableCast && ElementUtils.needsCastTo(currentType, instr.signature.returnType())) {
                    b.startStatement();
                    BytecodeRootNodeElement.startSetPrimitiveOrObjectFrame(b, instr.signature.returnType());
                    b.string("frame");
                    b.string(returnIndex);
                    b.tree(useResult);
                    b.end(); // setFrame
                    b.end(); // statement
                } else {
                    b.startStatement();
                    BytecodeRootNodeElement.startSetFrame(b, instr.getQuickeningRoot().signature.returnType());
                    b.string("frame");
                    b.string(returnIndex);
                    b.tree(useResult);
                    b.end(); // setFrame
                    b.end(); // statement
                }
            } else {
                b.startStatement();
                BytecodeRootNodeElement.startSetFrame(b, instr.signature.returnType());
                b.string("frame");
                b.string(returnIndex);
                b.tree(useResult);
                b.end(); // setFrame
                b.end(); // statement
            }
        } else {
            b.startStatement();
            BytecodeRootNodeElement.startSetFrame(b, type(Object.class));
            b.string("frame");
            b.string(returnIndex);
            b.tree(useResult);
            b.end(); // setFrame
            b.end(); // statement
        }
    }

    /**
     * If the input operand type is int but the return type is Object we need to box the value on
     * the stack.
     */
    private static boolean needsTransparentBoxing(InstructionModel instr) {
        if (!instr.isTransparent()) {
            return false;
        }

        if (!instr.isQuickening()) {
            return false;
        }
        if (instr.signature.dynamicOperandCount() != 1) {
            return false;
        }
        Operand operand = instr.signature.singleDynamicOperand();
        if (ElementUtils.typeEquals(instr.signature.returnType(), operand.type())) {
            return false;
        }
        if (instr.specializedType == null) {
            return false;
        }
        return true;
    }

    private boolean isBoxingOverloadReturnTypeQuickening() {
        if (!instruction.isReturnTypeQuickening()) {
            return false;
        }
        SpecializationData specialization = instruction.resolveSingleSpecialization();
        if (specialization == null) { // multiple specializations handled
            return false;
        }
        return specialization.getBoxingOverloads().size() > 0;
    }

    private TypeMirror type(Class<?> c) {
        return context.getType(c);
    }

    private BytecodeDSLModel model() {
        return parent.parent.model;
    }

    private InterpreterTier getTier() {
        return parent.tier;
    }

    enum ExecutionMode {
        FAST_PATH,
        SLOW_PATH;

        public boolean isFastPath() {
            return this == FAST_PATH;
        }

        public boolean isSlowPath() {
            return !isFastPath();
        }

    }

    /**
     * Category for each control flow kind that requires specialized generation. Since not all cases
     * are mapped to {@link InstructionModel#kind} this is useful to reduce the number of cases
     * during code generation and is an attempt at meaningfully group them.
     */
    enum HandlerKind {
        NEXT,
        NONE,
        BRANCH_BACKWARD,
        BRANCH_FALSE,
        STACK_POP,
        BRANCH,
        RETURN,
        INVALIDATE,
        SHORT_CIRCUIT_VALUE,
        SHORT_CIRCUIT_BOOLEAN,
        YIELD,
        EFFECT_GROUP,
        THROW;

        public boolean isShortCircuit() {
            return this == SHORT_CIRCUIT_BOOLEAN || this == SHORT_CIRCUIT_VALUE;
        }

        public boolean isBranch() {
            switch (this) {
                case BRANCH_BACKWARD:
                case BRANCH_FALSE:
                case BRANCH:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isReturn() {
            switch (this) {
                case RETURN:
                case YIELD:
                case INVALIDATE:
                    return true;
                default:
                    return false;
            }
        }

        public static HandlerKind resolve(InstructionModel instruction, boolean effectGroup) {
            if (instruction.isEpilogExceptional()) {
                return HandlerKind.NONE;
            }
            HandlerKind kindByInstruction = resolveByInstructionKind(instruction);
            if (effectGroup) {
                if (kindByInstruction != NEXT) {
                    throw new AssertionError("Cannot use effect group in combination with an instruction that is not NEXT.");
                }
                kindByInstruction = HandlerKind.EFFECT_GROUP;
            }
            return kindByInstruction;
        }

        private static HandlerKind resolveByInstructionKind(InstructionModel instruction) {
            switch (instruction.kind) {
                case CREATE_VARIADIC:
                case LOAD_VARIADIC:
                    return HandlerKind.STACK_POP;
                case BRANCH:
                    return HandlerKind.BRANCH;
                case BRANCH_BACKWARD:
                    return HandlerKind.BRANCH_BACKWARD;
                case BRANCH_FALSE:
                    return HandlerKind.BRANCH_FALSE;
                case RETURN:
                    return HandlerKind.RETURN;
                case YIELD:
                    return HandlerKind.YIELD;
                case THROW:
                    return HandlerKind.THROW;
                case CUSTOM_SHORT_CIRCUIT:
                    if (instruction.shortCircuitModel.producesBoolean()) {
                        return HandlerKind.SHORT_CIRCUIT_BOOLEAN;
                    } else {
                        return HandlerKind.SHORT_CIRCUIT_VALUE;
                    }
                case INVALIDATE:
                    return HandlerKind.INVALIDATE;
                case CUSTOM:
                    if (instruction.operation.kind == OperationKind.CUSTOM_YIELD) {
                        return HandlerKind.YIELD;
                    } else {
                        return HandlerKind.NEXT;
                    }
                default:
                    return HandlerKind.NEXT;
            }
        }

    }
}
