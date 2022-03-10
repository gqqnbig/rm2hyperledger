package converters;

import com.owlike.genson.Context;
import com.owlike.genson.Converter;
import com.owlike.genson.stream.ObjectReader;
import com.owlike.genson.stream.ObjectWriter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateConverter implements Converter<LocalDate> {
	public LocalDateConverter() {
	}

	@Override
	public void serialize(LocalDate object, ObjectWriter writer, Context ctx) {
		writer.writeString(object.format(DateTimeFormatter.ISO_DATE));
	}

	@Override
	public LocalDate deserialize(ObjectReader reader, Context ctx) {
		String str = reader.valueAsString();
		if (str != null)
			return LocalDate.parse(str, DateTimeFormatter.ISO_DATE);
		else
			return null;
	}
}
