# Updating Unicode data files

In order to update the Unicode data files, follow these steps:

1. Download the following files into your current working directory (e.g. `graal/regex`). If updating to another version, replace 12.1.0 with the version you are aiming for.
   * `UnicodeData.txt` (https://www.unicode.org/Public/12.1.0/ucd/UnicodeData.txt)
   * `CaseFolding.txt` (https://www.unicode.org/Public/12.1.0/ucd/CaseFolding.txt)
   * `SpecialCasing.txt` (https://www.unicode.org/Public/12.1.0/ucd/SpecialCasing.txt)
   * `PropertyAliases.txt` (https://www.unicode.org/Public/12.1.0/ucd/PropertyAliases.txt)
   * `PropertyValueAliases.txt` (https://www.unicode.org/Public/12.1.0/ucd/PropertyValueAliases.txt)
   * `ucd.nounihan.flat.xml` (https://www.unicode.org/Public/12.1.0/ucdxml/ucd.nounihan.flat.zip)
     * You will need to unzip the archive.
   * `emoji-data.txt` (https://unicode.org/Public/emoji/12.0/emoji-data.txt)
2. Run `src/com.oracle.truffle.regex/tools/unicode-script.sh`. This generates the following files in your current working directory:
   * `UnicodeFoldTable.txt`
   * `NonUnicodeFoldTable.txt`
   * `PythonSimpleCasing.txt`
   * `PythonExtendedCasing.txt`
3. Run `src/com.oracle.truffle.regex/tools/generate_case_fold_table.clj >> src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/tregex/parser/CaseFoldTable.java` to generate the new case fold tables and append them to `CaseFoldTable.java`. Then open `CaseFoldTable.java` in an editor to replace the old character data with the new definitions.
  * In order to run this script, you will need to have a way to run Clojure scripts.
    * You can use Boot (https://boot-clj.com/), which lets you execute the script directly. Boot can usually be installed from your distribution's package manager.
    * Alternatively, you can use a Clojure jar file directly as in `java -jar clojure-1.8.0.jar --init src/com.oracle.truffle.regex/tools/generate_case_fold_table.clj --eval '(-main)'`.
4. Run `src/com.oracle.truffle.regex/tools/generate_unicode_properties.py > src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/charset/UnicodePropertyData.java`. This rewrites `UnicodePropertyData.java` to contain the new definitions of Unicode properties.
5. Run the `main` method of `com.oracle.truffle.regex.charset.UnicodeGeneralCategoriesGenerator` and replace `src/com.oracle.truffle.regex/src/com/oracle/truffle/regex/charset/UnicodeGeneralCategories.java` with its output.
6. Run `mx eclipseformat` to fix any code formatting issues.

Steps 1-4 are automated by `run_scripts.sh`. This script assumes you have the following things installed: `clojure`, `python3`, `wget`, and `unzip`.
