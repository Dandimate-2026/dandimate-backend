/** Structured Outputs용 JSON Schema 조각. strict 모드라 모든 필드를 required로 두고 additionalProperties=false로 닫는다. */
package com.dandimate.durooduroo.ai.openai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Schemas {

    private Schemas() {
    }

    static Map<String, Object> object(Map<String, Object> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("additionalProperties", false);
        return schema;
    }

    static Map<String, Object> string() {
        return Map.of("type", "string");
    }

    static Map<String, Object> nullableString() {
        return Map.of("type", List.of("string", "null"));
    }

    static Map<String, Object> number() {
        return Map.of("type", "number");
    }

    static Map<String, Object> bool() {
        return Map.of("type", "boolean");
    }

    static Map<String, Object> enumOf(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    static Map<String, Object> nullableEnumOf(String... values) {
        List<Object> withNull = new java.util.ArrayList<>(List.of(values));
        withNull.add(null);
        return Map.of("type", List.of("string", "null"), "enum", withNull);
    }

    static Map<String, Object> array(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    static Map<String, Object> citation() {
        return object(ordered("entryId", string(), "memberId", string(), "exactQuote", string()));
    }

    static Map<String, Object> safety() {
        return object(ordered("action", enumOf("ALLOW", "SOFTEN", "OMIT"), "categories", array(string())));
    }

    static Map<String, Object> ordered(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }
}
