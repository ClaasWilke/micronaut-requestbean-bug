package micronaut.bug;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@MicronautTest
class PersonControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void noQueryParamsReturnsOkWithAllFieldsNull() {
        Map<?, ?> result = client.toBlocking()
            .retrieve(HttpRequest.GET("/people/search"), Map.class);

        assertNull(result.get("name"));
        assertNull(result.get("age"));
        assertNull(result.get("city"));
    }

    @Test
    void allQueryParamsProvidedReturnsOkWithValues() {
        Map<?, ?> result = client.toBlocking()
            .retrieve(HttpRequest.GET("/people/search?name=Jane&age=30&city=Berlin"), Map.class);

        assertEquals("Jane", result.get("name"));
        assertEquals(30, result.get("age"));
        assertEquals("Berlin", result.get("city"));
    }

    @Test
    void partialQueryParamsReturnsOkWithRemainingFieldsNull() {
        Map<?, ?> result = client.toBlocking()
            .retrieve(HttpRequest.GET("/people/search?name=Jane"), Map.class);

        assertEquals("Jane", result.get("name"));
        assertNull(result.get("age"));
        assertNull(result.get("city"));
    }

    @Test
    void noQueryParamsWithNullableParamReturnsOkWithAllFieldsNull() {
        Map<?, ?> result = client.toBlocking()
            .retrieve(HttpRequest.GET("/people/search-nullable-param"), Map.class);

        assertNull(result.get("name"));
        assertNull(result.get("age"));
        assertNull(result.get("city"));
    }
}
