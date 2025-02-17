<div style="display: flex; justify-content: space-between;"><span>Previous: <a>Introduction</a></span><span>Contents</span><span>Next: <a>Managed Types and Properties</a></span></div>

# Background: What is a Gradle file?

`.gradle` files can look like magic - but this is usually just some tricksy syntatic sugar. Every Gradle script could be written in Java - in fact this is what we do when we write a Gradle plugin. In this section we'll reveal what's behind this "magic" syntax, which will be essential when debugging issues with custom Gradle, and should help you write Java plugins

## `.gradle` files

These are the files that define the build specific logic for a Gradle project. They're written in the Groovy programming language, which you can think of as the unholy combination of the syntax of Ruby and semantics of Java. Groovy runs on the JVM and is (more or less) a superset of Java, so any valid Java code is also valid Groovy code. There's a pretty straightforward transliteration of the Groovy syntax Gradle scripts regularly use and Java, which are shown below:

## Gradle script Groovy <-> Java transliteration

> Further reading: [Learn X in Y minutes for Groovy](https://learnxinyminutes.com/docs/groovy/)

At the top level, Gradle has a `Project` object that all method calls which are not attached to a particular object are called on:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
someMethod(someArg)
```

</td><td>

```java
project.someMethod(someArg);
```

</td></tr></tbody></table>

You can omit the parentheses for method calls:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
someMethod foo, bar, baz
```

</td><td>

```java
project.someMethod(foo, bar, baz);
```

</td></tr></tbody></table>


Getters and setters can have the `get` and `set` parts omit in Groovy:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
something
```

</td><td>

```java
project.getSomething();
```

</td></tr><tr><td>

```groovy
something = 3
```

</td><td>

```java
project.setSomething(3);
```

</td></tr></tbody></table>

Maps can be made using `[foo: 3]` syntax or given directly to method calls without the square brackets:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
def map = [foo: 'bar', baz: 3]
```

</td><td>

```java
// Note completely untyped - that's groovy for you
Map map = new HashMap();
map.put("foo", "bar");
map.put("baz", 3);
```

</td></tr><tr><td>

```groovy
// This looks like "named parameters"
someMethod foo: 3, bar: 'baz'
```

</td><td>

```java
// It's actually just a map
Map tempMap = new HashMap();
tempMap.put("foo", 3);
tempMap.put("bar", "baz");

project.someMethod(tempMap);
```

</td></tr></tbody></table>

Closures are similar to Java 8 lambdas. They allow you to define a function and can "close over" some variables. You can give them as the final argument to a method call using curly braces directly:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
def string = 'foo'

def closure = { arg ->
    arg.doSomething(string)
}
```

</td><td>

```java
String string = "foo";

// Note you can't write closures in Java directly,
// but this is what it would be like as a lambda
Closure closure = arg -> arg.doSomething(string);
```

</td></tr><tr><td>

```groovy
// For one arg closures, you can
// use `it` implicitly
def closureWithImplicitArg = {
    it.doSomething()
} 
```

</td><td>

```java
// It's really the same as just specifying the
// parameter explicitly in the closure
Closure closureWithImplicitArg = arg -> {
    arg.doSomething();
};
```

</td></tr><tr><td>

```groovy
// This looks like something in-built with 
// the curly braces
someMethod('foo') {
    it.blah()
}
```

</td><td>

```java
// It's actually just a way of passing a closure
// as the last argument
someMethod('foo', arg -> {
    arg.blah();
});
```

</td></tr></tbody></table>

Closures have a special piece of state on them called a `delegate`. When a closure is run, and a method is called inside the closure on no object, the delegate is used as that object. We've already seen a use of `delegate` - how raw method calls in Gradle files end up called on `project`. In this case, the `delegate` was set to project before the closure was called. This is a big part of how Gradle's DSLs (especially Gradle "extension") are enabled. Example:

<table><thead><tr><td>Gradle</td><td>Behind the scenes</td></tr></thead><tbody><tr><td>

```groovy
someGradleExtension {
    hello()
}
```

</td><td>

```java
class SomeGradleExtension {
    void hello() {
        println 'hello'
    }
}

void someGradleExtension(Closure closure) {
    SomeGradleExtension extension = ...;
    closure.setDelegate(extension);
    closure.call();
}
```

</td></tr></tbody></table>

## `settings.gradle` file

This is the entry point for gradle builds. It must exist in Gradle projects and includes other projects:

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
include 'projectA'
include 'projectB'
```

</td><td>

_[`Settings`](https://docs.gradle.org/current/javadoc/org/gradle/api/initialization/Settings.html) is the delegate rather than `Project` here._

```java
settings.include('projectA');
settings.include('projectB');
```

</td></tr></tbody></table>

## `build.gradle` files

There's is one of these per Gradle project, although they can be omitted if there is no configuration. In it you can:

**Apply plugins**

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
apply plugin: 'java'
```

</td><td>

```java
project.apply(Map.of("plugin", "java"));
```

</td></tr></tbody></table>

**Configure Extensions**

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
someExtension {
    someProperty = 3
}
```

</td><td>

_We'll go deeper into how extensions work later_

```java
SomeExtension someExtension = project.getExtensions()
        .withType(SomeExtension.class)
        .getBytName("someExtension");

someExtension.getSomeProperty().set(3);
```

</td></tr></tbody></table>

**Create tasks**

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
task writeToFile {
    doFirst {
        file('hello').text = 'blah'
    }
}
```

</td><td>

```java
project.getTasks().register("writeToFile", writeToFile ->{
    writeToFile.doFirst(new Action() {
        public void execute () {
            ProcessGroovyMethods.setText(project.file("hello"), "blah");
        }
    });
});
```

</td></tr></tbody></table>

**Add dependencies**

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
dependencies {
    implementation 'group:name'
    implementation group: 'foo', artifactId: 'bar'
}
```

</td><td>

_The groovy literally calls `project.getDependencies().configure(closure)` but below is more "normal" for java._

```java
DependencyHandler dependencies = project.getDependencies();
dependencies.add("implementation", "group:name");
dependencies.add("implementation", Map.of("group", "foo", "artifactId", "bar"));
```

</td></tr></tbody></table>

**Create configurations**

<table><thead><tr><td>Gradle</td><td>Java</td></tr></thead><tbody><tr><td>

```groovy
configurations {
    someConfiguration
}
```

</td><td>

```java
Configuration someConfiguration = project.getConfigurations()
        .create("someConfiguration");
```

</td></tr></tbody></table>

<hr>
<div style="display: flex; justify-content: space-between;"><span>Previous: <a>Introduction</a></span><span>Next: <a>Managed Types and Properties</a></span></div>