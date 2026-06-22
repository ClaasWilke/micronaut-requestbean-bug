# micronaut-requestbean-bug

Minimal reproduction of a Micronaut Core regression: a `@RequestBean`-bound
Java `record` whose components are all annotated `@Nullable` (jspecify) is
rejected as `400 Bad Request` when no query parameters are present at all,
starting with Micronaut Core 5.1.0. The same code works correctly on 5.0.3.

Filed upstream as
[micronaut-projects/micronaut-core#12738](https://github.com/micronaut-projects/micronaut-core/issues/12738).

See [BUG_DESCRIPTION.md](./BUG_DESCRIPTION.md) for the full writeup (root
cause, affected files, repro steps, known workaround) and
[BUG_REPORT.md](./BUG_REPORT.md) for the bug report as submitted.

## Quick start

Requires JDK 25 (Micronaut 5.x's own annotation processors are compiled for
class file version 69 and won't load under earlier JDKs):

```bash
export JAVA_HOME=/path/to/jdk-25

./mvnw clean test
```

`pom.xml` pins the exact Micronaut Core version under test via a single
`micronaut.version` property. It's currently set to `5.1.2`, which
reproduces the bug (`PersonControllerTest.noQueryParamsReturnsOkWithAllFieldsNull`
fails with `400 Bad Request`). Set it to `5.0.3` and rerun to see all tests
pass.
