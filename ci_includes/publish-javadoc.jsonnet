{
  local common = import '../common.jsonnet',
  local utils = import '../common-utils.libsonnet',
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
      ["mx", "javadoc", "--projects", "org.graalvm.graphio"],
      ["cd", "src/org.graalvm.graphio/"],
      ["zip", "-r", "../../graphio-javadoc.zip", "javadoc"],
      ["cd", "../../.."],
      ["set-export", "GRAAL_REPO", ["pwd"]],
      ["cd", ".."],
      ["git", "clone", ["mx", "urlrewrite", "https://github.com/graalvm/graalvm-website.git"]],
      ["cd", "graalvm-website"],
      ["rm", "-rf", "sdk/javadoc", "truffle/javadoc", "tools/javadoc", "graphio/javadoc"],
      ["git", "status" ],
      ["unzip", "-o", "-d", "sdk", "$GRAAL_REPO/sdk/javadoc.zip"],
      ["unzip", "-o", "-d", "truffle", "$GRAAL_REPO/truffle/javadoc.zip"],
      ["unzip", "-o", "-d", "tools", "$GRAAL_REPO/tools/javadoc.zip"],
      ["unzip", "-o", "-d", "graphio", "$GRAAL_REPO/compiler/graphio-javadoc.zip"],
      ["git", "add", "sdk/javadoc", "truffle/javadoc", "tools/javadoc", "graphio/javadoc"],
      ["git", "config", "user.name", "Javadoc Publisher"],
      ["git", "config", "user.email", "graal-dev@openjdk.java.net"],
      ["git", "diff", "--staged", "--quiet", "||", "git", "commit", "-m", ["echo", "Javadoc as of", ["date", "+%Y/%m/%d"]]],
      ["git", "push", "origin", "HEAD"]
    ],
     notify_groups:: ["javadoc"],
     timelimit : "30:00"
  },

  local all_builds = [
    common.post_merge + linux_amd64 + common.oraclejdk8 + javadoc_publisher,
  ],
  // adds a "defined_in" field to all builds mentioning the location of this current file
  builds:: [{ defined_in: std.thisFile } + b for b in all_builds]
}
