# Reproduction of a possible Micronaut Bug

This repo is intended to reproduce a Micronaut problem that occurs when updating Micronaut Core from 5.0.3 to 5.1.x (5.1.0, 5.1.1 and 5.1.2) seem to be affected.

The problem occurs when a Rest API is implemented that has an enpoint using the @RequestBean annotation to bundle optional query parameters in a Java record. Although every query parameter defined within the record is annotated as Nullable (using the jspecify annotation), the request is rejected as a bad request when the parameters are missing.

See [BUG_DESCRIPTION.md](./BUG_DESCRIPTION.md) for the full reproduction writeup (root cause, affected files, repro steps, and a known workaround), and [BUG_REPORT.md](./BUG_REPORT.md) for the bug report as submitted.

Filed upstream as [micronaut-projects/micronaut-core#12738](https://github.com/micronaut-projects/micronaut-core/issues/12738).