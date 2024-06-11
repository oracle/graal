{
  local common = import '../ci/ci_common/common.jsonnet',
  local utils = import '../ci/ci_common/common-utils.libsonnet',
  local linux_amd64 = common.linux_amd64,

  local javadoc_publisher = {
    name: 'graal-publish-javadoc-' + utils.prefixed_jdk(self.jdk_version),
    run+: [
      ["cd", "./sdk"],
      ["mx", "build"],
      ["mx", "javadoc"],
      ["zip", "-r", "javadoc.zip", "javadoc"],
      ["cd", "../truffle"],
      ["mx", "build"],
      ["mx", "javadoc"],
      ["zip", "-r", "javadoc.zip", "javadoc"],
      ["cd", "../tools"],
      ["mx", "build"],
      ["mx", "javadoc"],
      ["zip", "-r", "javadoc.zip", "javadoc"],
      ["cd", "../compiler"],
      ["mx", "build"],
      ["mx", "javadoc", "--projects", "jdk.graal.compiler", "--packages", "jdk.graal.compiler.graphio,org.graalvm.word,org.graalvm.collections,com.oracle.truffle.compiler,org.graalvm.nativeimage,com.oracle.svm.core.annotate"],
      ["cd", "src/jdk.graal.compiler/"],
      ["zip", "-r", "../../graphio-javadoc.zip", "javadoc"],
      ["cd", "../../.."],
      ["set-export", "GRAAL_REPO", ["pwd"]],
      ["cd", ".."],
      ["git", "clone", ["mx", "urlrewrite", "https://github.com/graalvm/graalvm-website.git"]],
      ["cd", "graalvm-website"],
      # dev or release
      ["set-export", "GRAAL_VERSION", "24.2"],
      ["rm", "-rf", "$GRAAL_VERSION/javadoc"],
      ["mkdir", "-p", "$GRAAL_VERSION/javadoc"],
      ["unzip", "-o", "-d", "$GRAAL_VERSION/javadoc/tmp", "$GRAAL_REPO/sdk/javadoc.zip"],
      ["mv", "$GRAAL_VERSION/javadoc/tmp/javadoc", "$GRAAL_VERSION/javadoc/sdk"],
      ["unzip", "-o", "-d", "$GRAAL_VERSION/javadoc/tmp", "$GRAAL_REPO/truffle/javadoc.zip"],
      ["mv", "$GRAAL_VERSION/javadoc/tmp/javadoc", "$GRAAL_VERSION/javadoc/truffle"],
      ["unzip", "-o", "-d", "$GRAAL_VERSION/javadoc/tmp", "$GRAAL_REPO/tools/javadoc.zip"],
      ["mv", "$GRAAL_VERSION/javadoc/tmp/javadoc", "$GRAAL_VERSION/javadoc/tools"],
      ["unzip", "-o", "-d", "$GRAAL_VERSION/javadoc/tmp", "$GRAAL_REPO/compiler/graphio-javadoc.zip"],
      ["mv", "$GRAAL_VERSION/javadoc/tmp/javadoc", "$GRAAL_VERSION/javadoc/graphio"],
      ["git", "add", "$GRAAL_VERSION/javadoc"],
      ["git", "config", "user.name", "Javadoc Publisher"],
      ["git", "config", "user.email", "graal-dev@openjdk.java.net"],
      ["git", "diff", "--staged", "--quiet", "||", "git", "commit", "-m", ["echo", "Javadoc as of", ["date", "+%Y/%m/%d"]]],
      ["git", "push", "origin", "HEAD"]
    ],
     notify_groups:: ["javadoc"],
     timelimit : "30:00"
  },

  local all_builds = [
    common.post_merge + linux_amd64 + common.labsjdk21 + javadoc_publisher,
  ],
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: utils.add_defined_in(all_builds, std.thisFile),
}
