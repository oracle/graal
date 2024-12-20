/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;

import java.util.Arrays;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.CInterfaceWriteNode;
import com.oracle.svm.core.nodes.SubstrateIndirectCallTargetNode;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CInterfaceError;
import com.oracle.svm.hosted.c.CInterfaceWrapper;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.AccessorInfo;
import com.oracle.svm.hosted.c.info.AccessorInfo.AccessorKind;
import com.oracle.svm.hosted.c.info.ConstantInfo;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.PointerToInfo;
import com.oracle.svm.hosted.c.info.SizableInfo;
import com.oracle.svm.hosted.c.info.StructBitfieldInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointJavaCallStubMethod;
import com.oracle.svm.hosted.code.CFunctionPointerCallStubSupport;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CInterfaceInvocationPlugin implements NodePlugin {
    private final NativeLibraries nativeLibs;

    private final ResolvedJavaType functionPointerType;

    public CInterfaceInvocationPlugin(MetaAccessProvider metaAccess, NativeLibraries nativeLibs) {
        this.nativeLibs = nativeLibs;
        this.functionPointerType = metaAccess.lookupJavaType(CFunctionPointer.class);
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod m, ValueNode[] args) {
        AnalysisMethod method = (AnalysisMethod) m;
        ElementInfo methodInfo = nativeLibs.findElementInfo(method);
        if (methodInfo instanceof AccessorInfo) {
            ElementInfo parentInfo = methodInfo.getParent();
            if (parentInfo instanceof StructFieldInfo) {
                int offset = ((StructFieldInfo) parentInfo).getOffsetInfo().getProperty();
                if (((AccessorInfo) methodInfo).getAccessorKind() == AccessorKind.OFFSET) {
                    return replaceOffsetOf(b, method, args, (AccessorInfo) methodInfo, offset);
                } else {
                    return replaceAccessor(b, method, args, (AccessorInfo) methodInfo, offset);
                }
            } else if (parentInfo instanceof StructBitfieldInfo) {
                return replaceBitfieldAccessor(b, method, args, (StructBitfieldInfo) parentInfo, (AccessorInfo) methodInfo);
            } else if (parentInfo instanceof StructInfo || parentInfo instanceof PointerToInfo) {
                return replaceAccessor(b, method, args, (AccessorInfo) methodInfo, 0);
            } else {
                throw shouldNotReachHereUnexpectedInput(parentInfo); // ExcludeFromJacocoGeneratedReport
            }
        } else if (methodInfo instanceof ConstantInfo) {
            return replaceConstant(b, method, (ConstantInfo) methodInfo);
        } else if (method.getAnnotation(InvokeCFunctionPointer.class) != null) {
            return replaceCFunctionPointerInvoke(b, method, args);
        } else if (method.getAnnotation(InvokeJavaFunctionPointer.class) != null) {
            return replaceJavaFunctionPointerInvoke(b, method, args);
        } else if (method.getAnnotation(CEntryPoint.class) != null) {
            assert !(method.getWrapped() instanceof CEntryPointJavaCallStubMethod) : "Call stub should never have a @CEntryPoint annotation";
            AnalysisMethod stub = CEntryPointCallStubSupport.singleton().registerJavaStubForMethod(method);
            assert !b.getMethod().equals(stub) : "Plugin should not be called for the invoke in the stub itself";
            b.handleReplacedInvoke(InvokeKind.Static, stub, args, false);
            return true;
        } else {
            return false;
        }
    }

    private static boolean replaceOffsetOf(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, AccessorInfo accessorInfo, int displacement) {
        /*
         * A method annotated with @OffsetOf can be static, but does not need to be. If it is
         * non-static, we just ignore the receiver.
         */
        assert args.length == accessorInfo.parameterCount(!method.isStatic());

        JavaKind kind = b.getWordTypes().asKind(b.getInvokeReturnType());
        b.addPush(pushKind(method), ConstantNode.forIntegerKind(kind, displacement, b.getGraph()));
        return true;
    }

    private boolean replaceAccessor(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, AccessorInfo accessorInfo, int displacement) {
        assert args.length == accessorInfo.parameterCount(true);

        SizableInfo typeInC = (SizableInfo) accessorInfo.getParent();
        ValueNode base = args[AccessorInfo.baseParameterNumber(true)];
        assert base.getStackKind() == ConfigurationValues.getWordKind();

        switch (accessorInfo.getAccessorKind()) {
            case ADDRESS -> replaceWithAddress(b, method, base, args, accessorInfo, displacement, typeInC);
            case GETTER -> replaceWithRead(b, method, base, args, accessorInfo, displacement, typeInC);
            case SETTER -> replaceWithWrite(b, method, base, args, accessorInfo, displacement, typeInC);
            default -> throw shouldNotReachHereUnexpectedInput(accessorInfo.getAccessorKind()); // ExcludeFromJacocoGeneratedReport
        }
        return true;
    }

    private static void replaceWithAddress(GraphBuilderContext b, AnalysisMethod method, ValueNode base, ValueNode[] args, AccessorInfo accessorInfo, int displacement, SizableInfo typeInC) {
        StructuredGraph graph = b.getGraph();
        ValueNode address = graph.addOrUniqueWithInputs(new AddNode(base, makeOffset(graph, args, accessorInfo, displacement, typeInC.getSizeInBytes())));
        b.addPush(pushKind(method), address);
    }

    private void replaceWithRead(GraphBuilderContext b, AnalysisMethod method, ValueNode base, ValueNode[] args, AccessorInfo accessorInfo, int displacement, SizableInfo typeInC) {
        StructuredGraph graph = b.getGraph();
        OffsetAddressNode offsetAddress = makeOffsetAddress(graph, args, accessorInfo, base, displacement, typeInC.getSizeInBytes());
        LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);

        ValueNode node;
        JavaKind returnKind = b.getWordTypes().asKind(b.getInvokeReturnType());
        if (returnKind == JavaKind.Object) {
            Stamp stamp = b.getInvokeReturnStamp(null).getTrustedStamp();
            node = b.add(new JavaReadNode(stamp, returnKind, offsetAddress, locationIdentity, BarrierType.NONE, MemoryOrderMode.PLAIN, true));
        } else if (returnKind == JavaKind.Float || returnKind == JavaKind.Double) {
            Stamp stamp = StampFactory.forKind(returnKind);
            node = readPrimitive(b, offsetAddress, locationIdentity, stamp, accessorInfo, method);
        } else {
            IntegerStamp stamp = IntegerStamp.create(typeInC.getSizeInBytes() * Byte.SIZE);
            ValueNode read = readPrimitive(b, offsetAddress, locationIdentity, stamp, accessorInfo, method);
            node = convertCIntegerToMethodReturnType(graph, method, read, typeInC.isUnsigned());
        }
        b.push(pushKind(method), node);
    }

    private static void replaceWithWrite(GraphBuilderContext b, AnalysisMethod method, ValueNode base, ValueNode[] args, AccessorInfo accessorInfo, int displacement, SizableInfo typeInC) {
        StructuredGraph graph = b.getGraph();
        OffsetAddressNode offsetAddress = makeOffsetAddress(graph, args, accessorInfo, base, displacement, typeInC.getSizeInBytes());
        LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);

        ValueNode value = args[accessorInfo.valueParameterNumber(true)];
        JavaKind valueKind = value.getStackKind();
        if (valueKind == JavaKind.Object) {
            b.add(new JavaWriteNode(valueKind, offsetAddress, locationIdentity, value, BarrierType.NONE, true));
        } else if (valueKind == JavaKind.Float || valueKind == JavaKind.Double) {
            writePrimitive(b, offsetAddress, locationIdentity, value, accessorInfo, method);
        } else {
            ValueNode adaptedValue = adaptPrimitiveTypeForWrite(graph, value, typeInC.getSizeInBytes() * Byte.SIZE, typeInC.isUnsigned());
            writePrimitive(b, offsetAddress, locationIdentity, adaptedValue, accessorInfo, method);
        }
    }

    private static ValueNode adaptPrimitiveTypeForWrite(StructuredGraph graph, ValueNode value, int toBits, boolean isCValueUnsigned) {
        JavaKind valueKind = value.getStackKind();
        int valueBits = valueKind.getBitCount();
        if (valueBits == toBits) {
            return value;
        } else if (valueBits > toBits) {
            return graph.unique(new NarrowNode(value, toBits));
        } else if (isCValueUnsigned) {
            return graph.unique(new ZeroExtendNode(value, toBits));
        } else {
            return graph.unique(new SignExtendNode(value, toBits));
        }
    }

    private boolean replaceBitfieldAccessor(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, StructBitfieldInfo bitfieldInfo, AccessorInfo accessorInfo) {
        int byteOffset = bitfieldInfo.getByteOffsetInfo().getProperty();
        int startBit = bitfieldInfo.getStartBitInfo().getProperty();
        int endBit = bitfieldInfo.getEndBitInfo().getProperty();
        boolean isCValueUnsigned = bitfieldInfo.isUnsigned();
        assert byteOffset >= 0 && byteOffset < ((SizableInfo) bitfieldInfo.getParent()).getSizeInBytes();
        assert startBit >= 0 && startBit < 8;
        assert endBit >= startBit && endBit < 64;

        /*
         * The startBit is always in the first byte. Therefore, the endBit tells us how many bytes
         * we actually have to read and write.
         */
        JavaKind readKind;
        if (endBit < 8) {
            readKind = JavaKind.Byte;
        } else if (endBit < 16) {
            readKind = JavaKind.Short;
        } else if (endBit < 32) {
            readKind = JavaKind.Int;
        } else {
            readKind = JavaKind.Long;
        }

        /*
         * Try to align the byteOffset to be a multiple of readBytes. That should always be
         * possible, but we don't trust the C compiler and memory layout enough to make it an
         * assertion.
         */
        int readBytes = readKind.getByteCount();
        int alignmentCorrection = byteOffset % readBytes;
        if (alignmentCorrection > 0 && endBit + alignmentCorrection * Byte.SIZE < readKind.getBitCount()) {
            byteOffset -= alignmentCorrection;
            startBit += alignmentCorrection * Byte.SIZE;
            endBit += alignmentCorrection * Byte.SIZE;
        }
        assert byteOffset >= 0 && byteOffset < ((SizableInfo) bitfieldInfo.getParent()).getSizeInBytes();
        assert startBit >= 0 && startBit < readKind.getBitCount();
        assert endBit >= startBit && endBit < readKind.getBitCount();

        /*
         * Read the C memory. This is also necessary for writes, since we need to keep the bits
         * around the written bitfield unchanged.
         */
        assert args.length == accessorInfo.parameterCount(true);
        ValueNode base = args[AccessorInfo.baseParameterNumber(true)];
        StructuredGraph graph = b.getGraph();
        OffsetAddressNode address = makeOffsetAddress(graph, args, accessorInfo, base, byteOffset, -1);
        LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);
        Stamp stamp = IntegerStamp.create(readKind.getBitCount());
        ValueNode cValue = readPrimitive(b, address, locationIdentity, stamp, accessorInfo, method);

        /* Arithmetic operations are always performed on 32 or 64-bit. */
        JavaKind computeKind = readKind.getStackKind();
        int computeBits = computeKind.getBitCount();
        assert startBit >= 0 && startBit < computeBits;
        assert endBit >= startBit && endBit < computeBits;

        int accessedBits = endBit - startBit + 1;
        assert accessedBits > 0 && accessedBits <= readKind.getBitCount() && accessedBits <= 64;
        assert computeBits >= accessedBits;

        /* Zero-extend the C value if it is less than 32-bit so that we can do arithmetics. */
        if (readKind.getBitCount() < 32) {
            cValue = graph.unique(new ZeroExtendNode(cValue, computeBits));
        }

        switch (accessorInfo.getAccessorKind()) {
            case GETTER -> {
                /* Reduce the C value to the bits that we really wanted to read. */
                cValue = graph.unique(new LeftShiftNode(cValue, ConstantNode.forInt(computeBits - endBit - 1, graph)));
                if (isCValueUnsigned) {
                    cValue = graph.unique(new UnsignedRightShiftNode(cValue, ConstantNode.forInt(computeBits - accessedBits, graph)));
                } else {
                    cValue = graph.unique(new RightShiftNode(cValue, ConstantNode.forInt(computeBits - accessedBits, graph)));
                }

                /* Narrow the value so that its size matches the size of a C data type. */
                int targetBits = 1 << CodeUtil.log2(accessedBits);
                if (targetBits < accessedBits) {
                    targetBits = targetBits * 2;
                }
                if (targetBits < 8) {
                    targetBits = 8;
                }

                if (computeBits > targetBits) {
                    cValue = graph.unique(new NarrowNode(cValue, targetBits));
                }

                /*
                 * Now, the value looks as if we actually read targetBits from C memory. So, we can
                 * finally convert this value to the method's declared return type.
                 */
                ValueNode result = convertCIntegerToMethodReturnType(graph, method, cValue, isCValueUnsigned);
                b.push(pushKind(method), result);
                return true;
            }
            case SETTER -> {
                /* Convert the new value to at least 32-bit so that we can do arithmetics. */
                ValueNode newValue = args[accessorInfo.valueParameterNumber(true)];
                newValue = adaptPrimitiveTypeForWrite(graph, newValue, computeBits, isCValueUnsigned);

                if (accessedBits == 64) {
                    /* The new value replaces all 64 bits of the C memory. */
                    assert startBit == 0;
                    cValue = newValue;
                } else {
                    /* Reduce the new value to the bits that we want and shift the bits in place. */
                    newValue = graph.unique(new LeftShiftNode(newValue, ConstantNode.forInt(computeBits - accessedBits, graph)));
                    newValue = graph.unique(new UnsignedRightShiftNode(newValue, ConstantNode.forInt(computeBits - accessedBits - startBit, graph)));

                    /* Replace the bits in the old value with the bits from the new value. */
                    long mask = ~(((1L << accessedBits) - 1) << startBit);
                    cValue = graph.unique(new AndNode(cValue, ConstantNode.forIntegerBits(computeBits, mask, graph)));
                    cValue = graph.unique(new OrNode(cValue, newValue));
                }

                /* Narrow the value to the bytes that we need and write those to C memory. */
                cValue = adaptPrimitiveTypeForWrite(graph, cValue, readKind.getBitCount(), isCValueUnsigned);
                writePrimitive(b, address, locationIdentity, cValue, accessorInfo, method);
                return true;
            }
            default -> throw shouldNotReachHereUnexpectedInput(accessorInfo.getAccessorKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static ValueNode readPrimitive(GraphBuilderContext b, OffsetAddressNode address, LocationIdentity locationIdentity, Stamp stamp, AccessorInfo accessorInfo, AnalysisMethod method) {
        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ValueNode replaced = ImageSingletons.lookup(CInterfaceWrapper.class).replacePrimitiveRead(b, address, stamp, method);
            if (replaced != null) {
                return replaced;
            }
        }

        CInterfaceReadNode read = b.add(new CInterfaceReadNode(address, locationIdentity, stamp, BarrierType.NONE, MemoryOrderMode.PLAIN, accessName(accessorInfo)));
        /*
         * The read must not float outside its block otherwise it may float above an explicit zero
         * check on its base address.
         */
        read.setForceFixed(true);
        return read;
    }

    private static void writePrimitive(GraphBuilderContext b, OffsetAddressNode address, LocationIdentity locationIdentity, ValueNode value, AccessorInfo accessorInfo, AnalysisMethod method) {
        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            boolean replaced = ImageSingletons.lookup(CInterfaceWrapper.class).replacePrimitiveWrite(b, address, value, method);
            if (replaced) {
                return;
            }
        }

        b.add(new CInterfaceWriteNode(address, locationIdentity, value, BarrierType.NONE, MemoryOrderMode.PLAIN, accessName(accessorInfo)));
    }

    private static String accessName(AccessorInfo accessorInfo) {
        if (accessorInfo.getParent() instanceof StructFieldInfo) {
            return accessorInfo.getParent().getParent().getName() + "." + accessorInfo.getParent().getName();
        } else {
            return accessorInfo.getParent().getName() + "*";
        }
    }

    private static ValueNode makeOffset(StructuredGraph graph, ValueNode[] args, AccessorInfo accessorInfo, int displacement, int indexScaling) {
        ValueNode offset = ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), displacement, graph);

        if (accessorInfo.isIndexed()) {
            ValueNode index = args[accessorInfo.indexParameterNumber(true)];
            assert index.getStackKind().isPrimitive();
            ValueNode wordIndex = extend(graph, index, ConfigurationValues.getWordKind().getBitCount(), false);
            ValueNode scaledIndex = graph.unique(new MulNode(wordIndex, ConstantNode.forIntegerKind(ConfigurationValues.getWordKind(), indexScaling, graph)));

            offset = graph.unique(new AddNode(scaledIndex, offset));
        }

        return offset;
    }

    private static OffsetAddressNode makeOffsetAddress(StructuredGraph graph, ValueNode[] args, AccessorInfo accessorInfo, ValueNode base, int displacement, int indexScaling) {
        return graph.addOrUniqueWithInputs(new OffsetAddressNode(base, makeOffset(graph, args, accessorInfo, displacement, indexScaling)));
    }

    private static LocationIdentity makeLocationIdentity(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, AccessorInfo accessorInfo) {
        LocationIdentity locationIdentity;
        if (accessorInfo.hasLocationIdentityParameter()) {
            ValueNode locationIdentityNode = args[accessorInfo.locationIdentityParameterNumber(true)];
            if (!locationIdentityNode.isConstant()) {
                throw UserError.abort(new CInterfaceError(
                                "locationIdentity is not a compile time constant for call to " + method.format("%H.%n(%p)") + " in " + b.getMethod().asStackTraceElement(b.bci()),
                                method).getMessage());
            }
            locationIdentity = b.getSnippetReflection().asObject(LocationIdentity.class, locationIdentityNode.asJavaConstant());
        } else if (accessorInfo.hasUniqueLocationIdentity()) {
            StructFieldInfo fieldInfo = (StructFieldInfo) accessorInfo.getParent();
            assert fieldInfo.getLocationIdentity() != null;
            locationIdentity = fieldInfo.getLocationIdentity();
        } else {
            locationIdentity = CInterfaceLocationIdentity.DEFAULT_LOCATION_IDENTITY;
        }
        return locationIdentity;
    }

    private ValueNode convertCIntegerToMethodReturnType(StructuredGraph graph, AnalysisMethod method, ValueNode cValue, boolean isCValueUnsigned) {
        return convertCIntegerToMethodReturnType(graph, nativeLibs, method.getSignature().getReturnType(), cValue, isCValueUnsigned);
    }

    /**
     * Converts a C value to a value that can be returned by a Java method (i.e., a 32- or 64-bit
     * value). Does zero- or sign-extend the value if necessary.
     *
     * <pre>
     * -----------------------------------------------------------------------------------------
     * | C type          | declared return type | result                                       |
     * -----------------------------------------------------------------------------------------
     * | signed 8-bit    | boolean              | 1 byte read, convert to 0 or 1 (32-bit)      |
     * | signed 8-bit    | byte                 | 1 byte read, sign extend to 32-bit           |
     * | signed 8-bit    | short                | 1 byte read, sign extend to 32-bit           |
     * | signed 8-bit    | char                 | 1 byte read, zero extend to 32-bit           |
     * | signed 8-bit    | int                  | 1 byte read, sign extend to 32-bit           |
     * | signed 8-bit    | long                 | 1 byte read, sign extend to 64-bit           |
     * | signed 8-bit    | UnsignedWord         | 1 byte read, zero extend to 64-bit           |
     * |                                                                                       |
     * | unsigned 8-bit  | boolean              | 1 byte read, convert to 0 or 1 (32-bit)      |
     * | unsigned 8-bit  | byte                 | 1 byte read, sign extend to 32-bit           |
     * | unsigned 8-bit  | short                | 1 byte read, zero extend to 32-bit           |
     * | unsigned 8-bit  | char                 | 1 byte read, zero extend to 32-bit           |
     * | unsigned 8-bit  | int                  | 1 byte read, zero extend to 32-bit           |
     * | unsigned 8-bit  | long                 | 1 byte read, zero extend to 64-bit           |
     * | unsigned 8-bit  | UnsignedWord         | 1 byte read, zero extend to 64-bit           |
     * |                                                                                       |
     * | signed 32-bit   | boolean              | 4 byte read, convert to 0 or 1 (32-bit)      |
     * | signed 32-bit   | byte                 | 1 byte read, sign extend to 32-bit           |
     * | signed 32-bit   | short                | 2 byte read, sign extend to 32-bit           |
     * | signed 32-bit   | char                 | 2 byte read, zero extend to 32-bit           |
     * | signed 32-bit   | int                  | 4 byte read, use value directly              |
     * | signed 32-bit   | long                 | 4 byte read, sign extend to 64-bit           |
     * | signed 32-bit   | UnsignedWord         | 4 byte read, sign extend to 64-bit           |
     * |                                                                                       |
     * | unsigned 32-bit | boolean              | 4 byte read, convert to 0 or 1 (32-bit)      |
     * | unsigned 32-bit | byte                 | 1 byte read, sign extend to 32-bit           |
     * | unsigned 32-bit | short                | 2 byte read, sign extend to 32-bit           |
     * | unsigned 32-bit | char                 | 2 byte read, zero extend to 32-bit           |
     * | unsigned 32-bit | int                  | 4 byte read, use value directly              |
     * | unsigned 32-bit | long                 | 4 byte read, zero extend to 64-bit           |
     * | unsigned 32-bit | UnsignedWord         | 4 byte read, zero extend to 64-bit           |
     * |                                                                                       |
     * | signed 64-bit   | boolean              | 8 byte read, convert to 0 or 1 (32-bit)      |
     * | signed 64-bit   | byte                 | 1 byte read, sign extend to 32-bit           |
     * | signed 64-bit   | short                | 2 byte read, sign extend to 32-bit           |
     * | signed 64-bit   | char                 | 2 byte read, zero extend to 32-bit           |
     * | signed 64-bit   | int                  | 4 byte read, use value directly              |
     * | signed 64-bit   | long                 | 8 byte read, use value directly              |
     * | signed 64-bit   | UnsignedWord         | 8 byte read, use value directly              |
     * |                                                                                       |
     * | unsigned 64-bit | boolean              | 8 byte read, convert to 0 or 1 (32-bit)      |
     * | unsigned 64-bit | byte                 | 1 byte read, sign extend to 32-bit           |
     * | unsigned 64-bit | short                | 2 byte read, sign extend to 32-bit           |
     * | unsigned 64-bit | char                 | 2 byte read, zero extend to 32-bit           |
     * | unsigned 64-bit | int                  | 4 byte read, use value directly              |
     * | unsigned 64-bit | long                 | 8 byte read, use value directly              |
     * | unsigned 64-bit | UnsignedWord         | 8 byte read, use value directly              |
     * -----------------------------------------------------------------------------------------
     * </pre>
     */
    public static ValueNode convertCIntegerToMethodReturnType(StructuredGraph graph, NativeLibraries nativeLibs, ResolvedJavaType declaredReturnType,
                    ValueNode cValue, boolean isCValueUnsigned) {
        IntegerStamp cValueStamp = (IntegerStamp) cValue.stamp(NodeView.DEFAULT);
        int bitsInC = cValueStamp.getBits();

        JavaKind declaredReturnKind = nativeLibs.getWordTypes().asKind(declaredReturnType);
        if (declaredReturnKind == JavaKind.Boolean) {
            /* Convert the C value to a Java boolean (i.e., 32-bit zero or one). */
            ValueNode comparisonValue = cValue;
            int comparisonBits = bitsInC;
            if (bitsInC < 32) {
                /* For the comparison below, we need at least 32 bits. */
                comparisonValue = graph.unique(new ZeroExtendNode(cValue, 32));
                comparisonBits = 32;
            }

            LogicNode comparison = graph.unique(new IntegerEqualsNode(comparisonValue, ConstantNode.forIntegerBits(comparisonBits, 0, graph)));
            return graph.unique(new ConditionalNode(comparison, ConstantNode.forBoolean(false, graph), ConstantNode.forBoolean(true, graph)));
        }

        /*
         * Narrow the C value to the bits that are actually needed for the method's declared return
         * type.
         */
        ValueNode result = cValue;
        int resultBits = bitsInC;
        if (bitsInC > declaredReturnKind.getBitCount()) {
            result = graph.unique(new NarrowNode(result, declaredReturnKind.getBitCount()));
            resultBits = declaredReturnKind.getBitCount();
        }

        /*
         * Finally, sign- or zero-extend the value. Here, we need to be careful that the result is
         * within the value range of the method's declared return type (e.g., a Java method with
         * return type "byte" may only return values between -128 and +127). If we violated that
         * invariant, we could end up with miscompiled code (e.g., branches could fold away
         * incorrectly).
         *
         * So, we primarily use the signedness of the method's declared return type to decide if we
         * should zero- or sign-extend. There is only one exception: if the C value is unsigned and
         * uses fewer bits than the method's declared return type, then we can safely zero-extend
         * the value (i.e., the unsigned C value will fit into the positive value range of the
         * larger Java type).
         *
         * If the narrowed C value already has the correct number of bits, then the sign- or
         * zero-extension is still necessary. However, it will only adjust the stamp of the value so
         * that it is within the value range of the method's declared return type.
         */
        int actualReturnTypeBitCount = declaredReturnKind.getStackKind().getBitCount();
        assert actualReturnTypeBitCount >= resultBits;

        boolean zeroExtend = shouldZeroExtend(nativeLibs, declaredReturnType, isCValueUnsigned, bitsInC);
        return extend(graph, result, actualReturnTypeBitCount, zeroExtend);
    }

    private static boolean shouldZeroExtend(NativeLibraries nativeLibs, ResolvedJavaType declaredReturnType, boolean isCValueUnsigned, int bitsInC) {
        JavaKind declaredReturnKind = nativeLibs.getWordTypes().asKind(declaredReturnType);
        boolean isDeclaredReturnTypeSigned = nativeLibs.isSigned(declaredReturnType);
        return !isDeclaredReturnTypeSigned || isCValueUnsigned && bitsInC < declaredReturnKind.getBitCount();
    }

    private ValueNode convertCIntegerToMethodReturnType(StructuredGraph graph, AnalysisMethod method, long cValue, int bitsInC, boolean isCValueUnsigned) {
        return convertCIntegerToMethodReturnType(graph, nativeLibs, method.getSignature().getReturnType(), cValue, bitsInC, isCValueUnsigned);
    }

    /**
     * Creates a {@link ConstantNode} and ensures that the stamp matches the declared return type of
     * the method. Similar to the other {@link #convertCIntegerToMethodReturnType} implementation.
     */
    public static ValueNode convertCIntegerToMethodReturnType(StructuredGraph graph, NativeLibraries nativeLibs, ResolvedJavaType declaredReturnType,
                    long cValue, int bitsInC, boolean isCValueUnsigned) {
        JavaKind declaredReturnKind = nativeLibs.getWordTypes().asKind(declaredReturnType);
        if (declaredReturnKind == JavaKind.Boolean) {
            /* Convert the C value to a Java boolean (i.e., 32-bit zero or one). */
            return ConstantNode.forBoolean(cValue != 0, graph);
        }

        long result = convertCIntegerToMethodReturnType(nativeLibs, declaredReturnType, cValue, bitsInC, isCValueUnsigned);
        int resultBits = declaredReturnKind.getStackKind().getBitCount();

        /*
         * Finally, sign- or zero-extend the value. Here, we need to be careful that the stamp of
         * the result is within the value range of the method's declared return type.
         */
        boolean zeroExtend = shouldZeroExtend(nativeLibs, declaredReturnType, isCValueUnsigned, bitsInC);
        if (zeroExtend) {
            IntegerStamp stamp = StampFactory.forUnsignedInteger(resultBits, result, result);
            return ConstantNode.forIntegerStamp(stamp, result, graph);
        }
        return ConstantNode.forIntegerBits(resultBits, result, graph);
    }

    public static Object convertCIntegerToMethodReturnType(NativeLibraries nativeLibs, Class<?> objectReturnType, long cValue, int bitsInC, boolean isCValueUnsigned) {
        ResolvedJavaType declaredReturnType = nativeLibs.getMetaAccess().lookupJavaType(getPrimitiveOrWordClass(nativeLibs, objectReturnType));
        return createReturnObject(objectReturnType, convertCIntegerToMethodReturnType(nativeLibs, declaredReturnType, cValue, bitsInC, isCValueUnsigned));
    }

    private static long convertCIntegerToMethodReturnType(NativeLibraries nativeLibs, ResolvedJavaType declaredReturnType, long cValue, int bitsInC, boolean isCValueUnsigned) {
        JavaKind declaredReturnKind = nativeLibs.getWordTypes().asKind(declaredReturnType);
        if (declaredReturnKind == JavaKind.Boolean) {
            /* Convert the C value to a Java boolean (i.e., zero or one). */
            return cValue != 0L ? 1L : 0L;
        }

        /* Sign- or zero-extend the value. */
        boolean zeroExtend = shouldZeroExtend(nativeLibs, declaredReturnType, isCValueUnsigned, bitsInC);
        int inputBits = Math.min(bitsInC, declaredReturnKind.getBitCount());
        if (zeroExtend) {
            return CodeUtil.zeroExtend(cValue, inputBits);
        }
        return CodeUtil.signExtend(cValue, inputBits);
    }

    private static Class<?> getPrimitiveOrWordClass(NativeLibraries nativeLibs, Class<?> type) {
        if (type == Boolean.class) {
            return boolean.class;
        } else if (type == Byte.class) {
            return byte.class;
        } else if (type == Short.class) {
            return short.class;
        } else if (type == Character.class) {
            return char.class;
        } else if (type == Integer.class) {
            return int.class;
        } else if (type == Long.class) {
            return long.class;
        } else if (type == Float.class) {
            return float.class;
        } else if (type == Double.class) {
            return double.class;
        } else if (nativeLibs.getWordTypes().isWord(type)) {
            return type;
        } else {
            throw VMError.shouldNotReachHere("Unexpected type: " + type);
        }
    }

    private static Object createReturnObject(Class<?> returnType, long value) {
        if (returnType == Boolean.class) {
            return value != 0;
        } else if (returnType == Byte.class) {
            return (byte) value;
        } else if (returnType == Short.class) {
            return (short) value;
        } else if (returnType == Character.class) {
            return (char) value;
        } else if (returnType == Integer.class) {
            return (int) value;
        } else if (returnType == Long.class) {
            return value;
        } else if (WordBase.class.isAssignableFrom(returnType)) {
            return Word.unsigned(value);
        } else {
            throw VMError.shouldNotReachHere("Unexpected returnType: " + returnType.getName());
        }
    }

    private static ValueNode extend(StructuredGraph graph, ValueNode value, int resultBits, boolean zeroExtend) {
        if (zeroExtend) {
            return graph.unique(new ZeroExtendNode(value, resultBits));
        }
        return graph.unique(new SignExtendNode(value, resultBits));
    }

    private boolean replaceConstant(GraphBuilderContext b, AnalysisMethod method, ConstantInfo constantInfo) {
        Object value = constantInfo.getValue();
        JavaKind declaredReturnKind = b.getWordTypes().asKind(b.getInvokeReturnType());
        StructuredGraph graph = b.getGraph();

        ValueNode valueNode = switch (constantInfo.getKind()) {
            case INTEGER, POINTER -> convertCIntegerToMethodReturnType(graph, method, (long) value, constantInfo.getSizeInBytes() * Byte.SIZE, constantInfo.isUnsigned());
            case FLOAT -> ConstantNode.forFloatingKind(declaredReturnKind, (double) value, graph);
            case STRING, BYTEARRAY -> ConstantNode.forConstant(b.getSnippetReflection().forObject(value), b.getMetaAccess(), graph);
            default -> throw shouldNotReachHere("Unexpected constant kind " + constantInfo);
        };
        b.push(pushKind(method), valueNode);
        return true;
    }

    private boolean replaceCFunctionPointerInvoke(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args) {
        if (CFunctionPointerCallStubSupport.singleton().isStub(method)) {
            return false;
        }
        if (!functionPointerType.isAssignableFrom(method.getDeclaringClass())) {
            throw UserError.abort(new CInterfaceError("Function pointer invocation method " + method.format("%H.%n(%p)") +
                            " must be in a type that extends " + CFunctionPointer.class.getSimpleName(), method).getMessage());
        }
        assert b.getInvokeKind() == InvokeKind.Interface;
        AnalysisMethod stub = CFunctionPointerCallStubSupport.singleton().getOrCreateStubForMethod(method);
        b.handleReplacedInvoke(InvokeKind.Static, stub, args, false);
        return true;
    }

    private boolean replaceJavaFunctionPointerInvoke(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args) {
        if (!functionPointerType.isAssignableFrom(method.getDeclaringClass())) {
            throw UserError.abort(new CInterfaceError("Function pointer invocation method " + method.format("%H.%n(%p)") +
                            " must be in a type that extends " + CFunctionPointer.class.getSimpleName(), method).getMessage());
        }
        assert b.getInvokeKind() == InvokeKind.Interface;

        JavaType[] parameterTypes = method.getSignature().toParameterTypes(null);
        // We "discard" the receiver from the signature by pretending we are a static method
        assert args.length >= 1;
        ValueNode methodAddress = args[0];
        ValueNode[] argsWithoutReceiver = Arrays.copyOfRange(args, 1, args.length);
        assert argsWithoutReceiver.length == parameterTypes.length;

        Stamp returnStamp;
        if (b.getWordTypes().isWord(b.getInvokeReturnType())) {
            returnStamp = b.getWordTypes().getWordStamp((AnalysisType) b.getInvokeReturnType());
        } else {
            returnStamp = b.getInvokeReturnStamp(null).getTrustedStamp();
        }
        CallTargetNode indirectCallTargetNode = b.add(new SubstrateIndirectCallTargetNode(methodAddress, argsWithoutReceiver,
                        StampPair.createSingle(returnStamp), parameterTypes, null, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static));

        b.handleReplacedInvoke(indirectCallTargetNode, b.getInvokeReturnType().getJavaKind());
        return true;
    }

    public static JavaKind pushKind(AnalysisMethod method) {
        return method.getSignature().getReturnKind().getStackKind();
    }
}
