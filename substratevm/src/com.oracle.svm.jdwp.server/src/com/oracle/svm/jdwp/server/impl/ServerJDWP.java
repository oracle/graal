/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.io.File;
import java.util.Collection;

import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.jdwp.bridge.CheckedReader;
import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.InvokeOptions;
import com.oracle.svm.jdwp.bridge.JDWP;
import com.oracle.svm.jdwp.bridge.JDWPBridge;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.Logger;
import com.oracle.svm.jdwp.bridge.Packet;
import com.oracle.svm.jdwp.bridge.SymbolicRefs;
import com.oracle.svm.jdwp.bridge.TagConstants;
import com.oracle.svm.jdwp.bridge.TypeTag;
import com.oracle.svm.jdwp.bridge.WritablePacket;
import com.oracle.svm.jdwp.server.ClassUtils;
import com.oracle.svm.jdwp.server.MethodUtils;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ServerJDWP implements JDWP {

    // Base capabilities.
    public static final boolean CAN_WATCH_FIELD_MODIFICATION = false;
    public static final boolean CAN_WATCH_FIELD_ACCESS = false;
    public static final boolean CAN_GET_BYTECODES = false;
    public static final boolean CAN_GET_SYNTHETIC_ATTRIBUTE = false;
    public static final boolean CAN_GET_OWNED_MONITOR_INFO = false;
    public static final boolean CAN_GET_CURRENT_CONTENDED_MONITOR = false;
    public static final boolean CAN_GET_MONITOR_INFO = false;

    // New capabilities.
    public static final boolean CAN_REDEFINE_CLASSES = false;
    public static final boolean CAN_ADD_METHOD = false;
    public static final boolean CAN_UNRESTRICTEDLY_REDEFINE_CLASSES = false;
    public static final boolean CAN_POP_FRAMES = false;
    public static final boolean CAN_USE_INSTANCE_FILTERS = true;
    public static final boolean CAN_GET_SOURCE_DEBUG_EXTENSION = false;
    public static final boolean CAN_REQUEST_VMDEATH_EVENT = false;
    public static final boolean CAN_SET_DEFAULT_STRATUM = false;
    public static final boolean CAN_GET_INSTANCE_INFO = false;
    public static final boolean CAN_REQUEST_MONITOR_EVENTS = false;
    public static final boolean CAN_GET_MONITOR_FRAME_INFO = false;
    public static final boolean CAN_USE_SOURCE_NAME_FILTERS = false;

    // It's only possible to reconstruct the constant pool bytes partially.
    public static final boolean CAN_GET_CONSTANT_POOL = false;
    public static final boolean CAN_FORCE_EARLY_RETURN = false;

    public static JDWPBridge BRIDGE = null;

    public static final SymbolicRefs SYMBOLIC_REFS = new ServerSymbolicRefs();
    static final CheckedReader CHECKED_READER = new CheckedReader(SYMBOLIC_REFS);

    private final DebuggerController controller;
    public static Logger LOGGER = new Logger(false, "[JDWPServer]", System.err);

    private static final int ACC_SYNTHETIC = 0x00001000;

    private static final int JDWP_SYNTHETIC = 0xF0000000;

    public ServerJDWP(DebuggerController controller) {
        this.controller = controller;
    }

    static final String VERSION_NAME = "Java Debug Wire Protocol (Substrate VM)";

    public static void initLogging(boolean enabled) {
        LOGGER = new Logger(enabled, "[JDWPServer]", System.err);
    }

    private static int extractMajorVersion(String javaVersion) {
        int index = 0;
        while (index < javaVersion.length() && Character.isDigit(javaVersion.charAt(index))) {
            ++index;
        }
        return Integer.parseInt(javaVersion.substring(0, index));
    }

    @Override
    public Packet VirtualMachine_Version(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        String vmName = BRIDGE.getSystemProperty("java.vm.name");
        String vmVersion = BRIDGE.getSystemProperty("java.vm.version");
        String vmInfo = BRIDGE.getSystemProperty("java.vm.info");
        String javaVersion = BRIDGE.getSystemProperty("java.version");

        int majorVersion = extractMajorVersion(javaVersion);
        int minorVersion = 0;

        String verboseVersionMessage = MetadataUtil.fmt(
                        "%s version %s.%s%nJVM Debug Interface version %s.%s%nJVM version %s (%s, %s)",
                        VERSION_NAME,
                        majorVersion, minorVersion,
                        majorVersion, minorVersion,
                        vmVersion,
                        vmName,
                        vmInfo);

        // Text information on the VM version
        data.writeString(verboseVersionMessage);
        // Major JDWP Version number
        data.writeInt(majorVersion);
        // Minor JDWP Version number
        data.writeInt(minorVersion);
        // Target VM JRE version, as in the java.version property
        data.writeString(vmVersion);
        // Target VM name, as in the java.vm.name property
        data.writeString(vmName);

        return reply;
    }

    @Override
    public Packet VirtualMachine_IDSizes(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        // fieldID size in bytes
        data.writeInt(Long.BYTES);
        // methodID size in bytes
        data.writeInt(Long.BYTES);
        // objectID size in bytes
        data.writeInt(Long.BYTES);
        // referenceTypeID size in bytes
        data.writeInt(Long.BYTES);
        // frameID size in bytes
        data.writeInt(Long.BYTES);

        return reply;
    }

    @Override
    public Packet VirtualMachine_AllClasses(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_AllClassesWithGeneric(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    static boolean isSynthetic(int mod) {
        return (mod & ACC_SYNTHETIC) != 0;
    }

    //
    private static int checkSyntheticFlag(int modBits) {
        int mod = modBits;
        if (isSynthetic(modBits)) {
            // JDWP has a different bit for synthetic
            mod &= ~ACC_SYNTHETIC;
            mod |= JDWP_SYNTHETIC;
        }
        return mod;
    }

    private static Packet methodsHelper(Packet packet, boolean includeGenericSignature) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        Collection<? extends ResolvedJavaMethod> declaredMethods = ClassUtils.UNIVERSE.getAllDeclaredMethods(type);
        int numDeclaredMethods = declaredMethods.size();
        data.writeInt(numDeclaredMethods);
        for (ResolvedJavaMethod method : declaredMethods) {
            data.writeLong(SYMBOLIC_REFS.toMethodRef(method));
            data.writeString(method.getName());
            data.writeString(MethodUtils.getSignatureAsString(method));
            if (includeGenericSignature) {
                data.writeString(MethodUtils.getGenericSignatureAsString(method));
            }
            int modBits = checkSyntheticFlag(method.getModifiers());
            data.writeInt(modBits);
        }

        return reply;
    }

    @Override
    public Packet ReferenceType_MethodsWithGeneric(Packet packet) throws JDWPException {
        return methodsHelper(packet, true);
    }

    @Override
    public Packet ReferenceType_Methods(Packet packet) throws JDWPException {
        return methodsHelper(packet, false);
    }

    @Override
    public Packet VirtualMachine_AllThreads(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet EventRequest_Set(Packet packet) throws JDWPException {
        return controller.getRequestedJDWPEvents().registerRequest(packet);
    }

    @Override
    public Packet EventRequest_Clear(Packet packet) throws JDWPException {
        return controller.getRequestedJDWPEvents().clearRequest(packet);
    }

    @Override
    public Packet EventRequest_ClearAllBreakpoints(Packet packet) throws JDWPException {
        return controller.getRequestedJDWPEvents().clearAllBreakpoints(packet);
    }

    @Override
    public Packet VirtualMachine_ClassPaths(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        // Base directory used to resolve relative paths in either of the following lists.
        String baseDirectory = ServerJDWP.BRIDGE.currentWorkingDirectory();
        data.writeString(baseDirectory);

        // classpaths
        String classpath = ServerJDWP.BRIDGE.getSystemProperty("java.class.path");
        String[] classpathParts = classpath != null
                        ? classpath.split(File.pathSeparator)
                        : new String[0];
        data.writeInt(classpathParts.length);
        for (String part : classpathParts) {
            data.writeString(part);
        }

        // bootclasspaths
        String bootClasspath = ServerJDWP.BRIDGE.getSystemProperty("sun.boot.class.path");
        String[] bootClasspathParts = bootClasspath != null
                        ? bootClasspath.split(File.pathSeparator)
                        : new String[0];
        data.writeInt(bootClasspathParts.length);
        for (String part : bootClasspathParts) {
            data.writeString(part);
        }

        return reply;
    }

    @Override
    public Packet VirtualMachine_Capabilities(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();
        data.writeBoolean(CAN_WATCH_FIELD_MODIFICATION);      // canWatchFieldModification
        data.writeBoolean(CAN_WATCH_FIELD_ACCESS);            // canWatchFieldAccess
        data.writeBoolean(CAN_GET_BYTECODES);                 // canGetBytecodes
        data.writeBoolean(CAN_GET_SYNTHETIC_ATTRIBUTE);       // canGetSyntheticAttribute
        data.writeBoolean(CAN_GET_OWNED_MONITOR_INFO);        // canGetOwnedMonitorInfo
        data.writeBoolean(CAN_GET_CURRENT_CONTENDED_MONITOR); // canGetCurrentContendedMonitor
        data.writeBoolean(CAN_GET_MONITOR_INFO);              // canGetMonitorInfo
        return reply;
    }

    @Override
    public Packet VirtualMachine_CapabilitiesNew(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeBoolean(CAN_WATCH_FIELD_MODIFICATION);         // canWatchFieldModification
        data.writeBoolean(CAN_WATCH_FIELD_ACCESS);               // canWatchFieldAccess
        data.writeBoolean(CAN_GET_BYTECODES);                    // canGetBytecodes
        data.writeBoolean(CAN_GET_SYNTHETIC_ATTRIBUTE);          // canGetSyntheticAttribute
        data.writeBoolean(CAN_GET_OWNED_MONITOR_INFO);           // canGetOwnedMonitorInfo
        data.writeBoolean(CAN_GET_CURRENT_CONTENDED_MONITOR);    // canGetCurrentContendedMonitor
        data.writeBoolean(CAN_GET_MONITOR_INFO);                 // canGetMonitorInfo
        data.writeBoolean(CAN_REDEFINE_CLASSES);                 // canRedefineClasses
        data.writeBoolean(CAN_ADD_METHOD);                       // canAddMethod
        data.writeBoolean(CAN_UNRESTRICTEDLY_REDEFINE_CLASSES);  // canUnrestrictedlyRedefineClasses
        data.writeBoolean(CAN_POP_FRAMES);                       // canPopFrames
        data.writeBoolean(CAN_USE_INSTANCE_FILTERS);             // canUseInstanceFilters
        data.writeBoolean(CAN_GET_SOURCE_DEBUG_EXTENSION);       // canGetSourceDebugExtension
        data.writeBoolean(CAN_REQUEST_VMDEATH_EVENT);            // canRequestVMDeathEvent
        data.writeBoolean(CAN_SET_DEFAULT_STRATUM);              // canSetDefaultStratum
        data.writeBoolean(CAN_GET_INSTANCE_INFO);                // canGetInstanceInfo
        data.writeBoolean(CAN_REQUEST_MONITOR_EVENTS);           // canRequestMonitorEvents
        data.writeBoolean(CAN_GET_MONITOR_FRAME_INFO);           // canGetMonitorFrameInfo
        data.writeBoolean(CAN_USE_SOURCE_NAME_FILTERS);          // canUseSourceNameFilters
        data.writeBoolean(CAN_GET_CONSTANT_POOL);                // canGetConstantPool
        data.writeBoolean(CAN_FORCE_EARLY_RETURN);               // canForceEarlyReturn
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future
        data.writeBoolean(false); // reserved for future

        return reply;
    }

    @Override
    public Packet Method_LineTable(Packet packet) throws JDWPException {
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Reader input = packet.newDataReader();
        Packet.Writer data = reply.dataWriter();

        CHECKED_READER.readTypeRef(input);
        ResolvedJavaMethod method = CHECKED_READER.readMethodRef(input);

        LineNumberTable table = method.getLineNumberTable();

        int lastBCI = method.getCodeSize();
        long start = lastBCI > 0 ? 0 : -1;
        long end = lastBCI > 0 ? lastBCI : -1;

        // Lowest valid code index for the method, >=0, or -1 if the method is native
        data.writeLong(start);
        // Highest valid code index for the method, >=0, or -1 if the method is native
        data.writeLong(end);

        if (table == null) {
            // The number of entries in the line table for this method.
            data.writeInt(0);
        } else {
            int[] lines = table.getLineNumbers();
            int[] bcis = table.getBcis();
            assert lines.length == bcis.length;
            // The number of entries in the line table for this method.
            data.writeInt(lines.length);
            for (int i = 0; i < lines.length; i++) {
                int bci = bcis[i];
                int line = lines[i];
                // Initial code index of the line, start <= lineCodeIndex < end
                data.writeLong(bci);
                // Line number.
                data.writeInt(line);
            }
        }

        return reply;
    }

    private static Packet variableTableHelper(Packet packet, boolean includeGenericSignatures) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        // Ignore type, methodId points to the specific method.
        ResolvedJavaType declaringType = CHECKED_READER.readTypeRef(reader);
        ResolvedJavaMethod method = CHECKED_READER.readMethodRef(reader);
        assert declaringType.equals(method.getDeclaringClass());

        LocalVariableTable table = method.getLocalVariableTable();
        if (table == null) {
            throw JDWPException.raise(ErrorCode.ABSENT_INFORMATION);
        }

        int slotsForArguments = 0;
        if (!method.isStatic()) {
            slotsForArguments += JavaKind.Object.getSlotCount(); // this
        }
        for (JavaType parameterType : method.toParameterTypes()) {
            int slotCount = parameterType.getJavaKind().getSlotCount();
            slotsForArguments += slotCount;
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        // The number of words in the frame used by arguments. Eight-byte arguments use two words;
        // all others use one.
        writer.writeInt(slotsForArguments);

        Local[] locals = table.getLocals();
        writer.writeInt(locals.length);
        for (Local local : locals) {
            long codeIndex = local.getStartBCI();
            // First code index at which the variable is visible (unsigned). Used in conjunction
            // with length. The variable can be get or set only when the current codeIndex <=
            // current frame code index < codeIndex + length
            writer.writeLong(codeIndex);

            writer.writeString(local.getName());

            String signature = local.getType().getName();
            writer.writeString(signature);

            if (includeGenericSignatures) {
                // Generic signature is missing from JVMCI APIs.
                String genericSignature = "";
                writer.writeString(genericSignature);
            }

            // Unsigned value used in conjunction with codeIndex. The variable can be get or set
            // only when the current codeIndex <= current frame code index < code index + length
            int length = local.getEndBCI() - local.getStartBCI() + 1;
            writer.writeInt(length);

            writer.writeInt(local.getSlot());
        }

        return reply;
    }

    @Override
    public Packet Method_VariableTable(Packet packet) throws JDWPException {
        return variableTableHelper(packet, false);
    }

    @Override
    public Packet Method_VariableTableWithGeneric(Packet packet) throws JDWPException {
        return variableTableHelper(packet, true);
    }

    @Override
    public Packet Method_IsObsolete(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        // Ignore type, methodId points to the specific method.
        ResolvedJavaType declaringType = CHECKED_READER.readTypeRef(reader);
        ResolvedJavaMethod method = CHECKED_READER.readMethodRef(reader);
        assert declaringType.equals(method.getDeclaringClass());

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        // Redefinition is not supported, method never become obsolete.
        writer.writeBoolean(false);
        return reply;
    }

    @Override
    public Packet Method_Bytecodes(Packet packet) throws JDWPException {
        if (!CAN_GET_BYTECODES) {
            throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
        }

        Packet.Reader reader = packet.newDataReader();
        // Ignore type, methodId points to the specific method.
        ResolvedJavaType declaringType = CHECKED_READER.readTypeRef(reader);
        ResolvedJavaMethod method = CHECKED_READER.readMethodRef(reader);
        assert declaringType.equals(method.getDeclaringClass());

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        if (method.hasBytecodes()) {
            byte[] bytecodes = method.getCode();
            writer.writeInt(bytecodes.length);
            writer.writeBytes(bytecodes);
        } else {
            writer.writeInt(0);
        }

        return reply;
    }

    @Override
    public Packet VirtualMachine_Resume(Packet packet) throws JDWPException {
        controller.getContext().getThreadsCollector().resumeAll();
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet VirtualMachine_Suspend(Packet packet) throws JDWPException {
        controller.getContext().getThreadsCollector().suspendAll();
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet VirtualMachine_Dispose(Packet packet) throws JDWPException {
        controller.disposeDebugger(WritablePacket.newReplyTo(packet));
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_Exit(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        int exitCode = input.readInt(); // Exit code
        controller.disposeConnection(WritablePacket.newReplyTo(packet));
        System.exit(exitCode);
        return null;
    }

    @Override
    public Packet ThreadReference_Name(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ThreadReference_Suspend(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();

        if (threadId == 0) {
            // A null thread is invalid
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        ThreadRef threadRef = controller.getContext().getThreadRef(threadId);
        threadRef.suspend();

        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ThreadReference_Resume(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();

        if (threadId == 0) {
            // A null thread is invalid
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }

        ThreadRef threadRef = controller.getContext().getThreadRefIfExists(threadId);
        if (threadRef != null) {
            threadRef.resume(false);
        } else {
            ServerJDWP.BRIDGE.getThreadStatus(threadId);
            // Either throws JDWPException when the id is invalid,
            // or the thread exists in the app and is not suspended
        }
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ThreadReference_Status(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();

        int status = ServerJDWP.BRIDGE.getThreadStatus(threadId);
        ThreadRef threadRef = controller.getContext().getThreadRefIfExists(threadId);
        int suspendStatus = (threadRef != null) ? threadRef.getSuspendCount() > 0 ? 1 : 0 : 0;

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeInt(status);
        data.writeInt(suspendStatus);
        return reply;
    }

    @Override
    public Packet ThreadReference_Frames(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();
        int startFrame = input.readInt();
        int length = input.readInt();

        if (threadId == 0) {
            // A null thread is invalid
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        ThreadRef threadRef = controller.getContext().getThreadRefIfExists(threadId);
        CallFrame[] frames;
        if (threadRef == null) {
            // We do not know about this thread, it's invalid or running in the resident
            ServerJDWP.BRIDGE.getThreadStatus(threadId);
            // When no JDWPException was thrown, it's a running thread
            length = 0;
            frames = null;
        } else {
            SuspendedInfo suspendedInfo = threadRef.getSuspendedInfo();
            if (suspendedInfo == null) {
                throw JDWPException.raise(ErrorCode.INVALID_THREAD);
            }
            frames = suspendedInfo.getStackFrames();
            if (length == -1 || length > frames.length) {
                length = frames.length;
            }
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeInt(length);
        for (int i = startFrame; i < startFrame + length; i++) {
            CallFrame frame = frames[i];
            data.writeLong(frame.getFrameId());
            data.writeByte(frame.getTypeTag());
            data.writeLong(frame.getClassId());
            data.writeLong(frame.getMethodId());
            data.writeLong(frame.getCodeIndex());
        }
        return reply;
    }

    @Override
    public Packet ThreadReference_FrameCount(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();

        if (threadId == 0) {
            // A null thread is invalid
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        ThreadRef threadRef = controller.getContext().getThreadRefIfExists(threadId);
        int length;
        if (threadRef == null) {
            // We do not know about this thread, it's invalid or running in the resident
            ServerJDWP.BRIDGE.getThreadStatus(threadId);
            // When no JDWPException was thrown, it's a running thread
            length = 0;
        } else {
            SuspendedInfo suspendedInfo = threadRef.getSuspendedInfo();
            if (suspendedInfo == null) {
                throw JDWPException.raise(ErrorCode.INVALID_THREAD);
            }
            CallFrame[] frames = suspendedInfo.getStackFrames();
            length = frames.length;
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeInt(length);
        return reply;
    }

    @Override
    public Packet ThreadReference_SuspendCount(Packet packet) throws JDWPException {
        Packet.Reader input = packet.newDataReader();
        long threadId = input.readLong();
        ThreadRef thread = controller.getContext().getThreadRefIfExists(threadId);
        int suspendCount;
        if (thread != null) {
            suspendCount = thread.getSuspendCount();
        } else {
            ServerJDWP.BRIDGE.getThreadStatus(threadId);
            // When no JDWPException was thrown, it's a running thread
            suspendCount = 0;
        }
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer data = reply.dataWriter();

        data.writeInt(suspendCount);
        return reply;
    }

    @Override
    public Packet ThreadReference_ThreadGroup(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_TopLevelThreadGroups(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ThreadGroupReference_Name(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ThreadGroupReference_Children(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ThreadGroupReference_Parent(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ObjectReference_ReferenceType(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_DisposeObjects(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_HoldEvents(Packet packet) throws JDWPException {
        controller.getEventListener().holdEvents();
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet VirtualMachine_ReleaseEvents(Packet packet) throws JDWPException {
        controller.getEventListener().releaseEvents();
        return WritablePacket.newReplyTo(packet);
    }

    @Override
    public Packet ObjectReference_DisableCollection(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ObjectReference_EnableCollection(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ObjectReference_IsCollected(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet StringReference_Value(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_CreateString(Packet packet) throws JDWPException {
        return ServerJDWP.BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ReferenceType_SourceFile(Packet packet) throws JDWPException {

        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        String sourceFileName = type.getSourceFileName();
        if (sourceFileName == null) {
            throw JDWPException.raise(ErrorCode.ABSENT_INFORMATION);
        }

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeString(sourceFileName);

        return reply;
    }

    @Override
    public Packet VirtualMachine_ClassesBySignature(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        String signature = reader.readString();

        ResolvedJavaType[] matchingTypes = ClassUtils.UNIVERSE.getTypes()
                        .stream()
                        .filter(type -> signature.equals(type.getName()))
                        // Include reference types only.
                        .filter(type -> !type.isPrimitive())
                        .toArray(ResolvedJavaType[]::new);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        writer.writeInt(matchingTypes.length);
        for (ResolvedJavaType type : matchingTypes) {
            // Kind of following reference type.
            writer.writeByte(TypeTag.getKind(type));
            long typeId = SYMBOLIC_REFS.toTypeRef(type);
            writer.writeLong(typeId);
            int[] status = BRIDGE.typeStatus(typeId);
            writer.writeInt(status[0]);
        }

        return reply;
    }

    @Override
    public Packet ClassType_Superclass(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        ResolvedJavaType superclass = type.getSuperclass();

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        if (superclass == null) {
            writer.writeLong(SymbolicRefs.NULL);
        } else {
            writer.writeLong(SYMBOLIC_REFS.toTypeRef(superclass));
        }

        return reply;
    }

    @Override
    public Packet ReferenceType_Interfaces(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        ResolvedJavaType[] interfaces = type.getInterfaces();
        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        writer.writeInt(interfaces.length);
        for (ResolvedJavaType interfaceType : interfaces) {
            long typeId = SYMBOLIC_REFS.toTypeRef(interfaceType);
            writer.writeLong(typeId);
        }

        return reply;
    }

    private static Packet referenceSignatureHelper(Packet packet, boolean includeGenericSignature) {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        String signature = type.getName();
        writer.writeString(signature);

        if (includeGenericSignature) {
            String genericSignature = ""; // Generic signature not available via JVMCI APIs.
            writer.writeString(genericSignature);
        }

        return reply;
    }

    @Override
    public Packet ReferenceType_Signature(Packet packet) throws JDWPException {
        return referenceSignatureHelper(packet, false);
    }

    @Override
    public Packet ReferenceType_SignatureWithGeneric(Packet packet) throws JDWPException {
        return referenceSignatureHelper(packet, true);
    }

    @Override
    public Packet ReferenceType_Modifiers(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        int modBits = type.getModifiers();
        writer.writeInt(modBits);
        return reply;
    }

    private static Packet fieldsHelper(Packet packet, boolean includeGenericSignature) {
        Packet.Reader reader = packet.newDataReader();
        ResolvedJavaType type = CHECKED_READER.readTypeRef(reader);

        Collection<? extends ResolvedJavaField> allDeclaredFields = ClassUtils.UNIVERSE.getAllDeclaredFields(type);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();
        writer.writeInt(allDeclaredFields.size());
        for (ResolvedJavaField field : allDeclaredFields) {
            writer.writeLong(SYMBOLIC_REFS.toFieldRef(field));
            writer.writeString(field.getName());
            String signature = field.getType().getName();
            writer.writeString(signature);
            if (includeGenericSignature) {
                String genericSignature = ""; // not available through JVMCI APIs.
                writer.writeString(genericSignature);
            }
            int modBits = field.getModifiers();
            writer.writeInt(modBits);
        }
        return reply;
    }

    @Override
    public Packet ReferenceType_Fields(Packet packet) throws JDWPException {
        return fieldsHelper(packet, false);
    }

    @Override
    public Packet ReferenceType_FieldsWithGeneric(Packet packet) throws JDWPException {
        return fieldsHelper(packet, true);
    }

    @Override
    public Packet ReferenceType_Status(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        long typeId = reader.readLong();

        @SuppressWarnings("unused")
        ResolvedJavaType type = SYMBOLIC_REFS.toResolvedJavaType(typeId);

        int[] status = BRIDGE.typeStatus(typeId);

        WritablePacket reply = WritablePacket.newReplyTo(packet);
        Packet.Writer writer = reply.dataWriter();

        writer.writeInt(status[0]);

        return reply;
    }

    @Override
    public Packet ObjectReference_ReferringObjects(Packet packet) throws JDWPException {
        assert !CAN_GET_INSTANCE_INFO;
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet VirtualMachine_SetDefaultStratum(Packet packet) throws JDWPException {
        assert !CAN_SET_DEFAULT_STRATUM;
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet VirtualMachine_InstanceCounts(Packet packet) throws JDWPException {
        assert !CAN_GET_INSTANCE_INFO;
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet ReferenceType_Instances(Packet packet) throws JDWPException {
        assert !CAN_GET_INSTANCE_INFO;
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet ReferenceType_ConstantPool(Packet packet) throws JDWPException {
        assert !CAN_GET_CONSTANT_POOL;
        // Currently, it's only possible to reconstruct the constant pool bytes partially.
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet ReferenceType_SourceDebugExtension(Packet packet) throws JDWPException {
        assert !CAN_GET_SOURCE_DEBUG_EXTENSION;
        throw JDWPException.raise(ErrorCode.NOT_IMPLEMENTED);
    }

    @Override
    public Packet ReferenceType_ClassObject(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ClassObjectReference_ReflectedType(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ReferenceType_ClassLoader(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ReferenceType_Module(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ModuleReference_Name(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet VirtualMachine_AllModules(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ModuleReference_ClassLoader(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ReferenceType_NestedTypes(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ThreadReference_IsVirtual(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ArrayReference_Length(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ArrayReference_GetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ArrayReference_SetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ArrayType_NewInstance(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    private ThreadRef validateThread(long threadId, boolean mustBeSuspended) throws JDWPException {
        if (threadId == 0) {
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        ThreadRef threadRef = controller.getContext().getThreadRef(threadId);
        if (mustBeSuspended) {
            if (!(threadRef.getSuspendCount() > 0)) {
                throw JDWPException.raise(ErrorCode.THREAD_NOT_SUSPENDED);
            }
        }
        return threadRef;
    }

    @SuppressWarnings("unused")
    private ThreadRef validateThread(long threadId) throws JDWPException {
        return validateThread(threadId, false);
    }

    private static void validateFrameId(ThreadRef threadRef, long frameId) throws JDWPException {
        if (threadRef == null || !(threadRef.getSuspendCount() > 0)) {
            throw JDWPException.raise(ErrorCode.INVALID_THREAD);
        }
        if (!threadRef.isValidFrameId(frameId)) {
            throw JDWPException.raise(ErrorCode.INVALID_FRAMEID);
        }
    }

    @Override
    public Packet StackFrame_ThisObject(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        long threadId = reader.readLong();
        long frameId = reader.readLong();
        assert reader.isEndOfInput();
        ThreadRef threadRef = validateThread(threadId, true);
        validateFrameId(threadRef, frameId);
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet StackFrame_GetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        long threadId = reader.readLong();
        long frameId = reader.readLong();

        // There could be additional input pending e.g. slots and tags, but only some initial
        // parameters are validated.

        ThreadRef threadRef = validateThread(threadId, true);
        validateFrameId(threadRef, frameId);
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ObjectReference_GetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ReferenceType_GetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ClassLoaderReference_VisibleClasses(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ClassType_SetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet ObjectReference_SetValues(Packet packet) throws JDWPException {
        return BRIDGE.dispatch(packet);
    }

    @Override
    public Packet StackFrame_SetValues(Packet packet) throws JDWPException {
        Packet.Reader reader = packet.newDataReader();
        long threadId = reader.readLong();
        long frameId = reader.readLong();

        // There could be additional input pending e.g. slots and tags, but only some initial
        // parameters are validated.

        ThreadRef threadRef = validateThread(threadId, true);
        validateFrameId(threadRef, frameId);
        return BRIDGE.dispatch(packet);
    }

    private static void skipArguments(Packet.Reader reader) {
        int argCount = reader.readInt();
        assert argCount >= 0;
        for (int i = 0; i < argCount; i++) {
            byte tag = JDWP.readTag(reader);
            switch (tag) {
                case TagConstants.BYTE -> reader.readByte();
                case TagConstants.BOOLEAN -> reader.readBoolean();
                case TagConstants.SHORT -> reader.readShort();
                case TagConstants.CHAR -> reader.readChar();
                case TagConstants.INT -> reader.readInt();
                case TagConstants.LONG -> reader.readLong();
                case TagConstants.FLOAT -> reader.readFloat();
                case TagConstants.DOUBLE -> reader.readDouble();
                case TagConstants.VOID -> {
                    // Read nothing.
                }
                default -> reader.readLong();
            }
        }
    }

    @Override
    public Packet ClassType_InvokeMethod(Packet packet) throws JDWPException {
        return sharedInvokeMethod(false, packet); // INVOKESTATIC
    }

    @Override
    public Packet ObjectReference_InvokeMethod(Packet packet) throws JDWPException {
        return sharedInvokeMethod(true, packet); // INVOKEVIRTUAL/INVOKESPECIAL
    }

    @Override
    public Packet InterfaceType_InvokeMethod(Packet packet) throws JDWPException {
        return sharedInvokeMethod(false, packet); // INVOKESTATIC for interface methods.
    }

    @Override
    public Packet ClassType_NewInstance(Packet packet) throws JDWPException {
        return sharedInvokeMethod(false, packet); // INVOKESPECIAL <init>, receiver is instantiated
    }

    private Packet sharedInvokeMethod(boolean readReceiver, Packet packet) {
        Packet.Reader reader = packet.newDataReader();
        long receiverId;
        ResolvedJavaType type;
        if (readReceiver) {
            receiverId = reader.readLong();
            assert receiverId != 0;
        } else {
            type = CHECKED_READER.readTypeRef(reader);
            assert type != null;
        }
        long threadId = reader.readLong();
        ThreadRef threadRef = validateThread(threadId, true);
        if (readReceiver) {
            type = CHECKED_READER.readTypeRef(reader);
            assert type != null;
        }
        ResolvedJavaMethod method = CHECKED_READER.readMethodRef(reader);
        assert !readReceiver || !method.isStatic();

        skipArguments(reader);
        int options = reader.readInt();
        assert reader.isEndOfInput();

        boolean invokeSingleThreaded = InvokeOptions.singleThreaded(options);
        Packet[] invokeReply = new Packet[1];
        threadRef.invoke(
                        () -> invokeReply[0] = dispatchInvokeWithExceptions(packet),
                        () -> controller.getEventListener().sendEvent(invokeReply[0]),
                        invokeSingleThreaded);
        return null;
    }

    /**
     * This method is not part of the synchronous command -> reply loop, exceptions must be caught
     * and transformed into reply packets.
     */
    private static Packet dispatchInvokeWithExceptions(Packet packet) {
        try {
            return BRIDGE.dispatch(packet);
        } catch (JDWPException e) {
            ServerJDWP.LOGGER.log(e, "JDWP exception during method invocation");
            WritablePacket reply = WritablePacket.newReplyTo(packet);
            reply.errorCode(e.getError());
            return reply;
        } catch (Throwable t) {
            ServerJDWP.LOGGER.log(t, "internal exception during method invocation");
            WritablePacket reply = WritablePacket.newReplyTo(packet);
            reply.errorCode(ErrorCode.INTERNAL);
            return reply;
        }
    }
}
