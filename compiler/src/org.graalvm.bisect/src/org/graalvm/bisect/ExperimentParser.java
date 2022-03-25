package org.graalvm.bisect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExecutedMethodBuilder;
import org.graalvm.bisect.core.Experiment;
import org.graalvm.bisect.core.ExperimentImpl;
import org.graalvm.bisect.core.optimization.PartialLoopUnrollingOptimization;
import org.graalvm.bisect.json.JSONParser;

public class ExperimentParser {
    private final File profOutput;
    private final File[] optimizationLogs;

    public ExperimentParser(File profOutput, File[] optimizationLogs) {
        this.profOutput = profOutput;
        this.optimizationLogs = optimizationLogs;
    }

    public Experiment parse() throws IOException {
        Map<String, ExecutedMethodBuilder> methodByCompilationId = new HashMap<>();
        for (File optimizationLog : optimizationLogs) {
            ExecutedMethodBuilder method = parseCompiledMethod(optimizationLog);
            methodByCompilationId.put(method.getCompilationId(), method);
        }
        Map<String, Double> hotMethods = parseHotMethods(profOutput);
        for (Map.Entry<String, Double> hotMethod : hotMethods.entrySet()) {
            String compilationId = hotMethod.getKey();
            ExecutedMethodBuilder builder = methodByCompilationId.get(compilationId);
            if (builder == null) {
                continue;
            }
            builder.setHot(true);
            builder.setShare(hotMethod.getValue());
        }
        List<ExecutedMethod> methods = new ArrayList<>();
        for (ExecutedMethodBuilder builder : methodByCompilationId.values()) {
            if (builder.isHot()) {
                methods.add(builder.build());
            }
        }
        return new ExperimentImpl(methods);
    }

    private ExecutedMethodBuilder parseCompiledMethod(File optimizationLog) throws IOException {
        JSONParser parser = new JSONParser(optimizationLog);
        Map<String, Object> log = (Map<String, Object>) parser.parse();
        ExecutedMethodBuilder builder = new ExecutedMethodBuilder();
        builder.setCompilationId((String) log.get("compilationId"));
        builder.setCompilationMethodName((String) log.get("compilationMethodName"));
        for (Map<String, Object> optimizationObject : (List<Map<String, Object>>) log.get("optimizations")) {
            builder.addOptimization(new PartialLoopUnrollingOptimization((Integer) optimizationObject.get("bci")));
        }
        return builder;
    }

    private Map<String, Double> parseHotMethods(File profOutput) throws IOException {
        JSONParser parser = new JSONParser(profOutput);
        Map<String, Object> log = (Map<String, Object>) parser.parse();
        List<Map<String, Object>> hotMethodsRaw = (List<Map<String, Object>>) log.get("hotGeneratedCode");
        Map<String, Double> hotMethods = new HashMap<>();
        for (Map<String, Object> hotMethodRaw : hotMethodsRaw) {
            String compileId = (String) hotMethodRaw.get("compileId");
            if (compileId == null) {
                continue;
            }
            hotMethods.put(compileId, (Double) hotMethodRaw.get("share"));
        }
        return hotMethods;
    }
}
