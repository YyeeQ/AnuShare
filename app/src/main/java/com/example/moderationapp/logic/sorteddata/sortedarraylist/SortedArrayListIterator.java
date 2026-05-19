package com.example.moderationapp.logic.sorteddata.sortedarraylist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class SortedArrayListIterator<T> implements Iterator<T> {
    private final ArrayList<T> list;
    private int cursor;
    private final int end;

    public SortedArrayListIterator(ArrayList<T> list, Comparator<T> comparator, T start, int count) {
        this.list = list;
        if (start == null) {
            this.cursor = 0;
        } else {
            int s = 0, e = list.size();
            while (e > s) {
                int m = (s + e) / 2;
                if (comparator.compare(start, list.get(m)) > 0) {
                    s = m + 1;
                } else {
                    e = m;
                }
            }
            this.cursor = s;
        }
        this.end = (count == -1) ? list.size() : Math.min(list.size(), cursor + count);
    }

    @Override
    public boolean hasNext() {
        return cursor < end;
    }

    @Override
    public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        return list.get(cursor++);
    }
}
