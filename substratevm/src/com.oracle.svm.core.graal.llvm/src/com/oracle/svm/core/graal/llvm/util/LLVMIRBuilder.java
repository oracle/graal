/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.ENUM_ATTRIBUTE_VALUE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.FALSE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.TRUE;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpTypes;
import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.dumpValues;

import static org.graalvm.compiler.debug.GraalError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import com.oracle.svm.core.graal.llvm.LLVMGenerator;
import com.oracle.svm.hosted.image.LLVMDebugInfoProvider;
import com.oracle.svm.hosted.image.DebugInfoProviderHelper;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedPrimitiveType;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMDIBuilderRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMMetadataRef;

import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.BytePointer;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.Pointer;
import com.oracle.svm.shadowed.org.bytedeco.javacpp.PointerPointer;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMAttributeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBasicBlockRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMBuilderRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMContextRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMMemoryBufferRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMModuleRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMTypeRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;


public class LLVMIRBuilder implements AutoCloseable {
    private static final String DEFAULT_INSTR_NAME = "";
    private static final byte DW_ATE_float = 0x4;

    private LLVMContextRef context;
    public LLVMBuilderRef builder;
    private LLVMModuleRef module;
    private LLVMValueRef function;
    public LLVMDIBuilderRef diBuilder;

    // Maps filenames to a compile unit
    public HashMap<String, LLVMMetadataRef> diFilenameToCU;
    // Maps filenames to a corresponding diFile
    public HashMap<String, LLVMMetadataRef> diFilenameToDIFile;
    // Maps function names to a LLVM Debug Info SubPrograms
    public HashMap<String, LLVMMetadataRef> diFunctionToSP;
    // Maps type names to a corresponding debug info type
    public HashMap<String, LLVMMetadataRef> diTypeNameToDIType;
    private final boolean primary;
    private final LLVMHelperFunctions helpers;
    private LLVMDebugInfoProvider dbgInfoProvider;

    // The subprogram associated with the main function
    private LLVMMetadataRef diSubProgram = null;
    private LLVMDebugInfoProvider.LLVMLocationInfo methodStartLocation = null;

    // The debug info file descriptor for the file containing the main function
    private LLVMMetadataRef diMainFile;
    private StructuredGraph graph;
    private ResolvedJavaMethod mainMethod;

    // Helper Objects for debug info generation

    // The information about the local variables in each block and their corresponding instructions
    // is stored in this linked list while building the instructions of the block, once all the instructions in the block are generated
    // then the debug info for the local variables in the block is declared by iterating over this linked list.
    public LinkedList<DILocalVarInfo> localVarListPerBlock;

    // When generating types recursively, there are cases when the generation leads to 
    // an infinite loop, for example: class java/lang/ThreadGroup has a member of class java/lang/Thread type and 
    // class java/lang/Thread has a member of class java/lang/ThreadGroup. This causes an infinite loop when 
    // creating types leading to a stack overflow. This global array is used to catch such infinite loops
    private ArrayList<String> typesVisitedRecursively;
    private DebugInfoProvider.DebugFieldInfo debugFieldInfo;

    // The debug information regarding the local variables present in each block. First the information for all the local
    // variables is collected and then the variables are declared at then end of the block.
    class DILocalVarInfo {
        public LLVMValueRef instr;
        public LLVMMetadataRef diLocalVariable;
        public LLVMMetadataRef diLocation;
        DILocalVarInfo(LLVMValueRef instrArg, LLVMMetadataRef diLocalVariableArg, LLVMMetadataRef diLocationArg) {
            this.instr = instrArg;
            this.diLocalVariable = diLocalVariableArg;
            this.diLocation = diLocationArg;
        }
    }
    public LLVMIRBuilder(String name) {
        this.context = LLVM.LLVMContextCreate();
        this.builder = LLVM.LLVMCreateBuilderInContext(context);
        this.module = LLVM.LLVMModuleCreateWithNameInContext(name, context);
        if (SubstrateOptions.GenerateDebugInfo.getValue() > 0) {
            String dbgInfoFlagName = "Debug Info Version";
            String dwarfVersion = "Dwarf Version";
            LLVMValueRef dwarfVersionVal = LLVM.LLVMConstInt(LLVM.LLVMInt32TypeInContext(context), 4, 0);
            LLVMValueRef dbgInfoVal = LLVM.LLVMConstInt(LLVM.LLVMInt32TypeInContext(context), LLVM.LLVMDebugMetadataVersion(), 0);
            LLVM.LLVMAddModuleFlag(this.module, LLVM.LLVMModuleFlagBehaviorWarning, dwarfVersion,
                    dwarfVersion.length(), LLVM.LLVMValueAsMetadata(dwarfVersionVal));
            LLVM.LLVMAddModuleFlag(this.module, LLVM.LLVMModuleFlagBehaviorWarning, dbgInfoFlagName,
                    dbgInfoFlagName.length(), LLVM.LLVMValueAsMetadata(dbgInfoVal));
            diBuilder = LLVM.LLVMCreateDIBuilder(this.module);
            diFilenameToCU = new HashMap<String, LLVMMetadataRef>();
            diFunctionToSP = new HashMap<String, LLVMMetadataRef>();
            diFilenameToDIFile = new HashMap<String, LLVMMetadataRef>();
            diTypeNameToDIType = new HashMap<String, LLVMMetadataRef>();
            dbgInfoProvider = new LLVMDebugInfoProvider();
            typesVisitedRecursively = new ArrayList<String>();
            localVarListPerBlock = new LinkedList<DILocalVarInfo>();
        }
        this.primary = true;
        this.helpers = new LLVMHelperFunctions(this);
    }

    public LLVMIRBuilder(LLVMIRBuilder primary) {
        this.context = primary.context;
        this.builder = LLVM.LLVMCreateBuilderInContext(context);
        this.module = primary.module;
        this.function = null;
        this.primary = false;
        this.helpers = null;
    }

    // The current field type should not have been already visited. If the current field type is an array, then its
    // base type should not have been already visited.
    private boolean typeCyclePresent(ResolvedJavaField field) {
        HostedType checkType = (HostedType) field.getType();
        if (checkType.isArray()) {
            checkType = checkType.getBaseType();
        }
        if (checkType.isInterface() || checkType.isInstanceClass()) {
            if (typesVisitedRecursively.contains(checkType.getName())) {
                return true;
            }
        }
        return false;
    }

    // Create Debug information for a class type and its members.
    private LLVMMetadataRef createClassDebugTypeInfo(HostedType hostedType, LLVMMetadataRef diScope,
                                                LLVMMetadataRef diSourceFile) {
        LLVMDebugInfoProvider.LLVMDebugInstanceTypeInfo instanceTypeInfo =
                dbgInfoProvider.new LLVMDebugInstanceTypeInfo(hostedType);
        // type corresponding to the base class
        LLVMMetadataRef baseClass = null;
        if ((instanceTypeInfo.superClass() != null)) {
            baseClass = getDiType(instanceTypeInfo.superClass().getName());
        }
        ArrayList<LLVMMetadataRef> memberList = new ArrayList<LLVMMetadataRef>();

        instanceTypeInfo.fieldInfoProvider().forEach(debugFieldInfo -> {
            LLVMDebugInfoProvider.LLVMDebugFieldInfo llvmFieldInfo = (LLVMDebugInfoProvider.LLVMDebugFieldInfo) debugFieldInfo;
            // Check if generation of the current field causes a cycle, For example: class A is member of class B, but
            // class A also has a member of class B E.g. ThreadGroup inside java/lang/ThreadGroup
            if (!typeCyclePresent(llvmFieldInfo.getField())) {
                memberList.add(LLVM.LLVMDIBuilderCreateMemberType(diBuilder, diScope, llvmFieldInfo.name(), llvmFieldInfo.name().length(),
                    diSourceFile, 0, llvmFieldInfo.size() * 8, 0, llvmFieldInfo.offset(), 0,
                    getDiType(llvmFieldInfo.getField().getType().getName())));
            } else {
                // TODO: How to deal with this case? LLVM DIBuilder does not have an option to create a class pointer
            }
        });

        LLVMMetadataRef[] members = new LLVMMetadataRef[memberList.size()];
        PointerPointer<LLVMMetadataRef> memberArray = new PointerPointer<>(memberList.toArray(members));
        LLVMMetadataRef classType = LLVM.LLVMDIBuilderCreateClassType(diBuilder, diScope, instanceTypeInfo.typeName(),
                instanceTypeInfo.typeName().length(), diSourceFile, 0, instanceTypeInfo.size() * 8,
        0, 0, 0, baseClass, memberArray, memberList.size(), null,
                null, "", 0);
        return classType;
    }

