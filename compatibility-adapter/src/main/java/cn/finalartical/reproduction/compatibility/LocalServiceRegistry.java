package cn.finalartical.reproduction.compatibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class LocalServiceRegistry {
    private final Map<String, Object> providers = new LinkedHashMap<String, Object>();

    public <T> LocalServiceRegistry register(String serviceName, T provider) {
        if (serviceName == null || serviceName.trim().isEmpty() || provider == null) {
            throw new IllegalArgumentException("service name and provider must be valid");
        }
        providers.put(serviceName, provider);
        return this;
    }

    public <T> Optional<T> resolve(String serviceName, Class<T> providerType) {
        Object provider = providers.get(serviceName);
        if (provider == null || !providerType.isInstance(provider)) {
            return Optional.empty();
        }
        return Optional.of(providerType.cast(provider));
    }
}
