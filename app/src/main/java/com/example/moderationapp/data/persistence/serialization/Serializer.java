package com.example.moderationapp.data.persistence.serialization;

public interface Serializer<T, S> {
    S serialize(T object);
    T deserialize(S data);
}