    // Create debug information for an enumeration type
    private LLVMMetadataRef createEnumDebugTypeinfo(HostedType hostedType, LLVMMetadataRef diScope,
                                                    LLVMMetadataRef diSourceFile) {
        // Treating the base type of enum to be an integer value
        // the typeChar of the hostedType for int is "I" in the heap
        LLVMMetadataRef diBaseType = getDiType("I");
        LLVMDebugInfoProvider.LLVMDebugEnumTypeInfo enumTypeInfo =
                dbgInfoProvider.new LLVMDebugEnumTypeInfo((HostedInstanceClass) hostedType);

        ArrayList<LLVMMetadataRef> enumList = new ArrayList<LLVMMetadataRef>();
        if (enumTypeInfo.staticFieldInfoProvider() != null) {
            enumTypeInfo.staticFieldInfoProvider().forEach(debugFieldInfo -> {
                // TODO: How to get the value for an enum, is it using readStorageValue? Not sure how I am supposed to use it
                int enumValue = 0;
                LLVMDebugInfoProvider.LLVMDebugFieldInfo llvmFieldInfo = (LLVMDebugInfoProvider.LLVMDebugFieldInfo) debugFieldInfo;
                if (llvmFieldInfo.isEnumerator()) {
                    enumList.add(LLVM.LLVMDIBuilderCreateEnumerator(diBuilder, llvmFieldInfo.name(), llvmFieldInfo.name().length(),
                            enumValue, 1));
                }
            });
        }

        LLVMMetadataRef[] enums = new LLVMMetadataRef[enumList.size()];
        PointerPointer<LLVMMetadataRef> enumArray = new PointerPointer<LLVMMetadataRef>(enumList.toArray(enums));
        LLVMMetadataRef enumType = LLVM.LLVMDIBuilderCreateEnumerationType(
                diBuilder, diScope, enumTypeInfo.typeName(),
                enumTypeInfo.typeName().length(), diSourceFile, 0, enumTypeInfo.size() * 8,
                0, enumArray, enumList.size(), diBaseType);
        return enumType;

    }

    // Create DebugType info for the hosted type
    public void createDebugTypeInfo(HostedType hostedType) {

        // Create diFile and compile unit information required common for all the types
        LLVMDebugInfoProvider.LLVMDebugTypeInfo dbgTypeInfo =
            dbgInfoProvider.new LLVMDebugTypeInfo(hostedType);
        String filename = dbgTypeInfo.fileName();

        LLVMMetadataRef diSourceFile = null;
        LLVMMetadataRef diCompileUnit = null;
        if (!filename.equals("")) {
            String directory = dbgTypeInfo.filePath().toString();
            diSourceFile = getDiFile(filename, directory);
            diCompileUnit = getCompileUnit(filename, diSourceFile);
        }

        if (hostedType.isEnum()) {
            LLVMMetadataRef enumType = createEnumDebugTypeinfo(hostedType, diCompileUnit, diSourceFile);
            diTypeNameToDIType.put(hostedType.getName(), enumType);

        // LLVM DIBuilder doesn't provide a special method to generate an interface type, so interfaces are being treated the same
        // as classes
        } else if (hostedType.isInstanceClass() || hostedType.isInterface()) {
            // Add the type that has been visited
            typesVisitedRecursively.add(hostedType.getName());
            LLVMMetadataRef classType = createClassDebugTypeInfo(hostedType, diCompileUnit, diSourceFile);

            // Remove it from the list once the type is successfully created
            typesVisitedRecursively.remove(hostedType.getName());
            diTypeNameToDIType.put(hostedType.getName(), classType);

        } else if (hostedType.isArray()) {
            LLVMDebugInfoProvider.LLVMDebugArrayTypeInfo arrayTypeInfo =
                    dbgInfoProvider.new LLVMDebugArrayTypeInfo((HostedArrayClass) hostedType);
            // The subscipts correspond to the depth of the array and the number of elements in each suscript.
            LLVMMetadataRef[] subscripts = new LLVMMetadataRef[arrayTypeInfo.arrayDimension()];
            for (int i = 0; i < arrayTypeInfo.arrayDimension(); i++) {
                // TODO: Get the correct number of elements
                int numElements = 5;
                subscripts[i] = LLVM.LLVMDIBuilderGetOrCreateSubrange(diBuilder, 0, numElements);
            }
            PointerPointer<LLVMMetadataRef> subscriptPointer = new PointerPointer<LLVMMetadataRef>(subscripts);
            LLVMMetadataRef diBaseType = getDiType(arrayTypeInfo.baseType().getName());
            LLVMMetadataRef arrayType = LLVM.LLVMDIBuilderCreateArrayType(diBuilder, arrayTypeInfo.size() * 8,
                    0, diBaseType, subscriptPointer, 1);
            diTypeNameToDIType.put(hostedType.getName(), arrayType);

        } else if (hostedType.isPrimitive()) {
            LLVMDebugInfoProvider.LLVMDebugPrimitiveTypeInfo primitiveTypeInfo =
                    dbgInfoProvider.new LLVMDebugPrimitiveTypeInfo((HostedPrimitiveType) hostedType);
            LLVMMetadataRef primitiveDIType = LLVM.LLVMDIBuilderCreateBasicType(this.diBuilder,
                    primitiveTypeInfo.typeName(), primitiveTypeInfo.typeName().length(),
                    primitiveTypeInfo.bitCount(), primitiveTypeInfo.computeEncoding(), primitiveTypeInfo.flags());
            diTypeNameToDIType.put(hostedType.getName(), primitiveDIType);

        } else {
            throw new RuntimeException("Unknown type kind " + hostedType.getName());
        }
    }

    // This function gets called recursively when a type inside another type
    // has not been generated yet, e.g. member type of a class
    public LLVMMetadataRef getDiType(String typeName) {
        LLVMMetadataRef returnType;
        // If a LLVM Debug info type has already been generated for this type.
        if (diTypeNameToDIType.containsKey(typeName)) {
            returnType = diTypeNameToDIType.get(typeName);

        // If the NativeImageHeap contains a type then generate a debug info type
        } else if (LLVMDebugInfoProvider.typeMap.containsKey(typeName)) {
            createDebugTypeInfo(LLVMDebugInfoProvider.typeMap.get(typeName));
            returnType = diTypeNameToDIType.get(typeName);

        //TODO: What to do when the type is not present in the heap? E.g. DynamicHub
        } else {
            returnType = createPlaceholderDiType(typeName);
        }
        return returnType;
    }

    // Creates a placeholder Debug Info type
    public LLVMMetadataRef createPlaceholderDiType(String typeName) {
       LLVMMetadataRef diType = LLVM.LLVMDIBuilderCreateBasicType(this.diBuilder, typeName, typeName.length(),
                    32, DW_ATE_float, 0);
       diTypeNameToDIType.put(typeName, diType);
       return diType;
    }

    public void setMainFunction(String functionName, LLVMTypeRef functionType) {
        assert function == null;
        this.function = addFunction(functionName, functionType);
    }

    public void setMainMethod(ResolvedJavaMethod method) {
        this.mainMethod = method;
    }

    public void setGraph(StructuredGraph graph) {
        this.graph = graph;
    }

    // Get or create a debug info descriptor for a file if not already present
    public LLVMMetadataRef getDiFile(String filename, String directory) {
        if (diFilenameToDIFile.containsKey(filename + directory)) {
           return diFilenameToDIFile.get(filename + directory);
        } else {
            LLVMMetadataRef diFile = LLVM.LLVMDIBuilderCreateFile(diBuilder, filename, filename.length(), directory,
                    directory.length());
            diFilenameToDIFile.put(filename + directory, diFile);
            return diFile;
        }
    }

    // Create a subprogram for the main function if file information is present for the main function
    public void setDISubProgram() {
        DebugContext debugContext = graph.getDebug();
        dbgInfoProvider.debugContext = debugContext;
        LLVMDebugInfoProvider.LLVMLocationInfo dbgLocInfo =
                dbgInfoProvider.new LLVMLocationInfo(this.mainMethod, 0, debugContext);
        // The llvm-link verifier doesn't allow null filenames for the subprogram
        if ((dbgLocInfo.fileName() != null) && (!dbgLocInfo.fileName().equals(""))) {
            this.methodStartLocation = dbgLocInfo;
            String mainFilename = dbgLocInfo.fileName();
            String mainDirectory = String.valueOf(dbgLocInfo.filePath());
            this.diMainFile = getDiFile(mainFilename, mainDirectory);
            getCompileUnit(mainFilename, this.diMainFile);
            this.diSubProgram = getSubProgram(this.diMainFile, this.mainMethod);
            LLVM.LLVMSetSubprogram(this.function, this.diSubProgram);
            return;
        }
        // Ignoring creating subPrograms for functions with no filenames
    }

