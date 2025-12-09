package com.rrp.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.DataException;
import org.apache.kafka.connect.transforms.Transformation;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RegexMask SMT - performs regex replacement on String and byte[] values.
 *
 * Usage as a transformation on value:
 *   "transforms": "MaskEmail",
 *   "transforms.MaskEmail.type": "com.example.connect.transforms.RegexMask$Value",
 *   "transforms.MaskEmail.regex": "([A-Za-z0-9._%+-]+)@",
 *   "transforms.MaskEmail.replacement": "***@"
 *
 * Notes:
 * - Works with String values or byte[] (interpreted as UTF-8)
 * - For Struct values you should convert to Struct/JSON beforehand
 */
public abstract class RegexMask<R extends ConnectRecord<R>> implements Transformation<R> {

    private Pattern pattern;
    private String replacement;

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String REGEX = "regex";
    public static final String REPLACEMENT = "replacement";

    public static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(REGEX, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Regex pattern")
            .define(REPLACEMENT, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "Replacement text");

    @Override
    public void configure(Map<String, ?> configs) {
        String r = (String) configs.get(REGEX);
        String repl = (String) configs.get(REPLACEMENT);

        pattern = Pattern.compile(r);
        replacement = repl;
    }

    @Override
    public R apply(R record) {
        Object value = record.value();

        if (value == null) {
            return record;
        }

        try {
            // -------------------------------------------------------------------
            // Case 1: Plain string OR JSON string
            // -------------------------------------------------------------------
            if (value instanceof String) {
                String s = (String) value;

                // Try to parse JSON
                try {
                    JsonNode root = mapper.readTree(s);

                    // If successfully parsed AND is JSON object/array
                    if (root.isObject() || root.isArray()) {
                        JsonNode masked = maskJsonNode(root);
                        String maskedJson = mapper.writeValueAsString(masked);

                        return record.newRecord(
                                record.topic(), record.kafkaPartition(),
                                record.keySchema(), record.key(),
                                record.valueSchema(), maskedJson,
                                record.timestamp()
                        );
                    }
                } catch (IOException ignore) {
                    // not JSON → fall through to plain string masking
                }

                // Plain string masking
                String masked = applyMask(s);

                return record.newRecord(
                        record.topic(), record.kafkaPartition(),
                        record.keySchema(), record.key(),
                        record.valueSchema(), masked,
                        record.timestamp()
                );
            }

            // -------------------------------------------------------------------
            // Case 2: JSON without schema (Map)
            // -------------------------------------------------------------------
            if (value instanceof Map) {
                String json = mapper.writeValueAsString(value);
                JsonNode masked = maskJsonNode(mapper.readTree(json));
                Map newMap = mapper.readValue(masked.toString(), Map.class);

                return record.newRecord(
                        record.topic(), record.kafkaPartition(),
                        record.keySchema(), record.key(),
                        record.valueSchema(), newMap,
                        record.timestamp()
                );
            }

            // -------------------------------------------------------------------
            // Case 3: Struct (schema-aware)
            // -------------------------------------------------------------------
            if (value instanceof Struct) {
                Struct struct = (Struct) value;
                Struct newStruct = new Struct(struct.schema());

                for (var field : struct.schema().fields()) {
                    Object fieldValue = struct.get(field);

                    if (fieldValue instanceof String) {
                        newStruct.put(field.name(), applyMask((String) fieldValue));
                    } else {
                        newStruct.put(field.name(), fieldValue);
                    }
                }

                return record.newRecord(
                        record.topic(), record.kafkaPartition(),
                        record.keySchema(), record.key(),
                        struct.schema(), newStruct,
                        record.timestamp()
                );
            }

            // -------------------------------------------------------------------
            // Unsupported types
            // -------------------------------------------------------------------
            throw new DataException("Unsupported data type: " + value.getClass());

        } catch (Exception ex) {
            throw new DataException("Failed during RegexMask SMT", ex);
        }
    }

    private String applyMask(String input) {
        return pattern.matcher(input).replaceAll(replacement);
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public void close() {}

    // Value version
    public static class Value<R extends ConnectRecord<R>> extends RegexMask<R> {}

    private JsonNode maskJsonNode(JsonNode node) {
        if (node.isTextual()) {
            String masked = applyMask(node.asText());
            return mapper.getNodeFactory().textNode(masked);
        }

        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            ObjectNode newObj = mapper.createObjectNode();
            obj.fields().forEachRemaining(e -> newObj.set(e.getKey(), maskJsonNode(e.getValue())));
            return newObj;
        }

        if (node.isArray()) {
            ArrayNode arr = mapper.createArrayNode();
            node.forEach(item -> arr.add(maskJsonNode(item)));
            return arr;
        }

        // numbers, booleans, null → leave unchanged
        return node;
    }
}