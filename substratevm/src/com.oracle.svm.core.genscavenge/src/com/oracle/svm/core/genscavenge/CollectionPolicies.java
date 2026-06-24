/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import java.nio.charset.StandardCharsets;

import com.oracle.svm.shared.util.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.guest.staging.util.HostedByteBufferPointer;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import org.graalvm.word.UnsignedWord;

/**
 * Helper for creating, selecting, and validating serial GC {@link CollectionPolicy} instances.
 * Policies are created at image build-time so that runtime selection only changes which image-heap
 * instance is active.
 * <p>
 * GC policy names are compared byte-by-byte before Java heap allocation is available. Policy names
 * must use ASCII characters.
 */
final class CollectionPolicies {
    private static final String LEGACY_POLICY_NAME_PREFIX = "com.oracle.svm.core.genscavenge.CollectionPolicy$";

    private static final int ADAPTIVE2_POLICY = 0;
    private static final int ADAPTIVE_POLICY = ADAPTIVE2_POLICY + 1;
    private static final int LIBGRAAL_POLICY = ADAPTIVE_POLICY + 1;
    private static final int PROPORTIONATE_POLICY = LIBGRAAL_POLICY + 1;
    private static final int DYNAMIC_POLICY = PROPORTIONATE_POLICY + 1;
    private static final int BY_SPACE_AND_TIME_POLICY = DYNAMIC_POLICY + 1;
    private static final int ONLY_COMPLETELY_POLICY = BY_SPACE_AND_TIME_POLICY + 1;
    private static final int ONLY_INCREMENTALLY_POLICY = ONLY_COMPLETELY_POLICY + 1;
    private static final int NEVER_COLLECT_POLICY = ONLY_INCREMENTALLY_POLICY + 1;
    private static final int POLICY_COUNT = NEVER_COLLECT_POLICY + 1;

    private final CollectionPolicy[] policies;

    @Platforms(Platform.HOSTED_ONLY.class)
    CollectionPolicies() {
        policies = new CollectionPolicy[POLICY_COUNT];
        policies[ADAPTIVE2_POLICY] = new AdaptiveCollectionPolicy2();
        policies[ADAPTIVE_POLICY] = new AdaptiveCollectionPolicy();
        policies[LIBGRAAL_POLICY] = new LibGraalCollectionPolicy();
        policies[PROPORTIONATE_POLICY] = new ProportionateSpacesPolicy();
        policies[DYNAMIC_POLICY] = new DynamicCollectionPolicy();
        policies[BY_SPACE_AND_TIME_POLICY] = new BasicCollectionPolicies.BySpaceAndTime();
        policies[ONLY_COMPLETELY_POLICY] = new BasicCollectionPolicies.OnlyCompletely();
        policies[ONLY_INCREMENTALLY_POLICY] = new BasicCollectionPolicies.OnlyIncrementally();
        policies[NEVER_COLLECT_POLICY] = new BasicCollectionPolicies.NeverCollect();
    }

    @Uninterruptible(reason = "Called during startup.")
    CollectionPolicy getSelectedPolicy() {
        if (SubstrateOptions.useEpsilonGC()) {
            return policies[NEVER_COLLECT_POLICY];
        } else if (!SerialGCOptions.useRememberedSet()) {
            return policies[ONLY_COMPLETELY_POLICY];
        }

        int optionIndex = IsolateArgumentParser.getOptionIndex(SerialGCOptions.InitialCollectionPolicy);
        if (!IsolateArgumentParser.singleton().isNull(optionIndex)) {
            CCharPointer name = IsolateArgumentParser.singleton().getCCharPointerOptionValue(optionIndex);
            int policyIndex = getPolicyIndex(name);
            if (policyIndex >= 0) {
                return policies[policyIndex];
            }
        }

        /*
         * Return the default policy. If the name was unknown, option validation will report the
         * invalid option value once error reporting is available.
         */
        return policies[ADAPTIVE2_POLICY];
    }

    @Uninterruptible(reason = "Called during startup.")
    private static boolean matchesPolicyName(CCharPointer name, String policyName, boolean allowLegacyName) {
        UnsignedWord l = SubstrateUtil.strlen(name);
        if (l.aboveThan(Integer.MAX_VALUE)) {
            /* Very long strings cannot match. */
            return false;
        }

        int nameLength = NumUtil.safeToInt(l.rawValue());
        if (UninterruptibleUtils.ASCII.equals(name, nameLength, policyName)) {
            return true;
        } else if (allowLegacyName) {
            return nameLength == LEGACY_POLICY_NAME_PREFIX.length() + policyName.length() &&
                            UninterruptibleUtils.ASCII.startsWith(name, LEGACY_POLICY_NAME_PREFIX) &&
                            UninterruptibleUtils.ASCII.endsWith(name, nameLength, policyName);
        }
        return false;
    }

    @Uninterruptible(reason = "Called during startup.")
    private static int getPolicyIndex(CCharPointer name) {
        if (matchesPolicyName(name, "Adaptive2", false)) {
            return ADAPTIVE2_POLICY;
        } else if (matchesPolicyName(name, "Adaptive", false)) {
            return ADAPTIVE_POLICY;
        } else if (matchesPolicyName(name, "LibGraal", false)) {
            return LIBGRAAL_POLICY;
        } else if (matchesPolicyName(name, "Proportionate", false)) {
            return PROPORTIONATE_POLICY;
        } else if (matchesPolicyName(name, "Dynamic", false)) {
            return DYNAMIC_POLICY;
        } else if (matchesPolicyName(name, "BySpaceAndTime", true)) {
            return BY_SPACE_AND_TIME_POLICY;
        } else if (matchesPolicyName(name, "OnlyCompletely", true)) {
            return ONLY_COMPLETELY_POLICY;
        } else if (matchesPolicyName(name, "OnlyIncrementally", true)) {
            return ONLY_INCREMENTALLY_POLICY;
        } else if (matchesPolicyName(name, "NeverCollect", true)) {
            return NEVER_COLLECT_POLICY;
        }
        return -1;
    }

    static void validatePolicyName(RuntimeOptionKey<String> optionKey) {
        String name = optionKey.getValue();
        if (name == null) {
            return;
        }

        /* Convert to CCharPointer so that we can use the same code at build-time and run-time. */
        byte[] bytes = (name + '\0').getBytes(StandardCharsets.US_ASCII);
        if (SubstrateUtil.HOSTED) {
            CCharPointer cName = new HostedByteBufferPointer(bytes);
            if (getPolicyIndex(cName) < 0) {
                throw UserError.invalidOptionValue(optionKey, name, "The specified GC policy does not exist.");
            }
        } else {
            try (CCharPointerHolder cName = CTypeConversion.toCBytes(bytes)) {
                if (getPolicyIndex(cName.get()) < 0) {
                    throw new IllegalArgumentException("Invalid value for option '" + optionKey.getName() + "'. The specified GC policy ('" + optionKey.getValue() + "') does not exist.");
                }
            }
        }
    }
}
