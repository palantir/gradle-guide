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
* testing-and-running-your-plugins-locally.md
* managed-types-and-properties.md
* diagnosing-build-performance.md
-->

<!-- TableOfContents:START -->
1. [Introduction](guide/introduction.md)
    1. [What is this guide?](guide/introduction.md#what-is-this-guide?)
    2. [In the lair of the complexity demon](guide/introduction.md#in-the-lair-of-the-complexity-demon)
2. [Background: What is a Gradle file?](guide/background-what-is-a-gradle-file.md)
    2. [Plugins](guide/anatomy-of-a-gradle-plugin.md#plugins)
    3. [Extensions](guide/anatomy-of-a-gradle-plugin.md#extensions)
    4. [Tasks](guide/anatomy-of-a-gradle-plugin.md#tasks)
    5. [Next steps](guide/anatomy-of-a-gradle-plugin.md#next-steps)
4. [Testing and running your plugins](guide/testing-and-running-your-plugins-locally.md)
    1. [Testing locally](guide/testing-and-running-your-plugins-locally.md#testing-locally)
    2. [Running your plugin in other repos locally](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-locally)
    3. [Debugging your Gradle plugin running on a different repo](guide/testing-and-running-your-plugins-locally.md#debugging-your-gradle-plugin-running-on-a-different-repo)
    4. [Running your plugin in other repos on CI](guide/testing-and-running-your-plugins-locally.md#running-your-plugin-in-other-repos-on-ci)
5. [Managed Types and Properties](guide/managed-types-and-properties.md)
    1. [Managed types](guide/managed-types-and-properties.md#managed-types)
6. [Diagnosing Build Performance](guide/diagnosing-build-performance.md)
    1. [Expectations of performance](guide/diagnosing-build-performance.md#expectations-of-performance)
    2. [Finding a build scan](guide/diagnosing-build-performance.md#finding-a-build-scan)
    3. [Tasks: How to diagnose performance issues](guide/diagnosing-build-performance.md#tasks-how-to-diagnose-performance-issues)
    4. [Configuration: How to diagnose performance issues](guide/diagnosing-build-performance.md#configuration-how-to-diagnose-performance-issues)
    5. [Common perf issues](guide/diagnosing-build-performance.md#common-perf-issues)
<!-- TableOfContents:END -->

