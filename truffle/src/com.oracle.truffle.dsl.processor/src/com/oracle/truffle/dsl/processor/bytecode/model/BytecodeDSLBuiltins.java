/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.bytecode.model;

import java.util.List;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument.Encoding;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;

/**
 * Helper class that initializes a {@link BytecodeDSLModel} with all of the Bytecode DSL builtins.
 *
 * The user guide should be updated when new builtin operations are added.
 */
public class BytecodeDSLBuiltins {
    public static void addBuiltins(BytecodeDSLModel m, TruffleTypes types, ProcessorContext context) {
        m.popInstruction = m.instruction(InstructionKind.POP, "pop", m.signature(void.class, Object.class));
        m.dupInstruction = m.instruction(InstructionKind.DUP, "dup", m.signature(void.class));
        m.returnInstruction = m.instruction(InstructionKind.RETURN, "return", m.signature(void.class, Object.class));
        m.branchInstruction = m.instruction(InstructionKind.BRANCH, "branch", m.signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        m.branchBackwardInstruction = m.instruction(InstructionKind.BRANCH_BACKWARD, "branch.backward", m.signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        m.branchFalseInstruction = m.instruction(InstructionKind.BRANCH_FALSE, "branch.false", m.signature(void.class, Object.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target") //
                        .addImmediate(ImmediateKind.BRANCH_PROFILE, "branch_profile");
        m.storeLocalInstruction = m.instruction(InstructionKind.STORE_LOCAL, "store.local", m.signature(void.class, Object.class)) //
                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset");
        m.throwInstruction = m.instruction(InstructionKind.THROW, "throw", m.signature(void.class, Object.class));
        m.loadConstantInstruction = m.instruction(InstructionKind.LOAD_CONSTANT, "load.constant", m.signature(Object.class)) //
                        .addImmediate(ImmediateKind.CONSTANT, "constant");
        m.loadNullInstruction = m.instruction(InstructionKind.LOAD_NULL, "load.null", m.signature(Object.class));

        m.blockOperation = m.operation(OperationKind.BLOCK, "Block",
                        """
                                        Block is a grouping operation that executes each child in its body sequentially, producing the result of the last child (if any).
                                        This operation can be used to group multiple operations together in a single operation.
                                        The result of a Block is the result produced by the last child (or void, if no value is produced).
                                        """) //
                        .setTransparent(true) //
                        .setVariadic(true) //
                        .setDynamicOperands(transparentOperationChild());
        m.rootOperation = m.operation(OperationKind.ROOT, "Root",
                        String.format("""
                                        Each Root operation defines one function (i.e., a {@link %s}).
                                        It takes one or more children, which define the body of the function that executes when it is invoked.
                                        If control falls through to the end of the body without returning, instructions are inserted to implicitly return {@code null}.
                                        <p>
                                        A root operation is typically the outermost one. That is, a {@link BytecodeParser} should invoke {@link #beginRoot} first before using other builder methods to generate bytecode.
                                        The parser should invoke {@link #endRoot} to finish generating the {@link %s}.
                                        <p>
                                        A parser *can* nest this operation in Source and SourceSection operations in order to provide a {@link Node#getSourceSection source location} for the entire root node.
                                        The result of {@link Node#getSourceSection} on the generated root is undefined if there is no enclosing SourceSection operation.
                                        <p>
                                        This method can also be called inside of another root operation. Bytecode generation for the outer root node suspends until generation for the inner root node finishes.
                                        The inner root node is not lexically nested in the first (you can invoke the inner root node independently), but the inner root *can* manipulate the outer root's locals using
                                        materialized local accesses if the outer frame is provided to it.
                                        Multiple root nodes can be obtained from the {@link BytecodeNodes} object in the order of their {@link #beginRoot} calls.
                                        """,
                                        m.templateType.getSimpleName(), m.templateType.getSimpleName())) //
                        .setTransparent(true) //
                        .setVariadic(true) //
                        .setOperationBeginArguments(
                                        new OperationArgument(types.TruffleLanguage, OperationArgument.Encoding.LANGUAGE, "language", "the language to associate with the root node")) //
                        .setDynamicOperands(transparentOperationChild());
        m.ifThenOperation = m.operation(OperationKind.IF_THEN, "IfThen", """
                        IfThen implements an if-then statement. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code thens}.
                        This is a void operation; {@code thens} can also be void.
                        """) //
                        .setVoid(true) //
                        .setDynamicOperands(child("condition"), voidableChild("thens"));
        m.ifThenElseOperation = m.operation(OperationKind.IF_THEN_ELSE, "IfThenElse",
                        """
                                        IfThenElse implements an if-then-else statement. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code thens}; otherwise, it executes {@code elses}.
                                        This is a void operation; both {@code thens} and {@code elses} can also be void.
                                        """) //
                        .setVoid(true) //
                        .setDynamicOperands(child("condition"), voidableChild("thens"), voidableChild("elses"));
        m.conditionalOperation = m.operation(OperationKind.CONDITIONAL, "Conditional",
                        """
                                        Conditional implements a conditional expression (e.g., {@code condition ? thens : elses} in Java). It has the same semantics as IfThenElse, except it produces the value of the conditionally-executed child.
                                        """) //
                        .setDynamicOperands(child("condition"), child("thens"), child("elses"));
        m.whileOperation = m.operation(OperationKind.WHILE, "While",
                        """
                                        While implements a while loop. It evaluates {@code condition}, which must produce a boolean. If the value is {@code true}, it executes {@code body} and repeats.
                                        This is a void operation; {@code body} can also be void.
                                        """) //
                        .setVoid(true) //
                        .setDynamicOperands(child("condition"), voidableChild("body"));
        m.tryCatchOperation = m.operation(OperationKind.TRY_CATCH, "TryCatch",
                        """
                                        TryCatch implements an exception handler. It executes {@code try}, and if a Truffle exception is thrown, it executes {@code catch}.
                                        The exception can be accessed within the {@code catch} operation using LoadException.
                                        Unlike a Java try-catch, this operation does not filter the exception based on type.
                                        This is a void operation; both {@code try} and {@code catch} can also be void.
                                        """) //
                        .setVoid(true) //

                        .setDynamicOperands(voidableChild("try"), voidableChild("catch"));

        TypeMirror parserType = context.getDeclaredType(Runnable.class);
        m.finallyTryOperation = m.operation(OperationKind.FINALLY_TRY, "FinallyTry",
                        """
                                        FinallyTry implements a finally handler. It runs the given {@code finallyParser} to parse a {@code finally} operation.
                                        FinallyTry executes {@code try}, and after execution finishes it always executes {@code finally}.
                                        If {@code try} finishes normally, {@code finally} executes and control continues after the FinallyTry operation.
                                        If {@code try} finishes exceptionally, {@code finally} executes and then rethrows the exception.
                                        If {@code try} finishes with a control flow operation, {@code finally} executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
                                        This is a void operation; both {@code finally} and {@code try} can also be void.
                                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(parserType, Encoding.FINALLY_PARSER, "finallyParser",
                                        "a runnable that uses the builder to parse the finally operation (must be idempotent)") //
                        ).setDynamicOperands(voidableChild("try"));
        m.finallyTryCatchOperation = m.operation(OperationKind.FINALLY_TRY_CATCH, "FinallyTryCatch",
                        """
                                        FinallyTryCatch implements a finally handler with different behaviour for thrown exceptions. It runs the given {@code finallyParser} to parse a {@code finally} operation.
                                        FinallyTryCatch executes {@code try} and then one of the handlers.
                                        If {@code try} finishes normally, {@code finally} executes and control continues after the FinallyTryCatch operation.
                                        If {@code try} finishes exceptionally, {@code catch} executes. The exception can be accessed using LoadException, and it is rethrown afterwards.
                                        If {@code try} finishes with a control flow operation, {@code finally} executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
                                        This is a void operation; any of {@code finally}, {@code try}, or {@code catch} can be void.
                                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(parserType, Encoding.FINALLY_PARSER, "finallyParser",
                                        "a runnable that uses the builder to parse the finally operation (must be idempotent)") //
                        ).setDynamicOperands(voidableChild("try"), voidableChild("catch"));
        m.finallyHandlerOperation = m.operation(OperationKind.FINALLY_HANDLER, "FinallyHandler",
                        """
                                        FinallyHandler is an internal operation that has no stack effect. All finally parsers execute within a FinallyHandler operation.
                                        Executing the parser emits new operations, but these operations should not affect the outer operation's child count/value validation.
                                        To accomplish this, FinallyHandler "hides" these operations by popping any produced values and omitting calls to beforeChild/afterChild.
                                        When walking the operation stack, we skip over operations above finallyOperationSp since they do not logically enclose the handler.
                                        """) //
                        .setVoid(true) //
                        .setVariadic(true) //
                        .setDynamicOperands(transparentOperationChild()) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(short.class), Encoding.SHORT, "finallyOperationSp",
                                        "the operation stack pointer for the finally operation that created the FinallyHandler")) //
                        .setInternal();
        m.operation(OperationKind.LABEL, "Label", """
                        Label assigns {@code label} the current location in the bytecode (so that it can be used as the target of a Branch).
                        This is a void operation.
                        <p>
                        Each {@link BytecodeLabel} must be defined exactly once. It should be defined directly inside the same operation in which it is created (using {@link #createLabel}).
                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLabel, Encoding.LABEL, "label", "the label to define"));
        m.operation(OperationKind.BRANCH, "Branch", """
                        Branch performs a branch to {@code label}.
                        This operation only supports unconditional forward branches; use IfThen and While to perform other kinds of branches.
                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLabel, Encoding.LABEL, "label", "the label to branch to")) //
                        .setInstruction(m.branchInstruction);
        m.loadConstantOperation = m.operation(OperationKind.LOAD_CONSTANT, "LoadConstant", """
                        LoadConstant produces {@code constant}. The constant should be immutable, since it may be shared across multiple LoadConstant operations.
                        """) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(Object.class), Encoding.OBJECT, "constant", "the constant value to load")) //
                        .setInstruction(m.loadConstantInstruction);

