package first.carbon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Carbon implements ClientModInitializer {
    public static final Settings CONFIG = new Settings();
    @Override public void onInitializeClient() {
        CONFIG.load();
        first.carbon.client.ClientEvents.register();
    }
    public static final class Client {
        public static final first.carbon.compat.IrisCompatibility IRIS = new first.carbon.compat.IrisCompatibility();
        public static final first.carbon.client.ItemBatchRenderer RENDERER = new first.carbon.client.ItemBatchRenderer();
        public static final first.carbon.client.DroppedItemBatchRenderer DROPPED_RENDERER = new first.carbon.client.DroppedItemBatchRenderer();
    }
    public static final class Value<T> {
        private T value;
        Value(T value) { this.value = value; }
        public T get() { return value; }
        public void set(T value) { this.value = value; }
    }
    public static final class Settings {
        public final Settings spec = this;
        public final Value<Boolean> enabled = new Value<>(true), optimizeDroppedItems = new Value<>(true),
                physicalDroppedItems = new Value<>(false), skipVisibilityChecks = new Value<>(true);
        public final Value<Double> cacheUpdateBudgetMs = new Value<>(2.0);
        private final Path file = FabricLoader.getInstance().getConfigDir().resolve("carbon-client.toml");
        private final Map<String, Value<?>> values = new LinkedHashMap<>();
        private List<String> lines = new ArrayList<>();
        private Settings() {
            values.put("enabled", enabled);
            values.put("optimizeDroppedItems", optimizeDroppedItems);
            values.put("physicalDroppedItems", physicalDroppedItems);
            values.put("skipVisibilityChecks", skipVisibilityChecks);
            values.put("cacheUpdateBudgetMs", cacheUpdateBudgetMs);
        }
        private void load() {
            try {
                if (Files.isRegularFile(file)) {
                    lines = Files.readAllLines(file);
                    for (String line : lines) {
                        String[] parts = line.split("#", 2)[0].split("=", 2);
                        if (parts.length == 2) {
                            Value<?> value = values.get(parts[0].trim());
                            String text = parts[1].trim();
                            if (value == cacheUpdateBudgetMs) {
                                double budget = Double.parseDouble(text);
                                if (Double.isFinite(budget)) cacheUpdateBudgetMs.set(Math.max(0, Math.min(20, budget)));
                            } else if (value != null && (text.equals("true") || text.equals("false"))) {
                                @SuppressWarnings("unchecked") Value<Boolean> flag = (Value<Boolean>) value;
                                flag.set(Boolean.parseBoolean(text));
                            }
                        }
                    }
                } else save();
            } catch (Exception exception) { LogManager.getLogger("Carbon").warn("Unable to read Carbon configuration", exception); }
        }
        public void save() {
            try {
                Map<String, Value<?>> remaining = new LinkedHashMap<>(values);
                List<String> output = new ArrayList<>();
                for (String line : lines) {
                    String key = line.split("=", 2)[0].trim();
                    Value<?> value = remaining.remove(key);
                    output.add(value == null ? line : key + " = " + value.get());
                }
                remaining.forEach((key, value) -> output.add(key + " = " + value.get()));
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling("carbon-client.toml.tmp");
                Files.write(temporary, output);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                lines = output;
            } catch (Exception exception) { LogManager.getLogger("Carbon").warn("Unable to save Carbon configuration", exception); }
        }
    }
}
