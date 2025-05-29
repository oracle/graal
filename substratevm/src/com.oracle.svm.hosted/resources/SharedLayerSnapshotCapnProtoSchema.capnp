@0x9eb32e19f86ee174;
using Java = import "/capnp/java.capnp";
$Java.package("com.oracle.svm.hosted.imagelayer");
$Java.outerClassname("SharedLayerSnapshotCapnProtoSchemaHolder");

using TypeId = Int32;
using MethodId = Int32;
using FieldId = Int32;
using ConstantId = Int32;
using SingletonObjId = Int32;
using HostedMethodIndex = Int32;

struct PersistedAnalysisType {
  id @0 :TypeId;
  descriptor @1 :Text;
  fields @2 :List(FieldId);
  hubIdentityHashCode @3 :Int32;
  classJavaName @4 :Text;
  className @5 :Text;
  modifiers @6 :Int32;
  # Most of these fields apply only to instances and could be in a union or a separate structure:
  isInterface @7 :Bool;
  isEnum @8 :Bool;
  isInitialized @9 :Bool;
  isInitializedAtBuildTime @10 :Bool;
  isLinked @11 :Bool;
  sourceFileName @12 :Text;
  enclosingTypeId @13 :TypeId;
  componentTypeId @14 :TypeId;
  superClassTypeId @15 :TypeId;
  isInstantiated @16 :Bool;
  isUnsafeAllocated @17 :Bool;
  isReachable @18 :Bool;
  interfaces @19 :List(TypeId);
  instanceFieldIds @20 :List(FieldId);
  instanceFieldIdsWithSuper @21 :List(FieldId);
  staticFieldIds @22 :List(FieldId);
  annotationList @23 :List(Annotation);
  classInitializationInfo @24 :ClassInitializationInfo;
  hasArrayType @25 :Bool;
  subTypes @26 :List(TypeId);
  isAnySubtypeInstantiated @27 :Bool;
  wrappedType :union {
    none @28 :Void; # default
    serializationGenerated :group {
      rawDeclaringClass @29 :Text;
      rawTargetConstructor @30 :Text;
    }
    lambda :group {
      capturingClass @31 :Text;
    }
    proxyType @32 :Void;
  }
}

struct ClassInitializationInfo {
  isNoInitializerNoTracking @0 :Bool;
  isInitializedNoTracking @1 :Bool;
  isFailedNoTracking @2 :Bool;
  isInitialized @3 :Bool;
  isInErrorState @4 :Bool;
  isLinked @5 :Bool;
  hasInitializer @6 :Bool;
  isBuildTimeInitialized @7 :Bool;
  isTracked @8 :Bool;
  initializerMethodId @9 :MethodId;
}

struct PersistedAnalysisMethod {
  id @0 :MethodId;
  descriptor @1 :Text;
  name @2 :Text;
  className @3 :Text;
  declaringTypeId @4 :TypeId;
  argumentClassNames @5 :List(Text);
  argumentTypeIds @6 :List(TypeId);
  returnTypeId @7 :TypeId;
  modifiers @8 :Int32;
  bytecode @9 :Data;
  bytecodeSize @10 :Int32;
  isConstructor @11 :Bool;
  isSynthetic @12 :Bool;
  canBeStaticallyBound @13 :Bool;
  isVirtualRootMethod @14 :Bool;
  isDirectRootMethod @15 :Bool;
  isInvoked @16 :Bool;
  isImplementationInvoked @17 :Bool;
  isIntrinsicMethod @18 :Bool;
  methodHandleIntrinsicName @19 :Text;
  annotationList @20 :List(Annotation);
  isVarArgs @21 :Bool;
  isBridge @22 :Bool;
  analysisGraphLocation @23 :Text;
  analysisGraphIsIntrinsic @24 :Bool;
  strengthenedGraphLocation @25 :Text;
  hostedMethodIndex @26 :HostedMethodIndex;
  wrappedMethod :union {
    none @27 :Void; # default
    factoryMethod :group {
      targetConstructorId @28 :MethodId;
      throwAllocatedObject @29 :Bool;
      instantiatedTypeId @30 :TypeId;
    }
    outlinedSB :group {
      methodTypeReturn @31 :Text;
      methodTypeParameters @32 :List(Text);
    }
    cEntryPointCallStub :group {
      originalMethodId @33 :MethodId;
      notPublished @34 :Bool;
    }
    wrappedMember :group {
      union {
        reflectionExpandSignature @35 :Void;
        javaCallVariantWrapper @36 :Void;
      }
      name @37 :Text;
      declaringClassName @38 :Text;
      argumentTypeNames @39 :List(Text);
    }
    polymorphicSignature :group {
      callers @40 :List(MethodId);
    }
  }
}

