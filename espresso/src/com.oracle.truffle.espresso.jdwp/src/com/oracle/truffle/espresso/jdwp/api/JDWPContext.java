/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.List;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Interface that defines required methods for a guest language when implementing JDWP.
 */
public interface JDWPContext {

    /**
     * Rerturns the guest language representation of a host thread.
     *
     * @param hostThread
     * @return guest language thread
     */
    Object asGuestThread(Thread hostThread);

    /**
     * Returns the host thread corresponding to the guest language thread.
     *
     * @param thread guest language thread
     * @return host language thread
     */
    Thread asHostThread(Object thread);

    /**
     * Finds a klasses loaded under the given name.
     *
     * @param slashName name of the class
     * @return an array of all classes loaded with the given name
     */
    KlassRef[] findLoadedClass(String slashName);

    /**
     * Returns all loaded classes by the VM.
     *
     * @return array containing every class loaded
     */
    KlassRef[] getAllLoadedClasses();

    /**
     * Finds the method for which an root node was created from.
     *
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
     *
     * @param string host String
     * @return guest String representation
     */
    Object toGuestString(String string);

    /**
     * Returns a String representation of the guest language object. Corresponds to toString() in
     * Java.
     *
     * @param object arbitrary guest language object
     * @return String representation of the object
     */
    String getStringValue(Object object);

    /**
     * Returns the declaring class for an object.
     *
     * @param object arbitrary guest language object
     * @return the declaring class of the object
     */
    KlassRef getRefType(Object object);

    /**
     * Returns the TypeTag constant for the object. The TypeTag will be determined based on the
     * declaring class of the object.
     *
     * @param object an arbitrary guest language object
     * @return TypeTag for the object
     */
    byte getTag(Object object);

    /**
     * Returns the special guest language object that should represent null.
     *
     * @return the null object
     */
    Object getNullObject();

    /**
     * Returns the name of the guest language thread.
     *
     * @param thread guest language thread object
     * @return name of the thread
     */
    String getThreadName(Object thread);

    /**
     * Returns the status of the thread according to
     * https://docs.oracle.com/javase/8/docs/platform/jpda/jdwp/jdwp-protocol.html#JDWP_ThreadStatus
     * .
     *
     * @param thread guest language object representing the thread
     * @return the status of the thread
     */
    int getThreadStatus(Object thread);

    /**
     * Returns the thread group for the thread.
     *
     * @param thread guest language object representing the thread
     * @return the thread group for the thread
     */
    Object getThreadGroup(Object thread);

    /**
     * Returns the length of an array.
     *
     * @param array guest language representation of an array
     * @return array length
     */
    int getArrayLength(Object array);

    /**
     * Returns the TypeTag constant for the input object. The TypeTag will be determined based on
     * the declaring class of the object.
     *
     * @param array must be a guest language array object
     * @return TypeTag for the object
     */
    byte getTypeTag(Object array);

    /**
     * Returns an unboxed host primitive type array of the array.
     *
     * @param array guest language primitive array
     * @return primitive host language array
     */
    <T> T getUnboxedArray(Object array);

    /**
     * Returns all classes for which the class loader initiated loading.
     *
     * @param classLoader guest language class loader
     * @return array of classes initiated by the class loader
     */
    List<? extends KlassRef> getInitiatedClasses(Object classLoader);

    /**
     * Retrieves the field value of a static field.
     *
     * @param field the static field
     * @return the value stored within the field
     */
    Object getStaticFieldValue(FieldRef field);

    /**
     * Set the guest language input value on the field.
     *
     * @param field
     * @param value the guest language value to set
     */
    void setStaticFieldValue(FieldRef field, Object value);

    /**
     * Retrieves the value of the array at the index.
     *
     * @param array guest language array
     * @param index
     * @return the guest language value
     */
    Object getArrayValue(Object array, int index);

    /**
     * Set the guest language value at the given index in of the array.
     *
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
     * Determines if a thread is valid. A valid thread is an active thread.
     *
     * @param guestThread the guest thread
     * @param checkTerminated include a check if the thread has exited
     * @return true if thread is valid, false otherwise
     */
    boolean isValidThread(Object guestThread, boolean checkTerminated);

    /**
     * Determines if the thread group is valid.
     *
     * @param threadGroup
     * @return true if thread group is valid, false otherwise
     */
    boolean isValidThreadGroup(Object threadGroup);

    /**
     * Determines if the object is an array.
     *
     * @param object guest language object
     * @return true if object is an array, false otherwise
     */
    boolean isArray(Object object);

    /**
     * Verifies that the array has the expected length.
     *
     * @param array guest language array object
     * @param length expected length of the array
     * @return true if array is equal to or bigger in size than the expected length
     */
    boolean verifyArrayLength(Object array, int length);

    /**
     * Determines if an guest language object is a valid class loader.
     *
     * @param object
     * @return true if the object is a valid class loader, false otherwise
     */
    boolean isValidClassLoader(Object object);

    /**
     * Converts an arbitrary host object to the corresponding guest object.
     *
     * @param object the host object to convert
     * @return the guest object
     */
    Object toGuest(Object object);

    // temporarily needed until we get better exception-type based filtering in the Debug API
    Object getGuestException(Throwable exception);

    /**
     * Get the stackframes for the given guest thread.
     *
     * @param thread the guest thread
     * @return an array of the call frames for the thread
     */
    CallFrame[] getStackTrace(Object thread);

    /**
     * Determines if the given object is an instance of the given klass.
     *
     * @param object the guest-language object
     * @param klass the guest language klass
     * @return true if object is instance of the klass, otherwise false
     */
    boolean isInstanceOf(Object object, KlassRef klass);

