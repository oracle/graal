// location of resources that can be easily overwritten
{
  infra: {
    ol8_bench_image: "<ol8_bench_image>",
    benchmarking_config_repo: "<benchmarking_config_repo>",
    notify_releaser_service: ["<notify_releaser_service>"],
    notify_indexer_service(java_version, edition): ["<notify_indexer_service>"],
    nexus_base_url: "<nexus_base_url>"
  },
  // Public defaults are intentionally empty. Build definitions compose only the
  // repository environments they require; internal CI overrides the values.
  repository_environment: {
    // Recognized directly by Maven Wrapper scripts; no command-line forwarding needed.
    maven_wrapper: {
      MVNW_REPOURL: "",
    },
    // Consumed explicitly by Barista's Gradle build logic, not by Gradle itself.
    // The variables must be inherited by the process that invokes ./gradlew.
    gradle: {
      GRADLE_MAVEN_REPOSITORY_URL: "",
      GRADLE_PLUGIN_REPOSITORY_URL: "",
    },
  }
}