struct PersistedAnalysisField {
  id @0 :FieldId;
  className @1 :Text;
  declaringTypeId @2 :TypeId;
  typeId @3 :TypeId;
  position @4 :Int32;
  location @5 :Int32; # note currently we only read information about static fields' location
  modifiers @6 :Int32;
  isInternal @7 :Bool;
  isAccessed @8 :Bool;
  isRead @9 :Bool;
  isWritten @10 :Bool;
  isFolded @11 :Bool;
  isStatic @12 :Bool;
  isSynthetic @13 :Bool;
  annotationList @14 :List(Annotation);
  name @15 :Text;
  priorInstalledLayerNum @16 :Int32;
  assignmentStatus @17 :Int32;
}

struct CEntryPointLiteralReference {
  methodName @0 :Text;
  definingClass @1 :Text;
  parameterNames @2 :List(Text);
}

struct ConstantReference {
  union {
    objectConstant :group {
      constantId @0 :ConstantId;
    }
    nullPointer @1 :Void;
    notMaterialized @2 :Void;
    primitiveValue @3 :PrimitiveValue;
    methodPointer :group {
      methodId @4 :MethodId;
    }
    cEntryPointLiteralCodePointer @5 :CEntryPointLiteralReference;
    cGlobalDataBasePointer @6 :Void;
  }
}

struct PersistedConstant {
  id @0 :ConstantId;
  typeId @1 :TypeId;
  identityHashCode @2 :Int32;
  isSimulated @3 :Bool;
  objectOffset @4 :Int64;
  union {
    object :group {
      data @5 :List(ConstantReference);
      union {
        instance @6 :Void;
        objectArray @7 :Void;
      }
      relinking :union {
        notRelinked @8 :Void; # default
        stringConstant :group {
          value @9 :Text;
        }
        enumConstant :group {
          enumClass @10 :Text;
          enumName @11 :Text;
        }
        classConstant :group {
          typeId @12 :TypeId;
        }
        fieldConstant :group {
          originFieldId @13 :FieldId;
          requiresLateLoading @14 :Bool;
        }
      }
    }
    primitiveData @15 :PrimitiveArray;
    relocatable :group {
      key @16 :Text;
    }
  }
  parentConstantId @17 :ConstantId;
  parentIndex @18 :Int32;
}

struct KeyStoreEntry {
  key @0 :Text;
  value :union {
    i @1 :Int32;
    il @2 :List(Int32);
    j @3 :Int64;
    str @4 :Text;
    strl @5 :List(Text);
    zl @6 :List(Bool);
  }
}

struct ImageSingletonKey {
  keyClassName @0 :Text;
  persistFlag @1 :Int32;
  objectId @2 :SingletonObjId;
  constantId @3 :ConstantId;
}

struct ImageSingletonObject {
  id @0 :SingletonObjId;
  className @1 :Text;
  store @2 :List(KeyStoreEntry);
}

struct Annotation {
  typeName @0 :Text;
  values @1 :List(AnnotationValue);
}

