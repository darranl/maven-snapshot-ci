package org.wildfly.util.maven.snapshot.ci.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class BaseParser {
    protected Map<String, String> parseEnv(Object input) {
        if (input instanceof Map == false) {
            throw new IllegalStateException("Not an instance of Map");
        }
        Map<String, Object> map = (Map)input;
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : map.keySet()) {
            Object value = map.get(key);
            if (value instanceof String == false && value instanceof Number == false) {
                throw new IllegalStateException("Value under key '" + key + "' is not a String or a Number");
            }
            result.put(key, value.toString());
        }
        return result;
    }

}
