package com.oracle.svm.hosted.analysis.ai.example.access.inter;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathConstants;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathElement;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathMap;
import com.oracle.svm.hosted.analysis.ai.domain.access.ClassAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.PlaceHolderAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.List;

public class AccessPathIntervalSummaryFactory implements SummaryFactory<AccessPathMap<IntInterval>> {

    @Override
    public Summary<AccessPathMap<IntInterval>> createSummary(Invoke invoke,
                                                             AccessPathMap<IntInterval> callerPreCondition,
                                                             List<AccessPathMap<IntInterval>> domainArguments) {
        /**
         * Here we need to create a summary that takes a pre-condition and a list of actual arguments in the environment domain
         * and creates an initial callee environment domain. We should remove all access paths that will be accessible only in the caller environment,
         * and keep only those, that are accessible in the callee.
         * We will only keep:
         *                   1. Primitive type arguments (We will pass them as placeholder access paths for them like param0: [1, 10])
         *                   2. Object type arguments (We will pass all access paths that have this object as their base)
         *                   3. Fields inherited from the caller (We will pass them as-is since they're accessible)
         */
        AccessPathMap<IntInterval> preCondition = new AccessPathMap<>(new IntInterval());
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();

        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);

            /* Ad 1. */
            if (arg.getStackKind().isNumericInteger()) {
                AccessPathBase base = new PlaceHolderAccessPathBase(AccessPathConstants.PARAM_PREFIX + i);
                AccessPath paramAccessPath = new AccessPath(base);
                preCondition.put(paramAccessPath, domainArguments.get(i).getOnlyDataValue());
            }
            /* Ad 2. */
            else if (arg.getStackKind().isObject()) {
                AccessPathBase objectBase = domainArguments.get(i).getOnlyAccessPathBase();
                List<AccessPath> accessPathsWithBase = callerPreCondition.getAccessPathsWithBase(objectBase);
                accessPathsWithBase.add(new AccessPath(objectBase));

                for (AccessPath accessPath : accessPathsWithBase) {
                    IntInterval value = callerPreCondition.get(accessPath);
                    AccessPath prefixedPath = new AccessPath(accessPath.getBase().addPrefix(AccessPathConstants.PARAM_PREFIX + i), accessPath.getElements());
                    preCondition.put(prefixedPath, value);
                }
            }
        }

        /* Ad 3. */
        for (AccessPath path : callerPreCondition.getAccessPaths()) {
            if (!(path.getBase() instanceof ClassAccessPathBase)) {
                continue;
            }

            /* Only propagate if the field is accessible to the callee method */
            if (isAccessibleStaticField(path, invoke.callTarget().targetMethod())) {
                IntInterval value = callerPreCondition.get(path);
                preCondition.put(path, value);
            }
        }

        return new AccessPathIntervalSummary(preCondition, invoke);
    }

    /**
     * Determines if a static field is accessible to a target method based on Java visibility rules.
     *
     * @param path         The access path to check (must have a ClassAccessPathBase)
     * @param targetMethod The method that will access the field
     * @return true if the field is accessible, false otherwise
     */
    private boolean isAccessibleStaticField(AccessPath path, ResolvedJavaMethod targetMethod) {
        /* Ensure path has elements and the first one is a field */
        if (path.getElements().isEmpty() || !path.getElements().getFirst().getKind().equals(AccessPathElement.Kind.FIELD)) {
            return false;
        }

        ClassAccessPathBase classBase = (ClassAccessPathBase) path.getBase();
        String fieldName = path.getElements().getFirst().getName();

        // Get the field from the class
        ResolvedJavaField field = null;
        for (ResolvedJavaField f : classBase.type().getStaticFields()) {
            if (f.getName().equals(fieldName)) {
                field = f;
                break;
            }
        }

        if (field == null) {
            return false; /* Field not found */
        }

        /* Check accessibility based on Java rules */
        ResolvedJavaType declaringClass = field.getDeclaringClass();
        ResolvedJavaType accessingClass = targetMethod.getDeclaringClass();

        /* 1. Public fields are accessible from anywhere */
        if (field.isPublic()) {
            return true;
        }

        /* 2. Private fields are only accessible within the same class */
        if (field.isPrivate()) {
            return declaringClass.equals(accessingClass);
        }

        /* 3. Protected fields are accessible within the same package or subclasses */
        if (field.isProtected()) {
            /* Check if accessing class is in the same package */
            if (isSamePackage(declaringClass, accessingClass)) {
                return true;
            }

            /* Check if accessing class is a subclass of declaring class */
            return accessingClass.isAssignableFrom(declaringClass);
        }

        /* 4. Default (package-private) fields are accessible within the same package */
        return isSamePackage(declaringClass, accessingClass);
    }

    /**
     * Determines if two classes are in the same package.
     */
    private boolean isSamePackage(ResolvedJavaType type1, ResolvedJavaType type2) {
        String pkg1 = getPackageName(type1.toJavaName());
        String pkg2 = getPackageName(type2.toJavaName());
        return pkg1.equals(pkg2);
    }

    /**
     * Extracts package name from a fully qualified class name.
     */
    private String getPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot == -1) {
            return ""; // Default package
        }
        return className.substring(0, lastDot);
    }
}
