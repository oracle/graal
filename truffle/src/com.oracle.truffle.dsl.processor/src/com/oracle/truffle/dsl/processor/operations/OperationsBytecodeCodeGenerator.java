package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.GeneratorUtils;
import com.oracle.truffle.dsl.processor.generator.NodeCodeGenerator;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeAnnotationValue;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction.DataKind;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.ExecutionVariables;
import com.oracle.truffle.dsl.processor.operations.instructions.Instruction.InputType;

public class OperationsBytecodeCodeGenerator {

    private final Set<Modifier> MOD_PRIVATE_FINAL = Set.of(Modifier.PRIVATE, Modifier.FINAL);
    private final Set<Modifier> MOD_PRIVATE_STATIC_FINAL = Set.of(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);

    final static Object MARKER_CHILD = new Object();
    final static Object MARKER_CONST = new Object();

    static final boolean DO_STACK_LOGGING = false;

    final ProcessorContext context = ProcessorContext.getInstance();
    final TruffleTypes types = context.getTypes();

    private static final String ConditionProfile_Name = "com.oracle.truffle.api.profiles.ConditionProfile";
    final DeclaredType ConditionProfile = context.getDeclaredType(ConditionProfile_Name);

    private final CodeTypeElement typBuilderImpl;
    private final String simpleName;
    private final OperationsData m;
    private final boolean withInstrumentation;
    private final OperationsCodeGenerator parent;

    public OperationsBytecodeCodeGenerator(OperationsCodeGenerator parent, CodeTypeElement typBuilderImpl, String simpleName, OperationsData m, boolean withInstrumentation) {
        this.parent = parent;
        this.typBuilderImpl = typBuilderImpl;
        this.simpleName = simpleName;
        this.m = m;
        this.withInstrumentation = withInstrumentation;
    }

