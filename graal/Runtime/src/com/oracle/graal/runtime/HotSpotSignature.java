/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Represents a method signature.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
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
    public CiKind argumentKindAt(int index) {
        return CiKind.fromTypeString(arguments.get(index));
    }

    @Override
    public int argumentSlots(boolean withReceiver) {

        int argSlots = 0;
        for (int i = 0; i < argumentCount(false); i++) {
            argSlots += argumentKindAt(i).sizeInSlots();
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
            type = compiler.getVMEntries().RiSignature_lookupType(arguments.get(index), (HotSpotTypeResolved) accessingClass);
            argumentTypes[index] = type;
        }
        return type;
    }

    @Override
    public String asString() {
        return originalString;
    }

    @Override
    public CiKind returnKind() {
        return CiKind.fromTypeString(returnType);
    }

    @Override
    public RiType returnType(RiType accessingClass) {
        if (returnTypeCache == null) {
            returnTypeCache = compiler.getVMEntries().RiSignature_lookupType(returnType, (HotSpotTypeResolved) accessingClass);
        }
        return returnTypeCache;
    }

    @Override
    public String toString() {
        return "HotSpotSignature<" + originalString + ">";
    }

}
