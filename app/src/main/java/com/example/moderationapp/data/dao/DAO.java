package com.example.moderationapp.data.dao;

import com.example.moderationapp.data.model.HasUUID;
import com.example.moderationapp.logic.sorteddata.SortedData;
import com.example.moderationapp.logic.sorteddata.SortedDataFactory;

import java.util.Comparator;
import java.util.Iterator;

public abstract class DAO<T extends HasUUID> {
    protected final Comparator<T> comparator;
    protected SortedData<T> data;

    protected DAO(Comparator<T> comparator) {
        this.comparator = comparator;
        clear();
    }

    public T get(T element) {
        return data.get(element);
    }

    public boolean add(T element) {
        return data.insert(element);
    }

    public boolean remove(T element) {
        boolean removed = false;
        SortedData<T> rebuilt = SortedDataFactory.makeSortedData(comparator);
        Iterator<T> iterator = data.getAll();
        while (iterator.hasNext()) {
            T current = iterator.next();
            if (comparator.compare(current, element) == 0) {
                removed = true;
            } else {
                rebuilt.insert(current);
            }
        }
        data = rebuilt;
        return removed;
    }

    public void clear() {
        data = SortedDataFactory.makeSortedData(comparator);
    }

    public Iterator<T> getAll() {
        return data.getAll();
    }

    public T getRandom() {
        return data.getRandom();
    }
}
