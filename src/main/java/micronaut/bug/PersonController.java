package micronaut.bug;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestBean;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

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

    // Workaround attempt: mark the @RequestBean parameter itself nullable, not just its
    // record components, to see whether RequestBeanAnnotationBinder then tolerates an
    // all-missing query string (see micronaut-core PR #12632).
    @Get("/search-nullable-param")
    public Map<String, Object> searchNullableParam(@RequestBean @Nullable PersonFilter filter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", filter == null ? null : filter.name());
        result.put("age", filter == null ? null : filter.age());
        result.put("city", filter == null ? null : filter.city());
        return result;
    }
}
