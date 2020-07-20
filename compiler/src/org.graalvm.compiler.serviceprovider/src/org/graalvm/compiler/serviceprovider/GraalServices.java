/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.serviceprovider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import jdk.vm.ci.code.VirtualObject;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import jdk.vm.ci.services.JVMCIPermission;

/**
 * Interface to functionality that abstracts over which JDK version Graal is running on.
 */
public final class GraalServices {

    private GraalServices() {
    }

    private static InternalError shouldNotReachHere() {
        throw new InternalError("JDK specific overlay for " + GraalServices.class.getName() + " missing");
    }

    /**
     * Gets an {@link Iterable} of the providers available for a given service.
     *
     * @param service the service whose provider is being requested
     * @throws SecurityException if on JDK8 and a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> Iterable<S> load(Class<S> service) {
        throw shouldNotReachHere();
    }

    /**
     * Gets the provider for a given service for which at most one provider must be available.
     *
     * @param service the service whose provider is being requested
     * @param required specifies if an {@link InternalError} should be thrown if no provider of
     *            {@code service} is available
     * @return the requested provider if available else {@code null}
     * @throws SecurityException if on JDK8 and a security manager is present and it denies
     *             {@link JVMCIPermission}
     */
    public static <S> S loadSingle(Class<S> service, boolean required) {
        throw shouldNotReachHere();
    }

    /**
     * Gets the class file bytes for {@code c}.
     *
     * @param c the class for which class file bytes are being requested
     * @return an input stream for reading the class file bytes or {@code null} if the class file
     *         bytes could not be found
     * @throws IOException if there's an IO error retrieving the class file bytes
     */
    public static InputStream getClassfileAsStream(Class<?> c) throws IOException {
        throw shouldNotReachHere();
    }

    /**
     * Determines if invoking {@link Object#toString()} on an instance of {@code c} will only run
     * trusted code.
     *
     * @param c
     */
    public static boolean isToStringTrusted(Class<?> c) {
        throw shouldNotReachHere();
    }

    /**
     * Creates an encoding of the context objects representing a speculation reason.
     *
     * @param groupId
     * @param groupName
     * @param context the objects forming a key for the speculation
     */
    static SpeculationReason createSpeculationReason(int groupId, String groupName, Object... context) {
        throw shouldNotReachHere();
    }

    /**
     * Gets a unique identifier for this execution such as a process ID or a
     * {@linkplain #getGlobalTimeStamp() fixed time stamp}.
     */
    public static String getExecutionID() {
        throw shouldNotReachHere();
    }

    /**
     * Gets a time stamp for the current process. This method will always return the same value for
     * the current VM execution.
     */
    public static long getGlobalTimeStamp() {
        throw shouldNotReachHere();
    }

    /**
     * Returns an approximation of the total amount of memory, in bytes, allocated in heap memory
     * for the thread of the specified ID. The returned value is an approximation because some Java
     * virtual machine implementations may use object allocation mechanisms that result in a delay
     * between the time an object is allocated and the time its size is recorded.
     * <p>
     * If the thread of the specified ID is not alive or does not exist, this method returns
     * {@code -1}. If thread memory allocation measurement is disabled, this method returns
     * {@code -1}. A thread is alive if it has been started and has not yet died.
     * <p>
     * If thread memory allocation measurement is enabled after the thread has started, the Java
     * virtual machine implementation may choose any time up to and including the time that the
     * capability is enabled as the point where thread memory allocation measurement starts.
     *
     * @param id the thread ID of a thread
     * @return an approximation of the total memory allocated, in bytes, in heap memory for a thread
     *         of the specified ID if the thread of the specified ID exists, the thread is alive,
     *         and thread memory allocation measurement is enabled; {@code -1} otherwise.
     *
     * @throws IllegalArgumentException if {@code id} {@code <=} {@code 0}.
     * @throws UnsupportedOperationException if the Java virtual machine implementation does not
     *             {@linkplain #isThreadAllocatedMemorySupported() support} thread memory allocation
     *             measurement.
     */
    public static long getThreadAllocatedBytes(long id) {
        throw shouldNotReachHere();
    }

    /**
     * Convenience method for calling {@link #getThreadAllocatedBytes(long)} with the id of the
     * current thread.
     */
    public static long getCurrentThreadAllocatedBytes() {
        throw shouldNotReachHere();
    }

