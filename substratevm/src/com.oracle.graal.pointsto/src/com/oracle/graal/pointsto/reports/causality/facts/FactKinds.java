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
package com.oracle.graal.pointsto.reports.causality.facts;

public enum FactKinds {
    Reachable,
    ImplementationInvoked("Implementation Invoked"),
    VirtualInvoke("Virtual Invoke"),
    Inlined("Inlined"),
    Instantiated("Instantiated"),
    TypeInHeap("Type In Heap"),
    BuildTimeClassInitialization("Build-Time", ".<clinit>() [Build-Time]"),
    ConfigurationCondition("Configuration Condition"),
    ConfigurationFile("Configuration File"),
    Feature("Feature"),
    FieldRead("Read"),
    FieldWritten("Written"),
    FieldIsRecomputed("Recomputed"),
    HeapObjectDynamicHub("DynamicHub-Object in Heap"),
    UnknownHeapObject("Unknown Heap Object"),
    MethodCode("Impl"),
    VirtualJniCallVariantWrapper("Virtual JNI Call Variant Wrapper"),
    JniCallVariantWrapper("JNI Call Variant Wrapper"),
    JniRegistration("JNI Registration"),
    MethodGraphParsed("Method Graph Parsed"),
    MethodIsEntryPoint("Entry Point"),
    Snippet("Snippet"),
    ReachabilityCallback("Reachability Callback"),
    MethodOverrideReachableCallback("Method Override Reachable Callback"),
    MethodOverrideReachableCallbackInvocation("Method Override Reachable Callback Invocation"),
    ReflectionObjectInHeap("Reflection Object In Heap"),
    ReflectionRegistration("Reflection Registration"),
    RootMethodRegistration("Root Registration"),
    SubtypeReachableNotificationCallback("Subtype Reachable Callback"),
    SubtypeReachableNotificationCallbackInvocation("Subtype Reachable Callback Invocation"),

    DeferredTask("Deferred Task"),

    CauseConnection("Cause Connection"),
    CauseConnectionStack("Cause Connection Stack"),

    AutomaticFeatureRegistration("Automatic Feature Registration"),
    UserRequestedFeatureRegistration("User-Requested Feature Registration"),
    InitialRegistrations("Initial Registrations"),
    StructuralProperty("Structural Property");

    public final String name;
    public final String suffix;

    FactKinds(String name, String suffix) {
        this.name = name;
        this.suffix = suffix;
    }

    FactKinds() {
        name = "";
        suffix = "";
    }

    FactKinds(String name) {
        this.name = name;
        this.suffix = " [" + name + ']';
    }
}
