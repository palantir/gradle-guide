<!-- PreviousNext:START -->
<table><tr>
  <td>Previous: <a href="anatomy-of-a-gradle-plugin.md#">Anatomy of a Gradle Plugin</a></td>
  <td align="center"><a href="../README.md##table-of-contents">Table of Contents</a></td>
  <td align="right">Next: <a href="managed-types-and-properties.md#">Managed Types and Properties</a></td>
</tr></table>
<!-- PreviousNext:END -->

# Testing and running your plugins

Here we'll cover the best ways to test your code locally for fast iteration but also how to run your plugins on real projects, both locally and in CI. We'll not be going in-depth, that will be covered in the "Testing best practices" page.

## Testing locally

Writing a test in your plugin's repo and running locally is _by far_ the most efficient way to work out if your code works and debug issues. It allows you to create minimal reproducers that makes it far easier to work out what is going wrong than running in a real project.

When you made your repo, you should have a test (an `IntegrationSpec`) already existing. You can define tests here and run them. This will enable to you quickly iterate and also easily run the debugger to work out what Gradle is actually doing.

## Running your plugin in other repos locally

You do not need to have your code merged to developed and released to test in other repos. There is a simple way to do this locally:

1. In your repo, run the command `CIRCLE_TAG=999 ./gradlew pTML`.
   1. This runs the `publishToMavenLocal` task, which will locally publish your gradle plugin to the maven local repo at `~/.m2/repository`.
   2. We set `CIRCLE_TAG=999` as an easy way to set the version, otherwise it changes each time. `999` will be greater than any published version too, meaning our code will always be used. 
2. In the repo you wish to test in, add `mavenLocal()` to `repositories` block in the `buildscript` of the root `build.gradle`:
    ```diff
    buildscript {
        repositories {
    +       mavenLocal()
        }
    }
    ```
   1. You may also need to add `mavenLocal()` to other `repositories` blocks if the gradle plugin publishes a library as well.
3. Find where the gradle plugin has its version and change the version to `999` - this is either in:
   1. The buildscript, eg:
        ```gradle
        buildscript {
            dependencies {
                classpath 'my-plugin-group:my-plugin-name:<version>'
            }
        }
        ```
   2. A plugins block, eg:
        ```
        plugins {
            id '<my plugin id>' version '<version>'
        }
        ```
      1. In this case, you'll also need to add `mavenLocal()` to the `pluginManagement`'s `repositories` block in `settings.gradle`.
4. Run whatever Gradle command you wanted to try in the target repo.
5. To run with a newer version of the code you only need redo step 1. You should use the same version `999` to avoid needing to change the version again.
6. Make sure to remove the `mavenLocal()` changes before committing to git!

### Automatically publishing to maven local

If you are making lots of changes in another repo, first consider: should you be doing this in a test instead? It will be less painful and you'll have a test case that can be run as part of the build forever.

If you really do need to iterate locally, you can use the `--continuous`/`-t` gradle flag to your advantage.

Instead of step 1, run `CIRCLE_TAG=999 ./gradlew pTML -t`.

This will automatically republish the plugin each time you make a change in your plugin repo. Then you can rerun your Gradle invocation in the target repo without having to manually run `./gradlew pTML` again.

### Avoiding having to add `mavenLocal()`

If you do a lot of Gradle plugin development and support, you may find it tedious to have to add `mavenLocal()` to every repo to try out local changes. Luckily, we can automate this using [Gradle Initialization Scripts](https://docs.gradle.org/current/userguide/init_scripts.html). These are scripts that are run as part of _every_ Gradle invocation you run on your machine. We can make a script that will automatically add `mavenLocal()` to all the places we need on every Gradle invocation.

Simply make a file in `~/.gradle/init.d/maven-local.gradle` with these contents:

```gradle
allprojects {
    buildscript {
        repositories {
            mavenLocal()
        }
    }

    repositories {
        mavenLocal()
    }
}
```

> Note: this will run builds with different repositories than on other people's/CI's machines. There is the risk you end up using something unexpected from your Maven local repo. Always bare this in mind if using this script.

## Debugging your Gradle plugin running on a different repo

You can also debug your Gradle plugin when published to maven local as per above in a different repo. This is somewhat painful, but possible - prefer writing a test in plugin repo as much as possible.

1. Ensure your plugin repo and target repo are using the same version of the Gradle wrapper. If they are not, you'll need to change one or the other and refresh Gradle in IntelliJ.
2. In IntelliJ for your plugin repo:
   1. Click the "configurations" drop down in the top right hand corner.
   2. Edit Configurations
   3. Hit the `+` button in the top left of the new window
   4. Select "Remote JVM Debug"
   5. Change "Debugger mode" to "Listen to remote JVM"
   6. Select "Auto-restart"
   7. Run the debug configuration you just made.
   8. Add any breakpoints you are interested in.
3. In the target repo:
   1. Add the following to `gradle.properties` (if `org.gradle.jvmargs` already exists, append the `-agentlib` bit onwards with a space preceeding):
      ```
      org.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=n,address=localhost:5005
      ```
   2. Run your Gradle invocation. It should make a new daemon that attaches to the debugger and triggers the breakpoints.
   3. You _shouldn't_ need to restart the debugger/daemon in between runs. But it does sometimes go wrong. In these cases you can try:
      1. Running `./gradlew --stop` to stop all daemons and make a new one that should attach to the debugger.
      2. Restarting the debugger (if you selected auto-restart this should already happen).

This can be insightful to see what gradle is _actually_ doing in a build, but often there is so much going on it isn't much help. Paring down the target repo's Gradle code until you create a minimal reproducer and making a test for that is normally far, far easier. You can waste a lot of time debugging like this and end up more confused than you were before.

## Running your plugin in other repos on CI

Sometimes, you really do need to test your plugin in a CI environment. In every circumstance should prefer testing and running locally if possible. But we do have a way to do this internally:

1. Run `CIRCLE_TAG=999-some-unique-id ./gradlew pTAS`
   1. This will publish to place that can be accessed by CI, but artifacts will expire within days.
   2. Every time you publish you **must** the change the `some-unique-id` part. Otherwise, CI nodes will use the previous version that is in their Gradle caches and not your new change. 
2. Do step 2 in the "Running your plugin in other repos locally" section, except instead of `mavenLocal()` add:
    ```
    maven {
        url '<internal-url>'
    }
    ```
   replacing `<internal-url>` with the place [this link redirects to](https://pl.ntr/2kb).
3. Replace the version of the plugin as per step 3 in the "Running your plugin in other repos locally" section, but instead of `999` do your `999-some-unique-id`.
4. Push up and it should run on CI.
5. _Do not_ merge this change into develop. If this is on a PR, mark the PR as "do not merge" and leave a comment on maven line additions to remind yourself to remove it.