    /**
     * Returns the total CPU time for the current thread in nanoseconds. The returned value is of
     * nanoseconds precision but not necessarily nanoseconds accuracy. If the implementation
     * distinguishes between user mode time and system mode time, the returned CPU time is the
     * amount of time that the current thread has executed in user mode or system mode.
     *
     * @return the total CPU time for the current thread if CPU time measurement is enabled;
     *         {@code -1} otherwise.
     *
     * @throws UnsupportedOperationException if the Java virtual machine does not
     *             {@linkplain #isCurrentThreadCpuTimeSupported() support} CPU time measurement for
     *             the current thread
     */
    public static long getCurrentThreadCpuTime() {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the Java virtual machine implementation supports thread memory allocation
     * measurement.
     */
    public static boolean isThreadAllocatedMemorySupported() {
        throw shouldNotReachHere();
    }

    /**
     * Determines if the Java virtual machine supports CPU time measurement for the current thread.
     */
    public static boolean isCurrentThreadCpuTimeSupported() {
        throw shouldNotReachHere();
    }

    /**
     * Gets the input arguments passed to the Java virtual machine which does not include the
     * arguments to the {@code main} method. This method returns an empty list if there is no input
     * argument to the Java virtual machine.
     * <p>
     * Some Java virtual machine implementations may take input arguments from multiple different
     * sources: for examples, arguments passed from the application that launches the Java virtual
     * machine such as the 'java' command, environment variables, configuration files, etc.
     * <p>
     * Typically, not all command-line options to the 'java' command are passed to the Java virtual
     * machine. Thus, the returned input arguments may not include all command-line options.
     *
     * @return the input arguments to the JVM or {@code null} if they are unavailable
     */
    public static List<String> getInputArguments() {
        throw shouldNotReachHere();
    }

    /**
     * Returns the fused multiply add of the three arguments; that is, returns the exact product of
     * the first two arguments summed with the third argument and then rounded once to the nearest
     * {@code float}.
     */
    @SuppressWarnings("unused")
    public static float fma(float a, float b, float c) {
        throw shouldNotReachHere();
    }

    /**
     * Returns the fused multiply add of the three arguments; that is, returns the exact product of
     * the first two arguments summed with the third argument and then rounded once to the nearest
     * {@code double}.
     */
    @SuppressWarnings("unused")
    public static double fma(double a, double b, double c) {
        throw shouldNotReachHere();
    }

    /**
     * Creates a new {@link VirtualObject} based on a given existing object, with the given
     * contents. If {@code type} is an instance class then {@link VirtualObject#getValues} provides
     * the values for the fields returned by {@link ResolvedJavaType#getInstanceFields(boolean)
     * getInstanceFields(true)}. If {@code type} is an array then the length of
     * {@link VirtualObject#getValues} determines the array length.
     *
     * @param type the type of the object whose allocation was removed during compilation. This can
     *            be either an instance or an array type.
     * @param id a unique id that identifies the object within the debug information for one
     *            position in the compiled code.
     * @param isAutoBox a flag that tells the runtime that the object may be a boxed primitive that
     *            needs to be obtained from the box cache instead of creating a new instance.
     * @return a new {@link VirtualObject} instance.
     */
    @SuppressWarnings("unused")
    public static VirtualObject createVirtualObject(ResolvedJavaType type, int id, boolean isAutoBox) {
        throw shouldNotReachHere();
    }

    /**
     * Gets the update-release counter for the current Java runtime.
     *
     * @see "https://download.java.net/java/GA/jdk14/docs/api/java.base/java/lang/Runtime.Version.html"
     */
    public static int getJavaUpdateVersion() {
        throw shouldNotReachHere();
    }

    /**
     * Looks up the type referenced by the constant pool entry at {@code cpi} as referenced by the
     * {@code opcode} bytecode instruction.
     *
     * @param cpi the index of a constant pool entry that references a type
     * @param opcode the opcode of the instruction with {@code cpi} as an operand
     * @return a reference to the compiler interface type
     */
    @SuppressWarnings("unused")
    public static JavaType lookupReferencedType(ConstantPool constantPool, int cpi, int opcode) {
        throw shouldNotReachHere();
    }

    /**
     * Returns true if JVMCI supports {@code ConstantPool.lookupReferencedType} API.
     */
    public static boolean hasLookupReferencedType() {
        throw shouldNotReachHere();
    }
}
