# Draft Bug Report for micronaut-projects/micronaut-core

Drafted for: https://github.com/micronaut-projects/micronaut-core/issues
(New Issue → Bug Report)

No existing open or closed issue matches this exact scenario (searched for
`RequestBean`, `jspecify`, `nullable`, `12632` — see "Related issues found"
below for the closest match, which appears to be the root cause rather than
a duplicate). The fields below map 1:1 to the repo's Bug Report issue
template (`.github/ISSUE_TEMPLATE/bug_report.yaml`).

---

## Title

`@RequestBean` record with all-@Nullable components rejected as 400 Bad Request when no query parameters are present (regression since 5.1.0)

## Expected Behavior

When a `@Controller` method binds query parameters via `@RequestBean` into
a Java `record` whose every component is annotated `@Nullable`
(`org.jspecify.annotations.Nullable`), a request with **none** of those
query parameters present should succeed with `200 OK` and the bean
constructed with all components `null` — exactly as it does when only
*some* of the query parameters are present.

```java
@Introspected
public record PersonFilter(
    @Nullable String name,
    @Nullable Integer age,
    @Nullable String city
) {}

@Controller("/people")
public class PersonController {
    @Get("/search")
    public Map<String, Object> search(@RequestBean PersonFilter filter) {
        return Map.of("name", filter.name(), "age", filter.age(), "city", filter.city());
    }
}
```

`GET /people/search` (no query string) should return `200 OK` with
`{"name": null, "age": null, "city": null}`.

## Actual Behaviour

On Micronaut Core 5.1.0, 5.1.1 and 5.1.2, `GET /people/search` (no query
parameters) returns `400 Bad Request`. The exact same code, with only the
Micronaut Core version changed back to 5.0.3, returns `200 OK` as expected.

Critically, this only happens when **zero** of the record's query
parameters are present:
- No parameters at all → 400 (regression)
- Any subset of parameters (partial or all) → 200 (works correctly)

## Steps To Reproduce

1. Generate a Micronaut Maven project (Java 17, JUnit 5) via
   https://launch.micronaut.io, add `org.jspecify:jspecify` as a
   dependency.
2. Add a record with every component annotated
   `org.jspecify.annotations.Nullable`, annotated `@Introspected`:
   ```java
   @Introspected
   public record PersonFilter(
       @Nullable String name,
       @Nullable Integer age,
       @Nullable String city
   ) {}
   ```
3. Add a controller method using `@RequestBean` to bind it:
   ```java
   @Get("/search")
   public Map<String, Object> search(@RequestBean PersonFilter filter) { ... }
   ```
4. With `micronaut.version` (and the `micronaut-core-bom` import) set to
   `5.0.3`: `GET /people/search` (no query string) → `200 OK`.
5. Change only `micronaut.version` to `5.1.0`, `5.1.1`, or `5.1.2`, rebuild:
   `GET /people/search` (no query string) → `400 Bad Request`.
6. Adding any single query parameter (e.g. `?name=Jane`) on 5.1.x → back to
   `200 OK`.

A full, runnable reproduction with an integration test demonstrating all of
the above (including a `BUILD SUCCESS` on 5.0.3 and the failing test on
5.1.2) is linked under "Example Application" below.

## Environment Information

- Operating System: macOS (Darwin), arm64
- JDK Version: Temurin/OpenJDK 25 (required to run Micronaut 5.x's own
  annotation processors — they are compiled for class file version 69 and
  fail to load under JDK 24 or earlier with `UnsupportedClassVersionError`)
- Build tool: Maven 3.9.15 (via `mvnw`)
- Affected Micronaut Core versions: 5.1.0, 5.1.1, 5.1.2
- Last known-good version: 5.0.3

## Example Application

https://github.com/ClaasWilke/micronaut-requestbean-bug

The repo demonstrates the regression with a Maven project and an
integration test (`PersonControllerTest`) that passes on 5.0.3 and fails
on 5.1.2; see `BUG_DESCRIPTION.md` in the repo for the full writeup.

## Version

5.1.2 (also reproduced on 5.1.0 and 5.1.1; not present on 5.0.3)

---

## Related issues found (context, not part of the form)

While searching for existing reports, found what appears to be the root
cause rather than a duplicate:

- [#12608](https://github.com/micronaut-projects/micronaut-core/issues/12608)
  "Introspected types are always instantiated when binding from request"
  — reported that `@Introspected` beans bound via `@QueryValue`/`@RequestBean`
  were always instantiated even when no request data was present, so
  required/`@NotNull` constraints on them were silently never enforced.
- [#12632](https://github.com/micronaut-projects/micronaut-core/pull/12632)
  "Instantiate introspected beans only when request values are present"
  — the fix for #12608, merged into the `5.1.x` branch on 2026-06-06. Per
  its description: *"Avoid instantiating `@Introspected` beans for
  `@QueryValue` and `@RequestBean` when no request values are present.
  Return null instead of an empty object. Existing behavior for required
  arguments (e.g. HTTP 400) is unchanged; this applies when the argument is
  treated as optional/nullable."*

This fix appears to be the direct cause of the regression described here:
when none of a `@RequestBean` bean's properties are present, the binder now
resolves the bean argument to `null`. Whether that's accepted depends on
whether the **`@RequestBean` parameter itself** is nullable — not on
whether the bean's individual components are. Since our `PersonFilter`
parameter has no nullability annotation on the parameter itself (only on
its record components), Micronaut treats it as a missing *required*
argument and returns 400.

**Suggested fix direction**: when every component of an `@Introspected`
bean bound via `@RequestBean`/`@QueryValue` is itself `@Nullable`, treat the
whole bean as implicitly optional too (consistent with the pre-5.1
behavior), rather than requiring a redundant `@Nullable` on the
`@RequestBean`-annotated parameter as well.

**Known workaround**: explicitly annotate the `@RequestBean` parameter
itself as `@Nullable`, in addition to the record's own `@Nullable`
components:
```java
@Get("/search")
public Map<String, Object> search(@RequestBean @Nullable PersonFilter filter) { ... }
```
