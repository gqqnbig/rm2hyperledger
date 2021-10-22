package entities;

import com.owlike.genson.Genson;

import java.util.LinkedList;
import java.util.List;

public class GensonHelper {
	public static <T> List<T> deserializeList(Genson genson, String json, Class<T> type) {
		Object o = genson.deserialize(json, Object.class);
		List l = (List) o;
		List<T> l2 = new LinkedList<>();
		for (int i = 0; i < l.size(); i++) {
			String j = genson.serialize(l.get(i));
			l2.add(genson.deserialize(j, type));
		}

		return l2;
	}
}
