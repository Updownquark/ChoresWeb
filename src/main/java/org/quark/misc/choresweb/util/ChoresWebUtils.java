package org.quark.misc.choresweb.util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.qommons.Named;
import org.qommons.StringUtils;
import org.qommons.StringUtils.DuplicateName;

public class ChoresWebUtils {
	public static String getNewName(List<? extends Named> existing, int lengthLimit, String... firstTries) {
		Set<String> names = existing.stream().map(Named::getName).collect(Collectors.toSet());
		String firstTry = null;
		for (String name : firstTries) {
			if (name != null) {
				firstTry = name;
				break;
			}
		}
		if (firstTry.length() > lengthLimit)
			throw new IllegalArgumentException("Name cannot exceed " + lengthLimit + " characters");
		return StringUtils.getNewItemName(names::contains, firstTry, new StringUtils.DuplicateItemNamer() {

			@Override
			public DuplicateName detectDuplicate(String name) {
				return StringUtils.SIMPLE_DUPLICATES.detectDuplicate(name);
			}

			@Override
			public void appendDuplicate(StringBuilder name, int suffix) {
				int preLen = name.length();
				StringUtils.SIMPLE_DUPLICATES.appendDuplicate(name, suffix);
				if (name.length() > lengthLimit)
					name.delete(preLen - name.length() + lengthLimit, preLen);
			}
		});
	}
}