    /**
     * Returns all top-level thread groups within the context. A top-level thread group is one that
     * doesn't have a parent thread group.
     *
     * @return guest-language object array for all top-level thread groups
     */
    Object[] getTopLevelThreadGroups();

    /**
     * Returns the reflected klass type for a given guest language klass object.
     *
     * @param classObject the object instance representing a class
     * @return the reflected klass type
     */
    KlassRef getReflectedType(Object classObject);

    /**
     * Constructs a new array with component type matching the given klass with the given length.
     *
     * @param klass the component type of the new array
     * @param length
     * @return guest language object representing the new array
     */
    Object newArray(KlassRef klass, int length);

    /**
     * Stops the thread with an asynchronous exception, as if done by java.lang.Thread.stop
     *
     * @param guestThread the thread to stop
     * @param guestThrowable the exception to use
     */
    void stopThread(Object guestThread, Object guestThrowable);

    /**
     * Interrupt the thread, as if done by java.lang.Thread.interrupt
     *
     * @param thread the thread to interrupt
     */
    void interruptThread(Object thread);

    /**
     * Returns the classes and interfaces directly nested within this type.Types further nested
     * within those types are not included.
     *
     * @param klass the outer klass
     * @return nested types of the klass
     */
    KlassRef[] getNestedTypes(KlassRef klass);

    /**
     * Determines if the underlying language has a proper implementation of the Java-equivalent of
     * System.exit(int exitCode)
     *
     * @return true if System.exit is implemented, false otherwise
     */
    boolean systemExitImplemented();

    /**
     * Terminates the target VM with the given exit code. On some platforms, the exit code might be
     * truncated, for example, to the low order 8 bits. All ids previously returned from the target
     * VM become invalid. Threads running in the VM are abruptly terminated. A thread death
     * exception is not thrown and finally blocks are not run.
     */
    void exit(int exitCode);

    /**
     * Returns the classpath.
     *
     * @return a list representation of each classpath entry
     */
    List<Path> getClassPath();

    /**
     * Returns the bootclasspath.
     *
     * @return a list representation of each bootclasspath entry
     */
    List<Path> getBootClassPath();

    /**
     * Determines if the exception is caught by the method within the block containing the bci. If
     * it does it returns the exception handler bci.
     *
     * @param method the method
     * @param guestException the exception object
     * @param bci the code index within the method
     * @return the handler bci or -1 if exception is not caught by method
     */
    int getCatchLocation(MethodRef method, Object guestException, int bci);

    /**
     * Returns the bci of the next bytecode instruction within the current frame
     *
     * @param callerRoot the root node of the caller frame
     * @param frame the frame to read the current bci from
     * @return the bci of the next instruction
     */
    int getNextBCI(RootNode callerRoot, Frame frame);

    /**
     * Returns the current BCI or -1 if the BCI cannot be read.
     *
     * @param root the root node, representing the method/function
     * @param frame the frame to read the bci from
     * @return the BCI or -1
     */
    long readBCIFromFrame(RootNode root, Frame frame);

    /**
     * Returns a {@link CallFrame} representation of the location of
     * {@code Object.wait(long timeout)}.
     *
     * @return the {@link CallFrame} that represents the monitor wait method
     */
    CallFrame locateObjectWaitFrame();

    /**
     * Returns the owner thread of an object used as a monitor.
     *
     * @param monitor the monitor object
     * @return the guest language thread object that currently owns the monitor
     */
    Object getMonitorOwnerThread(Object monitor);

    /**
     * Returns the entry count for the monitor on the current thread.
     *
     * @param monitor the monitor
     * @return entry count of monitor
     */
    int getMonitorEntryCount(Object monitor);

    /**
     * Returns all owned guest-language monitor object of the input call frames.
     *
     * @param callFrames the current call frames
     * @return the owned monitor objects
     */
    MonitorStackInfo[] getOwnedMonitors(CallFrame[] callFrames);

    /**
     * Returns the language class associated with the implementing class of this interface.
     *
     * @return the Truffle language class
     */
    Class<? extends TruffleLanguage<?>> getLanguageClass();

    /**
     * Installs new class definitions. If there are active stack frames in methods of the redefined
     * classes in the target VM then those active frames continue to run the bytecodes of the
     * original method.
     *
     * @param redefineInfos the information about the original class and the new class bytes
     * @return 0 on success or the appropriate {@link ErrorCodes} if an error occur
     */
    int redefineClasses(List<RedefineInfo> redefineInfos);

    /**
     * Exit all monitors that was entered by the frame.
     *
     * @param frame
     */
    void clearFrameMonitors(CallFrame frame);

    /**
     * Aborts the context.
     *
     * @param exitCode the system exit code
     */
    void abort(int exitCode);

    /**
     * Returns the current BCI of the node.
     *
     * @param rawNode the current node
     * @param frame the current frame
     * @return the current bci
     */
    long getBCI(Node rawNode, Frame frame);

    /**
     * Returns the instrumentable delegate node for the language root node or <code>rootNode</code>
     * if no instrumentable node can be found.
     *
     * @param rootNode the root node
     * @return the instrumentable delegate node
     */
    Node getInstrumentableNode(RootNode rootNode);

    /**
     * Tests if the guest object is a member of the klass.
     *
     * @param guestObject the guest object
     * @param klass the class
     * @return true is guest object is a member of the klass
     */
    boolean isMemberOf(Object guestObject, KlassRef klass);

    /**
     * Returns all defined modules.
     *
     * @return all modules
     */
    ModuleRef[] getAllModulesRefs();

    /**
     * Tests if the thread is a virtual thread.
     */
    boolean isVirtualThread(Object thread);

    boolean isSingleSteppingDisabled();
}
