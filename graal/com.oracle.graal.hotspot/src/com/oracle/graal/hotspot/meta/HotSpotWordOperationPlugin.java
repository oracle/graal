/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_EQ;
import static com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode.POINTER_NE;
import static com.oracle.graal.nodes.ConstantNode.forBoolean;
import static jdk.vm.ci.meta.LocationIdentity.any;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LocationIdentity;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import com.oracle.graal.api.replacements.SnippetReflectionProvider;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.hotspot.nodes.LoadIndexedPointerNode;
import com.oracle.graal.hotspot.nodes.type.KlassPointerStamp;
import com.oracle.graal.hotspot.nodes.type.MetaspacePointerStamp;
import com.oracle.graal.hotspot.nodes.type.MethodPointerStamp;
import com.oracle.graal.hotspot.word.HotSpotOperation;
import com.oracle.graal.hotspot.word.HotSpotOperation.HotspotOpcode;
import com.oracle.graal.hotspot.word.PointerCastNode;
import com.oracle.graal.nodes.AbstractBeginNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.ConditionalNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.calc.PointerEqualsNode;
import com.oracle.graal.nodes.java.LoadIndexedNode;
import com.oracle.graal.nodes.memory.HeapAccess.BarrierType;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.address.AddressNode;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.replacements.WordOperationPlugin;
import com.oracle.graal.word.WordTypes;

/**
 * Extends {@link WordOperationPlugin} to handle {@linkplain HotSpotOperation HotSpot word
 * operations}.
 */
class HotSpotWordOperationPlugin extends WordOperationPlugin {
    public HotSpotWordOperationPlugin(SnippetReflectionProvider snippetReflection, WordTypes wordTypes) {
        super(snippetReflection, wordTypes);
    }

    @Override
    protected LoadIndexedNode createLoadIndexedNode(ValueNode array, ValueNode index) {
        ResolvedJavaType arrayType = StampTool.typeOrNull(array);
        Stamp componentStamp = wordTypes.getWordStamp(arrayType.getComponentType());
        if (componentStamp instanceof MetaspacePointerStamp) {
            return new LoadIndexedPointerNode(componentStamp, array, index);
        } else {
            return super.createLoadIndexedNode(array, index);
        }
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (!wordTypes.isWordOperation(method)) {
            return false;
        }

        HotSpotOperation operation = method.getAnnotation(HotSpotOperation.class);
        if (operation == null) {
            processWordOperation(b, args, wordTypes.getWordOperation(method, b.getMethod().getDeclaringClass()));
            return true;
        }
        processHotSpotWordOperation(b, method, args, operation);
        return true;
    }

    protected void processHotSpotWordOperation(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args, HotSpotOperation operation) {
        JavaKind returnKind = method.getSignature().getReturnKind();
        switch (operation.opcode()) {
            case POINTER_EQ:
            case POINTER_NE:
                assert args.length == 2;
                HotspotOpcode opcode = operation.opcode();
                ValueNode left = args[0];
                ValueNode right = args[1];
                assert left.stamp() instanceof MetaspacePointerStamp : left + " " + left.stamp();
                assert right.stamp() instanceof MetaspacePointerStamp : right + " " + right.stamp();
                assert opcode == POINTER_EQ || opcode == POINTER_NE;

                PointerEqualsNode comparison = b.add(new PointerEqualsNode(left, right));
                ValueNode eqValue = b.add(forBoolean(opcode == POINTER_EQ));
                ValueNode neValue = b.add(forBoolean(opcode == POINTER_NE));
                b.addPush(returnKind, new ConditionalNode(comparison, eqValue, neValue));
                break;

            case IS_NULL:
                assert args.length == 1;
                ValueNode pointer = args[0];
                assert pointer.stamp() instanceof MetaspacePointerStamp;

                IsNullNode isNull = b.add(new IsNullNode(pointer));
                b.addPush(returnKind, new ConditionalNode(isNull, b.add(forBoolean(true)), b.add(forBoolean(false))));
                break;

            case FROM_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(StampFactory.forKind(wordKind), args[0]));
                break;

            case TO_KLASS_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(KlassPointerStamp.klass(), args[0]));
                break;

            case TO_METHOD_POINTER:
                assert args.length == 1;
                b.addPush(returnKind, new PointerCastNode(MethodPointerStamp.method(), args[0]));
                break;

            case READ_KLASS_POINTER:
                assert args.length == 2 || args.length == 3;
                Stamp readStamp = KlassPointerStamp.klass();
                AddressNode address = makeAddress(b, args[0], args[1]);
                LocationIdentity location;
                if (args.length == 2) {
                    location = any();
                } else {
                    assert args[2].isConstant();
                    location = snippetReflection.asObject(LocationIdentity.class, args[2].asJavaConstant());
                }
                ReadNode read = b.add(new ReadNode(address, location, readStamp, BarrierType.NONE));
                /*
                 * The read must not float outside its block otherwise it may float above an
                 * explicit zero check on its base address.
                 */
                read.setGuard(AbstractBeginNode.prevBegin(read));
                b.push(returnKind, read);
                break;

            default:
                throw JVMCIError.shouldNotReachHere("unknown operation: " + operation.opcode());
        }
    }
}
