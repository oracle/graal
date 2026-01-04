/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.spi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * A {@link ProfileProvider} that caches the answer to all queries so that each query returns the
 * same answer for the entire compilation. This can improve the consistency of compilation results.
 */
public class StableProfileProvider implements ProfileProvider {

    private static final Class<?> TRANSLATED_EXCEPTION;
    static {
        Class<?> clz;
        try {
            clz = Class.forName("jdk.internal.vm.TranslatedException");
        } catch (ClassNotFoundException cnf) {
            clz = null;
        }
        TRANSLATED_EXCEPTION = clz;
    }

    private final TypeResolver resolver;

    /**
     * The profile provider uses a type resolver during profile replay to resolve the string type
     * names in type profiles to {@link ResolvedJavaType}s.
     */
    public interface TypeResolver {
        /**
         * Try to resolve the given {@code typeName} to a {@link ResolvedJavaType}.
         *
         * @param method the method containing the type profile in which the type name occurs
         * @param realProfile the currently loaded profile of {@code method}
         * @param bci the bci at which the type occurred
         * @param loadingIssuingType the type that triggered the load of the respective profile
         * @param typeName the type name to be resolved
         * @return a {@link ResolvedJavaType} if the type can be resolved, null otherwise
         */
        ResolvedJavaType resolve(ResolvedJavaMethod method, ProfilingInfo realProfile, int bci, ResolvedJavaType loadingIssuingType, String typeName);
    }

    private static boolean isTranslatedNoClassDefFound(Throwable e) {
        Throwable effectiveException = e;
        if (TRANSLATED_EXCEPTION != null && TRANSLATED_EXCEPTION.isInstance(e)) {
            /*
             * As of JDK 24 (JDK-8335553), a translated exception is boxed in a TranslatedException.
             * Unbox a translated unchecked exception to get the real one.
             */
            Throwable cause = e.getCause();
            effectiveException = cause;
        }
        return effectiveException instanceof NoClassDefFoundError;
    }

    /**
     * Default strategy for resolving type names into {@link ResolvedJavaType}s. This resolver
     * follows a four-step process to resolve types:
     * <ol>
     * <li>If the type exists in the real profile, use the type from the real profile</li>
     * <li>Try to resolve the type with the method owner as context</li>
     * <li>Try to resolve the type with the load triggering type as context</li>
     * <li>Try to resolve the type with any type occurring in the real profile as context</li>
     * </ol>
     */
    public static class DefaultTypeResolver implements TypeResolver {
        @Override
        public ResolvedJavaType resolve(ResolvedJavaMethod method, ProfilingInfo realProfile, int bci, ResolvedJavaType loadingIssuingType, String typeName) {
            JavaTypeProfile actualProfile = realProfile.getTypeProfile(bci);

            ResolvedJavaType actualType = null;
            if (actualProfile != null) {
                for (JavaTypeProfile.ProfiledType actual : actualProfile.getTypes()) {
                    if (actual.getType().getName().equals(typeName)) {
                        actualType = actual.getType();
                        break;
                    }
                }
            }

            if (actualType == null) {
                try {
                    actualType = UnresolvedJavaType.create(typeName).resolve(method.getDeclaringClass());
                } catch (Throwable t) {
                    if (isTranslatedNoClassDefFound(t)) {
                        // do nothing
                    } else {
                        throw t;
                    }
                }
            }

            if (actualType == null) {
                // try using the original type issuing the load operation of this profile
                try {
                    actualType = UnresolvedJavaType.create(typeName).resolve(loadingIssuingType);
                } catch (Throwable t) {
                    if (isTranslatedNoClassDefFound(t)) {
                        // do nothing
                    } else {
                        throw t;
                    }
                }
            }
            if (actualType == null) {
                if (actualProfile != null) {
                    for (JavaTypeProfile.ProfiledType actual : actualProfile.getTypes()) {
                        try {
                            actualType = UnresolvedJavaType.create(typeName).resolve(actual.getType());
                        } catch (Throwable t) {
                            if (isTranslatedNoClassDefFound(t)) {
                                // do nothing
                            } else {
                                throw t;
                            }
                        }
                        if (actualType != null) {
                            break;
                        }
                    }
                }
            }

            return actualType;
        }
    }

    public StableProfileProvider(TypeResolver resolver) {
        this.resolver = resolver;
    }

    public StableProfileProvider() {
        this.resolver = new DefaultTypeResolver();
    }

    private static final JavaTypeProfile NULL_PROFILE = new JavaTypeProfile(TriState.UNKNOWN, 1.0, new JavaTypeProfile.ProfiledType[0]);

