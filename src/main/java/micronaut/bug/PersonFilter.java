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
