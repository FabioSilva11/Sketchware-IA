package pro.sketchware.network.provider;

import java.util.EnumMap;
import java.util.Map;

import pro.sketchware.activities.chat.port.VoidPortLlmMessage.ProviderFamily;

public final class AiProviderAdapterRegistry {
    private final Map<ProviderFamily, AiProviderAdapter> adapters = new EnumMap<>(ProviderFamily.class);

    public AiProviderAdapterRegistry() {
        register(new OpenAiCompatibleAdapter());
        register(new AnthropicProviderAdapter());
        register(new GeminiProviderAdapter());
    }

    public AiProviderAdapter get(ProviderFamily family) {
        AiProviderAdapter adapter = adapters.get(family);
        if (adapter == null) {
            throw new IllegalArgumentException("Unsupported provider family: " + family);
        }
        return adapter;
    }

    private void register(AiProviderAdapter adapter) {
        adapters.put(adapter.family(), adapter);
    }
}
