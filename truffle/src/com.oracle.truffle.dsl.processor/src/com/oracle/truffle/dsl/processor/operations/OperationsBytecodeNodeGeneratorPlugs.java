package com.oracle.truffle.dsl.processor.operations;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.generator.BitSet;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.generator.NodeGeneratorPlugs;
import com.oracle.truffle.dsl.processor.generator.TypeSystemCodeGenerator;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.java.model.CodeVariableElement;
import com.oracle.truffle.dsl.processor.model.CacheExpression;
import com.oracle.truffle.dsl.processor.model.NodeExecutionData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.TypeSystemData;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction;
import com.oracle.truffle.dsl.processor.operations.instructions.CustomInstruction.DataKind;

final class OperationsBytecodeNodeGeneratorPlugs implements NodeGeneratorPlugs {
    private final CodeVariableElement fldBc;
    private final CodeVariableElement fldChildren;
    private final List<Object> constIndices;
    private final Set<String> innerTypeNames;
    private final List<Object> additionalData;
    private final Set<String> methodNames;
    private final boolean isVariadic;
    private final SingleOperationData soData;
    private final List<DataKind> additionalDataKinds;
    private final CodeVariableElement fldConsts;
    private final CustomInstruction cinstr;
    private final List<Object> childIndices;

    private final ProcessorContext context;
    private final TruffleTypes types;

    private static final boolean LOG_GET_VALUE_CALLS = false;

    OperationsBytecodeNodeGeneratorPlugs(CodeVariableElement fldBc, CodeVariableElement fldChildren, List<Object> constIndices,
                    Set<String> innerTypeNames, List<Object> additionalData,
                    Set<String> methodNames, boolean isVariadic, SingleOperationData soData, List<DataKind> additionalDataKinds, CodeVariableElement fldConsts, CustomInstruction cinstr,
                    List<Object> childIndices) {
        this.fldBc = fldBc;
        this.fldChildren = fldChildren;
        this.constIndices = constIndices;
        this.innerTypeNames = innerTypeNames;
        this.additionalData = additionalData;
        this.methodNames = methodNames;
        this.isVariadic = isVariadic;
        this.soData = soData;
        this.additionalDataKinds = additionalDataKinds;
        this.fldConsts = fldConsts;
        this.cinstr = cinstr;
        this.childIndices = childIndices;

        this.context = ProcessorContext.getInstance();
        this.types = context.getTypes();
    }

    @Override
    public String transformNodeMethodName(String name) {
        String result = soData.getName() + "_" + name + "_";
        methodNames.add(result);
        return result;
    }

    @Override
    public String transformNodeInnerTypeName(String name) {
        String result = soData.getName() + "_" + name;
        innerTypeNames.add(result);
        return result;
    }

    @Override
    public void addNodeCallParameters(CodeTreeBuilder builder, boolean isBoundary, boolean isRemoveThis) {
        if (!isBoundary) {
            builder.string("$frame");
        }
        builder.string("$bci");
        builder.string("$sp");
    }

    public boolean shouldIncludeValuesInCall() {
        return isVariadic;
    }

    @Override
    public int getMaxStateBits(int defaultValue) {
        return 8;
    }

    @Override
    public TypeMirror getBitSetType(TypeMirror defaultType) {
        return new CodeTypeMirror(TypeKind.BYTE);
    }

    @Override
    public CodeTree createBitSetReference(BitSet bits) {
        int index = additionalData.indexOf(bits);
        if (index == -1) {
            index = additionalData.size();
            additionalData.add(bits);

            additionalDataKinds.add(DataKind.BITS);
        }

        return CodeTreeBuilder.createBuilder().variable(fldBc).string("[$bci + " + cinstr.lengthWithoutState() + " + " + index + "]").build();
    }

    @Override
    public CodeTree transformValueBeforePersist(CodeTree tree) {
        return CodeTreeBuilder.createBuilder().cast(new CodeTypeMirror(TypeKind.BYTE)).startParantheses().tree(tree).end().build();
    }

