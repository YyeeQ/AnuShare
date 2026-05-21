package com.example.moderationapp.logic.sorteddata.sortedarraylist;

import com.example.moderationapp.logic.sorteddata.SortedData;

import java.util.*;

public class SortedArrayList<T> implements SortedData<T> {
    private final Comparator<T> comparator;
    private final ArrayList<T> list;

    public SortedArrayList(Comparator<T> comparator) {
        this.comparator = comparator;
        this.list = new ArrayList<>();
    }

    private int binarySearch(T value) {
        int s = 0, e = list.size();
        while (e > s) {
            int m = (s + e) / 2;
            if (comparator.compare(value, list.get(m)) > 0) {
                s = m + 1;
            } else {
                e = m;
            }
        }
        return s;
    }

    @Override
    public boolean insert(T value) {
        int i = binarySearch(value);
        if (i < list.size() && comparator.compare(list.get(i), value) == 0)
            return false;
        list.add(i, value);
        return true;
    }

    @Override
    public T get(T value) {
        int i = binarySearch(value);
        return i < list.size() && comparator.compare(list.get(i), value) == 0 ? list.get(i) : null;
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("SortedArrayList[");
        for (int i = 0; i < list.size(); i++) {
            if (i != 0) out.append(", ");
            out.append(list.get(i).toString());
        }
        return out.append("]").toString();
    }

    private static final Random random = new Random();

    public T getAtIndex(int i) {
        return list.get(i);
    }

    @Override
    public T getRandom() {
        if (list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    @Override
    public void clear() {
        list.clear();
    }

    @Override
    public Iterator<T> getAll() {
        return new SortedArrayListIterator<>(list, comparator, null, -1);
    }
}
