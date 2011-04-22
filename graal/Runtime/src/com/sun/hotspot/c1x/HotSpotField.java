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

package com.sun.hotspot.c1x;

import java.lang.reflect.*;

import com.sun.c1x.*;
import com.sun.cri.ci.CiConstant;
import com.sun.cri.ci.CiKind;
import com.sun.cri.ri.RiField;
import com.sun.cri.ri.RiType;

/**
 * Represents a field in a HotSpot type.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class HotSpotField extends CompilerObject implements RiField {

    private final RiType holder;
    private final String name;
    private final RiType type;
    private final int offset;
    private CiConstant constant;

    public HotSpotField(Compiler compiler, RiType holder, String name, RiType type, int offset) {
        super(compiler);
        this.holder = holder;
        this.name = name;
        this.type = type;
        this.offset = offset;
    }

    @Override
    public int accessFlags() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public CiConstant constantValue(CiConstant receiver) {
        if (receiver == null) {
            if (constant == null && holder.isResolved() && holder.isSubtypeOf(compiler.getVMEntries().getType(C1XOptions.class))) {
                Field f;
                try {
                    f = C1XOptions.class.getField(name);
                } catch (SecurityException e1) {
                    return null;
                } catch (NoSuchFieldException e1) {
                    return null;
                }
                f.setAccessible(true);
                if (Modifier.isStatic(f.getModifiers())) {
                    CiKind kind = CiKind.fromJavaClass(f.getType());
                    Object value;
                    try {
                        value = f.get(null);
                    } catch (IllegalArgumentException e) {
                        return null;
                    } catch (IllegalAccessException e) {
                        return null;
                    }
                    constant = CiConstant.forBoxed(kind, value);
                }
            }

            // Constant part only valid for static fields.
            return constant;
        }
        return null;
    }

    @Override
    public RiType holder() {
        return holder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HotSpotField) {
            HotSpotField other = (HotSpotField) obj;
            return other.offset == offset && other.holder.equals(holder());
        }
        return false;
    }

    @Override
    public boolean isResolved() {
        return offset != -1;
    }

    @Override
    public CiKind kind() {
        return type().kind();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public RiType type() {
        return type;
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "HotSpotField<" + holder.name() + "." + name + ">";
    }

}