struct AnnotationValue {
  name @0 :Text;
  union {
    string @1 :Text;
    primitive @2 :PrimitiveValue;
    primitiveArray @3 :PrimitiveArray;
    enum :group {
      className @4 :Text;
      name @5 :Text;
    }
    className @6 :Text;
    annotation @7 :Annotation;
    members :group {
      className @8 :Text;
      memberValues @9 :List(AnnotationValue);
    }
  }
}

struct SharedLayerSnapshot {
  nextTypeId @0 :TypeId;
  nextMethodId @1 :MethodId;
  nextFieldId @2 :FieldId;
  nextConstantId @3 :ConstantId;
  staticPrimitiveFieldsConstantId @4 :ConstantId;
  staticObjectFieldsConstantId @5 :ConstantId;
  imageHeapSize @6 :Int64;
  constantsToRelink @7 :List(ConstantId);
  types @8 :List(PersistedAnalysisType);
  methods @9 :List(PersistedAnalysisMethod);
  constants @10 :List(PersistedConstant);
  singletonKeys @11 :List(ImageSingletonKey);
  singletonObjects @12 :List(ImageSingletonObject);
  fields @13 :List(PersistedAnalysisField);
  nextLayerNumber @14 :Int32;
  staticFinalFieldFoldingSingleton @15 :StaticFinalFieldFoldingSingleton;
  registeredJNILibraries @16 :List(Text);
  layeredRuntimeMetadataSingleton @17 :LayeredRuntimeMetadataSingleton;
  dynamicHubInfos @18 :List(DynamicHubInfo);
  hostedMethods @19 :List(PersistedHostedMethod);
  nodeClassMapLocation @20 :Text;
  sharedLayerBootLayerModules @21 :List(Text);
  layeredModule @22 :LayeredModule;
}

struct StaticFinalFieldFoldingSingleton {
  fields @0 :List(FieldId);
  fieldCheckIndexes @1 :List(Int32);
  fieldInitializationStatusList @2 :List(Bool);
  bytecodeParsedFoldedFieldValues @3 :List(ConstantReference);
  afterParsingHooksDoneFoldedFieldValues @4 :List(ConstantReference);
}

struct LayeredRuntimeMetadataSingleton {
  methods @0 :List(MethodId);
  fields @1 :List(FieldId);
}

struct LayeredModule {
  openModulePackages @0 :List(ModulePackages);
  exportedModulePackages @1 :List(ModulePackages);
}

struct ModulePackages {
  moduleKey @0 :Text;
  packages @1 :List(Packages);
}

struct Packages {
  packageKey @0 :Text;
  modules @1 :List(Text);
}

struct PrimitiveValue {
  typeChar @0 :Int8;
  rawValue @1 :Int64;
}

struct PrimitiveArray {
  union {
    z @0 :List(Bool);
    b @1 :List(Int8);
    s @2 :List(Int16);
    c @3 :List(UInt16);
    i @4 :List(Int32);
    f @5 :List(Float32);
    j @6 :List(Int64);
    d @7 :List(Float64);
  }
}

struct DispatchSlotInfo {
    declaredHostedMethodIndex @0 :HostedMethodIndex;
    resolvedHostedMethodIndex @1 :HostedMethodIndex;
    slotIndex @2 :Int32;
    resolutionStatus @3 :Int32;
    slotSymbolName @4 :Text;
}

struct PersistedHostedMethod {
    index @0 :Int32;
    methodId @1 :MethodId;
    vTableIndex @2 :Int32;
    installedOffset @3 :Int32;
    isVirtualCallTarget @4 :Bool;
    symbolName @5 :Text;
    hostedMethodName @6 :Text;
    hostedMethodUniqueName @7 :Text;
}

struct DynamicHubInfo {
    typeId @0 :TypeId;
    installed @1 :Bool;
    typecheckId @2 :Int32;
    numClassTypes @3 :Int32;
    numInterfaceTypes @4 :Int32;
    typecheckSlotValues @5 :List(Int32);
    locallyDeclaredSlotsHostedMethodIndexes @6 :List(HostedMethodIndex);
    dispatchTableSlotValues @7 :List(DispatchSlotInfo);
}