    /**
     * Create the BytecodeNode type. This type contains the bytecode interpreter, and is the
     * executable Truffle node.
     */
    public CodeTypeElement createBuilderBytecodeNode() {
        CodeTypeElement builderBytecodeNodeType = GeneratorUtils.createClass(m, null, MOD_PRIVATE_STATIC_FINAL, simpleName, types.OperationsNode);

        CodeVariableElement fldBc = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(byte.class)), "bc");
        GeneratorUtils.addCompilationFinalAnnotation(fldBc, 1);
        builderBytecodeNodeType.add(fldBc);

        CodeVariableElement fldConsts = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(Object.class)), "consts");
        GeneratorUtils.addCompilationFinalAnnotation(fldConsts, 1);
        builderBytecodeNodeType.add(fldConsts);

        CodeVariableElement fldChildren = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(types.Node), "children");
        fldChildren.addAnnotationMirror(new CodeAnnotationMirror(types.Node_Children));
        builderBytecodeNodeType.add(fldChildren);

        CodeVariableElement fldHandlers = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(types.BuilderExceptionHandler), "handlers");
        GeneratorUtils.addCompilationFinalAnnotation(fldHandlers, 1);
        builderBytecodeNodeType.add(fldHandlers);

        CodeVariableElement fldConditionBranches = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(ConditionProfile), "conditionProfiles");
        GeneratorUtils.addCompilationFinalAnnotation(fldConditionBranches, 1);
        builderBytecodeNodeType.add(fldConditionBranches);

        CodeVariableElement fldSuccessorIndices = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(short.class)), "successorIndices");
        GeneratorUtils.addCompilationFinalAnnotation(fldSuccessorIndices, 1);
        builderBytecodeNodeType.add(fldSuccessorIndices);

        CodeVariableElement fldProbeNodes = null;
        if (withInstrumentation) {
            fldProbeNodes = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(types.OperationsInstrumentTreeNode), "instruments");
            GeneratorUtils.addCompilationFinalAnnotation(fldProbeNodes, 1);
            builderBytecodeNodeType.add(fldProbeNodes);
        }

        CodeExecutableElement ctor = GeneratorUtils.createConstructorUsingFields(Set.of(), builderBytecodeNodeType);
        builderBytecodeNodeType.add(ctor);

        CodeVariableElement fldTracer = null;
        CodeVariableElement fldHitCount = null;
        if (m.isTracing()) {
            fldHitCount = new CodeVariableElement(MOD_PRIVATE_FINAL, arrayOf(context.getType(int.class)), "hitCount");
            builderBytecodeNodeType.add(fldHitCount);

            fldTracer = new CodeVariableElement(types.ExecutionTracer, "tracer");
            builderBytecodeNodeType.add(fldTracer);
        }

        {
            CodeTreeBuilder b = ctor.getBuilder();

            if (m.isTracing()) {
                b.startAssign(fldHitCount).startNewArray(
                                (ArrayType) fldHitCount.getType(),
                                CodeTreeBuilder.createBuilder().variable(fldBc).string(".length").build());
                b.end(2);

                b.startAssign(fldTracer).startStaticCall(types.ExecutionTracer, "get").end(2);
            }
        }

        {
            Set<String> copiedLibraries = new HashSet<>();
            for (Instruction instr : m.getInstructions()) {
                if (!(instr instanceof CustomInstruction)) {
                    continue;
                }

                CustomInstruction cinstr = (CustomInstruction) instr;

                boolean isVariadic = cinstr.getData().getMainProperties().isVariadic;

                final Set<String> methodNames = new HashSet<>();
                final Set<String> innerTypeNames = new HashSet<>();

                final SingleOperationData soData = cinstr.getData();
                final List<Object> additionalData = new ArrayList<>();
                final List<DataKind> additionalDataKinds = new ArrayList<>();

                final List<Object> childIndices = new ArrayList<>();
                final List<Object> constIndices = new ArrayList<>();

                OperationsBytecodeNodeGeneratorPlugs plugs = new OperationsBytecodeNodeGeneratorPlugs(
                                fldBc, fldChildren, constIndices,
                                innerTypeNames, additionalData,
                                methodNames, isVariadic, soData,
                                additionalDataKinds,
                                fldConsts, cinstr, childIndices);
                cinstr.setPlugs(plugs);

                NodeCodeGenerator generator = new NodeCodeGenerator();
                generator.setPlugs(plugs);

                List<CodeTypeElement> resultList = generator.create(context, null, soData.getNodeData());
                if (resultList.size() != 1) {
                    throw new AssertionError("Node generator did not return exactly one class");
                }
                CodeTypeElement result = resultList.get(0);

                CodeExecutableElement uncExec = null;
                List<CodeExecutableElement> execs = new ArrayList<>();
                for (ExecutableElement ex : ElementFilter.methodsIn(result.getEnclosedElements())) {
                    if (!methodNames.contains(ex.getSimpleName().toString())) {
                        continue;
                    }

                    if (ex.getSimpleName().toString().equals(plugs.transformNodeMethodName("execute"))) {
                        uncExec = (CodeExecutableElement) ex;
                    }

                    execs.add((CodeExecutableElement) ex);
                }

                if (uncExec == null) {
                    throw new AssertionError(String.format("execute method not found in: %s", result.getSimpleName()));
                }

                for (TypeElement te : ElementFilter.typesIn(result.getEnclosedElements())) {
                    if (!innerTypeNames.contains(te.getSimpleName().toString())) {
                        continue;
                    }

                    builderBytecodeNodeType.add(te);
                }

                for (VariableElement ve : ElementFilter.fieldsIn(result.getEnclosedElements())) {
                    if (ve.getSimpleName().toString().equals("UNCACHED")) {
                        continue;
                    }
                    if (!ve.getModifiers().containsAll(MOD_PRIVATE_STATIC_FINAL)) {
                        continue;
                    }

                    if (copiedLibraries.contains(ve.getSimpleName().toString())) {
                        continue;
                    }

                    copiedLibraries.add(ve.getSimpleName().toString());

                    builderBytecodeNodeType.add(ve);
                }

                for (CodeExecutableElement exToCopy : execs) {
                    boolean isBoundary = exToCopy.getAnnotationMirrors().stream().anyMatch(x -> x.getAnnotationType().equals(types.CompilerDirectives_TruffleBoundary));

                    boolean isExecute = exToCopy.getSimpleName().toString().contains("_execute_");
                    boolean isExecuteAndSpecialize = exToCopy.getSimpleName().toString().endsWith("_executeAndSpecialize_");
                    boolean isFallbackGuard = exToCopy.getSimpleName().toString().endsWith("_fallbackGuard__");

                    if (isExecute || isExecuteAndSpecialize || isFallbackGuard) {
                        List<VariableElement> params = exToCopy.getParameters();

                        for (int i = 0; i < params.size(); i++) {
                            if (params.get(i).asType().equals(types.VirtualFrame)) {
                                params.remove(i);
                                i--;
                            }
                        }
                    }

                    if (!isVariadic) {
                        if (isExecute || isExecuteAndSpecialize) {
                            exToCopy.setReturnType(context.getType(void.class));
                        }
                    }

                    exToCopy.getParameters().add(0, new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "$sp"));
                    exToCopy.getParameters().add(0, new CodeVariableElement(new CodeTypeMirror(TypeKind.INT), "$bci"));
                    if (!isBoundary) {
                        exToCopy.getParameters().add(0, new CodeVariableElement(types.VirtualFrame, "$frame"));
                    }
                    exToCopy.getModifiers().remove(Modifier.PUBLIC);
                    exToCopy.getModifiers().add(Modifier.PRIVATE);
                    exToCopy.getAnnotationMirrors().removeIf(x -> x.getAnnotationType().equals(context.getType(Override.class)));
                    builderBytecodeNodeType.add(exToCopy);
                }

                cinstr.setExecuteMethod(uncExec);
                cinstr.setDataKinds(additionalDataKinds.toArray(new DataKind[additionalDataKinds.size()]));
                cinstr.setNumChildNodes(childIndices.size());
                cinstr.setNumConsts(constIndices.size());
            }

        }

        parent.createSetBoxedForInstructions(typBuilderImpl);

        ExecutionVariables vars = new ExecutionVariables();
        // vars.bytecodeNodeType = builderBytecodeNodeType;
        vars.bc = fldBc;
        vars.consts = fldConsts;
        vars.probeNodes = fldProbeNodes;
        // vars.handlers = fldHandlers;
        // vars.tracer = fldTracer;

        {
            CodeVariableElement argFrame = new CodeVariableElement(types.VirtualFrame, "frame");
            CodeVariableElement argStartIndex = new CodeVariableElement(types.OperationLabel, "startIndex");
            CodeExecutableElement mContinueAt = new CodeExecutableElement(
                            Set.of(Modifier.PUBLIC), context.getType(Object.class), "continueAt",
                            argFrame, argStartIndex);
            builderBytecodeNodeType.add(mContinueAt);

            {
                CodeAnnotationMirror annExplodeLoop = new CodeAnnotationMirror(types.ExplodeLoop);
                mContinueAt.addAnnotationMirror(annExplodeLoop);
                annExplodeLoop.setElementValue("kind", new CodeAnnotationValue(new CodeVariableElement(
                                context.getDeclaredType("com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind"), "MERGE_EXPLODE")));
            }

            CodeTreeBuilder b = mContinueAt.getBuilder();

            CodeVariableElement varSp = new CodeVariableElement(context.getType(int.class), "sp");
            CodeVariableElement varBci = new CodeVariableElement(context.getType(int.class), "bci");
            CodeVariableElement varCurOpcode = new CodeVariableElement(context.getType(short.class), "curOpcode");

            b.declaration("int", varSp.getName(), "maxLocals + VALUES_OFFSET");
            b.declaration("int", varBci.getName(), "0");

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "startFunction").string("this").end(2);
            }

            CodeVariableElement varReturnValue = new CodeVariableElement(context.getType(Object.class), "returnValue");
            b.statement("Object " + varReturnValue.getName() + " = null");

            b.string("loop: ");
            b.startWhile().string("true").end();
            b.startBlock();
            CodeVariableElement varNextBci = new CodeVariableElement(context.getType(int.class), "nextBci");
            b.statement("int nextBci");

            vars.bci = varBci;
            vars.nextBci = varNextBci;
            vars.frame = argFrame;
            vars.sp = varSp;
            vars.returnValue = varReturnValue;

            b.declaration("short", varCurOpcode.getName(), OperationGeneratorUtils.createReadOpcode(fldBc, varBci));

            b.startTryBlock();

            b.tree(GeneratorUtils.createPartialEvaluationConstant(varBci));
            b.tree(GeneratorUtils.createPartialEvaluationConstant(varSp));
            b.tree(GeneratorUtils.createPartialEvaluationConstant(varCurOpcode));

            if (m.isTracing()) {
                b.startStatement().variable(fldHitCount).string("[bci]++").end();
            }

            b.startIf().variable(varSp).string(" < maxLocals + VALUES_OFFSET").end();
            b.startBlock();
            b.tree(GeneratorUtils.createShouldNotReachHere("stack underflow"));
            b.end();

            b.startSwitch().string("curOpcode").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                if (op.isInstrumentationOnly() && !withInstrumentation) {
                    continue;
                }

                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                CodeVariableElement[] varInputs = null;
                CodeVariableElement[] varResults = null;

                if (op.standardPrologue()) {

                    varInputs = new CodeVariableElement[op.inputs.length];
                    TypeMirror[] inputTypes = op.expectedInputTypes(context);
                    vars.inputs = varInputs;
                    for (int i = op.inputs.length - 1; i >= 0; i--) {
                        if (op.inputs[i] == InputType.STACK_VALUE_IGNORED) {
                            b.statement("--sp");
                            continue;
                        }

                        varInputs[i] = new CodeVariableElement(inputTypes[i], "input_" + i);

                        b.declaration(varInputs[i].asType(), varInputs[i].getName(), createInputCode(vars, op, i, inputTypes[i]));
                        if (op.inputs[i] == InputType.VARARG_VALUE) {
                            b.startFor().string("int i = ").variable(varInputs[i]).string(".length - 1; i >= 0; i--").end();
                            b.startBlock();
                            b.startStatement().variable(varInputs[i]).string("[i] = ");
                            b.variable(argFrame).string(".getValue(--sp)");
                            b.end(2);
                        }
                    }

                    varResults = new CodeVariableElement[op.results.length];
                    vars.results = varResults;
                    for (int i = 0; i < op.results.length; i++) {
                        switch (op.results[i]) {
                            case STACK_VALUE:
                            case SET_LOCAL:
                                varResults[i] = new CodeVariableElement(context.getType(Object.class), "result_" + i);
                                b.statement("Object result_" + i);
                                break;
                            case BRANCH:
                                varResults[i] = varBci;
                                break;
                            case RETURN:
                                varResults[i] = varReturnValue;
                                break;
                        }
                    }
                }

                b.tree(op.createExecuteCode(vars));

                if (op.standardPrologue()) {
                    for (int i = 0; i < op.results.length; i++) {
                        switch (op.results[i]) {
                            case STACK_VALUE:
                                b.startStatement().startCall(argFrame, "setObject").string("sp++").variable(varResults[i]).end(2);
                                break;
                            case SET_LOCAL:
                                b.startStatement().startCall(vars.frame, "setObject") //
                                                .startGroup().string("VALUES_OFFSET + ").tree(op.createReadArgumentCode(op.inputs.length + i, vars)).end() //
                                                .variable(varResults[i]) //
                                                .end(2);
                                break;
                        }
                    }

                    if (m.isTracing()) {
                        b.startStatement().startCall(fldTracer, "traceInstruction");
                        b.variable(varBci);
                        b.variable(op.opcodeIdField);
                        b.doubleQuote(op.name);

                        for (CodeVariableElement input : varInputs) {
                            if (input == null) {
                                b.string("null");
                            } else {
                                b.variable(input);
                            }
                        }

                        for (CodeVariableElement res : varResults) {
                            if (res == null) {
                                b.string("null");
                            } else {
                                b.variable(res);
                            }
                        }

                        b.end(2);
                    }
                }

                if (op.isReturnInstruction()) {
                    b.statement("break loop");
                } else if (!op.isBranchInstruction()) {
                    b.startAssign(varNextBci).variable(varBci).string(" + " + op.length()).end();
                    b.statement("break");
                }

                b.end();

                vars.inputs = null;
                vars.results = null;
            }

            b.caseDefault().startCaseBlock();
            b.tree(GeneratorUtils.createTransferToInterpreterAndInvalidate());
            b.tree(GeneratorUtils.createShouldNotReachHere("unknown opcode encountered"));
            b.end();

            b.end(); // switch block

            b.end().startCatchBlock(context.getDeclaredType("com.oracle.truffle.api.exception.AbstractTruffleException"), "ex");

            b.tree(GeneratorUtils.createPartialEvaluationConstant(varBci));

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "traceException");
                b.string("ex");
                b.end(2);

            }

            b.startFor().string("int handlerIndex = 0; handlerIndex < " + fldHandlers.getName() + ".length; handlerIndex++").end();
            b.startBlock();

            b.tree(GeneratorUtils.createPartialEvaluationConstant("handlerIndex"));

            b.declaration(types.BuilderExceptionHandler, "handler", fldHandlers.getName() + "[handlerIndex]");

            b.startIf().string("handler.startBci > bci || handler.endBci <= bci").end();
            b.statement("continue");

            b.startAssign(varSp).string("handler.startStack + VALUES_OFFSET + maxLocals").end();
            // TODO check exception type (?)

            b.startStatement().startCall(argFrame, "setObject") //
                            .string("VALUES_OFFSET + handler.exceptionIndex") //
                            .string("ex") //
                            .end(2);

            b.statement("bci = handler.handlerBci");
            b.statement("continue loop");

            b.end(); // for (handlerIndex ...)

            b.startThrow().string("ex").end();

            b.end(); // catch block

            b.tree(GeneratorUtils.createPartialEvaluationConstant(varNextBci));
            b.statement("bci = nextBci");
            b.end(); // while block

            if (m.isTracing()) {
                b.startStatement().startCall(fldTracer, "endFunction").end(2);
            }

            b.startReturn().string("returnValue").end();

            vars.bci = null;
            vars.nextBci = null;
            vars.frame = null;
            vars.sp = null;
            vars.returnValue = null;

        }

        {
            CodeExecutableElement mDump = new CodeExecutableElement(Set.of(Modifier.PUBLIC), context.getType(String.class), "dump");
            builderBytecodeNodeType.add(mDump);

            CodeTreeBuilder b = mDump.getBuilder();

            b.declaration("int", "bci", "0");
            b.declaration("int", "instrIndex", "0");
            b.declaration("StringBuilder", "sb", "new StringBuilder()");

            vars.bci = new CodeVariableElement(context.getType(int.class), "bci");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            if (m.isTracing()) {
                b.statement("sb.append(String.format(\" [ %3d ]\", hitCount[bci]))");
            }

            b.statement("sb.append(String.format(\" %04x \", bci))");

            b.startSwitch().tree(OperationGeneratorUtils.createReadOpcode(fldBc, vars.bci)).end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                if (op.isInstrumentationOnly() && !withInstrumentation) {
                    continue;
                }
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                if (op.numPush() == 0) {
                    b.statement("sb.append(\"            \")");
                } else {
                    b.statement("sb.append(String.format(\"[ %04x %2d ] \", successorIndices[instrIndex], successorIndices[instrIndex + 1]))");
                    b.statement("instrIndex += 2");
                }

                for (int i = 0; i < 16; i++) {
                    if (i < op.length()) {
                        b.statement("sb.append(String.format(\"%02x \", bc[bci + " + i + "]))");
                    } else {
                        b.statement("sb.append(\"   \")");
                    }
                }

                b.statement("sb.append(\"" + op.name + " ".repeat(op.name.length() < 32 ? 32 - op.name.length() : 0) + " \")");

                for (int i = 0; i < op.inputs.length; i++) {
                    if (i != 0) {
                        b.statement("sb.append(\", \")");
                    }
                    b.tree(op.inputs[i].createDumpCode(i, op, vars));
                }

                b.statement("sb.append(\" -> \")");

                for (int i = 0; i < op.results.length; i++) {
                    if (i != 0) {
                        b.statement("sb.append(\", \")");
                    }
                    b.tree(op.results[i].createDumpCode(i, op, vars));
                }

                b.statement("bci += " + op.length());
                b.statement("break");

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.statement("sb.append(String.format(\"unknown 0x%02x\", bc[bci++]))");
            b.statement("break");
            b.end(); // default case block
            b.end(); // switch block

            b.statement("sb.append(\"\\n\")");

            b.end(); // while block

            b.startFor().string("int i = 0; i < ").variable(fldHandlers).string(".length; i++").end();
            b.startBlock();

            b.startStatement().string("sb.append(").variable(fldHandlers).string("[i] + \"\\n\")").end();

            b.end();

            b.startIf().string("sourceInfo != null").end();
            b.startBlock();
            {
                b.statement("sb.append(\"Source info:\\n\")");
                b.startFor().string("int i = 0; i < sourceInfo[0].length; i++").end();
                b.startBlock();

                b.statement("sb.append(String.format(\"  bci=%04x, offset=%d, length=%d\\n\", sourceInfo[0][i], sourceInfo[1][i], sourceInfo[2][i]))");

                b.end();
            }
            b.end();

            b.startReturn().string("sb.toString()").end();

            vars.bci = null;

        }

        if (m.isTracing()) {
            CodeExecutableElement mGetTrace = GeneratorUtils.override(types.OperationsNode, "getNodeTrace");
            builderBytecodeNodeType.add(mGetTrace);

            CodeTreeBuilder b = mGetTrace.getBuilder();

            b.declaration("int", "bci", "0");
            b.declaration("ArrayList<InstructionTrace>", "insts", "new ArrayList<>()");

            vars.bci = new CodeVariableElement(context.getType(int.class), "bci");

            b.startWhile().string("bci < bc.length").end();
            b.startBlock(); // while block

            b.startSwitch().string("bc[bci]").end();
            b.startBlock();

            for (Instruction op : m.getInstructions()) {
                if (op.isInstrumentationOnly() && !withInstrumentation) {
                    continue;
                }
                b.startCase().variable(op.opcodeIdField).end();
                b.startBlock();

                b.startStatement();
                b.startCall("insts", "add");
                b.startNew(types.InstructionTrace);

                b.variable(op.opcodeIdField);
                b.startGroup().variable(fldHitCount).string("[bci]").end();

                b.end(3);

                b.statement("bci += " + op.length());
                b.statement("break");

                b.end();
            }

            b.caseDefault().startCaseBlock();
            b.startThrow().startNew(context.getType(IllegalArgumentException.class)).doubleQuote("Unknown opcode").end(2);
            b.end(); // default case block
            b.end(); // switch block

            b.end(); // while block

            b.startReturn().startNew(types.NodeTrace);
            b.startCall("insts", "toArray").string("new InstructionTrace[0]").end();
            b.end(2);

            vars.bci = null;
        }

        {
            CodeExecutableElement mGetSourceSection = GeneratorUtils.overrideImplement(types.Node, "getSourceSection");
            builderBytecodeNodeType.add(mGetSourceSection);

            CodeTreeBuilder b = mGetSourceSection.createBuilder();

            b.tree(createReparseCheck());

            b.startReturn();
            b.startCall("this", "getSourceSectionImpl");
            b.end(2);
        }

        {
            CodeVariableElement pBci = new CodeVariableElement(context.getType(int.class), "bci");
            CodeExecutableElement mGetSourceSectionAtBci = GeneratorUtils.overrideImplement(types.OperationsNode, "getSourceSectionAtBci");
            builderBytecodeNodeType.add(mGetSourceSectionAtBci);

            CodeTreeBuilder b = mGetSourceSectionAtBci.createBuilder();

            b.tree(createReparseCheck());

            b.startReturn();
            b.startCall("this", "getSourceSectionAtBciImpl");
            b.variable(pBci);
            b.end(2);
        }

        {
            CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
            b.startJavadoc();

            for (Instruction instr : m.getInstructions()) {
                for (String s : instr.dumpInfo().split("\n")) {
                    b.string(s);
                    b.newLine();
                }
                b.string(" ");
                b.newLine();
            }

            b.end();
            builderBytecodeNodeType.setDocTree(b.build());
        }

        return builderBytecodeNodeType;
    }

    private CodeTree createInputCode(ExecutionVariables vars, Instruction instr, int index, TypeMirror inputType) {
        switch (instr.inputs[index]) {
            case ARGUMENT:
                return CodeTreeBuilder.createBuilder().maybeCast(context.getType(Object.class), inputType).startCall(vars.frame, "getArguments").end() //
                                .string("[").tree(instr.createReadArgumentCode(index, vars)).string("]") //
                                .build();
            case LOCAL:
                return CodeTreeBuilder.createBuilder().maybeCast(context.getType(Object.class), inputType).startCall(vars.frame, "getValue") //
                                .startGroup().string("VALUES_OFFSET + ").tree(instr.createReadArgumentCode(index, vars)).end() //
                                .end().build();
            case CONST_POOL:
                return CodeTreeBuilder.createBuilder().maybeCast(context.getType(Object.class), inputType).variable(vars.consts) //
                                .string("[").tree(instr.createReadArgumentCode(index, vars)).string("]") //
                                .build();
            case BRANCH_PROFILE:
                return CodeTreeBuilder.createBuilder().string("conditionProfiles") //
                                .string("[").tree(instr.createReadArgumentCode(index, vars)).string("]") //
                                .build();
            case INSTRUMENT:
            case BRANCH_TARGET:
                return instr.createReadArgumentCode(index, vars);
            case STACK_VALUE:
                return CodeTreeBuilder.createBuilder().maybeCast(context.getType(Object.class), inputType).startCall(vars.frame, "getValue") //
                                .startGroup().string("--").variable(vars.sp).end() //
                                .end().build();
            case VARARG_VALUE:
                return CodeTreeBuilder.createBuilder().startNewArray(
                                new ArrayCodeTypeMirror(context.getType(Object.class)),
                                instr.createReadArgumentCode(index, vars)).end().build();
            default:
                throw new IllegalArgumentException("Unsupported value: " + instr.inputs[index]);

        }
    }

    private CodeTree createReparseCheck() {
        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();
        b.startIf().string("sourceInfo == null").end();
        b.startBlock();
        {
            b.startStatement();
            b.string("OperationsNode reparsed = ");
            b.startStaticCall(typBuilderImpl.asType(), "reparse");
            b.startGroup().startCall("getRootNode").end().startCall(".getLanguage").typeLiteral(m.getLanguageType()).end(2);
            b.startGroup().maybeCast(context.getType(Object.class), m.getParseContextType()).string("parseContext").end();
            b.string("buildOrder");
            b.end(2);

            b.statement("copyReparsedInfo(reparsed)");
        }
        b.end();

        return b.build();
    }

    private static TypeMirror arrayOf(TypeMirror el) {
        return new ArrayCodeTypeMirror(el);
    }

}