    // Create debug info declaration for the function parameters of the main method
    public void createDIFunctionParameters() {
        if (this.diSubProgram == null) {
            return;
        }

        int offset = (LLVMGenerator.isEntryPoint(this.mainMethod) ? 0 : LLVMGenerator.SpecialRegister.count());
        int paramIndex = offset;

        ResolvedJavaMethod.Parameter[] params;
        // NonBytecodeStaticMethod does not have getParameters() implemented so the unimplemented() exception needs to
        // be caught.
        try {
            params = this.mainMethod.getParameters();
            if (params == null) {
                return;
            }
        } catch (UnsupportedOperationException e) {
            return;
        }
        // TODO: For non static methods, should I create a parameter called "this" as well?
        // Iterate over all the function parameters
        for (ResolvedJavaMethod.Parameter param : params) {
            String paramName = param.getName();
            LLVMMetadataRef paramDIType;
            paramDIType = getDiType(param.getType().getName());

            LLVMValueRef llvmParam = getFunctionParam(paramIndex);
            LLVMValueRef paramAlloca = createEntryBlockAlloca(typeOf(llvmParam), paramName);

            // (paramIndex + 1) because the llvm documentation states that index starts at 1.
            LLVMMetadataRef diParamVariable = LLVM.LLVMDIBuilderCreateParameterVariable(diBuilder, this.diSubProgram, paramName,
                    paramName.length(), paramIndex + 1, this.diMainFile, 0, paramDIType, 0, 0);
            LLVMMetadataRef debugLoc = LLVM.LLVMDIBuilderCreateDebugLocation(context, this.methodStartLocation.line(), 0,
                    this.diSubProgram, null);

            long[] nullopt = {};
            LLVMMetadataRef expr = LLVM.LLVMDIBuilderCreateExpression(diBuilder, nullopt, nullopt.length);

            LLVM.LLVMDIBuilderInsertDeclareAtEnd(diBuilder, paramAlloca, diParamVariable, expr, debugLoc, LLVM.LLVMGetInsertBlock(builder));
            paramIndex++;
        }
    }

    // Before the debug info for function parameters is declared, they need have corresponding allocas created.
    public LLVMValueRef createEntryBlockAlloca(LLVMTypeRef allocaType, String allocaName) {
        LLVMBasicBlockRef startBlock = LLVM.LLVMGetFirstBasicBlock(this.function);
        LLVMValueRef firstInst = LLVM.LLVMGetFirstInstruction(startBlock);
        LLVM.LLVMPositionBuilderBefore(builder, firstInst);
        return LLVM.LLVMBuildAlloca(builder, allocaType, allocaName);
    }
    /* Module */

    public byte[] getBitcode() {
        LLVMMemoryBufferRef buffer = LLVM.LLVMWriteBitcodeToMemoryBuffer(module);
        BytePointer start = LLVM.LLVMGetBufferStart(buffer);
        int size = NumUtil.safeToInt(LLVM.LLVMGetBufferSize(buffer));

        byte[] bitcode = new byte[size];
        start.get(bitcode, 0, size);
        LLVM.LLVMDisposeMemoryBuffer(buffer);
        return bitcode;
    }

