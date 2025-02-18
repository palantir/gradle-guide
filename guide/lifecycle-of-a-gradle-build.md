<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="managed-types-and-properties.md">Managed Types and Properties</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Lifecycle of a Gradle Build

> Further reading: [Gradle Build Lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html)

Gradle build have several stages, each of which are going to investigate in depth. As an overview, they are:

1. Starting the build
2. Settings evaluation
3. Configuration phase
4. Task dependency graph calculation
5. Task execution

## Starting the build

When you _invoke_ gradle, you call the `./gradlew` script and provide a set of "requested tasks":

```
./gradlew taskA taskB
```

This `./gradlew` script runs the "Gradle Wrapper" - a new process that runs code in the jar checked into your repo at `gradle/wrapper/gradle-wrapper.jar`. The Gradle Wrapper will ensure the Gradle distribution listed in `gradle/wrapper/gradle-wrapper.properties` is installed - if it isn't, it will download and install it. [Further reading about the Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html).

The Gradle Wrapper will now check to see if a "Gradle Daemon" already exists. Daemons are long-lived instances of Gradle which speed up build times by keeping the JVM running between builds. If a Daemon already exists, it will be reused. If not, a new one will be started. [Further reading about the Gradle Daemon](https://docs.gradle.org/current/userguide/gradle_daemon.html).

The Daemon now proceeds to load the `settings.gradle` file which takes us onto the next stage.

## Settings evaluation

The `settings.gradle` file is evaluated. This file is responsible for configuring which projects are loaded. The code in this file is run, along with any settings plugins that are applied. Once the list of projects is determined, the Daemon will evaluate and configure each project in turn.

## Configuration phase

Starting from the root project, Gradle will evaluate each project's `build.gradle` scripts one-by-one. This is a single-threaded process[^1]. The scripts apply plugins, which run code. Both scripts and plugin code create and mutate Gradle domain objects like `Configuration`s, `SourceSet`s and most importantly `Task`s.

[^1]: [Isolated projects](https://docs.gradle.org/current/userguide/isolated_projects.html) is an upcoming Gradle feature that will enable project configuration to run in parallel, provided a very strict series of are adhered to that limits projects reading other project's configuration.

Tasks have input and output properties. At configuration time, these tasks are created and have their inputs and outputs configured (however, ideally these input and output properties are lazy and not yet calculated).

## Task dependency graph calculation

Gradle works out which tasks need to run before which other ones, starting from the requested tasks. The `dependsOn` and `mustRunAfter` properties for each task are evaluated. Where task input properties have been linked up to task output properties, Gradle is able to keep track of these task dependencies _without_ evaluating the values of the properties.

Gradle now has a complete task dependency graph, and is ready to execute the tasks.

## Task execution

Gradle actually executes the tasks, scheduling them in dependency order. For each task:

* The task's `onlyIf`s, and `enabled` properties are inspected to see if it should run.
* The input and output properties are only now evaluated.
* Gradle decides whether the task is up-to-date (it's run before and none of it's inputs or outputs have changed) or whether it needs executing.
* Build cache keys are calculated by hashing the inputs of the task:
  * If there is a cache hit, the cached outputs are downloaded and used.
* Any pre-existing output directories or files are deleted.
* The task actions on the task are run.
* If appropriate, the task outputs are saved to the build cache.

## Build finish

Once all tasks have been executed, the build is finished. Gradle will print out the build result and possibly publish a build scan.

The Daemon will now wait for a period of time to see if it is needed again. If not, it will shut down.

<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="managed-types-and-properties.md">Managed Types and Properties</a></td>
  <td align="center"><a href="../README.md#table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="avoiding-unnecessary-configuration.md">Avoiding Unnecessary Configuration</a></td>
</tr></table>
<!-- PreviousNext:END -->
