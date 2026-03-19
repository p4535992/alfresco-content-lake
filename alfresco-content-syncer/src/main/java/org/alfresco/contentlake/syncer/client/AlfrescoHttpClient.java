package org.alfresco.contentlake.syncer.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.alfresco.contentlake.syncer.api.AlfrescoConnectionStatusResponse;
import org.alfresco.contentlake.syncer.api.AlfrescoConnectionRequest;
import org.alfresco.contentlake.syncer.api.AlfrescoSiteInfo;
import org.alfresco.contentlake.syncer.model.RemoteNode;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AlfrescoHttpClient {

    private static final int PAGE_SIZE = 100;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "syncer.alfresco.max-attempts", defaultValue = "3")
    int maxAttempts;

    @ConfigProperty(name = "syncer.alfresco.retry-delay-ms", defaultValue = "500")
    long retryDelayMs;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public RemoteNode getNode(AlfrescoConnectionRequest request, String nodeId) {
        return withRetry(() -> {
            HttpRequest httpRequest = requestBuilder(request, nodeUri(request, "/nodes/" + encode(nodeId)))
                    .GET()
                    .build();
            return parseEntry(send(httpRequest, 200));
        });
    }

    public AlfrescoConnectionStatusResponse verifyConnection(AlfrescoConnectionRequest request) {
        return withRetry(() -> {
            if (request.ticket() == null || request.ticket().isBlank()) {
                createTicket(request.username(), request.password(), request.authenticationApiBaseUrl());
            }

            HttpRequest httpRequest = requestBuilder(request, publicUri(request, "/people/-me"))
                    .GET()
                    .build();
            String body = send(httpRequest, 200);
            try {
                JsonNode entry = objectMapper.readTree(body).path("entry");
                String userId = textOrNull(entry.get("id"));
                String firstName = textOrNull(entry.get("firstName"));
                String lastName = textOrNull(entry.get("lastName"));
                String displayName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
                return new AlfrescoConnectionStatusResponse(userId, displayName.isBlank() ? userId : displayName);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse Alfresco response", e);
            }
        });
    }

    public List<AlfrescoSiteInfo> listSites(AlfrescoConnectionRequest request) {
        return withRetry(() -> {
            List<AlfrescoSiteInfo> sites = new ArrayList<>();
            int skipCount = 0;
            while (true) {
                URI uri = publicUri(request, "/sites?skipCount=" + skipCount + "&maxItems=" + PAGE_SIZE);
                HttpRequest httpRequest = requestBuilder(request, uri).GET().build();
                List<AlfrescoSiteInfo> page = parseSiteEntries(send(httpRequest, 200));
                sites.addAll(page);
                if (page.size() < PAGE_SIZE) {
                    return sites;
                }
                skipCount += PAGE_SIZE;
            }
        });
    }

    public AlfrescoSiteInfo getSite(AlfrescoConnectionRequest request, String siteId) {
        return withRetry(() -> {
            HttpRequest httpRequest = requestBuilder(request, publicUri(request, "/sites/" + encode(siteId)))
                    .GET()
                    .build();
            try {
                JsonNode root = objectMapper.readTree(send(httpRequest, 200));
                return toSiteInfo(root.path("entry"));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse Alfresco response", e);
            }
        });
    }

    public String getDocumentLibraryNodeId(AlfrescoConnectionRequest request, String siteId) {
        return withRetry(() -> {
            HttpRequest httpRequest = requestBuilder(request,
                    publicUri(request, "/sites/" + encode(siteId) + "/containers/documentLibrary"))
                    .GET()
                    .build();
            try {
                JsonNode root = objectMapper.readTree(send(httpRequest, 200));
                String folderId = textOrNull(root.path("entry").get("folderId"));
                if (folderId == null || folderId.isBlank()) {
                    throw new IllegalStateException("documentLibrary container not found for site " + siteId);
                }
                return folderId;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse Alfresco response", e);
            }
        });
    }

    public List<RemoteNode> listChildren(AlfrescoConnectionRequest request, String nodeId) {
        return withRetry(() -> {
            List<RemoteNode> children = new ArrayList<>();
            int skipCount = 0;
            while (true) {
                URI uri = nodeUri(request,
                        "/nodes/" + encode(nodeId) + "/children?skipCount=" + skipCount + "&maxItems=" + PAGE_SIZE + "&include=properties");
                HttpRequest httpRequest = requestBuilder(request, uri).GET().build();
                List<RemoteNode> page = parseEntries(send(httpRequest, 200));
                children.addAll(page);
                if (page.size() < PAGE_SIZE) {
                    return children;
                }
                skipCount += PAGE_SIZE;
            }
        });
    }

    public RemoteNode createFolder(AlfrescoConnectionRequest request, String parentNodeId, String folderName) {
        return withRetry(() -> {
            String payload = "{" +
                    "\"name\":\"" + escapeJson(folderName) + "\"," +
                    "\"nodeType\":\"cm:folder\"" +
                    "}";

            HttpRequest httpRequest = requestBuilder(request, nodeUri(request, "/nodes/" + encode(parentNodeId) + "/children"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            return parseEntry(send(httpRequest, 201));
        });
    }

    public RemoteNode uploadFile(AlfrescoConnectionRequest request, String parentNodeId, Path file) {
        return withRetry(() -> {
            try {
                String boundary = "----alfresco-syncer-" + UUID.randomUUID();
                String fileName = file.getFileName().toString();
                String mimeType = probeMimeType(file);

                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.concat(
                        stringPart(boundary, "filedata", fileName, mimeType),
                        HttpRequest.BodyPublishers.ofFile(file),
                        stringPublisher("\r\n"),
                        textPart(boundary, "name", fileName),
                        textPart(boundary, "nodeType", "cm:content"),
                        textPart(boundary, "autoRename", "false"),
                        stringPublisher("--" + boundary + "--\r\n")
                );

                HttpRequest httpRequest = requestBuilder(request, nodeUri(request, "/nodes/" + encode(parentNodeId) + "/children"))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(bodyPublisher)
                        .build();

                return parseEntry(send(httpRequest, 201));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read file for upload: " + file, e);
            }
        });
    }

    public RemoteNode updateFileContent(AlfrescoConnectionRequest request, String nodeId, Path file) {
        return withRetry(() -> {
            try {
                HttpRequest httpRequest = requestBuilder(request, nodeUri(request, "/nodes/" + encode(nodeId) + "/content?majorVersion=false"))
                        .header("Content-Type", probeMimeType(file))
                        .header("Content-Disposition", contentDisposition(file.getFileName().toString()))
                        .PUT(HttpRequest.BodyPublishers.ofFile(file))
                        .build();
                return parseEntry(send(httpRequest, 200));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read file for content update: " + file, e);
            }
        });
    }

    public void deleteNode(AlfrescoConnectionRequest request, String nodeId) {
        withRetry(() -> {
            HttpRequest httpRequest = requestBuilder(request, nodeUri(request, "/nodes/" + encode(nodeId)))
                    .DELETE()
                    .build();
            send(httpRequest, 204);
            return null;
        });
    }

    private HttpRequest.Builder requestBuilder(AlfrescoConnectionRequest request, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json");

        if (request.ticket() != null && !request.ticket().isBlank()) {
            return builder;
        }

        String credentials = request.username() + ":" + request.password();
        String basic = java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return builder.header("Authorization", "Basic " + basic);
    }

    private URI nodeUri(AlfrescoConnectionRequest request, String pathAndQuery) {
        return URI.create(request.publicApiBaseUrl() + ticketSuffix(request, pathAndQuery));
    }

    private URI publicUri(AlfrescoConnectionRequest request, String pathAndQuery) {
        return URI.create(request.publicApiBaseUrl() + ticketSuffix(request, pathAndQuery));
    }

    private String ticketSuffix(AlfrescoConnectionRequest request, String pathAndQuery) {
        String separator = pathAndQuery.contains("?") ? "&" : "?";
        return request.ticket() != null && !request.ticket().isBlank()
                ? pathAndQuery + separator + "alf_ticket=" + encode(request.ticket())
                : pathAndQuery;
    }

    private String send(HttpRequest request, int expectedStatus) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != expectedStatus) {
                throw new IllegalStateException("Alfresco request failed: status=" + response.statusCode() + " body=" + response.body());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to call Alfresco", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call Alfresco", e);
        }
    }

    private RemoteNode parseEntry(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return toRemoteNode(root.path("entry"));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Alfresco response", e);
        }
    }

    private List<RemoteNode> parseEntries(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.path("list").path("entries");
            List<RemoteNode> nodes = new ArrayList<>();
            if (!entries.isArray()) {
                return nodes;
            }
            for (JsonNode entry : entries) {
                nodes.add(toRemoteNode(entry.path("entry")));
            }
            return nodes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Alfresco response", e);
        }
    }

    private List<AlfrescoSiteInfo> parseSiteEntries(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode entries = root.path("list").path("entries");
            List<AlfrescoSiteInfo> sites = new ArrayList<>();
            if (!entries.isArray()) {
                return sites;
            }
            for (JsonNode entry : entries) {
                sites.add(toSiteInfo(entry.path("entry")));
            }
            return sites;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse Alfresco response", e);
        }
    }

    private AlfrescoSiteInfo toSiteInfo(JsonNode entryNode) {
        return new AlfrescoSiteInfo(
                textOrNull(entryNode.get("id")),
                textOrNull(entryNode.get("title")),
                textOrNull(entryNode.get("description"))
        );
    }

    private RemoteNode toRemoteNode(JsonNode entryNode) {
        JsonNode contentNode = entryNode.path("content");
        String modifiedAt = textOrNull(entryNode.get("modifiedAt"));
        return new RemoteNode(
                textOrNull(entryNode.get("id")),
                textOrNull(entryNode.get("name")),
                entryNode.path("isFolder").asBoolean(false),
                entryNode.path("isFile").asBoolean(false),
                contentNode.path("sizeInBytes").asLong(-1L),
                modifiedAt != null ? OffsetDateTime.parse(modifiedAt).toInstant() : null
        );
    }

    private HttpRequest.BodyPublisher stringPart(String boundary, String name, String fileName, String mimeType) {
        return stringPublisher("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + escapeQuotes(fileName) + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n");
    }

    private HttpRequest.BodyPublisher textPart(String boundary, String name, String value) {
        return stringPublisher("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private HttpRequest.BodyPublisher stringPublisher(String value) {
        return HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8);
    }

    private String probeMimeType(Path file) {
        try {
            String mimeType = Files.probeContentType(file);
            return mimeType != null && !mimeType.isBlank() ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private String contentDisposition(String fileName) {
        return "attachment; filename=\"" + escapeQuotes(fileName) + "\"";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeQuotes(String value) {
        return value.replace("\"", "");
    }

    private void createTicket(String username, String password, String authenticationApiBaseUrl) {
        String payload = "{"
                + "\"userId\":\"" + escapeJson(username) + "\","
                + "\"password\":\"" + escapeJson(password) + "\""
                + "}";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(authenticationApiBaseUrl + "/tickets"))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        send(httpRequest, 201);
    }

    private <T> T withRetry(IoSupplier<T> supplier) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException e) {
                last = e;
                if (attempt >= maxAttempts || !isRetryable(e)) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }
        throw last;
    }

    private boolean isRetryable(RuntimeException e) {
        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
            return true;
        }
        String message = e.getMessage();
        return message != null && message.contains("status=5");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(Math.max(0L, retryDelayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get();
    }
}
