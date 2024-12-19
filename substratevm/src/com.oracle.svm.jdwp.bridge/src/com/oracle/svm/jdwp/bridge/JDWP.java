/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.bridge;

import com.oracle.svm.core.util.VMError;

// Checkstyle: stop method name check
// Checkstyle: stop field name check

public interface JDWP {

    int VirtualMachine = 1;
    int VirtualMachine_Version = 1;
    int VirtualMachine_ClassesBySignature = 2;
    int VirtualMachine_AllClasses = 3;
    int VirtualMachine_AllThreads = 4;
    int VirtualMachine_TopLevelThreadGroups = 5;
    int VirtualMachine_Dispose = 6;
    int VirtualMachine_IDSizes = 7;
    int VirtualMachine_Suspend = 8;
    int VirtualMachine_Resume = 9;
    int VirtualMachine_Exit = 10;
    int VirtualMachine_CreateString = 11;
    int VirtualMachine_Capabilities = 12;
    int VirtualMachine_ClassPaths = 13;
    int VirtualMachine_DisposeObjects = 14;
    int VirtualMachine_HoldEvents = 15;
    int VirtualMachine_ReleaseEvents = 16;
    int VirtualMachine_CapabilitiesNew = 17;
    int VirtualMachine_RedefineClasses = 18;
    int VirtualMachine_SetDefaultStratum = 19;
    int VirtualMachine_AllClassesWithGeneric = 20;
    int VirtualMachine_InstanceCounts = 21;
    int VirtualMachine_AllModules = 22;

    default Packet VirtualMachine_Version(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Version");
    }

    default Packet VirtualMachine_ClassesBySignature(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.ClassesBySignature");
    }

    default Packet VirtualMachine_AllClasses(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.AllClasses");
    }

    default Packet VirtualMachine_AllThreads(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.AllThreads");
    }

    default Packet VirtualMachine_TopLevelThreadGroups(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.TopLevelThreadGroups");
    }

    default Packet VirtualMachine_Dispose(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Dispose");
    }

    default Packet VirtualMachine_IDSizes(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.IDSizes");
    }

    default Packet VirtualMachine_Suspend(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Suspend");
    }

    default Packet VirtualMachine_Resume(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Resume");
    }

    default Packet VirtualMachine_Exit(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Exit");
    }

    default Packet VirtualMachine_CreateString(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.CreateString");
    }

    default Packet VirtualMachine_Capabilities(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.Capabilities");
    }

    default Packet VirtualMachine_ClassPaths(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.ClassPaths");
    }

    default Packet VirtualMachine_DisposeObjects(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.DisposeObjects");
    }

    default Packet VirtualMachine_HoldEvents(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.HoldEvents");
    }

    default Packet VirtualMachine_ReleaseEvents(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.ReleaseEvents");
    }

    default Packet VirtualMachine_CapabilitiesNew(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.CapabilitiesNew");
    }

    default Packet VirtualMachine_RedefineClasses(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.RedefineClasses");
    }

    default Packet VirtualMachine_SetDefaultStratum(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.SetDefaultStratum");
    }

    default Packet VirtualMachine_AllClassesWithGeneric(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.AllClassesWithGeneric");
    }

    default Packet VirtualMachine_InstanceCounts(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.InstanceCounts");
    }

    default Packet VirtualMachine_AllModules(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("VirtualMachine.AllModules");
    }

    int ReferenceType = 2;
    int ReferenceType_Signature = 1;
    int ReferenceType_ClassLoader = 2;
    int ReferenceType_Modifiers = 3;
    int ReferenceType_Fields = 4;
    int ReferenceType_Methods = 5;
    int ReferenceType_GetValues = 6;
    int ReferenceType_SourceFile = 7;
    int ReferenceType_NestedTypes = 8;
    int ReferenceType_Status = 9;
    int ReferenceType_Interfaces = 10;
    int ReferenceType_ClassObject = 11;
    int ReferenceType_SourceDebugExtension = 12;
    int ReferenceType_SignatureWithGeneric = 13;
    int ReferenceType_FieldsWithGeneric = 14;
    int ReferenceType_MethodsWithGeneric = 15;
    int ReferenceType_Instances = 16;
    int ReferenceType_ClassFileVersion = 17;
    int ReferenceType_ConstantPool = 18;
    int ReferenceType_Module = 19;

