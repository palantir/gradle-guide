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

<a id="ProviderGet" href="guide/avoiding-unnecessary-configuration.md#using-taskproviders">`ProviderGet`</a>

</td>
<td>
<a href="guide/avoiding-unnecessary-configuration.md#using-taskproviders">Please read</a>
</td>
<td>

Do not call `Provider.get`. Instead, pass providers directly to methods that accept them, or transform providers using `Provider.map` or `Provider.flatMap`, or combine providers using `Provider.zip`. Calling `Provider.get` causes Gradle to lose track of implicit dependencies and can lead to timing issues by reading values too early.

</td>
</tr>
</tbody>
</table>
