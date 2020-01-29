---
name: "\U0001F6A8 Native Image Issue Report"
about: Create a report for a failure with Native Image. To report a security vulnerability, please see below or the SECURITY.md file at the root of the repository. Do not open a GitHub issue.
title: ''
labels: bug, native-image
assignees: ''

---
**Describe GraalVM and your environment :**
 - GraalVM version or commit id if built from source: **[e.g. 19.3]**
 - CE or EE: **[e.g.: CE]**
 - Build Time or run time failure: **[eg run-time]**
 - JDK version: **[e.g.: JDK8]**
 - Native compiler information:<details><summary>Run the following to capture compiler version</summary>
   - In windows: `cl.exe`
   - In macOS : `cc -v`
   - In Linux: `gcc --version`
</details>

```
**PASTE OUTPUT HERE**
```
 - Native linker information:<details><summary>Run the following to capture linker version</summary>
   - In windows: `cl.exe`
   - In macOS : `cc -Wl,-v`
   - In Linux: `gcc -Wl,--version`
</details>

```
**PASTE OUTPUT HERE**
```
 - OS and OS Version: **[e.g. macOS Catalina]**
 - Architecture: **[e.g.: AMD64]**
 - The output of `java -Xinternalversion`: 
```
 **PASTE OUTPUT HERE**
```

**Have you verified this issue still happens when using the latest snapshot?**
You can find snapshot builds here: https://github.com/graalvm/graalvm-ce-dev-builds/releases

**Describe the issue**
A clear and concise description of the issue.

**Describe the full native-image command**
<details><summary>Capture full native-image command by running with the `--verbose` flag e.g.:</summary>

```
 native-image --verbose [... other args]
```
</details>

```
**PASTE OUTPUT HERE**
```

**Code snippet or code repository that reproduces the issue**
```
**PASTE CODE/REPO HERE**
```

**Steps to reproduce the issue**
Please include both build steps as well as run steps
1. Step one [e.g.: git clone --depth 1 https://git.myrepo.com/projectone ]
2. Step two [e.g.: mvn clean package]

**Expected behavior**
A clear and concise description of what you expected to happen.

**Additional context**
Add any other context about the problem here. Specially important are stack traces or log output. Feel free to link to gists or to screenshots if necesary
<details><summary>Details</summary>

```
    PASTE YOUR LOG/STACK TRACE HERE
```
</details>