    public boolean verifyBitcode() {
        if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, new BytePointer((Pointer) null)) == TRUE) {
            LLVM.LLVMDumpModule(module);
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        LLVM.LLVMDisposeBuilder(builder);
        builder = null;
        if (primary) {
            if (SubstrateOptions.GenerateDebugInfo.getValue() > 0) {
                LLVM.LLVMDIBuilderFinalize(diBuilder);
                diFilenameToCU.clear();
                diFunctionToSP.clear();
                diTypeNameToDIType.clear();
                diFilenameToDIFile.clear();
                typesVisitedRecursively.clear();
                diBuilder = null;
            }
            LLVM.LLVMDisposeModule(module);
            module = null;
            LLVM.LLVMContextDispose(context);
            context = null;
        }
    }

    /* Functions */

    public LLVMValueRef addFunction(String name, LLVMTypeRef type) {
        return LLVM.LLVMAddFunction(module, name, type);
    }

    public LLVMValueRef getFunction(String name, LLVMTypeRef type) {
        LLVMValueRef func = getNamedFunction(name);
        if (func == null) {
            func = addFunction(name, type);
            setLinkage(func, LinkageType.External);
        }
        return func;
    }

    public LLVMValueRef getNamedFunction(String name) {
        return LLVM.LLVMGetNamedFunction(module, name);
    }

    public void addAlias(String alias) {
        LLVM.LLVMAddAlias(module, LLVM.LLVMTypeOf(function), function, alias);
    }

    public void setFunctionLinkage(LinkageType linkage) {
        setLinkage(function, linkage);
    }

    public static void setLinkage(LLVMValueRef global, LinkageType linkage) {
        LLVM.LLVMSetLinkage(global, linkage.code);
    }

    public enum LinkageType {
        External(LLVM.LLVMExternalLinkage),
        LinkOnce(LLVM.LLVMLinkOnceAnyLinkage),
        LinkOnceODR(LLVM.LLVMLinkOnceODRLinkage);

        private final int code;

        LinkageType(int code) {
            this.code = code;
        }
    }

    public void setFunctionAttribute(Attribute attribute) {
        setFunctionAttribute(function, attribute);
    }

    public void setFunctionAttribute(LLVMValueRef func, Attribute attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute.name, attribute.name.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, ENUM_ATTRIBUTE_VALUE);
        } else {
            String value = "true";
            attr = LLVM.LLVMCreateStringAttribute(context, attribute.name, attribute.name.length(), value, value.length());
        }
        LLVM.LLVMAddAttributeAtIndex(func, LLVM.LLVMAttributeFunctionIndex, attr);
    }

    public enum Attribute {
        AlwaysInline("alwaysinline"),
        GCLeafFunction("gc-leaf-function"),
        Naked("naked"),
        NoInline("noinline"),
        NoRealignStack("no-realign-stack"),
        NoRedZone("noredzone"),
        StatepointID("statepoint-id");

        private final String name;

        Attribute(String name) {
            this.name = name;
        }
    }

    public void setPersonalityFunction(LLVMValueRef personality) {
        LLVM.LLVMSetPersonalityFn(function, personality);
    }

    public void setGarbageCollector(GCStrategy gc) {
        setGarbageCollector(function, gc);
    }

    public void setGarbageCollector(LLVMValueRef func, GCStrategy gc) {
        LLVM.LLVMSetGC(func, gc.name);
    }

    public enum GCStrategy {
        CompressedPointers("compressed-pointer");

        private final String name;

        GCStrategy(String name) {
            this.name = name;
        }
    }

    /* Basic blocks */

    public LLVMBasicBlockRef appendBasicBlock(String name) {
        return appendBasicBlock(function, name);
    }

    public LLVMBasicBlockRef appendBasicBlock(LLVMValueRef func, String name) {
        return LLVM.LLVMAppendBasicBlockInContext(context, func, name);
    }

    public void positionAtStart() {
        LLVMBasicBlockRef firstBlock = LLVM.LLVMGetFirstBasicBlock(function);
        LLVMValueRef firstInstruction = LLVM.LLVMGetFirstInstruction(firstBlock);
        if (firstInstruction != null) {
            LLVM.LLVMPositionBuilderBefore(builder, firstInstruction);
        }
    }

    public void positionAtEnd(LLVMBasicBlockRef block) {
        LLVM.LLVMPositionBuilderAtEnd(builder, block);
    }

    public void positionBeforeTerminator(LLVMBasicBlockRef block) {
        LLVM.LLVMPositionBuilderBefore(builder, blockTerminator(block));
    }

    public LLVMValueRef blockTerminator(LLVMBasicBlockRef block) {
        return LLVM.LLVMGetBasicBlockTerminator(block);
    }

    /* Types */

    public static LLVMTypeRef typeOf(LLVMValueRef value) {
        return LLVM.LLVMTypeOf(value);
    }

    public LLVMTypeRef booleanType() {
        return integerType(1);
    }

    public static boolean isBooleanType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == 1;
    }

    public LLVMTypeRef byteType() {
        return integerType(Byte.SIZE);
    }

    public static boolean isByteType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Byte.SIZE;
    }

    public LLVMTypeRef shortType() {
        return integerType(Short.SIZE);
    }

    public static boolean isShortType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Short.SIZE;
    }

    public LLVMTypeRef charType() {
        return integerType(Character.SIZE);
    }

    public static boolean isCharType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Character.SIZE;
    }

    public LLVMTypeRef intType() {
        return integerType(Integer.SIZE);
    }

    public static boolean isIntType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Integer.SIZE;
    }

    public LLVMTypeRef longType() {
        return integerType(Long.SIZE);
    }

    public static boolean isLongType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == Long.SIZE;
    }

    LLVMTypeRef integerType(int bits) {
        return LLVM.LLVMIntTypeInContext(context, bits);
    }

    public static boolean isIntegerType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMIntegerTypeKind;
    }

    public static boolean isVectorType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMVectorTypeKind;
    }

    public static int integerTypeWidth(LLVMTypeRef intType) {
        return LLVM.LLVMGetIntTypeWidth(intType);
    }

    public LLVMTypeRef wordType() {
        return integerType(FrameAccess.wordSize() * Byte.SIZE);
    }

    public static boolean isWordType(LLVMTypeRef type) {
        return isIntegerType(type) && integerTypeWidth(type) == FrameAccess.wordSize() * Byte.SIZE;
    }

    public LLVMTypeRef floatType() {
        return LLVM.LLVMFloatTypeInContext(context);
    }

    public static boolean isFloatType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMFloatTypeKind;
    }

    public LLVMTypeRef doubleType() {
        return LLVM.LLVMDoubleTypeInContext(context);
    }

    public static boolean isDoubleType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMDoubleTypeKind;
    }

    /*
     * Pointer types can be of two types: references and regular pointers. References are pointers
     * to Java objects which are tracked by the GC statepoint emission pass to create reference maps
     * at call sites. Regular pointers are not tracked and represent non-java pointers. They are
     * distinguished by the pointer address space they live in (1, resp. 0).
     */
    private static final int UNTRACKED_POINTER_ADDRESS_SPACE = 0;
    private static final int TRACKED_POINTER_ADDRESS_SPACE = 1;
    private static final int COMPRESSED_POINTER_ADDRESS_SPACE = 2;

    private static int pointerAddressSpace(boolean tracked, boolean compressed) {
        assert tracked || !compressed;
        return tracked ? (compressed ? COMPRESSED_POINTER_ADDRESS_SPACE : TRACKED_POINTER_ADDRESS_SPACE) : UNTRACKED_POINTER_ADDRESS_SPACE;
    }

    public LLVMTypeRef objectType(boolean compressed) {
        return pointerType(byteType(), true, compressed);
    }

    public LLVMTypeRef rawPointerType() {
        return pointerType(byteType());
    }

    public LLVMTypeRef pointerType(LLVMTypeRef type) {
        return pointerType(type, false, false);
    }

    public LLVMTypeRef pointerType(LLVMTypeRef type, boolean tracked, boolean compressed) {
        return LLVM.LLVMPointerType(type, pointerAddressSpace(tracked, compressed));
    }

    public static LLVMTypeRef getElementType(LLVMTypeRef pointerType) {
        return LLVM.LLVMGetElementType(pointerType);
    }

    public static boolean isObjectType(LLVMTypeRef type) {
        return isPointerType(type) && isTrackedPointerType(type);
    }

    static boolean isPointerType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMPointerTypeKind;
    }

    static boolean isTrackedPointerType(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        return addressSpace == TRACKED_POINTER_ADDRESS_SPACE || addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    public static boolean isCompressedPointerType(LLVMTypeRef pointerType) {
        int addressSpace = LLVM.LLVMGetPointerAddressSpace(pointerType);
        assert addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE || addressSpace == TRACKED_POINTER_ADDRESS_SPACE;
        return addressSpace == COMPRESSED_POINTER_ADDRESS_SPACE;
    }

    public LLVMTypeRef arrayType(LLVMTypeRef type, int length) {
        return LLVM.LLVMArrayType(type, length);
    }

    public LLVMTypeRef structType(LLVMTypeRef... types) {
        return LLVM.LLVMStructTypeInContext(context, new PointerPointer<>(types), types.length, FALSE);
    }

    public static int countElementTypes(LLVMTypeRef structType) {
        return LLVM.LLVMCountStructElementTypes(structType);
    }

    private static LLVMTypeRef getTypeAtIndex(LLVMTypeRef structType, int index) {
        return LLVM.LLVMStructGetTypeAtIndex(structType, index);
    }

    private static LLVMTypeRef[] getElementTypes(LLVMTypeRef structType) {
        LLVMTypeRef[] types = new LLVMTypeRef[countElementTypes(structType)];
        for (int i = 0; i < types.length; ++i) {
            types[i] = getTypeAtIndex(structType, i);
        }
        return types;
    }

    public LLVMTypeRef vectorType(LLVMTypeRef type, int count) {
        return LLVM.LLVMVectorType(type, count);
    }

    public LLVMTypeRef functionType(LLVMTypeRef returnType, LLVMTypeRef... argTypes) {
        return functionType(returnType, false, argTypes);
    }

    public LLVMTypeRef functionType(LLVMTypeRef returnType, boolean varargs, LLVMTypeRef... argTypes) {
        return LLVM.LLVMFunctionType(returnType, new PointerPointer<>(argTypes), argTypes.length, varargs ? TRUE : FALSE);
    }

    public LLVMTypeRef functionPointerType(LLVMTypeRef returnType, LLVMTypeRef... argTypes) {
        return pointerType(functionType(returnType, argTypes), false, false);
    }

    public static LLVMTypeRef getReturnType(LLVMTypeRef functionType) {
        return LLVM.LLVMGetReturnType(functionType);
    }

    public static LLVMTypeRef[] getParamTypes(LLVMTypeRef functionType) {
        int numParams = LLVM.LLVMCountParamTypes(functionType);
        PointerPointer<LLVMTypeRef> argTypesPointer = new PointerPointer<>(numParams);
        LLVM.LLVMGetParamTypes(functionType, argTypesPointer);
        return IntStream.range(0, numParams).mapToObj(i -> argTypesPointer.get(LLVMTypeRef.class, i)).toArray(LLVMTypeRef[]::new);
    }

    public static boolean isFunctionVarArg(LLVMTypeRef functionType) {
        return LLVM.LLVMIsFunctionVarArg(functionType) == TRUE;
    }

    public LLVMTypeRef voidType() {
        return LLVM.LLVMVoidTypeInContext(context);
    }

    public static boolean isVoidType(LLVMTypeRef type) {
        return LLVM.LLVMGetTypeKind(type) == LLVM.LLVMVoidTypeKind;
    }

    private LLVMTypeRef tokenType() {
        return LLVM.LLVMTokenTypeInContext(context);
    }

    private LLVMTypeRef metadataType() {
        return LLVM.LLVMMetadataTypeInContext(context);
    }

    public LLVMTypeRef undefType() {
        /* Use a non-standard integer size to make sure the value is never used in an instruction */
        return integerType(42);
    }

    public static boolean compatibleTypes(LLVMTypeRef a, LLVMTypeRef b) {
        int aKind = LLVM.LLVMGetTypeKind(a);
        int bKind = LLVM.LLVMGetTypeKind(b);
        if (aKind != bKind) {
            return false;
        }
        if (aKind == LLVM.LLVMIntegerTypeKind) {
            return LLVM.LLVMGetIntTypeWidth(a) == LLVM.LLVMGetIntTypeWidth(b);
        }
        if (aKind == LLVM.LLVMPointerTypeKind) {
            return LLVM.LLVMGetPointerAddressSpace(a) == LLVM.LLVMGetPointerAddressSpace(b) && compatibleTypes(LLVM.LLVMGetElementType(a), LLVM.LLVMGetElementType(b));
        }
        return true;
    }

    public static String intrinsicType(LLVMTypeRef type) {
        switch (LLVM.LLVMGetTypeKind(type)) {
            case LLVM.LLVMIntegerTypeKind:
                return "i" + integerTypeWidth(type);
            case LLVM.LLVMFloatTypeKind:
                return "f32";
            case LLVM.LLVMDoubleTypeKind:
                return "f64";
            case LLVM.LLVMVoidTypeKind:
                return "isVoid";
            case LLVM.LLVMPointerTypeKind:
                return "p" + LLVM.LLVMGetPointerAddressSpace(type) + intrinsicType(LLVM.LLVMGetElementType(type));
            case LLVM.LLVMFunctionTypeKind:
                String args = Arrays.stream(getParamTypes(type)).map(LLVMIRBuilder::intrinsicType).collect(Collectors.joining(""));
                return "f_" + intrinsicType(getReturnType(type)) + args + "f";
            case LLVM.LLVMStructTypeKind:
                String types = Arrays.stream(getElementTypes(type)).map(LLVMIRBuilder::intrinsicType).collect(Collectors.joining(""));
                return "sl_" + types + "s";
            default:
                throw shouldNotReachHere();
        }
    }

    /* Constants */

    public LLVMValueRef constantBoolean(boolean x) {
        return constantInteger(x ? TRUE : FALSE, 1);
    }

    public LLVMValueRef constantByte(byte x) {
        return constantInteger(x, 8);
    }

    public LLVMValueRef constantShort(short x) {
        return constantInteger(x, 16);
    }

    public LLVMValueRef constantChar(char x) {
        return constantInteger(x, 16);
    }

    public LLVMValueRef constantInt(int x) {
        return constantInteger(x, 32);
    }

    public LLVMValueRef constantLong(long x) {
        return constantInteger(x, 64);
    }

    public LLVMValueRef constantInteger(long value, int bits) {
        return LLVM.LLVMConstInt(integerType(bits), value, FALSE);
    }

    public LLVMValueRef constantFloat(float x) {
        return LLVM.LLVMConstReal(floatType(), x);
    }

    public LLVMValueRef constantDouble(double x) {
        return LLVM.LLVMConstReal(doubleType(), x);
    }

    public LLVMValueRef constantNull(LLVMTypeRef type) {
        return LLVM.LLVMConstNull(type);
    }

    public LLVMValueRef buildGlobalStringPtr(String name) {
        return LLVM.LLVMBuildGlobalStringPtr(builder, name, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef constantString(String string) {
        return LLVM.LLVMConstStringInContext(context, string, string.length(), FALSE);
    }

    public LLVMValueRef constantVector(LLVMValueRef... values) {
        return LLVM.LLVMConstVector(new PointerPointer<>(values), values.length);
    }

    /* Values */

    public LLVMValueRef getFunctionParam(int index) {
        return getParam(function, index);
    }

    public static LLVMValueRef getParam(LLVMValueRef func, int index) {
        return LLVM.LLVMGetParam(func, index);
    }

    public LLVMValueRef buildPhi(LLVMTypeRef phiType, LLVMValueRef[] incomingValues, LLVMBasicBlockRef[] incomingBlocks) {
        LLVMValueRef phi = LLVM.LLVMBuildPhi(builder, phiType, DEFAULT_INSTR_NAME);
        addIncoming(phi, incomingValues, incomingBlocks);
        return phi;
    }

    public void addIncoming(LLVMValueRef phi, LLVMValueRef[] values, LLVMBasicBlockRef[] blocks) {
        assert values.length == blocks.length;
        LLVM.LLVMAddIncoming(phi, new PointerPointer<>(values), new PointerPointer<>(blocks), blocks.length);
    }

    /*
     * External globals are globals which are not created by LLVM but need to be accessed from the
     * code, while unique globals are values created by the LLVM backend, potentially in multiple
     * functions, and are then conflated by the LLVM linker.
     */
    public LLVMValueRef getExternalObject(String name, boolean compressed) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, objectType(compressed), name, pointerAddressSpace(true, false));
            setLinkage(val, LinkageType.External);
        }
        return val;
    }

    public LLVMValueRef getExternalSymbol(String name) {
        LLVMValueRef val = getGlobal(name);
        if (val == null) {
            val = LLVM.LLVMAddGlobalInAddressSpace(module, rawPointerType(), name, UNTRACKED_POINTER_ADDRESS_SPACE);
            setLinkage(val, LinkageType.External);
        }
        return val;
    }

    public LLVMValueRef getUniqueGlobal(String name, LLVMTypeRef type, boolean zeroInitialized) {
        LLVMValueRef global = getGlobal(name);
        if (global == null) {
            global = LLVM.LLVMAddGlobalInAddressSpace(module, type, name, pointerAddressSpace(isObjectType(type), false));
            if (zeroInitialized) {
                setInitializer(global, LLVM.LLVMConstNull(type));
            }
            setLinkage(global, LinkageType.LinkOnceODR);
        }
        return global;
    }

    private LLVMValueRef getGlobal(String name) {
        return LLVM.LLVMGetNamedGlobal(module, name);
    }

    public void setInitializer(LLVMValueRef global, LLVMValueRef value) {
        LLVM.LLVMSetInitializer(global, value);
    }

    public LLVMValueRef register(String name) {
        String nameEncoding = name + "\00";
        LLVMValueRef[] vals = new LLVMValueRef[]{LLVM.LLVMMDStringInContext(context, nameEncoding, nameEncoding.length())};
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(vals), vals.length);
    }

    public LLVMValueRef buildReadRegister(LLVMValueRef register) {
        LLVMTypeRef readRegisterType = functionType(wordType(), metadataType());
        return buildIntrinsicCall("llvm.read_register." + intrinsicType(wordType()), readRegisterType, register);
    }

    public LLVMValueRef buildExtractValue(LLVMValueRef struct, int i) {
        return LLVM.LLVMBuildExtractValue(builder, struct, i, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildInsertValue(LLVMValueRef struct, int i, LLVMValueRef value) {
        return LLVM.LLVMBuildInsertValue(builder, struct, value, i, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildExtractElement(LLVMValueRef vector, LLVMValueRef index) {
        return LLVM.LLVMBuildExtractElement(builder, vector, index, DEFAULT_INSTR_NAME);
    }

    public void setFunctionMetadata(String kind, LLVMValueRef metadata) {
        setMetadata(function, kind, metadata);
    }

    public void setMetadata(LLVMValueRef instr, String kind, LLVMValueRef metadata) {
        LLVM.LLVMSetMetadata(instr, LLVM.LLVMGetMDKindIDInContext(context, kind, kind.length()), metadata);
    }

    public void setValueName(LLVMValueRef value, String name) {
        LLVM.LLVMSetValueName(value, name);
    }

    public LLVMValueRef getUndef() {
        return LLVM.LLVMGetUndef(undefType());
    }

    /* Control flow */

    public LLVMValueRef buildCall(LLVMValueRef callee, LLVMValueRef... args) {
        return LLVM.LLVMBuildCall(builder, callee, new PointerPointer<>(args), args.length, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildInvoke(LLVMValueRef callee, LLVMBasicBlockRef successor, LLVMBasicBlockRef handler, LLVMValueRef... args) {
        return LLVM.LLVMBuildInvoke(builder, callee, new PointerPointer<>(args), args.length, successor, handler, DEFAULT_INSTR_NAME);
    }

    private LLVMValueRef buildIntrinsicCall(String name, LLVMTypeRef type, LLVMValueRef... args) {
        LLVMValueRef intrinsic = getFunction(name, type);
        return buildCall(intrinsic, args);
    }

    public LLVMValueRef buildInlineAsm(LLVMTypeRef functionType, String asm, boolean hasSideEffects, boolean alignStack, InlineAssemblyConstraint... constraints) {
        String[] constraintStrings = new String[constraints.length];
        for (int i = 0; i < constraints.length; ++i) {
            constraintStrings[i] = constraints[i].toString();
        }
        String constraintString = String.join(",", constraintStrings);
        return LLVM.LLVMConstInlineAsm(functionType, asm, constraintString, hasSideEffects ? TRUE : FALSE, alignStack ? TRUE : FALSE);
    }

    public static class InlineAssemblyConstraint {
        private final Type type;
        private final Location location;

        public InlineAssemblyConstraint(Type type, Location location) {
            this.type = type;
            this.location = location;
        }

        @Override
        public String toString() {
            return type.repr + location.repr;
        }

        public enum Type {
            Output("="),
            Input("");

            private String repr;

            Type(String repr) {
                this.repr = repr;
            }
        }

        public static final class Location {
            private final String repr;

            private Location(String repr) {
                this.repr = repr;
            }

            public static Location register() {
                return new Location("r");
            }

            public static Location namedRegister(String register) {
                return new Location("{" + register + "}");
            }
        }
    }

    public void setCallSiteAttribute(LLVMValueRef call, Attribute attribute, String value) {
        LLVMAttributeRef attr = LLVM.LLVMCreateStringAttribute(context, attribute.name, attribute.name.length(), value, value.length());
        LLVM.LLVMAddCallSiteAttribute(call, LLVM.LLVMAttributeFunctionIndex, attr);
    }

    public void setCallSiteAttribute(LLVMValueRef call, Attribute attribute) {
        int kind = LLVM.LLVMGetEnumAttributeKindForName(attribute.name, attribute.name.length());
        LLVMAttributeRef attr;
        if (kind != 0) {
            attr = LLVM.LLVMCreateEnumAttribute(context, kind, ENUM_ATTRIBUTE_VALUE);
            LLVM.LLVMAddCallSiteAttribute(call, LLVM.LLVMAttributeFunctionIndex, attr);
        } else {
            setCallSiteAttribute(call, attribute, "true");
        }
    }

    public void buildRetVoid() {
        LLVM.LLVMBuildRetVoid(builder);
    }

    public void buildRet(LLVMValueRef value) {
        LLVM.LLVMBuildRet(builder, value);
    }

    public void buildBranch(LLVMBasicBlockRef block) {
        LLVM.LLVMBuildBr(builder, block);
    }

    public LLVMValueRef buildIf(LLVMValueRef condition, LLVMBasicBlockRef thenBlock, LLVMBasicBlockRef elseBlock) {
        return LLVM.LLVMBuildCondBr(builder, condition, thenBlock, elseBlock);
    }

    public LLVMValueRef buildSwitch(LLVMValueRef value, LLVMBasicBlockRef defaultBlock, LLVMValueRef[] switchValues, LLVMBasicBlockRef[] switchBlocks) {
        assert switchValues.length == switchBlocks.length;

        LLVMValueRef switchVal = LLVM.LLVMBuildSwitch(builder, value, defaultBlock, switchBlocks.length);
        for (int i = 0; i < switchBlocks.length; ++i) {
            LLVM.LLVMAddCase(switchVal, switchValues[i], switchBlocks[i]);
        }
        return switchVal;
    }

    public void buildLandingPad() {
        LLVMValueRef landingPad = LLVM.LLVMBuildLandingPad(builder, tokenType(), null, 1, DEFAULT_INSTR_NAME);
        LLVM.LLVMAddClause(landingPad, constantNull(rawPointerType()));
    }

    public void buildStackmap(LLVMValueRef patchpointId, LLVMValueRef... liveValues) {
        LLVMTypeRef stackmapType = functionType(voidType(), true, longType(), intType());

        LLVMValueRef[] allArgs = new LLVMValueRef[2 + liveValues.length];
        allArgs[0] = patchpointId;
        allArgs[1] = constantInt(0);
        System.arraycopy(liveValues, 0, allArgs, 2, liveValues.length);

        buildIntrinsicCall("llvm.experimental.stackmap", stackmapType, allArgs);
    }

    public void buildUnreachable() {
        LLVM.LLVMBuildUnreachable(builder);
    }

    public void buildDebugtrap() {
        buildIntrinsicCall("llvm.debugtrap", functionType(voidType()));
    }

    private LLVMMetadataRef getCompileUnit(String filename, LLVMMetadataRef diFile) {
        final String producer = "Graal Compiler";
        LLVMMetadataRef compileUnit;
        if (diFilenameToCU.containsKey(filename)) {
            compileUnit = diFilenameToCU.get(filename);
        } else {
            compileUnit = LLVM.LLVMDIBuilderCreateCompileUnit(diBuilder, LLVM.LLVMDWARFSourceLanguageJava, diFile, producer, producer.length(),
                    0, "", 0, 0, "", 0, LLVM.LLVMDWARFEmissionFull,
                    0, 1, 1, "", 0, "", 0);


            diFilenameToCU.put(filename, compileUnit);

        }
        return compileUnit;
    }

    private PointerPointer<LLVMMetadataRef> buildFunctionParamType(ResolvedJavaMethod method) {
        int paramCount = method.getSignature().getParameterCount(false);
        LLVMMetadataRef[] paramTypes = new LLVMMetadataRef[paramCount + 1];
        ArrayList<LLVMMetadataRef> paramList = new ArrayList<LLVMMetadataRef>();
        ArrayList<String> typeNames = new ArrayList<String>();
        // First add the return type name
        typeNames.add(method.getSignature().getReturnType(method.getDeclaringClass()).getName());
        // Add the typenames of the parameters
        for (int i = 0; i < paramCount; i++) {
            typeNames.add(method.getSignature().getParameterType(i, method.getDeclaringClass()).getName());
        }
        for (String typeName: typeNames) {
            paramList.add(getDiType(typeName));
        }
        return new PointerPointer<LLVMMetadataRef>(paramList.toArray(paramTypes));
    }

    private LLVMMetadataRef getSubProgram(LLVMMetadataRef diFile, ResolvedJavaMethod method) {
        String linkageName = LLVMGenerator.getFunctionName(method);
        String methodName = DebugInfoProviderHelper.getMethodName(method);
        LLVMMetadataRef subProgram;
        if (diFunctionToSP.containsKey(linkageName)) {
            subProgram = diFunctionToSP.get(linkageName);
        } else {
            PointerPointer<LLVMMetadataRef> paramTypeP = buildFunctionParamType(method);

            LLVMMetadataRef spType = LLVM.LLVMDIBuilderCreateSubroutineType(diBuilder,
                    diFile, paramTypeP, method.getSignature().getParameterCount(false), 0);
            LLVMDebugInfoProvider.LLVMLocationInfo dbgLocInfo =
                    dbgInfoProvider.new LLVMLocationInfo(method, 0, graph.getDebug());
            //TODO: Get the scope line num if possible
            subProgram = LLVM.LLVMDIBuilderCreateFunction(
                    diBuilder, diFile, methodName, methodName.length(), linkageName, linkageName.length(),
                    diFile, dbgLocInfo.line(), // Func Line
                    spType, 0, 1,
                    0, // Scope Line
                    0, 0);
            diFunctionToSP.put(linkageName, subProgram);
        }
        return subProgram;
    }

    public void createDILocalVariable(ValueNode node, LLVMValueRef instr, LLVMMetadataRef diLocation, LLVMMetadataRef subProgram,
                                    int lineNum, int bci, ResolvedJavaMethod method) {
        Local[] localVars = DebugInfoProviderHelper.getLocalsBySlot(method, bci);
        for (Local localVar: localVars) {
            // Assuming the start bci of a local variable is where it is first declared
            if (localVar.getStartBCI() == bci) {
                LLVMMetadataRef diFile = LLVM.LLVMDIScopeGetFile(subProgram);
                LLVMMetadataRef localDIType = getDiType(localVar.getType().getName());
                String varName = localVar.getName();
                LLVMMetadataRef diLocalVariable = LLVM.LLVMDIBuilderCreateAutoVariable(diBuilder, subProgram, varName,
                        varName.length(), diFile, lineNum, localDIType, 0, 0, 0);


                DILocalVarInfo varInfo = new DILocalVarInfo(instr, diLocalVariable, diLocation);
                this.localVarListPerBlock.push(varInfo);
            }
        }
    }

    public void insertLocalVarDeclarations() {
        if (!localVarListPerBlock.isEmpty()) {
            for (DILocalVarInfo varInfo : localVarListPerBlock) {
                long[] nullopt = {};
                LLVMMetadataRef expr = LLVM.LLVMDIBuilderCreateExpression(diBuilder, nullopt, nullopt.length);
                LLVM.LLVMDIBuilderInsertDeclareAtEnd(diBuilder, varInfo.instr, varInfo.diLocalVariable, expr, varInfo.diLocation, LLVM.LLVMGetInsertBlock(builder));
            }
            localVarListPerBlock.clear();
        }
    }

    public void buildDebugInfoForInstr(ValueNode node, LLVMValueRef instr) {
        NodeSourcePosition position = node.getNodeSourcePosition();
        // If the subprogram is null, the debuginfo inside the function is ignored.
        if ((position != null) && (this.diSubProgram != null)) {
            DebugContext debugContext = node.getDebug();
            LLVMDebugInfoProvider.LLVMLocationInfo dbgLocInfo =
                    dbgInfoProvider.new LLVMLocationInfo(position.getMethod(), position.getBCI(), debugContext);
            String filename = dbgLocInfo.fileName();
            // The llvm-link verifier doesn't allow null filenames for subprograms
            if (!filename.equals("")) {
                String directory = "";
                if (dbgLocInfo.filePath() != null) {
                    directory = dbgLocInfo.filePath().toString();
                }
                int lineNum = dbgLocInfo.line();
                LLVMMetadataRef diFile = getDiFile(filename, directory);
                LLVMMetadataRef subProgram = getSubProgram(diFile, position.getMethod());
                LLVMMetadataRef diLocation;
                // Means the method is inlined
                if (position.getMethod() != this.mainMethod) {
                    // TODO: Is it possible to get the location where a method is inlined?
                    LLVMMetadataRef inlinedAt = LLVM.LLVMDIBuilderCreateDebugLocation(context, 0, 0,
                            this.diSubProgram, null);
                    diLocation = LLVM.LLVMDIBuilderCreateDebugLocation(context, lineNum, 0,
                            subProgram, inlinedAt);
                } else {
                    diLocation = LLVM.LLVMDIBuilderCreateDebugLocation(context, lineNum, 0,
                            subProgram, null);
                }
                LLVM.LLVMSetCurrentDebugLocation2(builder, diLocation);
                // Call instructions require the following API call for the debug info to be set corectly, not really sure why
                if ((LLVM.LLVMIsACallInst(instr) != null) || (LLVM.LLVMIsAInvokeInst(instr) != null)) {
                    LLVM.LLVMSetInstDebugLocation(builder, instr);
                }
                // Check if this llvm instruction corresponds to any local variables declared
                if (DebugInfoProviderHelper.getLocalsBySlot(position.getMethod(), position.getBCI()) != null) {
                    createDILocalVariable(node, instr, diLocation, subProgram, lineNum, position.getBCI(), position.getMethod());
                }
            }
        }
        // Ignoring adding debug info for instructions with no positions, so if there are inlinable call instructions without
        // position, they will be caught by the asserts of the llvm-linker.
  }

    public LLVMValueRef functionEntryCount(LLVMValueRef count) {
        String functionEntryCountName = "function_entry_count";
        LLVMValueRef[] values = new LLVMValueRef[2];
        values[0] = LLVM.LLVMMDStringInContext(context, functionEntryCountName, functionEntryCountName.length());
        values[1] = count;
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(values), values.length);
    }

    public LLVMValueRef branchWeights(LLVMValueRef... weights) {
        String branchWeightsName = "branch_weights";
        LLVMValueRef[] values = new LLVMValueRef[weights.length + 1];
        values[0] = LLVM.LLVMMDStringInContext(context, branchWeightsName, branchWeightsName.length());
        System.arraycopy(weights, 0, values, 1, weights.length);
        return LLVM.LLVMMDNodeInContext(context, new PointerPointer<>(values), values.length);
    }

    /* Comparisons */

    public LLVMValueRef buildIsNull(LLVMValueRef value) {
        return LLVM.LLVMBuildIsNull(builder, value, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCompare(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
        LLVMTypeRef aType = LLVM.LLVMTypeOf(a);
        LLVMTypeRef bType = LLVM.LLVMTypeOf(b);

        assert compatibleTypes(aType, bType) : dumpTypes("comparison", aType, bType);

        switch (LLVM.LLVMGetTypeKind(aType)) {
            case LLVM.LLVMIntegerTypeKind:
            case LLVM.LLVMPointerTypeKind:
                return buildICmp(cond, a, b);
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                return buildFCmp(cond, a, b, unordered);
            default:
                throw shouldNotReachHere(dumpTypes("comparison", aType, bType));
        }
    }

    public LLVMValueRef buildICmp(Condition cond, LLVMValueRef a, LLVMValueRef b) {
        int conditionCode;
        switch (cond) {
            case EQ:
                conditionCode = LLVM.LLVMIntEQ;
                break;
            case NE:
                conditionCode = LLVM.LLVMIntNE;
                break;
            case LT:
                conditionCode = LLVM.LLVMIntSLT;
                break;
            case LE:
                conditionCode = LLVM.LLVMIntSLE;
                break;
            case GT:
                conditionCode = LLVM.LLVMIntSGT;
                break;
            case GE:
                conditionCode = LLVM.LLVMIntSGE;
                break;
            case AE:
                conditionCode = LLVM.LLVMIntUGE;
                break;
            case BE:
                conditionCode = LLVM.LLVMIntULE;
                break;
            case AT:
                conditionCode = LLVM.LLVMIntUGT;
                break;
            case BT:
                conditionCode = LLVM.LLVMIntULT;
                break;
            default:
                throw shouldNotReachHere("invalid condition");
        }
        return LLVM.LLVMBuildICmp(builder, conditionCode, a, b, DEFAULT_INSTR_NAME);
    }

    private LLVMValueRef buildFCmp(Condition cond, LLVMValueRef a, LLVMValueRef b, boolean unordered) {
        int conditionCode;
        switch (cond) {
            case EQ:
                conditionCode = (unordered) ? LLVM.LLVMRealUEQ : LLVM.LLVMRealOEQ;
                break;
            case NE:
                conditionCode = (unordered) ? LLVM.LLVMRealUNE : LLVM.LLVMRealONE;
                break;
            case LT:
                conditionCode = (unordered) ? LLVM.LLVMRealULT : LLVM.LLVMRealOLT;
                break;
            case LE:
                conditionCode = (unordered) ? LLVM.LLVMRealULE : LLVM.LLVMRealOLE;
                break;
            case GT:
                conditionCode = (unordered) ? LLVM.LLVMRealUGT : LLVM.LLVMRealOGT;
                break;
            case GE:
                conditionCode = (unordered) ? LLVM.LLVMRealUGE : LLVM.LLVMRealOGE;
                break;
            default:
                throw shouldNotReachHere("invalid condition");
        }
        return LLVM.LLVMBuildFCmp(builder, conditionCode, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSelect(LLVMValueRef condition, LLVMValueRef trueVal, LLVMValueRef falseVal) {
        return LLVM.LLVMBuildSelect(builder, condition, trueVal, falseVal, DEFAULT_INSTR_NAME);
    }

    /* Arithmetic */

    private interface UnaryBuilder {
        LLVMValueRef build(LLVMBuilderRef builder, LLVMValueRef a, String str);
    }

    private interface BinaryBuilder {
        LLVMValueRef build(LLVMBuilderRef builder, LLVMValueRef a, LLVMValueRef b, String str);
    }

    public LLVMValueRef buildNeg(LLVMValueRef a) {
        LLVMTypeRef type = LLVM.LLVMTypeOf(a);

        UnaryBuilder unaryBuilder;
        switch (LLVM.LLVMGetTypeKind(type)) {
            case LLVM.LLVMIntegerTypeKind:
                unaryBuilder = LLVM::LLVMBuildNeg;
                break;
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                unaryBuilder = LLVM::LLVMBuildFNeg;
                break;
            default:
                throw shouldNotReachHere(dumpTypes("invalid negation type", type));
        }

        return unaryBuilder.build(builder, a, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAdd(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildAdd, LLVM::LLVMBuildFAdd);
    }

    public LLVMValueRef buildSub(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSub, LLVM::LLVMBuildFSub);
    }

    public LLVMValueRef buildMul(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildMul, LLVM::LLVMBuildFMul);
    }

    public LLVMValueRef buildDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSDiv, LLVM::LLVMBuildFDiv);
    }

    public LLVMValueRef buildRem(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildSRem, LLVM::LLVMBuildFRem);
    }

    public LLVMValueRef buildUDiv(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildUDiv, null);
    }

    public LLVMValueRef buildURem(LLVMValueRef a, LLVMValueRef b) {
        return buildBinaryNumberOp(a, b, LLVM::LLVMBuildURem, null);
    }

    private LLVMValueRef buildBinaryNumberOp(LLVMValueRef a, LLVMValueRef b, BinaryBuilder integerBuilder, BinaryBuilder realBuilder) {
        LLVMTypeRef aType = LLVM.LLVMTypeOf(a);
        LLVMTypeRef bType = LLVM.LLVMTypeOf(b);
        assert compatibleTypes(aType, bType) : dumpValues("invalid binary operation arguments", a, b);

        BinaryBuilder binaryBuilder;
        switch (LLVM.LLVMGetTypeKind(aType)) {
            case LLVM.LLVMIntegerTypeKind:
                binaryBuilder = integerBuilder;
                break;
            case LLVM.LLVMFloatTypeKind:
            case LLVM.LLVMDoubleTypeKind:
                binaryBuilder = realBuilder;
                break;
            default:
                throw shouldNotReachHere(dumpValues("invalid binary operation arguments", a, b));
        }

        return binaryBuilder.build(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAbs(LLVMValueRef a) {
        return buildIntrinsicOp("abs", a, constantBoolean(false));
    }

    public LLVMValueRef buildFabs(LLVMValueRef a) {
        return buildIntrinsicOp("fabs", a);
    }

    public LLVMValueRef buildLog(LLVMValueRef a) {
        return buildIntrinsicOp("log", a);
    }

    public LLVMValueRef buildLog10(LLVMValueRef a) {
        return buildIntrinsicOp("log10", a);
    }

    public LLVMValueRef buildSqrt(LLVMValueRef a) {
        return buildIntrinsicOp("sqrt", a);
    }

    public LLVMValueRef buildCos(LLVMValueRef a) {
        return buildIntrinsicOp("cos", a);
    }

    public LLVMValueRef buildSin(LLVMValueRef a) {
        return buildIntrinsicOp("sin", a);
    }

    public LLVMValueRef buildExp(LLVMValueRef a) {
        return buildIntrinsicOp("exp", a);
    }

    public LLVMValueRef buildPow(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("pow", a, b);
    }

    public LLVMValueRef buildCeil(LLVMValueRef a) {
        return buildIntrinsicOp("ceil", a);
    }

    public LLVMValueRef buildFloor(LLVMValueRef a) {
        return buildIntrinsicOp("floor", a);
    }

    public LLVMValueRef buildMin(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("minimum", a, b);
    }

    public LLVMValueRef buildMax(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("maximum", a, b);
    }

    public LLVMValueRef buildCopysign(LLVMValueRef a, LLVMValueRef b) {
        return buildIntrinsicOp("copysign", a, b);
    }

    public LLVMValueRef buildFma(LLVMValueRef a, LLVMValueRef b, LLVMValueRef c) {
        return buildIntrinsicOp("fma", a, b, c);
    }

    public LLVMValueRef buildBswap(LLVMValueRef a) {
        return buildIntrinsicOp("bswap", a);
    }

    private LLVMValueRef buildIntrinsicOp(String name, LLVMTypeRef retType, LLVMValueRef... args) {
        String intrinsicName = "llvm." + name + "." + intrinsicType(retType);
        LLVMTypeRef intrinsicType = functionType(retType, Arrays.stream(args).map(LLVM::LLVMTypeOf).toArray(LLVMTypeRef[]::new));

        return buildIntrinsicCall(intrinsicName, intrinsicType, args);
    }

    private LLVMValueRef buildIntrinsicOp(String name, LLVMValueRef... args) {
        return buildIntrinsicOp(name, LLVM.LLVMTypeOf(args[0]), args);
    }

    /* Bitwise */

    public LLVMValueRef buildNot(LLVMValueRef input) {
        return LLVM.LLVMBuildNot(builder, input, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAnd(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildAnd(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildOr(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildOr(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildXor(LLVMValueRef a, LLVMValueRef b) {
        return LLVM.LLVMBuildXor(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildShl(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildShl, a, b);
    }

    public LLVMValueRef buildShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildAShr, a, b);
    }

    public LLVMValueRef buildUShr(LLVMValueRef a, LLVMValueRef b) {
        return buildShift(LLVM::LLVMBuildLShr, a, b);
    }

    private LLVMValueRef buildShift(BinaryBuilder binaryBuilder, LLVMValueRef a, LLVMValueRef b) {
        return binaryBuilder.build(builder, a, b, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCtlz(LLVMValueRef a) {
        return buildIntrinsicOp("ctlz", a, constantBoolean(false));
    }

    public LLVMValueRef buildCttz(LLVMValueRef a) {
        return buildIntrinsicOp("cttz", a, constantBoolean(false));
    }

    public LLVMValueRef buildCtpop(LLVMValueRef a) {
        return buildIntrinsicOp("ctpop", a);
    }

    /* Conversions */

    public LLVMValueRef buildBitcast(LLVMValueRef value, LLVMTypeRef type) {
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        if (compatibleTypes(valueType, type)) {
            return value;
        }

        return LLVM.LLVMBuildBitCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildAddrSpaceCast(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildAddrSpaceCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildIntToPtr(LLVMValueRef value, LLVMTypeRef type) {
        if (isObjectType(type)) {
            return buildCall(helpers.getIntToObjectFunction(isCompressedPointerType(type)), value);
        }
        return buildLLVMIntToPtr(value, type);
    }

    LLVMValueRef buildLLVMIntToPtr(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildIntToPtr(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildPtrToInt(LLVMValueRef value) {
        return LLVM.LLVMBuildPtrToInt(builder, value, wordType(), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildFPToSI(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPToSI(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSIToFP(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildSIToFP(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildFPCast(LLVMValueRef value, LLVMTypeRef type) {
        return LLVM.LLVMBuildFPCast(builder, value, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildTrunc(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildTrunc(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildSExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildSExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildZExt(LLVMValueRef value, int toBits) {
        return LLVM.LLVMBuildZExt(builder, value, integerType(toBits), DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCompress(LLVMValueRef uncompressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        return buildCall(helpers.getCompressFunction(nonNull, shift), uncompressed, heapBase);
    }

    public LLVMValueRef buildUncompress(LLVMValueRef compressed, LLVMValueRef heapBase, boolean nonNull, int shift) {
        return buildCall(helpers.getUncompressFunction(nonNull, shift), compressed, heapBase);
    }

    /* Memory */

    public LLVMValueRef buildGEP(LLVMValueRef base, LLVMValueRef... indices) {
        return LLVM.LLVMBuildGEP(builder, base, new PointerPointer<>(indices), indices.length, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildLoad(LLVMValueRef address, LLVMTypeRef type) {
        LLVMTypeRef addressType = LLVM.LLVMTypeOf(address);
        if (isObjectType(type) && !isObjectType(addressType)) {
            boolean compressed = isCompressedPointerType(type);
            return buildCall(helpers.getLoadObjectFromUntrackedPointerFunction(compressed), address);
        }
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(type, isObjectType(addressType), false));
        return buildLoad(castedAddress);
    }

    public LLVMValueRef buildLoad(LLVMValueRef address) {
        return LLVM.LLVMBuildLoad(builder, address, DEFAULT_INSTR_NAME);
    }

    public void buildStore(LLVMValueRef value, LLVMValueRef address) {
        LLVM.LLVMBuildStore(builder, value, address);
    }

    public void buildVolatileStore(LLVMValueRef value, LLVMValueRef address, int alignment) {
        LLVMValueRef store = LLVM.LLVMBuildStore(builder, value, address);
        LLVM.LLVMSetOrdering(store, LLVM.LLVMAtomicOrderingRelease);
        LLVM.LLVMSetAlignment(store, alignment);
    }

    public LLVMValueRef buildAlloca(LLVMTypeRef type) {
        return LLVM.LLVMBuildAlloca(builder, type, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildArrayAlloca(LLVMTypeRef type, int slots, int alignmentInBytes) {
        LLVMValueRef alloca = LLVM.LLVMBuildArrayAlloca(builder, type, constantInt(slots), DEFAULT_INSTR_NAME);
        LLVM.LLVMSetAlignment(alloca, alignmentInBytes);
        return alloca;
    }

    public void buildPrefetch(LLVMValueRef address) {
        LLVMTypeRef addressType = LLVM.LLVMTypeOf(address);
        LLVMTypeRef prefetchType = functionType(voidType(), addressType, intType(), intType(), intType());
        /* llvm.prefetch(address, WRITE, NO_LOCALITY, DATA) */
        buildIntrinsicCall("llvm.prefetch." + intrinsicType(addressType), prefetchType, address, constantInt(1), constantInt(0), constantInt(1));
    }

    public LLVMValueRef buildReturnAddress(LLVMValueRef level) {
        LLVMTypeRef returnAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.returnaddress", returnAddressType, level);
    }

    public LLVMValueRef buildFrameAddress(LLVMValueRef level) {
        LLVMTypeRef frameAddressType = functionType(rawPointerType(), intType());
        return buildIntrinsicCall("llvm.frameaddress." + intrinsicType(rawPointerType()), frameAddressType, level);
    }

    /* Atomic */

    public void buildFence() {
        boolean singleThread = !SubstrateOptions.MultiThreaded.getValue();
        LLVM.LLVMBuildFence(builder, LLVM.LLVMAtomicOrderingSequentiallyConsistent, singleThread ? TRUE : FALSE, DEFAULT_INSTR_NAME);
    }

    public LLVMValueRef buildCmpxchg(LLVMValueRef address, LLVMValueRef expectedValue, LLVMValueRef newValue, MemoryOrderMode memoryOrder, boolean returnValue) {
        LLVMTypeRef exchangeType = typeOf(expectedValue);
        if (isObjectType(typeOf(expectedValue))) {
            return buildCall(helpers.getCmpxchgFunction(isCompressedPointerType(exchangeType), memoryOrder, returnValue), address, expectedValue, newValue);
        }
        return buildAtomicCmpXchg(address, expectedValue, newValue, memoryOrder, returnValue);
    }

    private static final int LLVM_CMPXCHG_VALUE = 0;
    private static final int LLVM_CMPXCHG_SUCCESS = 1;

    LLVMValueRef buildAtomicCmpXchg(LLVMValueRef addr, LLVMValueRef expected, LLVMValueRef newVal, MemoryOrderMode memoryOrder, boolean returnValue) {
        boolean singleThread = !SubstrateOptions.MultiThreaded.getValue();
        LLVMValueRef cas = LLVM.LLVMBuildAtomicCmpXchg(builder, addr, expected, newVal, atomicOrdering(memoryOrder, true), atomicOrdering(memoryOrder, false), singleThread ? TRUE : FALSE);
        return buildExtractValue(cas, returnValue ? LLVM_CMPXCHG_VALUE : LLVM_CMPXCHG_SUCCESS);
    }

    public LLVMValueRef buildAtomicXchg(LLVMValueRef address, LLVMValueRef value) {
        if (isObjectType(typeOf(value))) {
            boolean compressed = isCompressedPointerType(typeOf(value));
            return buildCall(helpers.getAtomicObjectXchgFunction(compressed), address, value);
        }
        return buildLLVMAtomicXchg(address, value);
    }

    LLVMValueRef buildLLVMAtomicXchg(LLVMValueRef address, LLVMValueRef value) {
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpXchg, address, value);
    }

    public LLVMValueRef buildAtomicAdd(LLVMValueRef address, LLVMValueRef value) {
        return buildAtomicRMW(LLVM.LLVMAtomicRMWBinOpAdd, address, value);
    }

    private LLVMValueRef buildAtomicRMW(int operation, LLVMValueRef address, LLVMValueRef value) {
        LLVMTypeRef valueType = LLVM.LLVMTypeOf(value);
        LLVMValueRef castedAddress = buildBitcast(address, pointerType(valueType, isObjectType(typeOf(address)), false));
        boolean singleThread = !SubstrateOptions.MultiThreaded.getValue();
        return LLVM.LLVMBuildAtomicRMW(builder, operation, castedAddress, value, LLVM.LLVMAtomicOrderingMonotonic, singleThread ? TRUE : FALSE);
    }

    public void buildClearCache(LLVMValueRef start, LLVMValueRef end) {
        LLVMTypeRef clearCacheType = functionType(voidType(), rawPointerType(), rawPointerType());
        buildIntrinsicCall("llvm.clear_cache", clearCacheType, start, end);
    }

    private static int atomicOrdering(MemoryOrderMode memoryOrder, boolean canRelease) {
        switch (memoryOrder) {
            case PLAIN:
            case OPAQUE:
                return LLVM.LLVMAtomicOrderingMonotonic;
            case ACQUIRE:
                return LLVM.LLVMAtomicOrderingAcquire;
            case RELEASE:
                return canRelease ? LLVM.LLVMAtomicOrderingRelease : LLVM.LLVMAtomicOrderingMonotonic;
            case RELEASE_ACQUIRE:
                return canRelease ? LLVM.LLVMAtomicOrderingAcquireRelease : LLVM.LLVMAtomicOrderingAcquire;
            case VOLATILE:
                return LLVM.LLVMAtomicOrderingSequentiallyConsistent;
            default:
                throw VMError.shouldNotReachHere();
        }
    }
}