    private CodeTree createArrayReference(Object refObject, boolean doCast, TypeMirror castTarget, boolean isChild) {
        if (refObject == null) {
            throw new IllegalArgumentException("refObject is null");
        }

        List<Object> refList = isChild ? childIndices : constIndices;
        int index = refList.indexOf(refObject);
        int baseIndex = additionalData.indexOf(isChild ? OperationsBytecodeCodeGenerator.MARKER_CHILD : OperationsBytecodeCodeGenerator.MARKER_CONST);

        if (index == -1) {
            if (baseIndex == -1) {
                baseIndex = additionalData.size();
                additionalData.add(isChild ? OperationsBytecodeCodeGenerator.MARKER_CHILD : OperationsBytecodeCodeGenerator.MARKER_CONST);
                additionalData.add(null);

                additionalDataKinds.add(isChild ? DataKind.CHILD : DataKind.CONST);
                additionalDataKinds.add(DataKind.CONTINUATION);
            }

            index = refList.size();
            refList.add(refObject);
        }

        CodeTreeBuilder b = CodeTreeBuilder.createBuilder();

        if (doCast) {
            b.startParantheses();
            b.cast(castTarget);
        }

        VariableElement targetField;
        if (isChild) {
            targetField = fldChildren;
        } else {
            targetField = fldConsts;
        }

        b.variable(targetField).string("[");
        b.startCall("LE_BYTES", "getShort");
        b.variable(fldBc);
        b.string("$bci + " + cinstr.lengthWithoutState() + " + " + baseIndex);
        b.end();
        b.string(" + " + index + "]");

        if (doCast) {
            b.end();
        }

        return b.build();
    }

    @Override
    public CodeTree createSpecializationFieldReference(SpecializationData s, String fieldName, boolean useSpecializationClass, TypeMirror fieldType) {
        Object refObject = useSpecializationClass ? s : fieldName;
        return createArrayReference(refObject, fieldType != null, fieldType, false);
    }

    @Override
    public CodeTree createNodeFieldReference(NodeExecutionData execution, String nodeFieldName, boolean forRead) {
        return createArrayReference(execution, forRead, execution.getNodeType(), true);
    }

    @Override
    public CodeTree createCacheReference(SpecializationData specialization, CacheExpression cache, String sharedName, boolean forRead) {
        Object refObject = sharedName != null ? sharedName : cache;
        boolean isChild = ElementUtils.isAssignable(cache.getParameter().getType(), types.Node);
        return createArrayReference(refObject, forRead, cache.getParameter().getType(), isChild);
    }

    private void createPrepareFor(String typeName, TypeMirror valueType, FrameState frameState, LocalVariable value, CodeTreeBuilder prepareBuilder) {

        boolean isValue = typeName == null;
        String type = isValue ? "Value" : typeName;

        String isName = null;
        if (!isValue) {
            isName = value.getName() + "_is" + type + "_";
            LocalVariable isVar = frameState.get(isName);
            if (isVar == null) {
                isVar = new LocalVariable(context.getType(boolean.class), isName, null);
                frameState.set(isName, isVar);
                prepareBuilder.declaration(context.getType(boolean.class), isName, (CodeTree) null);
            } else {
                prepareBuilder.lineComment("already have is" + type);
            }
        }

        String asName = value.getName() + "_as" + type + "_";
        LocalVariable asVar = frameState.get(asName);
        if (asVar == null) {
            asVar = new LocalVariable(valueType, asName, null);
            frameState.set(asName, asVar);
            prepareBuilder.declarationDefault(valueType, asName);
        } else {
            prepareBuilder.lineComment("already have as" + type + ": " + asVar);
        }
    }

    private CodeTree createIsType(FrameState state, LocalVariable value, String type) {
        String isDefinedKey = value.getName() + "_is" + type + "_defined_";
        if (state.getBoolean(isDefinedKey, false)) {
            return CodeTreeBuilder.singleString(value.getName() + "_is" + type + "_");
        } else {
            state.setBoolean(isDefinedKey, true);
            return CodeTreeBuilder.singleString("(" + value.getName() + "_is" + type + "_ = $frame.is" + type + "($sp - " + getStackOffset(value) + "))");
        }
    }

