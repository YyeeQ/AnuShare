package com.example.moderationapp.logic.sorteddata;

import com.example.moderationapp.logic.sorteddata.sortedarraylist.SortedArrayList;
import java.util.Comparator;

public class SortedDataFactory {
    public static <T> SortedData<T> makeSortedData(Comparator<T> comparator) {
        // Defaulting to SortedArrayList as per the original factory's typical implementation
        return new SortedArrayList<>(comparator);
    }
}
