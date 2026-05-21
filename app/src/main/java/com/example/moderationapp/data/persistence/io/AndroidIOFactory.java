package com.example.moderationapp.data.persistence.io;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public class AndroidIOFactory implements IOFactory {
    private static final String TAG = "AndroidIOFactory";
    private final File baseDir;

    public AndroidIOFactory(Context context) {
        baseDir = new File(context.getFilesDir(), "saved");
    }

    private File fileFor(String filename) {
        return new File(baseDir, filename + ".txt");
    }

    @Override
    public Writer writer(String filename) {
        try {
            if (!baseDir.exists() && !baseDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + baseDir);
            }
            
            final File finalFile = fileFor(filename);
            final File tempFile = new File(baseDir, filename + ".tmp");

            return new FileWriter(tempFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    if (tempFile.exists()) {
                        if (finalFile.exists() && !finalFile.delete()) {
                            Log.w(TAG, "Could not delete existing file: " + finalFile);
                        }
                        if (!tempFile.renameTo(finalFile)) {
                            throw new IOException("Failed to rename temp file to " + finalFile);
                        }
                    }
                }
            };
        } catch (IOException e) {
            Log.e(TAG, "Error creating writer for " + filename, e);
            return null;
        }
    }

    @Override
    public Reader reader(String filename) {
        try {
            File file = fileFor(filename);
            if (!file.exists()) return null;
            return new FileReader(file);
        } catch (IOException e) {
            Log.e(TAG, "Error creating reader for " + filename, e);
            return null;
        }
    }
}
