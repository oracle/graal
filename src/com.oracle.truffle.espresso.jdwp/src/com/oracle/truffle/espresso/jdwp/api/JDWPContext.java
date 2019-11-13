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

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.jdwp.impl.JDWPCallFrame;

/**
 * Interface that defines required methods for a guest language
 * when implementing JDWP.
 */
public interface JDWPContext {

    /**
     * @return the Truffle language environment.
     */
    TruffleLanguage.Env getEnv();

    /**
     * Rerturns the guest language representation of a host thread
     * @param hostThread
     * @return guest language thread
     */
    Object getHost2GuestThread(Thread hostThread);

    /**
     * Returns the host thread corresponding to the guest language thread
     * @param thread guest language thread
     * @return host language thread
     */
    Thread getGuest2HostThread(Object thread);

    /**
     * Returns the special KlassRef object representing the null class
     * @return the null class
     */
    KlassRef getNullKlass();

    /**
     * Finds a klasses loaded under the given name.
     * @param slashName name of the class
     * @return an array of all classes loaded with the given name
     */
    KlassRef[] findLoadedClass(String slashName);

    /**
     * Returns all loaded classes by the VM
     * @return array containing every class loaded
     */
    KlassRef[] getAllLoadedClasses();

    /**
     * @return the virtual machine representation
     */
    JDWPVirtualMachine getVirtualMachine();

    /**
     * Finds the klass for which an root node was created from.
     * @param root the Truffle root node object
     * @return the declaring class of the root node
     */
    KlassRef getKlassFromRootNode(RootNode root);

    /**
     * Finds the method for which an root node was created from.
     * @param root the Truffle root node object
     * @return the declaring method of the root node
     */
    MethodRef getMethodFromRootNode(RootNode root);

    /**
     * @return guest language array of all active threads
     */
    Object[] getAllGuestThreads();

    /**
     * Converts the input String to a guest language representation of the String.
     * @param string host String
     * @return guest String representation
     */
    Object toGuestString(String string);

    /**
     * Returns the declaring class for an object
     * @param object arbitrary guest language object
     * @return the declaring class of the object
     */
    KlassRef getRefType(Object object);

    /**
     * Returns the TypeTag constant for the object. The TypeTag will be
     * determined based on the declaring class of the object.
     * @param object an arbitrary guest language object
     * @return TypeTag for the object
     */
    byte getTag(Object object);

    /**
     * Returns the special guest language object that should represent
     * null.
     * @return the null object
     */
    Object getNullObject();

    /**
     * Returns a String representation of the guest language object.
     * Corresponds to toString() in Java.
     * @param object arbitrary guest language object
     * @return String representation of the object
     */
    String getStringValue(Object object);

    /**
     * Returns the name of the guest language thread
     * @param thread guest language thread object
     * @return name of the thread
     */
    String getThreadName(Object thread);

    /**
     * Returns the status of the thread according to https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_ThreadStatus
     * @param thread guest language object representing the thread
     * @return the status of the thread
     */
    int getThreadStatus(Object thread);

    /**
     * Returns the thread group for the thread
     * @param thread guest language object representing the thread
     * @return the thread group for the thread
     */
    Object getThreadGroup(Object thread);

    /**
     * Returns the length of an array.
     * @param array guest language representation of an array
     * @return array length
     */
    int getArrayLength(Object array);

    /**
     * Returns the TypeTag constant for the input object. The TypeTag will be
     * determined based on the declaring class of the object.
     * @param array must be a guest language array object
     * @return TypeTag for the object
     */
    byte getTypeTag(Object array);

    /**
     * Returns an unboxed host primitive type array of the array
     * @param array guest language primitive array
     * @return primitive host language array
     */
    <T> T getUnboxedArray(Object array);

    /**
     * Returns all classes for which the class loader initiated loading.
     * @param classLoader guest language class loader
     * @return array of classes initiated by the class loader
     */
    KlassRef[] getInitiatedClasses(Object classLoader);

    /**
     * Retrieves the field value of a static field.
     * @param field the static field
     * @return the value stored within the field
     */
    Object getStaticFieldValue(FieldRef field);

    /**
     * Set the guest language input value on the field.
     * @param field
     * @param value the guest language value to set
     */
    void setStaticFieldValue(FieldRef field, Object value);

    /**
     * Retrieves the value of the array at the index
     * @param array guest language array
     * @param index
     * @return the guest language value
     */
    Object getArrayValue(Object array, int index);

    /**
     * Set the guest language value at the given index in of the array
     * @param array guest language array
     * @param index
     * @param value guest language object
     */
    void setArrayValue(Object array, int index, Object value);

    /**
     * @return the Ids instance for maintaining guest language objects to unique ID.
     */
    Ids<Object> getIds();

    /**
     * @param string guest language string object
     * @return true if object is a guest language String, false otherwise
     */
    boolean isString(Object string);

    /**
     * Determines if a thread is valid. A valid thread is an active thread
     * @param thread
     * @return true if thread is valid, false otherwise
     */
    boolean isValidThread(Object thread);

    /**
     * Determines if the thread group is valid.
     * @param threadGroup
     * @return true if thread group is valid, false otherwise
     */
    boolean isValidThreadGroup(Object threadGroup);

    /**
     * Determines if the object is an array
     * @param object guest language object
     * @return true if object is an array, false otherwise
     */
    boolean isArray(Object object);

    /**
     * Verifies that the array has the expected length
     * @param array guest language array object
     * @param length expected length of the array
     * @return true if array is equal to or bigger in size than the expected length
     */
    boolean verifyArrayLength(Object array, int length);

    /**
     * Determines if an guest language object is a valid class loader.
     * @param object
     * @return true if the object is a valid class loader, false otherwise
     */
    boolean isValidClassLoader(Object object);

    /**
     * Converts an arbitrary host object to the corresponding guest object
     * @param object the host object to convert
     * @return the guest object
     */
    Object toGuest(Object object);

    // temporarily needed until we get better exception-type based filtering in the Debug API
    Object getGuestException(Throwable exception);

    /**
     * Get the stackframes for the given guest thread
     * @param thread the guest thread
     * @return an array of the call frames for the thread
     */
    JDWPCallFrame[] getStackTrace(Object thread);

    boolean isInstanceOf(Object object, KlassRef klass);
}
