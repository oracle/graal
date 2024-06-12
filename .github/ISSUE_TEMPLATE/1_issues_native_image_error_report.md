name: Native Image Issue Report
description: Report issues specific to GraalVM's native image
title: "[NATIVE IMAGE] "
labels: ["native-image", "bug", "triage"]

body:
  - type: markdown
    attributes:
      value: "For security vulnerabilities, please consult the GraalVM security policy and contact the security team directly."

  - type: textarea
    id: describe_issue
    attributes:
      label: Describe the Issue
      description: "Describe the native image issue you are experiencing. Provide a clear and concise description of what happened and what you were trying to achieve."
    validations:
      required: true

  - type: markdown
    attributes:
      value: "Using the latest version of GraalVM can resolve many issues. Please confirm if you have tested with the latest available version."

  - type: input
    id: graalvm_version
    attributes:
      label: GraalVM Version
      description: "Provide the version of GraalVM used."
      placeholder: "Output of `java -version` "
    validations:
      required: true

  - type: checkboxes
    id: latest_version_check
    attributes:
      label: "Version Confirmation"
      description: "Check the box if you tried with the latest version of GraalVM."
      options:
        - label: "I tried with the latest version of GraalVM."
    validations:
      required: true

  - type: checkboxes
    id: throw_missing_registration_errors
    attributes:
      label: "Diagnostic Flag Confirmation"
      description: "Check the box if you tried the `-H:ThrowMissingRegistrationErrors` flag to catch metadata exceptions."
      options:
        - label: "I tried the `-H:ThrowMissingRegistrationErrors` flag."
    validations:
      required: true

  - type: textarea
    id: expected_behavior
    attributes:
      label: Expected Behavior
      description: "What did you expect to happen when using GraalVM's native image?"
    validations:
      required: true

  - type: textarea
    id: actual_behavior
    attributes:
      label: Actual Behavior
      description: "What actually happened? Describe any errors or unexpected outcomes."
    validations:
      required: true

  - type: textarea
    id: steps_to_reproduce
    attributes:
      label: Steps to Reproduce
      description: "Provide a step-by-step description of how to reproduce the issue. Include any specific commands, configurations, or code snippets."
      placeholder: "1. \n2. \n3. "
    validations:
      required: true

  - type: input
    id: operating_system
    attributes:
      label: Operating System and Version
      description: "Provide details of your operating system and version (e.g., output of `uname -a` or Windows version)."
      placeholder: "OS details here"
    validations:
      required: true

  - type: textarea
    id: additional_context
    attributes:
      label: Additional Context
      description: "Provide any additional context or information that might help in diagnosing the issue, such as environmental variables, system settings, or external dependencies."
    validations:
      required: false

  - type: textarea
    id: log_output
    attributes:
      label: Log Output and Error Messages
      description: "Include any relevant log outputs or error messages. Attach files by selecting this field and then dragging and dropping them into the comment box below the issue form."
      placeholder: "Paste logs or error messages here"
    validations:
      required: false