    default Packet ReferenceType_Signature(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Signature");
    }

    default Packet ReferenceType_ClassLoader(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.ClassLoader");
    }

    default Packet ReferenceType_Modifiers(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Modifiers");
    }

    default Packet ReferenceType_Fields(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Fields");
    }

    default Packet ReferenceType_Methods(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Methods");
    }

    default Packet ReferenceType_GetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.GetValues");
    }

    default Packet ReferenceType_SourceFile(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.SourceFile");
    }

    default Packet ReferenceType_NestedTypes(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.NestedTypes");
    }

    default Packet ReferenceType_Status(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Status");
    }

    default Packet ReferenceType_Interfaces(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Interfaces");
    }

    default Packet ReferenceType_ClassObject(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.ClassObject");
    }

    default Packet ReferenceType_SourceDebugExtension(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.SourceDebugExtension");
    }

    default Packet ReferenceType_SignatureWithGeneric(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.SignatureWithGeneric");
    }

    default Packet ReferenceType_FieldsWithGeneric(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.FieldsWithGeneric");
    }

    default Packet ReferenceType_Instances(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Instances");
    }

    default Packet ReferenceType_MethodsWithGeneric(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.MethodsWithGeneric");
    }

    default Packet ReferenceType_ClassFileVersion(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.ClassFileVersion");
    }

    default Packet ReferenceType_ConstantPool(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.ConstantPool");
    }

    default Packet ReferenceType_Module(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ReferenceType.Module");
    }

    int ClassType = 3;

    int ClassType_Superclass = 1;
    int ClassType_SetValues = 2;
    int ClassType_InvokeMethod = 3;
    int ClassType_NewInstance = 4;

    default Packet ClassType_Superclass(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassType.Superclass");
    }

    default Packet ClassType_SetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassType.SetValues");
    }

    default Packet ClassType_InvokeMethod(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassType.InvokeMethod");
    }

    default Packet ClassType_NewInstance(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassType.NewInstance");
    }

    int ArrayType = 4;

    int ArrayType_NewInstance = 1;

    default Packet ArrayType_NewInstance(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ArrayType.NewInstance");
    }

    int InterfaceType = 5;
    int InterfaceType_InvokeMethod = 1;

    default Packet InterfaceType_InvokeMethod(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("InterfaceType.InvokeMethod");
    }

    int Method = 6;
    int Method_LineTable = 1;
    int Method_VariableTable = 2;
    int Method_Bytecodes = 3;
    int Method_IsObsolete = 4;
    int Method_VariableTableWithGeneric = 5;

    default Packet Method_LineTable(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Method.LineTable");
    }

    default Packet Method_VariableTable(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Method.VariableTable");
    }

    default Packet Method_Bytecodes(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Method.Bytecodes");
    }

    default Packet Method_IsObsolete(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Method.IsObsolete");
    }

    default Packet Method_VariableTableWithGeneric(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Method.VariableTableWithGeneric");
    }

    int Field = 8;
    int ObjectReference = 9;

    int ObjectReference_ReferenceType = 1;
    int ObjectReference_GetValues = 2;
    int ObjectReference_SetValues = 3;
    int ObjectReference_MonitorInfo = 5;
    int ObjectReference_InvokeMethod = 6;
    int ObjectReference_DisableCollection = 7;
    int ObjectReference_EnableCollection = 8;
    int ObjectReference_IsCollected = 9;
    int ObjectReference_ReferringObjects = 10;

    default Packet ObjectReference_ReferenceType(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.ReferenceType");
    }

    default Packet ObjectReference_GetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.GetValues");
    }

    default Packet ObjectReference_SetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.SetValues");
    }

    default Packet ObjectReference_MonitorInfo(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.MonitorInfo");
    }

    default Packet ObjectReference_InvokeMethod(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.InvokeMethod");
    }

    default Packet ObjectReference_DisableCollection(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.DisableCollection");
    }

    default Packet ObjectReference_EnableCollection(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.EnableCollection");
    }

    default Packet ObjectReference_IsCollected(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.IsCollected");
    }

    default Packet ObjectReference_ReferringObjects(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ObjectReference.ReferringObjects");
    }

    int StringReference = 10;

    int StringReference_Value = 1;

    default Packet StringReference_Value(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("StringReference.Value");
    }

    int ThreadReference = 11;

    int ThreadReference_Name = 1;
    int ThreadReference_Suspend = 2;
    int ThreadReference_Resume = 3;
    int ThreadReference_Status = 4;
    int ThreadReference_ThreadGroup = 5;
    int ThreadReference_Frames = 6;
    int ThreadReference_FrameCount = 7;
    int ThreadReference_OwnedMonitors = 8;
    int ThreadReference_CurrentContendedMonitor = 9;
    int ThreadReference_Stop = 10;
    int ThreadReference_Interrupt = 11;
    int ThreadReference_SuspendCount = 12;
    int ThreadReference_OwnedMonitorsStackDepthInfo = 13;
    int ThreadReference_ForceEarlyReturn = 14;
    int ThreadReference_IsVirtual = 15;

    default Packet ThreadReference_Name(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Name");
    }

    default Packet ThreadReference_Suspend(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Suspend");
    }

    default Packet ThreadReference_Resume(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Resume");
    }

    default Packet ThreadReference_Status(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Status");
    }

    default Packet ThreadReference_ThreadGroup(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.ThreadGroup");
    }

    default Packet ThreadReference_Frames(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Frames");
    }

    default Packet ThreadReference_FrameCount(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.FrameCount");
    }

    default Packet ThreadReference_OwnedMonitors(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.OwnedMonitors");
    }

    default Packet ThreadReference_CurrentContendedMonitor(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.CurrentContendedMonitor");
    }

    default Packet ThreadReference_Stop(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Stop");
    }

    default Packet ThreadReference_Interrupt(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.Interrupt");
    }

    default Packet ThreadReference_SuspendCount(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.SuspendCount");
    }

    default Packet ThreadReference_OwnedMonitorsStackDepthInfo(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.OwnedMonitorsStackDepthInfo");
    }

    default Packet ThreadReference_ForceEarlyReturn(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.ForceEarlyReturn");
    }

    default Packet ThreadReference_IsVirtual(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadReference.IsVirtual");
    }

    int ThreadGroupReference = 12;

    int ThreadGroupReference_Name = 1;
    int ThreadGroupReference_Parent = 2;
    int ThreadGroupReference_Children = 3;

    default Packet ThreadGroupReference_Name(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadGroupReference.Name");
    }

    default Packet ThreadGroupReference_Parent(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadGroupReference.Parent");
    }

    default Packet ThreadGroupReference_Children(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ThreadGroupReference.Children");
    }

    int ArrayReference = 13;

    int ArrayReference_Length = 1;
    int ArrayReference_GetValues = 2;
    int ArrayReference_SetValues = 3;

    default Packet ArrayReference_Length(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ArrayReferenc.Length");
    }

    default Packet ArrayReference_GetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ArrayReferenc.GetValues");
    }

    default Packet ArrayReference_SetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ArrayReferenc.SetValues");
    }

    int ClassLoaderReference = 14;

    int ClassLoaderReference_VisibleClasses = 1;

    default Packet ClassLoaderReference_VisibleClasses(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassLoaderReference.VisibleClasses");
    }

    int EventRequest = 15;

    int EventRequest_Set = 1;
    int EventRequest_Clear = 2;
    int EventRequest_ClearAllBreakpoints = 3;

    default Packet EventRequest_Set(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("EventRequest.Set");
    }

    default Packet EventRequest_Clear(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("EventRequest.Clear");
    }

    default Packet EventRequest_ClearAllBreakpoints(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("EventRequest.ClearAllBreakpoints");
    }

    int StackFrame = 16;
    int StackFrame_GetValues = 1;
    int StackFrame_SetValues = 2;
    int StackFrame_ThisObject = 3;
    int StackFrame_PopFrames = 4;

    default Packet StackFrame_GetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("StackFrame.GetValues");
    }

    default Packet StackFrame_SetValues(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("StackFrame.SetValues");
    }

    default Packet StackFrame_ThisObject(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("StackFrame.ThisObject");
    }

    default Packet StackFrame_PopFrames(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("StackFrame.PopFrames");
    }

    int ClassObjectReference = 17;

    int ClassObjectReference_ReflectedType = 1;

    default Packet ClassObjectReference_ReflectedType(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ClassObjectReference.ReflectedType");
    }

    int ModuleReference = 18;

    int ModuleReference_Name = 1;
    int ModuleReference_ClassLoader = 2;

    default Packet ModuleReference_Name(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ModuleReference.Name");
    }

    default Packet ModuleReference_ClassLoader(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("ModuleReference.ClassLoader");
    }

    int Event = 64;
    int Event_Composite = 100;

    default Packet Event_Composite(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        throw VMError.unimplemented("Event.Composite");
    }

    default Packet dispatch(@SuppressWarnings("unused") Packet packet) throws JDWPException {
        //@formatter:off
        Packet response = switch (packet.commandSet()) {
            case VirtualMachine -> switch (packet.command()) {
                case VirtualMachine_Version               -> VirtualMachine_Version(packet);
                case VirtualMachine_ClassesBySignature    -> VirtualMachine_ClassesBySignature(packet);
                case VirtualMachine_AllClasses            -> VirtualMachine_AllClasses(packet);
                case VirtualMachine_AllThreads            -> VirtualMachine_AllThreads(packet);
                case VirtualMachine_TopLevelThreadGroups  -> VirtualMachine_TopLevelThreadGroups(packet);
                case VirtualMachine_Dispose               -> VirtualMachine_Dispose(packet);
                case VirtualMachine_IDSizes               -> VirtualMachine_IDSizes(packet);
                case VirtualMachine_Suspend               -> VirtualMachine_Suspend(packet);
                case VirtualMachine_Resume                -> VirtualMachine_Resume(packet);
                case VirtualMachine_Exit                  -> VirtualMachine_Exit(packet);
                case VirtualMachine_CreateString          -> VirtualMachine_CreateString(packet);
                case VirtualMachine_Capabilities          -> VirtualMachine_Capabilities(packet);
                case VirtualMachine_ClassPaths            -> VirtualMachine_ClassPaths(packet);
                case VirtualMachine_DisposeObjects        -> VirtualMachine_DisposeObjects(packet);
                case VirtualMachine_HoldEvents            -> VirtualMachine_HoldEvents(packet);
                case VirtualMachine_ReleaseEvents         -> VirtualMachine_ReleaseEvents(packet);
                case VirtualMachine_CapabilitiesNew       -> VirtualMachine_CapabilitiesNew(packet);
                case VirtualMachine_RedefineClasses       -> VirtualMachine_RedefineClasses(packet);
                case VirtualMachine_SetDefaultStratum     -> VirtualMachine_SetDefaultStratum(packet);
                case VirtualMachine_AllClassesWithGeneric -> VirtualMachine_AllClassesWithGeneric(packet);
                case VirtualMachine_InstanceCounts        -> VirtualMachine_InstanceCounts(packet);
                case VirtualMachine_AllModules            -> VirtualMachine_AllModules(packet);
                default ->
                        throw VMError.unimplemented("VirtualMachine command " + packet.command());
            };
            case ReferenceType -> switch (packet.command()) {
                case ReferenceType_Signature            -> ReferenceType_Signature(packet);
                case ReferenceType_ClassLoader          -> ReferenceType_ClassLoader(packet);
                case ReferenceType_Modifiers            -> ReferenceType_Modifiers(packet);
                case ReferenceType_Fields               -> ReferenceType_Fields(packet);
                case ReferenceType_Methods              -> ReferenceType_Methods(packet);
                case ReferenceType_GetValues            -> ReferenceType_GetValues(packet);
                case ReferenceType_SourceFile           -> ReferenceType_SourceFile(packet);
                case ReferenceType_NestedTypes          -> ReferenceType_NestedTypes(packet);
                case ReferenceType_Status               -> ReferenceType_Status(packet);
                case ReferenceType_Interfaces           -> ReferenceType_Interfaces(packet);
                case ReferenceType_ClassObject          -> ReferenceType_ClassObject(packet);
                case ReferenceType_SourceDebugExtension -> ReferenceType_SourceDebugExtension(packet);
                case ReferenceType_SignatureWithGeneric -> ReferenceType_SignatureWithGeneric(packet);
                case ReferenceType_FieldsWithGeneric    -> ReferenceType_FieldsWithGeneric(packet);
                case ReferenceType_MethodsWithGeneric   -> ReferenceType_MethodsWithGeneric(packet);
                case ReferenceType_Instances            -> ReferenceType_Instances(packet);
                case ReferenceType_ClassFileVersion     -> ReferenceType_ClassFileVersion(packet);
                case ReferenceType_ConstantPool         -> ReferenceType_ConstantPool(packet);
                case ReferenceType_Module               -> ReferenceType_Module(packet);
                default ->
                        throw VMError.unimplemented("ReferenceType command " + packet.command());
            };
            case ClassType -> switch (packet.command()) {
                case ClassType_Superclass   -> ClassType_Superclass(packet);
                case ClassType_SetValues    -> ClassType_SetValues(packet);
                case ClassType_InvokeMethod -> ClassType_InvokeMethod(packet);
                case ClassType_NewInstance  -> ClassType_NewInstance(packet);
                default ->
                        throw VMError.unimplemented("ClassType command " + packet.command());
            };
            case ArrayType -> switch (packet.command()) {
                case ArrayType_NewInstance -> ArrayType_NewInstance(packet);
                default ->
                        throw VMError.unimplemented("ArrayType command " + packet.command());
            };
            case InterfaceType -> switch (packet.command()) {
                case InterfaceType_InvokeMethod -> InterfaceType_InvokeMethod(packet);
                default ->
                        throw VMError.unimplemented("InterfaceType command " + packet.command());
            };
            case Method -> switch (packet.command()) {
                case Method_LineTable                -> Method_LineTable(packet);
                case Method_VariableTable            -> Method_VariableTable(packet);
                case Method_Bytecodes                -> Method_Bytecodes(packet);
                case Method_IsObsolete               -> Method_IsObsolete(packet);
                case Method_VariableTableWithGeneric -> Method_VariableTableWithGeneric(packet);
                default ->
                        throw VMError.unimplemented("Method command " + packet.command());
            };
            case Field -> throw VMError.unimplemented("Field command " + packet.command());
            case ObjectReference -> switch (packet.command()) {
                case ObjectReference_ReferenceType     -> ObjectReference_ReferenceType(packet);
                case ObjectReference_GetValues         -> ObjectReference_GetValues(packet);
                case ObjectReference_SetValues         -> ObjectReference_SetValues(packet);
                case ObjectReference_MonitorInfo       -> ObjectReference_MonitorInfo(packet);
                case ObjectReference_InvokeMethod      -> ObjectReference_InvokeMethod(packet);
                case ObjectReference_DisableCollection -> ObjectReference_DisableCollection(packet);
                case ObjectReference_EnableCollection  -> ObjectReference_EnableCollection(packet);
                case ObjectReference_IsCollected       -> ObjectReference_IsCollected(packet);
                case ObjectReference_ReferringObjects  -> ObjectReference_ReferringObjects(packet);
                default ->
                        throw VMError.unimplemented("ObjectReference command " + packet.command());
            };
            case StringReference -> switch (packet.command()) {
                case StringReference_Value -> StringReference_Value(packet);
                default ->
                        throw VMError.unimplemented("StringReference command " + packet.command());
            };
            case ThreadReference -> switch (packet.command()) {
                case ThreadReference_Name                        -> ThreadReference_Name(packet);
                case ThreadReference_Suspend                     -> ThreadReference_Suspend(packet);
                case ThreadReference_Resume                      -> ThreadReference_Resume(packet);
                case ThreadReference_Status                      -> ThreadReference_Status(packet);
                case ThreadReference_ThreadGroup                 -> ThreadReference_ThreadGroup(packet);
                case ThreadReference_Frames                      -> ThreadReference_Frames(packet);
                case ThreadReference_FrameCount                  -> ThreadReference_FrameCount(packet);
                case ThreadReference_OwnedMonitors               -> ThreadReference_OwnedMonitors(packet);
                case ThreadReference_CurrentContendedMonitor     -> ThreadReference_CurrentContendedMonitor(packet);
                case ThreadReference_Stop                        -> ThreadReference_Stop(packet);
                case ThreadReference_Interrupt                   -> ThreadReference_Interrupt(packet);
                case ThreadReference_SuspendCount                -> ThreadReference_SuspendCount(packet);
                case ThreadReference_OwnedMonitorsStackDepthInfo -> ThreadReference_OwnedMonitorsStackDepthInfo(packet);
                case ThreadReference_ForceEarlyReturn            -> ThreadReference_ForceEarlyReturn(packet);
                case ThreadReference_IsVirtual                   -> ThreadReference_IsVirtual(packet);
                default ->
                        throw VMError.unimplemented("ThreadReference command " + packet.command());
            };
            case ThreadGroupReference -> switch (packet.command()) {
                case ThreadGroupReference_Name     -> ThreadGroupReference_Name(packet);
                case ThreadGroupReference_Parent   -> ThreadGroupReference_Parent(packet);
                case ThreadGroupReference_Children -> ThreadGroupReference_Children(packet);
                default ->
                        throw VMError.unimplemented("ThreadGroupReference command " + packet.command());
            };
            case ArrayReference -> switch (packet.command()) {
                case ArrayReference_Length -> ArrayReference_Length(packet);
                case ArrayReference_GetValues -> ArrayReference_GetValues(packet);
                case ArrayReference_SetValues -> ArrayReference_SetValues(packet);
                default ->
                        throw VMError.unimplemented("ArrayReference command " + packet.command());
            };
            case ClassLoaderReference -> switch (packet.command()) {
                case ClassLoaderReference_VisibleClasses -> ClassLoaderReference_VisibleClasses(packet);
                default ->
                        throw VMError.unimplemented("ClassLoaderReference command " + packet.command());
            };
            case EventRequest -> switch (packet.command()) {
                case EventRequest_Set                 -> EventRequest_Set(packet);
                case EventRequest_Clear               -> EventRequest_Clear(packet);
                case EventRequest_ClearAllBreakpoints -> EventRequest_ClearAllBreakpoints(packet);
                default ->
                        throw VMError.unimplemented("EventRequest command " + packet.command());
            };
            case StackFrame -> switch (packet.command()) {
                case StackFrame_GetValues  -> StackFrame_GetValues(packet);
                case StackFrame_SetValues  -> StackFrame_SetValues(packet);
                case StackFrame_ThisObject -> StackFrame_ThisObject(packet);
                case StackFrame_PopFrames  -> StackFrame_PopFrames(packet);
                default ->
                        throw VMError.unimplemented("StackFrame command " + packet.command());
            };
            case ClassObjectReference -> switch (packet.command()) {
                case ClassObjectReference_ReflectedType -> ClassObjectReference_ReflectedType(packet);
                default ->
                        throw VMError.unimplemented("ClassObjectReference command " + packet.command());
            };
            case ModuleReference -> switch (packet.command()) {
                case ModuleReference_Name        -> ModuleReference_Name(packet);
                case ModuleReference_ClassLoader -> ModuleReference_ClassLoader(packet);
                default ->
                        throw VMError.unimplemented("ModuleReference command " + packet.command());
            };
            case Event -> switch (packet.command()) {
                case Event_Composite -> Event_Composite(packet);
                default ->
                        throw VMError.unimplemented("Event command " + packet.command());
            };
            default ->
                    throw VMError.unimplemented("CommandSet " + packet.commandSet());
        };
        //@formatter:on
        return response;
    }

    static String toString(int commandSet) {
        //@formatter:off
        return switch (commandSet) {
            case VirtualMachine       -> "VirtualMachine";
            case ReferenceType        -> "ReferenceType";
            case ClassType            -> "ClassType";
            case ArrayType            -> "ArrayType";
            case InterfaceType        -> "InterfaceType";
            case Method               -> "Method";
            case Field                -> "Field";
            case ObjectReference      -> "ObjectReference";
            case StringReference      -> "StringReference";
            case ThreadReference      -> "ThreadReference";
            case ThreadGroupReference -> "ThreadGroupReference";
            case ArrayReference       -> "ArrayReference";
            case ClassLoaderReference -> "ClassLoaderReference";
            case EventRequest         -> "EventRequest";
            case StackFrame           -> "StackFrame";
            case ClassObjectReference -> "ClassObjectReference";
            case ModuleReference      -> "ModuleReference";
            case Event                -> "Event";
            default                   -> unknown(commandSet);
        };
        //@formatter:on
    }

    static String toString(int commandSet, int command) {
        //@formatter:off
        return toString(commandSet) + "." + switch (commandSet) {
            case VirtualMachine -> switch (command) {
                case VirtualMachine_Version               -> "Version";
                case VirtualMachine_ClassesBySignature    -> "ClassesBySignature";
                case VirtualMachine_AllClasses            -> "AllClasses";
                case VirtualMachine_AllThreads            -> "AllThreads";
                case VirtualMachine_TopLevelThreadGroups  -> "TopLevelThreadGroups";
                case VirtualMachine_Dispose               -> "Dispose";
                case VirtualMachine_IDSizes               -> "IDSizes";
                case VirtualMachine_Suspend               -> "Suspend";
                case VirtualMachine_Resume                -> "Resume";
                case VirtualMachine_Exit                  -> "Exit";
                case VirtualMachine_CreateString          -> "CreateString";
                case VirtualMachine_Capabilities          -> "Capabilities";
                case VirtualMachine_ClassPaths            -> "ClassPaths";
                case VirtualMachine_DisposeObjects        -> "DisposeObjects";
                case VirtualMachine_HoldEvents            -> "HoldEvents";
                case VirtualMachine_ReleaseEvents         -> "ReleaseEvents";
                case VirtualMachine_CapabilitiesNew       -> "CapabilitiesNew";
                case VirtualMachine_RedefineClasses       -> "RedefineClasses";
                case VirtualMachine_SetDefaultStratum     -> "SetDefaultStratum";
                case VirtualMachine_AllClassesWithGeneric -> "AllClassesWithGeneric";
                case VirtualMachine_InstanceCounts        -> "InstanceCounts";
                case VirtualMachine_AllModules            -> "AllModules";
                default                                   -> unknown(command);
            };
            case ReferenceType -> switch (command) {
                case ReferenceType_Signature            -> "Signature";
                case ReferenceType_ClassLoader          -> "ClassLoader";
                case ReferenceType_Modifiers            -> "Modifiers";
                case ReferenceType_Fields               -> "Fields";
                case ReferenceType_Methods              -> "Methods";
                case ReferenceType_GetValues            -> "GetValues";
                case ReferenceType_SourceFile           -> "SourceFile";
                case ReferenceType_NestedTypes          -> "NestedTypes";
                case ReferenceType_Status               -> "Status";
                case ReferenceType_Interfaces           -> "Interfaces";
                case ReferenceType_ClassObject          -> "ClassObject";
                case ReferenceType_SourceDebugExtension -> "SourceDebugExtension";
                case ReferenceType_SignatureWithGeneric -> "SignatureWithGeneric";
                case ReferenceType_FieldsWithGeneric    -> "FieldsWithGeneric";
                case ReferenceType_MethodsWithGeneric   -> "MethodsWithGeneric";
                case ReferenceType_Instances            -> "Instances";
                case ReferenceType_ClassFileVersion     -> "ClassFileVersion";
                case ReferenceType_ConstantPool         -> "ConstantPool";
                case ReferenceType_Module               -> "Module";
                default                                 -> unknown(command);
            };
            case ClassType -> switch (command) {
                case ClassType_Superclass   -> "Superclass";
                case ClassType_SetValues    -> "SetValues";
                case ClassType_InvokeMethod -> "InvokeMethod";
                case ClassType_NewInstance  -> "NewInstance";
                default                     -> unknown(command);
            };
            case ArrayType -> switch (command) {
                case ArrayType_NewInstance -> "NewInstance";
                default                    -> unknown(command);
            };
            case InterfaceType -> switch (command) {
                case InterfaceType_InvokeMethod -> "InvokeMethod";
                default                         -> unknown(command);
            };
            case Method -> switch (command) {
                case Method_LineTable                -> "LineTable";
                case Method_VariableTable            -> "VariableTable";
                case Method_Bytecodes                -> "Bytecodes";
                case Method_IsObsolete               -> "IsObsolete";
                case Method_VariableTableWithGeneric -> "VariableTableWithGeneric";
                default                              -> unknown(command);
            };
            case Field -> unknown(command);
            case ObjectReference -> switch (command) {
                case ObjectReference_ReferenceType     -> "ReferenceType";
                case ObjectReference_GetValues         -> "GetValues";
                case ObjectReference_SetValues         -> "SetValues";
                case ObjectReference_MonitorInfo       -> "MonitorInfo";
                case ObjectReference_InvokeMethod      -> "InvokeMethod";
                case ObjectReference_DisableCollection -> "DisableCollection";
                case ObjectReference_EnableCollection  -> "EnableCollection";
                case ObjectReference_IsCollected       -> "IsCollected";
                case ObjectReference_ReferringObjects  -> "ReferringObjects";
                default                                -> unknown(command);
            };
            case StringReference -> switch (command) {
                case StringReference_Value -> "Value";
                default                    -> unknown(command);
            };
            case ThreadReference -> switch (command) {
                case ThreadReference_Name                        -> "Name";
                case ThreadReference_Suspend                     -> "Suspend";
                case ThreadReference_Resume                      -> "Resume";
                case ThreadReference_Status                      -> "Status";
                case ThreadReference_ThreadGroup                 -> "ThreadGroup";
                case ThreadReference_Frames                      -> "Frames";
                case ThreadReference_FrameCount                  -> "FrameCount";
                case ThreadReference_OwnedMonitors               -> "OwnedMonitors";
                case ThreadReference_CurrentContendedMonitor     -> "CurrentContendedMonitor";
                case ThreadReference_Stop                        -> "Stop";
                case ThreadReference_Interrupt                   -> "Interrupt";
                case ThreadReference_SuspendCount                -> "SuspendCount";
                case ThreadReference_OwnedMonitorsStackDepthInfo -> "OwnedMonitorsStackDepthInfo";
                case ThreadReference_ForceEarlyReturn            -> "ForceEarlyReturn";
                case ThreadReference_IsVirtual                   -> "IsVirtual";
                default                                          -> unknown(command);
            };
            case ThreadGroupReference -> switch (command) {
                case ThreadGroupReference_Name     -> "Name";
                case ThreadGroupReference_Parent   -> "Parent";
                case ThreadGroupReference_Children -> "Children";
                default                            -> unknown(command);
            };
            case ArrayReference -> switch (command) {
                case ArrayReference_Length    -> "Length";
                case ArrayReference_GetValues -> "GetValues";
                case ArrayReference_SetValues -> "SetValues";
                default                       -> unknown(command);
            };
            case ClassLoaderReference -> switch (command) {
                case ClassLoaderReference_VisibleClasses -> "VisibleClasses";
                default                                  -> unknown(command);
            };
            case EventRequest -> switch (command) {
                case EventRequest_Set                 -> "Set";
                case EventRequest_Clear               -> "Clear";
                case EventRequest_ClearAllBreakpoints -> "ClearAllBreakpoints";
                default                               -> unknown(command);
            };
            case StackFrame -> switch (command) {
                case StackFrame_GetValues  -> "GetValues";
                case StackFrame_SetValues  -> "SetValues";
                case StackFrame_ThisObject -> "ThisObject";
                case StackFrame_PopFrames  -> "PopFrames";
                default                    -> unknown(command);
            };
            case ClassObjectReference -> switch (command) {
                case ClassObjectReference_ReflectedType -> "ReflectedType";
                default                                 -> unknown(command);
            };
            case ModuleReference -> switch (command) {
                case ModuleReference_Name        -> "Name";
                case ModuleReference_ClassLoader -> "ClassLoader";
                default                          -> unknown(command);
            };
            case Event -> switch (command) {
                case Event_Composite -> "Composite";
                default              -> unknown(command);
            };
            default ->
                    unknown(command);
        };
        //@formatter:on
    }

    private static String unknown(int id) {
        return "<UNKNOWN(" + id + ")>";
    }

    static byte readTag(Packet.Reader reader) throws JDWPException {
        byte tag = (byte) reader.readByte();
        if (TagConstants.isValidTag(tag)) {
            return tag;
        }
        throw JDWPException.raise(ErrorCode.INVALID_TAG);
    }

}
// Checkstyle: resume method name check
// Checkstyle: resume field name check
