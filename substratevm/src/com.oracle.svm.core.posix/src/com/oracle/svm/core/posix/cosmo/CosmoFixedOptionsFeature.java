package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.util.UserError;
import jdk.graal.compiler.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

@AutomaticallyRegisteredFeature
public class CosmoFixedOptionsFeature implements InternalFeature {

    private final long DEFAULT_RESERVED_ADDRESS_SPACE_SIZE = 1073741824L;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if ("cosmo".equals(SubstrateOptions.UseLibC.getValue())) {
            if (Platform.includedIn(Platform.AMD64.class)) {
                optionShouldBe(SubstrateOptions.getPageSize(), 4096, "PageSize");
            }
            else if (Platform.includedIn(Platform.AARCH64.class)) {
                optionShouldBe(SubstrateOptions.getPageSize(), 16384, "PageSize");
            }
            optionShouldNotBe(SubstrateOptions.hasFramePointer(), false, "PreserveFramePointer");
            optionShouldBe(StackOverflowCheck.Options.StackRedZoneSize.getValue(), 0, "StackRedZoneSize");
            if (SubstrateGCOptions.ReservedAddressSpaceSize.hasBeenSet()) {
                optionShouldNotBe(SubstrateGCOptions.ReservedAddressSpaceSize.getValue(), 0, "ReservedAddressSpaceSize");
            } else {
                SubstrateGCOptions.ReservedAddressSpaceSize.update(DEFAULT_RESERVED_ADDRESS_SPACE_SIZE);
            }
        }
    }

    private <T> void optionShouldBe(T option, T correctValue, String name) {
        if (!correctValue.equals(option)) {
            throw UserError.abort("%s must be %s when using cosmo libc, not %s", name, correctValue, option);
        }
    }

    private <T> void optionShouldNotBe(T option, T wrongValue, String name) {
        if (wrongValue.equals(option)) {
            throw UserError.abort("%s must NOT be %s when using cosmo libc", name, wrongValue);
        }
    }
}
