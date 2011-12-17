/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

import java.util.*;

import com.oracle.max.graal.compiler.graphbuilder.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Represents a method signature.
 */
public class HotSpotSignature extends CompilerObject implements RiSignature {

    private final List<String> arguments = new ArrayList<String>();
    private final String returnType;
    private final String originalString;
    private RiType[] argumentTypes;
    private RiType returnTypeCache;

    public HotSpotSignature(Compiler compiler, String signature) {
        super(compiler);
        assert signature.length() > 0;
        this.originalString = signature;

        if (signature.charAt(0) == '(') {
            int cur = 1;
            while (cur < signature.length() && signature.charAt(cur) != ')') {
                int nextCur = parseSignature(signature, cur);
                arguments.add(signature.substring(cur, nextCur));
                cur = nextCur;
            }

            cur++;
            int nextCur = parseSignature(signature, cur);
            returnType = signature.substring(cur, nextCur);
            assert nextCur == signature.length();
        } else {
            returnType = null;
        }
    }

    private int parseSignature(String signature, int cur) {
        char first;
        do {
            first = signature.charAt(cur++);
        } while (first == '[');

        switch (first) {
            case 'L':
                while (signature.charAt(cur) != ';') {
                    cur++;
                }
                cur++;
                break;
            case 'V':
            case 'I':
            case 'B':
            case 'C':
            case 'D':
            case 'F':
            case 'J':
            case 'S':
            case 'Z':
                break;
            default:
                assert false;
        }
        return cur;
    }

    @Override
    public int argumentCount(boolean withReceiver) {
        return arguments.size() + (withReceiver ? 1 : 0);
    }

    @Override
    public CiKind argumentKindAt(int index, boolean architecture) {
        return CiKind.fromTypeString(arguments.get(index));
    }

    @Override
    public int argumentSlots(boolean withReceiver) {
        int argSlots = 0;
        for (int i = 0; i < argumentCount(false); i++) {
            argSlots += FrameStateBuilder.stackSlots(argumentKindAt(i, false));
        }
        return argSlots + (withReceiver ? 1 : 0);
    }

    @Override
    public RiType argumentTypeAt(int index, RiType accessingClass) {
        if (argumentTypes == null) {
            argumentTypes = new RiType[arguments.size()];
        }
        RiType type = argumentTypes[index];
        if (type == null) {
            type = compiler.lookupType(arguments.get(index), (HotSpotTypeResolved) accessingClass);
            argumentTypes[index] = type;
        }
        return type;
    }

    @Override
    public String asString() {
        return originalString;
    }

    @Override
    public CiKind returnKind(boolean architecture) {
        return CiKind.fromTypeString(returnType);
    }

    @Override
    public RiType returnType(RiType accessingClass) {
        if (returnTypeCache == null) {
            returnTypeCache = compiler.lookupType(returnType, (HotSpotTypeResolved) accessingClass);
        }
        return returnTypeCache;
    }

    @Override
    public String toString() {
        return "HotSpotSignature<" + originalString + ">";
    }

}
