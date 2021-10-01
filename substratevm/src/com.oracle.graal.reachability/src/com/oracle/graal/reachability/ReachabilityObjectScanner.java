package com.oracle.graal.reachability;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class ReachabilityObjectScanner extends ObjectScanner {

    private final AnalysisMetaAccess access;

    public ReachabilityObjectScanner(BigBang bb, CompletionExecutor executor, ReusableSet scannedObjects, AnalysisMetaAccess access) {
        super(bb, executor, scannedObjects);
        this.access = access;
    }

    ReachabilityAnalysis getAnalysis() {
        return ((ReachabilityAnalysis) bb);
    }

    @Override
    public void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        field.registerAsAccessed();
        if (fieldValue.isNonNull() && fieldValue.getJavaKind() == JavaKind.Object) {
            // todo mark as instantiated
//            getAnalysis().markTypeInstantiated(constantType(bb, fieldValue));
        }
    }

    @Override
    public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
        if (receiver != null)
            getAnalysis().markTypeReachable(constantType(bb, receiver));
        getAnalysis().markTypeReachable(field.getType());
//        System.out.println("Scanning field " + field);
    }

    @Override
    public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        if (receiver != null)
            getAnalysis().markTypeReachable(constantType(bb, receiver));
        getAnalysis().markTypeReachable(field.getType());
//        System.out.println("Scanning field " + field);
    }

    @Override
    public void forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex) {
        getAnalysis().markTypeReachable(arrayType);
    }

    @Override
    public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex) {
        getAnalysis().markTypeReachable(arrayType);
        getAnalysis().markTypeInstantiated(elementType);
    }

    @Override
    protected void forScannedConstant(JavaConstant scannedValue, ScanReason reason) {
        AnalysisType type = constantType(bb, scannedValue);
//        System.out.println("Scanning constant of type " + type);
        getAnalysis().markTypeInstantiated(type);
        type.registerAsInHeap();
    }
}