---
layout: docs
toc_group: reference-manual
link_title: GraalVM Updater
permalink: /reference-manual/graalvm-updater/
redirect_from: /$version/docs/graalvm-updater/
---

# GraalVM Updater

* [Check Available Components](#check-available-components)
* [Install Components on GraalVM Community](#install-components-on-graalvm-community)
* [Install Components on GraalVM Enterprise](#install-components-on-graalvm-enterprise)
* [Install Components Manually](#install-components-manually)
* [Install Components from Local Collection](#install-cmponents-from-local-collection)
* [Uninstall Components](#uninstall-components)
* [Upgrade GraalVM](#upgrade-graalvm)
* [Rebuild Images](#rebuild-images)
* [Replace Components and Files](#replace-components-and-files)
* [Configure Proxies](#configure-proxies)
* [Configure Installation](#configure-installation)
* [GraalVM Updater Commands Overview](#graalvm-updater-commands-overview)
* [Troubleshooting](#troubleshooting)

GraalVM Updater, `gu`, is a command-line tool for installing and managing optional GraalVM language runtimes and utilities. 
It is available in the core GraalVM installation.
To assist you with the installation, language runtimes and utilities are pre-packaged as JAR files and referenced in the documentation as "components".
GraalVM Updater can be also used to update your local GraalVM installation to a newer version or upgrade from a Community to Enterprise edition.
Read more in [Upgrade GraalVM](#upgrade-graalvm).

## Check Available Components

To check what components are already shipped with your GraalVM installation or what you have already installed, run the `list` command:
```shell
gu list
```

To check what components are available for your GraalVM version to install, run the `gu available` command:
```shell
gu available
Downloading: Component catalog from ...
ComponentId              Version            Component name                
-----------------------------------------------------------------------------
espresso                 <version>          Java on Truffle               
espresso-llvm            <version>          Java on Truffle LLVM Java library
js                       <version>          JavaScript
llvm                     <version>          LLVM
llvm-toolchain           <version>          LLVM.org toolchain            
native-image             <version>          Native Image                  
nodejs                   <version>          Graal.nodejs                  
python                   <version>          Graal.Python                  
R                        <version>          FastR                         
ruby                     <version>          TruffleRuby                   
wasm                     <version>          GraalWasm                     
```

Note down the `ComponentId` value for the component you would like to install.

GraalVM Updater verifies whether or not the version of a component is appropriate for your current GraalVM installation. 
A component may require other components as prerequisites for its operation.
GraalVM Updater verifies such requirements and will either attempt to download the required dependencies, or abort the installation if the component's requirements are not met.
Components intended for Oracle GraalVM Enterprise Edition cannot be installed on GraalVM Community Edition.

Generic support for Node.js, R, Ruby, Python, and WebAssembly will work out of the box in most cases.
It is recommended to fine-tune system-dependent configurations, following the recommendations in the component post-installation messages.

## Install Components on GraalVM Community

You can install a component on GraalVM Community Edition **by component's ID** using GraalVM Updater: `gu install ComponentId`. 
GraalVM Updater downloads a component from GitHub.

1. Get a list of components available for your GraalVM version and their descriptive names:
    ```shell
    gu available
    ```
2. Install a component package using the `ComponentId` value. For example, `js`:
    ```shell
    gu install js
    ```
GraalVM Updater first downloads the list of components, then uses the information in the list to download the actual component package, and then installs it.
To see more verbose output during the installation, as the download progress bar, print versions, and dependency information, use the `-v` (`--verbose`) switch.

If a component is installed that depends on another component, GraalVM Updater will search for the appropriate dependency and install it as well.
If a required component cannot be found, the installation will fail.

## Install Components on GraalVM Enterprise

You can install a component on GraalVM Enterprise Edition **by component's ID** using GraalVM Updater: `gu install ComponentId`.
GraalVM Updater downloads a component from Oracle's storage point.

Installing a component to GraalVM Enterprise requires a user to provide a valid email address and accept a license for a given component.
GraalVM Updater uses the **download token** (a personal access token, an alternative to using a password) which is bound to the user's email and defines the set of accepted licenses.
Follow these steps to install a component to GraalVM Enterprise, for example, Native Image: 

1. Run `gu available` to list the available components for GraalVM Enterprise Edition you have installed.
    
    ```shell
    gu available
    ```

2. Install a component using the `ComponentId` value:

    ```shell
    gu install native-image
    ```
   You will see a message to provide your valid email address or input your download token and press `ENTER`. 
   Supposedly, this is your first installation and you have not accepted the license yet. So press `ENTER`.

3. Type your valid email address. You will immediately be sent an email to verify your email address and accept the license. If an email address is not provided, `gu` will abort the installation and print an error message that no download token was provided.

4. Go to your email client and review the [Oracle Technology Network License Agreement GraalVM Enterprise Edition Including License for Early Adopter Versions](https://www.oracle.com/downloads/licenses/graalvm-otn-license.html). 

5. Accept the license. This way you confirm the license acceptance and generate a download token simultaneously.

6. Return to the console window and press `ENTER` to continue. It will ask you where to store the download token on your computer. By default, the download token will be saved in the `~/.gu/config` file in the user's home directory. To confirm the default location, type `yes`. The download and installation of the component will start.

Once the installation completes, you can continue installing other components using the same command: `gu install ComponentId`. 
GraalVM Updater reads your `~/.gu/config` and you do not have to re-accept the license. 
 
Consider the following aspects:

* A single download token is associated with a single email address.
* If you use the same email address to download a GraalVM Enterprise artifact from another computer, the existing download token will become invalid.
* You can transfer a download token to another computer, `gu` will accept it.
* You will be asked to re-accept the license only if the license text changes.

The following commands can help you manage a download token: 

* `--email <address>` to print an e-mail address used for generating a download token
* `--config <path>` to provide the path to a download token
* `--show-ee-token` to print a saved download token

### Provide a Download Token via Environment Variable

A download token can also be provided via an environment variable.
This is useful for automation use cases, for example, in continuous integration (CI) and continuous delivery (CD) setups.

Note: The GitHub Action for GraalVM has [instructions on how to set up CI/CD using the download token](https://github.com/graalvm/setup-graalvm#basic-graalvm-enterprise-edition-template).

As a prerequisite, use the GraalVM Updater manually to set up GraalVM and any components you may need.
As part of this, the GraalVM Updater will help you create a valid download token.

To show this download token, run:

```shell
gu --show-ee-token
```

To use this download token on another machine, copy the download token and set the `$GRAAL_EE_DOWNLOAD_TOKEN` environment variable:
```shell
export GRAAL_EE_DOWNLOAD_TOKEN=<valid-download-token>
```

GraalVM Updater then reads the download token from the variable.
Note: `$GRAAL_EE_DOWNLOAD_TOKEN` takes precedence over the token stored in the `~/.gu/config` file.

## Install Components Manually

You can install a component **from a local file**, in other words, manually.

1. Download a component in consideration of the operating system, the Java version, and architecture (if applicable) from:

    - [Oracle GraalVM Downloads](https://www.oracle.com/downloads/graalvm-downloads.html) for GraalVM Enterprise Edition
    - [GitHub](https://github.com/graalvm/graalvm-ce-builds/releases/) for GraalVM Community Edition

2. Having downloaded the appropriate JAR file, install it with this command:

    ```shell
    gu -L install component.jar
    ```
    The `-L` option, equivalent to `--local-file` or `--file`, installs a component from a downloaded JAR.
    However, a component may depend on other components (e.g., Ruby depends on the LLVM toolchain).
    For example, `gu -L install component.jar` will fail if the required components are not yet installed.
    If all dependencies are downloaded into the same directory, you can run:

    ```shell
    gu -L install -D
    ```

## Install Components from Local Collection

Components can be downloaded manually in advance to a local file folder, or to a folder shared on the local network.
GraalVM Updater can then use that folder instead of the catalog. 
Specify the directory to use for the components collection:
```shell
gu install -C /path/to/downloads/directory ComponentId
```

It is possible to type a component ID (like `ruby`) instead of a filename.
GraalVM Updater will also attempt to find required dependencies in the local component collection.

When installing components from a given directory, you can install all components which have the correct version number using wildcards. For example, you can install the `native-image` component, or anything that starts with `native` like this:
```shell
gu install -C ~/Download/Components/ native*
```

## Uninstall Components

Components may be uninstalled from GraalVM when no longer needed.
To uninstall a specific component, use its `ComponentId`. Run `gu list` to find out the exact `ComponentId`.

The command to uninstall the component is:
```shell
gu remove ComponentId
```

If more components end with, for example, `ruby`, the installer will print an error message that a component’s full name is required (`org.graalvm.ruby`).

Note that the LLVM toolchain component may fail uninstallation if its dependent component(s) remains installed. In this case, remove the dependent component first, or add the `-D` option, which would remove dependent components in addition to those explicitly selected:
```shell
gu -D remove llvm-toolchain
```

## Upgrade GraalVM

You can update the existing GraalVM installation to the most recent version with GraalVM Updater.

For example, having GraalVM 20.x installed, update to the most recent available version with:
```shell
gu upgrade
```

GraalVM Updater will attempt to download the latest version of either GraalVM Enterprise or GraalVM Community Edition, if available.

Consider the following aspects:

* It will not rewrite the existing installation, but unpack it into a new directory and print out the location path.
* It will also verify if you have any optional components installed in the current GraalVM installation and update those as well.
* If your setup involves some environment variables (e.g., `PATH`) pointing to a selected GraalVM installation, those variables should be updated manually.

You can also upgrade the edition from **Community** to **Enterprise**.
To upgrade GraalVM Community Edition to Enterprise, run:
```shell
gu upgrade --edition ee
```

It will install the newest version of GraalVM Enterprise Edition, next to the current installation.
GraalVM Updater will check for the optional component presence, verify if a component is appropriate for the installation, and upgrade it as well.

> Note: You can only upgrade GraalVM to a newer version with GraalVM Updater.
The downgrades to an older version, and from GraalVM Enterprise to Community Edition are not possible.

## Rebuild Images

Language runtime components for GraalVM may change. For example:
- polyglot native libraries become out of sync;
- removed languages runtimes may cause the native binary to fail on missing resources or libraries.

To rebuild and refresh the native binaries (language launchers), use the following command:
```shell
gu rebuild-images [--verbose] polyglot|libpolyglot|js|llvm|python|ruby|R... [custom native-image args]...
```

## Replace Components and Files

A component may be only installed once. 
GraalVM Updater refuses to install a component if a component with the same ID is already installed.
However, the installed component can be replaced. 
GraalVM Updater first uninstalls the component and then installs a new one.

To replace a component, use the `-r` option and the `-L` (`--local-file` or `--file`) option to treat parameters as the local filename of a packaged component:
```shell
gu install -L -r component.jar
gu install -r ruby
```

The process is the same as if `gu remove` is run first and `gu install` next.

GraalVM Updater also refuses to overwrite existing files if the to-be-installed and existing versions differ.
There are cases when refreshing file contents may be needed, such as if they were modified or damaged.
In this case, use the `-o` option:
```shell
gu install -L -o component.jar
gu install -o ruby
```

GraalVM Updater will then instruct the user to replace the contained files of a component.
By default, it will not alter anything. 
Alternatively, use the `-f` (`--force`) option, which disables most of the checks and allows the user to install non-matching versions.

## Configure Proxies

If GraalVM Updater needs to reach the component catalog, or download a component, it may need to pass through the HTTP/HTTPS proxy, if the network uses one.
On macOS, the proxy settings are automatically obtained from the OS.
On Linux, ensure that the `http_proxy` and `https_proxy` environment variables are set appropriately before launching the `gu` tool.
Refer to the distribution and/or desktop environment documentation for the details.

GraalVM Updater intentionally does not support an option to disable certificate or hostname verification, for security reasons.
A user may try to add a proxy's certificate to the GraalVM default security trust store.
A user can also download a component manually to a folder, and then use `gu -L install /path/to/file` or `gu -C /path/to/download/dir install component` to install from a local filesystem.

### Working without Internet Access

If your machine cannot access and download the catalog and components from the Internet, the GraalVM Updater can install components from a local directory, or a directory on an accessible network share.

You need to prepare a directory, download all components that you want to install and their dependencies (in case they require other GraalVM components to work) into that directory.

Then you can use `gu -L install /path/to/file` (where the `-L` option instructs to use local files, equivalent to `--local-file` or `--file`).
Adding the `-D` option will instruct GraalVM Updater to look for potential dependencies in the directory next to the installable file.
Additionally, `gu -C /path/to/download/dir install component` can be used, with the specified directory contents acting as a catalog of components.

Note that with `gu -L` you need to specify the component's file name, but when using `gu -C <dir>`, you need to specify a component ID (`ComponentId`):
```shell
# Specify file location
gu -LD install /tmp/installables/ruby.jar

# Specify component name
gu -C /tmp/instalables install ruby
```

## Configure Installation

The installation command of GraalVM Updater accepts multiple options and parameters:
    ```shell
    gu install [-0CcDfiLMnosruvyxY] param [param ...]
    ```

The following options are currently supported:
* `-0, --dry-run`: dry run, do not change anything
* `-c, --catalog`: treat parameters as component IDs from the GraalVM components catalog. This is the default
* `-C, --custom-catalog <url>`: use a specific catalog URL to locate components
* `-L, --local-file`: treat parameters as local filenames of packaged components
* `-M`: force `gu` to ignore dependencies of installed components
* `-f, --force`: force overwrite, bypass version checks
* `-i, --fail-existing`: fail on an existing component
* `-n, --no-progress`: do not display the downloading progress
* `-o, --overwrite`: overwrite different files
* `-r, --replace`: replace existing components
* `-s, --no-verify-jars`: skip integrity verification of component archives
* `-u, --url`: interpret parameters as URLs of packaged components
* `-v, --verbose`: be verbose. Prints versions and dependency information
* `-x, --ignore`: ignore failures
* `-y, --only-validate`: do not install, just check compatibility and conflicting files
* `-Y, --validate-before`: download, verify, and check file conflicts before any disk change is made

## GraalVM Updater Commands Overview

Command-line help is available by running `gu` or `gu -h`.  Run `gu <command> -h` to get help specific for the particular command. For example, `gu install -h`.

GraalVM Updater usage options:
* `gu info [-cClLnprstuvV] <param>`: print the information about specific component (from file, URL, or catalog)
* `gu available [-aClvV] <expr>`: list components available in the catalog
* `gu install [--0CcDfiLMnosruvyxY] <param>`: install a component package
* `gu list [-clv] <expression>`: list installed components or components from catalog
* `gu remove [-0DfMxv] <id>`: uninstall a component
* `gu upgrade [-cCnLsuxSd] [<ver>] [<cmp>]`: upgrade to the recent GraalVM version
* `gu upgrade --edition ee`: upgrade from GraalVM Community Edition to the most recent available version of GraalVM Enterprise Edition
* `gu rebuild-images`: rebuild the native launchers. Use `-h` for detailed usage
*
GraalVM Updater common options:
* `-A, --auto-yes`: say YES or ACCEPT to a question
* `-c, --catalog`: treat parameters as component IDs from the catalog of GraalVM components. This is the default
* `-C, --custom-catalog <url>`: use user-supplied catalog at URL
* `-e, --debug`: enable debugging and print stacktraces
* `-E, --no-catalog-errors`: do not stop if at least one catalog is working
* `-h, --help`: print help
* `-L, --local-file, --file`: treat parameters as local filenames of packaged components
* `-N, --non-interactive`: enable non-interactive mode. Fail when input is required
* `--show-version`: print version information and continue
* `-u, --url`: interpret parameters as URLs of packaged components
* `-v, --verbose`: enable verbose output. Print versions and dependency information
* `--version`: print version

Additonal options:

* `--email <address>`: print an e-mail address used for generating a download token
* `--config <path>`: provide the path to a download token
* `--show-ee-token`: print a saved download token
* `-k, --public-key <path>`: provide path to custom GPG public key for verification
* `-U, --username <username>`: enter a username for login to Oracle component repository

## What to Read Next

Check the [information about GraalVM components availability and support per platform](../introduction.md#features-support).

On Windows, for example, installing the Native Image component requires Visual Studio Code and Microsoft Visual C++(MSVC). Step-by-step instructions on installing Visual Studio Build Tools and Windows 10 SDK, and starting using Native Image can be found [here](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311).