    private static final double[] NULL_SWITCH_PROBABILITIES = new double[0];

    public static final String METHOD_FORMAT = "%H.%n(%P)%R";

    /**
     * Profiles queried and cached in the enclosing root compilation.
     */
    private final EconomicMap<ProfileKey, CachingProfilingInfo> profiles = EconomicMap.create();
    /**
     * Lazy loaded profiles from disk.
     */
    private EconomicMap<String, CachingProfilingInfo> loaded;
    private boolean frozen;
    private boolean warnNonCachedLoadAccess;

    @Override
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method) {
        return getProfilingInfo(method, true, true);
    }

    public static class ProfileKey {
        final ResolvedJavaMethod method;
        final boolean includeNormal;
        final boolean includeOSR;

        ProfileKey(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
            this.method = method;
            this.includeNormal = includeNormal;
            this.includeOSR = includeOSR;
        }

        public ResolvedJavaMethod method() {
            return method;
        }

        public boolean includeNormal() {
            return includeNormal;
        }

        public boolean includeOSR() {
            return includeOSR;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ProfileKey that = (ProfileKey) o;
            return includeNormal == that.includeNormal && includeOSR == that.includeOSR && method.equals(that.method);
        }

        @Override
        public int hashCode() {
            return method.hashCode() + (includeNormal ? 1 : 0) + (includeOSR ? 2 : 0);
        }
    }

    @Override
    public ProfilingInfo getProfilingInfo(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
        // In the normal case true is passed for both arguments but the root method of the compile
        // will pass true for only one of these flags.
        ProfileKey key = new ProfileKey(method, includeNormal, includeOSR);

        CachingProfilingInfo profile = profiles.get(key);
        if (profile == null && loaded != null) {
            profile = loaded.get(method.format(METHOD_FORMAT));
            if (profile != null) {
                profile.materialize(method);
            } else {
                if (loadingLambdaNameFormatter != null) {
                    // try to find it as a lambda method
                    profile = loaded.get(loadingLambdaNameFormatter.formatLamdaName(method));
                    if (profile != null) {
                        profile.materialize(method);
                    }
                }
            }
        }
        if (profile == null) {
            if (frozen) {
                throw new InternalError("frozen - cannot get method " + method.format(METHOD_FORMAT));
            } else {
                if (loaded != null && warnNonCachedLoadAccess) {
                    TTY.printf("Requesting not cached profile for %s%n", method.format(METHOD_FORMAT));
                }
            }
            profile = new CachingProfilingInfo(method, includeNormal, includeOSR);
            profiles.put(key, profile);
        }
        return profile;
    }

    /**
     * Lazy cache of the per-method profile queries.
     */
    public class CachingProfilingInfo implements ProfilingInfo {

        /**
         * Lazy cache of the per bytecode profile queries.
         */
        class BytecodeProfile {
            final int bci;
            TriState exceptionSeen;
            TriState nullSeen;
            Double branchTakenProbability;
            double[] switchProbabilities;
            Integer executionCount;
            JavaTypeProfile typeProfile;
            EconomicMap<String, Object> symbolicTypeProfile;

            BytecodeProfile(int bci) {
                this.bci = bci;
            }

            @SuppressWarnings("unchecked")
            BytecodeProfile(EconomicMap<String, Object> bciData) {
                bci = (int) bciData.get("bci");
                exceptionSeen = parseTriState((String) bciData.get("exceptionSeen"));
                nullSeen = parseTriState((String) bciData.get("nullSeen"));
                executionCount = (Integer) bciData.get("executionCount");
                branchTakenProbability = (Double) bciData.get("branchTakenProbability");
                List<?> probs = (List<?>) bciData.get("switchProbabilities");
                if (probs != null) {
                    switchProbabilities = new double[probs.size()];
                    for (int i = 0; i < probs.size(); i++) {
                        switchProbabilities[i] = (double) probs.get(i);
                    }
                }
                EconomicMap<String, Object> profileData = (EconomicMap<String, Object>) bciData.get("typeProfile");
                if (profileData != null) {
                    symbolicTypeProfile = profileData;
                }
            }

            @SuppressWarnings("unchecked")
            void materializeProfile() {
                if (symbolicTypeProfile == null) {
                    return;
                }
                TriState theNullSeen = parseTriState((String) symbolicTypeProfile.get("nullSeen"));
                double notRecordedProbabilty = (double) symbolicTypeProfile.get("notRecordedProbability");
                List<?> types = (List<?>) symbolicTypeProfile.get("types");
                ArrayList<JavaTypeProfile.ProfiledType> recoverableTypes = new ArrayList<>();
                for (Object e : types) {
                    EconomicMap<String, Object> entry = (EconomicMap<String, Object>) e;
                    String typeName = (String) entry.get("type");

                    double probability = (double) entry.get("probability");

                    ResolvedJavaType actualType = null;
                    actualType = resolver.resolve(method, realProfile, bci, loadingIssuingType, typeName);

                    if (actualType == null) {
                        if (frozen) {
                            throw new InternalError("Unable to load " + typeName + " for profile");
                        } else {
                            TTY.println("Unable to load " + typeName + " for profile");
                        }
                    } else {
                        /*
                         * Only create a JavaTypeProfile if we could resolve the type. The
                         * ProfiledType constructor will fail otherwise.
                         */
                        recoverableTypes.add(new JavaTypeProfile.ProfiledType(actualType, probability));
                    }
                }
                typeProfile = new JavaTypeProfile(theNullSeen, notRecordedProbabilty, recoverableTypes.toArray(new JavaTypeProfile.ProfiledType[0]));
            }
        }

        private ResolvedJavaMethod method;

        /**
         * The underlying profiling object used for queries.
         */
        private ProfilingInfo realProfile;

        private Boolean isMature;

        private Integer compilerIRSize;

        /**
         * Per bci profiles.
         */
        private final EconomicMap<Integer, BytecodeProfile> bytecodeProfiles;

        /**
         * The original type issuing the load of the CachingProfile from disk. Needed for resolution
         * of type profiles.
         */
        private ResolvedJavaType loadingIssuingType;

        private boolean materialized;

        CachingProfilingInfo(ResolvedJavaMethod method, boolean includeNormal, boolean includeOSR) {
            this.method = method;
            this.realProfile = method.getProfilingInfo(includeNormal, includeOSR);
            this.bytecodeProfiles = EconomicMap.create();
        }

        @SuppressWarnings("unchecked")
        CachingProfilingInfo(EconomicMap<String, Object> map) {
            isMature = (Boolean) map.get("isMature");
            compilerIRSize = (Integer) map.get("compilerIRSize");
            bytecodeProfiles = EconomicMap.create();
            List<?> data = (List<?>) map.get("data");
            if (data != null) {
                for (Object d : data) {
                    BytecodeProfile b = new BytecodeProfile((EconomicMap<String, Object>) d);
                    bytecodeProfiles.put(b.bci, b);
                }
            }
        }

        private void checkFrozen(int bci, String caller) {
            if (frozen) {
                throw new InternalError("Profile is frozen and profiling information was requested for " + method + "@" + bci);
            } else {
                if (loaded != null && warnNonCachedLoadAccess) {
                    TTY.printf("Requesting uncached profile for bci %d in %s method %s%n", bci, method.format(METHOD_FORMAT), caller);
                }
            }
        }

        @Override
        public int getCodeSize() {
            return realProfile.getCodeSize();
        }

        @Override
        public double getBranchTakenProbability(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.branchTakenProbability == null) {
                checkFrozen(bci, "getBranchTakenProbability");
                cached.branchTakenProbability = realProfile.getBranchTakenProbability(bci);
            }
            return cached.branchTakenProbability;
        }

        @Override
        public double[] getSwitchProbabilities(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.switchProbabilities == null) {
                checkFrozen(bci, "getSwitchProbabilities");
                cached.switchProbabilities = realProfile.getSwitchProbabilities(bci);
                if (cached.switchProbabilities == null) {
                    cached.switchProbabilities = NULL_SWITCH_PROBABILITIES;
                }
            }
            double[] ret = cached.switchProbabilities == NULL_SWITCH_PROBABILITIES ? null : cached.switchProbabilities;
            return ret;
        }

        @Override
        public JavaTypeProfile getTypeProfile(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.symbolicTypeProfile != null) {
                cached.materializeProfile();
            }
            if (cached.typeProfile == null) {
                checkFrozen(bci, "getTypeProfile");
                cached.typeProfile = realProfile.getTypeProfile(bci);
                if (cached.typeProfile == null) {
                    cached.typeProfile = NULL_PROFILE;
                }
            }
            JavaTypeProfile ret = cached.typeProfile == NULL_PROFILE ? null : cached.typeProfile;
            GraalError.guarantee(ret != NULL_PROFILE, "Must never return null profile");
            return ret;
        }

        @Override
        public JavaMethodProfile getMethodProfile(int bci) {
            return null;
        }

        @Override
        public TriState getExceptionSeen(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.exceptionSeen == null) {
                checkFrozen(bci, "getExceptionSeen");
                cached.exceptionSeen = realProfile.getExceptionSeen(bci);
            }
            return cached.exceptionSeen;
        }

        private BytecodeProfile getBytecodeProfile(int bci) {
            BytecodeProfile cached = bytecodeProfiles.get(bci);
            if (cached == null) {
                checkFrozen(bci, "getBytecodeProfile");
                cached = new BytecodeProfile(bci);
                bytecodeProfiles.put(bci, cached);
            }
            return cached;
        }

        @Override
        public TriState getNullSeen(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.nullSeen == null) {
                checkFrozen(bci, "getNullSeen");
                cached.nullSeen = realProfile.getNullSeen(bci);
            }
            return cached.nullSeen;
        }

        @Override
        public int getExecutionCount(int bci) {
            BytecodeProfile cached = getBytecodeProfile(bci);
            if (cached.executionCount == null) {
                checkFrozen(bci, "getExceutionCount");
                cached.executionCount = realProfile.getExecutionCount(bci);
            }
            return cached.executionCount;
        }

        @Override
        public int getDeoptimizationCount(DeoptimizationReason reason) {
            return realProfile.getDeoptimizationCount(reason);
        }

        @Override
        public boolean setCompilerIRSize(Class<?> irType, int irSize) {
            return realProfile.setCompilerIRSize(irType, irSize);
        }

        @Override
        public int getCompilerIRSize(Class<?> irType) {
            assert irType == StructuredGraph.class;
            if (compilerIRSize == null) {
                if (frozen) {
                    throw new InternalError("Profile is frozen and profiling information was requested for " + method);
                }
                compilerIRSize = realProfile.getCompilerIRSize(irType);
            }
            return compilerIRSize;
        }

        @Override
        public boolean isMature() {
            if (isMature == null) {
                if (frozen) {
                    throw new InternalError("Profile is frozen and profiling information was requested for " + method);
                }
                isMature = realProfile.isMature();
            }
            return isMature;
        }

        @Override
        public void setMature() {
            throw new UnsupportedOperationException();
        }

        private EconomicMap<String, Object> asMap(Object signature, TypeFilter filter, LambdaNameFormatter lambdaFormatter) {
            EconomicMap<String, Object> profileRecord = EconomicMap.create();
            if (signature instanceof String) {
                profileRecord.put("method", signature);
            } else {
                ProfileKey key = (ProfileKey) signature;
                ResolvedJavaMethod m = key.method;
                if (lambdaFormatter.isLambda(m)) {
                    profileRecord.put("stableLambdaName", lambdaFormatter.formatLamdaName(m));
                }
                profileRecord.put("method", m.format(METHOD_FORMAT));
            }
            profileRecord.put("isMature", this.isMature == null ? false : this.isMature);
            profileRecord.put("compilerIRSize", compilerIRSize == null ? -1 : compilerIRSize);
            ArrayList<EconomicMap<String, Object>> bciData = new ArrayList<>();
            for (BytecodeProfile data : bytecodeProfiles.getValues()) {
                EconomicMap<String, Object> v = EconomicMap.create();
                v.put("bci", data.bci);
                if (data.exceptionSeen != null) {
                    v.put("exceptionSeen", data.exceptionSeen);
                }
                if (data.nullSeen != null) {
                    v.put("nullSeen", data.nullSeen);
                }
                if (data.executionCount != null) {
                    v.put("executionCount", data.executionCount);
                }
                if (data.switchProbabilities != null) {
                    ArrayList<Object> s = new ArrayList<>();
                    for (double p : data.switchProbabilities) {
                        s.add(p);
                    }
                    v.put("switchProbabilities", s);
                }
                if (data.branchTakenProbability != null) {
                    v.put("branchTakenProbability", data.branchTakenProbability);
                }
                if (data.symbolicTypeProfile != null) {
                    v.put("typeProfile", data.symbolicTypeProfile);
                }
                if (data.typeProfile != null) {
                    EconomicMap<String, Object> typeProfile = EconomicMap.create();
                    typeProfile.put("nullSeen", data.typeProfile.getNullSeen());
                    List<Object> types = new ArrayList<>();
                    typeProfile.put("types", types);
                    double effectiveNotRecorded = data.typeProfile.getNotRecordedProbability();
                    for (JavaTypeProfile.ProfiledType ptype : data.typeProfile.getTypes()) {
                        if (filter == null || lambdaFormatter.isLambda(method) || filter.includeType(ptype.getType())) {
                            EconomicMap<String, Object> ptypeMap = EconomicMap.create();
                            ptypeMap.put("type", ptype.getType().getName());
                            ptypeMap.put("probability", ptype.getProbability());
                            types.add(ptypeMap);
                        } else {
                            effectiveNotRecorded += ptype.getProbability();
                        }
                    }
                    typeProfile.put("notRecordedProbability", effectiveNotRecorded);
                    v.put("typeProfile", typeProfile);
                }
                bciData.add(v);
            }
            // For convenience sort the records by bci
            bciData.sort(Comparator.comparingInt(a -> (int) a.get("bci")));
            profileRecord.put("data", bciData);
            return profileRecord;
        }

        public void materialize(ResolvedJavaMethod realMethod) {
            if (materialized) {
                return;
            }
            assert this.method == null;
            this.method = realMethod;
            this.realProfile = realMethod.getProfilingInfo();
            for (BytecodeProfile bytecodeProfile : bytecodeProfiles.getValues()) {
                bytecodeProfile.materializeProfile();
            }
            materialized = true;
        }

    }

    /**
     * Hook to encode lamba and lambda form type names with special encodings to be stable.
     */
    public interface LambdaNameFormatter {
        boolean isLambda(ResolvedJavaMethod m);

        String formatLamdaName(ResolvedJavaMethod m);
    }

    /**
     * A interface describing if stable profile serialization to disk should include a type or not.
     * For some types, especially in the context of profile pollution, re-creating an exact state is
     * not possible.
     */
    public interface TypeFilter {
        boolean includeType(ResolvedJavaType type);
    }

    @SuppressWarnings("unchecked")
    public StableProfileProvider load(Object parser, ResolvedJavaType loadingIssuingType, boolean warnNonCachedLoadAccess1, LambdaNameFormatter lambdaFormatter) {
        EconomicMap<String, Object> map = (EconomicMap<String, Object>) parser;
        this.loaded = EconomicMap.create();
        List<?> methods = (List<?>) map.get("profiles");
        for (Object m : methods) {
            EconomicMap<String, Object> methodMap = (EconomicMap<String, Object>) m;
            String signature = (String) methodMap.get("method");
            CachingProfilingInfo info = new CachingProfilingInfo(methodMap);
            info.loadingIssuingType = loadingIssuingType;
            loaded.put(signature, info);
            if (methodMap.containsKey("stableLambdaName")) {
                // record another entry based on the stable lambda name
                String signatureStableLambda = (String) methodMap.get("stableLambdaName");
                loaded.put(signatureStableLambda, info);
            }
        }
        this.loadingLambdaNameFormatter = lambdaFormatter;
        this.warnNonCachedLoadAccess = warnNonCachedLoadAccess1;
        return this;
    }

    private LambdaNameFormatter loadingLambdaNameFormatter;

    public void freeze() {
        frozen = true;
    }

    private static TriState parseTriState(String exceptionSeen) {
        if (exceptionSeen == null) {
            return null;
        }
        return TriState.valueOf(exceptionSeen);
    }

    public EconomicMap<String, Object> recordProfiles(EconomicMap<String, Object> map, TypeFilter typeFilter, LambdaNameFormatter lambdaFormatter) {
        ArrayList<Object> methods = new ArrayList<>();
        map.put("profiles", methods);
        if (loaded != null) {
            MapCursor<String, CachingProfilingInfo> entries = loaded.getEntries();
            while (entries.advance()) {
                ResolvedJavaMethod method = entries.getValue().method;
                ResolvedJavaType declaringType = method.getDeclaringClass();
                if (typeFilter == null || lambdaFormatter != null && lambdaFormatter.isLambda(method) || typeFilter.includeType(declaringType)) {
                    methods.add(entries.getValue().asMap(entries.getKey(), typeFilter, lambdaFormatter));
                }
            }
        } else {
            MapCursor<ProfileKey, CachingProfilingInfo> entries = profiles.getEntries();
            while (entries.advance()) {
                ResolvedJavaMethod method = entries.getKey().method;
                ResolvedJavaType declaringType = method.getDeclaringClass();
                if (typeFilter == null || lambdaFormatter != null && lambdaFormatter.isLambda(method) || typeFilter.includeType(declaringType)) {
                    methods.add(entries.getValue().asMap(entries.getKey(), typeFilter, lambdaFormatter));
                }
            }
        }
        return map;
    }

    /**
     * Iterates over all queried profiles and invokes the provided consumer for each pair of
     * {@link ProfileKey} and corresponding {@link ProfilingInfo}.
     *
     * @param consumer a callback function that accepts a {@link ProfileKey} and a
     *            {@link ProfilingInfo} as input parameters
     */
    public void forQueriedProfiles(BiConsumer<ProfileKey, ProfilingInfo> consumer) {
        var cursor = profiles.getEntries();
        while (cursor.advance()) {
            consumer.accept(cursor.getKey(), cursor.getValue());
        }
    }
}
