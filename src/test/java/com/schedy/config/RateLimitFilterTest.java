package com.schedy.config;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RateLimitFilter unit tests")
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(java.util.List.of("127.0.0.1", "::1"));
        chain = mock(FilterChain.class);
    }

    @Nested
    @DisplayName("B-11 — pin-clock rate limiting at 5/min/IP")
    class PinClockRateLimit {

        @Test
        @DisplayName("first request creates bucket in kioskPinClockBuckets")
        void pinClockPath_createsBucket() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST",
                    "/api/v1/pointage-codes/kiosk/pin-clock");
            req.setRemoteAddr("192.168.1.10");
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);

            @SuppressWarnings("unchecked")
            Map<String, Bucket> buckets = (Map<String, Bucket>)
                    ReflectionTestUtils.getField(filter, "kioskPinClockBuckets");
            assertThat(buckets).containsKey("192.168.1.10");
        }

        @Test
        @DisplayName("allows exactly 5 requests then blocks 6th with 429")
        void pinClockPath_allows5ThenBlocks() throws Exception {
            String ip = "10.0.0.5";
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST",
                        "/api/v1/pointage-codes/kiosk/pin-clock");
                req.setRemoteAddr(ip);
                MockHttpServletResponse res = new MockHttpServletResponse();
                filter.doFilterInternal(req, res, chain);
                assertThat(res.getStatus()).isNotEqualTo(429);
            }
            // 6th request — blocked
            MockHttpServletRequest sixthReq = new MockHttpServletRequest("POST",
                    "/api/v1/pointage-codes/kiosk/pin-clock");
            sixthReq.setRemoteAddr(ip);
            MockHttpServletResponse sixthRes = new MockHttpServletResponse();
            filter.doFilterInternal(sixthReq, sixthRes, chain);
            assertThat(sixthRes.getStatus()).isEqualTo(429);
            verify(chain, times(5)).doFilter(any(), any());
        }

        @Test
        @DisplayName("pin-clock and login buckets are independent")
        void pinClockPath_independentFromLogin() throws Exception {
            String ip = "10.0.0.9";
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST",
                        "/api/v1/pointage-codes/kiosk/pin-clock");
                req.setRemoteAddr(ip);
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }
            MockHttpServletRequest loginReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            loginReq.setRemoteAddr(ip);
            MockHttpServletResponse loginRes = new MockHttpServletResponse();
            filter.doFilterInternal(loginReq, loginRes, chain);
            assertThat(loginRes.getStatus()).isNotEqualTo(429);
        }

        @Test
        @DisplayName("different IPs have independent buckets")
        void pinClockPath_differentIps_independent() throws Exception {
            for (int i = 0; i < 5; i++) {
                MockHttpServletRequest req = new MockHttpServletRequest("POST",
                        "/api/v1/pointage-codes/kiosk/pin-clock");
                req.setRemoteAddr("10.1.1.1");
                filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            }
            MockHttpServletRequest reqB = new MockHttpServletRequest("POST",
                    "/api/v1/pointage-codes/kiosk/pin-clock");
            reqB.setRemoteAddr("10.1.1.2");
            MockHttpServletResponse resB = new MockHttpServletResponse();
            filter.doFilterInternal(reqB, resB, chain);
            assertThat(resB.getStatus()).isNotEqualTo(429);
        }
    }

    @Nested
    @DisplayName("General filter behaviour")
    class GeneralBehaviour {

        @Test
        @DisplayName("unrelated paths pass through without rate limiting")
        void unrelatedPath_passesThrough() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/employes");
            req.setRemoteAddr("1.2.3.4");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(req, res, chain);
            assertThat(res.getStatus()).isNotEqualTo(429);
            verify(chain).doFilter(req, res);
        }

        @Test
        @DisplayName("evictBuckets() clears pin-clock bucket map")
        void evictBuckets_clearsAllMaps() throws Exception {
            MockHttpServletRequest req = new MockHttpServletRequest("POST",
                    "/api/v1/pointage-codes/kiosk/pin-clock");
            req.setRemoteAddr("5.5.5.5");
            filter.doFilterInternal(req, new MockHttpServletResponse(), chain);
            filter.evictBuckets();

            @SuppressWarnings("unchecked")
            Map<String, Bucket> after = (Map<String, Bucket>)
                    ReflectionTestUtils.getField(filter, "kioskPinClockBuckets");
            assertThat(after).isEmpty();
        }
    }
}
