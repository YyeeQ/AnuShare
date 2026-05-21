package com.example.moderationapp.logic.sorteddata;

import java.util.Iterator;

public interface SortedData<T> extends SortedDataSubject<T> {
    boolean insert(T element);
    T get(T element);
    Iterator<T> getAll();
    T getRandom();
    void clear();
}
