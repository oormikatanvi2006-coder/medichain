package org.springframework.web.client;
import org.springframework.core.ParameterizedTypeReference;
public interface RestClient {
    RequestHeadersUriSpec<?> get();
    RequestBodyUriSpec post();
    interface Builder { Builder baseUrl(String u); RestClient build(); }
    interface UriSpec<S extends RequestHeadersSpec<?>> { S uri(String uri, Object... vars); }
    interface RequestHeadersSpec<S extends RequestHeadersSpec<S>> { ResponseSpec retrieve(); }
    interface RequestBodySpec extends RequestHeadersSpec<RequestBodySpec> { RequestBodySpec body(Object body); }
    interface RequestHeadersUriSpec<S extends RequestHeadersSpec<S>> extends UriSpec<S>, RequestHeadersSpec<S> {}
    interface RequestBodyUriSpec extends RequestBodySpec, RequestHeadersUriSpec<RequestBodySpec> {}
    interface ResponseSpec { <T> T body(Class<T> c); <T> T body(ParameterizedTypeReference<T> t); }
}
