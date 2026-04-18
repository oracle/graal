#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SUITE_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
KEEP_WORK_DIR=0
TEST_CASE="all"
RESOURCE_PROBE_NAME="gr73955-condition-errors-resource.txt"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --keep-work-dir)
            KEEP_WORK_DIR=1
            shift
            ;;
        --case)
            if [[ $# -lt 2 ]]; then
                echo "ERROR: --case requires one of: array, reflection-query, resource-scan, resource-explicit, resources, all." >&2
                exit 2
            fi
            TEST_CASE="$2"
            shift 2
            ;;
        *)
            echo "Usage: $0 [--keep-work-dir] [--case array|resource-scan|resource-explicit|resources|all]" >&2
            exit 2
            ;;
    esac
done

if [[ "${TEST_CASE}" != "array" && "${TEST_CASE}" != "reflection-query" && "${TEST_CASE}" != "resource-scan" && "${TEST_CASE}" != "resource-explicit" && "${TEST_CASE}" != "resources" && "${TEST_CASE}" != "all" ]]; then
    echo "ERROR: unsupported case '${TEST_CASE}'. Expected one of: array, reflection-query, resource-scan, resource-explicit, resources, all." >&2
    exit 2
fi

if ! command -v mx >/dev/null 2>&1; then
    echo "ERROR: mx is not on PATH." >&2
    exit 1
fi
if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac is not on PATH." >&2
    exit 1
fi

HEAD_SHA="$(git -C "${SUITE_DIR}" rev-parse --short=12 HEAD)"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/condition-errors-test.XXXXXX")"

cleanup() {
    if [[ ${KEEP_WORK_DIR} -eq 0 ]]; then
        rm -rf "${WORK_DIR}"
    fi
}
trap cleanup EXIT

write_array_probe() {
    local probe_dir="${WORK_DIR}/array"
    local src_dir="${probe_dir}/src/array/probe"
    local classes_dir="${probe_dir}/classes"
    local res_dir="${probe_dir}/resources"
    local meta_dir="${res_dir}/META-INF/native-image/array/probe"

    mkdir -p "${src_dir}" "${classes_dir}" "${meta_dir}"

    cat > "${src_dir}/ArrayTypeProbe.java" <<'EOF'
package array.probe;

public final class ArrayTypeProbe {
    private static final String EXPECTED_CONDITION = "typeReached(array.probe.ArrayTypeProbe$NeverReachedCondition)";

    public static final class ArrayComponent {
    }

    public static final class NeverReachedCondition {
        static void touch() {
        }
    }

    public static void main(String[] args) {
        maybeReachCondition(args);

        Class<?> ordinaryJavaArrayType = new ArrayComponent[0].getClass();
        if (ordinaryJavaArrayType != ArrayComponent[].class) {
            throw new AssertionError("Expected a regular Java array allocation to produce ArrayComponent[].class.");
        }

        try {
            Class<?> arrayType = ArrayComponent.class.arrayType();
            throw new AssertionError("Expected MissingReflectionRegistrationError, got " + arrayType.getTypeName());
        } catch (AssertionError assertionFailure) {
            throw assertionFailure;
        } catch (Error expected) {
            String errorType = expected.getClass().getName();
            if (!errorType.endsWith("MissingReflectionRegistrationError")) {
                throw new AssertionError("Expected MissingReflectionRegistrationError, got " + errorType, expected);
            }
            String message = String.valueOf(expected.getMessage());
            if (!message.contains(EXPECTED_CONDITION)) {
                throw new AssertionError("Missing unsatisfied-condition details in error message:\n" + message, expected);
            }
        }
    }

    private static void maybeReachCondition(String[] args) {
        /*
         * Keep NeverReachedCondition reachable in analysis so the metadata entry is preserved,
         * but do not execute the path during the default runtime invocation.
         */
        if (args.length == 1 && "reach-condition".equals(args[0])) {
            NeverReachedCondition.touch();
        }
    }
}
EOF

    cat > "${meta_dir}/reachability-metadata.json" <<'EOF'
{
  "reflection": [
    {
      "condition": {
        "typeReached": "array.probe.ArrayTypeProbe$NeverReachedCondition"
      },
      "type": "array.probe.ArrayTypeProbe$ArrayComponent[]"
    }
  ]
}
EOF

    javac -d "${classes_dir}" "${src_dir}/ArrayTypeProbe.java"
}

build_and_run_array_probe() {
    local probe_dir="${WORK_DIR}/array"
    local image_path="${probe_dir}/array-type-probe"

    echo "[HEAD ${HEAD_SHA}] Compiling arrayType() regression probe..."
    write_array_probe

    echo "[HEAD ${HEAD_SHA}] Building arrayType() regression image..."
    mx -p "${SUITE_DIR}" native-image \
        --exact-reachability-metadata \
        -H:+RuntimeClassLoading \
        -cp "${probe_dir}/classes:${probe_dir}/resources" \
        -o "${image_path}" \
        array.probe.ArrayTypeProbe

    echo "[HEAD ${HEAD_SHA}] Running arrayType() regression probe..."
    "${image_path}"

    echo "[HEAD ${HEAD_SHA}] PASS: arrayType() still enforces unsatisfied array metadata under runtime class loading."
}

write_reflection_query_probe() {
    local probe_dir="${WORK_DIR}/reflection-query"
    local src_dir="${probe_dir}/src/reflection/query"
    local classes_dir="${probe_dir}/classes"
    local res_dir="${probe_dir}/resources"
    local meta_dir="${res_dir}/META-INF/native-image/reflection/query"

    mkdir -p "${src_dir}" "${classes_dir}" "${meta_dir}"

    cat > "${src_dir}/ReflectionClassQueryProbe.java" <<'EOF'
package reflection.query;

public final class ReflectionClassQueryProbe {
    public static final class QueryTarget {
        public void ping() {
        }
    }

    public static void main(String[] args) throws Exception {
        /*
         * java.lang.String is always reached at build time, so a typeReached(String) condition
         * should be treated as unconditional and the query should succeed. The logging flag should
         * still print the class-query diagnostic because a runtime getMethod() routes through
         * DynamicHub.classDynamicAccessAllowed(...).
         */
        if (args.length != 2) {
            throw new AssertionError("Expected arguments: <class-name> <method-name>");
        }
        Class<?> queryClass = Class.forName(args[0]);
        queryClass.getMethod(args[1]);
        System.out.println("QUERY_OK");
    }
}
EOF

    cat > "${meta_dir}/reachability-metadata.json" <<'EOF'
{
  "reflection": [
    {
      "condition": {
        "typeReached": "java.lang.String"
      },
      "type": "reflection.query.ReflectionClassQueryProbe$QueryTarget",
      "allPublicMethods": true
    }
  ]
}
EOF

    javac -d "${classes_dir}" "${src_dir}/ReflectionClassQueryProbe.java"
}

build_and_run_reflection_query_probe() {
    local probe_dir="${WORK_DIR}/reflection-query"
    local image_path="${probe_dir}/reflection-class-query-probe"
    local output_path="${probe_dir}/reflection-class-query-probe.out"

    echo "[HEAD ${HEAD_SHA}] Compiling reflection class-query regression probe..."
    write_reflection_query_probe

    echo "[HEAD ${HEAD_SHA}] Building reflection class-query regression image..."
    mx -p "${SUITE_DIR}" native-image \
        --exact-reachability-metadata \
        -H:+TrackReflectionClassQueryChecks \
        -cp "${probe_dir}/classes:${probe_dir}/resources" \
        -o "${image_path}" \
        reflection.query.ReflectionClassQueryProbe

    echo "[HEAD ${HEAD_SHA}] Running reflection class-query regression probe..."
    "${image_path}" "reflection.query.ReflectionClassQueryProbe\$QueryTarget" "ping" | tee "${output_path}"

    if ! grep -Fq "Class query succeeded: class=reflection.query.ReflectionClassQueryProbe\$QueryTarget, query=getMethod, classFlagSet=true, conditionsSatisfied=true, unsatisfiedConditions=[]" "${output_path}"; then
        echo "ERROR: expected reflection class-query logging output was not observed." >&2
        exit 1
    fi

    if ! grep -Fq "QUERY_OK" "${output_path}"; then
        echo "ERROR: reflection class-query probe did not complete successfully." >&2
        exit 1
    fi

    echo "[HEAD ${HEAD_SHA}] PASS: reflection class-query logging treats typeReached(java.lang.String) as unconditional."
}

write_resource_probe() {
    local probe_dir="${WORK_DIR}/resources"
    local modules_src_dir="${probe_dir}/module-src"
    local modules_dir="${probe_dir}/modules"
    local app_src_dir="${probe_dir}/app-src/resource/probe"
    local app_classes_dir="${probe_dir}/app-classes"
    local app_res_dir="${probe_dir}/app-resources"
    local app_meta_dir="${app_res_dir}/META-INF/native-image/resource/probe"
    local module_a_dir="${modules_dir}/resource.test.module.a"
    local module_a_meta_dir="${module_a_dir}/META-INF/native-image/resource/modulea"

    mkdir -p \
        "${modules_src_dir}/resource.test.module.a/resource/modulea" \
        "${modules_src_dir}/resource.test.module.b/resource/moduleb" \
        "${modules_dir}" \
        "${app_src_dir}" \
        "${app_classes_dir}" \
        "${app_meta_dir}" \
        "${module_a_meta_dir}"

    cat > "${modules_src_dir}/resource.test.module.a/module-info.java" <<'EOF'
module resource.test.module.a {
    exports resource.modulea;
}
EOF

    cat > "${modules_src_dir}/resource.test.module.a/resource/modulea/ConditionMarker.java" <<'EOF'
package resource.modulea;

public final class ConditionMarker {
    public static void touch() {
    }

    private ConditionMarker() {
    }
}
EOF

    cat > "${modules_src_dir}/resource.test.module.b/module-info.java" <<'EOF'
module resource.test.module.b {
    exports resource.moduleb;
}
EOF

    cat > "${modules_src_dir}/resource.test.module.b/resource/moduleb/Marker.java" <<'EOF'
package resource.moduleb;

public final class Marker {
    private Marker() {
    }
}
EOF

    javac \
        --module-source-path "${modules_src_dir}" \
        -d "${modules_dir}" \
        -m resource.test.module.a,resource.test.module.b

    cat > "${module_a_meta_dir}/reachability-metadata.json" <<'EOF'
{
  "resources": [
    {
      "condition": {
        "typeReached": "resource.modulea.ConditionMarker"
      },
      "module": "resource.test.module.a",
      "glob": "__RESOURCE_PROBE_NAME__"
    }
  ]
}
EOF

    sed -i "s/__RESOURCE_PROBE_NAME__/${RESOURCE_PROBE_NAME}/g" "${module_a_meta_dir}/reachability-metadata.json"

    cat > "${app_src_dir}/ResourceLookupProbe.java" <<'EOF'
package resource.probe;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import resource.modulea.ConditionMarker;

public final class ResourceLookupProbe {
    private static final String MODULE_B = "resource.test.module.b";
    private static final String RESOURCE_NAME = "__RESOURCE_PROBE_NAME__";
    private static final String EXPECTED_CONTENT = "resource from the unnamed module";

    public static void main(String[] args) throws Exception {
        maybeReachCondition(args);
        String mode = args.length == 0 ? "all" : args[0];
        switch (mode) {
            case "all":
                verifyCrossModuleMetadataDoesNotAbortUnnamedLookup();
                verifyExplicitModuleLookupIgnoresOtherModuleMetadata();
                break;
            case "scan":
                verifyCrossModuleMetadataDoesNotAbortUnnamedLookup();
                break;
            case "explicit":
                verifyExplicitModuleLookupIgnoresOtherModuleMetadata();
                break;
            default:
                throw new AssertionError("Unsupported mode: " + mode);
        }
    }

    private static void verifyCrossModuleMetadataDoesNotAbortUnnamedLookup() throws Exception {
        Enumeration<URL> resources = ClassLoader.getSystemResources(RESOURCE_NAME);
        List<URL> urls = Collections.list(resources);
        if (urls.size() != 1) {
            throw new AssertionError("Expected exactly one URL from the unnamed module, got " + urls.size() + ".");
        }

        URL resource = urls.get(0);
        String contents;
        try (InputStream in = resource.openStream()) {
            contents = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        if (!EXPECTED_CONTENT.equals(contents)) {
            throw new AssertionError("Unexpected resource contents: " + contents);
        }
    }

    private static void verifyExplicitModuleLookupIgnoresOtherModuleMetadata() throws Exception {
        Module moduleB = ModuleLayer.boot().findModule(MODULE_B).orElseThrow(() -> new AssertionError("Missing module " + MODULE_B));
        try (InputStream in = moduleB.getResourceAsStream(RESOURCE_NAME)) {
            if (in != null) {
                throw new AssertionError("Expected explicit module lookup to return null for an unrelated missing resource.");
            }
        }
    }

    private static void maybeReachCondition(String[] args) {
        /*
         * Keep ConditionMarker reachable in analysis so the conditional resource pattern is
         * preserved, but do not execute the path during the default runtime invocation.
         */
        if (args.length == 2 && "reach-condition".equals(args[1])) {
            ConditionMarker.touch();
        }
    }
}
EOF

    sed -i "s/__RESOURCE_PROBE_NAME__/${RESOURCE_PROBE_NAME}/g" "${app_src_dir}/ResourceLookupProbe.java"

    cat > "${app_res_dir}/${RESOURCE_PROBE_NAME}" <<'EOF'
resource from the unnamed module
EOF

    cat > "${app_meta_dir}/reachability-metadata.json" <<'EOF'
{
  "resources": [
    {
      "glob": "__RESOURCE_PROBE_NAME__"
    }
  ]
}
EOF

    sed -i "s/__RESOURCE_PROBE_NAME__/${RESOURCE_PROBE_NAME}/g" "${app_meta_dir}/reachability-metadata.json"

    javac \
        --module-path "${modules_dir}" \
        --add-modules resource.test.module.a,resource.test.module.b \
        -d "${app_classes_dir}" \
        "${app_src_dir}/ResourceLookupProbe.java"
}

build_resource_probe_image() {
    local probe_dir="${WORK_DIR}/resources"
    local image_path="${probe_dir}/resource-lookup-probe"

    echo "[HEAD ${HEAD_SHA}] Compiling resource lookup regression probe..."
    write_resource_probe

    echo "[HEAD ${HEAD_SHA}] Building resource lookup regression image..."
    mx -p "${SUITE_DIR}" native-image \
        --exact-reachability-metadata \
        --module-path "${probe_dir}/modules" \
        --add-modules resource.test.module.a,resource.test.module.b \
        -cp "${probe_dir}/app-classes:${probe_dir}/app-resources" \
        -o "${image_path}" \
        resource.probe.ResourceLookupProbe
}

run_resource_probe() {
    local mode="$1"
    local probe_dir="${WORK_DIR}/resources"
    local image_path="${probe_dir}/resource-lookup-probe"

    echo "[HEAD ${HEAD_SHA}] Running resource lookup regression probe (${mode})..."
    "${image_path}" "${mode}"

    echo "[HEAD ${HEAD_SHA}] PASS: resource lookup regression probe (${mode}) passed."
}

if [[ "${TEST_CASE}" == "array" || "${TEST_CASE}" == "all" ]]; then
    build_and_run_array_probe
fi

if [[ "${TEST_CASE}" == "reflection-query" || "${TEST_CASE}" == "all" ]]; then
    build_and_run_reflection_query_probe
fi

if [[ "${TEST_CASE}" == "resource-scan" || "${TEST_CASE}" == "resource-explicit" || "${TEST_CASE}" == "resources" || "${TEST_CASE}" == "all" ]]; then
    build_resource_probe_image
fi

if [[ "${TEST_CASE}" == "resource-scan" ]]; then
    run_resource_probe scan
fi

if [[ "${TEST_CASE}" == "resource-explicit" ]]; then
    run_resource_probe explicit
fi

if [[ "${TEST_CASE}" == "resources" || "${TEST_CASE}" == "all" ]]; then
    run_resource_probe scan
    run_resource_probe explicit
fi

if [[ ${KEEP_WORK_DIR} -eq 1 ]]; then
    echo "Work directory preserved at: ${WORK_DIR}"
fi
