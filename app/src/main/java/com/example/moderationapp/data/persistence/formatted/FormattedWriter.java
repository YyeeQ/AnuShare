package com.example.moderationapp.data.persistence.formatted;

public interface FormattedWriter<S> {
    void putHeader();
    void putNext(S serialized);
    void putFooter();
}
