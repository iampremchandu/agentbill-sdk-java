package com.agentbill;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * AgentBill SDK for Java
 * OpenTelemetry-based SDK for tracking AI agent usage and billing
 */
public class AgentBill {
    private final Config config;
    private final Tracer tracer;
    private static final Gson gson = new Gson();

    public static class Config {
        private String apiKey;
        private String baseUrl = "https://uenhjwdtnxtchlmqarjo.supabase.co";
        private String customerId;
        private boolean debug = false;

        public Config(String apiKey) {
            this.apiKey = apiKey;
        }

        public Config setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Config setCustomerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Config setDebug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public String getApiKey() { return apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public String getCustomerId() { return customerId; }
        public boolean isDebug() { return debug; }
    }

    private AgentBill(Config config) {
        this.config = config;
        this.tracer = new Tracer(config);
    }

    public static AgentBill init(Config config) {
        return new AgentBill(config);
    }

    public OpenAIWrapper wrapOpenAI() {
        return new OpenAIWrapper(this);
    }

    public void trackSignal(String eventName, double revenue) {
        trackSignal(eventName, revenue, new HashMap<>());
    }
    
    public void trackSignal(String eventName, double revenue, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_name", eventName);
            payload.put("revenue", revenue);
            payload.put("customer_id", config.getCustomerId());
            payload.put("timestamp", System.currentTimeMillis() / 1000);
            payload.put("data", data);
            
            String json = gson.toJson(payload);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/functions/v1/record-signals"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (config.isDebug()) {
                System.out.printf("[AgentBill] Signal tracked: %s, revenue: $%.2f%n", eventName, revenue);
            }
        } catch (Exception e) {
            if (config.isDebug()) {
                System.err.println("[AgentBill] Failed to track signal: " + e.getMessage());
            }
        }
    }

    public void flush() throws IOException, InterruptedException {
        tracer.flush();
    }

    public static class OpenAIWrapper {
        private final AgentBill client;

        OpenAIWrapper(AgentBill client) {
            this.client = client;
        }

        public Map<String, Object> chatCompletion(String model, List<Map<String, String>> messages) throws Exception {
            long startTime = System.currentTimeMillis();

            Map<String, Object> attributes = new HashMap<>();
            attributes.put("model", model);
            attributes.put("provider", "openai");

            Span span = client.tracer.startSpan("openai.chat.completion", attributes);

            try {
                // Build request payload
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", messages);

                String jsonPayload = new com.google.gson.Gson().toJson(requestBody);

                // Make actual OpenAI API call
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) 
                    new java.net.URL("https://api.openai.com/v1/chat/completions").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + System.getenv("OPENAI_API_KEY"));
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("OpenAI API returned status: " + responseCode);
                }

                // Parse response
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = new com.google.gson.Gson().fromJson(
                    response.toString(), Map.class);

                // Extract token usage
                @SuppressWarnings("unchecked")
                Map<String, Object> usage = (Map<String, Object>) responseMap.get("usage");
                int promptTokens = ((Number) usage.get("prompt_tokens")).intValue();
                int completionTokens = ((Number) usage.get("completion_tokens")).intValue();
                int totalTokens = ((Number) usage.get("total_tokens")).intValue();

                long latency = System.currentTimeMillis() - startTime;
                span.setAttribute("response.prompt_tokens", promptTokens);
                span.setAttribute("response.completion_tokens", completionTokens);
                span.setAttribute("response.total_tokens", totalTokens);
                span.setAttribute("latency_ms", latency);
                span.setStatus(0, "");

                return responseMap;
            } catch (Exception e) {
                span.setStatus(1, e.getMessage());
                throw e;
            } finally {
                span.end();
            }
        }
    }

    static class Span {
        private final String name;
        private final String traceId;
        private final String spanId;
        private final Map<String, Object> attributes;
        private final long startTime;
        private long endTime;
        private Map<String, Object> status;

        Span(String name, String traceId, String spanId, Map<String, Object> attributes) {
            this.name = name;
            this.traceId = traceId;
            this.spanId = spanId;
            this.attributes = new HashMap<>(attributes);
            this.startTime = System.nanoTime();
            this.status = new HashMap<>();
            this.status.put("code", 0);
        }

        void setAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        void setStatus(int code, String message) {
            status = new HashMap<>();
            status.put("code", code);
            status.put("message", message);
        }

        void end() {
            endTime = System.nanoTime();
        }

        String getName() { return name; }
        String getTraceId() { return traceId; }
        String getSpanId() { return spanId; }
        Map<String, Object> getAttributes() { return attributes; }
        long getStartTime() { return startTime; }
        long getEndTime() { return endTime == 0 ? System.nanoTime() : endTime; }
        Map<String, Object> getStatus() { return status; }
    }

    static class Tracer {
        private final Config config;
        private final List<Span> spans = new ArrayList<>();
        private final HttpClient httpClient;

        Tracer(Config config) {
            this.config = config;
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }

        Span startSpan(String name, Map<String, Object> attributes) {
            String traceId = UUID.randomUUID().toString().replace("-", "");
            String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

            attributes.put("service.name", "agentbill-java-sdk");
            if (config.getCustomerId() != null) {
                attributes.put("customer.id", config.getCustomerId());
            }

            Span span = new Span(name, traceId, spanId, attributes);
            spans.add(span);
            return span;
        }

        void flush() throws IOException, InterruptedException {
            if (spans.isEmpty()) return;

            Map<String, Object> payload = buildOTLPPayload();
            String json = gson.toJson(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/functions/v1/otel-collector"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (config.isDebug()) {
                System.out.println("AgentBill flush: " + response.statusCode());
            }

            if (response.statusCode() == 200) {
                spans.clear();
            }
        }

        private Map<String, Object> buildOTLPPayload() {
            List<Map<String, Object>> spansList = new ArrayList<>();
            for (Span span : spans) {
                spansList.add(spanToOTLP(span));
            }

            Map<String, Object> scope = new HashMap<>();
            scope.put("name", "agentbill");
            scope.put("version", "1.0.0");
            scope.put("spans", spansList);

            Map<String, Object> resource = new HashMap<>();
            List<Map<String, Object>> resourceAttrs = new ArrayList<>();
            resourceAttrs.add(createAttribute("service.name", "agentbill-java-sdk"));
            resourceAttrs.add(createAttribute("service.version", "1.0.0"));
            resource.put("attributes", resourceAttrs);

            Map<String, Object> resourceSpan = new HashMap<>();
            resourceSpan.put("resource", resource);
            resourceSpan.put("scopeSpans", Collections.singletonList(Collections.singletonMap("scope", scope)));

            return Collections.singletonMap("resourceSpans", Collections.singletonList(resourceSpan));
        }

        private Map<String, Object> spanToOTLP(Span span) {
            List<Map<String, Object>> attributes = new ArrayList<>();
            for (Map.Entry<String, Object> entry : span.getAttributes().entrySet()) {
                attributes.add(createAttribute(entry.getKey(), entry.getValue()));
            }

            Map<String, Object> otlpSpan = new HashMap<>();
            otlpSpan.put("traceId", span.getTraceId());
            otlpSpan.put("spanId", span.getSpanId());
            otlpSpan.put("name", span.getName());
            otlpSpan.put("kind", 1);
            otlpSpan.put("startTimeUnixNano", String.valueOf(span.getStartTime()));
            otlpSpan.put("endTimeUnixNano", String.valueOf(span.getEndTime()));
            otlpSpan.put("attributes", attributes);
            otlpSpan.put("status", span.getStatus());

            return otlpSpan;
        }

        private Map<String, Object> createAttribute(String key, Object value) {
            Map<String, Object> attr = new HashMap<>();
            attr.put("key", key);
            attr.put("value", valueToOTLP(value));
            return attr;
        }

        private Map<String, Object> valueToOTLP(Object value) {
            if (value instanceof String) {
                return Collections.singletonMap("stringValue", value);
            } else if (value instanceof Number) {
                return Collections.singletonMap("intValue", ((Number) value).intValue());
            } else if (value instanceof Boolean) {
                return Collections.singletonMap("boolValue", value);
            } else {
                return Collections.singletonMap("stringValue", value.toString());
            }
        }
    }
}
