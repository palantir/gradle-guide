# gradle-guide

CURRENTLY IN PROGRESS

A guide for writing good Gradle plugins and a plugin to check for bad Gradle code.

## Guide Table of Contents

<!-- TableOfContentsSource:
* introduction.md
* background-what-is-a-gradle-file.md
* anatomy-of-a-gradle-plugin.md
* testing-and-running-your-plugins-locally.md
* managed-types-and-properties.md
* diagnosing-build-performance.md
-->

<!-- TableOfContents: START -->
1. [Introduction](guide/introduction.md)
    1. [Introduction](guide/introduction.md#introduction)
    2. [What is this guide?](guide/introduction.md#what-is-this-guide?)
    3. [In the lair of the complexity demon](guide/introduction.md#in-the-lair-of-the-complexity-demon)
2. [Background: What is a Gradle file?](guide/background-what-is-a-gradle-file.md)
    1. [Background: What is a Gradle file?](guide/background-what-is-a-gradle-file.md#background:-what-is-a-gradle-file?)
    2. [`.gradle` files](guide/background-what-is-a-gradle-file.md#.gradle-files)
    3. [Gradle script Groovy <-> Java transliteration](guide/background-what-is-a-gradle-file.md#gradle-script-groovy---java-transliteration)
    4. [`settings.gradle` file](guide/background-what-is-a-gradle-file.md#settings.gradle-file)
    5. [`build.gradle` files](guide/background-what-is-a-gradle-file.md#build.gradle-files)
3. [Anatomy of a Gradle Plugin](guide/anatomy-of-a-gradle-plugin.md)
    1. [Anatomy of a Gradle Plugin](guide/anatomy-of-a-gradle-plugin.md#anatomy-of-a-gradle-plugin)
    2. [A simple motivating example](guide/anatomy-of-a-gradle-plugin.md#a-simple-motivating-example)
    3. [Plugins](guide/anatomy-of-a-gradle-plugin.md#plugins)
    4. [Extensions](guide/anatomy-of-a-gradle-plugin.md#extensions)
    5. [Tasks](guide/anatomy-of-a-gradle-plugin.md#tasks)
    6. [Next steps](guide/anatomy-of-a-gradle-plugin.md#next-steps)
4. [Testing and running your plugins](guide/testing-and-running-your-plugins-locally.md)
    1. [Testing and running your plugins](guide/testing-and-running-your-plugins-locally.md#testing-and-running-your-plugins)
    2. [Testing locally](guide/testing-and-running-your-plugins-locally.md#testing-locally)
    3. [Running your plugin in other repos locally](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-locally)
    4. [Debugging your Gradle plugin running on a different repo](guide/testing-and-running-your-plugins-locally.md#debugging-your-gradle-plugin-running-on-a-different-repo)
    5. [Running your plugin in other repos on CI](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-on-ci)
5. [Managed Types and Properties](guide/managed-types-and-properties.md)
    1. [Managed Types and Properties](guide/managed-types-and-properties.md#managed-types-and-properties)
    2. [Managed types](guide/managed-types-and-properties.md#managed-types)
6. [Diagnosing Build Performance](guide/diagnosing-build-performance.md)
    1. [Diagnosing Build Performance](guide/diagnosing-build-performance.md#diagnosing-build-performance)
    2. [Expectations of performance](guide/diagnosing-build-performance.md#expectations-of-performance)
    3. [Finding a build scan](guide/diagnosing-build-performance.md#finding-a-build-scan)
    4. [Tasks: How to diagnose performance issues](guide/diagnosing-build-performance.md#tasks:-how-to-diagnose-performance-issues)
    5. [Configuration: How to diagnose performance issues](guide/diagnosing-build-performance.md#configuration:-how-to-diagnose-performance-issues)
    6. [Common perf issues](guide/diagnosing-build-performance.md#common-perf-issues)
<!-- TableOfContents: END -->

