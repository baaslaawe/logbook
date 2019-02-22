package org.zalando.logbook.servlet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.zalando.logbook.DefaultHttpLogFormatter;
import org.zalando.logbook.DefaultSink;
import org.zalando.logbook.HttpLogFormatter;
import org.zalando.logbook.HttpLogWriter;
import org.zalando.logbook.HttpMessage;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Strategy;

import javax.servlet.DispatcherType;
import java.io.IOException;

import static com.jayway.jsonassert.JsonAssert.with;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;
import static org.hobsoft.hamcrest.compose.ComposeMatchers.hasFeature;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.zalando.logbook.servlet.RequestBuilders.async;

/**
 * Verifies that {@link LogbookFilter} handles {@link DispatcherType#ASYNC} correctly.
 */
final class AsyncDispatchTest {

    private final HttpLogFormatter formatter = spy(new ForwardingHttpLogFormatter(new DefaultHttpLogFormatter()));
    private final HttpLogWriter writer = mock(HttpLogWriter.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ExampleController())
            .addFilter(new LogbookFilter(Logbook.builder()
                    .strategy(new Strategy() {
                        @Override
                        public HttpRequest process(final HttpRequest request) throws IOException {
                            request.getBody();
                            return request.withBody().withBody();
                        }

                        @Override
                        public HttpResponse process(final HttpRequest request, final HttpResponse response) throws IOException {
                            response.getBody();
                            return response.withBody().withBody();
                        }
                    })
                    .sink(new DefaultSink(formatter, writer))
                    .build()))
            .build();

    @BeforeEach
    void setUp() {
        reset(formatter, writer);

        when(writer.isActive()).thenReturn(true);
    }

    @Test
    void shouldFormatAsyncRequest() throws Exception {
        mvc.perform(async(mvc.perform(get("/api/async"))
                .andExpect(request().asyncStarted())
                .andReturn()));

        final HttpRequest request = interceptRequest();

        assertThat(request, hasFeature("remote address", HttpRequest::getRemote, is("127.0.0.1")));
        assertThat(request, hasFeature("method", HttpRequest::getMethod, is("GET")));
        assertThat(request, hasFeature("url", HttpRequest::getRequestUri,
                hasToString("http://localhost/api/async")));
        assertThat(request, hasFeature("headers", HttpRequest::getHeaders, is(emptyMap())));
        assertThat(request, hasFeature("body", this::getBodyAsString, is(emptyOrNullString())));
    }

    @Test
    void shouldFormatAsyncResponse() throws Exception {
        mvc.perform(async(mvc.perform(get("/api/async"))
                .andExpect(request().asyncStarted())
                .andReturn())).andReturn();

        final HttpResponse response = interceptResponse();

        assertThat(response, hasFeature("status", HttpResponse::getStatus, is(200)));
        assertThat(response, hasFeature("headers", HttpMessage::getHeaders,
                hasEntry("Content-Type", singletonList("application/json;charset=UTF-8"))));
        assertThat(response, hasFeature("content type",
                HttpResponse::getContentType, is("application/json;charset=UTF-8")));

        with(response.getBodyAsString())
                .assertThat("$.*", hasSize(1))
                .assertThat("$.value", is("Hello, world!"));
    }

    private String getBodyAsString(final HttpMessage message) {
        try {
            return message.getBodyAsString();
        } catch (final IOException e) {
            throw new AssertionError(e);
        }
    }

    private HttpRequest interceptRequest() throws IOException {
        final ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(formatter).format(any(), captor.capture());
        return captor.getValue();
    }

    private HttpResponse interceptResponse() throws IOException {
        final ArgumentCaptor<HttpResponse> captor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(formatter).format(any(), captor.capture());
        return captor.getValue();
    }

}
