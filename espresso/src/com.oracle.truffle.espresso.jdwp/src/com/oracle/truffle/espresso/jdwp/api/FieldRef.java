/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.api;

/**
 * Interface representing a Field in a running program.
 */
public interface FieldRef {

    /**
     * Returns the TypeTag constant as defined in the JDWP protocol. See
     * https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_TypeTag
     * 
     * @return the TypeTag constant
     */
    byte getTagConstant();

    /**
     * Returns a String representation of the name of the field.
     * 
     * @return the field name
     */
    String getNameAsString();

    /**
     * Returns the String representation of the type of the field according to the field descriptor
     * grammar. See https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
     * 
     * @return the field type descriptor name
     */
    String getTypeAsString();

    /**
     * Returns the generic String representation of the type of the field according to the field.
     * 
     * @return the field type descriptor name including generics information
     */
    String getGenericSignatureAsString();

    /**
     * Returns the modifiers mask of the field.
     * 
     * @return the field modifiers
     */
    int getModifiers();

    /**
     * Returns the declaring class of the field.
     * 
     * @return the {@link KlassRef} which declared this field
     */
    KlassRef getDeclaringKlass();

    /**
     * Gets the field value for a specified object.
     * 
     * @param self the object (or class/null) for which to read the field value from.
     * @return the value of the field
     */
    Object getValue(Object self);

    /**
     * Sets the value of the field on the specified object.
     * 
     * @param self the object (or class/null) for which to read the field value from.
     * @param value the value to set
     */
    void setValue(Object self, Object value);

    /**
     * Returns all information about potential field breakpoints set on this field.
     * 
     * @return array of field breakpoint info
     */
    FieldBreakpoint[] getFieldBreakpointInfos();

    /**
     * Add a new field breakpoint with the given info on this field.
     * 
     * @param info the info that describes the breakpoint
     */
    void addFieldBreakpointInfo(FieldBreakpoint info);

    /**
     * Remove a field breakpoint with the given info on this field.
     * 
     * @param requestId the ID for the request that set the breakpoint
     */
    void removeFieldBreakpointInfo(int requestId);

    /**
     * Determines if there are any breakpoints set on this field.
     * 
     * @return true if this field has any breakpoints, false otherwise
     */
    boolean hasActiveBreakpoint();
}
