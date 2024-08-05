package com.oracle.graal.pointsto.reports.causality.events;

public enum EventKinds {
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
    HeapObjectClass("Class-Object in Heap"),
    HeapObjectDynamicHub("DynamicHub-Object in Heap"),
    UnknownHeapObject("Unknown Heap Object"),
    MethodCode("Impl"),
    VirtualJniCallVariantWrapper("Virtual JNI Call Variant Wrapper"),
    JniCallVariantWrapper("JNI Call Variant Wrapper"),
    JniRegistration("JNI Registration"),
    MethodGraphParsed("Method Graph Parsed"),
    Snippet("Snippet"),
    ReachabilityCallback("Reachability Callback"),
    MethodOverrideReachableCallback("Method Override Reachable Callback"),
    MethodOverrideReachableCallbackInvocation("Method Override Reachable Callback Invocation"),
    ReflectionObjectInHeap("Reflection Object In Heap"),
    ReflectionRegistration("Reflection Registration"),
    RootMethodRegistration("Root Registration"),
    SubtypeReachableNotificationCallback("Subtype Reachable Callback"),
    SubtypeReachableNotificationCallbackInvocation("Subtype Reachable Callback Invocation"),

    AutomaticFeatureRegistration("Automatic Feature Registration"),
    UserRequestedFeatureRegistration("User-Requested Feature Registration"),
    InitialRegistrations("Initial Registrations"),
    ;

    public final String name;
    public final String suffix;

    EventKinds(String name, String suffix) {
        this.name = name;
        this.suffix = suffix;
    }

    EventKinds() {
        name = "";
        suffix = "";
    }

    EventKinds(String name) {
        this.name = name;
        this.suffix = " [" + name + ']';
    }
}
