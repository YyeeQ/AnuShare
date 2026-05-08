package persistentdata;

import persistentdata.formatted.*;
import persistentdata.io.IOFactory;
import persistentdata.serialization.*;
import dao.*;

import java.io.*;
import java.util.Iterator;

public class DataPipeline<T, S> {
	private final IOFactory ioFactory;
	private final FormattedFactory<S> formattedFactory;
	private final Serializer<T, S> serializer;
	private final String filename;

	public DataPipeline(IOFactory ioFactory, FormattedFactory<S> formattedFactory, Serializer<T, S> serializer, String filename) {
		this.ioFactory = ioFactory;
		this.formattedFactory = formattedFactory;
		this.serializer = serializer;
		this.filename = filename;
	}

	public void writeFrom(Iterator<T> iterator) {
		try {
			Writer writer = ioFactory.writer(filename);
			if (writer == null) return;
			FormattedWriter<S> formattedWriter = formattedFactory.writer(writer);

			while (iterator.hasNext()) {
				formattedWriter.putNext(serializer.serialize(iterator.next()));
			}

			writer.close();
		} catch (IOException e) {
			throw new PersistentDataException(e.getMessage());
		}
	}

	public interface AddToDAO<T> {
		void run(T item);
	}

	public void readTo(AddToDAO<T> callback) {
		try {
			Reader reader = ioFactory.reader(filename);
			if (reader == null) return;
			FormattedReader<S> formattedReader = formattedFactory.reader(reader);

			while (formattedReader.hasNext()) {
				callback.run(serializer.deserialize(formattedReader.getNext()));
			}

			reader.close();
		} catch (IOException e) {
			throw new PersistentDataException(e.getMessage());
		}
	}
}
