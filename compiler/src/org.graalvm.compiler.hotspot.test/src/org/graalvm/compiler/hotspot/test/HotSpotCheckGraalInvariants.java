package org.graalvm.compiler.hotspot.test;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.core.test.CheckGraalInvariants;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.VerifyPhase;
import org.junit.Test;

public class HotSpotCheckGraalInvariants extends CheckGraalInvariants {

    @Override
    @Test
    public void test() {
        assumeManagementLibraryIsLoadable();
        test(new HotSpotCheckGraalInvariants());
    }

    @Override
    public List<VerifyPhase<CoreProviders>> getInitialVerifiers() {
        List<VerifyPhase<CoreProviders>> initialVerifiers = new ArrayList<>();
        initialVerifiers.add(new VerifyIterableNodeTypes());
        return initialVerifiers;
    }

}
