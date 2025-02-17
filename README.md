<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-guide"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

# gradle-guide

CURRENTLY IN PROGRESS

A guide for writing good Gradle plugins and a plugin to check for bad Gradle code.

## Table of Contents

<!-- TableOfContentsSource:
* introduction.md
* background-what-is-a-gradle-file.md
* anatomy-of-a-gradle-plugin.md
* managed-types-and-properties.md
* avoiding-unnecessary-configuration.md
* testing-and-running-your-plugins-locally.md
* diagnosing-build-performance.md
-->

<!-- TableOfContents:START -->
1. [Introduction](guide/introduction.md)
    1. [What is this guide?](guide/introduction.md#what-is-this-guide?)
    2. [In the lair of the complexity demon](guide/introduction.md#in-the-lair-of-the-complexity-demon)
2. [Background: What is a Gradle file?](guide/background-what-is-a-gradle-file.md)
    1. [`.gradle` files](guide/background-what-is-a-gradle-file.md#.gradle-files)
    2. [Gradle script Groovy <-> Java transliteration](guide/background-what-is-a-gradle-file.md#gradle-script-groovy---java-transliteration)
    3. [`settings.gradle` file](guide/background-what-is-a-gradle-file.md#settings.gradle-file)
    4. [`build.gradle` files](guide/background-what-is-a-gradle-file.md#build.gradle-files)
3. [Anatomy of a Gradle Plugin](guide/anatomy-of-a-gradle-plugin.md)
    1. [A simple motivating example](guide/anatomy-of-a-gradle-plugin.md#a-simple-motivating-example)
    2. [Plugins](guide/anatomy-of-a-gradle-plugin.md#plugins)
    3. [Extensions](guide/anatomy-of-a-gradle-plugin.md#extensions)
    4. [Tasks](guide/anatomy-of-a-gradle-plugin.md#tasks)
    5. [Next steps](guide/anatomy-of-a-gradle-plugin.md#next-steps)
4. [Managed Types and Properties](guide/managed-types-and-properties.md)
    1. [Managed types](guide/managed-types-and-properties.md#managed-types)
5. [Avoiding Unnecessary Configuration](guide/avoiding-unnecessary-configuration.md)

6. [Testing and running your plugins](guide/testing-and-running-your-plugins-locally.md)
    1. [Testing locally](guide/testing-and-running-your-plugins-locally.md#testing-locally)
    2. [Running your plugin in other repos locally](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-locally)
    3. [Debugging your Gradle plugin running on a different repo](guide/testing-and-running-your-plugins-locally.md#debugging-your-gradle-plugin-running-on-a-different-repo)
    4. [Running your plugin in other repos on CI](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-on-ci)
7. [Diagnosing Build Performance](guide/diagnosing-build-performance.md)
    1. [Expectations of performance](guide/diagnosing-build-performance.md#expectations-of-performance)
    2. [Finding a build scan](guide/diagnosing-build-performance.md#finding-a-build-scan)
    3. [Tasks: How to diagnose performance issues](guide/diagnosing-build-performance.md#tasks-how-to-diagnose-performance-issues)
    4. [Configuration: How to diagnose performance issues](guide/diagnosing-build-performance.md#configuration-how-to-diagnose-performance-issues)
    5. [Common perf issues](guide/diagnosing-build-performance.md#common-perf-issues)
<!-- TableOfContents:END -->

