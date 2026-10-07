package me.matterz.supernaturals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PluginDescriptorTest {

	@SuppressWarnings("unchecked")
	private Map<String, Object> load() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/plugin.yml")) {
			assertNotNull(in, "plugin.yml missing");
			return (Map<String, Object>) new Yaml().load(in);
		}
	}

	@Test
	void mainClassExists() throws Exception {
		Class<?> c = Class.forName((String) load().get("main"));
		assertTrue(org.bukkit.plugin.java.JavaPlugin.class.isAssignableFrom(c));
	}

	@Test
	void declaresApiVersionAndSnCommand() throws Exception {
		Map<String, Object> y = load();
		assertNotNull(y.get("api-version"));
		assertTrue(((Map<?, ?>) y.get("commands")).containsKey("sn"));
		assertEquals("mmSupernaturals", y.get("name"));
	}
}