        m.loadNullOperation = m.operation(OperationKind.LOAD_NULL, "LoadNull", """
                        LoadNull produces a {@code null} value.
                        """) //
                        .setInstruction(m.loadNullInstruction);
        m.operation(OperationKind.LOAD_ARGUMENT, "LoadArgument", """
                        LoadArgument reads the argument at {@code index} from the frame.
                        """) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(int.class), Encoding.INTEGER, "index", "the index of the argument to load (must fit into a short)")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_ARGUMENT, "load.argument", m.signature(Object.class))//
                                        .addImmediate(ImmediateKind.SHORT, "index"));
        m.operation(OperationKind.LOAD_EXCEPTION, "LoadException", """
                        LoadException reads the current exception from the frame.
                        This operation is only permitted inside the {@code catch} operation of TryCatch and FinallyTryCatch operations.
                        """) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_EXCEPTION, "load.exception", m.signature(Object.class))//
                                        .addImmediate(ImmediateKind.STACK_POINTER, "exceptionSp"));
        m.loadLocalOperation = m.operation(OperationKind.LOAD_LOCAL, "LoadLocal",
                        """
                                        LoadLocal reads {@code local} from the current frame.
                                        If a value has not been written to the local, LoadLocal produces the default value as defined in the {@link FrameDescriptor} ({@code null} by default).
                                        """) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, Encoding.LOCAL, "local", "the local to load")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_LOCAL, "load.local", m.signature(Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.loadLocalMaterializedOperation = m.operation(OperationKind.LOAD_LOCAL_MATERIALIZED, "LoadLocalMaterialized", """
                        LoadLocalMaterialized reads {@code local} from the frame produced by {@code frame}.
                        This operation can be used to read locals from materialized frames. The materialized frame must belong to the same root node or an enclosing root node.
                        """) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, Encoding.LOCAL, "local", "the local to load")) //
                        .setDynamicOperands(child("frame")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_LOCAL_MATERIALIZED, "load.local.mat", m.signature(Object.class, Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.storeLocalOperation = m.operation(OperationKind.STORE_LOCAL, "StoreLocal", """
                        StoreLocal writes the value produced by {@code value} into the {@code local} in the current frame.
                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, Encoding.LOCAL, "local", "the local to store to")) //
                        .setDynamicOperands(child("value")) //
                        .setInstruction(m.storeLocalInstruction);
        m.storeLocalMaterializedOperation = m.operation(OperationKind.STORE_LOCAL_MATERIALIZED, "StoreLocalMaterialized", """
                        StoreLocalMaterialized writes the value produced by {@code value} into the {@code local} in the frame produced by {@code frame}.
                        This operation can be used to store locals into materialized frames. The materialized frame must belong to the same root node or an enclosing root node.
                        """) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, Encoding.LOCAL, "local", "the local to store to")) //
                        .setDynamicOperands(child("frame"), child("value")) //
                        .setInstruction(m.instruction(InstructionKind.STORE_LOCAL_MATERIALIZED, "store.local.mat",
                                        m.signature(void.class, Object.class, Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.returnOperation = m.operation(OperationKind.RETURN, "Return", "Return returns the value produced by {@code result}.") //
                        .setVoid(true) //
                        .setDynamicOperands(child("result")) //
                        .setInstruction(m.returnInstruction);
        if (m.enableYield) {
            m.yieldInstruction = m.instruction(InstructionKind.YIELD, "yield", m.signature(void.class, Object.class)).addImmediate(ImmediateKind.CONSTANT, "location");
            m.operation(OperationKind.YIELD, "Yield", """
                            Yield executes {@code value} and suspends execution at the given location, returning a {@link com.oracle.truffle.api.bytecode.ContinuationResult} containing the result.
                            The caller can resume the continuation, which continues execution after the Yield. When resuming, the caller passes a value that becomes the value produced by the Yield.
                            """) //
                            .setDynamicOperands(child("value")).setInstruction(m.yieldInstruction);
        }
        m.sourceOperation = m.operation(OperationKind.SOURCE, "Source", """
                        Source associates the children in its {@code body} with {@code source}. Together with SourceSection, it encodes source locations for operations in the program.
                        """) //
                        .setTransparent(true) //
                        .setVariadic(true) //
                        .setOperationBeginArguments(new OperationArgument(types.Source, Encoding.OBJECT, "source", "the source object to associate with the enclosed operations")) //
                        .setDynamicOperands(transparentOperationChild());
        m.sourceSectionOperation = m.operation(OperationKind.SOURCE_SECTION, "SourceSection",
                        """
                                        SourceSection associates the children in its {@code body} with the source section with the given character {@code index} and {@code length}.
                                        To specify an {@link Source#createUnavailableSection() unavailable source section}, provide {@code -1} for both arguments.
                                        This operation must be (directly or indirectly) enclosed within a Source operation.
                                        """) //
                        .setTransparent(true) //
                        .setVariadic(true) //
                        .setOperationBeginArguments(
                                        new OperationArgument(context.getType(int.class), Encoding.INTEGER, "index",
                                                        "the starting character index of the source section, or -1 if the section is unavailable"),
                                        new OperationArgument(context.getType(int.class), Encoding.INTEGER, "length",
                                                        "the length (in characters) of the source section, or -1 if the section is unavailable")) //
                        .setDynamicOperands(transparentOperationChild());

        if (m.enableTagInstrumentation) {
            m.tagEnterInstruction = m.instruction(InstructionKind.TAG_ENTER, "tag.enter", m.signature(void.class));
            m.tagEnterInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagLeaveValueInstruction = m.instruction(InstructionKind.TAG_LEAVE, "tag.leave", m.signature(Object.class, Object.class));
            m.tagLeaveValueInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagLeaveVoidInstruction = m.instruction(InstructionKind.TAG_LEAVE_VOID, "tag.leaveVoid", m.signature(Object.class));
            m.tagLeaveVoidInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagOperation = m.operation(OperationKind.TAG, "Tag",
                            """
                                            Tag associates {@code tagged} with the given tags.
                                            When the {@link BytecodeConfig} includes one or more of the given tags, the interpreter will automatically invoke instrumentation probes when entering/leaving {@code tagged}.
                                            """) //
                            .setTransparent(true) //
                            .setOperationBeginArgumentVarArgs(true) //
                            .setOperationBeginArguments(
                                            new OperationArgument(new ArrayCodeTypeMirror(context.getDeclaredType(Class.class)), Encoding.TAGS, "newTags",
                                                            "the tags to associate with the enclosed operations"))//
                            .setDynamicOperands(voidableChild("tagged")) //
                            .setOperationEndArguments(
                                            new OperationArgument(new ArrayCodeTypeMirror(context.getDeclaredType(Class.class)), Encoding.TAGS, "newTags",
                                                            "the tags to associate with the enclosed operations"))//
                            .setInstruction(m.tagLeaveValueInstruction);

            if (m.enableYield) {
                m.tagYieldInstruction = m.instruction(InstructionKind.TAG_YIELD, "tag.yield", m.signature(Object.class, Object.class));
                m.tagYieldInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");

                m.tagResumeInstruction = m.instruction(InstructionKind.TAG_RESUME, "tag.resume", m.signature(void.class));
                m.tagResumeInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            }
        }

        m.loadVariadicInstruction = new InstructionModel[9];
        for (int i = 0; i <= 8; i++) {
            m.loadVariadicInstruction[i] = m.instruction(InstructionKind.LOAD_VARIADIC, "load.variadic_" + i, m.signature(void.class, Object.class));
            m.loadVariadicInstruction[i].variadicPopCount = i;
        }
        m.mergeVariadicInstruction = m.instruction(InstructionKind.MERGE_VARIADIC, "merge.variadic", m.signature(Object.class, Object.class));
        m.storeNullInstruction = m.instruction(InstructionKind.STORE_NULL, "constant_null", m.signature(Object.class));

        m.clearLocalInstruction = m.instruction(InstructionKind.CLEAR_LOCAL, "clear.local", m.signature(void.class));
        m.clearLocalInstruction.addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset");
    }

    private static DynamicOperandModel child(String name) {
        return new DynamicOperandModel(List.of(name), false, false);
    }

    private static DynamicOperandModel voidableChild(String name) {
        return new DynamicOperandModel(List.of(name), true, false);
    }

    private static DynamicOperandModel transparentOperationChild() {
        return new DynamicOperandModel(List.of("body"), true, true);
    }
}
