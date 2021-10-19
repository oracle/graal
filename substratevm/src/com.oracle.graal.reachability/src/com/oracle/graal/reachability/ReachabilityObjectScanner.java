package com.oracle.graal.reachability;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanningObserver;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class ReachabilityObjectScanner implements ObjectScanningObserver {

    private final ReachabilityAnalysis bb;
    private final AnalysisMetaAccess access;

    public ReachabilityObjectScanner(BigBang bb, AnalysisMetaAccess access) {
        this.bb = ((ReachabilityAnalysis) bb);
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
// getAnalysis().markTypeInstantiated(constantType(bb, fieldValue));
        }
    }

    @Override
    public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
        if (receiver != null) {
            getAnalysis().markTypeReachable(constantType(bb, receiver));
        }
        getAnalysis().markTypeReachable(field.getType());
// System.out.println("Scanning field " + field);
    }

    @Override
    public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        if (receiver != null)
            getAnalysis().markTypeReachable(constantType(bb, receiver));
        getAnalysis().markTypeReachable(field.getType());
// System.out.println("Scanning field " + field);
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
    public void forScannedConstant(JavaConstant scannedValue, ObjectScanner.ScanReason reason) {
        AnalysisType type = constantType(bb, scannedValue);
// System.out.println("Scanning constant of type " + type);
        getAnalysis().markTypeInstantiated(type);
        type.registerAsInHeap();
    }

    private AnalysisType constantType(BigBang bb, JavaConstant constant) {
        return access.lookupJavaType(constantAsObject(bb, constant).getClass());
    }

    public Object constantAsObject(BigBang bb, JavaConstant constant) {
        return bb.getSnippetReflectionProvider().asObject(Object.class, constant);
    }

}