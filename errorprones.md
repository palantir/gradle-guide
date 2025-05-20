# gradle-guide Error Prone Permalinks

<table>
<thead>
<tr>
<td>Name</td>
<td>Detailed Link</td>
<td>Description</td>
</tr>
</thead>
<tbody>
<tr>
<td>

<a id="ConfigurationAvoidanceRegistration" href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">`ConfigurationAvoidanceRegistration`</a>

</td>
<td>
<a href="guide/avoiding-unnecessary-configuration.md#lazy-task-registration">Please read</a>
</td>
<td>

When registering a new `Task`, `Configuration` or other Gradle domain type, use `.register` instead of `.create` to avoid realising the object eagerly and performing unnecessary work which will slow down the build.

</td>
</tr>

<tr>
<td>

<a id="NonAbstractClassField" href="guide/managed-types-and-properties.md">`NonAbstractClassField`</a>

</td>
<td>
<a href="guide/managed-types-and-properties.md">Please read</a>
</td>
<td>

Do not declare class fields directly on Tasks or Extensions. Instead, declare an abstract getter method, e.g., 'public abstract Property<String> getFoo();'. This enables Gradle to inject the property implementation automatically, removes boilerplate, and supports the Groovy DSL (e.g. `foo = 3`).

</td>
</tr>

<tr>
<td>

<a id="NonAbstractGradleType" href="guide/managed-types-and-properties.md">`NonAbstractGradleType`</a>

</td>
<td>
<a href="guide/managed-types-and-properties.md">Please read</a>
</td>
<td>

When defining a custom Task or Extension, you should make it an abstract class with abstract getter methods of each of the properties and other gradle containers (eg NamedDomainObjectSet). Gradle will then automatically create the properties and containers, removing a lot of boilerplate. Additionally, as you declare eg `public abstract Property<Integer> getFoo()`, this will automatically make the `foo = 3` groovy syntax work of the box.

</td>
</tr>
</tbody>
</table>
