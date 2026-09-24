package ch.lxrin.ql.spring;

import ch.lxrin.ql.types.JsonCodec;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Type;
import java.util.Objects;

/** A {@link JsonCodec} backed by a Jackson 3 {@link JsonMapper}, e.g. the one Spring Boot configures. */
public final class JacksonJsonCodec implements JsonCodec {

    private final JsonMapper mapper;

    /** Creates the codec. */
    public JacksonJsonCodec(JsonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    @Override
    public <T> T read(String json, Type type) {
        return mapper.readValue(json, mapper.constructType(type));
    }
}
