package amidst.json;

import java.util.List;

public class JarLibrary {
	private String name;
	private List<JarRule> rules;

	public String getName() {
		return name;
	}

	public List<JarRule> getRules() {
		return rules;
	}

	public boolean isActive() {
		if (rules.isEmpty()) {
			return true;
		}
		for (JarRule rule : rules) {
			if (rule.isApplicable() && rule.isAllowed()) {
				return true;
			}
		}
		return false;
	}
}