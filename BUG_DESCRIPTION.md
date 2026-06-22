# Bug Description: `@RequestBean` record with all-nullable components rejected as 400 on Micronaut 5.1.x

**Filed upstream as:**
[micronaut-projects/micronaut-core#12738](https://github.com/micronaut-projects/micronaut-core/issues/12738)

## Summary

A REST endpoint that binds optional query parameters via `@RequestBean` into a
Java `record` — where every record component is annotated `@Nullable`
(jspecify, `org.jspecify.annotations.Nullable`) — works correctly on
Micronaut Core 5.0.3. Starting with 5.1.0 (also reproduced on 5.1.1 and
5.1.2), the **same request with no query parameters at all** is rejected
with `HTTP 400 Bad Request`, even though no individual field is required.

Providing at least one query parameter (partially or fully) avoids the
400 — only the all-parameters-missing case is affected.

## Reproduction project

This repository (`micronaut-bug`), Maven-based, Java 17 source/target,
generated via [Micronaut Launch](https://launch.micronaut.io).

Key files:
- `pom.xml` — pins the exact Micronaut Core version under test via a single
  `micronaut.version` property (see "Version pinning" below).
- `src/main/java/micronaut/bug/PersonFilter.java` — the record under test.
- `src/main/java/micronaut/bug/PersonController.java` — the `@RequestBean`
  endpoint (`GET /people/search`), plus a second endpoint
  `GET /people/search-nullable-param` used to test a workaround (see below).
- `src/test/java/micronaut/bug/PersonControllerTest.java` — the integration
  test that demonstrates the regression.

### `PersonFilter.java`
```java
package micronaut.bug;

import io.micronaut.core.annotation.Introspected;
import org.jspecify.annotations.Nullable;

@Introspected
public record PersonFilter(
    @Nullable String name,
    @Nullable Integer age,
    @Nullable String city
) {
}
```

### `PersonController.java`
```java
@Controller("/people")
public class PersonController {

    @Get("/search")
    public Map<String, Object> search(@RequestBean PersonFilter filter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", filter.name());
        result.put("age", filter.age());
        result.put("city", filter.city());
        return result;
    }
    // see "Attempted workaround" below for the second endpoint
}
```

The controller returns a `Map` instead of the record directly. This is a
deliberate workaround for an unrelated, pre-existing code-generation bug in
`micronaut-serde-processor` (generates an invalid `case Type.CONSTANT ->`
enum switch label for this record's generated JSON deserializer,
independent of the `@RequestBean` issue and reproducible on both 5.0.3 and
5.1.2). It does not affect the binding behavior under test.

### Version pinning

`io.micronaut.platform:micronaut-parent` (the usual parent BOM) only has
published releases up to `5.0.2` — `5.0.3`/`5.1.x` platform BOM releases
don't exist yet. However `io.micronaut:micronaut-core-bom` *is* published
per Micronaut Core version (5.0.3, 5.1.0, 5.1.1, 5.1.2 all exist on Maven
Central). `pom.xml` therefore:
- Keeps `io.micronaut.platform:micronaut-parent:5.0.2` as parent (manages
  unrelated artifacts like `micronaut-serde-jackson`, `micronaut-test-junit5`).
- Adds a `<dependencyManagement>` import of `io.micronaut:micronaut-core-bom`
  pinned to `${micronaut.version}`, which overrides just the
  core/http/inject/runtime artifact versions.
- Overrides the `micronaut.core.version` property (used by the
  `micronaut-http-validation`/`micronaut-inject` annotation processors in
  the `maven-compiler-plugin` config) to track the same `${micronaut.version}`.

This means flipping a single property (`<micronaut.version>` in `pom.xml`)
switches the entire build — both compile-time annotation processing and
runtime — between Micronaut Core versions.

## Steps to reproduce

```bash
# Requires JDK 25 — Micronaut 5.x's own annotation processors are compiled
# for class file version 69 and won't load under JDK 24 or earlier.
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home

# 1. Baseline: set <micronaut.version> to 5.0.3 in pom.xml, then:
./mvnw clean test
# => BUILD SUCCESS, all tests green.

# 2. Reproduce: set <micronaut.version> to 5.1.2 in pom.xml, then:
./mvnw clean test
# => noQueryParamsReturnsOkWithAllFieldsNull fails:
#    io.micronaut.http.client.exceptions.HttpClientResponseException: Bad Request
#    All other tests (all-params, partial-params, nullable-param workaround) still pass.
```

## Root cause (identified)

Checked https://github.com/micronaut-projects/micronaut-core/issues for
related reports before filing a new one. Found the likely root cause:

- **Issue [#12608](https://github.com/micronaut-projects/micronaut-core/issues/12608)**
  "Introspected types are always instantiated when binding from request"
  (filed against 4.10.0): `@Introspected` beans bound via `@QueryValue` or
  `@RequestBean` were *always* instantiated, even when no request data was
  present — so `@NotNull`/required constraints on such beans were silently
  never enforced.
- Fixed by **PR [#12632](https://github.com/micronaut-projects/micronaut-core/pull/12632)**
  "Instantiate introspected beans only when request values are present",
  merged into the `5.1.x` branch on 2026-06-06. Per the PR description:
  > Avoid instantiating `@Introspected` beans for `@QueryValue` and
  > `@RequestBean` when no request values are present. Return null instead
  > of an empty object. Existing behavior for required arguments (e.g. HTTP
  > 400) is unchanged; this applies when the argument is treated as
  > optional/nullable.

This fix changed `RequestBeanAnnotationBinder` so that when **zero** of a
`@RequestBean` bean's bindable properties are present in the request, the
binder now resolves the bean argument itself to `null` rather than an
all-null instance. Whether a `null` bean is then accepted depends on
whether the **method parameter** (the one annotated `@RequestBean`) is
itself considered nullable/optional — not on whether the bean's individual
*components* are nullable.

In our repro, `@RequestBean PersonFilter filter` has no nullability
annotation on the parameter itself (only on `PersonFilter`'s record
components), so when the binder resolves it to `null`, downstream argument
validation treats it as a missing *required* argument and returns 400 —
even though, semantically, every field of `PersonFilter` is optional and the
intent of the all-`@Nullable` record was that the whole filter should be
optional too.

### Attempted workaround

Annotating the `@RequestBean` parameter itself as `@Nullable` (in addition
to the record's own `@Nullable` components) avoids the 400:

```java
@Get("/search-nullable-param")
public Map<String, Object> searchNullableParam(@RequestBean @Nullable PersonFilter filter) {
    ...
}
```

`noQueryParamsWithNullableParamReturnsOkWithAllFieldsNull` in
`PersonControllerTest` confirms this passes on 5.1.2. This is a viable
workaround, but it means: code that relied on "every record component is
`@Nullable`" being sufficient (and which worked, even if only by accident,
under the pre-#12632 behavior) silently breaks on 5.1.x unless the
`@RequestBean` parameter itself is also annotated nullable.

#### Drawback: the workaround changes what `filter` can be, not just whether the request is rejected

Before the regression (5.0.3, and on 5.1.x without the workaround when at
least one query parameter is present), `filter` is **always** a non-null
`PersonFilter` instance — only its individual fields can be `null`. With the
`@Nullable`-on-parameter workaround, `filter` **itself** can now be `null`
when no query parameters are present at all. That's a real behavioral
change at the call site, not just an annotation tweak: any code that called
`filter.name()`/`filter.age()`/`filter.city()` without a null-check on
`filter` will now NPE in exactly the case the workaround "fixes". See
`searchNullableParam` in `PersonController.java`, which has to guard for it
explicitly:

```java
result.put("name", filter == null ? null : filter.name());
```

So adopting the workaround in a real codebase isn't a drop-in annotation
change — every caller of the bound bean needs an added null-check (or a
default instance substituted right after binding) to restore the pre-5.1.x
guarantee that the bean itself is never null.

## Follow-up finding: this looks intentional, not an oversight

After filing, a closer read of the full `RequestBeanAnnotationBinder.java`
on the `5.1.x` branch and the complete PR #12632 diff turned up a test the
PR itself added — for exactly our pattern (a bean with only nullable
components):

```groovy
@Introspected
static class EmptyBean {
    @Nullable String value
}

@Get("/request-bean-nullable")
String requestBeanNull(@RequestBean @Nullable EmptyBean bean) {
    return bean == null ? "null" : "not-null"
}

void "test request bean is null when no input is provided"() {
    expect:
    client.requestBeanNull(null) == "null"
}
```

The only difference from our failing case: that test's parameter is
`@RequestBean @Nullable EmptyBean` — the *parameter* is nullable, not just
the bean's own field. So the designed contract appears to be: nullability
of a `@RequestBean`/`@QueryValue` bean's own components never makes the
bean itself optional; only nullability on the bound *parameter* does.
That's a deliberate, regression-tested decision in #12632, not an
implementation bug.

This changes the resolution of the "open question" below: option (1)
(infer "bean is optional" from all-nullable components) would directly
conflict with and break this existing, intentional test. A binder behavior
change is therefore not the right ask. The real gap looks to be that this
contract isn't documented anywhere obvious — it's easy to assume
"all components nullable" is sufficient (it was, by accident, before
#12608/#12632), with no compiler warning or doc note steering users toward
adding `@Nullable` on the parameter itself.

This was shared as a
[follow-up comment](https://github.com/micronaut-projects/micronaut-core/issues/12738#issuecomment-4766421857)
on the filed issue, leaving the resolution (documentation clarification vs.
a compile-time hint vs. something else) to maintainer triage rather than
proposing a binder code change.

## Open question, as filed (superseded by the follow-up finding above)

Whether this should be treated as:
1. A genuine regression — Micronaut should infer "bean is optional" when all
   of its components are individually nullable, without requiring a
   redundant `@Nullable` on the `@RequestBean` parameter; or
2. A documentation gap — the new (correct) behavior from #12632 is
   intentional, and projects upgrading to 5.1.x need to add `@Nullable` (or
   `@Nullable` equivalent) on `@RequestBean` parameters explicitly.

`BUG_REPORT.md` (and the filed issue,
[#12738](https://github.com/micronaut-projects/micronaut-core/issues/12738))
originally took position (1), since from a user's perspective declaring
every component of a request-bound record as `@Nullable` is the natural,
idiomatic way to express "this whole filter is optional" — and that
contract held on 5.0.3. The follow-up finding above shows the maintainers'
own tests favor (2); awaiting their triage for the final call.
