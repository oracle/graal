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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.word.LocationIdentity;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.c.struct.CInterfaceLocationIdentity;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.nodes.CInterfaceReadNode;
import com.oracle.svm.core.graal.nodes.CInterfaceWriteNode;
import com.oracle.svm.core.util.UserError;
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

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.AndNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.OrNode;
import jdk.graal.compiler.nodes.calc.RightShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.extended.JavaReadNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
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

    private static boolean replaceAccessor(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, AccessorInfo accessorInfo, int displacement) {
        StructuredGraph graph = b.getGraph();
        SizableInfo sizableInfo = (SizableInfo) accessorInfo.getParent();
        int elementSize = sizableInfo.getSizeInfo().getProperty();
        boolean isUnsigned = sizableInfo.isUnsigned();
        boolean isPinnedObject = sizableInfo.isObject();

        assert args.length == accessorInfo.parameterCount(true);

        ValueNode base = args[AccessorInfo.baseParameterNumber(true)];
        assert base.getStackKind() == ConfigurationValues.getWordKind();

        switch (accessorInfo.getAccessorKind()) {
            case ADDRESS: {
                ValueNode address = graph.addOrUniqueWithInputs(new AddNode(base, makeOffset(graph, args, accessorInfo, displacement, elementSize)));
                b.addPush(pushKind(method), address);
                return true;
            }
            case GETTER: {
                JavaKind resultKind = b.getWordTypes().asKind(b.getInvokeReturnType());
                JavaKind readKind = kindFromSize(elementSize, resultKind);
                if (readKind == JavaKind.Object) {
                    assert resultKind == JavaKind.Object;
                } else if (readKind.getBitCount() > resultKind.getBitCount() && !readKind.isNumericFloat() && resultKind != JavaKind.Boolean) {
                    readKind = resultKind;
                }
                OffsetAddressNode offsetAddress = makeOffsetAddress(graph, args, accessorInfo, base, displacement, elementSize);
                LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);
                final Stamp stamp;
                if (readKind == JavaKind.Object) {
                    stamp = b.getInvokeReturnStamp(null).getTrustedStamp();
                } else if (readKind == JavaKind.Float || readKind == JavaKind.Double) {
                    stamp = StampFactory.forKind(readKind);
                } else {
                    stamp = IntegerStamp.create(readKind.getBitCount());
                }
                final ValueNode node;
                if (isPinnedObject) {
                    node = b.add(new JavaReadNode(stamp, readKind, offsetAddress, locationIdentity, BarrierType.NONE, MemoryOrderMode.PLAIN, true));
                } else {
                    ValueNode read = readPrimitive(b, offsetAddress, locationIdentity, stamp, accessorInfo, method);
                    node = adaptPrimitiveType(graph, read, readKind, resultKind == JavaKind.Boolean ? resultKind : resultKind.getStackKind(), isUnsigned);
                }
                b.push(pushKind(method), node);
                return true;
            }
            case SETTER: {
                ValueNode value = args[accessorInfo.valueParameterNumber(true)];
                JavaKind valueKind = value.getStackKind();
                JavaKind writeKind = kindFromSize(elementSize, valueKind);
                OffsetAddressNode offsetAddress = makeOffsetAddress(graph, args, accessorInfo, base, displacement, elementSize);
                LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);
                if (isPinnedObject) {
                    b.add(new JavaWriteNode(writeKind, offsetAddress, locationIdentity, value, BarrierType.NONE, true));
                } else {
                    ValueNode adaptedValue = adaptPrimitiveType(graph, value, valueKind, writeKind, isUnsigned);
                    writePrimitive(b, offsetAddress, locationIdentity, adaptedValue, accessorInfo, method);
                }
                return true;
            }
            default:
                throw shouldNotReachHereUnexpectedInput(accessorInfo.getAccessorKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static boolean replaceBitfieldAccessor(GraphBuilderContext b, AnalysisMethod method, ValueNode[] args, StructBitfieldInfo bitfieldInfo, AccessorInfo accessorInfo) {
        int byteOffset = bitfieldInfo.getByteOffsetInfo().getProperty();
        int startBit = bitfieldInfo.getStartBitInfo().getProperty();
        int endBit = bitfieldInfo.getEndBitInfo().getProperty();
        boolean isUnsigned = bitfieldInfo.isUnsigned();
        assert byteOffset >= 0 && byteOffset < ((SizableInfo) bitfieldInfo.getParent()).getSizeInfo().getProperty();
        assert startBit >= 0 && startBit < 8;
        assert endBit >= startBit && endBit < 64;

        /*
         * The startBit is always in the first byte. Therefore, the endBit tells us how many bytes
         * we actually have to read and write.
         */
        JavaKind memoryKind;
        if (endBit < 8) {
            memoryKind = JavaKind.Byte;
        } else if (endBit < 16) {
            memoryKind = JavaKind.Short;
        } else if (endBit < 32) {
            memoryKind = JavaKind.Int;
        } else {
            memoryKind = JavaKind.Long;
        }
        int numBytes = memoryKind.getByteCount();

        /*
         * Try to align the byteOffset to be a multiple of numBytes. That should always be possible,
         * but we don't trust the C compiler and memory layout enough to make it an assertion.
         */
        int alignmentCorrection = byteOffset % numBytes;
        if (alignmentCorrection > 0 && endBit + alignmentCorrection * 8 < numBytes * 8) {
            byteOffset -= alignmentCorrection;
            startBit += alignmentCorrection * 8;
            endBit += alignmentCorrection * 8;
        }
        assert byteOffset >= 0 && byteOffset < ((SizableInfo) bitfieldInfo.getParent()).getSizeInfo().getProperty();
        assert startBit >= 0 && startBit < numBytes * 8;
        assert endBit >= startBit && endBit < numBytes * 8;

        int numBits = endBit - startBit + 1;
        assert numBits > 0 && numBits <= numBytes * 8;

        /*
         * The bit-operations on the value are either performed on Int or Long. We do not perform 8
         * or 16 bit arithmetic operations.
         */
        JavaKind computeKind = memoryKind.getStackKind();
        Stamp computeStamp = StampFactory.forKind(computeKind);
        int computeBits = computeKind.getBitCount();
        assert startBit >= 0 && startBit < computeBits;
        assert endBit >= startBit && endBit < computeBits;
        assert computeBits >= numBits;

        assert args.length == accessorInfo.parameterCount(true);
        ValueNode base = args[AccessorInfo.baseParameterNumber(true)];
        StructuredGraph graph = b.getGraph();
        /*
         * Read the memory location. This is also necessary for writes, since we need to keep the
         * bits around the written bitfield unchanged.
         */
        OffsetAddressNode address = makeOffsetAddress(graph, args, accessorInfo, base, byteOffset, -1);
        LocationIdentity locationIdentity = makeLocationIdentity(b, method, args, accessorInfo);
        Stamp stamp = IntegerStamp.create(memoryKind.getBitCount());
        ValueNode cur = readPrimitive(b, address, locationIdentity, stamp, accessorInfo, method);
        cur = adaptPrimitiveType(graph, cur, memoryKind, computeKind, true);

        switch (accessorInfo.getAccessorKind()) {
            case GETTER: {
                if (isUnsigned) {
                    /*
                     * Unsigned reads: shift the bitfield to the right and mask out the unnecessary
                     * high-order bits.
                     */
                    cur = graph.unique(new RightShiftNode(cur, ConstantNode.forInt(startBit, graph)));
                    cur = graph.unique(new AndNode(cur, ConstantNode.forIntegerStamp(computeStamp, (1L << numBits) - 1, graph)));
                } else {
                    /*
                     * Signed reads: shift the bitfield to the right end to get the sign bit in
                     * place, then do a signed left shift to have a proper sign extension.
                     */
                    cur = graph.unique(new LeftShiftNode(cur, ConstantNode.forInt(computeBits - endBit - 1, graph)));
                    cur = graph.unique(new RightShiftNode(cur, ConstantNode.forInt(computeBits - numBits, graph)));
                }

                JavaKind resultKind = b.getWordTypes().asKind(b.getInvokeReturnType());
                b.push(pushKind(method), adaptPrimitiveType(graph, cur, computeKind, resultKind == JavaKind.Boolean ? resultKind : resultKind.getStackKind(), isUnsigned));
                return true;
            }
            case SETTER: {
                /* Zero out the bits of our bitfields, i.e., the bits we are going to change. */
                long mask = ~(((1L << numBits) - 1) << startBit);
                cur = graph.unique(new AndNode(cur, ConstantNode.forIntegerStamp(computeStamp, mask, graph)));

                /*
                 * Mask the unnecessary high-order bits of the value to be written, and shift it to
                 * its place.
                 */
                ValueNode value = args[accessorInfo.valueParameterNumber(true)];
                value = adaptPrimitiveType(graph, value, value.getStackKind(), computeKind, isUnsigned);
                value = graph.unique(new AndNode(value, ConstantNode.forIntegerStamp(computeStamp, (1L << numBits) - 1, graph)));
                value = graph.unique(new LeftShiftNode(value, ConstantNode.forInt(startBit, graph)));

                /* Combine the leftover bits of the original memory word with the new value. */
                cur = graph.unique(new OrNode(cur, value));

                /* Narrow value to the number of bits we need to write. */
                cur = adaptPrimitiveType(graph, cur, computeKind, memoryKind, true);
                /* Perform the write (bitcount is taken from the stamp of the written value). */
                writePrimitive(b, address, locationIdentity, cur, accessorInfo, method);
                return true;
            }
            default:
                throw shouldNotReachHereUnexpectedInput(accessorInfo.getAccessorKind()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static ValueNode readPrimitive(GraphBuilderContext b, OffsetAddressNode address, LocationIdentity locationIdentity, Stamp stamp, AccessorInfo accessorInfo, ResolvedJavaMethod method) {
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

    private static void writePrimitive(GraphBuilderContext b, OffsetAddressNode address, LocationIdentity locationIdentity, ValueNode value, AccessorInfo accessorInfo, ResolvedJavaMethod method) {
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
            ValueNode wordIndex = adaptPrimitiveType(graph, index, index.getStackKind(), ConfigurationValues.getWordKind(), false);
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

    public static ValueNode adaptPrimitiveType(StructuredGraph graph, ValueNode value, JavaKind fromKind, JavaKind toKind, boolean isUnsigned) {
        if (fromKind == toKind) {
            return value;
        }
        assert fromKind.isNumericFloat() == toKind.isNumericFloat();

        int fromBits = fromKind.getBitCount();
        int toBits = toKind.getBitCount();

        if (fromBits == toBits) {
            return value;
        } else if (fromKind.isNumericFloat()) {
            FloatConvert op;
            if (fromKind == JavaKind.Float && toKind == JavaKind.Double) {
                op = FloatConvert.F2D;
            } else if (fromKind == JavaKind.Double && toKind == JavaKind.Float) {
                op = FloatConvert.D2F;
            } else {
                throw shouldNotReachHereUnexpectedInput(fromKind); // ExcludeFromJacocoGeneratedReport
            }
            return graph.unique(new FloatConvertNode(op, value));
        } else if (toKind == JavaKind.Boolean) {
            JavaKind computeKind = fromKind == JavaKind.Long ? JavaKind.Long : JavaKind.Int;
            LogicNode comparison = graph.unique(new IntegerEqualsNode(adaptPrimitiveType(graph, value, fromKind, computeKind, true), ConstantNode.forIntegerKind(computeKind, 0, graph)));
            return graph.unique(new ConditionalNode(comparison, ConstantNode.forBoolean(false, graph), ConstantNode.forBoolean(true, graph)));
        } else if (fromBits > toBits) {
            return graph.unique(new NarrowNode(value, toBits));
        } else if (isUnsigned) {
            return graph.unique(new ZeroExtendNode(value, toBits));
        } else {
            return graph.unique(new SignExtendNode(value, toBits));
        }
    }

    private static JavaKind kindFromSize(int sizeInBytes, JavaKind matchingKind) {
        if (matchingKind == JavaKind.Object || sizeInBytes * 8 == matchingKind.getBitCount()) {
            /* Out preferred matching kind fits, so we can use it. */
            return matchingKind;
        }

        if (matchingKind == JavaKind.Float || matchingKind == JavaKind.Double) {
            switch (sizeInBytes) {
                case 4:
                    return JavaKind.Float;
                case 8:
                    return JavaKind.Double;
            }
        } else {
            switch (sizeInBytes) {
                case 1:
                    return JavaKind.Byte;
                case 2:
                    return JavaKind.Short;
                case 4:
                    return JavaKind.Int;
                case 8:
                    return JavaKind.Long;
            }
        }
        throw shouldNotReachHere("Unsupported size: " + sizeInBytes);
    }

    private static boolean replaceConstant(GraphBuilderContext b, AnalysisMethod method, ConstantInfo constantInfo) {
        Object value = constantInfo.getValueInfo().getProperty();
        JavaKind kind = b.getWordTypes().asKind(b.getInvokeReturnType());

        ConstantNode valueNode;
        switch (constantInfo.getKind()) {
            case INTEGER:
            case POINTER:
                if (method.getSignature().getReturnKind() == JavaKind.Boolean) {
                    valueNode = ConstantNode.forBoolean((long) value != 0, b.getGraph());
                } else {
                    valueNode = ConstantNode.forIntegerKind(kind, (long) value, b.getGraph());
                }
                break;
            case FLOAT:
                valueNode = ConstantNode.forFloatingKind(kind, (double) value, b.getGraph());
                break;
            case STRING:
            case BYTEARRAY:
                valueNode = ConstantNode.forConstant(b.getSnippetReflection().forObject(value), b.getMetaAccess(), b.getGraph());
                break;
            default:
                throw shouldNotReachHere("Unexpected constant kind " + constantInfo);
        }
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
        CallTargetNode indirectCallTargetNode = b.add(new IndirectCallTargetNode(methodAddress, argsWithoutReceiver,
                        StampPair.createSingle(returnStamp), parameterTypes, null, SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static));

        b.handleReplacedInvoke(indirectCallTargetNode, b.getInvokeReturnType().getJavaKind());
        return true;
    }

    public static JavaKind pushKind(AnalysisMethod method) {
        return method.getSignature().getReturnKind().getStackKind();
    }
}
