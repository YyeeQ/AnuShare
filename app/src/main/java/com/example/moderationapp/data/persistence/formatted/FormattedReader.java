package com.example.moderationapp.data.persistence.formatted;

public interface FormattedReader<S> {
    boolean hasNext();
    S getNext();
}
