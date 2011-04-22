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

import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Implementation of RiType for unresolved HotSpot classes.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class HotSpotTypeUnresolved extends HotSpotType {

    public final String simpleName;
    public final int dimensions;

    /**
     * Creates a new unresolved type for a specified type descriptor.
     */
    public HotSpotTypeUnresolved(Compiler compiler, String name) {
        super(compiler);
        assert name.length() > 0 : "name cannot be empty";

        int dimensions = 0;
        // Decode name if necessary.
        if (name.charAt(name.length() - 1) == ';') {
            int startIndex = 0;
            while (name.charAt(startIndex) == '[') {
                startIndex++;
                dimensions++;
            }
            assert name.charAt(startIndex) == 'L';
            this.simpleName = name.substring(startIndex + 1, name.length() - 1);
            this.name = name;
        } else {
            this.simpleName = name;
            this.name = getFullName(name, dimensions);
        }

        this.dimensions = dimensions;
    }

    public HotSpotTypeUnresolved(Compiler compiler, String name, int dimensions) {
        super(compiler);
        assert dimensions >= 0;
        this.simpleName = name;
        this.dimensions = dimensions;
        this.name = getFullName(name, dimensions);
    }

    private String getFullName(String name, int dimensions) {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < dimensions; i++) {
            str.append('[');
        }
        str.append('L').append(simpleName).append(';');
        return str.toString();
    }

    @Override
    public RiType uniqueConcreteSubtype() {
        throw unresolved("uniqueConcreteSubtype");
    }

    @Override
    public Class<?> javaClass() {
        throw unresolved("javaClass");
    }

    @Override
    public boolean hasSubclass() {
        throw unresolved("hasSubclass()");
    }

    @Override
    public boolean hasFinalizer() {
        throw unresolved("hasFinalizer()");
    }

    @Override
    public boolean hasFinalizableSubclass() {
        throw unresolved("hasFinalizableSubclass()");
    }

    @Override
    public boolean isInterface() {
        throw unresolved("isInterface()");
    }

    @Override
    public boolean isArrayClass() {
        return dimensions > 0;
    }

    @Override
    public boolean isInstanceClass() {
        throw unresolved("isInstanceClass()");
    }

    @Override
    public int accessFlags() {
        throw unresolved("accessFlags()");
    }

    @Override
    public boolean isResolved() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        throw unresolved("isInitialized()");
    }

    @Override
    public boolean isSubtypeOf(RiType other) {
        throw unresolved("isSubtypeOf()");
    }

    @Override
    public boolean isInstance(CiConstant obj) {
        throw unresolved("isInstance()");
    }

    @Override
    public RiType componentType() {
        assert isArrayClass() : "no array class" + name();
        return new HotSpotTypeUnresolved(compiler, simpleName, dimensions - 1);
    }

    @Override
    public RiType exactType() {
        throw unresolved("exactType()");
    }

    @Override
    public RiType superType() {
        throw unresolved("superType()");
    }

    @Override
    public RiType arrayOf() {
        return new HotSpotTypeUnresolved(compiler, simpleName, dimensions + 1);
    }

    @Override
    public RiMethod resolveMethodImpl(RiMethod method) {
        throw unresolved("resolveMethodImpl()");
    }

    @Override
    public CiKind kind() {
        return CiKind.Object;
    }

    private CiUnresolvedException unresolved(String operation) {
        throw new CiUnresolvedException(operation + " not defined for unresolved class " + simpleName);
    }

    @Override
    public int hashCode() {
        return simpleName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    @Override
    public String toString() {
        return "HotSpotType<" + simpleName + ", unresolved>";
    }

    @Override
    public CiConstant getEncoding(RiType.Representation r) {
        throw unresolved("getEncoding()");
    }

    @Override
    public CiKind getRepresentationKind(RiType.Representation r) {
        return CiKind.Object;
    }

}
