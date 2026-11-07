package eu.wiegandt.librehousehold.realtime;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the event stream contract {@code api/asyncapi.yml}, the single source of truth for event
 * names and payloads (ADR-019). No code is generated from it, so tests compare the handwritten
 * types and the bytes on the wire against it.
 */
public final class AsyncApiContract {

    // Relative to the backend module, which is the working directory of Surefire and Failsafe
    private static final Path ASYNCAPI_SPEC = Path.of("../api/asyncapi.yml");
    private static final String LOCAL_SCHEMA_REF_PREFIX = "#/components/schemas/";

    private final Map<String, Object> components;

    private AsyncApiContract(Map<String, Object> components) {
        this.components = components;
    }

    public static AsyncApiContract load() {
        try (var spec = Files.newInputStream(ASYNCAPI_SPEC)) {
            Map<String, Object> root = new Yaml().load(spec);
            return new AsyncApiContract(section(root, "components"));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * The {@code name} of every message, which is the SSE {@code event:} field.
     */
    public List<String> messageNames() {
        return section(components, "messages").values().stream().map(AsyncApiContract::messageName).toList();
    }

    @SuppressWarnings("unchecked")
    public List<String> schemaEnum(String schemaName) {
        return (List<String>) section(components, "schemas", schemaName).get("enum");
    }

    public Set<String> schemaProperties(String schemaName) {
        return section(components, "schemas", schemaName, "properties").keySet();
    }

    /**
     * The payload schema of the message with the given {@code name}, resolved from its {@code $ref}.
     */
    public PayloadSchema payloadOf(String messageName) {
        var message = section(components, "messages").values().stream()
                .filter(candidate -> messageName.equals(messageName(candidate)))
                .findFirst()
                .orElseThrow();
        var schemaName = payloadRef(message).substring(LOCAL_SCHEMA_REF_PREFIX.length());
        return PayloadSchema.of(section(components, "schemas", schemaName));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> parent, String... path) {
        var current = parent;
        for (var key : path) {
            current = (Map<String, Object>) current.get(key);
        }
        return current;
    }

    private static String messageName(Object message) {
        return (String) ((Map<?, ?>) message).get("name");
    }

    private static String payloadRef(Object message) {
        return (String) ((Map<?, ?>) ((Map<?, ?>) message).get("payload")).get("$ref");
    }

    /**
     * The fields an object schema demands and allows.
     *
     * @param required             the {@code required} fields
     * @param properties           the declared {@code properties}
     * @param additionalProperties whether fields beyond {@code properties} are allowed
     */
    public record PayloadSchema(Set<String> required, Set<String> properties, boolean additionalProperties) {

        @SuppressWarnings("unchecked")
        private static PayloadSchema of(Map<String, Object> schema) {
            return new PayloadSchema(Set.copyOf((List<String>) schema.get("required")),
                    section(schema, "properties").keySet(),
                    !Boolean.FALSE.equals(schema.get("additionalProperties")));
        }
    }
}
