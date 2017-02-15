package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionValuesAccess;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

@ServiceProvider(OptionValuesAccess.class)
public class GraalOptionValuesAccess implements OptionValuesAccess {

    @Override
    public OptionValues getOptions() {
        return OptionValues.GLOBAL;
    }
}