    private CodeTree createAsType(FrameState state, LocalVariable value, String typeName) {
        if (typeName == null) {
            throw new IllegalArgumentException("typeName");
        }

        String isDefinedKey = value.getName() + "_as" + typeName + "_defined_";
        if (state.getBoolean(isDefinedKey, false)) {
            return CodeTreeBuilder.singleString(value.getName() + "_as" + typeName + "_");
        } else {
            state.setBoolean(isDefinedKey, true);
            return CodeTreeBuilder.singleString("(" + value.getName() + "_as" + typeName + "_ = $frame.get" + typeName + "($sp - " + getStackOffset(value) + "))");
        }
    }

    private CodeTree createAsValue(FrameState state, LocalVariable value, String reason) {
        String isDefinedKey = value.getName() + "_asValue_defined_";
        if (state.getBoolean(isDefinedKey, false)) {
            return CodeTreeBuilder.singleString(value.getName() + "_asValue_");
        } else {
            state.setBoolean(isDefinedKey, true);
            if (LOG_GET_VALUE_CALLS) {
                return CodeTreeBuilder.singleString("interlog(" + value.getName() + "_asValue_ = $frame.getValue($sp - " + getStackOffset(value) + "), \"" + reason + "\")");
            } else {
                return CodeTreeBuilder.singleString("(" + value.getName() + "_asValue_ = $frame.getValue($sp - " + getStackOffset(value) + "))");
            }
        }
    }

