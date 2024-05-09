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

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.ImmediateKind;
import com.oracle.truffle.dsl.processor.bytecode.model.InstructionModel.InstructionKind;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationArgument;
import com.oracle.truffle.dsl.processor.bytecode.model.OperationModel.OperationKind;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;

/**
 * Helper class that initializes a {@link BytecodeDSLModel} with all of the Bytecode DSL builtins.
 */
public class BytecodeDSLBuiltins {
    public static void addBuiltins(BytecodeDSLModel m, TruffleTypes types, ProcessorContext context) {
        m.popInstruction = m.instruction(InstructionKind.POP, "pop", m.signature(void.class, Object.class));
        m.dupInstruction = m.instruction(InstructionKind.DUP, "dup", m.signature(void.class));
        m.trapInstruction = m.instruction(InstructionKind.TRAP, "trap", m.signature(void.class));
        m.returnInstruction = m.instruction(InstructionKind.RETURN, "return", m.signature(void.class, Object.class));
        m.branchInstruction = m.instruction(InstructionKind.BRANCH, "branch", m.signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        m.branchBackwardInstruction = m.instruction(InstructionKind.BRANCH_BACKWARD, "branch.backward", m.signature(void.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target");
        m.branchFalseInstruction = m.instruction(InstructionKind.BRANCH_FALSE, "branch.false", m.signature(void.class, Object.class)) //
                        .addImmediate(ImmediateKind.BYTECODE_INDEX, "branch_target") //
                        .addImmediate(ImmediateKind.BRANCH_PROFILE, "branch_profile");
        m.throwInstruction = m.instruction(InstructionKind.THROW, "throw", m.signature(void.class, void.class)) //
                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "exception_local");
        m.loadConstantInstruction = m.instruction(InstructionKind.LOAD_CONSTANT, "load.constant", m.signature(Object.class)) //
                        .addImmediate(ImmediateKind.CONSTANT, "constant");

        m.blockOperation = m.operation(OperationKind.BLOCK, "Block",
                        """
                                        Block is a grouping operation that executes one or more children sequentially, producing the result of the last child (if any).
                                        This operation can be used to group multiple operations together in a single operation.
                                        It has a similar role to a block in Java, but it can also produce a value (i.e., blocks can be expressions).
                                          """) //
                        .setTransparent(true) //
                        .setVariadic(0) //
                        .setChildrenMustBeValues(false);
        m.rootOperation = m.operation(OperationKind.ROOT, "Root",
                        String.format("""
                                        Each Root operation defines one function (i.e., a {@link %s}). It takes one or more children, which define the body of the function.
                                        <p>
                                        A root operation is typically the outermost one. That is, a {@link BytecodeParser} should invoke {@link #beginRoot} first before using other builder methods to generate bytecode.
                                        The parser should invoke {@link #endRoot} to finish generating the {@link %s}.
                                        <p>
                                        This operation *can* be nested in Source and SourceSection operations in order to provide a source location for the entire root node.
                                        <p>
                                        This method can also be called inside of another root operation. Bytecode generation for the first root node suspends until generation for the second root node finishes.
                                        The second root node will be completely independent of the first (i.e., it is not "nested" and can be invoked directly).
                                        Multiple root nodes can be obtained from the {@link BytecodeNodes} object in the order of their {@link #beginRoot} calls.
                                        """,
                                        m.templateType.getSimpleName(), m.templateType.getSimpleName())) //
                        .setVariadic(0) //
                        .setTransparent(true) //
                        .setChildrenMustBeValues(false) //
                        .setOperationBeginArguments(new OperationArgument(types.TruffleLanguage, "language", "the language to associate with the root node"));
        m.ifThenOperation = m.operation(OperationKind.IF_THEN, "IfThen", """
                        IfThen implements an if-then statement. It has two children. It evaluates its first child, and if it produces {@code true}, it executes its second child.
                        This operation does not produce a result.
                        Note that only Java booleans are accepted as results of the first operation, and all other values produce undefined behaviour.
                        """) //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, false);
        m.ifThenElseOperation = m.operation(OperationKind.IF_THEN_ELSE, "IfThenElse",
                        """
                                        IfThenElse implements an if-then-else statement. It has three children. It evaluates its first child, and if it produces {@code true}, it executes its second child; otherwise, it executes its third child.
                                        This operation does not produce a result.
                                        Note that only Java booleans are accepted as results of the first operation, and all other values produce undefined behaviour.
                                        """) //
                        .setVoid(true) //
                        .setNumChildren(3) //
                        .setChildrenMustBeValues(true, false, false);
        m.conditionalOperation = m.operation(OperationKind.CONDITIONAL, "Conditional",
                        """
                                        Conditional implements a conditional expression (e.g., {@code cond ? a : b} in Java). It has the same semantics as IfThenElse, except it produces the value of the child that is conditionally executed.
                                        """) //
                        .setNumChildren(3) //
                        .setChildrenMustBeValues(true, true, true);

        m.whileOperation = m.operation(OperationKind.WHILE, "While",
                        """
                                        While implements a while loop. It has two children. It evaluates its first child, and if it produces {@code true}, it executes its second child and then repeats.
                                        Note that only Java booleans are accepted as results of the first operation, and all other values produce undefined behaviour.
                                        """) //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, false);
        m.tryCatchOperation = m.operation(OperationKind.TRY_CATCH, "TryCatch",
                        """
                                        TryCatch implements an exception handler. It has two children: the body and the handler. It executes the body, and if any {@link com.oracle.truffle.api.exception.AbstractTruffleException} occurs during execution, it stores the exception in the given local and executes the handler.
                                        Unlike a Java try-catch, this operation does not filter the exception based on type.
                                        """) //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "exceptionLocal", "the local to bind the caught exception to"));
        m.finallyTryOperation = m.operation(OperationKind.FINALLY_TRY, "FinallyTry",
                        """
                                        FinallyTry implements a finally handler. It takes two children: the handler and the body. It executes the body, and after execution finishes it always executes the handler.
                                        If the body finishes normally, the handler executes and control continues after the FinallyTry operation.
                                        If the body finishes exceptionally, the handler executes and can access the Truffle exception using the given local. The exception is rethrown after the handler.
                                        If the body finishes with a control flow operation, the handler executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
                                        <p>
                                        Note that the first child is the handler and the second child is the body. Specifying the handler first greatly simplifies and speeds up bytecode generation.
                                        """) //
                        .setVoid(true) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(false, false) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "exceptionLocal", "the local to bind a thrown exception to (if available)"));
        m.operation(OperationKind.FINALLY_TRY_CATCH, "FinallyTryCatch",
                        """
                                        FinallyTryCatch implements a finally handler that behaves differently when an exception is thrown. It takes three children: the regular handler, the body, and the exceptional handler. It executes the body and then executes one of the handlers.
                                        If the body finishes normally, the regular handler executes and control continues after the FinallyTryCatch operation.
                                        If the body finishes exceptionally, the exceptional handler executes and can access the Truffle exception using the given local. The exception is rethrown after the handler.
                                        If the body finishes with a control flow operation, the regular handler executes and then the control flow operation continues (i.e., a Branch will branch, a Return will return).
                                        """) //
                        .setVoid(true) //
                        .setNumChildren(3) //
                        .setChildrenMustBeValues(false, false, false) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "exceptionLocal", "the local to bind a thrown exception to"));
        m.operation(OperationKind.LABEL, "Label", """
                        Label defines a location in the bytecode that can be used as a forward Branch target.
                        <p>
                        Each {@link BytecodeLabel} must be defined exactly once. It should be defined directly inside the same operation in which it is created (using {@link #createLabel}).
                        """) //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLabel, "label", "the label to define"));
        m.operation(OperationKind.BRANCH, "Branch", """
                        Branch performs a branch to the given label.
                        This operation only supports unconditional forward branches; use IfThen and While to perform other kinds of branches.
                        """) //
                        .setVoid(true) //
                        .setNumChildren(0) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLabel, "label", "the label to branch to")) //
                        .setInstruction(m.branchInstruction);
        m.loadConstantOperation = m.operation(OperationKind.LOAD_CONSTANT, "LoadConstant", """
                        LoadConstant produces the given constant value. The constant should be immutable, since it may be shared across multiple LoadConstant operations.
                        """) //
                        .setNumChildren(0) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(Object.class), "constant", "the constant value to load")) //
                        .setInstruction(m.loadConstantInstruction);
        m.operation(OperationKind.LOAD_ARGUMENT, "LoadArgument", """
                        LoadArgument reads an argument from the frame using the given index and produces its value.
                        """) //
                        .setNumChildren(0) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(int.class), "index", "the index of the argument to load")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_ARGUMENT, "load.argument", m.signature(Object.class))//
                                        .addImmediate(ImmediateKind.INTEGER, "index"));
        m.loadLocalOperation = m.operation(OperationKind.LOAD_LOCAL, "LoadLocal",
                        """
                                        LoadLocal reads the given local from the frame and produces the current value.
                                        If a value has not been written to the local, LoadLocal produces the default value as defined in the {@link FrameDescriptor} ({@code null} by default).
                                        """) //
                        .setNumChildren(0) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "local", "the local to load")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_LOCAL, "load.local", m.signature(Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.loadLocalMaterializedOperation = m.operation(OperationKind.LOAD_LOCAL_MATERIALIZED, "LoadLocalMaterialized", """
                        LoadLocalMaterialized reads the given local from the frame produced by its child.
                        This operation can be used to read locals from materialized frames, including frames of enclosing root nodes.
                        """) //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "local", "the local to load")) //
                        .setInstruction(m.instruction(InstructionKind.LOAD_LOCAL_MATERIALIZED, "load.local.mat", m.signature(Object.class, Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.storeLocalOperation = m.operation(OperationKind.STORE_LOCAL, "StoreLocal", """
                        StoreLocal executes its child and overwrites the given local with the result.
                        """) //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "local", "the local to store to")) //
                        .setInstruction(m.instruction(InstructionKind.STORE_LOCAL, "store.local", m.signature(void.class, Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.storeLocalMaterializedOperation = m.operation(OperationKind.STORE_LOCAL_MATERIALIZED, "StoreLocalMaterialized", """
                        StoreLocalMaterialized evaluates its first child to produce a frame, then evaluates its second child and stores the result in the given local.
                        This operation can be used to store locals into materialized frames, including frames of enclosing root nodes.
                        """) //
                        .setNumChildren(2) //
                        .setChildrenMustBeValues(true, true) //
                        .setVoid(true) //
                        .setOperationBeginArguments(new OperationArgument(types.BytecodeLocal, "local", "the local to store to")) //
                        .setInstruction(m.instruction(InstructionKind.STORE_LOCAL_MATERIALIZED, "store.local.mat",
                                        m.signature(void.class, Object.class, Object.class)) //
                                        .addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset"));
        m.returnOperation = m.operation(OperationKind.RETURN, "Return", "Return evaluates its child and returns the result.") //
                        .setNumChildren(1) //
                        .setChildrenMustBeValues(true) //
                        .setInstruction(m.returnInstruction);
        if (m.enableYield) {
            m.yieldInstruction = m.instruction(InstructionKind.YIELD, "yield", m.signature(void.class, Object.class)).addImmediate(ImmediateKind.CONSTANT, "location");
            m.operation(OperationKind.YIELD, "Yield", """
                            Yield executes its child, suspends execution at the given location, and returns a {@link com.oracle.truffle.api.bytecode.ContinuationResult} containing the result.
                            The caller can resume the continuation, which continues execution after the Yield. When resuming, the caller passes a value that becomes the value produced by the Yield.
                            """) //
                            .setNumChildren(1) //
                            .setChildrenMustBeValues(true) //
                            .setInstruction(m.yieldInstruction);
        }
        m.sourceOperation = m.operation(OperationKind.SOURCE, "Source", """
                        Source associates the enclosed children with the given source object. Together with SourceSection, it encodes source locations for operations in the program.
                        """) //
                        .setVariadic(0) //
                        .setChildrenMustBeValues(false) //
                        .setTransparent(true) //
                        .setRequiresParentRoot(false) //
                        .setOperationBeginArguments(new OperationArgument(types.Source, "source", "the source object to associate with the enclosed operations"));
        m.sourceSectionOperation = m.operation(OperationKind.SOURCE_SECTION, "SourceSection", """
                        SourceSection associates the enclosed children with the given source character index and length. It must be (directly or indirectly) enclosed within a Source operation.
                        """) //
                        .setVariadic(0) //
                        .setChildrenMustBeValues(false)//
                        .setTransparent(true) //
                        .setRequiresParentRoot(false) //
                        .setOperationBeginArguments(new OperationArgument(context.getType(int.class), "index", "the starting character index of the source section"),
                                        new OperationArgument(context.getType(int.class), "length", "the length (in characters) of the source section"));

        if (m.enableTagInstrumentation) {
            m.tagEnterInstruction = m.instruction(InstructionKind.TAG_ENTER, "tag.enter", m.signature(void.class));
            m.tagEnterInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagLeaveValueInstruction = m.instruction(InstructionKind.TAG_LEAVE, "tag.leave", m.signature(Object.class, Object.class));
            m.tagLeaveValueInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagLeaveVoidInstruction = m.instruction(InstructionKind.TAG_LEAVE_VOID, "tag.leaveVoid", m.signature(Object.class));
            m.tagLeaveVoidInstruction.addImmediate(ImmediateKind.TAG_NODE, "tag");
            m.tagOperation = m.operation(OperationKind.TAG, "Tag",
                            """
                                            Tag associates the enclosed children with the given tags.
                                            When the {@link BytecodeConfig} includes one or more of the given tags, the root node will automatically invoke instrumentation probes when entering/leaving the enclosed operations.
                                            """) //
                            .setNumChildren(1) //
                            .setOperationBeginArgumentVarArgs(true) //
                            .setOperationBeginArguments(
                                            new OperationArgument(new ArrayCodeTypeMirror(context.getDeclaredType(Class.class)), "newTags", "the tags to associate with the enclosed operations"))//
                            .setInstruction(m.tagLeaveValueInstruction);

        }

        m.popVariadicInstruction = new InstructionModel[9];
        for (int i = 0; i <= 8; i++) {
            m.popVariadicInstruction[i] = m.instruction(InstructionKind.LOAD_VARIADIC, "load.variadic_" + i, m.signature(void.class, Object.class));
            m.popVariadicInstruction[i].variadicPopCount = i;
        }
        m.mergeVariadicInstruction = m.instruction(InstructionKind.MERGE_VARIADIC, "merge.variadic", m.signature(Object.class, Object.class));
        m.storeNullInstruction = m.instruction(InstructionKind.STORE_NULL, "constant_null", m.signature(Object.class));

        m.clearLocalInstruction = m.instruction(InstructionKind.CLEAR_LOCAL, "clear.local", m.signature(void.class));
        m.clearLocalInstruction.addImmediate(ImmediateKind.LOCAL_OFFSET, "localOffset");
    }
}
