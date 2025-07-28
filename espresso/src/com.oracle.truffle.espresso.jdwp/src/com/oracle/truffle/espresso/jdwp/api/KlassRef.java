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
 * Interface representing a class object in the VM.
 */
public interface KlassRef {

    /**
     * Determines if the class is an array.
     * 
     * @return true if class is an array, false otherwise
     */
    boolean isArray();

    /**
     * Determines if the underlying class is an interface.
     * 
     * @return true if class is an interface, false otherwise
     */
    boolean isInterface();

    /**
     * Returns a String representation of the name of the class.
     * 
     * @return the name of the class
     */
    String getNameAsString();

    /**
     * Returns the String representation of the type of the class.
     * 
     * @return the class type descriptor name
     */
    String getTypeAsString();

    /**
     * Returns the String representation of the generic type of the class.
     *
     * @return the generic class type descriptor name
     */
    String getGenericTypeAsString();

    /**
     * Returns all declared methods of the class.
     * 
     * @return array of MethodRef
     */
    MethodRef[] getDeclaredMethods();

    /**
     * Returns a guest-language representation of the classloader for which loaded the class.
     * 
     * @return the class loader object
     */
    Object getDefiningClassLoader();

    /**
     * Returns all declared fields of the class.
     * 
     * @return array of FieldRef
     */
    FieldRef[] getDeclaredFields();

    /**
     * Returns all direct implemented interfaces for the class. Note that interfaces inherited from
     * a super type is not included in the returned array.
     * 
     * @return the array of implemented interfaces
     */
    KlassRef[] getImplementedInterfaces();

    /**
     * @return the status according to ClassStatusConstants of the class
     */
    int getStatus();

    /**
     * @return the immediate super class of the class
     */
    KlassRef getSuperClass();

    /**
     * Returns the TypeTag constant as defined in the JDWP protocol. See
     * https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_TypeTag
     * 
     * @return the TypeTag constant
     */
    byte getTagConstant();

    /**
     * Determines if the class is a class for a primitive type.
     * 
     * @return true if class is primitive, false otherwise.
     */
    boolean isPrimitive();

    /**
     * Returns the guest-language object representing the thread that was running when the class was
     * prepared within the VM.
     * 
     * @return the guest-language object of the thread
     */
    Object getPrepareThread();

    /**
     * Determines if the input this klass is assignable from the given input klass.
     * 
     * @param klass
     * @return true if this klass is assignable from the input klass
     */
    boolean isAssignable(KlassRef klass);

    /**
     * Returns the object representing this klass type.
     * 
     * @return guest language object for the klass type
     */
    Object getKlassObject();

    /**
     * Returns the modifiers of this klass.
     * 
     * @return klass modifier bitmask.
     */
    int getModifiers();

    /**
     * Returns the array klass for this klass with the given dimensions iff the array type was
     * loaded.
     *
     * @param dimensions array dimension
     * @return array klass
     */
    KlassRef getArrayClassNoCreate(int dimensions);

    /**
     * Returns the major version of the corresponding class file for this klass.
     * 
     * @return the major class file version
     */
    int getMajorVersion();

    /**
     * Returns the minor version of the corresponding class file for this klass.
     *
     * @return the minor class file version
     */
    int getMinorVersion();

    /**
     * Returns the constant pool as specified in the Class File Format.
     *
     * @return a representation of the constant pool
     */
    JDWPConstantPool getJDWPConstantPool();

    /**
     * Returns the source debug extension class file attribute value or <code>null</code>.
     *
     * @return the extension
     */
    String getSourceDebugExtension();

    /**
     * Returns the Module reference of the class.
     *
     * @return the module reference
     */
    ModuleRef module();
}