    private void createUnboxedCheck(TypeSystemData typeSystem, FrameState frameState, String typeName, TypeMirror targetType, LocalVariable value, CodeTreeBuilder b, CodeTreeBuilder prepareBuilder) {
        b.startParantheses();
        b.tree(createIsType(frameState, value, typeName));
        b.string(" || ");
        if (typeSystem.getCheck(targetType) == null) {
            createPrepareFor("Object", context.getType(Object.class), frameState, value, prepareBuilder);
            b.startParantheses();
            b.tree(createIsType(frameState, value, "Object"));
            b.string(" && ");
            b.tree(createAsType(frameState, value, "Object")).instanceOf(ElementUtils.boxType(context, targetType));
            b.end();
        } else {
            b.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, createAsValue(frameState, value, "boxed value check")));
        }
        b.end();
    }

    private void createUnboxedCast(TypeSystemData typeSystem, FrameState frameState, String typeName, TypeMirror targetType, LocalVariable value, CodeTreeBuilder b) {
        b.tree(createIsType(frameState, value, typeName));
        b.string(" ? ");
        b.tree(createAsType(frameState, value, typeName));
        b.string(" : ");
        if (typeSystem.getCast(targetType) == null) {
            // TODO check individual possible boxed types here
            b.maybeCast(context.getType(Object.class), targetType).tree(createAsType(frameState, value, "Object"));
        } else {
            b.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, createAsValue(frameState, value, "boxed value cast")));
        }
        b.end(2);
    }

    public int getStackOffset(LocalVariable value) {
        if (value.getName().startsWith("arg") && value.getName().endsWith("Value")) {
            return cinstr.numPopStatic() - Integer.parseInt(value.getName().substring(3, value.getName().length() - 5));
        }
        throw new UnsupportedOperationException("" + value);
    }

    private static String getFrameName(TypeKind kind) {
        switch (kind) {
            case INT:
                return "Int";
            case BYTE:
                return "Byte";
            case BOOLEAN:
                return "Boolean";
            case DOUBLE:
                return "Double";
            case FLOAT:
                return "Float";
            case LONG:
                return "Long";
            default:
                // shorts and chars are handled as reference types, since VirtualFrame does not
                // support them directly
                throw new IllegalArgumentException("Unknown primitive type: " + kind);
        }
    }

    @Override
    public boolean createCheckCast(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, CodeTreeBuilder prepareBuilder,
                    CodeTreeBuilder checkBuilder, CodeTreeBuilder castBuilder, boolean castOnly) {
        if (isVariadic) {
            return false;
        }
        createPrepareFor(null, context.getType(Object.class), frameState, value, prepareBuilder);
        switch (targetType.getKind()) {
            case BYTE:
            case LONG:
            case INT:
            case BOOLEAN:
            case FLOAT:
            case DOUBLE: {
                String typeName = getFrameName(targetType.getKind());
                if (!castOnly) {
                    createPrepareFor(typeName, targetType, frameState, value, prepareBuilder);
                    createUnboxedCheck(typeSystem, frameState, typeName, targetType, value, checkBuilder, prepareBuilder);
                } else {
                    createUnboxedCast(typeSystem, frameState, typeName, targetType, value, castBuilder);
                }
                break;
            }
            default:
                // shorts and chars are handled as reference types, since VirtualFrame does not
                // support them directly
                if (ElementUtils.isObject(targetType)) {
                    if (!castOnly) {
                        checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, createAsValue(frameState, value, "object check")));
                    } else {
                        castBuilder.tree(createAsValue(frameState, value, "object cast"));
                    }
                } else {
                    boolean hasCheck = typeSystem.getCheck(targetType) != null;
                    if (!castOnly) {
                        createPrepareFor("Object", context.getType(Object.class), frameState, value, prepareBuilder);

                        checkBuilder.startParantheses();
                        checkBuilder.tree(createIsType(frameState, value, "Object"));
                        checkBuilder.string(" && ");
                        checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, createAsType(frameState, value, "Object")));
                        checkBuilder.end();
                        if (hasCheck) {
                            // TODO: this should do primitive checks itself, w/o resorting to
                            // getValue
                            checkBuilder.string(" || ");
                            checkBuilder.tree(TypeSystemCodeGenerator.check(typeSystem, targetType, createAsValue(frameState, value, "type system check")));
                        }
                    } else {
                        if (hasCheck) {
                            castBuilder.tree(createIsType(frameState, value, "Object"));
                            castBuilder.string(" ? ");
                            castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, createAsType(frameState, value, "Object")));
                            castBuilder.string(" : ");
                            castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, createAsValue(frameState, value, "type system cast")));
                        } else {
                            castBuilder.tree(TypeSystemCodeGenerator.cast(typeSystem, targetType, createAsType(frameState, value, "Object")));
                        }
                    }
                }
                break;
        }

        return true;
    }

    @Override
    public boolean createImplicitCheckCast(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, CodeTree implicitState,
                    CodeTreeBuilder prepareBuilder, CodeTreeBuilder checkBuilder, CodeTreeBuilder castBuilder, boolean castOnly) {
        if (isVariadic) {
            return false;
        }

        if (!castOnly) {
            createPrepareFor(null, context.getType(Object.class), frameState, value, prepareBuilder);
            checkBuilder.tree(TypeSystemCodeGenerator.implicitCheckFlat(typeSystem, targetType, createAsValue(frameState, value, "implicit check"), implicitState));
        } else {
            castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(typeSystem, targetType, createAsValue(frameState, value, "implicit cast"), implicitState));
        }

        return true;
    }

    @Override
    public boolean createImplicitCheckCastSlowPath(TypeSystemData typeSystem, FrameState frameState, TypeMirror targetType, LocalVariable value, String implicitStateName,
                    CodeTreeBuilder prepareBuilder, CodeTreeBuilder checkBuilder, CodeTreeBuilder castBuilder, boolean castOnly) {
        if (isVariadic) {
            return false;
        }

        if (!castOnly) {
            createPrepareFor(null, context.getType(Object.class), frameState, value, prepareBuilder);

            checkBuilder.startParantheses();
            checkBuilder.string(implicitStateName, " = ");
            checkBuilder.tree(TypeSystemCodeGenerator.implicitSpecializeFlat(typeSystem, targetType, createAsValue(frameState, value, "implicit specialize")));
            checkBuilder.end().string(" != 0");
        } else {
            castBuilder.tree(TypeSystemCodeGenerator.implicitCastFlat(
                            typeSystem, targetType,
                            createAsValue(frameState, value, "implicit specialize cast"),
                            CodeTreeBuilder.singleString(implicitStateName)));
        }

        return true;
    }

    public boolean createSameTypeCast(FrameState frameState, LocalVariable value, TypeMirror genericTargetType, CodeTreeBuilder prepareBuilder, CodeTreeBuilder castBuilder, boolean castOnly) {
        if (isVariadic)
            return false;

        assert ElementUtils.isObject(genericTargetType);

        if (!castOnly) {
            prepareBuilder.lineComment("same type: " + value);
            createPrepareFor(null, genericTargetType, frameState, value, prepareBuilder);
        } else {
            castBuilder.tree(createAsValue(frameState, value, "cast to object: " + cinstr.name));
        }
        return true;
    }

    public CodeTree[] createThrowUnsupportedValues(FrameState frameState, List<CodeTree> values, CodeTreeBuilder parent, CodeTreeBuilder builder) {
        if (isVariadic) {
            return values.toArray(new CodeTree[values.size()]);
        }
        CodeTree[] result = new CodeTree[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = CodeTreeBuilder.singleString("$frame.getValue($sp - " + (cinstr.numPopStatic() - i) + ")");
        }

        return result;
    }

    public void initializeFrameState(FrameState frameState, CodeTreeBuilder builder) {
        frameState.set("frameValue", new LocalVariable(types.VirtualFrame, "$frame", null));
    }

    private void createPushResult(CodeTreeBuilder b, CodeTree specializationCall, TypeMirror retType) {
        if (cinstr.numPush() == 0) {
            b.statement(specializationCall);
            b.returnStatement();
            return;
        }

        assert cinstr.numPush() == 1;

        int destOffset = cinstr.numPopStatic();

        CodeTree value;
        String typeName;
        if (retType.getKind() == TypeKind.VOID) {
            // we need to push something, lets just push a `null`.
            // maybe this should be an error? DSL just returns default value

            b.statement(specializationCall);
            value = CodeTreeBuilder.singleString("null");
            typeName = "Object";
        } else if (retType.getKind().isPrimitive()) {
            value = specializationCall;
            typeName = getFrameName(retType.getKind());
        } else {
            value = specializationCall;
            typeName = "Object";
        }

        if (OperationsBytecodeCodeGenerator.DO_STACK_LOGGING) {
            b.startBlock();
            b.declaration(retType, "__value__", value);
            b.statement("System.out.printf(\" pushing " + typeName + " at -" + destOffset + ": %s%n\", __value__)");
        }

        b.startStatement();
        b.startCall("$frame", "set" + typeName);
        b.string("$sp - " + destOffset);
        if (OperationsBytecodeCodeGenerator.DO_STACK_LOGGING) {
            b.string("__value__");
        } else {
            b.tree(value);
        }
        b.end(2);

        b.returnStatement();

        if (OperationsBytecodeCodeGenerator.DO_STACK_LOGGING) {
            b.end();
        }
    }

    public boolean createCallSpecialization(SpecializationData specialization, CodeTree specializationCall, CodeTreeBuilder b, boolean inBoundary) {
        if (isVariadic || inBoundary)
            return false;

        createPushResult(b, specializationCall, specialization.getMethod().getReturnType());
        return true;
    }

    public boolean createCallExecuteAndSpecialize(CodeTreeBuilder builder, CodeTree call) {
        if (isVariadic) {
            return false;
        }
        builder.statement(call);
        builder.returnStatement();
        return true;
    }

    public void createCallBoundaryMethod(CodeTreeBuilder builder, FrameState frameState, CodeExecutableElement boundaryMethod, Consumer<CodeTreeBuilder> addArguments) {
        if (isVariadic) {
            builder.startReturn().startCall("this", boundaryMethod);
            builder.string("$bci");
            builder.string("$sp");
            addArguments.accept(builder);
            builder.end(2);
            return;
        }

        CodeTreeBuilder callBuilder = builder.create();

        callBuilder.startCall("this", boundaryMethod);
        callBuilder.string("$bci");
        callBuilder.string("$sp");
        addArguments.accept(callBuilder);
        callBuilder.end();

        createPushResult(builder, callBuilder.build(), boundaryMethod.getReturnType());
    }
}