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

enum TemplateFlag {
    NULL_CHECK, UNRESOLVED, READ_BARRIER, WRITE_BARRIER, STORE_CHECK, BOUNDS_CHECK, GIVEN_LENGTH, INPUTS_DIFFERENT, INPUTS_SAME, STATIC_METHOD, SYNCHRONIZED;

    private static final long FIRST_FLAG = 0x0000000100000000L;
    public static final long FLAGS_MASK = 0x0000FFFF00000000L;
    public static final long INDEX_MASK = 0x00000000FFFFFFFFL;

    public long bits() {
        assert ((FIRST_FLAG << ordinal()) & FLAGS_MASK) != 0;
        return FIRST_FLAG << ordinal();
    }
}
