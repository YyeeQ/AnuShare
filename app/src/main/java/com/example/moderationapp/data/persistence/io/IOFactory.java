package com.example.moderationapp.data.persistence.io;

import java.io.Reader;
import java.io.Writer;

public interface IOFactory {
    Writer writer(String filename);
    Reader reader(String filename);
}
